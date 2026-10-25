-- Private proof objects are stored in PostgreSQL, never under the public uploads path.
CREATE TABLE private_evidence_objects (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES bookings(id),
    uploader_id bigint NOT NULL REFERENCES users(id),
    purpose text NOT NULL CHECK (purpose IN ('DIRECT_RECEIPT','ARRIVAL','REFUND','PAYOUT','DISPUTE')),
    sha256 text NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    media_type text NOT NULL CHECK (media_type IN ('image/jpeg','image/png','application/pdf')),
    original_name text NOT NULL CHECK (length(original_name) BETWEEN 1 AND 200),
    content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (booking_id,purpose,sha256)
);
CREATE INDEX idx_private_evidence_objects_booking ON private_evidence_objects(booking_id,purpose,created_at);
CREATE TRIGGER immutable_private_evidence_objects BEFORE UPDATE OR DELETE ON private_evidence_objects
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();

ALTER TABLE private_evidence_access_log DROP CONSTRAINT private_evidence_access_log_resource_type_check;
ALTER TABLE private_evidence_access_log ADD CONSTRAINT private_evidence_access_log_resource_type_check
    CHECK (resource_type IN ('DIRECT_RECEIPT','ARRIVAL','PAYOUT_RECOVERY','REFUND','EVIDENCE_OBJECT'));
