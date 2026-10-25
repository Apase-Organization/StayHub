-- TSK-112/085 append-only operational evidence. None of these rows enables
-- production money movement; legal/operations gates default closed.
CREATE TABLE booking_arrivals (
    booking_id bigint NOT NULL REFERENCES bookings(id),
    item_id bigint NOT NULL,
    room_ordinal integer NOT NULL,
    host_id bigint NOT NULL REFERENCES users(id),
    actual_at timestamptz NOT NULL,
    timezone text NOT NULL CHECK (timezone='Asia/Ho_Chi_Minh'),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    state text NOT NULL CHECK (state IN ('REPORTED','ACKNOWLEDGED','CONTESTED','ADMIN_VERIFIED','REVERSED')),
    guest_id bigint REFERENCES users(id),
    admin_id bigint REFERENCES users(id),
    needs_review boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0 CHECK (version>=0),
    PRIMARY KEY (booking_id,item_id,room_ordinal),
    FOREIGN KEY (item_id,room_ordinal) REFERENCES room_allocations(item_id,room_ordinal)
);
CREATE TABLE booking_arrival_events (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL,
    item_id bigint NOT NULL,
    room_ordinal integer NOT NULL,
    actor_id bigint NOT NULL REFERENCES users(id),
    event_type text NOT NULL CHECK (event_type IN ('REPORTED','ACKNOWLEDGED','CONTESTED','ADMIN_VERIFIED','REVERSED')),
    actual_at timestamptz,
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    expected_version bigint NOT NULL CHECK (expected_version>=0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id,booking_id,idempotency_key),
    FOREIGN KEY (booking_id,item_id,room_ordinal) REFERENCES booking_arrivals(booking_id,item_id,room_ordinal)
);
CREATE TABLE private_evidence_access_log (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES bookings(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    resource_type text NOT NULL CHECK (resource_type IN ('DIRECT_RECEIPT','ARRIVAL','PAYOUT_RECOVERY','REFUND')),
    resource_id bigint NOT NULL CHECK (resource_id>0),
    access_reason text NOT NULL CHECK (access_reason IN ('READ','REVIEW','RESOLVE')),
    accessed_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER immutable_private_evidence_access BEFORE UPDATE OR DELETE ON private_evidence_access_log
  FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION booking_arrival_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.booking_id,NEW.item_id,NEW.room_ordinal,NEW.host_id,NEW.actual_at,NEW.timezone,
        NEW.evidence_digest) IS DISTINCT FROM
       (OLD.booking_id,OLD.item_id,OLD.room_ordinal,OLD.host_id,OLD.actual_at,OLD.timezone,
        OLD.evidence_digest)
       OR NEW.version<>OLD.version+1
       OR (NEW.needs_review IS DISTINCT FROM OLD.needs_review AND (NEW.state<>'ADMIN_VERIFIED' OR NEW.admin_id IS NULL))
        OR NOT ((OLD.state='REPORTED' AND NEW.state IN ('ACKNOWLEDGED','CONTESTED','ADMIN_VERIFIED'))
           OR (OLD.state='ACKNOWLEDGED' AND NEW.state='CONTESTED')
           OR (OLD.state IN ('CONTESTED','ACKNOWLEDGED') AND NEW.state='ADMIN_VERIFIED')) THEN
        RAISE EXCEPTION 'invalid or non-append-only arrival transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER booking_arrival_version_guard BEFORE UPDATE ON booking_arrivals
  FOR EACH ROW EXECUTE FUNCTION booking_arrival_version_guard();

CREATE TABLE booking_settlement_blocks (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES bookings(id),
    reason_code text NOT NULL CHECK (reason_code IN ('DISPUTE','REFUND','RECONCILIATION','LEGAL','LIQUIDITY','OPERATIONS')),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    active boolean NOT NULL DEFAULT true,
    actor_id bigint NOT NULL REFERENCES users(id),
    resolution_actor_id bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    resolved_at timestamptz,
    resolution_digest text CHECK (resolution_digest IS NULL OR resolution_digest ~ '^[a-f0-9]{64}$'),
    CHECK ((active AND resolved_at IS NULL AND resolution_digest IS NULL)
        OR (NOT active AND resolved_at IS NOT NULL AND resolution_digest IS NOT NULL AND resolution_actor_id IS NOT NULL))
);
CREATE INDEX idx_booking_settlement_blocks_active ON booking_settlement_blocks(booking_id) WHERE active;
CREATE TABLE booking_settlement_block_events (
    id bigserial PRIMARY KEY,
    block_id bigint NOT NULL REFERENCES booking_settlement_blocks(id),
    booking_id bigint NOT NULL REFERENCES bookings(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    event_type text NOT NULL CHECK (event_type IN ('OPENED','RESOLVED')),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    expected_version bigint NOT NULL CHECK (expected_version>=0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id,booking_id,idempotency_key)
);
-- TSK-087 will append these only after an actual externally executed transfer.
-- A ledger reserve CONSUME alone is accounting evidence, not a bank payout.
CREATE TABLE payout_execution_proofs (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    reservation_id bigint NOT NULL UNIQUE REFERENCES ledger_reservations(id),
    transfer_ref text NOT NULL CHECK (length(transfer_ref) BETWEEN 1 AND 200),
    proof_digest text NOT NULL CHECK (proof_digest ~ '^[a-f0-9]{64}$'),
    maker_id bigint NOT NULL REFERENCES users(id),
    checker_id bigint NOT NULL REFERENCES users(id),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    executed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (maker_id,idempotency_key),
    CHECK (maker_id<>checker_id)
);
CREATE TRIGGER immutable_payout_execution_proofs BEFORE UPDATE OR DELETE ON payout_execution_proofs
  FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION payout_execution_proof_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r record;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    SELECT * INTO r FROM ledger_reservations WHERE id=NEW.reservation_id AND booking_id=NEW.booking_id;
    IF NOT FOUND OR r.kind<>'PAYOUT' OR r.source<>'ONLINE_VERIFIED' OR r.actor_id<>NEW.maker_id
       OR NEW.maker_id=NEW.checker_id OR NOT EXISTS (SELECT 1 FROM ledger_reserve_movements
           WHERE reservation_id=NEW.reservation_id AND action='CONSUME') THEN
        RAISE EXCEPTION 'payout proof must bind consumed platform-held payout with separated checker' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payout_execution_proof_guard BEFORE INSERT ON payout_execution_proofs
  FOR EACH ROW EXECUTE FUNCTION payout_execution_proof_guard();
CREATE TABLE payout_recovery_cases (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    settlement_block_id bigint NOT NULL REFERENCES booking_settlement_blocks(id),
    payout_reservation_id bigint NOT NULL UNIQUE REFERENCES ledger_reservations(id),
    exposure_amount ledger_vnd NOT NULL CHECK (exposure_amount>0),
    source text NOT NULL CHECK (source='ONLINE_VERIFIED'),
    owner_role text NOT NULL DEFAULT 'OperationsFinance' CHECK (owner_role='OperationsFinance'),
    owner_id bigint REFERENCES users(id),
    state text NOT NULL CHECK (state IN ('OPEN','RESOLVED')),
    outcome text CHECK (outcome IN ('RECONCILED_NO_LOSS','MANUAL_RECOVERY_DOCUMENTED')),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    resolution_digest text CHECK (resolution_digest IS NULL OR resolution_digest ~ '^[a-f0-9]{64}$'),
    opened_by bigint NOT NULL REFERENCES users(id),
    version bigint NOT NULL DEFAULT 0 CHECK (version>=0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    resolved_at timestamptz,
    CHECK ((state='OPEN' AND outcome IS NULL AND resolved_at IS NULL AND resolution_digest IS NULL)
        OR (state='RESOLVED' AND outcome IS NOT NULL AND resolved_at IS NOT NULL AND resolution_digest IS NOT NULL))
);
CREATE TABLE payout_recovery_events (
    id bigserial PRIMARY KEY,
    recovery_case_id bigint NOT NULL REFERENCES payout_recovery_cases(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    event_type text NOT NULL CHECK (event_type IN ('OPENED','ASSIGNED','RESOLVED')),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    expected_version bigint NOT NULL CHECK (expected_version>=0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id,recovery_case_id,idempotency_key)
);
CREATE TABLE booking_release_clearance (
    booking_id bigint PRIMARY KEY REFERENCES booking_financial_snapshots(booking_id),
    finance_legal boolean NOT NULL DEFAULT false,
    operations boolean NOT NULL DEFAULT false,
    liquidity boolean NOT NULL DEFAULT false,
    evidence_digest text CHECK (evidence_digest IS NULL OR evidence_digest ~ '^[a-f0-9]{64}$'),
    updated_by bigint REFERENCES users(id),
    updated_at timestamptz,
    CHECK ((finance_legal AND operations AND liquidity) = (evidence_digest IS NOT NULL AND updated_by IS NOT NULL AND updated_at IS NOT NULL))
);
CREATE TABLE refund_sla_config (
    id bigserial PRIMARY KEY,
    minimum_business_days integer NOT NULL CHECK (minimum_business_days=5),
    maximum_business_days integer NOT NULL CHECK (maximum_business_days=10),
    timezone text NOT NULL CHECK (timezone='Asia/Ho_Chi_Minh'),
    calendar_version text NOT NULL CHECK (length(calendar_version) BETWEEN 1 AND 100),
    signer_id bigint NOT NULL REFERENCES users(id),
    signed_evidence_digest text NOT NULL CHECK (signed_evidence_digest ~ '^[a-f0-9]{64}$'),
    active boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX uq_active_refund_sla_config ON refund_sla_config(active) WHERE active;
CREATE TABLE refund_liquidity_snapshots (
    id bigserial PRIMARY KEY,
    available_online ledger_vnd NOT NULL,
    as_of timestamptz NOT NULL,
    actor_id bigint NOT NULL REFERENCES users(id),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_refund_liquidity_latest ON refund_liquidity_snapshots(as_of DESC,id DESC);
CREATE TABLE refund_liquidity_allocations (
    reservation_id bigint PRIMARY KEY REFERENCES ledger_reservations(id),
    snapshot_id bigint NOT NULL REFERENCES refund_liquidity_snapshots(id),
    amount ledger_vnd NOT NULL CHECK (amount>0),
    state text NOT NULL CHECK (state IN ('RESERVED','CONSUMED','RELEASED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE refund_cases (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    source text NOT NULL CHECK (source IN ('ONLINE_VERIFIED','HOST_ATTESTED')),
    amount ledger_vnd NOT NULL CHECK (amount>0),
    currency text NOT NULL CHECK (currency='VND'),
    policy_digest text NOT NULL CHECK (policy_digest ~ '^[a-f0-9]{64}$'),
    reservation_id bigint NOT NULL UNIQUE REFERENCES ledger_reservations(id),
    sla_config_id bigint NOT NULL REFERENCES refund_sla_config(id),
    state text NOT NULL CHECK (state IN ('REQUESTED','APPROVED','PROCESSING','PAID','FAILED','REVERSED')),
    maker_id bigint NOT NULL REFERENCES users(id),
    checker_id bigint REFERENCES users(id),
    transfer_ref text,
    proof_digest text CHECK (proof_digest IS NULL OR proof_digest ~ '^[a-f0-9]{64}$'),
    version bigint NOT NULL DEFAULT 0 CHECK (version>=0),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    requested_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (booking_id,idempotency_key),
    CHECK (checker_id IS NULL OR checker_id<>maker_id),
    CHECK ((state<>'PAID') OR (checker_id IS NOT NULL AND transfer_ref IS NOT NULL AND proof_digest IS NOT NULL))
);
CREATE TABLE refund_case_events (
    id bigserial PRIMARY KEY,
    refund_case_id bigint NOT NULL REFERENCES refund_cases(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    event_type text NOT NULL CHECK (event_type IN ('REQUESTED','APPROVED','PROCESSING','PAID','FAILED','REVERSED')),
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    expected_version bigint NOT NULL CHECK (expected_version>=0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id,refund_case_id,idempotency_key)
);
CREATE TRIGGER immutable_booking_arrival_events BEFORE UPDATE OR DELETE ON booking_arrival_events
  FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE TRIGGER immutable_booking_settlement_block_events BEFORE UPDATE OR DELETE ON booking_settlement_block_events
  FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE TRIGGER immutable_payout_recovery_events BEFORE UPDATE OR DELETE ON payout_recovery_events
  FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION payout_recovery_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.booking_id,NEW.settlement_block_id,NEW.payout_reservation_id,NEW.exposure_amount,
        NEW.source,NEW.owner_role,NEW.evidence_digest,NEW.opened_by,NEW.created_at) IS DISTINCT FROM
       (OLD.id,OLD.booking_id,OLD.settlement_block_id,OLD.payout_reservation_id,OLD.exposure_amount,
        OLD.source,OLD.owner_role,OLD.evidence_digest,OLD.opened_by,OLD.created_at)
       OR NEW.version<>OLD.version+1 OR OLD.state<>'OPEN' OR NEW.state<>'RESOLVED' THEN
        RAISE EXCEPTION 'invalid/non-append-only payout recovery resolution' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payout_recovery_version_guard BEFORE UPDATE ON payout_recovery_cases
  FOR EACH ROW EXECUTE FUNCTION payout_recovery_version_guard();
CREATE FUNCTION payout_recovery_event_required() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payout_recovery_events e WHERE e.recovery_case_id=NEW.id
        AND e.event_type=CASE WHEN TG_OP='INSERT' THEN 'OPENED' ELSE 'RESOLVED' END
        AND e.expected_version=CASE WHEN TG_OP='INSERT' THEN NEW.version ELSE NEW.version-1 END) THEN
        RAISE EXCEPTION 'payout recovery transition requires audit event' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payout_recovery_audit AFTER INSERT OR UPDATE ON payout_recovery_cases
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION payout_recovery_event_required();
CREATE TRIGGER immutable_refund_case_events BEFORE UPDATE OR DELETE ON refund_case_events
  FOR EACH ROW EXECUTE FUNCTION ledger_immutable();

CREATE FUNCTION booking_arrival_event_required() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM booking_arrival_events e WHERE e.booking_id=NEW.booking_id
        AND e.item_id=NEW.item_id AND e.room_ordinal=NEW.room_ordinal AND e.event_type=NEW.state) THEN
        RAISE EXCEPTION 'arrival state requires append-only event' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER booking_arrival_audit AFTER INSERT OR UPDATE ON booking_arrivals
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION booking_arrival_event_required();

CREATE FUNCTION booking_settlement_block_event_required() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM booking_settlement_block_events e
        WHERE e.block_id=NEW.id AND e.event_type='OPENED') THEN
        RAISE EXCEPTION 'settlement block insert requires event' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' AND (NEW.active IS DISTINCT FROM OLD.active OR NEW.resolution_digest IS DISTINCT FROM OLD.resolution_digest)
       AND NOT EXISTS (SELECT 1 FROM booking_settlement_block_events e
          WHERE e.block_id=NEW.id AND e.event_type='RESOLVED') THEN
        RAISE EXCEPTION 'settlement block resolution requires event' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER booking_settlement_block_audit AFTER INSERT OR UPDATE ON booking_settlement_blocks
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION booking_settlement_block_event_required();

CREATE FUNCTION refund_case_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.booking_id,NEW.source,NEW.amount,NEW.currency,NEW.policy_digest,NEW.reservation_id,
        NEW.maker_id,NEW.idempotency_key,NEW.body_hash,NEW.requested_at) IS DISTINCT FROM
       (OLD.id,OLD.booking_id,OLD.source,OLD.amount,OLD.currency,OLD.policy_digest,OLD.reservation_id,
        OLD.maker_id,OLD.idempotency_key,OLD.body_hash,OLD.requested_at)
       OR NEW.version<>OLD.version+1
       OR NOT ((OLD.state='REQUESTED' AND NEW.state='APPROVED')
          OR (OLD.state='APPROVED' AND NEW.state IN ('PROCESSING','FAILED'))
          OR (OLD.state='PROCESSING' AND NEW.state IN ('PAID','FAILED'))) THEN
        RAISE EXCEPTION 'invalid or non-append-only refund transition' USING ERRCODE='23514';
    END IF;
    IF NEW.checker_id IS DISTINCT FROM OLD.checker_id AND
       (NEW.checker_id IS NULL OR NEW.checker_id=NEW.maker_id OR NEW.state<>'APPROVED') THEN
        RAISE EXCEPTION 'refund maker/checker separation' USING ERRCODE='23514';
    END IF;
    IF NEW.state='PAID' AND (NEW.checker_id IS NULL OR NEW.checker_id=NEW.maker_id
        OR NEW.transfer_ref IS NULL OR NEW.proof_digest IS NULL) THEN
        RAISE EXCEPTION 'paid refund requires separate checker and proof' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER refund_case_version_guard BEFORE UPDATE ON refund_cases
  FOR EACH ROW EXECUTE FUNCTION refund_case_version_guard();
CREATE FUNCTION refund_case_event_required() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM refund_case_events e WHERE e.refund_case_id=NEW.id
        AND e.event_type=NEW.state AND e.expected_version=
            CASE WHEN TG_OP='INSERT' THEN NEW.version ELSE NEW.version-1 END) THEN
        RAISE EXCEPTION 'refund state requires append-only event' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER refund_case_audit AFTER INSERT OR UPDATE ON refund_cases
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION refund_case_event_required();

-- Acknowledged direct receipts can fund Host-responsibility refunds, but never
-- platform cash. Disputed/refused/reversed receipts do not enter that pool.
CREATE OR REPLACE FUNCTION ledger_reserve_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE collected numeric; allocated numeric; payout_allocated numeric; s record;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=NEW.booking_id;
    SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=NEW.booking_id;
    SELECT coalesce(sum(amount),0) INTO collected FROM payment_collections WHERE booking_id=NEW.booking_id
        AND source=NEW.source AND disposition IN ('ACCEPTED','MEMO');
    IF NEW.source='HOST_ATTESTED' THEN
        collected:=collected + coalesce((SELECT sum(amount) FROM direct_receipt_cases
            WHERE booking_id=NEW.booking_id AND state IN ('ACKNOWLEDGED','RESOLVED')
              AND guest_disposition='ACKNOWLEDGE'),0);
    END IF;
    SELECT coalesce(sum(r.amount),0),coalesce(sum(r.amount) FILTER (WHERE r.kind='PAYOUT'),0)
        INTO allocated,payout_allocated FROM ledger_reservations r
        LEFT JOIN ledger_reserve_movements m ON m.reservation_id=r.id
        WHERE r.booking_id=NEW.booking_id AND r.source=NEW.source AND (m.action IS NULL OR m.action='CONSUME');
    IF NEW.amount>collected-allocated OR NEW.amount>NEW.policy_bound-allocated
        OR (NEW.kind='PAYOUT' AND NEW.amount>s.held_host_payable-payout_allocated) THEN
        RAISE EXCEPTION 'source/policy/held reserve pool exceeded' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
