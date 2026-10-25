package com.stayhub.payment.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.payment.ledger.PaymentPrimitivesService.*;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DirectReceiptSmokeTest extends PostgreSqlIntegrationTest {
    private static final String HASH = "a".repeat(64);
    @Autowired JdbcTemplate jdbc;
    @Autowired DirectReceiptService receipts;
    @Autowired PrivateEvidenceService privateEvidence;
    @Autowired PaymentPrimitivesService primitives;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    private long hostId;
    private long guestId;
    private long adminId;
    private long bookingId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        User host = users.save(User.builder().email("direct-host-" + suffix + "@test.invalid")
                .fullName("Host").passwordHash("test").role(UserRole.HOST).status(UserStatus.ACTIVE).build());
        User guest = users.save(User.builder().email("direct-guest-" + suffix + "@test.invalid")
                .fullName("Guest").passwordHash("test").role(UserRole.GUEST).status(UserStatus.ACTIVE).build());
        User admin = users.save(User.builder().email("direct-admin-" + suffix + "@test.invalid")
                .fullName("Admin").passwordHash("test").role(UserRole.ADMIN).status(UserStatus.ACTIVE).build());
        hostId = host.getId();
        guestId = guest.getId();
        adminId = admin.getId();
        Property property = properties.save(Property.builder().host(host).title("Receipt smoke")
                .description("Test").city("Da Lat").address("Test").pricePerNight(BigDecimal.valueOf(1_000_000))
                .cleaningFee(BigDecimal.ZERO).maxGuests(2).bedrooms(1).beds(1).bathrooms(1)
                .ratingAvg(BigDecimal.ZERO).propertyType(PropertyType.VILLA).status(PropertyStatus.ACTIVE).build());
        bookingId = jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,
                    cleaning_fee,service_fee,subtotal_price,discount_amount,total_price,status,cancelled_at,created_at,updated_at)
                VALUES (?,?,?,?,1,1000000,0,0,1000000,0,1000000,'CANCELLED',clock_timestamp(),clock_timestamp(),clock_timestamp())
                RETURNING id
                """, Long.class, property.getId(), guestId, LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));
        Instant created = jdbc.queryForObject("SELECT created_at FROM bookings WHERE id=?", Timestamp.class, bookingId).toInstant();
        primitives.createSnapshot(new SnapshotInput(bookingId, guestId, Mode.HYBRID_DEPOSIT, "VND",
                BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                "s0-policy-v1", "s0-fixtures-v1", HASH, created.plusSeconds(900)), "snapshot");
    }

    @Test
    void hostAttestationCanBeAcknowledgedButNeverBecomesProviderCashAndRejectsOverpayment() {
        assertThatThrownBy(()->receipts.listForHost(bookingId,guestId))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_NOT_FOUND");
        assertThatThrownBy(()->receipts.listForGuest(bookingId,hostId))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_NOT_FOUND");
        var receipt = receipts.attest(new DirectReceiptService.Attestation(bookingId, hostId, "cash-1",
                BigDecimal.valueOf(300_000), "VND", DirectReceiptService.Method.CASH,
                Instant.now(), HASH, "attest-1", 0));
        assertThat(receipt.state()).isEqualTo("ATTESTED");
        assertThatThrownBy(()->receipts.attest(new DirectReceiptService.Attestation(bookingId,hostId,"cash-1",
                BigDecimal.valueOf(300_001),"VND",DirectReceiptService.Method.CASH,Instant.now(),HASH,"attest-1",0)))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(()->receipts.attest(new DirectReceiptService.Attestation(bookingId,guestId,"wrong-host",
                BigDecimal.ONE,"VND",DirectReceiptService.Method.CASH,Instant.now(),HASH,"wrong-host",0)))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_FORBIDDEN");
        assertThatThrownBy(()->receipts.attest(new DirectReceiptService.Attestation(bookingId,hostId,"stale-version",
                BigDecimal.ONE,"VND",DirectReceiptService.Method.CASH,Instant.now(),HASH,"stale-version",0)))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_VERSION_CONFLICT");

        var acknowledged = receipts.guestDisposition(bookingId, guestId, receipt.id(),
                DirectReceiptService.Disposition.ACKNOWLEDGE, "ack-1", HASH, receipt.version());
        assertThat(receipts.guestDisposition(bookingId, guestId, receipt.id(),
                DirectReceiptService.Disposition.ACKNOWLEDGE, "ack-1", HASH, receipt.version())).isEqualTo(acknowledged);
        assertThat(acknowledged.state()).isEqualTo("ACKNOWLEDGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=?", Integer.class,
                bookingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT source FROM payment_collections WHERE booking_id=?",String.class,bookingId))
                .isEqualTo("HOST_ATTESTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_lines WHERE booking_id=? AND account='ONLINE_CASH'",
                Integer.class, bookingId)).isZero();
        assertThat(jdbc.queryForObject("SELECT sum(credit-debit) FROM ledger_lines WHERE booking_id=? AND account='MEMO_HOST_DIRECT'",
                BigDecimal.class,bookingId)).isEqualByComparingTo("-300000");

        var second=receipts.attest(new DirectReceiptService.Attestation(bookingId,hostId,"cash-2",
                BigDecimal.valueOf(500_000),"VND",DirectReceiptService.Method.CASH,Instant.now(),
                "b".repeat(64),"attest-2",acknowledged.version()));
        var secondAck=receipts.guestDisposition(bookingId,guestId,second.id(),DirectReceiptService.Disposition.ACKNOWLEDGE,
                "ack-2","b".repeat(64),second.version());
        var balance=receipts.balanceForActor(bookingId,guestId);
        assertThat(balance.hostAttested()).isEqualByComparingTo("800000");
        assertThat(balance.guestAcknowledged()).isEqualByComparingTo("800000");
        assertThat(balance.outstanding()).isZero();
        assertThat(receipts.listForGuest(bookingId,guestId)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM private_evidence_access_log WHERE booking_id=? AND actor_id=?",
                Integer.class,bookingId,guestId)).isEqualTo(2);
        primitives.reserve(new ReserveInput(bookingId,guestId,ReserveKind.REFUND,MoneySource.HOST_ATTESTED,
                BigDecimal.valueOf(800_000),"VND",BigDecimal.valueOf(800_000),BigDecimal.valueOf(800_000),HASH,
                secondAck.version()),"direct-refund-reserve");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_lines WHERE booking_id=? AND account='ONLINE_CASH'",
                Integer.class,bookingId)).isZero();

        assertThatThrownBy(() -> receipts.attest(new DirectReceiptService.Attestation(bookingId, hostId, "cash-3",
                BigDecimal.ONE, "VND", DirectReceiptService.Method.CASH,Instant.now(), HASH, "attest-over",
                jdbc.queryForObject("SELECT version FROM payment_settlement_locks WHERE booking_id=?",Long.class,bookingId))))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_DIRECT_OVERPAYMENT");
    }

    @Test
    void adminReversalAppendsHistoryAndRestoresDirectOutstanding() {
        var receipt = receipts.attest(new DirectReceiptService.Attestation(bookingId, hostId, "transfer-1",
                BigDecimal.valueOf(300_000), "VND", DirectReceiptService.Method.BANK_TRANSFER,
                Instant.now(), HASH, "attest-transfer", 0));
        var acknowledged=receipts.guestDisposition(bookingId,guestId,receipt.id(),DirectReceiptService.Disposition.ACKNOWLEDGE,
                "ack-transfer",HASH,receipt.version());
        var reversed = receipts.reverse(bookingId, adminId, receipt.id(), HASH, "reverse-transfer", acknowledged.version());
        assertThat(reversed.state()).isEqualTo("REVERSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_receipt_case_events WHERE case_id=?", Integer.class,
                receipt.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT sum(credit-debit) FROM ledger_lines WHERE booking_id=? AND account='MEMO_HOST_DIRECT'",
                BigDecimal.class,bookingId)).isZero();
        var replacement = receipts.attest(new DirectReceiptService.Attestation(bookingId, hostId, "transfer-2",
                BigDecimal.valueOf(800_000), "VND", DirectReceiptService.Method.BANK_TRANSFER,
                Instant.now(), "b".repeat(64), "attest-transfer-2", reversed.version()));
        assertThat(replacement.state()).isEqualTo("ATTESTED");
    }

    @Test
    void adminCanResolveContestedReceiptWithAuditedAcceptedDisposition() {
        var receipt=receipts.attest(new DirectReceiptService.Attestation(bookingId,hostId,"resolved-ref",
                BigDecimal.valueOf(100_000),"VND",DirectReceiptService.Method.CASH,Instant.now(),HASH,"resolve-attest",0));
        var contested=receipts.guestDisposition(bookingId,guestId,receipt.id(),DirectReceiptService.Disposition.CONTEST,
                "resolve-contest",HASH,receipt.version());
        var resolved=receipts.resolve(bookingId,adminId,receipt.id(),DirectReceiptService.AdminDecision.ACCEPT_RECEIPT,
                "b".repeat(64),"resolve-admin",contested.version());
        assertThat(resolved.state()).isEqualTo("RESOLVED");
        assertThat(resolved.guestDisposition()).isEqualTo("ACKNOWLEDGE");
        assertThat(receipts.balanceForActor(bookingId,guestId).guestAcknowledged()).isEqualByComparingTo("100000");
    }

    @Test
    void privateEvidenceIsStoredOutsidePublicUploadsAndRequiresBookingScope() {
        byte[] png={(byte)0x89,'P','N','G',13,10,26,10,1,2,3};
        var stored=privateEvidence.store(bookingId,hostId,PrivateEvidenceService.Purpose.DIRECT_RECEIPT,"receipt.png",png);
        assertThat(stored.sha256()).hasSize(64);
        privateEvidence.requireEvidence(bookingId,guestId,PrivateEvidenceService.Purpose.DIRECT_RECEIPT,stored.sha256());
        assertThat(privateEvidence.list(bookingId,guestId,PrivateEvidenceService.Purpose.DIRECT_RECEIPT))
                .extracting(PrivateEvidenceService.ObjectView::id).containsExactly(stored.id());
        assertThat(privateEvidence.download(bookingId,stored.id(),adminId).content()).isEqualTo(png);
        assertThatThrownBy(()->privateEvidence.requireEvidenceUploadedByActor(bookingId,guestId,
                PrivateEvidenceService.Purpose.DIRECT_RECEIPT,stored.sha256()))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_EVIDENCE_REQUIRED");
        assertThatThrownBy(()->privateEvidence.store(bookingId,guestId,PrivateEvidenceService.Purpose.PAYOUT,"payout.png",png))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_FORBIDDEN");
        assertThatThrownBy(()->privateEvidence.download(bookingId,stored.id(),999999))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_NOT_FOUND");
        assertThatThrownBy(()->privateEvidence.requireEvidence(bookingId,guestId,PrivateEvidenceService.Purpose.ARRIVAL,stored.sha256()))
                .isInstanceOf(PaymentPrimitiveException.class).extracting("errorCode").isEqualTo("ERR_EVIDENCE_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM private_evidence_access_log WHERE booking_id=? AND resource_type='EVIDENCE_OBJECT'",
                Integer.class,bookingId)).isEqualTo(3);
    }
}
