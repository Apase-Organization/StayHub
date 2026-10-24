-- Additive foundation only. V12 property-level exclusion is deliberately retained.
-- No inferred sale modes, historical prices or active commitments are backfilled.
ALTER TABLE properties
    ADD COLUMN sale_mode varchar(20) CHECK (sale_mode IN ('WHOLE_PLACE','ROOM_BASED')),
    ADD COLUMN timezone varchar(40) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh'
        CHECK (timezone = 'Asia/Ho_Chi_Minh'),
    ADD CONSTRAINT uq_properties_mode UNIQUE (id,sale_mode);
ALTER TABLE bookings ADD CONSTRAINT uq_bookings_property UNIQUE (id,property_id);

CREATE TABLE room_types (
    id bigserial PRIMARY KEY,
    property_id bigint NOT NULL,
    sale_mode varchar(20) NOT NULL,
    name varchar(255) NOT NULL,
    base_quantity integer NOT NULL CHECK (base_quantity > 0),
    max_adults integer NOT NULL CHECK (max_adults >= 1),
    max_occupants integer NOT NULL CHECK (max_occupants >= max_adults),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    metadata jsonb NOT NULL DEFAULT '{}',
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (id,property_id),
    FOREIGN KEY (property_id,sale_mode) REFERENCES properties(id,sale_mode),
    CHECK (sale_mode IN ('WHOLE_PLACE','ROOM_BASED')),
    CHECK (sale_mode <> 'WHOLE_PLACE' OR base_quantity = 1)
);
CREATE UNIQUE INDEX uq_whole_place_active_type ON room_types(property_id)
    WHERE sale_mode='WHOLE_PLACE';
-- Conservative foundation: keep the whole-place type identity even when archived,
-- so replacement types cannot create a second pool alongside old commitments.

