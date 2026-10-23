package com.stayhub.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ConflictException;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.common.validation.StayDatePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates inventory-only holds from server-owned rate-plan prices; payment settlement is separate. */
@Service
public class InventoryBookingService {
    private static final String POLICY_VERSION = "s0-policy-v1";
    private final JdbcTemplate jdbc;
    private final InventoryCapacityService capacity;
    private final ObjectMapper mapper;
    private final InventoryDiscountReservationService discounts;

    @Autowired
    public InventoryBookingService(JdbcTemplate jdbc, InventoryCapacityService capacity, ObjectMapper mapper,
                                   InventoryDiscountReservationService discounts) {
        this.jdbc = jdbc;
        this.capacity = capacity;
        this.mapper = mapper;
        this.discounts = discounts;
    }

    public InventoryBookingService(JdbcTemplate jdbc, InventoryCapacityService capacity, ObjectMapper mapper) {
        this(jdbc,capacity,mapper,null);
    }

    @Transactional
    public HoldCreated create(long guestId, InventoryBookingRequest request) {
        return createInternal(guestId, request, null);
    }

    @Transactional
    public HoldCreated createQuoted(long guestId, InventoryBookingRequest request, BookingPricing pricing) {
        if (pricing == null || pricing.quoteHash() == null) throw new IllegalArgumentException("verified quote pricing required");
        HoldCreated created=createInternal(guestId, request, pricing);
        if(pricing.discountCodeId()!=null && pricing.discount().signum()>0) {
            if(discounts==null) throw new IllegalStateException("discount reservation service unavailable");
            discounts.reserve(created.bookingId(),guestId,pricing.discountCodeId(),pricing.discountCode(),pricing.discount(),created.expiresAt());
        }
        return created;
    }

    private HoldCreated createInternal(long guestId, InventoryBookingRequest request, BookingPricing pricing) {
        validate(request);
        String requestHash = pricing == null || pricing.requestHash() == null ? hash(request) : pricing.requestHash();
        jdbc.execute("SELECT pg_advisory_xact_lock(" + advisoryKey(guestId, request.idempotencyKey()) + ")");
        List<HoldCreated> replay = jdbc.query("""
                SELECT id,inventory_request_hash,inventory_hold_expires_at FROM bookings
                WHERE guest_id=? AND inventory_idempotency_key=?
                """, (rs, row) -> {
                    if (!requestHash.equals(rs.getString("inventory_request_hash")))
                        throw new ConflictException("ERR_IDEMPOTENCY_CONFLICT", "The idempotency key was already used for another request.");
                    return new HoldCreated(rs.getLong("id"), rs.getTimestamp("inventory_hold_expires_at").toInstant(), true);
                }, guestId, request.idempotencyKey());
        if (!replay.isEmpty()) return replay.getFirst();

        PropertySnapshot property = jdbc.query("""
                SELECT id,sale_mode,cleaning_fee,timezone FROM properties
                WHERE id=? AND status='ACTIVE' FOR SHARE
                """, (rs, row) -> new PropertySnapshot(rs.getLong("id"), rs.getString("sale_mode"),
                rs.getBigDecimal("cleaning_fee"), rs.getString("timezone")), request.propertyId()).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        if (property.saleMode() == null) invalid("ERR_ROOM_NOT_AVAILABLE", "The property has no configured room inventory.");
        int nights = StayDatePolicy.validate(request.checkIn(), request.checkOut(), ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));

