ALTER TABLE discount_codes
    ADD COLUMN reserved_count integer NOT NULL DEFAULT 0 CHECK (reserved_count >= 0),
    ADD CONSTRAINT chk_discount_codes_reserved_quota
        CHECK (usage_limit IS NULL OR used_count + reserved_count <= usage_limit);

CREATE TABLE inventory_quotes (
    quote_id varchar(36) PRIMARY KEY,
    actor_id bigint NOT NULL REFERENCES users(id),
    property_id bigint NOT NULL REFERENCES properties(id),
    policy_version varchar(100) NOT NULL CHECK (policy_version='s0-policy-v1'),
    fixture_version varchar(100) NOT NULL CHECK (fixture_version='s0-fixtures-v1'),
    selection_hash char(64) NOT NULL CHECK (selection_hash ~ '^[a-f0-9]{64}$'),
    quote_hash char(64) NOT NULL CHECK (quote_hash ~ '^[a-f0-9]{64}$'),
    selection jsonb NOT NULL,
    amounts jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    consumed_booking_id bigint UNIQUE REFERENCES bookings(id),
    consumed_at timestamptz,
    CHECK ((consumed_booking_id IS NULL AND consumed_at IS NULL)
        OR (consumed_booking_id IS NOT NULL AND consumed_at IS NOT NULL))
);
CREATE INDEX idx_inventory_quotes_actor_expiry ON inventory_quotes(actor_id,expires_at);

CREATE FUNCTION inventory_quote_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.quote_id,NEW.actor_id,NEW.property_id,NEW.policy_version,NEW.fixture_version,
        NEW.selection_hash,NEW.quote_hash,NEW.selection,NEW.amounts,NEW.created_at,NEW.expires_at)
       IS DISTINCT FROM
       (OLD.quote_id,OLD.actor_id,OLD.property_id,OLD.policy_version,OLD.fixture_version,
        OLD.selection_hash,OLD.quote_hash,OLD.selection,OLD.amounts,OLD.created_at,OLD.expires_at)
       OR (OLD.consumed_booking_id IS NOT NULL AND
           (NEW.consumed_booking_id,NEW.consumed_at) IS DISTINCT FROM (OLD.consumed_booking_id,OLD.consumed_at)) THEN
        RAISE EXCEPTION 'immutable quote binding' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_quote_immutable BEFORE UPDATE ON inventory_quotes
    FOR EACH ROW EXECUTE FUNCTION inventory_quote_immutable();

CREATE TABLE inventory_discount_reservations (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL UNIQUE REFERENCES bookings(id),
    discount_code_id bigint NOT NULL REFERENCES discount_codes(id),
    guest_id bigint NOT NULL REFERENCES users(id),
    code_snapshot varchar(50) NOT NULL,
    amount inventory_vnd NOT NULL CHECK (amount > 0),
    state varchar(20) NOT NULL CHECK (state IN ('RESERVED','CONSUMED','RELEASED')),
    hold_expires_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (id,booking_id)
);
CREATE UNIQUE INDEX uq_inventory_discount_user_use ON inventory_discount_reservations(guest_id,discount_code_id)
    WHERE state IN ('RESERVED','CONSUMED');
CREATE INDEX idx_inventory_discount_expiry ON inventory_discount_reservations(hold_expires_at,id)
    WHERE state='RESERVED';

CREATE FUNCTION inventory_discount_reservation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE d record; guest bigint;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    SELECT guest_id INTO guest FROM bookings WHERE id=NEW.booking_id;
    IF guest IS DISTINCT FROM NEW.guest_id THEN
        RAISE EXCEPTION 'discount guest booking scope' USING ERRCODE='23514'; END IF;
    PERFORM 1 FROM discount_codes WHERE id=NEW.discount_code_id FOR UPDATE;
    SELECT active,starts_at,ends_at,usage_limit,used_count,reserved_count
        INTO d FROM discount_codes WHERE id=NEW.discount_code_id;
    IF NOT FOUND OR NOT d.active OR clock_timestamp()<d.starts_at OR clock_timestamp()>d.ends_at
        OR (d.usage_limit IS NOT NULL AND d.used_count+d.reserved_count>=d.usage_limit)
        OR NEW.hold_expires_at<=clock_timestamp() THEN
        RAISE EXCEPTION 'discount campaign unavailable' USING ERRCODE='23514'; END IF;
    UPDATE discount_codes SET reserved_count=reserved_count+1 WHERE id=NEW.discount_code_id;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_discount_reservation_insert BEFORE INSERT ON inventory_discount_reservations
    FOR EACH ROW EXECUTE FUNCTION inventory_discount_reservation_guard();

CREATE FUNCTION inventory_discount_reservation_transition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.booking_id,NEW.discount_code_id,NEW.guest_id,NEW.code_snapshot,NEW.amount,NEW.hold_expires_at)
       IS DISTINCT FROM (OLD.booking_id,OLD.discount_code_id,OLD.guest_id,OLD.code_snapshot,OLD.amount,OLD.hold_expires_at)
       OR NOT (NEW.state=OLD.state OR (OLD.state='RESERVED' AND NEW.state IN ('CONSUMED','RELEASED'))) THEN
        RAISE EXCEPTION 'invalid discount reservation transition' USING ERRCODE='23514'; END IF;
    IF OLD.state='RESERVED' AND NEW.state='CONSUMED' THEN
        UPDATE discount_codes SET reserved_count=reserved_count-1,used_count=used_count+1 WHERE id=OLD.discount_code_id;
    ELSIF OLD.state='RESERVED' AND NEW.state='RELEASED' THEN
        UPDATE discount_codes SET reserved_count=reserved_count-1 WHERE id=OLD.discount_code_id;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_discount_reservation_update BEFORE UPDATE ON inventory_discount_reservations
    FOR EACH ROW EXECUTE FUNCTION inventory_discount_reservation_transition();
CREATE FUNCTION inventory_discount_reservation_no_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'discount reservation history cannot be deleted' USING ERRCODE='23514'; END $$;
CREATE TRIGGER inventory_discount_reservation_delete BEFORE DELETE ON inventory_discount_reservations
    FOR EACH ROW EXECUTE FUNCTION inventory_discount_reservation_no_delete();
