CREATE TABLE payout_workflow_cases (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    reservation_id bigint NOT NULL UNIQUE REFERENCES ledger_reservations(id),
    liquidity_snapshot_id bigint NOT NULL REFERENCES refund_liquidity_snapshots(id),
    amount ledger_vnd NOT NULL CHECK (amount>0),
    currency text NOT NULL CHECK (currency='VND'),
    eligibility_digest text NOT NULL CHECK (eligibility_digest ~ '^[a-f0-9]{64}$'),
    state text NOT NULL CHECK (state IN ('REQUESTED','APPROVED','PROCESSING','PAID','FAILED')),
    maker_id bigint NOT NULL REFERENCES users(id),
    checker_id bigint REFERENCES users(id),
    transfer_ref text,
    proof_digest text CHECK (proof_digest IS NULL OR proof_digest ~ '^[a-f0-9]{64}$'),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    version bigint NOT NULL DEFAULT 0 CHECK (version>=0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (booking_id,idempotency_key),
    CHECK (checker_id IS NULL OR checker_id<>maker_id),
    CHECK (state<>'PAID' OR (checker_id IS NOT NULL AND transfer_ref IS NOT NULL AND proof_digest IS NOT NULL))
);
CREATE TABLE payout_workflow_events (
    id bigserial PRIMARY KEY,
    payout_case_id bigint NOT NULL REFERENCES payout_workflow_cases(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    event_type text NOT NULL CHECK (event_type IN ('REQUESTED','APPROVED','PROCESSING','PAID','FAILED')),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    expected_version bigint NOT NULL CHECK (expected_version>=0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id,payout_case_id,idempotency_key)
);
CREATE TABLE payout_liquidity_allocations (
    reservation_id bigint PRIMARY KEY REFERENCES ledger_reservations(id),
    snapshot_id bigint NOT NULL REFERENCES refund_liquidity_snapshots(id),
    amount ledger_vnd NOT NULL CHECK (amount>0),
    state text NOT NULL CHECK (state IN ('RESERVED','CONSUMED','RELEASED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER immutable_payout_workflow_events BEFORE UPDATE OR DELETE ON payout_workflow_events
  FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION payout_workflow_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.booking_id,NEW.reservation_id,NEW.liquidity_snapshot_id,NEW.amount,NEW.currency,
        NEW.eligibility_digest,NEW.maker_id,NEW.idempotency_key,NEW.body_hash,NEW.created_at) IS DISTINCT FROM
       (OLD.id,OLD.booking_id,OLD.reservation_id,OLD.liquidity_snapshot_id,OLD.amount,OLD.currency,
        OLD.eligibility_digest,OLD.maker_id,OLD.idempotency_key,OLD.body_hash,OLD.created_at)
       OR NEW.version<>OLD.version+1
       OR NOT ((OLD.state='REQUESTED' AND NEW.state='APPROVED')
          OR (OLD.state='APPROVED' AND NEW.state IN ('PROCESSING','FAILED'))
          OR (OLD.state='PROCESSING' AND NEW.state IN ('PAID','FAILED'))) THEN
        RAISE EXCEPTION 'invalid or non-append-only payout transition' USING ERRCODE='23514';
    END IF;
    IF NEW.checker_id IS DISTINCT FROM OLD.checker_id AND
       (NEW.checker_id IS NULL OR NEW.checker_id=NEW.maker_id OR NEW.state<>'APPROVED') THEN
        RAISE EXCEPTION 'payout maker/checker separation' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payout_workflow_version_guard BEFORE UPDATE ON payout_workflow_cases
  FOR EACH ROW EXECUTE FUNCTION payout_workflow_version_guard();
CREATE FUNCTION payout_workflow_event_required() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payout_workflow_events e WHERE e.payout_case_id=NEW.id
        AND e.event_type=NEW.state AND e.expected_version=CASE WHEN TG_OP='INSERT' THEN NEW.version ELSE NEW.version-1 END) THEN
        RAISE EXCEPTION 'payout state requires append-only event' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payout_workflow_audit AFTER INSERT OR UPDATE ON payout_workflow_cases
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payout_workflow_event_required();
