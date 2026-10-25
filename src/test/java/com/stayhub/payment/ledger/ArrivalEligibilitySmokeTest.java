package com.stayhub.payment.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.payment.ledger.PaymentPrimitivesService.*;
import com.stayhub.property.*;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.*;
import com.stayhub.booking.BookingService;
import com.stayhub.booking.BookingRepository;
import com.stayhub.review.ReviewService;
import com.stayhub.review.dto.ReviewCreateRequest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ArrivalEligibilitySmokeTest extends PostgreSqlIntegrationTest {
    private static final String HASH="c".repeat(64);
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired PaymentPrimitivesService primitives;
    @Autowired ArrivalEligibilityService arrivals;
    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired ReviewService reviews;
    long host,guest,admin,booking,item;
    long checker;
    Instant actual;

    @BeforeEach void fixture() {
        String nonce=UUID.randomUUID().toString();
        var h=users.save(User.builder().email("arrival-host-"+nonce+"@test.invalid").fullName("Host")
                .passwordHash("test").role(UserRole.HOST).status(UserStatus.ACTIVE).build());
        var g=users.save(User.builder().email("arrival-guest-"+nonce+"@test.invalid").fullName("Guest")
                .passwordHash("test").role(UserRole.GUEST).status(UserStatus.ACTIVE).build());
        var a=users.save(User.builder().email("arrival-admin-"+nonce+"@test.invalid").fullName("Admin")
                .passwordHash("test").role(UserRole.ADMIN).status(UserStatus.ACTIVE).build());
        var c=users.save(User.builder().email("arrival-checker-"+nonce+"@test.invalid").fullName("Checker")
                .passwordHash("test").role(UserRole.ADMIN).status(UserStatus.ACTIVE).build());
        host=h.getId(); guest=g.getId(); admin=a.getId(); checker=c.getId();
        var p=properties.save(Property.builder().host(h).title("Arrival smoke").description("Test")
                .city("Da Lat").address("Test").pricePerNight(BigDecimal.valueOf(1_000_000))
                .cleaningFee(BigDecimal.ZERO).maxGuests(1).bedrooms(1).beds(1).bathrooms(1)
                .ratingAvg(BigDecimal.ZERO).propertyType(PropertyType.VILLA).status(PropertyStatus.ACTIVE).build());
        jdbc.update("UPDATE properties SET sale_mode='ROOM_BASED' WHERE id=?",p.getId());
        LocalDate in=LocalDate.now().minusDays(2),out=in.plusDays(1);
        booking=jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,
                    service_fee,subtotal_price,discount_amount,total_price,status,cancelled_at,created_at,updated_at)
                VALUES (?,?,?, ?,1,1000000,0,0,1000000,0,1000000,'CONFIRMED',NULL,clock_timestamp(),clock_timestamp())
                RETURNING id
                """,Long.class,p.getId(),guest,in,out);
        var created=jdbc.queryForObject("SELECT created_at FROM bookings WHERE id=?",Timestamp.class,booking).toInstant();
        primitives.createSnapshot(new SnapshotInput(booking,guest,Mode.FULL_PREPAY,"VND",BigDecimal.valueOf(1_000_000),
                BigDecimal.ZERO,BigDecimal.ZERO,0,"s0-policy-v1","s0-fixtures-v1",HASH,created.plusSeconds(900)),"arrival-snapshot");
        long type=jdbc.queryForObject("""
                INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants)
                VALUES (?,'ROOM_BASED','Standard',1,1,1) RETURNING id
                """,Long.class,p.getId());
        long plan=jdbc.queryForObject("""
                INSERT INTO rate_plans(property_id,room_type_id,name,policy_version,nightly_price)
                VALUES (?,?,'Standard','S0-109-v1',1000000) RETURNING id
                """,Long.class,p.getId(),type);
        jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?,?,1)",type,in);
        item=jdbc.queryForObject("""
                INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,
                    max_adults_snapshot,max_occupants_snapshot,type_plan_policy_snapshot)
                VALUES (?,?,?,?,1,1,1,'{}') RETURNING id
                """,Long.class,booking,p.getId(),type,plan);
        jdbc.update("INSERT INTO room_allocations(item_id,room_ordinal,adults,children_ages) VALUES (?,1,1,'[]')",item);
        jdbc.update("""
                INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,fees,taxes,discount_allocation,policy_snapshot)
                VALUES (?,1,?,1000000,0,0,0,'{}')
                """,item,in);
        actual=Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test void guestAcknowledgedPerRoomEvidencePlus24hAndAllClearanceGatesControlEligibility() {
        var reported=arrivals.report(new ArrivalEligibilityService.ArrivalCommand(booking,item,1,host,actual,HASH,"arrival-report",0));
        assertThat(reported.state()).isEqualTo("REPORTED");
        var ack=arrivals.guestDecision(booking,item,1,guest,ArrivalEligibilityService.GuestDecision.ACKNOWLEDGE,
                HASH,"arrival-ack",reported.version());
        assertThat(ack.state()).isEqualTo("ACKNOWLEDGED");
        var verified=arrivals.adminVerify(booking,item,1,admin,HASH,"arrival-admin-verify",ack.version());
        assertThat(verified.state()).isEqualTo("ADMIN_VERIFIED");
        var blocked=arrivals.eligibility(booking);
        assertThat(blocked.eligible()).isFalse();
        assertThat(blocked.blockers()).contains("ARRIVAL_PLUS_24H_NOT_REACHED","RELEASE_CLEARANCE_NOT_APPROVED");
        jdbc.update("""
                INSERT INTO booking_release_clearance(booking_id,finance_legal,operations,liquidity,evidence_digest,updated_by,updated_at)
                VALUES (?,true,true,true,?,?,clock_timestamp())
                """,booking,HASH,admin);
        var justBefore=new ArrivalEligibilityService(jdbc,Clock.fixed(actual.plusSeconds(86_400).minusMillis(1),ZoneOffset.UTC))
                .eligibility(booking);
        assertThat(justBefore.eligible()).isFalse();
        var ready=new ArrivalEligibilityService(jdbc,Clock.fixed(actual.plusSeconds(86_400),ZoneOffset.UTC))
                .eligibility(booking);
        assertThat(ready.eligible()).as(ready.blockers().toString()).isTrue();
        assertThat(ready.earliestAt()).isEqualTo(actual.plusSeconds(86400).truncatedTo(ChronoUnit.MICROS));
        var completed=bookingService.completeBooking(host,booking);
        assertThat(completed.getStatus()).isEqualTo(com.stayhub.booking.BookingStatus.COMPLETED);
        bookings.flush();
        assertThat(jdbc.queryForObject("SELECT completed_at FROM bookings WHERE id=?",Timestamp.class,booking)).isNotNull();
        var review=new ReviewCreateRequest();review.setRating((short)5);review.setComment("Verified stay");
        assertThat(reviews.createReview(guest,booking,review).getBookingId()).isEqualTo(booking);
    }

    @Test void contestedRoomArrivalCreatesSettlementBlockAndCannotUnlockEligibility() {
        var reported=arrivals.report(new ArrivalEligibilityService.ArrivalCommand(booking,item,1,host,actual,HASH,"arrival-report",0));
        var contested=arrivals.guestDecision(booking,item,1,guest,ArrivalEligibilityService.GuestDecision.CONTEST,
                HASH,"arrival-contest",reported.version());
        assertThat(contested.state()).isEqualTo("CONTESTED");
        var result=arrivals.eligibility(booking);
        assertThat(result.eligible()).isFalse();
        assertThat(result.blockers()).contains("ARRIVAL_UNACKNOWLEDGED_OR_CONTESTED","ACTIVE_SETTLEMENT_BLOCK");
    }

    @Test void cancelledNoArrivalBookingCannotBeCompletedOrReviewed() {
        jdbc.update("UPDATE bookings SET status='CANCELLED',cancelled_at=clock_timestamp() WHERE id=?",booking);
        assertThatThrownBy(()->arrivals.authorizeCompletion(booking,host))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_INVALID_STATE");
        var request=new com.stayhub.review.dto.ReviewCreateRequest();request.setRating((short)5);
        assertThatThrownBy(()->reviews.createReview(guest,booking,request))
                .isInstanceOf(com.stayhub.common.exception.BusinessException.class)
                .extracting("errorCode").isEqualTo("ERR_REVIEW_NOT_ALLOWED");
    }

    @Test void adminNoShowFindingIsAuditedAndBlocksPayoutAndCompletion() {
        long block=arrivals.recordNoShow(booking,admin,HASH,"no-show",0);
        assertThat(jdbc.queryForObject("SELECT reason_code FROM booking_settlement_blocks WHERE id=?",String.class,block))
                .isEqualTo("NO_SHOW");
        assertThat(arrivals.eligibility(booking).blockers()).contains("ACTIVE_SETTLEMENT_BLOCK");
        assertThatThrownBy(()->bookingService.completeBooking(host,booking))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_ACTUAL_STAY_REQUIRED");
        arrivals.resolveBlock(booking,block,admin,HASH,"resolve-no-show",1);
        assertThat(arrivals.eligibility(booking).blockers()).contains("ALL_ROOMS_NOT_RECORDED");
    }

    @Test void aDisputeAfterDocumentedManualPayoutOpensAnOperationsFinanceRecoveryCase() {
        var attempt=primitives.allocateAttempt(booking,guest,"merchant","test-config","arrival-payout-attempt");
        primitives.recordVerifiedReceipt(new VerifiedReceipt(booking,guest,attempt.id(),"arrival-online-"+booking,
                BigDecimal.valueOf(1_000_000),"VND",Instant.now().minusSeconds(1),HASH,ReceiptIntent.REQUIRED_COLLECTION),
                "arrival-online-collection");
        long reservation=primitives.reserve(new ReserveInput(booking,admin,ReserveKind.PAYOUT,MoneySource.ONLINE_VERIFIED,
                BigDecimal.valueOf(100_000),"VND",BigDecimal.valueOf(850_000),BigDecimal.valueOf(850_000),HASH,
                primitives.settlementVersion(booking)),"arrival-payout-reserve");
        primitives.moveReserve(booking,admin,reservation,Movement.CONSUME,HASH,"arrival-payout-consume",
                primitives.settlementVersion(booking));
        arrivals.recordManualPayoutProof(new ArrivalEligibilityService.PayoutProofCommand(booking,reservation,admin,checker,
                "manual-transfer-ref",HASH,Instant.now(),"payout-proof"));

        var reported=arrivals.report(new ArrivalEligibilityService.ArrivalCommand(booking,item,1,host,actual,HASH,
                "payout-arrival",primitives.settlementVersion(booking)));
        arrivals.guestDecision(booking,item,1,guest,ArrivalEligibilityService.GuestDecision.CONTEST,HASH,
                "payout-arrival-contest",reported.version());
        var recoveries=arrivals.recoveriesForActor(booking,admin);
        assertThat(recoveries).hasSize(1);
        assertThat(recoveries.getFirst().exposureAmount()).isEqualByComparingTo("100000");
        assertThat(recoveries.getFirst().ownerRole()).isEqualTo("OperationsFinance");
        assertThat(arrivals.eligibility(booking).blockers()).contains("POST_PAYOUT_RECOVERY_OPEN");
        assertThatThrownBy(()->arrivals.resolveRecovery(booking,recoveries.getFirst().id(),admin,admin,"same-human",
                ArrivalEligibilityService.RecoveryOutcome.MANUAL_RECOVERY_DOCUMENTED,HASH,"recovery-self",0))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_MAKER_CHECKER_CONFLICT");
        var resolved=arrivals.resolveRecovery(booking,recoveries.getFirst().id(),admin,checker,"recovery-bank-ref",
                ArrivalEligibilityService.RecoveryOutcome.MANUAL_RECOVERY_DOCUMENTED,HASH,"recovery-checker",0);
        assertThat(resolved.state()).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("SELECT resolution_checker_id FROM payout_recovery_cases WHERE id=?",Long.class,
                recoveries.getFirst().id())).isEqualTo(checker);
    }
}
