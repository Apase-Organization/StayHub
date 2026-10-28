package com.stayhub.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.payment.ledger.PaymentPrimitivesService;
import com.stayhub.inventory.InventoryCapacityService;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class HostInventoryDashboardIntegrationTest extends PostgreSqlIntegrationTest {
    private static final String HASH="d".repeat(64);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired HostInventoryDashboardService dashboard;
    @Autowired PaymentPrimitivesService payments;
    @Autowired InventoryCapacityService capacity;
    private User host;
    private User guest;
    private Property property;
    private long typeId;
    private long planId;
    private long bookingId;
    private LocalDate checkIn;

    @BeforeEach
    void fixture() {
        String nonce=UUID.randomUUID().toString();
        host=users.save(User.builder().email("inventory-view-host-"+nonce+"@test.invalid").fullName("Inventory Host")
                .passwordHash("test").role(UserRole.HOST).status(UserStatus.ACTIVE).build());
        guest=users.save(User.builder().email("inventory-view-guest-"+nonce+"@test.invalid").fullName("Test Guest")
                .passwordHash("test").role(UserRole.GUEST).status(UserStatus.ACTIVE).build());
        property=properties.save(Property.builder().host(host).title("Room inventory overview").description("Test")
                .address("Test address").city("Da Nang").pricePerNight(new BigDecimal("1000000"))
                .cleaningFee(new BigDecimal("100000")).maxGuests(4).bedrooms(2).beds(2).bathrooms(1)
                .ratingAvg(BigDecimal.ZERO).propertyType(PropertyType.HOTEL_ROOM).status(PropertyStatus.ACTIVE).build());
        jdbc.update("UPDATE properties SET sale_mode='ROOM_BASED' WHERE id=?",property.getId());
        typeId=jdbc.queryForObject("""
                INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants)
                VALUES (?,'ROOM_BASED','Deluxe',3,2,3) RETURNING id
                """,Long.class,property.getId());
        planId=jdbc.queryForObject("""
                INSERT INTO rate_plans(property_id,room_type_id,name,policy_version,nightly_price)
                VALUES (?,?,'Flexible','s0-policy-v1',1000000) RETURNING id
                """,Long.class,property.getId(),typeId);
        checkIn=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3);
        jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?,?,3),(?,?,3)",
                typeId,checkIn,typeId,checkIn.plusDays(1));
        bookingId=jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,
                    service_fee,subtotal_price,total_price,status,created_at,updated_at)
                VALUES (?,?,?, ?,2,1000000,100000,0,2100000,2100000,'CONFIRMED',clock_timestamp(),clock_timestamp()) RETURNING id
                """,Long.class,property.getId(),guest.getId(),checkIn,checkIn.plusDays(2));
        InstantCreated created=jdbc.queryForObject("SELECT created_at FROM bookings WHERE id=?",
                (r,n)->new InstantCreated(r.getTimestamp(1).toInstant()),bookingId);
        payments.createSnapshot(new PaymentPrimitivesService.SnapshotInput(bookingId,guest.getId(),PaymentPrimitivesService.Mode.HYBRID_DEPOSIT,
                "VND",new BigDecimal("2000000"),new BigDecimal("100000"),BigDecimal.ZERO,0,
                "s0-policy-v1","s0-fixtures-v1",HASH,created.value().plusSeconds(900)),"inventory-view-snapshot");
        long item=jdbc.queryForObject("""
                INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,
                    max_adults_snapshot,max_occupants_snapshot,type_plan_policy_snapshot)
                VALUES (?,?,?, ?,1,2,3,'{}') RETURNING id
                """,Long.class,bookingId,property.getId(),typeId,planId);
        jdbc.update("INSERT INTO room_allocations(item_id,room_ordinal,adults,children_ages) VALUES (?,1,2,'[4]')",item);
        jdbc.update("""
                INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,fees,taxes,discount_allocation,policy_snapshot)
                VALUES (?,1,?,1000000,100000,0,0,'{}'),(?,1,?,1000000,0,0,0,'{}')
                """,item,checkIn,item,checkIn.plusDays(1));
        jdbc.update("""
                INSERT INTO inventory_commitments(item_id,room_type_id,stay_date,quantity,state,expires_at)
                VALUES (?, ?, ?,1,'HELD',clock_timestamp()+interval '30 days'),
                       (?, ?, ?,1,'HELD',clock_timestamp()+interval '30 days')
                """,item,typeId,checkIn,item,typeId,checkIn.plusDays(1));
        capacity.confirmAll(bookingId);
    }

    @Test
    void hostSeesDailyRemainingInventoryAndTypeAggregatedBookingFinanceWithoutRoomNumbers() throws Exception {
        var view=dashboard.property(host.getId(),property.getId(),checkIn,checkIn.plusDays(1));
        assertThat(view.roomTypes()).hasSize(1);
        assertThat(view.roomTypes().getFirst().days()).extracting(HostInventoryDashboardService.CalendarDay::remaining)
                .containsExactly(2,2);
        assertThat(view.bookings()).hasSize(1);
        var booking=view.bookings().getFirst();
        assertThat(booking.items()).containsExactly(new HostInventoryDashboardService.BookingItem("Deluxe","Flexible",1));
        assertThat(booking.dueOnline()).isEqualByComparingTo("420000");
        assertThat(booking.balanceDueHost()).isEqualByComparingTo("1680000");
        assertThat(booking.directOutstanding()).isEqualByComparingTo("1680000");
        assertThat(booking.requiredRooms()).isEqualTo(1);

        var principal=UserPrincipal.create(host);
        mvc.perform(get("/host/properties/{id}/inventory",property.getId()).param("from",checkIn.toString())
                        .param("through",checkIn.plusDays(1).toString()).with(user(principal)))
                .andExpect(status().isOk()).andExpect(view().name("host/property-inventory"))
                .andExpect(model().attributeExists("overview"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Flexible")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("11:00 HCM")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1,680,000 VND")));

        long version0=jdbc.queryForObject("SELECT version FROM type_day_inventory WHERE room_type_id=? AND stay_date=?",Long.class,typeId,checkIn);
        long version1=jdbc.queryForObject("SELECT version FROM type_day_inventory WHERE room_type_id=? AND stay_date=?",Long.class,typeId,checkIn.plusDays(1));
        String batch="""
                {"days":[{"date":"%s","total":3,"outOfOrder":0,"stopSale":false,"expectedVersion":%d},
                          {"date":"%s","total":3,"outOfOrder":0,"stopSale":false,"expectedVersion":%d}]}
                """.formatted(checkIn,version0,checkIn.plusDays(1),version1);
        mvc.perform(put("/api/v1/host/properties/{propertyId}/inventory/room-types/{typeId}/calendar/batch",
                        property.getId(),typeId).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk());

        var stranger=users.save(User.builder().email("inventory-view-stranger-"+UUID.randomUUID()+"@test.invalid")
                .fullName("Stranger").passwordHash("test").role(UserRole.HOST).status(UserStatus.ACTIVE).build());
        mvc.perform(get("/host/properties/{id}/inventory",property.getId()).with(user(UserPrincipal.create(stranger))))
                .andExpect(status().isNotFound());
    }

    @Test
    void propertyDotPreservesExactStatusAndInventoryKeepsOtherAlerts() throws Exception {
        for (PropertyStatus state : new PropertyStatus[]{PropertyStatus.ACTIVE, PropertyStatus.DRAFT, PropertyStatus.INACTIVE}) {
            property.setStatus(state);
            properties.saveAndFlush(property);
            String html = mvc.perform(get("/host/dashboard").with(user(UserPrincipal.create(host))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("title=\""+state.name()+"\"", "aria-label=\""+state.name()+"\"",
                    state == PropertyStatus.ACTIVE ? "bg-green-500" : "bg-red-500", "min-w-0 flex-1");
        }
        mvc.perform(get("/host/properties/{id}/inventory",property.getId()).with(user(UserPrincipal.create(host))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Tồn kho được quản lý theo"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inventory-message")));
    }

    @Test
    void centralDashboardShowsPaidConfirmedNewestFirstAndKeepsPendingActions() throws Exception {
        jdbc.update("""
                INSERT INTO payments(booking_id,amount,payment_method,status,transaction_id,paid_at)
                VALUES (?,2100000,'VNPAY','SUCCESS',?,clock_timestamp())
                """, bookingId, "dashboard-"+UUID.randomUUID());
        long newer = copyBooking("CONFIRMED", checkIn.plusDays(10));
        long pending = copyBooking("PENDING", checkIn.plusDays(20));
        var result=mvc.perform(get("/host/dashboard").with(user(UserPrincipal.create(host))))
                .andExpect(status().isOk()).andExpect(model().attribute("pendingBookingRequests",1))
                .andReturn();
        @SuppressWarnings("unchecked")
        var confirmed=(java.util.List<com.stayhub.booking.dto.BookingResponse>)result.getModelAndView().getModel().get("confirmedBookings");
        assertThat(confirmed).isNotNull();
        assertThat(confirmed).extracting(com.stayhub.booking.dto.BookingResponse::getId).containsExactly(newer,bookingId);
        assertThat(confirmed.getLast().getPaymentStatus().name()).isEqualTo("SUCCESS");
        String html=result.getResponse().getContentAsString();
        assertThat(html).contains("Đơn đặt phòng đã xác nhận", "/host/bookings/"+bookingId,
                "/host/bookings/"+pending+"/accept", "/host/bookings/"+pending+"/reject")
                .doesNotContain("/host/bookings/"+bookingId+"/accept", "/host/bookings/"+newer+"/reject");
        assertThat(html.indexOf("Yêu cầu đặt phòng")).isLessThan(html.indexOf("Đơn đặt phòng đã xác nhận"));
    }

    @Test
    void centralDashboardLimitsConfirmedToTwentyAndExcludesOtherHosts() throws Exception {
        for(int i=0;i<22;i++) copyBooking("CONFIRMED",checkIn.plusDays(10+i*3));
        var result=mvc.perform(get("/host/dashboard").with(user(UserPrincipal.create(host))))
                .andExpect(status().isOk()).andReturn();
        assertThat((java.util.List<?>)result.getModelAndView().getModel().get("confirmedBookings")).hasSize(20);
        var stranger=users.save(User.builder().email("dashboard-stranger-"+UUID.randomUUID()+"@test.invalid")
                .fullName("Stranger").passwordHash("test").role(UserRole.HOST).status(UserStatus.ACTIVE).build());
        mvc.perform(get("/host/dashboard").with(user(UserPrincipal.create(stranger))))
                .andExpect(status().isOk()).andExpect(model().attribute("confirmedBookings",org.hamcrest.Matchers.empty()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa có chỗ nghỉ")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Room inventory overview"))));
    }

    private long copyBooking(String status, LocalDate date) {
        return jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,
                    service_fee,subtotal_price,total_price,status,created_at,updated_at)
                VALUES (?,?,?, ?,2,1000000,100000,0,2100000,2100000,?,
                    (SELECT created_at FROM bookings WHERE id=?),clock_timestamp()) RETURNING id
                """,Long.class,property.getId(),guest.getId(),date,date.plusDays(2),status,bookingId);
    }

    private record InstantCreated(java.time.Instant value) {}
}
