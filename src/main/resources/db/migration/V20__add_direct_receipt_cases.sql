-- TSK-111 additive evidence workflow. Direct Host reports remain distinct from
-- provider-verified collections; this migration does not create platform cash.
CREATE TABLE direct_receipt_cases (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    host_id bigint NOT NULL REFERENCES users(id),
    receipt_ref text NOT NULL CHECK (length(receipt_ref) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    amount ledger_vnd NOT NULL CHECK (amount > 0),
    currency text NOT NULL CHECK (currency = 'VND'),
    method text NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER')),
    paid_at timestamptz NOT NULL,
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    state text NOT NULL CHECK (state IN ('ATTESTED','ACKNOWLEDGED','CONTESTED','REFUSED','RESOLVED','REVERSED')),
    guest_disposition text CHECK (guest_disposition IN ('ACKNOWLEDGE','CONTEST','REFUSE')),
    resolution_digest text CHECK (resolution_digest IS NULL OR resolution_digest ~ '^[a-f0-9]{64}$'),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (booking_id, receipt_ref),
    UNIQUE (id, booking_id)
);

CREATE TABLE direct_receipt_case_events (
    id bigserial PRIMARY KEY,
    case_id bigint NOT NULL REFERENCES direct_receipt_cases(id),
    booking_id bigint NOT NULL REFERENCES bookings(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    event_type text NOT NULL CHECK (event_type IN ('ATTESTED','ACKNOWLEDGED','CONTESTED','REFUSED','RESOLVED','REVERSED')),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    expected_version bigint NOT NULL CHECK (expected_version >= 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id, booking_id, idempotency_key),
    FOREIGN KEY (case_id, booking_id) REFERENCES direct_receipt_cases(id, booking_id)
);

CREATE FUNCTION direct_receipt_case_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s record; owner_id bigint; direct_total numeric;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    SELECT p.host_id INTO owner_id FROM bookings b JOIN properties p ON p.id=b.property_id
      WHERE b.id=NEW.booking_id;
    IF NEW.host_id <> owner_id THEN
        RAISE EXCEPTION 'direct receipt host ownership' USING ERRCODE='23514';
    END IF;
    SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=NEW.booking_id;
    SELECT coalesce((SELECT sum(amount) FROM direct_receipt_cases
        WHERE booking_id=NEW.booking_id AND state <> 'REVERSED'),0)
      + coalesce((SELECT sum(amount) FROM payment_collections
        WHERE booking_id=NEW.booking_id AND source='HOST_ATTESTED' AND disposition='MEMO'),0)
      INTO direct_total;
    IF direct_total + NEW.amount > s.balance_due_host THEN
        RAISE EXCEPTION 'direct receipt exceeds outstanding host balance' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER direct_receipt_case_insert_guard BEFORE INSERT ON direct_receipt_cases
  FOR EACH ROW EXECUTE FUNCTION direct_receipt_case_guard();

CREATE FUNCTION direct_receipt_case_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id <> OLD.id OR NEW.booking_id <> OLD.booking_id OR NEW.host_id <> OLD.host_id
       OR NEW.receipt_ref <> OLD.receipt_ref OR NEW.body_hash <> OLD.body_hash
       OR NEW.amount <> OLD.amount OR NEW.currency <> OLD.currency OR NEW.method <> OLD.method
       OR NEW.paid_at <> OLD.paid_at OR NEW.evidence_digest <> OLD.evidence_digest
       OR NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'direct receipt evidence is immutable; append a transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER direct_receipt_case_version BEFORE UPDATE ON direct_receipt_cases
  FOR EACH ROW EXECUTE FUNCTION direct_receipt_case_version_guard();
CREATE TRIGGER immutable_direct_receipt_events BEFORE UPDATE OR DELETE ON direct_receipt_case_events
  FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE INDEX idx_direct_receipt_cases_booking ON direct_receipt_cases(booking_id, id);
CREATE FUNCTION direct_receipt_event_required() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM direct_receipt_case_events e WHERE e.case_id=NEW.id
        AND e.booking_id=NEW.booking_id AND e.event_type=NEW.state) THEN
        RAISE EXCEPTION 'direct receipt state requires append-only event' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER direct_receipt_insert_event AFTER INSERT OR UPDATE ON direct_receipt_cases
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION direct_receipt_event_required();
