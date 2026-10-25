-- TSK-094 immutable listing/terms snapshots and TSK-104 item-aware notification
-- payloads. Payloads are persisted in the booking transaction; TSK-076 owns send/retry.
CREATE TABLE booking_listing_snapshots (
    booking_id bigint PRIMARY KEY REFERENCES bookings(id) ON DELETE CASCADE,
    property_id bigint NOT NULL REFERENCES properties(id),
    property_updated_at timestamptz NOT NULL,
    listing_snapshot jsonb NOT NULL,
    terms_snapshot jsonb NOT NULL,
    snapshot_hash text NOT NULL CHECK (snapshot_hash ~ '^[a-f0-9]{64}$'),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE FUNCTION stayhub_immutable_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'booking snapshot is immutable' USING ERRCODE='23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (SELECT 1 FROM bookings WHERE id = OLD.booking_id) THEN
            RAISE EXCEPTION 'booking snapshot is immutable' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN OLD;
END $$;
CREATE TRIGGER booking_listing_snapshot_immutable BEFORE UPDATE OR DELETE ON booking_listing_snapshots
    FOR EACH ROW EXECUTE FUNCTION stayhub_immutable_snapshot();

CREATE FUNCTION capture_booking_listing_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p record; listing jsonb; terms jsonb; snapshot_digest text;
BEGIN
    SELECT id,title,description,address,city,property_type,bedrooms,beds,bathrooms,cleaning_fee,price_per_night,
           timezone,updated_at INTO p FROM properties WHERE id=NEW.property_id;
    SELECT jsonb_build_object(
        'title',p.title,'description',p.description,'address',p.address,'city',p.city,
        'propertyType',p.property_type,'bedrooms',p.bedrooms,'beds',p.beds,'bathrooms',p.bathrooms,
        'coverImage', (SELECT image_url FROM property_images WHERE property_id=p.id ORDER BY is_cover DESC,display_order,id LIMIT 1),
        'amenities', coalesce((SELECT jsonb_agg(a.name ORDER BY a.name) FROM property_amenities pa
            JOIN amenities a ON a.id=pa.amenity_id WHERE pa.property_id=p.id),'[]'::jsonb)
    ) INTO listing;
    SELECT jsonb_build_object(
        'checkIn',NEW.check_in_date,'checkOut',NEW.check_out_date,'guests',NEW.guests,
        'currency',NEW.inventory_currency,'paymentMode',NEW.payment_mode,
        'policyVersion',NEW.inventory_policy_version,'inventoryRequestHash',NEW.inventory_request_hash,
        'nightlyPrice',NEW.nightly_price,'cleaningFee',NEW.cleaning_fee,'serviceFee',NEW.service_fee,
        'subtotal',NEW.subtotal_price,'discountCodeId',NEW.discount_code_id,
        'discountAmount',NEW.discount_amount,'total',NEW.total_price,
        'financialSnapshot',(SELECT jsonb_build_object('mode',s.payment_mode,'currency',s.currency,
            'roomCharges',s.room_charges,'cleaningFee',s.cleaning_fee,'subtotal',s.subtotal,
            'discount',s.discount,'total',s.total_stay,'dueOnline',s.due_online,
            'balanceDueHost',s.balance_due_host,'policyVersion',s.policy_version,
            'fixtureVersion',s.fixture_version,'quoteHash',s.quote_hash)
            FROM booking_financial_snapshots s WHERE s.booking_id=NEW.id)
    ) INTO terms;
    snapshot_digest:=md5(listing::text||terms::text)||md5(terms::text||listing::text);
    INSERT INTO booking_listing_snapshots(booking_id,property_id,property_updated_at,listing_snapshot,terms_snapshot,snapshot_hash)
        VALUES (NEW.id,NEW.property_id,p.updated_at,listing,terms,snapshot_digest);
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER booking_listing_snapshot_capture AFTER INSERT ON bookings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION capture_booking_listing_snapshot();

CREATE TABLE booking_notification_outbox (
    id bigserial PRIMARY KEY,
    booking_id bigint NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    event_key text NOT NULL UNIQUE,
    event_type text NOT NULL CHECK (event_type IN ('BOOKING_CONFIRMED','BOOKING_REJECTED','BOOKING_CANCELLED','BOOKING_COMPLETED')),
    status text NOT NULL CHECK (status IN ('CONFIRMED','REJECTED','CANCELLED','COMPLETED')),
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    dispatched_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts>=0),
    last_error_code text
);
CREATE INDEX idx_booking_notification_outbox_pending ON booking_notification_outbox(created_at,id)
    WHERE dispatched_at IS NULL;

CREATE FUNCTION enqueue_booking_status_notification() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE event_name text; body jsonb;
BEGIN
    IF NEW.status IS NOT DISTINCT FROM OLD.status THEN RETURN NEW; END IF;
    IF NEW.status NOT IN ('CONFIRMED','REJECTED','CANCELLED','COMPLETED') THEN RETURN NEW; END IF;
    event_name:='BOOKING_'||NEW.status;
    SELECT jsonb_build_object(
        'bookingId',NEW.id,'eventType',event_name,'status',NEW.status,
        'guestId',NEW.guest_id,'propertyId',NEW.property_id,
        'listing',s.listing_snapshot,'terms',s.terms_snapshot,
        'items',coalesce((
            SELECT jsonb_agg(jsonb_build_object(
                'itemId',bi.id,'roomTypeId',bi.room_type_id,'roomType',rt.name,
                'ratePlanId',bi.rate_plan_id,'ratePlan',rp.name,'quantity',bi.quantity,
                'roomNights',bi.quantity*(NEW.check_out_date-NEW.check_in_date),
                'total',coalesce((SELECT sum(ns.room_charge+ns.fees+ns.taxes-ns.discount_allocation)
                    FROM nightly_snapshots ns WHERE ns.item_id=bi.id),0)
            ) ORDER BY bi.id)
            FROM booking_items bi JOIN room_types rt ON rt.id=bi.room_type_id
                JOIN rate_plans rp ON rp.id=bi.rate_plan_id WHERE bi.booking_id=NEW.id
        ),'[]'::jsonb)
    ) INTO body FROM booking_listing_snapshots s WHERE s.booking_id=NEW.id;
    IF body IS NULL THEN
        body:=jsonb_build_object('bookingId',NEW.id,'eventType',event_name,'status',NEW.status,
            'guestId',NEW.guest_id,'propertyId',NEW.property_id,'items','[]'::jsonb);
    END IF;
    INSERT INTO booking_notification_outbox(booking_id,event_key,event_type,status,payload)
    VALUES (NEW.id,'booking-status:'||NEW.id||':'||NEW.status,event_name,NEW.status,body)
    ON CONFLICT (event_key) DO NOTHING;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER booking_status_notification_outbox AFTER UPDATE ON bookings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enqueue_booking_status_notification();
