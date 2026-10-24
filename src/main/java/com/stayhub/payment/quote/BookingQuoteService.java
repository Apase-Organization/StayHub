package com.stayhub.payment.quote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ConflictException;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.common.validation.StayDatePolicy;
import com.stayhub.inventory.InventoryBookingRequest;
import com.stayhub.inventory.InventoryOfferRequest;
import com.stayhub.inventory.InventoryOfferService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingQuoteService {
    private static final String POLICY_VERSION = "s0-policy-v1";
    private static final String FIXTURE_VERSION = "s0-fixtures-v1";
    private static final JsonMapper CANONICAL = JsonMapper.builder().findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final JdbcTemplate jdbc;
    private final InventoryOfferService offers;
    private final long validitySeconds;

    public BookingQuoteService(JdbcTemplate jdbc, InventoryOfferService offers,
                               @Value("${app.payment.quote-validity-seconds:0}") long validitySeconds) {
        this.jdbc = jdbc;
        this.offers = offers;
        this.validitySeconds = validitySeconds;
    }

    @Transactional
    public BookingQuoteResponse quote(long guestId, BookingQuoteRequest request) {
        if (validitySeconds <= 0) throw new ConflictException("ERR_PAYMENT_CONFIG", "Server quote validity is not configured.");
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return buildQuote(guestId, request, UUID.randomUUID().toString(), createdAt,
                createdAt.plusSeconds(validitySeconds), true);
    }

    @Transactional
    public AcceptedQuote verifyConsent(long guestId, String quoteId, String policyVersion,
                                       String fixtureVersion, String acceptedQuoteHash) {
        var stored = jdbc.query("""
                SELECT actor_id,policy_version,fixture_version,quote_hash,selection,created_at,expires_at,consumed_booking_id
                FROM inventory_quotes WHERE quote_id=?
                """, (rs, row) -> new StoredQuote(rs.getLong("actor_id"), rs.getString("policy_version"),
                rs.getString("fixture_version"), rs.getString("quote_hash"), rs.getString("selection"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
                (Long)rs.getObject("consumed_booking_id")), quoteId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Booking quote not found"));
        if (stored.actorId()!=guestId) throw new ResourceNotFoundException("Booking quote not found");
        if (!POLICY_VERSION.equals(policyVersion) || !FIXTURE_VERSION.equals(fixtureVersion)
                || !stored.quoteHash().equals(acceptedQuoteHash) || !stored.policyVersion().equals(policyVersion)
                || !stored.fixtureVersion().equals(fixtureVersion))
            throw new ConflictException("ERR_QUOTE_STALE", "Quote versions or hash changed; request a new quote.");
        if (stored.expiresAt().compareTo(Instant.now())<=0)
            throw new ConflictException("ERR_QUOTE_EXPIRED", "Quote expired; request and review a new quote.");
        if (stored.consumedBookingId()!=null) throw new ConflictException("ERR_IDEMPOTENCY_CONFLICT", "Quote has already been used.");
        BookingQuoteRequest selection;
        try { selection=CANONICAL.readValue(stored.selection(),BookingQuoteRequest.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored quote selection is invalid",exception); }
        BookingQuoteResponse refreshed=buildQuote(guestId,selection,quoteId,stored.createdAt(),stored.expiresAt(),false);
        if (!stored.quoteHash().equals(refreshed.quoteHash()))
            throw new ConflictException("ERR_QUOTE_STALE", "Offer price, policy or availability changed; request a new quote.");
        DiscountResult discount=discount(selection.discountCode(),guestId,refreshed.subtotal(),refreshed.roomCharges().add(refreshed.cleaningFee()));
        return new AcceptedQuote(quoteId,selection,refreshed,discount.codeId(),discount.code());
    }

    @Transactional(readOnly = true)
    public BookingQuoteResponse storedQuote(long guestId,String quoteId,String acceptedQuoteHash) {
        var rows=jdbc.query("SELECT actor_id,quote_hash,amounts FROM inventory_quotes WHERE quote_id=?",
                (rs,row)->new StoredQuoteSummary(rs.getLong("actor_id"),rs.getString("quote_hash"),rs.getString("amounts")),quoteId);
        var stored=rows.stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Booking quote not found"));
        if(stored.actorId()!=guestId || !stored.quoteHash().equals(acceptedQuoteHash))
            throw new ResourceNotFoundException("Booking quote not found");
        try { return CANONICAL.readValue(stored.amounts(),BookingQuoteResponse.class); }
        catch(JsonProcessingException exception) { throw new IllegalStateException("Stored quote result is invalid",exception); }
    }

    @Transactional
    public void consumeQuote(String quoteId,long bookingId,long guestId) {
        int changed=jdbc.update("""
                UPDATE inventory_quotes SET consumed_booking_id=?,consumed_at=clock_timestamp()
                WHERE quote_id=? AND actor_id=? AND consumed_booking_id IS NULL AND expires_at>clock_timestamp()
                """,bookingId,quoteId,guestId);
        if(changed!=1) throw new ConflictException("ERR_QUOTE_EXPIRED","Quote is no longer available.");
    }

    private BookingQuoteResponse buildQuote(long guestId, BookingQuoteRequest request, String quoteId,
                                            Instant createdAt, Instant expiresAt, boolean persist) {
        StayDatePolicy.validate(request.checkIn(), request.checkOut(), ZonedDateTime.now(StayDatePolicy.ZONE));
        if (!"VND".equals(request.currency())) invalid("ERR_INVALID_CURRENCY", "Only VND is supported.");
        Long hostId = jdbc.query("SELECT host_id FROM properties WHERE id=? AND status='ACTIVE'", (rs, row) -> rs.getLong(1),
                request.propertyId()).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        if (hostId == guestId) invalid("ERR_SELF_BOOKING", "A property owner cannot book their own property.");

        int nights = StayDatePolicy.validate(request.checkIn(), request.checkOut(), ZonedDateTime.now(StayDatePolicy.ZONE));
        List<Integer> requestChildren = request.childrenAges().stream().sorted().toList();
        int rooms = 0;
        int adults = 0;
        List<Integer> allocatedChildren = new ArrayList<>();
        Set<String> uniquePlans = new HashSet<>();
        Map<Long, Integer> requestedByType = new java.util.HashMap<>();
        List<BookingQuoteResponse.QuoteItem> quoteItems = new ArrayList<>();
        EnumSet<InventoryBookingRequest.PaymentMode> allowedModes = EnumSet.allOf(InventoryBookingRequest.PaymentMode.class);
        BigDecimal roomCharges = BigDecimal.ZERO;
        BigDecimal cleaningFee = null;

        for (InventoryBookingRequest.Item item : request.items()) {
            if (item == null || item.roomAllocations() == null || item.quantity() != item.roomAllocations().size())
                invalid("ERR_INVALID_OCCUPANCY", "Each booked room needs one allocation.");
            if (!uniquePlans.add(item.roomTypeId() + ":" + item.ratePlanId()))
                invalid("ERR_INVALID_REQUEST", "Combine duplicate type and rate-plan selections.");
            rooms += item.quantity();
            requestedByType.merge(item.roomTypeId(), item.quantity(), Integer::sum);
            for (InventoryBookingRequest.RoomAllocation allocation : item.roomAllocations()) {
                if (allocation == null || allocation.childrenAges() == null) invalid("ERR_INVALID_OCCUPANCY", "Room allocation is incomplete.");
                adults += allocation.adults();
                allocatedChildren.addAll(allocation.childrenAges());
            }
            var offerResult = offers.search(request.propertyId(), new InventoryOfferRequest(request.checkIn(), request.checkOut(),
                    request.currency(), request.paymentMode(), item.roomAllocations().stream()
                            .map(a -> new InventoryOfferRequest.RoomOccupancy(a.adults(), a.childrenAges())).toList()));
            var offer = offerResult.offers().stream().filter(candidate -> candidate.roomTypeId() == item.roomTypeId()
                    && candidate.ratePlanId() == item.ratePlanId()).findFirst()
                    .orElseThrow(() -> new ConflictException("ERR_PAYMENT_MODE_NOT_ALLOWED", "The selected plan does not allow this payment mode."));
            if (!offer.eligible()) throw new ConflictException("ERR_ROOM_NOT_AVAILABLE", offer.unavailableReason());
            BigDecimal itemCharges = offer.nightlyPerRoom().multiply(BigDecimal.valueOf(nights))
                    .multiply(BigDecimal.valueOf(item.quantity()));
            roomCharges = roomCharges.add(itemCharges);
            if (cleaningFee == null) cleaningFee = offer.cleaningFee();
            List<BookingQuoteResponse.NightlyPrice> nightly = new ArrayList<>();
            for (var date = request.checkIn(); date.isBefore(request.checkOut()); date = date.plusDays(1))
                nightly.add(new BookingQuoteResponse.NightlyPrice(date, offer.nightlyPerRoom()));
            List<InventoryBookingRequest.PaymentMode> itemModes = offer.allowedPaymentModes().stream()
                    .map(InventoryBookingRequest.PaymentMode::valueOf).toList();
            allowedModes.retainAll(itemModes);
            quoteItems.add(new BookingQuoteResponse.QuoteItem(item.roomTypeId(), item.ratePlanId(), item.quantity(),
                    item.roomAllocations(), offer.availableQuantity(), nightly, itemCharges));
        }
        if (rooms != request.rooms() || adults != request.adults() || !requestChildren.equals(allocatedChildren.stream().sorted().toList()))
            invalid("ERR_INVALID_OCCUPANCY", "Header room/adult/child totals must match per-room allocations.");
        if (requestedByType.entrySet().stream().anyMatch(entry -> quoteItems.stream()
                .filter(item -> item.roomTypeId() == entry.getKey()).mapToInt(BookingQuoteResponse.QuoteItem::quantity).sum()
                > quoteItems.stream().filter(item -> item.roomTypeId() == entry.getKey()).mapToInt(BookingQuoteResponse.QuoteItem::availableQuantity).min().orElse(0)))
            throw new ConflictException("ERR_ROOM_NOT_AVAILABLE", "The combined room quantity exceeds shared type inventory.");
        if (!allowedModes.contains(request.paymentMode())) invalid("ERR_PAYMENT_MODE_NOT_ALLOWED", "The selected mode is not allowed by every plan.");
        BigDecimal clean = cleaningFee == null ? BigDecimal.ZERO : cleaningFee;
        BigDecimal taxes = BigDecimal.ZERO;
        BigDecimal subtotal = roomCharges.add(clean).add(taxes);
        DiscountResult discountResult = discount(request.discountCode(), guestId, subtotal, roomCharges.add(clean));
        BigDecimal discount = discountResult.amount();
        BigDecimal total = whole(subtotal.subtract(discount));
        if (total.signum() <= 0) invalid("ERR_NONPOSITIVE_BOOKING_AMOUNT", "A booking total must be positive.");
        BigDecimal grossCommission = whole(roomCharges.add(clean).multiply(new BigDecimal("0.15")));
        if (discount.compareTo(grossCommission) > 0) invalid("ERR_DISCOUNT_FUNDING", "Discount exceeds platform commission funding.");
        BigDecimal netRetention = grossCommission.subtract(discount);
        BigDecimal dueOnline = request.paymentMode() == InventoryBookingRequest.PaymentMode.FULL_PREPAY ? total
                : whole(total.multiply(new BigDecimal("0.20")));
        if (dueOnline.signum() <= 0) invalid("ERR_NONPOSITIVE_BOOKING_AMOUNT", "Online collection must be positive.");
        if (dueOnline.compareTo(netRetention) < 0)
            throw new ConflictException("ERR_DEPOSIT_COVERAGE", "The selected offer cannot cover the approved platform retention.");
        BigDecimal balanceDueHost = total.subtract(dueOnline);

        BookingQuoteResponse response = new BookingQuoteResponse(quoteId, POLICY_VERSION, FIXTURE_VERSION, "",
                expiresAt, request.paymentMode(), allowedModes.stream().sorted().toList(), request.currency(),
                subtotal, discount, total, dueOnline, balanceDueHost, roomCharges, clean, taxes, quoteItems);
        String selectionHash = hash(List.of(guestId, request));
        String quoteHash = hash(new QuoteBinding(guestId, POLICY_VERSION, FIXTURE_VERSION, request,
                subtotal, discount, total, dueOnline, balanceDueHost, roomCharges, clean, taxes,
                allowedModes.stream().sorted().toList(), quoteItems, expiresAt));
        response = new BookingQuoteResponse(quoteId, POLICY_VERSION, FIXTURE_VERSION, quoteHash, expiresAt,
                request.paymentMode(), allowedModes.stream().sorted().toList(), request.currency(), subtotal,
                discount, total, dueOnline, balanceDueHost, roomCharges, clean, taxes, quoteItems);
        if(persist) jdbc.update("""
                    INSERT INTO inventory_quotes(quote_id,actor_id,property_id,policy_version,fixture_version,
                        selection_hash,quote_hash,selection,amounts,created_at,expires_at)
                    VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)
                    """, quoteId, guestId, request.propertyId(), POLICY_VERSION, FIXTURE_VERSION, selectionHash,
                    quoteHash, json(request), json(response), java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(expiresAt));
        return response;
    }

    private DiscountResult discount(String code, long guestId, BigDecimal subtotal, BigDecimal commissionBase) {
        if (code == null || code.isBlank()) return new DiscountResult(BigDecimal.ZERO,null,null);
        var rows = jdbc.query("""
                SELECT id,code,type,value,cap,minimum_amount,starts_at,ends_at,usage_limit,used_count,reserved_count,
                       per_user_limit,active
                FROM discount_codes WHERE lower(code)=lower(?)
                """, (rs, row) -> new DiscountRow(rs.getLong("id"), rs.getString("code"), rs.getString("type"),
                rs.getBigDecimal("value"), rs.getBigDecimal("cap"), rs.getBigDecimal("minimum_amount"),
                rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                (Integer)rs.getObject("usage_limit"), rs.getInt("used_count"), rs.getInt("reserved_count"),
                (Integer)rs.getObject("per_user_limit"), rs.getBoolean("active")), code.trim());
        DiscountRow row = rows.stream().findFirst().orElseThrow(() -> new BusinessException("ERR_DISCOUNT_INVALID", "Discount code is invalid."));
        Instant now = Instant.now();
        if (!row.active() || now.isBefore(row.startsAt()) || now.isAfter(row.endsAt()) || subtotal.compareTo(row.minimumAmount())<0)
            throw new BusinessException("ERR_DISCOUNT_INVALID", "Discount code is not eligible for this quote.");
        if (row.usageLimit()==null || row.usageLimit()<=0)
            throw new BusinessException("ERR_DISCOUNT_CONFIG", "A finite campaign quota is required.");
        if (row.usedCount()+row.reservedCount()>=row.usageLimit())
            throw new ConflictException("ERR_DISCOUNT_QUOTA_EXHAUSTED", "Discount quota has been reached.");
        long usedByGuest = jdbc.queryForObject("SELECT count(*) FROM bookings WHERE guest_id=? AND discount_code_id=?", Long.class, guestId,row.id());
        long reservedByGuest = jdbc.queryForObject("SELECT count(*) FROM inventory_discount_reservations WHERE guest_id=? AND discount_code_id=? AND state IN ('RESERVED','CONSUMED')",Long.class,guestId,row.id());
        if (row.perUserLimit()==null || row.perUserLimit()!=1)
            throw new BusinessException("ERR_DISCOUNT_CONFIG", "The current policy requires a one-use-per-guest campaign limit.");
        if (usedByGuest+reservedByGuest>=1)
            throw new ConflictException("ERR_DISCOUNT_USER_LIMIT", "This guest has already used the discount quota.");
        BigDecimal amount = "PERCENT".equals(row.type())
                ? whole(subtotal.multiply(row.value()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
                : whole(row.value());
        if (row.cap()!=null) amount=amount.min(whole(row.cap()));
        amount=amount.min(subtotal.subtract(BigDecimal.ONE));
        if (amount.signum()<0) amount=BigDecimal.ZERO;
        if(amount.signum()==0) throw new BusinessException("ERR_DISCOUNT_INVALID","Discount does not produce a positive whole-VND reduction.");
        if (amount.compareTo(whole(commissionBase.multiply(new BigDecimal("0.15"))))>0)
            invalid("ERR_DISCOUNT_FUNDING", "Discount exceeds the approved platform funding bound.");
        return new DiscountResult(amount,row.id(),row.code());
    }

    private String json(Object value) {
        try { return CANONICAL.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot encode quote", exception); }
    }
    private String hash(Object value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json(value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    private static BigDecimal whole(BigDecimal amount) { return amount.setScale(0, RoundingMode.HALF_UP); }
    private static void invalid(String code, String message) { throw new BusinessException(code, message); }

    private record QuoteBinding(long actorId, String policyVersion, String fixtureVersion, BookingQuoteRequest selection,
                                BigDecimal subtotal, BigDecimal discount, BigDecimal totalStay, BigDecimal dueOnline,
                                BigDecimal balanceDueHost, BigDecimal roomCharges, BigDecimal cleaningFee,
                                BigDecimal taxes, List<InventoryBookingRequest.PaymentMode> allowedPaymentModes,
                                List<BookingQuoteResponse.QuoteItem> items, Instant quoteExpiresAt) {}
    private record DiscountRow(long id, String code, String type, BigDecimal value, BigDecimal cap, BigDecimal minimumAmount,
                               Instant startsAt, Instant endsAt, Integer usageLimit, int usedCount, int reservedCount,
                               Integer perUserLimit, boolean active) {}
    public record AcceptedQuote(String quoteId,BookingQuoteRequest selection,BookingQuoteResponse quote,
                                Long discountCodeId,String discountCode) {}
    private record DiscountResult(BigDecimal amount,Long codeId,String code) {}
    private record StoredQuote(long actorId,String policyVersion,String fixtureVersion,String quoteHash,String selection,
                               Instant createdAt,Instant expiresAt,Long consumedBookingId) {}
    private record StoredQuoteSummary(long actorId,String quoteHash,String amounts) {}
}