        List<PlanSnapshot> plans = new ArrayList<>();
        Set<Long> types = new HashSet<>();
        Set<String> uniquePairs = new HashSet<>();
        int adults = 0;
        int children = 0;
        for (InventoryBookingRequest.Item item : request.items()) {
            if (item == null || item.roomAllocations() == null || item.roomAllocations().stream().anyMatch(java.util.Objects::isNull))
                invalid("ERR_INVALID_REQUEST", "Every selected room needs a valid allocation.");
            if (item.quantity() != item.roomAllocations().size()) invalid("ERR_INVALID_OCCUPANCY", "Each booked room needs one occupancy allocation.");
            if (!uniquePairs.add(item.roomTypeId() + ":" + item.ratePlanId())) invalid("ERR_INVALID_REQUEST", "Combine duplicate room type and plan selections.");
            PlanSnapshot plan = jdbc.query("""
                    SELECT t.id AS type_id,t.name AS type_name,t.sale_mode,t.status AS type_status,
                           t.max_adults,t.max_occupants,t.base_quantity,t.version AS type_version,
                           p.id AS plan_id,p.name AS plan_name,p.policy_version,p.nightly_price,
                           p.allowed_payment_modes::text AS allowed_modes,p.pricing_restrictions::text AS restrictions,
                           p.status AS plan_status,p.version AS plan_version
                    FROM room_types t JOIN rate_plans p ON p.room_type_id=t.id AND p.property_id=t.property_id
                    WHERE t.id=? AND p.id=? AND t.property_id=?
                    """, (rs, row) -> new PlanSnapshot(rs.getLong("type_id"), rs.getString("type_name"),
                    rs.getString("sale_mode"), rs.getString("type_status"), rs.getInt("max_adults"), rs.getInt("max_occupants"),
                    rs.getInt("base_quantity"), rs.getLong("type_version"), rs.getLong("plan_id"), rs.getString("plan_name"),
                    rs.getString("policy_version"), rs.getBigDecimal("nightly_price"), rs.getString("allowed_modes"),
                    rs.getString("restrictions"),
                    rs.getString("plan_status"), rs.getLong("plan_version")), item.roomTypeId(), item.ratePlanId(), request.propertyId())
                    .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Room type or rate plan not found"));
            if (!"ACTIVE".equals(plan.typeStatus()) || !"ACTIVE".equals(plan.planStatus()) || plan.nightlyPrice() == null)
                invalid("ERR_ROOM_NOT_AVAILABLE", "The room type or rate plan is inactive or has no server price.");
            if (!plan.saleMode().equals(property.saleMode()) || ("WHOLE_PLACE".equals(plan.saleMode()) && item.quantity() != 1))
                invalid("ERR_INVALID_OCCUPANCY", "The selected quantity does not match the property's sale mode.");
            if (item.quantity() > plan.baseQuantity()) invalid("ERR_ROOM_NOT_AVAILABLE", "Requested room quantity exceeds the type's configured capacity.");
            if (!plan.allowedModes().contains("\"" + request.paymentMode().name() + "\""))
                invalid("ERR_PAYMENT_MODE_NOT_ALLOWED", "The selected rate plan does not allow this payment mode.");
            if (planStopSale(plan.pricingRestrictions(), request.checkIn(), request.checkOut()))
                invalid("ERR_ROOM_NOT_AVAILABLE", "The selected rate plan is not selling for every night.");
            for (InventoryBookingRequest.RoomAllocation allocation : item.roomAllocations()) {
                if (allocation.childrenAges() == null || allocation.adults() < 1 || allocation.adults() > plan.maxAdults()
                        || allocation.childrenAges().stream().anyMatch(age -> age == null || age < 0 || age > 17)
                        || allocation.adults() + allocation.childrenAges().size() > plan.maxOccupants())
                    invalid("ERR_INVALID_OCCUPANCY", "Room occupancy exceeds the selected type's limits.");
                adults += allocation.adults();
                children += allocation.childrenAges().size();
            }
            types.add(item.roomTypeId());
            plans.add(plan);
        }
        if ("WHOLE_PLACE".equals(property.saleMode()) && (request.items().size() != 1 || request.items().getFirst().quantity() != 1))
            invalid("ERR_INVALID_OCCUPANCY", "A whole-place booking must contain exactly one unit.");

