package com.stayhub.admin;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only metrics with each business grain aggregated before joining. */
@Service
public class InventoryFinancialMetricsService {
    public record Metrics(long bookingCount,long activeInventoryBookings,long roomsBooked,long roomNights,
            long configuredSellableRoomNights,BigDecimal grossCommissionDue,BigDecimal onlineVerified,
            BigDecimal hostDirectAttested,BigDecimal discountFunding,BigDecimal commissionReserved,
            BigDecimal commissionEarned,BigDecimal hostHeldPayable,BigDecimal hostDirectOutstanding,
            BigDecimal payoutReserved,BigDecimal onlineRefundPaid,BigDecimal directRefundPaid,
            BigDecimal onlineRefundReserved,BigDecimal directRefundReserved) {}
    private final JdbcTemplate jdbc;
    public InventoryFinancialMetricsService(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}

    @Transactional(readOnly=true)
    public Metrics read() {
        return jdbc.queryForObject("""
                WITH booking AS (
                    SELECT count(*)::bigint AS booking_count FROM bookings
                ),
                inventory AS (
                    SELECT count(DISTINCT b.id)::bigint AS active_bookings,
                      coalesce(sum(bi.quantity),0)::bigint AS rooms,
                      coalesce(sum(bi.quantity*(b.check_out_date-b.check_in_date)),0)::bigint AS room_nights
                    FROM bookings b JOIN booking_items bi ON bi.booking_id=b.id
                    WHERE b.status IN ('CONFIRMED','COMPLETED')
                ),
                capacity AS (
                    SELECT coalesce(sum(total-out_of_order),0)::bigint AS sellable_roomnights FROM type_day_inventory
                ),
                collected AS (
                    SELECT coalesce(sum(amount) FILTER (WHERE source='ONLINE_VERIFIED' AND disposition='ACCEPTED'),0) AS online,
                      coalesce(sum(amount) FILTER (WHERE source='HOST_ATTESTED' AND disposition='MEMO'
                        AND NOT EXISTS (SELECT 1 FROM direct_receipt_cases c WHERE c.booking_id=pc.booking_id AND c.receipt_ref=pc.receipt_ref)),0)
                      + coalesce((SELECT sum(amount) FROM direct_receipt_cases
                        WHERE state IN ('ACKNOWLEDGED','RESOLVED') AND guest_disposition='ACKNOWLEDGE'),0) AS direct
                    FROM payment_collections pc
                ),
                journal AS (
                    SELECT coalesce(sum(l.credit-l.debit) FILTER (WHERE l.account='COMMISSION_RESERVED'),0) AS commission_reserved,
                      coalesce(sum(l.credit-l.debit) FILTER (WHERE l.account='COMMISSION_EARNED'),0) AS commission_earned,
                      coalesce(sum(l.credit-l.debit) FILTER (WHERE l.account='HOST_HELD_PAYABLE'),0) AS host_held,
                      coalesce(sum(l.debit-l.credit) FILTER (WHERE l.account='DISCOUNT_FUNDING_EXPENSE'),0) AS discount_funding
                    FROM ledger_lines l
                ),
                snapshots AS (
                    SELECT coalesce(sum(gross_commission),0) AS gross_due,
                      coalesce(sum(balance_due_host),0) AS direct_due FROM booking_financial_snapshots
                ),
                direct AS (
                    SELECT coalesce(sum(amount) FILTER (WHERE state IN ('ACKNOWLEDGED','RESOLVED')
                        AND guest_disposition='ACKNOWLEDGE'),0) AS received FROM direct_receipt_cases
                ),
                refund AS (
                    SELECT coalesce(sum(amount) FILTER (WHERE state='PAID' AND source='ONLINE_VERIFIED'),0) AS online_paid,
                      coalesce(sum(amount) FILTER (WHERE state='PAID' AND source='HOST_ATTESTED'),0) AS direct_paid,
                      coalesce(sum(amount) FILTER (WHERE state IN ('REQUESTED','APPROVED','PROCESSING') AND source='ONLINE_VERIFIED'),0) AS online_reserved,
                      coalesce(sum(amount) FILTER (WHERE state IN ('REQUESTED','APPROVED','PROCESSING') AND source='HOST_ATTESTED'),0) AS direct_reserved
                    FROM refund_cases
                ),
                payout AS (
                    SELECT coalesce(sum(r.amount) FILTER(WHERE p.reservation_id IS NOT NULL),0) AS paid,
                      coalesce(sum(r.amount) FILTER(WHERE p.reservation_id IS NULL AND (m.action IS NULL OR m.action='CONSUME')),0) AS reserved
                    FROM ledger_reservations r LEFT JOIN payout_execution_proofs p ON p.reservation_id=r.id
                      LEFT JOIN ledger_reserve_movements m ON m.reservation_id=r.id
                    WHERE r.kind='PAYOUT' AND r.source='ONLINE_VERIFIED'
                )
                SELECT booking.booking_count,inventory.active_bookings,inventory.rooms,inventory.room_nights,
                    capacity.sellable_roomnights,snapshots.gross_due,collected.online,collected.direct,
                    journal.discount_funding,journal.commission_reserved,journal.commission_earned,
                    journal.host_held-payout.paid,snapshots.direct_due-direct.received,payout.reserved,
                    refund.online_paid,refund.direct_paid,refund.online_reserved,refund.direct_reserved
                FROM booking,inventory,capacity,collected,journal,snapshots,direct,refund,payout
                """,(r,n)->new Metrics(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getLong(5),
                    r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),r.getBigDecimal(9),r.getBigDecimal(10),
                    r.getBigDecimal(11),r.getBigDecimal(12),r.getBigDecimal(13),r.getBigDecimal(14),
                    r.getBigDecimal(15),r.getBigDecimal(16),r.getBigDecimal(17),r.getBigDecimal(18)));
    }
}
