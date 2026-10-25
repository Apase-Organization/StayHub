ALTER TABLE payment_collection_allocations
    ADD COLUMN gross_amount inventory_vnd,
    ADD COLUMN discount_amount inventory_vnd,
    ADD CONSTRAINT chk_payment_collection_allocation_amounts CHECK (
        (gross_amount IS NULL AND discount_amount IS NULL)
        OR (gross_amount IS NOT NULL AND discount_amount IS NOT NULL
            AND gross_amount>=net_amount AND gross_amount-discount_amount=net_amount)
    );

ALTER TABLE refund_cases ADD COLUMN policy_basis text NOT NULL DEFAULT 'FULL_COLLECTED_FREE_WINDOW'
    CHECK (policy_basis IN ('FULL_COLLECTED_FREE_WINDOW','FULL_COLLECTED_HOST_UNABLE','LATE_UNUSED_CLEANING'));
ALTER TABLE refund_cases ADD COLUMN commission_adjustment ledger_vnd NOT NULL DEFAULT 0;
ALTER TABLE refund_cases ALTER COLUMN policy_basis DROP DEFAULT;

CREATE TABLE refund_charge_allocations (
    id bigserial PRIMARY KEY,
    refund_case_id bigint NOT NULL REFERENCES refund_cases(id),
    booking_id bigint NOT NULL REFERENCES bookings(id),
    source text NOT NULL CHECK (source IN ('ONLINE_VERIFIED','HOST_ATTESTED')),
    line_key text NOT NULL CHECK (length(line_key) BETWEEN 1 AND 200),
    charge_type text NOT NULL CHECK (charge_type IN ('ROOM','CLEANING','TAX')),
    gross_amount inventory_vnd NOT NULL CHECK (gross_amount>=0),
    discount_amount inventory_vnd NOT NULL CHECK (discount_amount>=0 AND discount_amount<=gross_amount),
    net_amount inventory_vnd NOT NULL CHECK (net_amount=gross_amount-discount_amount),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (refund_case_id,line_key)
);
CREATE INDEX idx_refund_charge_allocations_booking_source ON refund_charge_allocations(booking_id,source,charge_type);
CREATE TRIGGER immutable_refund_charge_allocations BEFORE UPDATE OR DELETE ON refund_charge_allocations
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION refund_case_allocation_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE allocated numeric;
BEGIN
    SELECT coalesce(sum(net_amount),0) INTO allocated FROM refund_charge_allocations
      WHERE refund_case_id=NEW.id AND booking_id=NEW.booking_id AND source=NEW.source;
    IF allocated<>NEW.amount THEN
        RAISE EXCEPTION 'refund request requires exact source/charge allocation' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER refund_case_allocation_complete AFTER INSERT ON refund_cases
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION refund_case_allocation_complete();
