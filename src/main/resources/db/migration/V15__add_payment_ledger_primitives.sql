-- TSK-110 internal primitives only; legacy payments and all V14 objects unchanged.
-- No backfill, provider invocation, earning, refund or payout execution.
CREATE DOMAIN ledger_vnd AS numeric CHECK (
    VALUE >= 0 AND VALUE <= 9999999999999999999 AND VALUE = trunc(VALUE)
);

CREATE TABLE booking_financial_snapshots (
    booking_id bigint PRIMARY KEY REFERENCES bookings(id),
    guest_id bigint NOT NULL REFERENCES users(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    payment_mode text NOT NULL CHECK (payment_mode IN ('FULL_PREPAY','HYBRID_DEPOSIT')),
    currency text NOT NULL CHECK (currency='VND'),
    room_charges ledger_vnd NOT NULL,
    cleaning_fee ledger_vnd NOT NULL,
    subtotal ledger_vnd NOT NULL,
    discount ledger_vnd NOT NULL,
    total_stay ledger_vnd NOT NULL CHECK (total_stay>0),
    due_online ledger_vnd NOT NULL CHECK (due_online>0),
    balance_due_host ledger_vnd NOT NULL,
    gross_commission ledger_vnd NOT NULL,
    net_retention ledger_vnd NOT NULL,
    host_entitlement ledger_vnd NOT NULL,
    held_host_payable ledger_vnd NOT NULL,
    version bigint NOT NULL CHECK (version>=0),
    policy_version text NOT NULL CHECK (policy_version='s0-policy-v1'),
    fixture_version text NOT NULL CHECK (fixture_version='s0-fixtures-v1'),
    quote_hash text NOT NULL CHECK (quote_hash ~ '^[a-f0-9]{64}$'),
    original_created_at timestamptz NOT NULL,
    hold_expires_at timestamptz NOT NULL,
    CHECK (actor_id=guest_id),
    CHECK (subtotal=room_charges+cleaning_fee AND total_stay=subtotal-discount),
    CHECK (gross_commission=round(subtotal*0.15) AND discount<=gross_commission),
    CHECK (net_retention=gross_commission-discount AND due_online>=net_retention),
    CHECK (due_online+balance_due_host=total_stay),
    CHECK ((payment_mode='FULL_PREPAY' AND due_online=total_stay AND balance_due_host=0)
        OR (payment_mode='HYBRID_DEPOSIT' AND due_online=round(total_stay*0.20))),
    CHECK (host_entitlement=subtotal-gross_commission),
    CHECK (held_host_payable=due_online-net_retention AND held_host_payable+balance_due_host=host_entitlement),
    CHECK (hold_expires_at=original_created_at+interval '900 seconds')
);

CREATE FUNCTION ledger_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'immutable financial history' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_financial_snapshot BEFORE UPDATE OR DELETE ON booking_financial_snapshots
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();

CREATE FUNCTION ledger_snapshot_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b record;
BEGIN
    SELECT bookings.guest_id,bookings.created_at,p.host_id INTO b
        FROM bookings JOIN properties p ON p.id=bookings.property_id
        WHERE bookings.id=NEW.booking_id FOR UPDATE OF bookings;
    IF NEW.guest_id<>b.guest_id OR NEW.original_created_at<>b.created_at OR NEW.guest_id=b.host_id THEN
        RAISE EXCEPTION 'snapshot booking/actor/original deadline binding' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_snapshot_binding BEFORE INSERT ON booking_financial_snapshots
    FOR EACH ROW EXECUTE FUNCTION ledger_snapshot_binding();
CREATE FUNCTION ledger_booking_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.guest_id,NEW.created_at,NEW.property_id) IS DISTINCT FROM (OLD.guest_id,OLD.created_at,OLD.property_id)
        AND EXISTS (SELECT 1 FROM booking_financial_snapshots WHERE booking_id=OLD.id) THEN
        RAISE EXCEPTION 'immutable financial booking binding' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_booking_binding BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION ledger_booking_binding();

