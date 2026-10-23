package com.stayhub.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ConflictException;
import com.stayhub.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
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

    public InventoryBookingService(JdbcTemplate jdbc, InventoryCapacityService capacity, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.capacity = capacity;
        this.mapper = mapper;
    }

    @Transactional
    public HoldCreated create(long guestId, InventoryBookingRequest request) {
        validate(request);
        String requestHash = hash(request);
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
        int nights = Math.toIntExact(request.checkOut().toEpochDay() - request.checkIn().toEpochDay());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (nights < 1 || nights > 30 || request.checkIn().isBefore(today))
            invalid("ERR_INVALID_DATES", "Stay dates must be 1 to 30 nights and cannot start in the past.");
        if (request.checkIn().equals(today) && !LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).isBefore(LocalTime.of(20, 0)))
            invalid("ERR_SAME_DAY_CUTOFF", "Same-day bookings must be created before 20:00 local time.");

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
                           p.allowed_payment_modes::text AS allowed_modes,p.status AS plan_status,p.version AS plan_version
                    FROM room_types t JOIN rate_plans p ON p.room_type_id=t.id AND p.property_id=t.property_id
                    WHERE t.id=? AND p.id=? AND t.property_id=?
                    """, (rs, row) -> new PlanSnapshot(rs.getLong("type_id"), rs.getString("type_name"),
                    rs.getString("sale_mode"), rs.getString("type_status"), rs.getInt("max_adults"), rs.getInt("max_occupants"),
                    rs.getInt("base_quantity"), rs.getLong("type_version"), rs.getLong("plan_id"), rs.getString("plan_name"),
                    rs.getString("policy_version"), rs.getBigDecimal("nightly_price"), rs.getString("allowed_modes"),
                    rs.getString("plan_status"), rs.getLong("plan_version")), item.roomTypeId(), item.ratePlanId(), request.propertyId())
                    .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Room type or rate plan not found"));
            if (!"ACTIVE".equals(plan.typeStatus()) || !"ACTIVE".equals(plan.planStatus()) || plan.nightlyPrice() == null)
                invalid("ERR_ROOM_NOT_AVAILABLE", "The room type or rate plan is inactive or has no server price.");
            if (!plan.saleMode().equals(property.saleMode()) || ("WHOLE_PLACE".equals(plan.saleMode()) && item.quantity() != 1))
                invalid("ERR_INVALID_OCCUPANCY", "The selected quantity does not match the property's sale mode.");
            if (item.quantity() > plan.baseQuantity()) invalid("ERR_ROOM_NOT_AVAILABLE", "Requested room quantity exceeds the type's configured capacity.");
            if (!plan.allowedModes().contains("\"" + request.paymentMode().name() + "\""))
                invalid("ERR_PAYMENT_MODE_NOT_ALLOWED", "The selected rate plan does not allow this payment mode.");
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
        if (roomTotal.signum() <= 0 || subtotal.signum() <= 0) invalid("ERR_NONPOSITIVE_BOOKING_AMOUNT", "A booking total must be positive.");
        BigDecimal headerNightly = roomTotal.divide(BigDecimal.valueOf(roomNights), 2, RoundingMode.HALF_UP);

        Long bookingId = jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,
                    service_fee,subtotal_price,discount_amount,total_price,status,payment_mode,inventory_policy_version,
                    inventory_idempotency_key,inventory_request_hash,inventory_hold_expires_at)
                VALUES (?,?,?,?,?,?,?,0,?,0,?,'PENDING_PAYMENT',?,?,?, ?,CURRENT_TIMESTAMP+interval '15 minutes')
                RETURNING id
                """, Long.class, request.propertyId(), guestId, request.checkIn(), request.checkOut(), adults + children,
                headerNightly, cleaningFee, subtotal, subtotal, request.paymentMode().name(), POLICY_VERSION,
                request.idempotencyKey(), requestHash);

        // Establish the global order before row triggers and the hold service: booking, sorted types, sorted plans, sorted days.
        jdbc.queryForObject("SELECT id FROM bookings WHERE id=? FOR UPDATE", Long.class, bookingId);
        for (long typeId : types.stream().sorted().toList()) jdbc.queryForObject("SELECT id FROM room_types WHERE id=? FOR SHARE", Long.class, typeId);
        for (long planId : plans.stream().map(PlanSnapshot::planId).sorted().toList()) jdbc.queryForObject("SELECT id FROM rate_plans WHERE id=? FOR SHARE", Long.class, planId);
        List<InventoryBookingRequest.Item> sortedItems = request.items().stream().sorted(
                java.util.Comparator.comparingLong(InventoryBookingRequest.Item::roomTypeId)
                        .thenComparingLong(InventoryBookingRequest.Item::ratePlanId)).toList();
        for (InventoryBookingRequest.Item item : sortedItems) {
            PlanSnapshot plan = plans.stream().filter(p -> p.typeId() == item.roomTypeId() && p.planId() == item.ratePlanId()).findFirst().orElseThrow();
            String policySnapshot = json(new PolicySnapshot(POLICY_VERSION, property.timezone(), property.saleMode(),
                    request.currency(), request.paymentMode(), plan.typeName(), plan.planName(), plan.policyVersion(),
                    plan.allowedModes(), plan.nightlyPrice(), plan.typeVersion(), plan.planVersion()));
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
                    jdbc.update("""
                            INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,fees,taxes,discount_allocation,policy_snapshot)
                            VALUES (?,?,?,?::inventory_vnd,0,0,0,?::jsonb)
                            """, itemId, ordinal, date, plan.nightlyPrice(), policySnapshot);
                }
            }
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
    }

    @Transactional
    public void expire(long bookingId) {
        int changed = jdbc.update("""
                UPDATE bookings SET status='CANCELLED',cancelled_at=clock_timestamp(),inventory_version=inventory_version+1
                WHERE id=? AND status='PENDING_PAYMENT' AND inventory_hold_expires_at<=clock_timestamp()
                """, bookingId);
        if (changed == 1) capacity.releaseAll(bookingId);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpired(int limit) {
        return jdbc.queryForList("""
                SELECT id FROM bookings WHERE status='PENDING_PAYMENT' AND inventory_hold_expires_at<=clock_timestamp()
                ORDER BY inventory_hold_expires_at,id LIMIT ?
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
    private static long advisoryKey(long guestId, String key) {
        byte[] bytes = (guestId + ":" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try { return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes)).getLong(); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void invalid(String code, String message) { throw new BusinessException(code, message); }
    public record HoldCreated(long bookingId, Instant expiresAt, boolean replayed) {}
    private record PropertySnapshot(long id, String saleMode, BigDecimal cleaningFee, String timezone) {}
    private record PlanSnapshot(long typeId, String typeName, String saleMode, String typeStatus, int maxAdults,
                                int maxOccupants, int baseQuantity, long typeVersion, long planId, String planName,
                                String policyVersion, BigDecimal nightlyPrice, String allowedModes, String planStatus,
                                long planVersion) {}
    private record PolicySnapshot(String policyVersion, String timezone, String saleMode, String currency,
                                  InventoryBookingRequest.PaymentMode paymentMode, String roomTypeName, String ratePlanName,
                                  String planPolicyVersion, String allowedPaymentModes, BigDecimal nightlyPrice,
                                  long typeVersion, long planVersion) {}
}
