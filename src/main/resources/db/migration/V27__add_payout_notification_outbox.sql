CREATE TABLE payout_notification_outbox (
    id bigserial PRIMARY KEY,
    payout_case_id bigint NOT NULL REFERENCES payout_workflow_cases(id),
    booking_id bigint NOT NULL REFERENCES bookings(id),
    event_key text NOT NULL UNIQUE,
    event_type text NOT NULL CHECK (event_type IN ('PAYOUT_REQUESTED','PAYOUT_APPROVED','PAYOUT_PROCESSING','PAYOUT_PAID','PAYOUT_FAILED')),
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    dispatched_at timestamptz,
    claimed_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts>=0),
    last_error_code text
);
CREATE INDEX idx_payout_notification_outbox_pending ON payout_notification_outbox(created_at,id)
    WHERE dispatched_at IS NULL;
CREATE FUNCTION enqueue_payout_notification() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE event text; payload jsonb; host bigint;
BEGIN
    IF TG_OP='INSERT' THEN event:='PAYOUT_REQUESTED';
    ELSIF NEW.state IS DISTINCT FROM OLD.state AND NEW.state='APPROVED' THEN event:='PAYOUT_APPROVED';
    ELSIF NEW.state IS DISTINCT FROM OLD.state AND NEW.state='PROCESSING' THEN event:='PAYOUT_PROCESSING';
    ELSIF NEW.state IS DISTINCT FROM OLD.state AND NEW.state='PAID' THEN event:='PAYOUT_PAID';
    ELSIF NEW.state IS DISTINCT FROM OLD.state AND NEW.state='FAILED' THEN event:='PAYOUT_FAILED';
    ELSE RETURN NEW; END IF;
    SELECT p.host_id INTO host FROM bookings b JOIN properties p ON p.id=b.property_id WHERE b.id=NEW.booking_id;
    payload:=jsonb_build_object('payoutId',NEW.id,'bookingId',NEW.booking_id,'hostId',host,
        'amount',NEW.amount,'currency',NEW.currency,'state',NEW.state,'transferRef',NEW.transfer_ref,
        'proofDigest',NEW.proof_digest,'eligibilityDigest',NEW.eligibility_digest);
    INSERT INTO payout_notification_outbox(payout_case_id,booking_id,event_key,event_type,payload)
        VALUES (NEW.id,NEW.booking_id,'payout:'||NEW.id||':'||event,event,payload)
        ON CONFLICT (event_key) DO NOTHING;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER payout_notification_outbox_enqueue AFTER INSERT OR UPDATE ON payout_workflow_cases
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enqueue_payout_notification();