-- An actual write, not just SELECT FOR UPDATE, also aborts stale-snapshot
-- REPEATABLE READ/SERIALIZABLE writers instead of permitting stale aggregate sums.
CREATE TABLE payment_settlement_locks (
    booking_id bigint PRIMARY KEY REFERENCES booking_financial_snapshots(booking_id),
    version bigint NOT NULL DEFAULT 0 CHECK (version>=0)
);
CREATE FUNCTION ledger_lock_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR NEW.booking_id<>OLD.booking_id OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'settlement lock identity/version must advance once' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_lock_version_guard BEFORE UPDATE OR DELETE ON payment_settlement_locks
    FOR EACH ROW EXECUTE FUNCTION ledger_lock_version_guard();
CREATE FUNCTION ledger_initialize_lock() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO payment_settlement_locks(booking_id) VALUES (NEW.booking_id);
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_initialize_lock AFTER INSERT ON booking_financial_snapshots
    FOR EACH ROW EXECUTE FUNCTION ledger_initialize_lock();

CREATE TABLE payment_primitive_keys (
    actor_id bigint NOT NULL REFERENCES users(id),
    operation text NOT NULL CHECK (operation IN ('SNAPSHOT','ATTEMPT','COLLECTION','DIRECT_MEMO','JOURNAL','RESERVE','RESERVE_MOVEMENT')),
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    key text NOT NULL CHECK (length(key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    result_id bigint NOT NULL CHECK (result_id>0),
    PRIMARY KEY (actor_id,operation,booking_id,key)
);
CREATE TRIGGER immutable_primitive_keys BEFORE UPDATE OR DELETE ON payment_primitive_keys
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();

CREATE TABLE payment_attempts (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    actor_id bigint NOT NULL REFERENCES users(id),
    ordinal integer NOT NULL CHECK (ordinal BETWEEN 1 AND 3),
    provider_ref text NOT NULL UNIQUE CHECK (length(provider_ref) BETWEEN 1 AND 200),
    merchant text NOT NULL CHECK (length(merchant) BETWEEN 1 AND 200),
    config_version text NOT NULL CHECK (length(config_version) BETWEEN 1 AND 200),
    expected_amount ledger_vnd NOT NULL CHECK (expected_amount>0),
    currency text NOT NULL CHECK (currency='VND'),
    hold_expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (booking_id,ordinal), UNIQUE (id,booking_id)
);
CREATE TRIGGER immutable_payment_attempts BEFORE UPDATE OR DELETE ON payment_attempts
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION ledger_attempt_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s record; n integer;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=NEW.booking_id;
    SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=NEW.booking_id;
    SELECT count(*) INTO n FROM payment_attempts WHERE booking_id=NEW.booking_id;
    IF n>=3 OR NEW.ordinal<>n+1 OR NEW.actor_id<>s.actor_id OR NEW.expected_amount<>s.due_online
        OR NEW.currency<>s.currency OR NEW.hold_expires_at<>s.hold_expires_at
        OR clock_timestamp()>=s.hold_expires_at THEN
        RAISE EXCEPTION 'attempt limit/expiry/snapshot binding' USING ERRCODE='23514';
    END IF;
    NEW.created_at:=clock_timestamp();
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_attempt_binding BEFORE INSERT ON payment_attempts
    FOR EACH ROW EXECUTE FUNCTION ledger_attempt_binding();

-- Append creation outcome, never rewrite immutable provider bindings. Failure
-- does not close booking/hold; provider verification/lifecycle belongs to082.
CREATE TABLE payment_attempt_outcomes (
    attempt_id bigint PRIMARY KEY REFERENCES payment_attempts(id),
    outcome text NOT NULL CHECK (outcome IN ('CREATED','CREATION_FAILED')),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER immutable_attempt_outcomes BEFORE UPDATE OR DELETE ON payment_attempt_outcomes
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();

CREATE TABLE payment_collections (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    attempt_id bigint,
    actor_id bigint NOT NULL REFERENCES users(id),
    source text NOT NULL CHECK (source IN ('ONLINE_VERIFIED','HOST_ATTESTED')),
    method text NOT NULL CHECK (method IN ('PROVIDER','CASH','BANK_TRANSFER')),
    direction text NOT NULL DEFAULT 'IN' CHECK (direction='IN'),
    disposition text NOT NULL CHECK (disposition IN ('ACCEPTED','RECONCILE','MEMO')),
    receipt_ref text NOT NULL UNIQUE CHECK (length(receipt_ref) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    amount ledger_vnd NOT NULL CHECK (amount>0),
    currency text NOT NULL CHECK (currency='VND'),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    FOREIGN KEY (attempt_id,booking_id) REFERENCES payment_attempts(id,booking_id),
    UNIQUE (id,booking_id),
    CHECK ((source='ONLINE_VERIFIED' AND method='PROVIDER' AND attempt_id IS NOT NULL AND disposition IN ('ACCEPTED','RECONCILE'))
        OR (source='HOST_ATTESTED' AND method IN ('CASH','BANK_TRANSFER') AND attempt_id IS NULL AND disposition='MEMO'))
);
CREATE UNIQUE INDEX uq_accepted_online_booking ON payment_collections(booking_id) WHERE disposition='ACCEPTED';
CREATE TRIGGER immutable_payment_collections BEFORE UPDATE OR DELETE ON payment_collections
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION ledger_collection_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s record; a record; direct_total numeric;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=NEW.booking_id;
    SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=NEW.booking_id;
    IF NEW.disposition='ACCEPTED' THEN
        SELECT * INTO a FROM payment_attempts WHERE id=NEW.attempt_id;
        IF NEW.amount<>a.expected_amount OR NEW.currency<>a.currency OR clock_timestamp()>=s.hold_expires_at THEN
            RAISE EXCEPTION 'accepted collection amount/currency/deadline binding' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.source='HOST_ATTESTED' THEN
        SELECT coalesce(sum(amount),0) INTO direct_total FROM payment_collections
            WHERE booking_id=NEW.booking_id AND source='HOST_ATTESTED';
        IF direct_total+NEW.amount>s.balance_due_host THEN
            RAISE EXCEPTION 'direct memorandum exceeds B' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_collection_binding BEFORE INSERT ON payment_collections
    FOR EACH ROW EXECUTE FUNCTION ledger_collection_binding();

CREATE TABLE ledger_journals (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    actor_id bigint NOT NULL REFERENCES users(id),
    event_key text NOT NULL UNIQUE CHECK (length(event_key) BETWEEN 1 AND 200),
    body_hash text NOT NULL CHECK (body_hash ~ '^[a-f0-9]{64}$'),
    currency text NOT NULL CHECK (currency='VND'),
    source text NOT NULL CHECK (source IN ('ONLINE_VERIFIED','HOST_ATTESTED','ADJUSTMENT')),
    status text NOT NULL DEFAULT 'POSTED' CHECK (status='POSTED'),
    collection_id bigint UNIQUE,
    reversal_of bigint UNIQUE,
    metadata_digest text NOT NULL CHECK (metadata_digest ~ '^[a-f0-9]{64}$'),
    construction_xid bigint NOT NULL DEFAULT txid_current(),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (id,booking_id,currency),
    FOREIGN KEY (collection_id,booking_id) REFERENCES payment_collections(id,booking_id),
    FOREIGN KEY (reversal_of,booking_id,currency) REFERENCES ledger_journals(id,booking_id,currency),
    CHECK (reversal_of IS NULL OR (reversal_of<>id AND collection_id IS NULL AND source='ADJUSTMENT'))
);
CREATE TABLE ledger_lines (
    id bigserial PRIMARY KEY,
    journal_id bigint NOT NULL,
    booking_id bigint NOT NULL,
    currency text NOT NULL CHECK (currency='VND'),
    account text NOT NULL CHECK (account IN ('ONLINE_CASH','COMMISSION_DUE','COMMISSION_RESERVED','COMMISSION_EARNED',
        'HOST_HELD_PAYABLE','DISCOUNT_FUNDING_EXPENSE','PROVIDER_COST','TAX_LIABILITY','REFUND_RESERVED',
        'PAYOUT_RESERVED','RECOVERY','MEMO_HOST_DIRECT','MEMO_GUEST_DIRECT')),
    debit ledger_vnd NOT NULL,
    credit ledger_vnd NOT NULL,
    FOREIGN KEY (journal_id,booking_id,currency) REFERENCES ledger_journals(id,booking_id,currency),
    CHECK ((debit>0 AND credit=0) OR (credit>0 AND debit=0))
);
CREATE TRIGGER immutable_ledger_journals BEFORE UPDATE OR DELETE ON ledger_journals
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE TRIGGER immutable_ledger_lines BEFORE UPDATE OR DELETE ON ledger_lines
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
-- DEFAULT is only a convenience, never an authority boundary: a SQL caller can
-- explicitly supply any value. Bind every new header to the actual constructing
-- transaction, unconditionally overriding even a claimed foreign transaction XID.
CREATE FUNCTION ledger_stamp_construction_xid() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.construction_xid:=txid_current();
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_stamp_construction_xid BEFORE INSERT ON ledger_journals
    FOR EACH ROW EXECUTE FUNCTION ledger_stamp_construction_xid();
CREATE FUNCTION ledger_line_admission() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j record; original_source text; parent_id bigint;
BEGIN
    SELECT * INTO j FROM ledger_journals WHERE id=NEW.journal_id FOR SHARE;
    IF j.construction_xid<>txid_current() THEN
        RAISE EXCEPTION 'posted journal closed to new lines' USING ERRCODE='23514';
    END IF;
    original_source:=j.source;
    parent_id:=j.reversal_of;
    WHILE parent_id IS NOT NULL LOOP
        SELECT source,reversal_of INTO original_source,parent_id FROM ledger_journals WHERE id=parent_id;
    END LOOP;
    IF (original_source='HOST_ATTESTED') IS DISTINCT FROM (NEW.account LIKE 'MEMO_%') THEN
        RAISE EXCEPTION 'direct memorandum cannot enter platform accounts' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_line_admission BEFORE INSERT ON ledger_lines
    FOR EACH ROW EXECUTE FUNCTION ledger_line_admission();
CREATE FUNCTION ledger_balance_check() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE jid bigint; j record; c record; s record; n integer; d numeric; cr numeric;
BEGIN
    IF TG_TABLE_NAME='ledger_journals' THEN jid:=NEW.id; ELSE jid:=NEW.journal_id; END IF;
    SELECT * INTO j FROM ledger_journals WHERE id=jid;
    SELECT count(*),coalesce(sum(debit),0),coalesce(sum(credit),0) INTO n,d,cr FROM ledger_lines WHERE journal_id=jid;
    IF n<2 OR d<>cr THEN RAISE EXCEPTION 'unbalanced/empty posted journal' USING ERRCODE='23514'; END IF;
    IF j.reversal_of IS NOT NULL AND EXISTS (
        SELECT account FROM (
            SELECT account,debit-credit AS delta FROM ledger_lines WHERE journal_id=jid
            UNION ALL SELECT account,debit-credit FROM ledger_lines WHERE journal_id=j.reversal_of
        ) x GROUP BY account HAVING sum(delta)<>0
    ) THEN RAISE EXCEPTION 'reversal must invert original accounts' USING ERRCODE='23514'; END IF;
    IF j.collection_id IS NOT NULL THEN
        SELECT * INTO c FROM payment_collections WHERE id=j.collection_id;
        SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=j.booking_id;
        IF j.source<>c.source OR c.disposition='RECONCILE' THEN
            RAISE EXCEPTION 'collection journal source/disposition' USING ERRCODE='23514';
        END IF;
        IF c.source='ONLINE_VERIFIED' AND (
            coalesce((SELECT sum(debit-credit) FROM ledger_lines WHERE journal_id=jid AND account='ONLINE_CASH'),0)<>c.amount
            OR coalesce((SELECT sum(credit-debit) FROM ledger_lines WHERE journal_id=jid AND account='COMMISSION_RESERVED'),0)<>s.gross_commission
            OR coalesce((SELECT sum(debit-credit) FROM ledger_lines WHERE journal_id=jid AND account='DISCOUNT_FUNDING_EXPENSE'),0)<>s.discount
            OR coalesce((SELECT sum(credit-debit) FROM ledger_lines WHERE journal_id=jid AND account='HOST_HELD_PAYABLE'),0)<>s.held_host_payable
            OR EXISTS (SELECT 1 FROM ledger_lines WHERE journal_id=jid AND account NOT IN
                ('ONLINE_CASH','COMMISSION_RESERVED','DISCOUNT_FUNDING_EXPENSE','HOST_HELD_PAYABLE'))
        ) THEN RAISE EXCEPTION 'collection journal frozen funding amounts' USING ERRCODE='23514'; END IF;
        IF c.source='HOST_ATTESTED' AND (
            coalesce((SELECT sum(debit-credit) FROM ledger_lines WHERE journal_id=jid AND account='MEMO_HOST_DIRECT'),0)<>c.amount
            OR coalesce((SELECT sum(credit-debit) FROM ledger_lines WHERE journal_id=jid AND account='MEMO_GUEST_DIRECT'),0)<>c.amount
        ) THEN RAISE EXCEPTION 'direct memorandum amounts' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ledger_header_balance AFTER INSERT ON ledger_journals
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_balance_check();
CREATE CONSTRAINT TRIGGER ledger_line_balance AFTER INSERT ON ledger_lines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_balance_check();
CREATE FUNCTION ledger_collection_posted() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.disposition<>'RECONCILE' AND NOT EXISTS (SELECT 1 FROM ledger_journals WHERE collection_id=NEW.id) THEN
        RAISE EXCEPTION 'collection requires atomic posted journal' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ledger_collection_posted AFTER INSERT ON payment_collections
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_collection_posted();

CREATE TABLE ledger_reservations (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES booking_financial_snapshots(booking_id),
    actor_id bigint NOT NULL REFERENCES users(id),
    kind text NOT NULL CHECK (kind IN ('REFUND','PAYOUT')),
    source text NOT NULL CHECK (source IN ('ONLINE_VERIFIED','HOST_ATTESTED')),
    amount ledger_vnd NOT NULL CHECK (amount>0),
    currency text NOT NULL CHECK (currency='VND'),
    policy_bound ledger_vnd NOT NULL,
    liquidity_bound ledger_vnd NOT NULL,
    eligibility_digest text NOT NULL CHECK (eligibility_digest ~ '^[a-f0-9]{64}$'),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK (amount<=policy_bound AND amount<=liquidity_bound),
    CHECK (kind<>'PAYOUT' OR source='ONLINE_VERIFIED')
);
CREATE TABLE ledger_reserve_movements (
    id bigserial PRIMARY KEY,
    reservation_id bigint NOT NULL UNIQUE REFERENCES ledger_reservations(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    action text NOT NULL CHECK (action IN ('RELEASE','CONSUME')),
    evidence_digest text NOT NULL CHECK (evidence_digest ~ '^[a-f0-9]{64}$'),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER immutable_ledger_reservations BEFORE UPDATE OR DELETE ON ledger_reservations
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE TRIGGER immutable_reserve_movements BEFORE UPDATE OR DELETE ON ledger_reserve_movements
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION ledger_reserve_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE collected numeric; allocated numeric; payout_allocated numeric; s record;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=NEW.booking_id;
    SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=NEW.booking_id;
    SELECT coalesce(sum(amount),0) INTO collected FROM payment_collections WHERE booking_id=NEW.booking_id
        AND source=NEW.source AND disposition IN ('ACCEPTED','MEMO');
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
CREATE TRIGGER ledger_reserve_guard BEFORE INSERT ON ledger_reservations
    FOR EACH ROW EXECUTE FUNCTION ledger_reserve_guard();
CREATE FUNCTION ledger_movement_lock() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE bid bigint;
BEGIN
    SELECT booking_id INTO bid FROM ledger_reservations WHERE id=NEW.reservation_id;
    PERFORM 1 FROM bookings WHERE id=bid FOR UPDATE;
    UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=bid;
    RETURN NEW;
END $$;
CREATE TRIGGER ledger_movement_lock BEFORE INSERT ON ledger_reserve_movements
    FOR EACH ROW EXECUTE FUNCTION ledger_movement_lock();
