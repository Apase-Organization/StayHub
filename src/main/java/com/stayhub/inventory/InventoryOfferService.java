package com.stayhub.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.common.validation.StayDatePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryOfferService {
    private static final String POLICY_VERSION = "s0-policy-v1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public InventoryOfferService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public OfferSearchResult search(long propertyId, InventoryOfferRequest request) {
        boolean withDates = request.checkIn() != null || request.checkOut() != null;
        int nights = 0;
        if (withDates) {
            if (request.checkIn() == null || request.checkOut() == null) invalid("ERR_INVALID_DATES", "Provide both check-in and check-out dates.");
            nights = StayDatePolicy.validate(request.checkIn(), request.checkOut(), ZonedDateTime.now(StayDatePolicy.ZONE));
        }
        if (!"VND".equals(request.currency())) invalid("ERR_INVALID_CURRENCY", "Only VND is supported.");
        PropertyContext property = jdbc.query("""
                SELECT id,title,city,sale_mode,cleaning_fee FROM properties WHERE id=? AND status='ACTIVE'
                """, (rs, row) -> new PropertyContext(rs.getLong("id"), rs.getString("title"), rs.getString("city"),
                rs.getString("sale_mode"), rs.getBigDecimal("cleaning_fee")), propertyId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        BigDecimal cleaningFee;
        try { cleaningFee = property.cleaningFee().setScale(0, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException exception) { invalid("ERR_INVALID_AMOUNT", "The property's cleaning fee is not a whole VND amount."); return null; }
        List<TypeRow> types = jdbc.query("""
                SELECT t.id,t.name,t.sale_mode,t.base_quantity,t.max_adults,t.max_occupants,t.status,
                       p.id AS plan_id,p.name AS plan_name,p.policy_version,p.nightly_price,
                       p.allowed_payment_modes::text AS payment_modes,p.pricing_restrictions::text AS restrictions,
                       p.status AS plan_status
                FROM room_types t JOIN rate_plans p ON p.room_type_id=t.id AND p.property_id=t.property_id
                WHERE t.property_id=? ORDER BY t.id,p.id
                """, (rs, row) -> new TypeRow(rs.getLong("id"), rs.getString("name"), rs.getString("sale_mode"),
                rs.getInt("base_quantity"), rs.getInt("max_adults"), rs.getInt("max_occupants"), rs.getString("status"),
                rs.getLong("plan_id"), rs.getString("plan_name"), rs.getString("policy_version"), rs.getBigDecimal("nightly_price"),
                 rs.getString("payment_modes"), rs.getString("restrictions"), rs.getString("plan_status")), propertyId);

        List<Offer> offers = new ArrayList<>();
        for (TypeRow type : types) {
            if (!"ACTIVE".equals(type.typeStatus()) || !"ACTIVE".equals(type.planStatus())
                    || !type.saleMode().equals(property.saleMode()) || !POLICY_VERSION.equals(type.policyVersion())
                    || type.nightlyPrice() == null) continue;
            List<String> modes = paymentModes(type.allowedModes());
            if (!modes.contains(request.paymentMode().name())) continue;
            List<Integer> eligibleRooms = eligibleRoomOrdinals(type, request.roomOccupancies());
            Integer available = null;
            BigDecimal totalStay = null;
            boolean eligible = !eligibleRooms.isEmpty();
            String reason = eligible ? null : "Room occupancy exceeds this type's per-room limits.";
            if (withDates) {
                DayStock stock = stock(type.typeId(), request.checkIn(), request.checkOut(), nights);
                // Report live stock independently of the number of occupancy rows
                // in this search request. eligibleRoomOrdinals describes only the
                // requested rooms; it is not an inventory capacity ceiling.
                available = Math.min(type.baseQuantity(), stock.available());
                if (planStopSale(type.pricingRestrictions(), request.checkIn(), request.checkOut())) {
                    available = 0;
                    eligible = false;
                    reason = "This rate plan is not selling for the selected dates.";
                } else if (!stock.calendarComplete()) {
                    available = 0;
                    eligible = false;
                    reason = "Calendar is not configured for every night.";
                } else if (stock.stopSale()) {
                    available = 0;
                    eligible = false;
                    reason = "This room type is not selling for the selected dates.";
                } else if (available < request.roomOccupancies().size()) {
                    eligible = false;
                    reason = "Not enough units are available for every night.";
                }
                if (eligible) totalStay = type.nightlyPrice()
                        .multiply(BigDecimal.valueOf(nights))
                        .multiply(BigDecimal.valueOf(request.roomOccupancies().size())).add(cleaningFee);
            }
            offers.add(new Offer(type.typeId(), type.typeName(), type.planId(), type.planName(),
                    type.nightlyPrice(), "VND per room per night; total includes one cleaning fee; tax not enabled",
                    request.roomOccupancies().size(), eligibleRooms, type.baseQuantity(), available, nights, cleaningFee,
                    BigDecimal.ZERO, totalStay, modes, eligible, reason));
        }
        offers.sort(java.util.Comparator.comparing(Offer::totalStay, java.util.Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(Offer::nightlyPerRoom));
        return new OfferSearchResult(property.id(), property.title(), property.city(), property.saleMode(),
                withDates, request.currency(), request.paymentMode().name(), offers);
    }

    private DayStock stock(long typeId, LocalDate checkIn, LocalDate checkOut, int nights) {
        return jdbc.queryForObject("""
                WITH stay AS (
                    SELECT d::date AS stay_date FROM generate_series(?::date, (?::date - 1), interval '1 day') d
                )
                SELECT count(di.stay_date) AS configured,
                       COALESCE(min(di.total-di.out_of_order-COALESCE(used.quantity,0)),0) AS available,
                       COALESCE(bool_or(di.stop_sale),false) AS stop_sale
                FROM stay s
                LEFT JOIN type_day_inventory di ON di.room_type_id=? AND di.stay_date=s.stay_date
                LEFT JOIN LATERAL (
                    SELECT sum(c.quantity) AS quantity FROM inventory_commitments c
                    WHERE c.room_type_id=? AND c.stay_date=s.stay_date AND c.state IN ('HELD','CONFIRMED')
                ) used ON true
                """, (rs, row) -> new DayStock(rs.getInt("configured") == nights,
                Math.max(0, rs.getInt("available")), rs.getBoolean("stop_sale")), checkIn, checkOut, typeId, typeId);
    }

    private List<Integer> eligibleRoomOrdinals(TypeRow type, List<InventoryOfferRequest.RoomOccupancy> rooms) {
        List<Integer> eligible=new ArrayList<>();
        for(int index=0;index<rooms.size();index++) {
            InventoryOfferRequest.RoomOccupancy room=rooms.get(index);
            if(room.adults()>=1 && room.adults()<=type.maxAdults() && room.childrenAges()!=null
                    && room.childrenAges().stream().allMatch(age->age!=null && age>=0 && age<=17)
                    && room.adults()+room.childrenAges().size()<=type.maxOccupants()) eligible.add(index+1);
        }
        return eligible;
    }

    private List<String> paymentModes(String json) {
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Stored rate-plan payment modes are invalid", exception); }
    }
    private boolean planStopSale(String json, LocalDate checkIn, LocalDate checkOut) {
        try {
            JsonNode dates = mapper.readTree(json).path("stopSaleDates");
            for (JsonNode date : dates) {
                LocalDate blocked = LocalDate.parse(date.asText());
                if (!blocked.isBefore(checkIn) && blocked.isBefore(checkOut)) return true;
            }
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException("Stored rate-plan restrictions are invalid", exception);
        }
    }
    private static void invalid(String code, String message) { throw new BusinessException(code, message); }

    public record OfferSearchResult(long propertyId, String propertyTitle, String city, String saleMode,
                                    boolean liveAvailabilityChecked, String currency, String paymentMode,
                                    List<Offer> offers) {}
    public record Offer(long roomTypeId, String roomTypeName, long ratePlanId, String ratePlanName,
                        BigDecimal nightlyPerRoom, String priceBasis, int requestedQuantity,
                        List<Integer> eligibleRoomOrdinals, int configuredBaseQuantity, Integer availableQuantity, int nights,
                        BigDecimal cleaningFee, BigDecimal taxes, BigDecimal totalStay,
                        List<String> allowedPaymentModes, boolean eligible, String unavailableReason) {}
    private record PropertyContext(long id, String title, String city, String saleMode, BigDecimal cleaningFee) {}
    private record TypeRow(long typeId, String typeName, String saleMode, int baseQuantity,
                           int maxAdults, int maxOccupants, String typeStatus, long planId, String planName,
                           String policyVersion, BigDecimal nightlyPrice, String allowedModes,
                           String pricingRestrictions, String planStatus) {}
    private record DayStock(boolean calendarComplete, int available, boolean stopSale) {}
}
