-- PREPARATION ONLY. No execute mode is authorized or implemented.
-- Run manually ONLY against the dedicated local demo database after review:
-- psql "$DEMO_DATABASE_URL" -X -v ON_ERROR_STOP=1 -f scripts/prepare-demo-bookings.sql
-- No credentials are embedded; no application/provider/outbox calls are made.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';

-- Deterministic inventory of ALL blocking bookings; no implicit demo selector.
-- Every row is excluded from mutation pending controller review and a verified
-- database-wide demo attestation, backup and inventory/financial release plan.
SELECT id AS booking_id, status AS original_status,
       check_in_date AS original_check_in, check_out_date AS original_check_out,
       CASE WHEN status IN ('PENDING','PENDING_PAYMENT')
            THEN 'CANCEL_ONLY_IF_UNPAID_AND_RELEASE_PROTOCOL_VERIFIED'
            ELSE 'COMPLETE_ONLY_AFTER_FINANCE_AND_SYNTHETIC_EVIDENCE_REVIEW'
       END AS proposed_action,
       DATE '2026-10-29' AS proposed_demo_check_in,
       DATE '2026-10-30' AS proposed_demo_check_out,
       TIMESTAMPTZ '2026-10-30 11:00:00+07' AS requested_completion,
       'EXCLUDED_EXECUTION_NOT_IMPLEMENTED' AS disposition,
       'SYNTHETIC DEMO ONLY; NOT CUSTOMER ACKNOWLEDGMENT' AS proposed_audit_marker
FROM bookings
WHERE status IN ('PENDING','PENDING_PAYMENT','CONFIRMED')
ORDER BY id;

-- V25 uses clock_timestamp(), not the requested fixture time. Running before
-- 30 October 11:00 HCM cannot complete the proposed stay without bypassing it.
SELECT clock_timestamp() >= TIMESTAMPTZ '2026-10-30 11:00:00+07'
       AS completion_clock_gate_open;
ROLLBACK;
