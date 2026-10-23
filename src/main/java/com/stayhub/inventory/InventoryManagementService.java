package com.stayhub.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ConflictException;
import com.stayhub.common.exception.ResourceNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryManagementService {
    private final JdbcTemplate jdbc;
    private final InventoryCapacityService capacity;
    private final ObjectMapper mapper;

    public InventoryManagementService(JdbcTemplate jdbc, InventoryCapacityService capacity, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.capacity = capacity;
        this.mapper = mapper;
    }

    @Transactional
    public long createType(long hostId, long propertyId, RoomTypeRequest request) {
        lockOwnedProperty(hostId, propertyId);
        if (request.maxOccupants() < request.maxAdults()) invalid("ERR_INVALID_ROOM_TYPE", "Occupancy must cover adults.");
        String currentMode = jdbc.queryForObject("SELECT sale_mode FROM properties WHERE id=?", String.class, propertyId);
        if (currentMode == null) {
            jdbc.update("UPDATE properties SET sale_mode=? WHERE id=? AND sale_mode IS NULL", request.saleMode().name(), propertyId);
        } else if (!currentMode.equals(request.saleMode().name())) {
            invalid("ERR_SALE_MODE_IMMUTABLE", "A property's sale mode cannot be changed after it is selected.");
        }
        return jdbc.queryForObject("""
                INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, propertyId, request.saleMode().name(), request.name().trim(), request.quantity(),
                request.maxAdults(), request.maxOccupants());
    }

    @Transactional
    public long createPlan(long hostId, long propertyId, long typeId, RatePlanRequest request) {
        lockOwnedProperty(hostId, propertyId);
        requirePolicyVersion(request.policyVersion());
        lockType(propertyId, typeId, "FOR SHARE");
        Set<String> modes = new TreeSet<>();
        request.allowedPaymentModes().forEach(mode -> modes.add(mode.name()));
        try {
            String json = mapper.writeValueAsString(modes);
            return jdbc.queryForObject("""
                    INSERT INTO rate_plans(property_id,room_type_id,name,policy_version,nightly_price,allowed_payment_modes,pricing_restrictions)
                    VALUES (?,?,?,?,?::inventory_vnd,?::jsonb,?::jsonb) RETURNING id
                    """, Long.class, propertyId, typeId, request.name().trim(), request.policyVersion().trim(),
                    request.nightlyPrice(), json, restrictionsJson(request.stopSaleDates()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode payment modes", exception);
        }
    }

    @Transactional
    public void updateType(long hostId, long propertyId, long typeId, RoomTypeUpdateRequest request) {
        lockOwnedProperty(hostId, propertyId);
        lockType(propertyId, typeId, "FOR UPDATE");
        if (request.maxOccupants() < request.maxAdults()) invalid("ERR_INVALID_ROOM_TYPE", "Occupancy must cover adults.");
        int changed = jdbc.update("""
                UPDATE room_types SET name=?,base_quantity=?,max_adults=?,max_occupants=?,version=version+1
                WHERE id=? AND version=? AND status='ACTIVE'
                """, request.name().trim(), request.quantity(), request.maxAdults(), request.maxOccupants(), typeId, request.expectedVersion());
        if (changed != 1) throw new ConflictException("ERR_VERSION_CONFLICT", "The room type is archived or its version is stale.");
    }

    @Transactional
    public void updatePlan(long hostId, long propertyId, long typeId, long planId, RatePlanUpdateRequest request) {
        lockOwnedProperty(hostId, propertyId);
        requirePolicyVersion(request.policyVersion());
        lockType(propertyId, typeId, "FOR SHARE");
        lockPlan(propertyId, typeId, planId);
        Set<String> modes = new TreeSet<>();
        request.allowedPaymentModes().forEach(mode -> modes.add(mode.name()));
        try {
            String json = mapper.writeValueAsString(modes);
            int changed = jdbc.update("""
                    UPDATE rate_plans SET name=?,policy_version=?,nightly_price=?::inventory_vnd,allowed_payment_modes=?::jsonb,
                        pricing_restrictions=?::jsonb,version=version+1
                    WHERE id=? AND version=? AND status='ACTIVE'
                    """, request.name().trim(), request.policyVersion().trim(), request.nightlyPrice(), json,
                    restrictionsJson(request.stopSaleDates()), planId, request.expectedVersion());
            if (changed != 1) throw new ConflictException("ERR_VERSION_CONFLICT", "The rate plan is archived or its version is stale.");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode payment modes", exception);
        }
    }

    @Transactional
    public void updateCalendar(long hostId, long propertyId, long typeId, InventoryDayRequest request) {
        lockOwnedProperty(hostId, propertyId);
        lockType(propertyId, typeId, "FOR SHARE");
        if (request.outOfOrder() > request.total()) invalid("ERR_INVALID_CAPACITY", "Out-of-order capacity cannot exceed total capacity.");
        int exists = jdbc.queryForObject("SELECT count(*) FROM type_day_inventory WHERE room_type_id=? AND stay_date=?",
                Integer.class, typeId, request.date());
        if (exists == 0) {
            if (request.expectedVersion() != 0) throw new ConflictException("ERR_VERSION_CONFLICT", "The calendar version is stale.");
            jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total,out_of_order,stop_sale) VALUES (?,?,?,?,?)",
                    typeId, request.date(), request.total(), request.outOfOrder(), request.stopSale());
        } else {
            try {
                capacity.updateDay(typeId, request.date(), request.total(), request.outOfOrder(), request.stopSale(), request.expectedVersion());
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                throw new ConflictException("ERR_CAPACITY_CONFLICT", "The calendar change would break held or confirmed inventory.");
            } catch (IllegalStateException e) {
                throw new ConflictException("ERR_VERSION_CONFLICT", "The calendar version is stale.");
            }
        }
    }

    @Transactional
    public void archiveType(long hostId, long propertyId, long typeId, long expectedVersion) {
        lockOwnedProperty(hostId, propertyId);
        lockType(propertyId, typeId, "FOR UPDATE");
        int changed = jdbc.update("UPDATE room_types SET status='ARCHIVED',version=version+1 WHERE id=? AND version=?", typeId, expectedVersion);
        if (changed != 1) throw new ConflictException("ERR_VERSION_CONFLICT", "The room type version is stale.");
    }

    @Transactional
    public void archivePlan(long hostId, long propertyId, long typeId, long planId, long expectedVersion) {
        lockOwnedProperty(hostId, propertyId);
        lockType(propertyId, typeId, "FOR SHARE");
        lockPlan(propertyId, typeId, planId);
        int changed = jdbc.update("UPDATE rate_plans SET status='ARCHIVED',version=version+1 WHERE id=? AND version=?", planId, expectedVersion);
        if (changed != 1) throw new ConflictException("ERR_VERSION_CONFLICT", "The rate plan version is stale.");
    }

    @Transactional(readOnly = true)
    public List<RoomTypeView> listTypes(long hostId, long propertyId) {
        requireOwnedProperty(hostId, propertyId);
        return jdbc.query("SELECT id,name,sale_mode,base_quantity,max_adults,max_occupants,status,version FROM room_types WHERE property_id=? ORDER BY id",
                (rs, row) -> new RoomTypeView(rs.getLong("id"), rs.getString("name"), rs.getString("sale_mode"),
                        rs.getInt("base_quantity"), rs.getInt("max_adults"), rs.getInt("max_occupants"),
                        rs.getString("status"), rs.getLong("version")), propertyId);
    }

    @Transactional(readOnly = true)
    public List<RatePlanView> listPlans(long hostId, long propertyId, long typeId) {
        requireOwnedProperty(hostId, propertyId);
        requireType(propertyId, typeId);
        return jdbc.query("SELECT id,name,policy_version,nightly_price,allowed_payment_modes::text,pricing_restrictions::text,status,version FROM rate_plans WHERE property_id=? AND room_type_id=? ORDER BY id",
                (rs, row) -> planView(rs), propertyId, typeId);
    }

    @Transactional(readOnly = true)
    public List<CalendarView> listCalendar(long hostId, long propertyId, long typeId, LocalDate from, LocalDate through) {
        requireOwnedProperty(hostId, propertyId);
        requireType(propertyId, typeId);
        if (from == null || through == null || through.isBefore(from) || through.isAfter(from.plusDays(365)))
            invalid("ERR_INVALID_DATES", "Calendar range must be within 366 days.");
        return jdbc.query("SELECT stay_date,total,out_of_order,stop_sale,version FROM type_day_inventory WHERE room_type_id=? AND stay_date BETWEEN ? AND ? ORDER BY stay_date",
                (rs, row) -> new CalendarView(rs.getObject("stay_date", LocalDate.class), rs.getInt("total"),
                        rs.getInt("out_of_order"), rs.getBoolean("stop_sale"), rs.getLong("version")), typeId, from, through);
    }

    private RatePlanView planView(ResultSet rs) throws SQLException {
        return new RatePlanView(rs.getLong("id"), rs.getString("name"), rs.getString("policy_version"),
                rs.getBigDecimal("nightly_price"), rs.getString("allowed_payment_modes"),
                rs.getString("pricing_restrictions"), rs.getString("status"), rs.getLong("version"));
    }
    private void lockOwnedProperty(long hostId, long propertyId) {
        if (jdbc.query("SELECT id FROM properties WHERE id=? AND host_id=? FOR UPDATE", (rs, row) -> rs.getLong(1), propertyId, hostId).isEmpty())
            throw new ResourceNotFoundException("Property not found");
    }
    private void requireOwnedProperty(long hostId, long propertyId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM properties WHERE id=? AND host_id=?)", Boolean.class, propertyId, hostId)))
            throw new ResourceNotFoundException("Property not found");
    }
    private void requireType(long propertyId, long typeId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM room_types WHERE id=? AND property_id=?)", Boolean.class, typeId, propertyId)))
            throw new ResourceNotFoundException("Room type not found");
    }
    private void lockType(long propertyId, long typeId, String lock) {
        if (jdbc.query("SELECT id FROM room_types WHERE id=? AND property_id=? " + lock,
                (rs, row) -> rs.getLong(1), typeId, propertyId).isEmpty())
            throw new ResourceNotFoundException("Room type not found");
    }
    private void lockPlan(long propertyId, long typeId, long planId) {
        if (jdbc.query("SELECT id FROM rate_plans WHERE id=? AND room_type_id=? AND property_id=? FOR UPDATE",
                (rs, row) -> rs.getLong(1), planId, typeId, propertyId).isEmpty())
            throw new ResourceNotFoundException("Rate plan not found");
    }
    private static void invalid(String code, String message) { throw new BusinessException(code, message); }
    private static void requirePolicyVersion(String version) {
        if (!"s0-policy-v1".equals(version)) invalid("ERR_POLICY_VERSION", "Only the current approved inventory policy is supported.");
    }

    private String restrictionsJson(Set<LocalDate> stopSaleDates) throws JsonProcessingException {
        return mapper.writeValueAsString(Map.of("stopSaleDates", stopSaleDates == null ? List.of() : new TreeSet<>(stopSaleDates)));
    }

    public record RoomTypeView(long id, String name, String saleMode, int quantity, int maxAdults, int maxOccupants, String status, long version) {}
    public record RatePlanView(long id, String name, String policyVersion, java.math.BigDecimal nightlyPrice,
                               String allowedPaymentModes, String pricingRestrictions, String status, long version) {}
    public record CalendarView(LocalDate date, int total, int outOfOrder, boolean stopSale, long version) {}
}
