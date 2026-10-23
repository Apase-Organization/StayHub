package com.stayhub.payment;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stayhub.booking.BookingStatus;
import com.stayhub.config.VnpayProperties;
import com.stayhub.inventory.InventoryCapacityService;
import com.stayhub.inventory.InventoryDiscountReservationService;
import com.stayhub.notification.NotificationService;
import com.stayhub.payment.dto.VnpayIpnResponse;
import com.stayhub.payment.ledger.PaymentPrimitivesService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Verified IPN composition for V16/V15 inventory bookings; legacy callbacks remain unchanged. */
@Service
public class InventoryPaymentLifecycleService {
    private static final DateTimeFormatter PAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final JsonMapper CANONICAL = JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    private final JdbcTemplate jdbc;
    private final VnpayProperties properties;
    private final VnpayPaymentService vnpay;
    private final InventoryCapacityService capacity;
    private final PaymentPrimitivesService primitives;
    private final InventoryDiscountReservationService discounts;
    private final PaymentRepository payments;
    private final NotificationService notifications;

    public InventoryPaymentLifecycleService(JdbcTemplate jdbc,VnpayProperties properties,VnpayPaymentService vnpay,
            InventoryCapacityService capacity,PaymentPrimitivesService primitives,
            InventoryDiscountReservationService discounts,PaymentRepository payments,NotificationService notifications) {
        this.jdbc=jdbc; this.properties=properties; this.vnpay=vnpay; this.capacity=capacity; this.primitives=primitives;
        this.discounts=discounts; this.payments=payments; this.notifications=notifications;
    }

