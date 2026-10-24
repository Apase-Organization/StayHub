package com.stayhub.inventory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic campaign/user cap reservation; provider acceptance is the only consume path. */
@Service
public class InventoryDiscountReservationService {
    private final JdbcTemplate jdbc;

    public InventoryDiscountReservationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional
    public void reserve(long bookingId,long guestId,long codeId,String code,BigDecimal amount,Instant expiresAt) {
        if(amount==null || amount.signum()<=0) return;
        jdbc.update("""
                INSERT INTO inventory_discount_reservations(booking_id,discount_code_id,guest_id,code_snapshot,amount,state,hold_expires_at)
                VALUES (?,?,?,?,?::inventory_vnd,'RESERVED',?)
                """,bookingId,codeId,guestId,code,amount,Timestamp.from(expiresAt));
    }

    @Transactional
    public void consume(long bookingId) {
        jdbc.update("UPDATE inventory_discount_reservations SET state='CONSUMED',version=version+1 WHERE booking_id=? AND state='RESERVED'",bookingId);
    }

    @Transactional
    public void release(long bookingId) {
        jdbc.update("UPDATE inventory_discount_reservations SET state='RELEASED',version=version+1 WHERE booking_id=? AND state='RESERVED'",bookingId);
    }
}
