package com.stayhub.host;

import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.inventory.InventoryManagementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Host-scoped operations view. It reports room-type pools, never physical room numbers. */
@Service
public class HostInventoryDashboardService {
    private static final ZoneId HCM=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LOCAL_TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final JdbcTemplate jdbc;
    private final InventoryManagementService inventory;

    public HostInventoryDashboardService(JdbcTemplate jdbc,InventoryManagementService inventory) {
        this.jdbc=Objects.requireNonNull(jdbc);this.inventory=Objects.requireNonNull(inventory);
    }

    @Transactional(readOnly=true)
    public PropertyOverview property(long hostId,long propertyId,LocalDate from,LocalDate through) {
        if(from==null||through==null||through.isBefore(from)||through.isAfter(from.plusDays(30)))
            throw new com.stayhub.common.exception.BusinessException("ERR_INVALID_DATES","Choose an inventory range of 1–31 days.");
        var propertyRows=jdbc.query("SELECT id,title,city,sale_mode FROM properties WHERE id=? AND host_id=?",
                (r,n)->new PropertyInfo(r.getLong("id"),r.getString("title"),r.getString("city"),r.getString("sale_mode")),propertyId,hostId);
        if(propertyRows.isEmpty()) throw new ResourceNotFoundException("Property not found");

        var types=inventory.listTypes(hostId,propertyId);
        List<TypeOverview> typeViews=new ArrayList<>();
        for(var type:types) {
            var plans=inventory.listPlans(hostId,propertyId,type.id());
            var days=jdbc.query("""
                    SELECT d::date AS stay_date,cal.total,cal.out_of_order,cal.stop_sale,cal.version,
                        (cal.room_type_id IS NOT NULL) AS configured,
                        coalesce(commitment.held,0) AS held,coalesce(commitment.confirmed,0) AS confirmed
                    FROM generate_series(?::date,?::date,interval '1 day') d
                    LEFT JOIN type_day_inventory cal ON cal.room_type_id=? AND cal.stay_date=d::date
                    LEFT JOIN LATERAL (
                        SELECT coalesce(sum(ic.quantity) FILTER (WHERE ic.state='HELD' AND ic.expires_at>clock_timestamp()),0) AS held,
                            coalesce(sum(ic.quantity) FILTER (WHERE ic.state='CONFIRMED'),0) AS confirmed
                        FROM inventory_commitments ic JOIN booking_items bi ON bi.id=ic.item_id
                        WHERE bi.room_type_id=? AND ic.stay_date=d::date
                    ) commitment ON true
                    ORDER BY stay_date
                    """,(r,n)->{
                        boolean configured=r.getBoolean("configured");
                        int total=configured?r.getInt("total"):0;
                        int blocked=configured?r.getInt("out_of_order"):0;
                        int held=r.getInt("held"),confirmed=r.getInt("confirmed");
                        int remaining=Math.max(0,total-blocked-held-confirmed);
                        boolean stopSale=configured&&r.getBoolean("stop_sale");
                        return new CalendarDay(r.getObject("stay_date",LocalDate.class),configured,total,blocked,held,
                                confirmed,remaining,stopSale,configured&&!stopSale&&remaining>0,
                                configured?r.getLong("version"):0L);
                    },from,through,type.id(),type.id());
            typeViews.add(new TypeOverview(type,plans,days));
        }

        var rows=jdbc.query("""
                SELECT b.id AS booking_id,b.status,b.check_in_date,b.check_out_date,b.guests,
                    coalesce(u.full_name,'Guest') AS guest_name,(s.booking_id IS NOT NULL) AS financial_snapshot_available,
                    s.payment_mode,s.total_stay,s.due_online,s.balance_due_host,pay.status AS payment_status,
                    coalesce((SELECT sum(pc.amount) FROM payment_collections pc WHERE pc.booking_id=b.id
                        AND pc.source='ONLINE_VERIFIED' AND pc.disposition='ACCEPTED'),0) AS online_paid,
                    coalesce((SELECT sum(pc.amount) FROM payment_collections pc WHERE pc.booking_id=b.id
                        AND pc.source='HOST_ATTESTED' AND pc.disposition='MEMO'
                        AND NOT EXISTS(SELECT 1 FROM direct_receipt_cases c WHERE c.booking_id=pc.booking_id AND c.receipt_ref=pc.receipt_ref)),0)
                      +coalesce((SELECT sum(c.amount) FROM direct_receipt_cases c WHERE c.booking_id=b.id
                        AND c.state IN ('ACKNOWLEDGED','RESOLVED') AND c.guest_disposition='ACKNOWLEDGE'),0) AS direct_acknowledged,
                    coalesce((SELECT sum(c.amount) FROM direct_receipt_cases c WHERE c.booking_id=b.id
                        AND c.state<>'REVERSED'),0) AS direct_attested,
                    (SELECT count(*) FROM room_allocations ra JOIN booking_items ri ON ri.id=ra.item_id
                        WHERE ri.booking_id=b.id) AS required_rooms,
                    (SELECT count(*) FROM booking_arrivals a JOIN booking_items ai ON ai.id=a.item_id
                        WHERE ai.booking_id=b.id AND a.state IN ('ACKNOWLEDGED','ADMIN_VERIFIED') AND NOT a.needs_review) AS verified_arrivals,
                    (SELECT max(a.actual_at) FROM booking_arrivals a JOIN booking_items ai ON ai.id=a.item_id
                        WHERE ai.booking_id=b.id AND a.state IN ('ACKNOWLEDGED','ADMIN_VERIFIED') AND NOT a.needs_review) AS last_verified_arrival,
                    bi.id AS item_id,rt.name AS room_type,rp.name AS rate_plan,bi.quantity
                FROM bookings b JOIN properties p ON p.id=b.property_id
                LEFT JOIN users u ON u.id=b.guest_id
                LEFT JOIN booking_financial_snapshots s ON s.booking_id=b.id
                LEFT JOIN payments pay ON pay.booking_id=b.id
                LEFT JOIN booking_items bi ON bi.booking_id=b.id
                LEFT JOIN room_types rt ON rt.id=bi.room_type_id
                LEFT JOIN rate_plans rp ON rp.id=bi.rate_plan_id
                WHERE b.property_id=? AND p.host_id=? AND b.check_in_date<=? AND b.check_out_date>?
                  AND b.status IN ('PENDING_PAYMENT','PENDING','CONFIRMED','COMPLETED','CANCELLED','REJECTED')
                ORDER BY b.check_in_date,b.id,bi.id
                """,(r,n)->new BookingData(r.getLong("booking_id"),r.getString("status"),r.getString("guest_name"),
                        r.getObject("check_in_date",LocalDate.class),r.getObject("check_out_date",LocalDate.class),r.getInt("guests"),
                        r.getBoolean("financial_snapshot_available"),r.getString("payment_mode"),r.getString("payment_status"),
                        money(r.getBigDecimal("total_stay")),money(r.getBigDecimal("due_online")),
                        money(r.getBigDecimal("balance_due_host")),money(r.getBigDecimal("online_paid")),
                        money(r.getBigDecimal("direct_acknowledged")),money(r.getBigDecimal("direct_attested")),
                        r.getInt("required_rooms"),r.getInt("verified_arrivals"),r.getTimestamp("last_verified_arrival")==null?null:
                                r.getTimestamp("last_verified_arrival").toInstant(),r.getObject("item_id",Long.class),
                        r.getString("room_type"),r.getString("rate_plan"),r.getInt("quantity")),propertyId,hostId,through,from);
        Map<Long,BookingAccumulator> bookings=new LinkedHashMap<>();
        for(var row:rows) {
            var booking=bookings.computeIfAbsent(row.id(),ignored->new BookingAccumulator(row.id(),row.status(),row.guestName(),
                    row.checkIn(),row.checkOut(),row.guests(),row.financialSnapshotAvailable(),row.paymentMode(),row.paymentStatus(),
                    row.totalStay(),row.dueOnline(),row.onlinePaid(),
                    row.balanceDueHost(),row.directAttested(),row.directAcknowledged(),
                    row.balanceDueHost().subtract(row.directAcknowledged()).max(BigDecimal.ZERO),row.requiredRooms(),row.verifiedArrivals(),
                    row.lastArrival()==null?null:LOCAL_TIME.format(LocalDateTime.ofInstant(row.lastArrival(),HCM))));
            if(row.itemId()!=null) booking.items.add(new BookingItem(row.roomType(),row.ratePlan(),row.quantity()));
        }
        var bookingViews=bookings.values().stream().map(BookingAccumulator::view).toList();
        PropertyInfo info=propertyRows.getFirst();
        return new PropertyOverview(info.id(),info.title(),info.city(),info.saleMode(),from,through,List.copyOf(typeViews),bookingViews);
    }

