package com.stayhub.inventory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal capacity primitives, not a checkout/payment or authorization API.
 * Callers compose selection creation, policy validation and lifecycle changes in
 * the SAME transaction. Missing calendar rows always fail closed; this service
 * never guesses stock from bedrooms or creates inventory implicitly.
 */
@Service
public class InventoryCapacityService {
    private final JdbcTemplate jdbc;

    public InventoryCapacityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private record Day(long type, LocalDate date) {}

    private void lockBookingAndDays(long bookingId) {
        jdbc.queryForObject("SELECT id FROM bookings WHERE id=? FOR UPDATE", Long.class, bookingId);
        // Lock every type first, then every plan, each in ascending id order.
        // SHARE (not KEY SHARE) linearizes admission with non-key status/limit
        // updates. No ACTIVE filter here: existing holds may convert/release
        // after archive; admission status is rechecked by the DB INSERT trigger.
        List<Long> types = jdbc.queryForList("""
                SELECT DISTINCT room_type_id FROM booking_items WHERE booking_id=? ORDER BY room_type_id
                """,Long.class,bookingId);
        for (long type : types) {
            jdbc.queryForObject("SELECT id FROM room_types WHERE id=? FOR SHARE",Long.class,type);
        }
        List<Long> plans = jdbc.queryForList("""
                SELECT DISTINCT rate_plan_id FROM booking_items WHERE booking_id=? ORDER BY rate_plan_id
                """,Long.class,bookingId);
        for (long plan : plans) {
            jdbc.queryForObject("SELECT id FROM rate_plans WHERE id=? FOR SHARE",Long.class,plan);
        }
        List<Day> days = jdbc.query("""
                SELECT DISTINCT i.room_type_id, d::date AS stay_date
                FROM booking_items i JOIN bookings b ON b.id=i.booking_id
                CROSS JOIN LATERAL generate_series(b.check_in_date::timestamp,
                    (b.check_out_date-1)::timestamp, interval '1 day') d
                WHERE i.booking_id=? ORDER BY i.room_type_id,stay_date
                """, (r,n) -> new Day(r.getLong(1),r.getObject(2,LocalDate.class)),bookingId);
        if (days.isEmpty()) throw new IllegalStateException("inventory selection missing");
        for (Day day : days) {
            // queryForObject rejects missing rows, never unlimited availability.
            jdbc.queryForObject("""
                    SELECT version FROM type_day_inventory
                    WHERE room_type_id=? AND stay_date=? FOR UPDATE
                    """,Long.class,day.type(),day.date());
        }
    }

    /** Reserve every item/night or rollback all. Replays require caller idempotency;
     * duplicate item/night keys reject rather than silently changing a deadline. */
    @Transactional
    public void holdAll(long bookingId, Instant originalExpiresAt) {
        if (originalExpiresAt == null) throw new IllegalArgumentException("deadline required");
        lockBookingAndDays(bookingId);
        jdbc.update("""
                INSERT INTO inventory_commitments(item_id,room_type_id,stay_date,quantity,state,expires_at)
                SELECT i.id,i.room_type_id,d::date,i.quantity,'HELD',?
                FROM booking_items i JOIN bookings b ON b.id=i.booking_id
                CROSS JOIN LATERAL generate_series(b.check_in_date::timestamp,
                    (b.check_out_date-1)::timestamp, interval '1 day') d
                WHERE i.booking_id=? ORDER BY i.room_type_id,d,i.id
                """,Timestamp.from(originalExpiresAt),bookingId);
    }

    /** No new confirmation rows, quantity changes or stock counters. */
    @Transactional
    public void confirmAll(long bookingId) {
        lockBookingAndDays(bookingId);
        Integer expected = jdbc.queryForObject("""
                SELECT sum(b.check_out_date-b.check_in_date)::integer
                FROM booking_items i JOIN bookings b ON b.id=i.booking_id WHERE b.id=?
                """,Integer.class,bookingId);
        Integer held = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_commitments c JOIN booking_items i ON i.id=c.item_id
                WHERE i.booking_id=? AND c.state='HELD'
                """,Integer.class,bookingId);
        if (!expected.equals(held)) throw new IllegalStateException("all-item active hold required");
        jdbc.update("""
                UPDATE inventory_commitments SET state='CONFIRMED'
                WHERE item_id IN (SELECT id FROM booking_items WHERE booking_id=?) AND state='HELD'
                """,bookingId);
    }

    /** Whole-booking release; repeated release is a no-op. Caller decides whether
     * expiry, cancellation or another approved lifecycle guard permits release. */
    @Transactional
    public void releaseAll(long bookingId) {
        lockBookingAndDays(bookingId);
        jdbc.update("""
                UPDATE inventory_commitments SET state='RELEASED'
                WHERE item_id IN (SELECT id FROM booking_items WHERE booking_id=?)
                  AND state IN ('HELD','CONFIRMED')
                """,bookingId);
    }

    /** Single-day host/calendar primitive; DB enforces OOO and active commitments.
     * Future batch writers must sort all (type,date) keys before invoking updates. */
    @Transactional
    public void updateDay(long typeId, LocalDate date, int total, int outOfOrder,
                          boolean stopSale, long expectedVersion) {
        int changed = jdbc.update("""
                UPDATE type_day_inventory SET total=?,out_of_order=?,stop_sale=?,version=version+1
                WHERE room_type_id=? AND stay_date=? AND version=?
                """,total,outOfOrder,stopSale,typeId,date,expectedVersion);
        if (changed!=1) throw new IllegalStateException("inventory calendar missing or version conflict");
    }
}