CREATE TABLE rate_plans (
    id bigserial PRIMARY KEY,
    property_id bigint NOT NULL,
    room_type_id bigint NOT NULL,
    name varchar(255) NOT NULL,
    currency varchar(3) NOT NULL DEFAULT 'VND' CHECK (currency='VND'),
    policy_version varchar(100) NOT NULL,
    allowed_payment_modes jsonb NOT NULL DEFAULT '["FULL_PREPAY","HYBRID_DEPOSIT"]'
        CHECK (jsonb_typeof(allowed_payment_modes)='array'
            AND jsonb_array_length(allowed_payment_modes)>0
            AND allowed_payment_modes <@ '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb),
    pricing_restrictions jsonb NOT NULL DEFAULT '{}',
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (id,room_type_id,property_id),
    FOREIGN KEY (room_type_id,property_id) REFERENCES room_types(id,property_id)
);

CREATE TABLE type_day_inventory (
    room_type_id bigint NOT NULL REFERENCES room_types(id),
    stay_date date NOT NULL,
    total integer NOT NULL CHECK (total >= 0),
    out_of_order integer NOT NULL DEFAULT 0 CHECK (out_of_order >= 0 AND out_of_order <= total),
    stop_sale boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (room_type_id,stay_date)
);

CREATE TABLE booking_items (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL,
    property_id bigint NOT NULL,
    room_type_id bigint NOT NULL,
    rate_plan_id bigint NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    max_adults_snapshot integer NOT NULL CHECK (max_adults_snapshot >= 1),
    max_occupants_snapshot integer NOT NULL CHECK (max_occupants_snapshot >= max_adults_snapshot),
    type_plan_policy_snapshot jsonb NOT NULL,
    UNIQUE (id,room_type_id),
    FOREIGN KEY (booking_id,property_id) REFERENCES bookings(id,property_id),
    FOREIGN KEY (room_type_id,property_id) REFERENCES room_types(id,property_id),
    FOREIGN KEY (rate_plan_id,room_type_id,property_id) REFERENCES rate_plans(id,room_type_id,property_id)
);
CREATE INDEX idx_booking_items_booking ON booking_items(booking_id);
CREATE INDEX idx_booking_items_plan ON booking_items(rate_plan_id,room_type_id,property_id);

CREATE FUNCTION inventory_valid_children(ages jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE age jsonb;
BEGIN
    IF jsonb_typeof(ages)<>'array' THEN RETURN false; END IF;
    FOR age IN SELECT value FROM jsonb_array_elements(ages) LOOP
        IF jsonb_typeof(age)<>'number' THEN RETURN false; END IF;
        IF age::text !~ '^(0|[1-9][0-9]*)$' THEN RETURN false; END IF;
        IF (age::text)::numeric > 17 THEN RETURN false; END IF;
    END LOOP;
    RETURN true;
END $$;

CREATE TABLE room_allocations (
    item_id bigint NOT NULL REFERENCES booking_items(id),
    room_ordinal integer NOT NULL CHECK (room_ordinal >= 1),
    adults integer NOT NULL CHECK (adults >= 1),
    children_ages jsonb NOT NULL DEFAULT '[]' CHECK (inventory_valid_children(children_ages)),
    PRIMARY KEY (item_id,room_ordinal)
);
-- Unconstrained NUMERIC + explicit range/whole checks rejects fractions BEFORE
-- typmod rounding (NUMERIC(19,0), and even NUMERIC(19,2), can silently round).
CREATE DOMAIN inventory_vnd AS numeric CHECK (
    VALUE >= 0 AND VALUE <= 9999999999999999999 AND VALUE = trunc(VALUE)
);
CREATE TABLE nightly_snapshots (
    item_id bigint NOT NULL,
    room_ordinal integer NOT NULL,
    stay_date date NOT NULL,
    room_charge inventory_vnd NOT NULL,
    fees inventory_vnd NOT NULL DEFAULT 0,
    taxes inventory_vnd NOT NULL DEFAULT 0,
    discount_allocation inventory_vnd NOT NULL DEFAULT 0,
    policy_snapshot jsonb NOT NULL,
    PRIMARY KEY (item_id,room_ordinal,stay_date),
    FOREIGN KEY (item_id,room_ordinal) REFERENCES room_allocations(item_id,room_ordinal)
        DEFERRABLE INITIALLY DEFERRED,
    CHECK (discount_allocation <= room_charge + fees + taxes)
);
CREATE TABLE inventory_commitments (
    item_id bigint NOT NULL,
    room_type_id bigint NOT NULL,
    stay_date date NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    state varchar(20) NOT NULL CHECK (state IN ('HELD','CONFIRMED','RELEASED')),
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (item_id,stay_date),
    FOREIGN KEY (item_id,room_type_id) REFERENCES booking_items(id,room_type_id),
    FOREIGN KEY (room_type_id,stay_date) REFERENCES type_day_inventory(room_type_id,stay_date)
);
CREATE INDEX idx_inventory_active_day ON inventory_commitments(room_type_id,stay_date)
    INCLUDE (quantity) WHERE state IN ('HELD','CONFIRMED');

-- Existing snapshots/selection coordinates cannot be silently moved after creation.
CREATE FUNCTION inventory_immutable_selection() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'immutable inventory selection' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_booking_items BEFORE UPDATE ON booking_items
    FOR EACH ROW EXECUTE FUNCTION inventory_immutable_selection();
CREATE TRIGGER immutable_nightly_snapshots BEFORE UPDATE ON nightly_snapshots
    FOR EACH ROW EXECUTE FUNCTION inventory_immutable_selection();

-- Transaction rollback undoes construction without issuing DELETE. Once inserted,
-- snapshots cannot be removed and replaced at the same key to rewrite history.
CREATE FUNCTION inventory_snapshot_delete_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'inventory snapshot delete forbidden' USING ERRCODE='23514';
END $$;
CREATE TRIGGER inventory_snapshot_delete_guard BEFORE DELETE ON nightly_snapshots
    FOR EACH ROW EXECUTE FUNCTION inventory_snapshot_delete_guard();

CREATE FUNCTION inventory_allocation_key_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.item_id,NEW.room_ordinal) IS DISTINCT FROM (OLD.item_id,OLD.room_ordinal) THEN
        RAISE EXCEPTION 'immutable inventory allocation key' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_allocation_key_guard BEFORE UPDATE ON room_allocations
    FOR EACH ROW EXECUTE FUNCTION inventory_allocation_key_guard();

CREATE FUNCTION inventory_header_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.property_id,NEW.check_in_date,NEW.check_out_date) IS DISTINCT FROM
       (OLD.property_id,OLD.check_in_date,OLD.check_out_date)
       AND EXISTS (SELECT 1 FROM booking_items WHERE booking_id=OLD.id) THEN
        RAISE EXCEPTION 'immutable inventory booking range' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_header_guard BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION inventory_header_guard();

