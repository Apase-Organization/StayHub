-- Explicit local/demo campaign setup, not an automatic startup migration.
-- Campaign: 15 percent, minimum 200,000 VND, no fixed monetary cap.
-- Preserve counters, reservations, campaign dates and history when renaming.
-- Run: docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < src/main/resources/db/seed/restore_stayhub15.sql
BEGIN;
LOCK TABLE discount_codes IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM discount_codes WHERE lower(code)='stayhub25')
       AND EXISTS (SELECT 1 FROM discount_codes WHERE lower(code)='stayhub15') THEN
        RAISE EXCEPTION 'Both campaigns exist; review manually before renaming';
    END IF;
END $$;
UPDATE discount_codes
SET code='stayhub15',type='PERCENT',value=15,cap=NULL,minimum_amount=200000,
    updated_at=CURRENT_TIMESTAMP
WHERE lower(code) IN ('stayhub25','stayhub15');
INSERT INTO discount_codes
    (code,type,value,cap,minimum_amount,starts_at,ends_at,usage_limit,used_count,per_user_limit,active)
SELECT 'stayhub15','PERCENT',15,NULL,200000,
       CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 year',1000,0,1,TRUE
WHERE NOT EXISTS (SELECT 1 FROM discount_codes WHERE lower(code)='stayhub15');
SELECT code,type,value,cap,minimum_amount,starts_at,ends_at,usage_limit,
       used_count,reserved_count,per_user_limit,active
FROM discount_codes WHERE lower(code)='stayhub15';
COMMIT;
