package com.stayhub.payment.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.stayhub.payment.ledger.PaymentPrimitivesService.*;
import com.stayhub.property.*;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PayoutWorkflowSmokeTest extends PostgreSqlIntegrationTest {
    private static final String HASH="f".repeat(64);
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired PaymentPrimitivesService primitives;
    @Autowired PayoutWorkflowService payouts;
    @SpyBean ArrivalEligibilityService arrivals;
    long host,guest,maker,checker,booking,item;

    @BeforeEach void fixture() {
        String nonce=UUID.randomUUID().toString();
        var h=user("payout-host-"+nonce,UserRole.HOST);var g=user("payout-guest-"+nonce,UserRole.GUEST);
        var m=user("payout-maker-"+nonce,UserRole.ADMIN);var c=user("payout-checker-"+nonce,UserRole.ADMIN);
        host=h.getId();guest=g.getId();maker=m.getId();checker=c.getId();
        var p=properties.save(Property.builder().host(h).title("Payout smoke").description("Test")
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
        Instant created=jdbc.queryForObject("SELECT created_at FROM bookings WHERE id=?",Timestamp.class,booking).toInstant();
        primitives.createSnapshot(new SnapshotInput(booking,guest,Mode.FULL_PREPAY,"VND",BigDecimal.valueOf(1_000_000),
                BigDecimal.ZERO,BigDecimal.ZERO,0,"s0-policy-v1","s0-fixtures-v1",HASH,created.plusSeconds(900)),"payout-snapshot");
        var attempt=primitives.allocateAttempt(booking,guest,"merchant","sandbox","payout-attempt");
        primitives.recordVerifiedReceipt(new VerifiedReceipt(booking,guest,attempt.id(),"payout-collection-"+nonce,
                BigDecimal.valueOf(1_000_000),"VND",Instant.now().minusSeconds(1),HASH,ReceiptIntent.REQUIRED_COLLECTION),"payout-collection");
        long type=jdbc.queryForObject("INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants) VALUES (?,'ROOM_BASED','Room',1,1,1) RETURNING id",Long.class,p.getId());
        long plan=jdbc.queryForObject("INSERT INTO rate_plans(property_id,room_type_id,name,policy_version,nightly_price) VALUES (?,?,'Plan','S0-109-v1',1000000) RETURNING id",Long.class,p.getId(),type);
        jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?,?,1)",type,in);
        item=jdbc.queryForObject("INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,max_adults_snapshot,max_occupants_snapshot,type_plan_policy_snapshot) VALUES (?,?,?,?,1,1,1,'{}') RETURNING id",Long.class,booking,p.getId(),type,plan);
        jdbc.update("INSERT INTO room_allocations(item_id,room_ordinal,adults,children_ages) VALUES (?,1,1,'[]')",item);
        jdbc.update("INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,fees,taxes,discount_allocation,policy_snapshot) VALUES (?,1,?,1000000,0,0,0,'{}')",item,in);
        jdbc.update("INSERT INTO booking_release_clearance(booking_id,finance_legal,operations,liquidity,evidence_digest,updated_by,updated_at) VALUES (?,true,true,true,?,?,clock_timestamp())",booking,HASH,maker);
        jdbc.update("INSERT INTO refund_liquidity_snapshots(available_online,as_of,actor_id,evidence_digest) VALUES (2000000,clock_timestamp(),?,?)",maker,HASH);
        doReturn(new ArrivalEligibilityService.Eligibility(booking,true,Instant.now().minusSeconds(1),1,1,List.of(),0))
                .when(arrivals).eligibilityForActor(eq(booking),eq(maker));
    }

    private User user(String email,UserRole role) {
        return users.save(User.builder().email(email+"@test.invalid").fullName(role.name()).passwordHash("test")
                .role(role).status(UserStatus.ACTIVE).build());
    }

    @Test void payoutNeedsMakerCheckerAndProofAndDisputeOpensRecoveryWithoutAssumingHostDebt() {
        Instant actual=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var reported=arrivals.report(new ArrivalEligibilityService.ArrivalCommand(booking,item,1,host,actual,HASH,
                "payout-arrival",primitives.settlementVersion(booking)));
        var ack=arrivals.guestDecision(booking,item,1,guest,ArrivalEligibilityService.GuestDecision.ACKNOWLEDGE,
                HASH,"payout-arrival-ack",reported.version());
        var verified=arrivals.adminVerify(booking,item,1,maker,HASH,"payout-arrival-admin-verify",ack.version());
        var request=payouts.request(booking,maker,"payout-request");
        assertThat(request.amount()).isEqualByComparingTo("850000");
        var approved=payouts.approve(request.id(),checker,"payout-approve",HASH,request.version());
        var processing=payouts.processing(request.id(),checker,"payout-processing",HASH,approved.version());
        var paid=payouts.paid(request.id(),checker,"payout-paid","manual-bank-ref",HASH,processing.version());
        assertThat(paid.state()).isEqualTo(PayoutWorkflowService.State.PAID);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payout_execution_proofs WHERE reservation_id=?",Integer.class,
                request.reservationId())).isEqualTo(1);
        long dispute=arrivals.openDispute(booking,guest,"b".repeat(64),"post-payout-dispute",
                primitives.settlementVersion(booking));
        assertThat(dispute).isPositive();
        var recoveries=arrivals.recoveriesForActor(booking,maker);
        assertThat(recoveries).hasSize(1);
        assertThat(recoveries.getFirst().exposureAmount()).isEqualByComparingTo("850000");
        assertThat(recoveries.getFirst().ownerRole()).isEqualTo("OperationsFinance");
        assertThat(arrivals.eligibility(booking).blockers()).contains("POST_PAYOUT_RECOVERY_OPEN","ACTIVE_SETTLEMENT_BLOCK");
    }
}