CREATE FUNCTION inventory_item_header_lock() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1 FROM bookings WHERE id=NEW.booking_id FOR UPDATE;
    -- Capture booking rights, not a permanent freeze on Host occupancy settings.
    -- SHARE conflicts with non-key capacity/status UPDATE; KEY SHARE does not.
    SELECT max_adults,max_occupants INTO NEW.max_adults_snapshot,NEW.max_occupants_snapshot
        FROM room_types WHERE id=NEW.room_type_id FOR SHARE;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_item_header_lock BEFORE INSERT ON booking_items
    FOR EACH ROW EXECUTE FUNCTION inventory_item_header_lock();

CREATE FUNCTION inventory_type_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.property_id,NEW.sale_mode)
       IS DISTINCT FROM (OLD.id,OLD.property_id,OLD.sale_mode) THEN
        RAISE EXCEPTION 'immutable inventory type scope' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_type_guard BEFORE UPDATE ON room_types
    FOR EACH ROW EXECUTE FUNCTION inventory_type_guard();

-- Deferred shape checks permit item + allocations + snapshots in one transaction,
-- but reject incomplete or out-of-range graphs at commit. Legacy headers untouched.
CREATE FUNCTION inventory_item_shape() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE iid bigint; i record; a record; n integer;
BEGIN
    IF TG_TABLE_NAME='booking_items' THEN iid := COALESCE(NEW.id,OLD.id);
    ELSE iid := COALESCE(NEW.item_id,OLD.item_id); END IF;
    SELECT bi.*,b.check_in_date,b.check_out_date INTO i
        FROM booking_items bi JOIN bookings b ON b.id=bi.booking_id
        WHERE bi.id=iid;
    IF NOT FOUND THEN RETURN NULL; END IF;
    IF i.check_out_date-i.check_in_date NOT BETWEEN 1 AND 30 THEN
        RAISE EXCEPTION 'inventory stay range' USING ERRCODE='23514'; END IF;
    SELECT count(*) INTO n FROM room_allocations WHERE item_id=iid;
    IF n<>i.quantity THEN RAISE EXCEPTION 'inventory allocation count' USING ERRCODE='23514'; END IF;
    FOR a IN SELECT * FROM room_allocations WHERE item_id=iid LOOP
        IF a.room_ordinal>i.quantity OR a.adults>i.max_adults_snapshot OR
           a.adults+jsonb_array_length(a.children_ages)>i.max_occupants_snapshot THEN
            RAISE EXCEPTION 'inventory allocation occupancy/ordinal' USING ERRCODE='23514'; END IF;
    END LOOP;
    IF EXISTS (SELECT 1 FROM nightly_snapshots WHERE item_id=iid
        AND (stay_date<i.check_in_date OR stay_date>=i.check_out_date)) THEN
        RAISE EXCEPTION 'inventory snapshot date' USING ERRCODE='23514'; END IF;
    SELECT count(*) INTO n FROM nightly_snapshots WHERE item_id=iid;
    IF n::bigint<>i.quantity::bigint*(i.check_out_date-i.check_in_date) THEN
        RAISE EXCEPTION 'inventory snapshot count' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER inventory_item_shape AFTER INSERT OR UPDATE OR DELETE ON booking_items
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION inventory_item_shape();
CREATE CONSTRAINT TRIGGER inventory_allocation_shape AFTER INSERT OR UPDATE OR DELETE ON room_allocations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION inventory_item_shape();
CREATE CONSTRAINT TRIGGER inventory_snapshot_shape AFTER INSERT OR UPDATE OR DELETE ON nightly_snapshots
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION inventory_item_shape();

CREATE FUNCTION inventory_day_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE used bigint; mode varchar;
BEGIN
    IF TG_OP='UPDATE' AND (NEW.room_type_id,NEW.stay_date) IS DISTINCT FROM (OLD.room_type_id,OLD.stay_date) THEN
        RAISE EXCEPTION 'immutable inventory day key' USING ERRCODE='23514'; END IF;
    SELECT sale_mode INTO mode FROM room_types WHERE id=NEW.room_type_id;
    IF mode='WHOLE_PLACE' AND NEW.total>1 THEN
        RAISE EXCEPTION 'whole place capacity is one' USING ERRCODE='23514'; END IF;
    SELECT COALESCE(sum(quantity),0) INTO used FROM inventory_commitments
        WHERE room_type_id=NEW.room_type_id AND stay_date=NEW.stay_date AND state IN ('HELD','CONFIRMED');
    IF used>NEW.total::bigint-NEW.out_of_order THEN
        RAISE EXCEPTION 'inventory capacity below commitments' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_day_guard BEFORE INSERT OR UPDATE ON type_day_inventory
    FOR EACH ROW EXECUTE FUNCTION inventory_day_guard();

