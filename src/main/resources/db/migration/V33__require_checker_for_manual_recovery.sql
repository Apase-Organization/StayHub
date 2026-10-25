ALTER TABLE payout_recovery_cases
    ADD COLUMN resolution_maker_id bigint REFERENCES users(id),
    ADD COLUMN resolution_checker_id bigint REFERENCES users(id),
    ADD COLUMN transfer_ref text;
ALTER TABLE payout_recovery_cases ADD CONSTRAINT chk_payout_recovery_resolution_proof CHECK (
    (state='OPEN' AND resolution_maker_id IS NULL AND resolution_checker_id IS NULL AND transfer_ref IS NULL)
    OR (state='RESOLVED' AND outcome='RECONCILED_NO_LOSS' AND resolution_maker_id IS NOT NULL
        AND resolution_checker_id IS NULL AND transfer_ref IS NULL)
    OR (state='RESOLVED' AND outcome='MANUAL_RECOVERY_DOCUMENTED' AND resolution_maker_id IS NOT NULL
        AND resolution_checker_id IS NOT NULL AND resolution_checker_id<>resolution_maker_id
        AND transfer_ref IS NOT NULL AND length(transfer_ref) BETWEEN 1 AND 200)
);
