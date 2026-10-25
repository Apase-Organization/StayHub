package com.stayhub.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.payment.ledger.PaymentPrimitivesService;
import com.stayhub.payment.ledger.PaymentPrimitivesService.Mode;
import com.stayhub.payment.ledger.PaymentPrimitivesService.SnapshotInput;
import com.stayhub.property.*;
import com.stayhub.review.ReviewService;
import com.stayhub.review.dto.ReviewCreateRequest;
import com.stayhub.admin.InventoryFinancialMetricsService;
import com.stayhub.payment.ledger.ArrivalEligibilityService;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class BookingProjectionSmokeTest extends PostgreSqlIntegrationTest {
    private static final String HASH="e".repeat(64);
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PaymentPrimitivesService financials;
    @Autowired BookingRepository bookings;
    @Autowired BookingMapper mapper;
    @Autowired ReviewService reviews;
    @Autowired InventoryFinancialMetricsService metrics;
    @Autowired ArrivalEligibilityService arrivals;
    @Autowired BookingService bookingService;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    long propertyId,guestId,hostId,adminId,bookingId;

    @BeforeEach void usersAndProperty() {
        String nonce=UUID.randomUUID().toString();
        var host=users.save(User.builder().email("projection-host-"+nonce+"@test.invalid").fullName("Host")
                .passwordHash("test").role(UserRole.HOST).status(UserStatus.ACTIVE).build());
        var guest=users.save(User.builder().email("projection-guest-"+nonce+"@test.invalid").fullName("Guest")
                .passwordHash("test").role(UserRole.GUEST).status(UserStatus.ACTIVE).build());
        guestId=guest.getId();
        hostId=host.getId();
        var admin=users.save(User.builder().email("projection-admin-"+nonce+"@test.invalid").fullName("Admin")
                .passwordHash("test").role(UserRole.ADMIN).status(UserStatus.ACTIVE).build());
        adminId=admin.getId();
        var property=properties.save(Property.builder().host(host).title("Frozen title").description("Frozen listing")
                .city("Da Lat").address("Private address").pricePerNight(BigDecimal.valueOf(1_000_000))
                .cleaningFee(BigDecimal.ZERO).maxGuests(2).bedrooms(1).beds(2).bathrooms(1)
                .ratingAvg(BigDecimal.ZERO).propertyType(PropertyType.VILLA).status(PropertyStatus.ACTIVE).build());
        propertyId=property.getId();
        jdbc.update("UPDATE properties SET sale_mode='ROOM_BASED' WHERE id=?",propertyId);
    }

    @Test void itemNotificationPayloadIsTransactionalAndBookingViewUsesFrozenListingAndFinance() {
        var tx=new TransactionTemplate(transactions);
        bookingId=tx.execute(status->{
            LocalDate in=LocalDate.now().minusDays(2),out=in.plusDays(1);
            long booking=jdbc.queryForObject("""
                    INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,
                        cleaning_fee,service_fee,subtotal_price,discount_amount,total_price,status,
                        inventory_currency,payment_mode,inventory_policy_version,inventory_idempotency_key,
                        inventory_request_hash,inventory_hold_expires_at)
                    VALUES (?,?,?, ?,2,2000000,0,0,2000000,0,2000000,'PENDING_PAYMENT','VND','HYBRID_DEPOSIT',
                        's0-policy-v1',?,?::char(64),clock_timestamp()+interval '15 minutes') RETURNING id
                    """,Long.class,propertyId,guestId,in,out,"projection-"+UUID.randomUUID(),HASH);
            var created=jdbc.queryForObject("SELECT created_at FROM bookings WHERE id=?",java.sql.Timestamp.class,booking).toInstant();
            financials.createSnapshot(new SnapshotInput(booking,guestId,Mode.HYBRID_DEPOSIT,"VND",
                    BigDecimal.valueOf(2_000_000),BigDecimal.ZERO,BigDecimal.ZERO,1,
                    "s0-policy-v1","s0-fixtures-v1",HASH,created.plusSeconds(900)),"projection-financial");
            long type=jdbc.queryForObject("""
                    INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants)
                    VALUES (?,'ROOM_BASED','Twin',2,2,2) RETURNING id
                    """,Long.class,propertyId);
            long plan=jdbc.queryForObject("""
                    INSERT INTO rate_plans(property_id,room_type_id,name,policy_version,nightly_price)
                    VALUES (?,?,'Flexible','S0-109-v1',1000000) RETURNING id
                    """,Long.class,propertyId,type);
            long item=jdbc.queryForObject("""
                    INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,
                        max_adults_snapshot,max_occupants_snapshot,type_plan_policy_snapshot)
                    VALUES (?,?,?,?,2,2,2,'{}') RETURNING id
                    """,Long.class,booking,propertyId,type,plan);
            for(int room=1;room<=2;room++) {
                jdbc.update("INSERT INTO room_allocations(item_id,room_ordinal,adults,children_ages) VALUES (?,?,1,'[]')",item,room);
                jdbc.update("""
                        INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,fees,taxes,discount_allocation,policy_snapshot)
                        VALUES (?,?,?,1000000,0,0,0,'{}')
                        """,item,room,in);
            }
            return booking;
        });

        jdbc.update("UPDATE bookings SET status='CONFIRMED' WHERE id=?",bookingId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_notification_outbox WHERE booking_id=?",Integer.class,bookingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload->'items'->0->>'quantity' FROM booking_notification_outbox WHERE booking_id=?",String.class,bookingId)).isEqualTo("2");
        assertThat(jdbc.queryForObject("SELECT payload->'items'->0->>'roomNights' FROM booking_notification_outbox WHERE booking_id=?",String.class,bookingId)).isEqualTo("2");

        jdbc.update("UPDATE properties SET title='Changed title',city='Changed city',status='INACTIVE',price_per_night=2500000 WHERE id=?",propertyId);
        var response=mapper.toResponse(bookings.findDetailedById(bookingId).orElseThrow());
        assertThat(response.getPropertyTitle()).isEqualTo("Frozen title");
        assertThat(response.getPropertyCity()).isEqualTo("Da Lat");
        assertThat(response.getPaymentMode()).isEqualTo("HYBRID_DEPOSIT");
        assertThat(response.getDueOnline()).isEqualByComparingTo("400000");
        assertThat(response.getBalanceDueHost()).isEqualByComparingTo("1600000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_listing_snapshots WHERE booking_id=?",Integer.class,bookingId)).isEqualTo(1);
        var metric=metrics.read();
        assertThat(metric.bookingCount()).isEqualTo(1);
        assertThat(metric.activeInventoryBookings()).isEqualTo(1);
        assertThat(metric.roomsBooked()).isEqualTo(2);
        assertThat(metric.roomNights()).isEqualTo(2);
        assertThat(metric.grossCommissionDue()).isEqualByComparingTo("300000");
        assertThat(metric.hostDirectOutstanding()).isEqualByComparingTo("1600000");
        assertThat(metric.onlineVerified()).isZero();
        assertThat(metric.hostDirectAttested()).isZero();

        Instant actual=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        long itemId=jdbc.queryForObject("SELECT id FROM booking_items WHERE booking_id=?",Long.class,bookingId);
        var reported=arrivals.report(new ArrivalEligibilityService.ArrivalCommand(bookingId,itemId,1,
                hostId,actual,"e".repeat(64),"projection-arrival-1",0));
        var ack=arrivals.guestDecision(bookingId,
                itemId,1,guestId,ArrivalEligibilityService.GuestDecision.ACKNOWLEDGE,"e".repeat(64),"projection-arrival-ack-1",reported.version());
        arrivals.adminVerify(bookingId,
                itemId,1,adminId,"f".repeat(64),"projection-arrival-review-1",ack.version());
        var reported2=arrivals.report(new ArrivalEligibilityService.ArrivalCommand(bookingId,itemId,2,
                hostId,actual,"2".repeat(64),"projection-arrival-2",jdbc.queryForObject(
                        "SELECT version FROM payment_settlement_locks WHERE booking_id=?",Long.class,bookingId)));
        var ack2=arrivals.guestDecision(bookingId,itemId,2,guestId,ArrivalEligibilityService.GuestDecision.ACKNOWLEDGE,
                "2".repeat(64),"projection-arrival-ack-2",reported2.version());
        arrivals.adminVerify(bookingId,itemId,2,adminId,"3".repeat(64),"projection-arrival-review-2",ack2.version());
        bookingService.completeBooking(hostId,bookingId);
        var request=new ReviewCreateRequest();request.setRating((short)5);request.setComment("One booking review");
        assertThat(reviews.createReview(guestId,bookingId,request).getBookingId()).isEqualTo(bookingId);
        assertThatThrownBy(()->reviews.createReview(guestId,bookingId,request))
                .isInstanceOf(com.stayhub.common.exception.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews WHERE booking_id=?",Integer.class,bookingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_notification_outbox WHERE booking_id=?",Integer.class,bookingId)).isEqualTo(2);
    }
}
