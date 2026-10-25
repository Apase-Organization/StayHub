-- TSK-085 refund policy math uses immutable charge-level source allocations.
CREATE TABLE payment_collection_allocations (
    id bigserial PRIMARY KEY,
    collection_id bigint NOT NULL,
    booking_id bigint NOT NULL,
    source text NOT NULL CHECK (source IN ('ONLINE_VERIFIED','HOST_ATTESTED')),
    line_key text NOT NULL CHECK (length(line_key) BETWEEN 1 AND 200),
    charge_type text NOT NULL CHECK (charge_type IN ('ROOM','CLEANING','TAX')),
    item_id bigint,
    room_ordinal integer,
    stay_date date,
    net_amount inventory_vnd NOT NULL CHECK (net_amount>0),
    allocated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (collection_id,booking_id) REFERENCES payment_collections(id,booking_id),
    FOREIGN KEY (item_id,room_ordinal,stay_date) REFERENCES nightly_snapshots(item_id,room_ordinal,stay_date),
    UNIQUE (collection_id,line_key),
    CHECK ((item_id IS NULL AND room_ordinal IS NULL AND stay_date IS NULL)
        OR (item_id IS NOT NULL AND room_ordinal IS NOT NULL AND stay_date IS NOT NULL))
);
CREATE INDEX idx_payment_collection_allocations_booking_source ON payment_collection_allocations(booking_id,source,charge_type);
CREATE TRIGGER immutable_payment_collection_allocations BEFORE UPDATE OR DELETE ON payment_collection_allocations
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION payment_collection_allocation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c record; total numeric;
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    SELECT source,amount,disposition INTO c FROM payment_collections WHERE id=NEW.collection_id AND booking_id=NEW.booking_id;
    IF NOT FOUND OR c.source<>NEW.source OR c.disposition NOT IN ('ACCEPTED','MEMO') THEN
        RAISE EXCEPTION 'collection allocation source/disposition binding' USING ERRCODE='23514';
    END IF;
    SELECT coalesce(sum(net_amount),0) INTO total FROM payment_collection_allocations WHERE collection_id=NEW.collection_id;
    IF total+NEW.net_amount>c.amount THEN
        RAISE EXCEPTION 'source allocations exceed actual collection' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payment_collection_allocation_guard BEFORE INSERT ON payment_collection_allocations
    FOR EACH ROW EXECUTE FUNCTION payment_collection_allocation_guard();
CREATE FUNCTION payment_collection_allocations_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE cid bigint; c record; allocated numeric;
BEGIN
    cid:=NEW.id;
    IF NEW.disposition IN ('ACCEPTED','MEMO') THEN
        SELECT * INTO c FROM payment_collections WHERE id=cid;
        SELECT coalesce(sum(net_amount),0) INTO allocated FROM payment_collection_allocations WHERE collection_id=cid;
        IF allocated<>c.amount THEN
            RAISE EXCEPTION 'accepted collection requires exact charge source allocation' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payment_collection_allocations_complete
    AFTER INSERT ON payment_collections DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION payment_collection_allocations_complete();