    /** Returns null for legacy payment references so the old adapter can handle them. */
    @Transactional
    public VnpayIpnResponse handleInventoryIpn(Map<String,String> params) {
        String reference=params.get("vnp_TxnRef");
        if(reference==null) return null;
        List<AttemptContext> attempts=jdbc.query("""
                SELECT a.id AS attempt_id,a.booking_id,a.actor_id,a.expected_amount,a.currency,a.merchant,a.hold_expires_at,
                       b.status AS booking_status,b.inventory_hold_expires_at
                FROM payment_attempts a JOIN bookings b ON b.id=a.booking_id WHERE a.provider_ref=?
                """,(rs,row)->new AttemptContext(rs.getLong("attempt_id"),rs.getLong("booking_id"),rs.getLong("actor_id"),
                rs.getBigDecimal("expected_amount"),rs.getString("currency"),rs.getString("merchant"),
                rs.getTimestamp("hold_expires_at").toInstant(),rs.getString("booking_status"),
                rs.getTimestamp("inventory_hold_expires_at")==null?null:rs.getTimestamp("inventory_hold_expires_at").toInstant()),reference);
        if(attempts.isEmpty()) return null;
        AttemptContext attempt=attempts.getFirst();
        if(!vnpay.verifySecureHash(params) || !attempt.merchant().equals(params.get("vnp_TmnCode"))
                || !attempt.merchant().equals(properties.getTmnCode()))
            return new VnpayIpnResponse("97","Invalid provider verification");
        if(!"2.1.0".equals(params.get("vnp_Version")) || !"pay".equals(params.get("vnp_Command"))
                || params.get("vnp_ResponseCode")==null || !params.get("vnp_ResponseCode").matches("[0-9]{2}")
                || params.get("vnp_TransactionStatus")==null || !params.get("vnp_TransactionStatus").matches("[0-9]{2}"))
            return new VnpayIpnResponse("97","Invalid provider envelope");

        String transactionNo=params.get("vnp_TransactionNo");
        Map<String,String> signed=new TreeMap<>(params);
        signed.remove("vnp_SecureHash"); signed.remove("vnp_SecureHashType");
        String bodyHash=digest(signed);
        String eventKey=digest(List.of(attempt.id(),transactionNo==null?"":transactionNo,bodyHash));
        ExistingEvent event=findExisting(eventKey,transactionNo,bodyHash);
        if(event!=null) return response(event.disposition());

        Long eventId=jdbc.query("""
                INSERT INTO inventory_provider_events(attempt_id,booking_id,provider_ref,provider_transaction_no,event_key,
                    body_hash,wire_amount,wire_currency,response_code,transaction_status,disposition)
                VALUES (?,?,?,?,?,?,?,?,?,?,'RECEIVED') ON CONFLICT DO NOTHING RETURNING id
                """,(rs,row)->rs.getLong(1),attempt.id(),attempt.bookingId(),reference,transactionNo,eventKey,bodyHash,
                params.get("vnp_Amount"),params.get("vnp_CurrCode"),params.get("vnp_ResponseCode"),params.get("vnp_TransactionStatus"))
                .stream().findFirst().orElse(null);
        if(eventId==null) {
            ExistingEvent prior=findExisting(eventKey,transactionNo,bodyHash);
            if(prior!=null) return response(prior.disposition());
            return new VnpayIpnResponse("02","Provider event already handled");
        }

        boolean success="00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
        BigDecimal actualAmount=parseWireAmount(params.get("vnp_Amount"));
        boolean exactAttemptAmount=actualAmount!=null
                && actualAmount.compareTo(attempt.expectedAmount().multiply(BigDecimal.valueOf(100)))==0
                && "VND".equals(params.get("vnp_CurrCode"));
        if(!success) {
            // Only a well-formed failure for the exact server-owned attempt amount
            // is terminal. An unexpected amount/currency must remain in the
            // reconciliation path and cannot trigger early hold release.
            finish(eventId,exactAttemptAmount?"FAILED":"RECONCILE");
            return new VnpayIpnResponse("00","Confirm success");
        }

        String currency=params.get("vnp_CurrCode");
        Instant occurred=parsePaidAt(params.get("vnp_PayDate"));
        boolean exact=actualAmount!=null && actualAmount.compareTo(attempt.expectedAmount().multiply(BigDecimal.valueOf(100)))==0
                && "VND".equals(currency) && transactionNo!=null && !transactionNo.isBlank()
                && occurred!=null && !occurred.isAfter(Instant.now());
        if(!exact) {
            recordReconciliation(attempt,transactionNo,eventKey,bodyHash,actualAmount,currency,occurred);
            finish(eventId,"RECONCILE");
            return new VnpayIpnResponse("00","Receipt recorded for reconciliation");
        }

        jdbc.queryForObject("SELECT id FROM bookings WHERE id=? FOR UPDATE",Long.class,attempt.bookingId());
        Instant now=Instant.now();
        if(!BookingStatus.PENDING_PAYMENT.name().equals(attempt.bookingStatus()) || attempt.holdExpiresAt()==null
                || attempt.inventoryHoldExpiresAt()==null || !now.isBefore(attempt.holdExpiresAt())
                || !now.isBefore(attempt.inventoryHoldExpiresAt())) {
            recordReconciliation(attempt,transactionNo,eventKey,bodyHash,actualAmount,currency,occurred);
            finish(eventId,"RECONCILE");
            return new VnpayIpnResponse("00","Late or extra receipt recorded for reconciliation");
        }
        boolean allActive=Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)=sum(CASE WHEN c.state='HELD' AND c.expires_at>clock_timestamp() THEN 1 ELSE 0 END)
                  AND count(*)>0
                FROM inventory_commitments c JOIN booking_items i ON i.id=c.item_id WHERE i.booking_id=?
                """,Boolean.class,attempt.bookingId()));
        if(!allActive) {
            recordReconciliation(attempt,transactionNo,eventKey,bodyHash,actualAmount,currency,occurred);
            finish(eventId,"RECONCILE");
            return new VnpayIpnResponse("00","Inactive hold receipt recorded for reconciliation");
        }

        capacity.confirmAll(attempt.bookingId());
        var collection=primitives.recordVerifiedReceipt(new PaymentPrimitivesService.VerifiedReceipt(
                attempt.bookingId(),attempt.actorId(),attempt.id(),transactionNo,attempt.expectedAmount(),"VND",
                occurred,bodyHash,PaymentPrimitivesService.ReceiptIntent.REQUIRED_COLLECTION),eventKey);
        if(!"ACCEPTED".equals(collection.disposition())) {
            finish(eventId,"RECONCILE");
            return new VnpayIpnResponse("00","Receipt recorded for reconciliation");
        }
        discounts.consume(attempt.bookingId());
        jdbc.update("UPDATE bookings SET status='CONFIRMED',inventory_version=inventory_version+1 WHERE id=? AND status='PENDING_PAYMENT'",attempt.bookingId());
        jdbc.update("UPDATE payments SET status='SUCCESS',paid_at=?,provider_transaction_no=?,transaction_id=? WHERE booking_id=? AND status='PENDING'",
                Timestamp.from(occurred),transactionNo,transactionNo,attempt.bookingId());
        finish(eventId,"ACCEPTED");
        jdbc.update("""
                INSERT INTO inventory_payment_outbox(booking_id,provider_event_id,event_type,event_key,payment_status)
                VALUES (?,?,'PAYMENT_CONFIRMED',?,'SUCCESS') ON CONFLICT (event_key) DO NOTHING
                """,attempt.bookingId(),eventId,"provider-event:"+eventKey+":confirmed");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                payments.findByProviderTxnRefWithBooking(reference).ifPresent(payment->notifications.bookingStatusChanged(payment.getBooking()));
            }
        });
        return new VnpayIpnResponse("00","Confirm success");
    }

    private ExistingEvent findExisting(String eventKey,String transactionNo,String bodyHash) {
        List<ExistingEvent> rows=jdbc.query("""
                SELECT event_key,body_hash,disposition FROM inventory_provider_events
                WHERE event_key=? OR (? IS NOT NULL AND provider_transaction_no=?)
                ORDER BY id LIMIT 1
                """,(rs,row)->new ExistingEvent(rs.getString("event_key"),rs.getString("body_hash"),rs.getString("disposition")),
                eventKey,transactionNo,transactionNo);
        if(rows.isEmpty()) return null;
        ExistingEvent event=rows.getFirst();
        if(!event.bodyHash().equals(bodyHash)) return new ExistingEvent(event.eventKey(),bodyHash,"RECONCILE");
        return event;
    }
    private void recordReconciliation(AttemptContext attempt,String transactionNo,String eventKey,String bodyHash,
                                      BigDecimal amount,String currency,Instant occurred) {
        if(amount==null || amount.signum()<=0 || amount.remainder(BigDecimal.valueOf(100)).signum()!=0
                || !"VND".equals(currency) || occurred==null || occurred.isAfter(Instant.now())
                || transactionNo==null || transactionNo.isBlank()) return;
        primitives.recordVerifiedReceipt(new PaymentPrimitivesService.VerifiedReceipt(attempt.bookingId(),attempt.actorId(),
                attempt.id(),transactionNo,amount.divide(BigDecimal.valueOf(100)),"VND",occurred,bodyHash,
                PaymentPrimitivesService.ReceiptIntent.RECONCILE_ONLY),eventKey);
    }
    private void finish(long id,String disposition) {
        jdbc.update("UPDATE inventory_provider_events SET disposition=?,processed_at=clock_timestamp() WHERE id=? AND disposition='RECEIVED'",disposition,id);
    }
    private BigDecimal parseWireAmount(String raw) {
        if(raw==null || !raw.matches("[0-9]{1,18}")) return null;
        try { return new BigDecimal(raw); } catch(NumberFormatException exception) { return null; }
    }
    private Instant parsePaidAt(String raw) {
        if(raw==null || !raw.matches("[0-9]{14}")) return null;
        try { return LocalDateTime.parse(raw,DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(); }
        catch(DateTimeParseException exception) { return null; }
    }
    private String digest(Object value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CANONICAL.writeValueAsBytes(value))); }
        catch(Exception exception) { throw new IllegalStateException("Cannot hash provider event",exception); }
    }
    private VnpayIpnResponse response(String disposition) {
        return "ACCEPTED".equals(disposition) ? new VnpayIpnResponse("02","Order already confirmed")
                : new VnpayIpnResponse("00","Provider event already processed");
    }
    private record AttemptContext(long id,long bookingId,long actorId,BigDecimal expectedAmount,String currency,
                                  String merchant,Instant holdExpiresAt,String bookingStatus,Instant inventoryHoldExpiresAt) {}
    private record ExistingEvent(String eventKey,String bodyHash,String disposition) {}
}
