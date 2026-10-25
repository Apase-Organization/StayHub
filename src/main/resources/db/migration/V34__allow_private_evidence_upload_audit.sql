ALTER TABLE private_evidence_access_log DROP CONSTRAINT private_evidence_access_log_access_reason_check;
ALTER TABLE private_evidence_access_log ADD CONSTRAINT private_evidence_access_log_access_reason_check
    CHECK (access_reason IN ('UPLOAD','READ','REVIEW','RESOLVE'));
