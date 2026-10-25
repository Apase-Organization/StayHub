ALTER TABLE bookings ADD COLUMN completed_at timestamptz;
ALTER TABLE bookings ADD COLUMN completed_by bigint REFERENCES users(id);
ALTER TABLE bookings ADD CONSTRAINT chk_booking_completion_pair
    CHECK ((completed_at IS NULL AND completed_by IS NULL) OR (completed_at IS NOT NULL AND completed_by IS NOT NULL));

CREATE FUNCTION inventory_completion_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE expected integer; verified integer; disputed integer; host bigint; checkout_at timestamptz;
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status AND NEW.status='COMPLETED'
       AND EXISTS (SELECT 1 FROM booking_financial_snapshots WHERE booking_id=NEW.id) THEN
        SELECT p.host_id,(NEW.check_out_date + time '11:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
            INTO host,checkout_at FROM properties p WHERE p.id=NEW.property_id;
        IF NEW.completed_by<>host OR clock_timestamp()<checkout_at THEN
            RAISE EXCEPTION 'inventory completion actor/time guard' USING ERRCODE='23514';
        END IF;
        NEW.completed_at:=clock_timestamp();
        SELECT count(*) INTO expected FROM room_allocations ra JOIN booking_items bi ON bi.id=ra.item_id
            WHERE bi.booking_id=NEW.id;
        SELECT count(*) INTO verified FROM booking_arrivals a JOIN booking_items bi ON bi.id=a.item_id
            WHERE bi.booking_id=NEW.id AND a.state IN ('ACKNOWLEDGED','ADMIN_VERIFIED') AND NOT a.needs_review;
        SELECT count(*) INTO disputed FROM booking_settlement_blocks WHERE booking_id=NEW.id AND active;
        IF expected=0 OR expected<>verified OR disputed>0
           OR EXISTS (SELECT 1 FROM booking_arrivals a JOIN booking_items bi ON bi.id=a.item_id
                WHERE bi.booking_id=NEW.id AND a.state IN ('CONTESTED','REVERSED'))
           OR EXISTS (SELECT 1 FROM refund_cases WHERE booking_id=NEW.id AND state IN ('REQUESTED','APPROVED','PROCESSING')) THEN
            RAISE EXCEPTION 'inventory completion requires all verified rooms and resolved cases' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_completion_guard BEFORE UPDATE OF status ON bookings
    FOR EACH ROW EXECUTE FUNCTION inventory_completion_guard();

CREATE FUNCTION inventory_completion_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.completed_at IS NOT NULL AND (NEW.completed_at,NEW.completed_by) IS DISTINCT FROM (OLD.completed_at,OLD.completed_by) THEN
        RAISE EXCEPTION 'completed booking evidence is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_completion_immutable BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION inventory_completion_immutable();
