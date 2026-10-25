ALTER TABLE booking_settlement_blocks DROP CONSTRAINT booking_settlement_blocks_reason_code_check;
ALTER TABLE booking_settlement_blocks ADD CONSTRAINT booking_settlement_blocks_reason_code_check
    CHECK (reason_code IN ('DISPUTE','REFUND','RECONCILIATION','LEGAL','LIQUIDITY','OPERATIONS','NO_SHOW'));
