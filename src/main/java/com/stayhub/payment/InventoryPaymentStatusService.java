package com.stayhub.payment;

import com.stayhub.booking.BookingStatus;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.payment.dto.PaymentStatusResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryPaymentStatusService {
    private final JdbcTemplate jdbc;

    public InventoryPaymentStatusService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public PaymentStatusResponse findForGuest(long guestId,long bookingId) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_financial_snapshots WHERE booking_id=?)",Boolean.class,bookingId)))
            return null;
        return jdbc.query("""
                SELECT b.id,b.guest_id,b.status,b.inventory_version,p.status AS payment_status,
                       s.payment_mode,s.due_online,s.balance_due_host,
                       (SELECT coalesce(sum(c.amount),0) FROM payment_collections c WHERE c.booking_id=b.id
                           AND c.source='ONLINE_VERIFIED' AND c.disposition='ACCEPTED') AS online_paid,
                       (SELECT coalesce(sum(c.amount),0) FROM payment_collections c WHERE c.booking_id=b.id
                           AND c.source='HOST_ATTESTED' AND c.disposition='MEMO') AS direct_paid,
                       EXISTS(SELECT 1 FROM inventory_provider_events e WHERE e.booking_id=b.id AND e.disposition='RECONCILE') AS reconcile
                FROM bookings b JOIN booking_financial_snapshots s ON s.booking_id=b.id
                LEFT JOIN payments p ON p.booking_id=b.id
                WHERE b.id=? AND b.guest_id=?
                """, (rs,row)->{
                    BigDecimal balance=rs.getBigDecimal("balance_due_host");
                    BigDecimal direct=rs.getBigDecimal("direct_paid");
                    BigDecimal outstanding=balance.subtract(direct).max(BigDecimal.ZERO);
                    String receiptState=direct.signum()==0?"OUTSTANDING":(outstanding.signum()==0?"PAID_ATTESTED":"PARTIAL_ATTESTED");
                    String onlineState=rs.getBoolean("reconcile") && rs.getBigDecimal("online_paid").signum()==0
                            ? "RECONCILE" : (rs.getBigDecimal("online_paid").signum()>0?"SUCCESS":
                            (rs.getString("payment_status")==null?"PENDING":rs.getString("payment_status")));
                    return PaymentStatusResponse.builder().bookingId(rs.getLong("id"))
                            .bookingStatus(BookingStatus.valueOf(rs.getString("status")))
                            .paymentStatus(rs.getString("payment_status")==null?PaymentStatus.PENDING:PaymentStatus.valueOf(rs.getString("payment_status")))
                            .paymentMode(rs.getString("payment_mode")).onlinePaymentStatus(onlineState)
                            .onlinePaidVerified(rs.getBigDecimal("online_paid")).balanceDueHost(balance)
                            .directPaidAttested(direct).directOutstanding(outstanding).directReceiptStatus(receiptState)
                            .refundStatus("NONE").expectedVersion(rs.getLong("inventory_version")).build();
                },bookingId,guestId).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Booking not found"));
    }

    @Transactional(readOnly=true)
    public PaymentStatusResponse findByAttemptReference(long guestId,String providerRef) {
        List<Long> ids=jdbc.queryForList("SELECT booking_id FROM payment_attempts WHERE provider_ref=?",Long.class,providerRef);
        if(ids.isEmpty()) return null;
        return findForGuest(guestId,ids.getFirst());
    }
}