    private static BigDecimal money(BigDecimal value){return value==null?BigDecimal.ZERO:value;}

    public record PropertyOverview(long propertyId,String title,String city,String saleMode,LocalDate from,LocalDate through,
            List<TypeOverview> roomTypes,List<BookingView> bookings) {}
    public record TypeOverview(InventoryManagementService.RoomTypeView type,List<InventoryManagementService.RatePlanView> plans,
            List<CalendarDay> days) {}
    public record CalendarDay(LocalDate date,boolean configured,int total,int outOfOrder,int held,int confirmed,
            int remaining,boolean stopSale,boolean bookable,long version) {}
    public record BookingView(long id,String status,String guestName,LocalDate checkIn,LocalDate checkOut,int guests,
            boolean financialSnapshotAvailable,
            String paymentMode,String paymentStatus,BigDecimal totalStay,BigDecimal dueOnline,BigDecimal onlinePaid,BigDecimal balanceDueHost,
            BigDecimal directAttested,BigDecimal directAcknowledged,BigDecimal directOutstanding,int requiredRooms,
            int verifiedArrivals,String lastVerifiedArrival,List<BookingItem> items) {}
    public record BookingItem(String roomType,String ratePlan,int quantity) {}
    private record PropertyInfo(long id,String title,String city,String saleMode) {}
    private record BookingData(long id,String status,String guestName,LocalDate checkIn,LocalDate checkOut,int guests,
            boolean financialSnapshotAvailable,String paymentMode,String paymentStatus,BigDecimal totalStay,BigDecimal dueOnline,
            BigDecimal balanceDueHost,BigDecimal onlinePaid,
            BigDecimal directAcknowledged,BigDecimal directAttested,int requiredRooms,int verifiedArrivals,
            java.time.Instant lastArrival,Long itemId,String roomType,String ratePlan,int quantity) {}
    private static final class BookingAccumulator {
        private final long id;private final String status,guestName;private final LocalDate checkIn,checkOut;private final int guests;
        private final boolean financialSnapshotAvailable;private final String paymentMode,paymentStatus;
        private final BigDecimal total,dueOnline,onlinePaid,balance,directAttested,directAck,directOutstanding;
        private final int requiredRooms,verifiedArrivals;private final String lastArrival;private final List<BookingItem> items=new ArrayList<>();
        BookingAccumulator(long id,String status,String guestName,LocalDate checkIn,LocalDate checkOut,int guests,
                boolean financialSnapshotAvailable,String paymentMode,String paymentStatus,
                BigDecimal total,BigDecimal dueOnline,BigDecimal onlinePaid,BigDecimal balance,BigDecimal directAttested,
                BigDecimal directAck,BigDecimal directOutstanding,int requiredRooms,int verifiedArrivals,String lastArrival) {
            this.id=id;this.status=status;this.guestName=guestName;this.checkIn=checkIn;this.checkOut=checkOut;this.guests=guests;
            this.financialSnapshotAvailable=financialSnapshotAvailable;this.paymentMode=paymentMode;
            this.paymentStatus=paymentStatus;this.total=total;
            this.dueOnline=dueOnline;this.onlinePaid=onlinePaid;this.balance=balance;
            this.directAttested=directAttested;this.directAck=directAck;this.directOutstanding=directOutstanding;
            this.requiredRooms=requiredRooms;this.verifiedArrivals=verifiedArrivals;this.lastArrival=lastArrival;
        }
        BookingView view(){return new BookingView(id,status,guestName,checkIn,checkOut,guests,financialSnapshotAvailable,paymentMode,paymentStatus,total,dueOnline,
                onlinePaid,balance,directAttested,directAck,directOutstanding,requiredRooms,verifiedArrivals,lastArrival,List.copyOf(items));}
    }
}
