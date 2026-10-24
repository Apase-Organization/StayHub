CREATE TABLE inventory_payment_outbox (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES bookings(id),
    provider_event_id bigint REFERENCES inventory_provider_events(id),
    event_type varchar(40) NOT NULL CHECK (event_type IN ('PAYMENT_CONFIRMED','PAYMENT_FAILED','PAYMENT_EXPIRED')),
    event_key varchar(200) NOT NULL UNIQUE,
    payment_status varchar(20) NOT NULL CHECK (payment_status IN ('SUCCESS','FAILED','EXPIRED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    dispatched_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error_code varchar(80)
);
CREATE INDEX idx_inventory_payment_outbox_pending ON inventory_payment_outbox(created_at,id)
    WHERE dispatched_at IS NULL;
