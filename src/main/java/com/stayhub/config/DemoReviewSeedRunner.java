package com.stayhub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit demo fixtures, never customer endorsements or payment/notification events. */
@Component
@Profile({"local", "dev", "demo"})
@Order(100)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed-data", name = "enabled", havingValue = "true")
public class DemoReviewSeedRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                DO $$
                DECLARE guest bigint; p record; v_slot integer; bid bigint;
                BEGIN
                    PERFORM pg_advisory_xact_lock(736482109);
                    -- Even without the demo account, never retain a fabricated empty rating.
                    UPDATE properties target SET rating_avg=coalesce(
                        (SELECT round(avg(r.rating),2) FROM reviews r WHERE r.property_id=target.id),0);
                    SELECT id INTO guest FROM users WHERE email='guest@stayhub.com' AND role='GUEST';
                    IF guest IS NULL THEN RETURN; END IF;
                    CREATE TABLE IF NOT EXISTS demo_review_seed_fixtures (
                        property_id bigint NOT NULL REFERENCES properties(id),
                        slot integer NOT NULL CHECK (slot IN (1,2)),
                        booking_id bigint NOT NULL UNIQUE REFERENCES bookings(id),
                        PRIMARY KEY(property_id,slot)
                    );
                    FOR p IN SELECT id,host_id,price_per_night,cleaning_fee FROM properties ORDER BY id LOOP
                        IF p.host_id=guest THEN
                            RAISE EXCEPTION 'Demo guest cannot review own property';
                        END IF;
                        FOR v_slot IN 1..2 LOOP
                            SELECT f.booking_id INTO bid FROM demo_review_seed_fixtures f
                                WHERE f.property_id=p.id AND f.slot=v_slot;
                            IF bid IS NULL THEN
                                -- Dedicated historical LEGACY fixture: no inventory, payment,
                                -- provider call, or status UPDATE. Outbox triggers are UPDATE-only.
                                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,
                                    guests,nightly_price,cleaning_fee,service_fee,subtotal_price,
                                    discount_amount,total_price,status,completed_at,completed_by)
                                VALUES(p.id,guest,DATE '2020-01-01'+v_slot*3,DATE '2020-01-02'+v_slot*3,
                                    1,p.price_per_night,p.cleaning_fee,0,p.price_per_night+p.cleaning_fee,
                                    0,p.price_per_night+p.cleaning_fee,'COMPLETED',
                                    (DATE '2020-01-02'+v_slot*3+time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',p.host_id)
                                RETURNING id INTO bid;
                                INSERT INTO demo_review_seed_fixtures(property_id,slot,booking_id)
                                    VALUES(p.id,v_slot,bid);
                            END IF;
                            INSERT INTO reviews(booking_id,property_id,guest_id,rating,comment)
                            VALUES(bid,p.id,guest,5,CASE v_slot
                                WHEN 1 THEN '[Đánh giá demo] Chỗ nghỉ đẹp, sạch sẽ. Dữ liệu minh họa, không phải nhận xét của khách hàng thực.'
                                ELSE '[Đánh giá demo] Không gian thoải mái, tiện nghi. Dữ liệu minh họa, không phải nhận xét của khách hàng thực.' END)
                            ON CONFLICT (booking_id) DO NOTHING;
                        END LOOP;
                    END LOOP;
                    UPDATE properties target SET rating_avg=coalesce(
                        (SELECT round(avg(r.rating),2) FROM reviews r WHERE r.property_id=target.id),0);
                END $$
                """);
    }
}
