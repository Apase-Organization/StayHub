ALTER TABLE booking_notification_outbox ADD COLUMN claimed_at timestamptz;

CREATE TABLE refund_notification_outbox (
    id bigserial PRIMARY KEY,
    refund_case_id bigint NOT NULL REFERENCES refund_cases(id),
    booking_id bigint NOT NULL REFERENCES bookings(id),
    event_key text NOT NULL UNIQUE,
    event_type text NOT NULL CHECK (event_type IN ('REFUND_REQUESTED','REFUND_APPROVED','REFUND_PROCESSING','REFUND_PAID','REFUND_FAILED')),
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    dispatched_at timestamptz,
    claimed_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts>=0),
    last_error_code text
);
CREATE INDEX idx_refund_notification_outbox_pending ON refund_notification_outbox(created_at,id)
    WHERE dispatched_at IS NULL;

CREATE FUNCTION enqueue_refund_notification() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payload jsonb; event text; booking_guest bigint; cfg record;
BEGIN
    IF TG_OP='INSERT' THEN event:='REFUND_REQUESTED';
    ELSIF NEW.state IS DISTINCT FROM OLD.state AND NEW.state='APPROVED' THEN event:='REFUND_APPROVED';
    ELSIF NEW.state IS DISTINCT FROM OLD.state AND NEW.state='PROCESSING' THEN event:='REFUND_PROCESSING';
    ELSIF NEW.state IS DISTINCT FROM OLD.state AND NEW.state='PAID' THEN event:='REFUND_PAID';
    ELSIF NEW.state IS DISTINCT FROM OLD.state AND NEW.state='FAILED' THEN event:='REFUND_FAILED';
    ELSE RETURN NEW; END IF;
    SELECT guest_id INTO booking_guest FROM bookings WHERE id=NEW.booking_id;
    SELECT minimum_business_days,maximum_business_days,timezone,calendar_version INTO cfg
        FROM refund_sla_config WHERE id=NEW.sla_config_id;
    payload:=jsonb_build_object('refundId',NEW.id,'bookingId',NEW.booking_id,'guestId',booking_guest,
        'source',NEW.source,'amount',NEW.amount,'currency',NEW.currency,'state',NEW.state,
        'minimumBusinessDays',CASE WHEN NEW.source='ONLINE_VERIFIED' THEN cfg.minimum_business_days ELSE NULL END,
        'maximumBusinessDays',CASE WHEN NEW.source='ONLINE_VERIFIED' THEN cfg.maximum_business_days ELSE NULL END,
        'timezone',CASE WHEN NEW.source='ONLINE_VERIFIED' THEN cfg.timezone ELSE NULL END,
        'calendarVersion',CASE WHEN NEW.source='ONLINE_VERIFIED' THEN cfg.calendar_version ELSE NULL END,
        'transferRef',NEW.transfer_ref,'proofDigest',NEW.proof_digest,
        'policyDigest',NEW.policy_digest);
    INSERT INTO refund_notification_outbox(refund_case_id,booking_id,event_key,event_type,payload)
        VALUES (NEW.id,NEW.booking_id,'refund:'||NEW.id||':'||event,event,payload)
        ON CONFLICT (event_key) DO NOTHING;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER refund_notification_outbox_enqueue
    AFTER INSERT OR UPDATE ON refund_cases DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enqueue_refund_notification();