CREATE FUNCTION inventory_commitment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE i record; d record; t record; p record; used bigint;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'inventory commitments must transition RELEASED, not delete' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' THEN
        IF (NEW.item_id,NEW.room_type_id,NEW.stay_date,NEW.quantity,NEW.expires_at) IS DISTINCT FROM
           (OLD.item_id,OLD.room_type_id,OLD.stay_date,OLD.quantity,OLD.expires_at) THEN
            RAISE EXCEPTION 'immutable inventory commitment coordinates/quantity/deadline' USING ERRCODE='23514'; END IF;
        IF NOT (NEW.state=OLD.state OR (OLD.state='HELD' AND NEW.state IN ('CONFIRMED','RELEASED'))
                OR (OLD.state='CONFIRMED' AND NEW.state='RELEASED')) THEN
            RAISE EXCEPTION 'invalid inventory transition' USING ERRCODE='23514'; END IF;
    ELSIF NEW.state<>'HELD' THEN
        RAISE EXCEPTION 'new inventory commitment must be HELD' USING ERRCODE='23514';
    END IF;
    SELECT bi.quantity,bi.room_type_id,bi.rate_plan_id,b.check_in_date,b.check_out_date,b.id AS booking_id INTO i
        FROM booking_items bi JOIN bookings b ON b.id=bi.booking_id
        WHERE bi.id=NEW.item_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'inventory item missing' USING ERRCODE='23503'; END IF;
    PERFORM 1 FROM bookings WHERE id=i.booking_id FOR UPDATE;
    IF NEW.quantity<>i.quantity OR NEW.stay_date<i.check_in_date OR NEW.stay_date>=i.check_out_date THEN
        RAISE EXCEPTION 'inventory commitment item quantity/date' USING ERRCODE='23514'; END IF;
    IF TG_OP='INSERT' THEN
        -- Global protocol: booking -> types by id -> plans by id -> days sorted.
        -- The service prelocks ALL resources in that order; direct single-row SQL
        -- admission uses the same order here. Arbitrary reversed multirow writers
        -- can be deadlock-aborted by PostgreSQL, never admitted without locks.
        SELECT status,max_adults,max_occupants INTO t FROM room_types
            WHERE id=i.room_type_id FOR SHARE;
        SELECT status INTO p FROM rate_plans WHERE id=i.rate_plan_id FOR SHARE;
        IF t.status<>'ACTIVE' OR p.status<>'ACTIVE' THEN
            RAISE EXCEPTION 'inventory type/plan archived' USING ERRCODE='23514'; END IF;
        IF EXISTS (SELECT 1 FROM room_allocations WHERE item_id=NEW.item_id
            AND (adults>t.max_adults OR adults+jsonb_array_length(children_ages)>t.max_occupants)) THEN
            RAISE EXCEPTION 'inventory current occupancy capacity' USING ERRCODE='23514'; END IF;
    END IF;
    -- Actual write serializes snapshots at READ COMMITTED; stronger isolation
    -- aborts conflicting writers rather than accepting a stale SUM.
    UPDATE type_day_inventory SET version=version+1
        WHERE room_type_id=NEW.room_type_id AND stay_date=NEW.stay_date RETURNING * INTO d;
    IF NOT FOUND THEN RAISE EXCEPTION 'inventory calendar missing' USING ERRCODE='23503'; END IF;
    IF TG_OP='INSERT' AND d.stop_sale THEN
        RAISE EXCEPTION 'inventory stop_sale' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND OLD.state='HELD' AND NEW.state='CONFIRMED' AND clock_timestamp()>=OLD.expires_at THEN
        RAISE EXCEPTION 'inventory hold expired' USING ERRCODE='23514'; END IF;
    SELECT COALESCE(sum(quantity),0) INTO used FROM inventory_commitments
        WHERE room_type_id=NEW.room_type_id AND stay_date=NEW.stay_date
        AND state IN ('HELD','CONFIRMED') AND item_id<>NEW.item_id;
    IF NEW.state IN ('HELD','CONFIRMED') THEN used:=used+NEW.quantity; END IF;
    IF used>d.total::bigint-d.out_of_order THEN
        RAISE EXCEPTION 'inventory capacity exhausted' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_commitment_guard BEFORE INSERT OR UPDATE OR DELETE ON inventory_commitments
    FOR EACH ROW EXECUTE FUNCTION inventory_commitment_guard();
