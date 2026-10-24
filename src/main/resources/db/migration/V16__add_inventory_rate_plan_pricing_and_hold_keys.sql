-- Minimal server-owned nightly price input for inventory booking snapshots.
ALTER TABLE rate_plans ADD COLUMN nightly_price inventory_vnd;
ALTER TABLE rate_plans ADD CONSTRAINT chk_rate_plan_nightly_price
    CHECK (nightly_price IS NULL OR nightly_price > 0);

ALTER TABLE bookings
    ADD COLUMN inventory_currency varchar(3) NOT NULL DEFAULT 'VND' CHECK (inventory_currency='VND'),
    ADD COLUMN payment_mode varchar(20),
    ADD COLUMN inventory_policy_version varchar(100),
    ADD COLUMN inventory_idempotency_key varchar(100),
    ADD COLUMN inventory_request_hash char(64),
    ADD COLUMN inventory_hold_expires_at timestamptz,
    ADD COLUMN inventory_version bigint NOT NULL DEFAULT 0 CHECK (inventory_version >= 0),
    ADD CONSTRAINT chk_bookings_payment_mode
        CHECK (payment_mode IS NULL OR payment_mode IN ('FULL_PREPAY','HYBRID_DEPOSIT')),
    ADD CONSTRAINT uq_booking_inventory_key UNIQUE (guest_id,inventory_idempotency_key),
    ADD CONSTRAINT chk_booking_inventory_metadata CHECK (
        (inventory_idempotency_key IS NULL AND inventory_request_hash IS NULL
            AND inventory_hold_expires_at IS NULL AND inventory_policy_version IS NULL AND payment_mode IS NULL)
        OR
        (inventory_idempotency_key IS NOT NULL AND inventory_request_hash IS NOT NULL
            AND inventory_request_hash ~ '^[0-9a-f]{64}$'
            AND inventory_hold_expires_at IS NOT NULL AND inventory_policy_version IS NOT NULL AND payment_mode IS NOT NULL)
    );

CREATE INDEX idx_inventory_expiring_bookings ON bookings(inventory_hold_expires_at,id)
    WHERE status='PENDING_PAYMENT' AND inventory_hold_expires_at IS NOT NULL;
