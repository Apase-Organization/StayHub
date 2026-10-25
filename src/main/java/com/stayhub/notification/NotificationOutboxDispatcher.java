package com.stayhub.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** At-least-once SMTP delivery from committed booking/refund outbox rows. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxDispatcher {
    private static final int MAX_ATTEMPTS=3;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final EmailNotificationService email;
    private final ObjectMapper mapper;

    @Scheduled(fixedDelayString="${app.notification.outbox.poll-delay-ms:5000}",
            initialDelayString="${app.notification.outbox.initial-delay-ms:5000}")
    public void poll() {
        if(email.isMailEnabled()) dispatchBatch(50);
    }

    /** Public for bounded integration tests and controlled manual drain operations. */
    public int dispatchBatch(int limit) {
        if(!email.isMailEnabled()||limit<=0) return 0;
        int bounded=Math.min(limit,100);
        List<OutboxEvent> events=new TransactionTemplate(transactions).execute(status->claim(bounded));
        if(events==null) return 0;
        int delivered=0;
        for(OutboxEvent event:events) {
            try {
                dispatch(event);
                complete(event);
                delivered++;
            } catch(Exception failure) {
                fail(event);
                log.warn("Notification outbox delivery failed for event {} (attempt {}/{}): {}",
                        event.eventKey(),event.attempt(),MAX_ATTEMPTS,failure.getClass().getSimpleName());
            }
        }
        return delivered;
    }

    private List<OutboxEvent> claim(int limit) {
        var claimed=new ArrayList<OutboxEvent>();
        int bookingLimit=(limit+2)/3;
        int refundLimit=(limit+1)/3;
        claimTable("booking_notification_outbox",bookingLimit,claimed);
        claimTable("refund_notification_outbox",Math.min(refundLimit,limit-claimed.size()),claimed);
        claimTable("payout_notification_outbox",limit-claimed.size(),claimed);
        if(claimed.size()<limit) {
            int remaining=limit-claimed.size();
            claimTable("booking_notification_outbox",remaining,claimed);
            remaining=limit-claimed.size();
            claimTable("refund_notification_outbox",remaining,claimed);
            remaining=limit-claimed.size();
            claimTable("payout_notification_outbox",remaining,claimed);
        }
        return List.copyOf(claimed);
    }
    private void claimTable(String table,int limit,List<OutboxEvent> target) {
        if(limit<=0) return;
        if(!table.equals("booking_notification_outbox")&&!table.equals("refund_notification_outbox")
                &&!table.equals("payout_notification_outbox"))
            throw new IllegalArgumentException("unsupported notification outbox");
        var rows=jdbc.query("""
                SELECT id,event_key,event_type,payload::text AS payload,attempts
                FROM %s WHERE dispatched_at IS NULL AND attempts<?
                  AND (claimed_at IS NULL OR claimed_at<clock_timestamp()-interval '5 minutes')
                ORDER BY created_at,id LIMIT ? FOR UPDATE SKIP LOCKED
                """.formatted(table),(r,n)->new OutboxEvent(table,r.getLong("id"),r.getString("event_key"),
                r.getString("event_type"),r.getString("payload"),r.getInt("attempts")+1),MAX_ATTEMPTS,limit);
        for(var row:rows) {
            jdbc.update("UPDATE "+table+" SET claimed_at=clock_timestamp(),attempts=attempts+1 WHERE id=?",row.id());
            target.add(row);
        }
    }

    private void dispatch(OutboxEvent event) throws Exception {
        JsonNode p=mapper.readTree(event.payload());
        long recipientId=event.table().equals("payout_notification_outbox")?p.path("hostId").asLong():p.path("guestId").asLong();
        var recipient=jdbc.queryForList("SELECT email,full_name FROM users WHERE id=? AND status='ACTIVE'",recipientId);
        if(recipient.isEmpty()) throw new IllegalStateException("recipient unavailable");
        String address=(String)recipient.getFirst().get("email");
        String name=(String)recipient.getFirst().get("full_name");
        if(event.table().equals("booking_notification_outbox")) sendBooking(event,p,address,name);
        else if(event.table().equals("refund_notification_outbox")) sendRefund(event,p,address,name);
        else sendPayout(event,p,address,name);
    }

    private void sendBooking(OutboxEvent event,JsonNode p,String address,String name) throws Exception {
        JsonNode terms=p.path("terms"),listing=p.path("listing");
        String status=p.path("status").asText();
        String title=listing.path("title").asText("your property");
        LocalDate checkIn=LocalDate.parse(terms.path("checkIn").asText());
        LocalDate checkOut=LocalDate.parse(terms.path("checkOut").asText());
        int guests=terms.path("guests").asInt();
        BigDecimal total=decimal(terms.path("total"));
        String mode=terms.path("paymentMode").isMissingNode()?null:terms.path("paymentMode").asText();
        BigDecimal online=optionalDecimal(terms.path("financialSnapshot").path("dueOnline"));
        BigDecimal hostBalance=optionalDecimal(terms.path("financialSnapshot").path("balanceDueHost"));
        var items=new ArrayList<EmailTemplateRenderer.ItemDetail>();
        for(JsonNode item:p.path("items")) items.add(new EmailTemplateRenderer.ItemDetail(
                item.path("roomType").asText("Room"),item.path("ratePlan").asText("Plan"),
                item.path("quantity").asInt(),item.path("roomNights").asLong(),decimal(item.path("total"))));
        String subject="StayHub booking "+status.toLowerCase();
        String plain="Booking "+p.path("bookingId").asLong()+" for "+title+" is "+status+".";
        String html=EmailTemplateRenderer.bookingStatusEmail(name,title,status,checkIn,checkOut,guests,total,
                mode,online,hostBalance,items);
        email.sendOutboxEmail(address,subject,plain,html);
    }

    private void sendRefund(OutboxEvent event,JsonNode p,String address,String name) throws Exception {
        String state=p.path("state").asText();
        String source=p.path("source").asText();
        BigDecimal amount=decimal(p.path("amount"));
        String sla=source.equals("ONLINE_VERIFIED")
                ? "Platform refund estimate: "+p.path("minimumBusinessDays").asInt()+"–"+
                    p.path("maximumBusinessDays").asInt()+" business days, calendar "+p.path("calendarVersion").asText()+"."
                : "This is a Host-direct refund responsibility; StayHub did not collect this amount.";
        String html=EmailTemplateRenderer.refundStatusEmail(name,p.path("bookingId").asLong(),state,source,amount,
                p.path("currency").asText("VND"),sla,p.path("transferRef").asText(null));
        email.sendOutboxEmail(address,"StayHub refund "+state.toLowerCase(),
                "Refund "+state+" for booking "+p.path("bookingId").asLong()+". "+sla,html);
    }

    private void sendPayout(OutboxEvent event,JsonNode p,String address,String name) throws Exception {
        String state=p.path("state").asText();
        BigDecimal amount=decimal(p.path("amount"));
        String reference=p.path("transferRef").asText(null);
        String html=EmailTemplateRenderer.payoutStatusEmail(name,p.path("bookingId").asLong(),state,
                amount,p.path("currency").asText("VND"),reference);
        email.sendOutboxEmail(address,"StayHub payout "+state.toLowerCase(),
                "Payout "+state+" for booking "+p.path("bookingId").asLong()+". No automatic transfer is used.",html);
    }

    private void complete(OutboxEvent event) {
        jdbc.update("UPDATE "+event.table()+" SET dispatched_at=clock_timestamp(),claimed_at=NULL,last_error_code=NULL WHERE id=?",event.id());
    }
    private void fail(OutboxEvent event) {
        jdbc.update("UPDATE "+event.table()+" SET claimed_at=NULL,last_error_code='SMTP_DELIVERY_FAILED' WHERE id=?",event.id());
    }
    private static BigDecimal decimal(JsonNode value) {
        return value==null||value.isNull()||value.isMissingNode()?BigDecimal.ZERO:value.decimalValue();
    }
    private static BigDecimal optionalDecimal(JsonNode value) { return value==null||value.isNull()||value.isMissingNode()?null:value.decimalValue(); }
    private record OutboxEvent(String table,long id,String eventKey,String eventType,String payload,int attempt) {}
}
