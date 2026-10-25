-- Avoid double-counting an acknowledged receipt once its non-cash ledger memo
-- exists. A reversed receipt remains history but no longer occupies Host B.
CREATE OR REPLACE FUNCTION direct_receipt_case_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s record; owner_id bigint; direct_total numeric;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    SELECT p.host_id INTO owner_id FROM bookings b JOIN properties p ON p.id=b.property_id WHERE b.id=NEW.booking_id;
    IF NEW.host_id<>owner_id THEN RAISE EXCEPTION 'direct receipt host ownership' USING ERRCODE='23514'; END IF;
    SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=NEW.booking_id;
    SELECT coalesce((SELECT sum(amount) FROM direct_receipt_cases
        WHERE booking_id=NEW.booking_id AND state<>'REVERSED'),0)
      + coalesce((SELECT sum(pc.amount) FROM payment_collections pc WHERE pc.booking_id=NEW.booking_id
        AND pc.source='HOST_ATTESTED' AND pc.disposition='MEMO'
        AND NOT EXISTS (SELECT 1 FROM direct_receipt_cases c WHERE c.booking_id=pc.booking_id
            AND c.receipt_ref=pc.receipt_ref)),0)
      INTO direct_total;
    IF direct_total+NEW.amount>s.balance_due_host THEN
        RAISE EXCEPTION 'direct receipt exceeds outstanding host balance' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION ledger_reserve_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE collected numeric; allocated numeric; payout_allocated numeric; s record;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=NEW.booking_id;
    SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=NEW.booking_id;
    SELECT coalesce(sum(pc.amount),0) INTO collected FROM payment_collections pc
        WHERE pc.booking_id=NEW.booking_id AND pc.source=NEW.source AND pc.disposition IN ('ACCEPTED','MEMO')
          AND NOT (pc.source='HOST_ATTESTED' AND EXISTS (SELECT 1 FROM direct_receipt_cases c
            WHERE c.booking_id=pc.booking_id AND c.receipt_ref=pc.receipt_ref));
    IF NEW.source='HOST_ATTESTED' THEN collected:=collected+coalesce((SELECT sum(amount)
        FROM direct_receipt_cases WHERE booking_id=NEW.booking_id
          AND state IN ('ACKNOWLEDGED','RESOLVED') AND guest_disposition='ACKNOWLEDGE'),0); END IF;
    SELECT coalesce(sum(r.amount),0),coalesce(sum(r.amount) FILTER (WHERE r.kind='PAYOUT'),0)
        INTO allocated,payout_allocated FROM ledger_reservations r
        LEFT JOIN ledger_reserve_movements m ON m.reservation_id=r.id
        WHERE r.booking_id=NEW.booking_id AND r.source=NEW.source AND (m.action IS NULL OR m.action='CONSUME');
    IF NEW.amount>collected-allocated OR NEW.amount>NEW.policy_bound-allocated
        OR (NEW.kind='PAYOUT' AND NEW.amount>s.held_host_payable-payout_allocated) THEN
        RAISE EXCEPTION 'source/policy/held reserve pool exceeded' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION ledger_collection_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s record; a record; direct_total numeric;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=NEW.booking_id;
    SELECT * INTO s FROM booking_financial_snapshots WHERE booking_id=NEW.booking_id;
    IF NEW.disposition='ACCEPTED' THEN
        SELECT * INTO a FROM payment_attempts WHERE id=NEW.attempt_id;
        IF NEW.amount<>a.expected_amount OR NEW.currency<>a.currency OR clock_timestamp()>=s.hold_expires_at THEN
            RAISE EXCEPTION 'accepted collection amount/currency/deadline binding' USING ERRCODE='23514'; END IF;
    ELSIF NEW.source='HOST_ATTESTED' THEN
        SELECT coalesce(sum(pc.amount),0) INTO direct_total FROM payment_collections pc
          WHERE pc.booking_id=NEW.booking_id AND pc.source='HOST_ATTESTED'
            AND NOT EXISTS (SELECT 1 FROM direct_receipt_cases c WHERE c.booking_id=pc.booking_id
              AND c.receipt_ref=pc.receipt_ref AND c.state='REVERSED');
        IF direct_total+NEW.amount>s.balance_due_host THEN
            RAISE EXCEPTION 'direct memorandum exceeds B' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