        long roomNights = request.items().stream().mapToLong(i -> (long) i.quantity() * nights).sum();
        BigDecimal roomTotal = plans.stream().map(plan -> request.items().stream()
                .filter(item -> item.roomTypeId() == plan.typeId() && item.ratePlanId() == plan.planId())
                .map(item -> plan.nightlyPrice().multiply(BigDecimal.valueOf(item.quantity() * (long) nights)))
                .findFirst().orElse(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cleaningFee;
        try { cleaningFee = property.cleaningFee().setScale(0, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { invalid("ERR_INVALID_AMOUNT", "The property cleaning fee must use whole VND."); return null; }
        BigDecimal subtotal = roomTotal.add(cleaningFee);
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal totalPrice = subtotal;
        if (pricing != null) {
            if (roomTotal.compareTo(pricing.roomCharges())!=0 || cleaningFee.compareTo(pricing.cleaningFee())!=0
                    || !POLICY_VERSION.equals(pricing.policyVersion()) || !"s0-fixtures-v1".equals(pricing.fixtureVersion()))
                throw new ConflictException("ERR_QUOTE_STALE", "Current inventory price no longer matches the accepted quote.");
            discountAmount = pricing.discount();
            subtotal = pricing.subtotal();
            totalPrice = pricing.totalStay();
            if (subtotal.compareTo(roomTotal.add(cleaningFee))!=0 || discountAmount.signum()<0
                    || totalPrice.compareTo(subtotal.subtract(discountAmount))!=0)
                invalid("ERR_INVALID_AMOUNT", "Quoted booking totals do not reconcile.");
        }
        if (roomTotal.signum() <= 0 || subtotal.signum() <= 0 || totalPrice.signum() <= 0)
            invalid("ERR_NONPOSITIVE_BOOKING_AMOUNT", "A booking total must be positive.");
        BigDecimal headerNightly = roomTotal.divide(BigDecimal.valueOf(roomNights), 2, RoundingMode.HALF_UP);

        Long bookingId = jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,
                    service_fee,subtotal_price,discount_code_id,discount_amount,total_price,status,payment_mode,inventory_policy_version,
                    inventory_idempotency_key,inventory_request_hash,inventory_hold_expires_at)
                VALUES (?,?,?,?,?,?,?,0,?,?,?,?, 'PENDING_PAYMENT',?,?,?, ?,CURRENT_TIMESTAMP+interval '15 minutes')
                RETURNING id
                """, Long.class, request.propertyId(), guestId, request.checkIn(), request.checkOut(), adults + children,
                headerNightly, cleaningFee, subtotal, pricing == null ? null : pricing.discountCodeId(), discountAmount,
                totalPrice, request.paymentMode().name(), POLICY_VERSION,
                request.idempotencyKey(), requestHash);

        // Establish the global order before row triggers and the hold service: booking, sorted types, sorted plans, sorted days.
        jdbc.queryForObject("SELECT id FROM bookings WHERE id=? FOR UPDATE", Long.class, bookingId);
        for (long typeId : types.stream().sorted().toList()) jdbc.queryForObject("SELECT id FROM room_types WHERE id=? FOR SHARE", Long.class, typeId);
        for (long planId : plans.stream().map(PlanSnapshot::planId).sorted().toList()) jdbc.queryForObject("SELECT id FROM rate_plans WHERE id=? FOR SHARE", Long.class, planId);
        List<InventoryBookingRequest.Item> sortedItems = request.items().stream().sorted(
                java.util.Comparator.comparingLong(InventoryBookingRequest.Item::roomTypeId)
                        .thenComparingLong(InventoryBookingRequest.Item::ratePlanId)).toList();
        List<SnapshotRow> snapshots = new ArrayList<>();
        for (InventoryBookingRequest.Item item : sortedItems) {
            PlanSnapshot plan = plans.stream().filter(p -> p.typeId() == item.roomTypeId() && p.planId() == item.ratePlanId()).findFirst().orElseThrow();
            String policySnapshot = json(new PolicySnapshot(POLICY_VERSION, property.timezone(), property.saleMode(),
                    request.currency(), request.paymentMode(), plan.typeName(), plan.planName(), plan.policyVersion(),
                    plan.allowedModes(), plan.nightlyPrice(), plan.typeVersion(), plan.planVersion(),
                    pricing == null ? null : pricing.quoteHash()));
            long itemId = jdbc.queryForObject("""
                    INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,
                        max_adults_snapshot,max_occupants_snapshot,type_plan_policy_snapshot)
                    VALUES (?,?,?,?,?,?,?,?::jsonb) RETURNING id
                    """, Long.class, bookingId, request.propertyId(), item.roomTypeId(), item.ratePlanId(),
                    item.quantity(), plan.maxAdults(), plan.maxOccupants(), policySnapshot);
            for (int index = 0; index < item.roomAllocations().size(); index++) {
                int ordinal = index + 1;
                InventoryBookingRequest.RoomAllocation allocation = item.roomAllocations().get(index);
                jdbc.update("INSERT INTO room_allocations(item_id,room_ordinal,adults,children_ages) VALUES (?,?,?,?::jsonb)",
                        itemId, ordinal, allocation.adults(), json(allocation.childrenAges()));
                for (LocalDate date = request.checkIn(); date.isBefore(request.checkOut()); date = date.plusDays(1)) {
                    snapshots.add(new SnapshotRow(itemId, ordinal, date, plan.nightlyPrice(), BigDecimal.ZERO,
                            BigDecimal.ZERO, policySnapshot));
                }
            }
        }
        if (!snapshots.isEmpty()) snapshots.set(0, snapshots.getFirst().withFees(cleaningFee));
        List<BigDecimal> discountAllocations = allocateDiscount(snapshots, discountAmount, subtotal);
        for (int index = 0; index < snapshots.size(); index++) {
            SnapshotRow row = snapshots.get(index);
            jdbc.update("""
                    INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,fees,taxes,discount_allocation,policy_snapshot)
                    VALUES (?,?,?,?::inventory_vnd,?::inventory_vnd,?::inventory_vnd,?::inventory_vnd,?::jsonb)
                    """, row.itemId(), row.ordinal(), row.date(), row.roomCharge(), row.fees(), row.taxes(),
                    discountAllocations.get(index), row.policySnapshot());
        }
        Instant expires = jdbc.queryForObject("SELECT inventory_hold_expires_at FROM bookings WHERE id=?", java.sql.Timestamp.class, bookingId).toInstant();
        capacity.holdAll(bookingId, expires);
        return new HoldCreated(bookingId, expires, false);
    }

    @Transactional
    public void cancel(long guestId, long bookingId) {
        int changed = jdbc.update("""
                UPDATE bookings SET status='CANCELLED',cancelled_at=clock_timestamp(),inventory_version=inventory_version+1
                WHERE id=? AND guest_id=? AND status='PENDING_PAYMENT' AND inventory_hold_expires_at>clock_timestamp()
                """, bookingId, guestId);
        if (changed != 1) throw new ResourceNotFoundException("Active booking hold not found");
        capacity.releaseAll(bookingId);
        if(discounts!=null) discounts.release(bookingId);
    }

    @Transactional
    public void expire(long bookingId) {
        int changed = jdbc.update("""
                UPDATE bookings SET status='CANCELLED',cancelled_at=clock_timestamp(),inventory_version=inventory_version+1
                WHERE id=? AND status='PENDING_PAYMENT' AND inventory_hold_expires_at IS NOT NULL AND (
                    inventory_hold_expires_at<=clock_timestamp() OR (
                        (SELECT count(*) FROM payment_attempts a WHERE a.booking_id=bookings.id)=3
                        AND NOT EXISTS (SELECT 1 FROM payment_collections c WHERE c.booking_id=bookings.id AND c.disposition='ACCEPTED')
                        AND NOT EXISTS (SELECT 1 FROM payment_attempts a WHERE a.booking_id=bookings.id AND NOT (
                            EXISTS (SELECT 1 FROM payment_attempt_outcomes o WHERE o.attempt_id=a.id AND o.outcome='CREATION_FAILED')
                            OR EXISTS (SELECT 1 FROM inventory_provider_events e WHERE e.attempt_id=a.id AND e.disposition='FAILED')
                        ))
                    )
                )
                """, bookingId);
        if (changed == 1) {
            jdbc.update("""
                    UPDATE payments SET status=CASE
                        WHEN (SELECT inventory_hold_expires_at FROM bookings WHERE id=?)<=clock_timestamp()
                        THEN 'EXPIRED' ELSE 'FAILED' END
                    WHERE booking_id=? AND status='PENDING'
                    """,bookingId,bookingId);
            List<String> paymentStatuses=jdbc.queryForList("SELECT status FROM payments WHERE booking_id=? AND status IN ('FAILED','EXPIRED')",String.class,bookingId);
            if(!paymentStatuses.isEmpty()) {
                String paymentStatus=paymentStatuses.getFirst();
                String eventType="EXPIRED".equals(paymentStatus)?"PAYMENT_EXPIRED":"PAYMENT_FAILED";
                jdbc.update("""
                        INSERT INTO inventory_payment_outbox(booking_id,event_type,event_key,payment_status)
                        VALUES (?,?,?,?) ON CONFLICT (event_key) DO NOTHING
                        """,bookingId,eventType,"booking-terminal:"+bookingId,paymentStatus);
            }
            capacity.releaseAll(bookingId);
            if(discounts!=null) discounts.release(bookingId);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findExpired(int limit) {
        return jdbc.queryForList("""
                SELECT b.id FROM bookings b WHERE b.status='PENDING_PAYMENT' AND b.inventory_hold_expires_at IS NOT NULL AND (
                    b.inventory_hold_expires_at<=clock_timestamp() OR (
                        (SELECT count(*) FROM payment_attempts a WHERE a.booking_id=b.id)=3
                        AND NOT EXISTS (SELECT 1 FROM payment_collections c WHERE c.booking_id=b.id AND c.disposition='ACCEPTED')
                        AND NOT EXISTS (SELECT 1 FROM payment_attempts a WHERE a.booking_id=b.id AND NOT (
                            EXISTS (SELECT 1 FROM payment_attempt_outcomes o WHERE o.attempt_id=a.id AND o.outcome='CREATION_FAILED')
                            OR EXISTS (SELECT 1 FROM inventory_provider_events e WHERE e.attempt_id=a.id AND e.disposition='FAILED')
                        ))
                    )
                ) ORDER BY b.inventory_hold_expires_at,b.id LIMIT ?
                """, Long.class, Math.max(1, Math.min(limit, 500)));
    }

    private void validate(InventoryBookingRequest r) {
        if (r == null || r.items() == null || r.items().isEmpty() || r.idempotencyKey() == null || r.idempotencyKey().isBlank())
            invalid("ERR_INVALID_REQUEST", "A selection and idempotency key are required.");
        if (r.paymentMode() == null) invalid("ERR_INVALID_REQUEST", "Payment mode is required.");
        if (!"VND".equals(r.currency())) invalid("ERR_INVALID_CURRENCY", "Inventory bookings must use VND.");
        if (!r.idempotencyKey().matches("[A-Za-z0-9._:-]{1,100}")) invalid("ERR_INVALID_REQUEST", "Invalid idempotency key.");
        if (r.checkIn() == null || r.checkOut() == null || !r.checkIn().isBefore(r.checkOut())) invalid("ERR_INVALID_DATES", "Check-out must follow check-in.");
    }
    private String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value))); }
        catch (NoSuchAlgorithmException | JsonProcessingException e) { throw new IllegalStateException("Cannot hash booking request", e); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode inventory snapshot", e); }
    }
    private List<BigDecimal> allocateDiscount(List<SnapshotRow> rows, BigDecimal discount, BigDecimal subtotal) {
        List<BigDecimal> allocations = new ArrayList<>(rows.size());
        List<DiscountRemainder> remainders = new ArrayList<>(rows.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index=0; index<rows.size(); index++) {
            SnapshotRow row=rows.get(index);
            BigDecimal base=row.roomCharge().add(row.fees()).add(row.taxes());
            BigDecimal numerator=discount.multiply(base);
            BigDecimal floor=numerator.divide(subtotal,0,RoundingMode.DOWN);
            allocations.add(floor);
            allocated=allocated.add(floor);
            remainders.add(new DiscountRemainder(index,numerator.subtract(floor.multiply(subtotal))));
        }
        int remaining=discount.subtract(allocated).intValueExact();
        remainders.sort(java.util.Comparator.comparing(DiscountRemainder::remainder).reversed().thenComparingInt(DiscountRemainder::index));
        for(int i=0;i<remaining;i++) allocations.set(remainders.get(i).index(),allocations.get(remainders.get(i).index()).add(BigDecimal.ONE));
        return allocations;
    }
    private boolean planStopSale(String json, LocalDate checkIn, LocalDate checkOut) {
        try {
            var dates = mapper.readTree(json).path("stopSaleDates");
            for (var date : dates) {
                LocalDate blocked = LocalDate.parse(date.asText());
                if (!blocked.isBefore(checkIn) && blocked.isBefore(checkOut)) return true;
            }
            return false;
        } catch (JsonProcessingException | java.time.format.DateTimeParseException e) {
            throw new IllegalStateException("Stored rate-plan restrictions are invalid", e);
        }
    }
    private static long advisoryKey(long guestId, String key) {
        byte[] bytes = (guestId + ":" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try { return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes)).getLong(); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void invalid(String code, String message) { throw new BusinessException(code, message); }
    public record HoldCreated(long bookingId, Instant expiresAt, boolean replayed) {}
    public record BookingPricing(BigDecimal roomCharges, BigDecimal cleaningFee, BigDecimal subtotal,
                                 BigDecimal discount, BigDecimal totalStay, Long discountCodeId,
                                 String discountCode, String quoteHash, String policyVersion,
                                 String fixtureVersion, String requestHash) {}
    private record PropertySnapshot(long id, String saleMode, BigDecimal cleaningFee, String timezone) {}
    private record PlanSnapshot(long typeId, String typeName, String saleMode, String typeStatus, int maxAdults,
                                int maxOccupants, int baseQuantity, long typeVersion, long planId, String planName,
                                String policyVersion, BigDecimal nightlyPrice, String allowedModes,
                                String pricingRestrictions, String planStatus,
                                long planVersion) {}
    private record PolicySnapshot(String policyVersion, String timezone, String saleMode, String currency,
                                  InventoryBookingRequest.PaymentMode paymentMode, String roomTypeName, String ratePlanName,
                                  String planPolicyVersion, String allowedPaymentModes, BigDecimal nightlyPrice,
                                  long typeVersion, long planVersion, String quoteHash) {}
    private record SnapshotRow(long itemId,int ordinal,LocalDate date,BigDecimal roomCharge,BigDecimal fees,
                               BigDecimal taxes,String policySnapshot) {
        SnapshotRow withFees(BigDecimal value) { return new SnapshotRow(itemId,ordinal,date,roomCharge,value,taxes,policySnapshot); }
    }
    private record DiscountRemainder(int index,BigDecimal remainder) {}
}
