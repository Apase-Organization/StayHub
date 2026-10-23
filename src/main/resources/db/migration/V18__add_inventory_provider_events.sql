CREATE TABLE inventory_provider_events (
    id bigserial PRIMARY KEY,
    attempt_id bigint NOT NULL,
    booking_id bigint NOT NULL,
    provider_ref varchar(200) NOT NULL,
    provider_transaction_no varchar(200),
    event_key char(64) NOT NULL UNIQUE CHECK (event_key ~ '^[a-f0-9]{64}$'),
    body_hash char(64) NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    wire_amount varchar(40),
    wire_currency varchar(3),
    response_code varchar(20),
    transaction_status varchar(20),
    disposition varchar(20) NOT NULL CHECK (disposition IN ('RECEIVED','FAILED','ACCEPTED','RECONCILE')),
    received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    processed_at timestamptz,
    FOREIGN KEY (attempt_id,booking_id) REFERENCES payment_attempts(id,booking_id),
    CHECK ((disposition='RECEIVED' AND processed_at IS NULL)
        OR (disposition<>'RECEIVED' AND processed_at IS NOT NULL))
);
CREATE INDEX idx_inventory_provider_events_booking ON inventory_provider_events(booking_id,received_at);
CREATE UNIQUE INDEX uq_inventory_provider_transaction_no ON inventory_provider_events(provider_transaction_no)
    WHERE provider_transaction_no IS NOT NULL;

CREATE FUNCTION inventory_provider_event_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.attempt_id,NEW.booking_id,NEW.provider_ref,NEW.provider_transaction_no,NEW.event_key,
        NEW.body_hash,NEW.wire_amount,NEW.wire_currency,NEW.response_code,NEW.transaction_status,NEW.received_at)
       IS DISTINCT FROM
       (OLD.attempt_id,OLD.booking_id,OLD.provider_ref,OLD.provider_transaction_no,OLD.event_key,
        OLD.body_hash,OLD.wire_amount,OLD.wire_currency,OLD.response_code,OLD.transaction_status,OLD.received_at)
       OR NOT (NEW.disposition=OLD.disposition OR (OLD.disposition='RECEIVED' AND NEW.disposition IN ('FAILED','ACCEPTED','RECONCILE')))
       THEN RAISE EXCEPTION 'provider event is immutable except one processing transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_provider_event_guard BEFORE UPDATE ON inventory_provider_events
    FOR EACH ROW EXECUTE FUNCTION inventory_provider_event_guard();
CREATE FUNCTION inventory_provider_event_no_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'provider event history cannot be deleted' USING ERRCODE='23514'; END $$;
CREATE TRIGGER inventory_provider_event_no_delete BEFORE DELETE ON inventory_provider_events
    FOR EACH ROW EXECUTE FUNCTION inventory_provider_event_no_delete();
