package com.stayhub.payment.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingStatus;
import com.stayhub.payment.ledger.PaymentPrimitivesService.*;
import com.stayhub.property.*;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RefundWorkflowSmokeTest extends PostgreSqlIntegrationTest {
    private static final String HASH="d".repeat(64);
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired PaymentPrimitivesService primitives;
    @Autowired RefundWorkflowService refunds;
    private long booking,maker,checker,guest,host;

    @BeforeEach void fixture() {
        String nonce=UUID.randomUUID().toString();
        var h=user("refund-host-"+nonce,UserRole.HOST);var g=user("refund-guest-"+nonce,UserRole.GUEST);
        var m=user("refund-maker-"+nonce,UserRole.ADMIN);var c=user("refund-checker-"+nonce,UserRole.ADMIN);
        host=h.getId();guest=g.getId();maker=m.getId();checker=c.getId();
        var p=properties.save(Property.builder().host(h).title("Refund smoke").description("Test")
                .city("Da Lat").address("Test").pricePerNight(BigDecimal.valueOf(900_000))
                .cleaningFee(BigDecimal.valueOf(100_000)).maxGuests(1).bedrooms(1).beds(1).bathrooms(1)
                .ratingAvg(BigDecimal.ZERO).propertyType(PropertyType.VILLA).status(PropertyStatus.ACTIVE).build());
        LocalDate checkin=LocalDate.now().plusDays(5);
        booking=jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,
                    service_fee,subtotal_price,discount_amount,total_price,status,cancelled_at,created_at,updated_at)
                VALUES (?,?,?, ?,1,900000,100000,0,1000000,0,1000000,'CANCELLED',clock_timestamp(),clock_timestamp(),clock_timestamp())
                RETURNING id
                """,Long.class,p.getId(),guest,checkin,checkin.plusDays(1));
        Instant created=jdbc.queryForObject("SELECT created_at FROM bookings WHERE id=?",Timestamp.class,booking).toInstant();
        primitives.createSnapshot(new SnapshotInput(booking,guest,Mode.FULL_PREPAY,"VND",BigDecimal.valueOf(900_000),
                BigDecimal.valueOf(100_000),BigDecimal.ZERO,0,"s0-policy-v1","s0-fixtures-v1",HASH,created.plusSeconds(900)),"snapshot");
        var attempt=primitives.allocateAttempt(booking,guest,"test-merchant","sandbox-v1","attempt");
        primitives.recordVerifiedReceipt(new VerifiedReceipt(booking,guest,attempt.id(),"refund-source-"+nonce,
                BigDecimal.valueOf(1_000_000),"VND",Instant.now().minusSeconds(2),HASH,ReceiptIntent.REQUIRED_COLLECTION),"collect");
        jdbc.update("INSERT INTO booking_release_clearance(booking_id,finance_legal,operations,liquidity,evidence_digest,updated_by,updated_at) VALUES (?,true,true,true,?,?,clock_timestamp())",
                booking,HASH,maker);
        jdbc.update("INSERT INTO refund_sla_config(minimum_business_days,maximum_business_days,timezone,calendar_version,signer_id,signed_evidence_digest,active) VALUES (5,10,'Asia/Ho_Chi_Minh','test-calendar',?, ?,true)",checker,HASH);
        jdbc.update("INSERT INTO refund_liquidity_snapshots(available_online,as_of,actor_id,evidence_digest) VALUES (1000000,clock_timestamp(),?,?)",maker,HASH);
    }
    private User user(String email,UserRole role) {
        return users.save(User.builder().email(email+"@test.invalid").fullName(role.name()).passwordHash("test")
                .role(role).status(UserStatus.ACTIVE).build());
    }

    @Test void sourceBoundFullRefundRequiresDifferentCheckerAndTransferProof() {
        var requested=refunds.requestFreeWindowRefund(booking,maker,RefundWorkflowService.Source.ONLINE_VERIFIED,"refund-request");
        assertThat(requested.state()).isEqualTo(RefundWorkflowService.State.REQUESTED);
        assertThat(requested.amount()).isEqualByComparingTo("1000000");
        assertThat(refunds.requestFreeWindowRefund(booking,maker,RefundWorkflowService.Source.ONLINE_VERIFIED,"refund-request"))
                .isEqualTo(requested);
        assertThatThrownBy(()->refunds.approve(requested.id(),maker,"self-approve",HASH,requested.version()))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_MAKER_CHECKER_CONFLICT");
        var approved=refunds.approve(requested.id(),checker,"approve",HASH,requested.version());
        var processing=refunds.processing(requested.id(),checker,"processing",HASH,approved.version());
        var paid=refunds.paid(requested.id(),checker,"paid","bank-reference",HASH,processing.version());
        assertThat(paid.state()).isEqualTo(RefundWorkflowService.State.PAID);
        assertThat(paid.checkerId()).isEqualTo(checker);
        assertThat(paid.transferRef()).isEqualTo("bank-reference");
        assertThat(jdbc.queryForObject("SELECT sum(credit-debit) FROM ledger_lines WHERE booking_id=? AND account='ONLINE_CASH'",
                BigDecimal.class,booking)).isZero();
        assertThat(jdbc.queryForObject("SELECT action FROM ledger_reserve_movements WHERE reservation_id=(SELECT reservation_id FROM refund_cases WHERE id=?)",
                String.class,requested.id())).isEqualTo("CONSUME");
    }

    @Test void lateCancellationRefundsOnlyTheCollectedNetCleaningAllocation() {
        jdbc.update("UPDATE bookings SET check_in_date=?,check_out_date=?,cancelled_at=clock_timestamp() WHERE id=?",
                LocalDate.now().plusDays(1),LocalDate.now().plusDays(2),booking);
        var requested=refunds.requestPolicyRefund(booking,maker,RefundWorkflowService.Source.ONLINE_VERIFIED,
                RefundWorkflowService.PolicyBasis.LATE_UNUSED_CLEANING,"late-cleaning",HASH);
        assertThat(requested.amount()).isEqualByComparingTo("100000");
        assertThat(jdbc.queryForObject("SELECT sum(net_amount) FROM refund_charge_allocations WHERE refund_case_id=? AND charge_type='CLEANING'",
                BigDecimal.class,requested.id())).isEqualByComparingTo("100000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refund_charge_allocations WHERE refund_case_id=? AND charge_type<>'CLEANING'",
                Integer.class,requested.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT policy_basis FROM refund_cases WHERE id=?",String.class,requested.id()))
                .isEqualTo("LATE_UNUSED_CLEANING");
    }

    @Test void hostInabilityRequiresHostEvidenceCaseThenCancelsAndReleasesBeforeFullCollectedRefund() {
        jdbc.update("UPDATE bookings SET status='CONFIRMED',cancelled_at=NULL WHERE id=?",booking);
        long version=jdbc.queryForObject("SELECT version FROM payment_settlement_locks WHERE booking_id=?",Long.class,booking);
        long caseId=refunds.recordHostUnable(booking,maker,HASH,"host-unable",version);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,booking)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT active FROM booking_settlement_blocks WHERE id=?",Boolean.class,caseId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_settlement_block_events WHERE block_id=?",Integer.class,caseId)).isEqualTo(2);
        var refund=refunds.requestPolicyRefund(booking,maker,RefundWorkflowService.Source.ONLINE_VERIFIED,
                RefundWorkflowService.PolicyBasis.FULL_COLLECTED_HOST_UNABLE,"host-unable-refund",HASH);
        assertThat(refund.amount()).isEqualByComparingTo("1000000");
        assertThat(jdbc.queryForObject("SELECT policy_basis FROM refund_cases WHERE id=?",String.class,refund.id()))
                .isEqualTo("FULL_COLLECTED_HOST_UNABLE");
    }
}
