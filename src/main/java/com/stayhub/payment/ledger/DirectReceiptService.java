package com.stayhub.payment.ledger;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Host-reported direct receipts are evidence only, never provider cash. */
@Service
public class DirectReceiptService {
    public enum Method { CASH, BANK_TRANSFER }
    public enum Disposition { ACKNOWLEDGE, CONTEST, REFUSE }
    public enum AdminDecision { ACCEPT_RECEIPT, REJECT_RECEIPT }
    public record Attestation(long bookingId, long hostId, String receiptRef, BigDecimal amount,
            String currency, Method method, Instant paidAt, String evidenceDigest,
            String idempotencyKey, long expectedVersion) {}
    public record CaseView(long id, long bookingId, BigDecimal amount, String currency,
            Method method, Instant paidAt, String state, String guestDisposition, long version) {}
    public record BalanceView(long bookingId,BigDecimal balanceDueHost,BigDecimal hostAttested,
            BigDecimal guestAcknowledged,BigDecimal disputed,BigDecimal outstanding,long version) {}
    public record AdminCaseView(long id,long bookingId,String receiptRef,BigDecimal amount,String method,
            Instant paidAt,String state,String guestDisposition,String evidenceDigest,long version) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PaymentPrimitivesService primitives;
    private final LedgerService ledger;

    @Autowired
    public DirectReceiptService(JdbcTemplate jdbc,PaymentPrimitivesService primitives,LedgerService ledger) {
        this(jdbc,Clock.systemUTC(),primitives,ledger);
    }

    DirectReceiptService(JdbcTemplate jdbc, Clock clock,PaymentPrimitivesService primitives,LedgerService ledger) {
        this.jdbc = jdbc;
        this.clock = Objects.requireNonNull(clock);
        this.primitives=Objects.requireNonNull(primitives);
        this.ledger=Objects.requireNonNull(ledger);
    }

    @Transactional
    public CaseView attest(Attestation input) {
        Objects.requireNonNull(input);
        require(input.bookingId() > 0 && input.hostId() > 0 && input.expectedVersion() >= 0,
                "ERR_INVALID_REQUEST");
        text(input.receiptRef());
        text(input.idempotencyKey());
        require(input.amount() != null && input.amount().signum() > 0
                && input.amount().stripTrailingZeros().scale() <= 0, "ERR_NONPOSITIVE_BOOKING_AMOUNT");
        require("VND".equals(input.currency()), "ERR_INVALID_REQUEST");
        hash(input.evidenceDigest());
        require(input.method() != null && input.paidAt() != null
                && !input.paidAt().isAfter(clock.instant()), "ERR_INVALID_REQUEST");

        lock(input.bookingId());
        var booking=jdbc.queryForMap("""
                SELECT p.host_id,b.created_at FROM bookings b JOIN properties p ON p.id=b.property_id WHERE b.id=?
                """,input.bookingId());
        require(((Number)booking.get("host_id")).longValue()==input.hostId(),"ERR_FORBIDDEN");
        require(!input.paidAt().isBefore(((Timestamp)booking.get("created_at")).toInstant()),"ERR_INVALID_REQUEST");
        String bodyHash = LedgerService.digest(List.of(input.bookingId(), input.hostId(), input.receiptRef(),
                input.amount(), input.currency(), input.method(), input.paidAt(), input.evidenceDigest(),
                input.expectedVersion()));
        var replay = jdbc.query("""
                SELECT c.id,c.booking_id,c.amount,c.currency,c.method,c.paid_at,c.state,c.guest_disposition,
                    psl.version AS booking_version,e.body_hash
                FROM direct_receipt_case_events e JOIN direct_receipt_cases c ON c.id=e.case_id
                JOIN payment_settlement_locks psl ON psl.booking_id=c.booking_id
                WHERE e.actor_id=? AND e.booking_id=? AND e.idempotency_key=?
                """, (r, n) -> Map.entry(r.getString("body_hash"), view(r)), input.hostId(),
                input.bookingId(), input.idempotencyKey());
        if (!replay.isEmpty()) {
            require(bodyHash.equals(replay.getFirst().getKey()), "ERR_IDEMPOTENCY_CONFLICT");
            return replay.getFirst().getValue();
        }
        var duplicateRef=jdbc.query("SELECT id,body_hash FROM direct_receipt_cases WHERE booking_id=? AND receipt_ref=?",
                (r,n)->Map.entry(r.getLong(1),r.getString(2)),input.bookingId(),input.receiptRef());
        if(!duplicateRef.isEmpty()) {
            require(bodyHash.equals(duplicateRef.getFirst().getValue()),"ERR_IDEMPOTENCY_CONFLICT");
            return get(duplicateRef.getFirst().getKey(),input.bookingId());
        }
        long snapshotVersion = jdbc.queryForObject(
                "SELECT version FROM payment_settlement_locks WHERE booking_id=?", Long.class, input.bookingId());
        require(snapshotVersion == input.expectedVersion(), "ERR_VERSION_CONFLICT");
        BigDecimal balance = jdbc.queryForObject(
                "SELECT balance_due_host FROM booking_financial_snapshots WHERE booking_id=?", BigDecimal.class,
                input.bookingId());
        BigDecimal recorded = jdbc.queryForObject("""
                SELECT coalesce((SELECT sum(amount) FROM direct_receipt_cases
                    WHERE booking_id=? AND state <> 'REVERSED'),0)
                  + coalesce((SELECT sum(pc.amount) FROM payment_collections pc
                    WHERE pc.booking_id=? AND pc.source='HOST_ATTESTED' AND pc.disposition='MEMO'
                      AND NOT EXISTS (SELECT 1 FROM direct_receipt_cases x WHERE x.booking_id=pc.booking_id AND x.receipt_ref=pc.receipt_ref)),0)
                """, BigDecimal.class, input.bookingId(), input.bookingId());
        BigDecimal outstanding = balance.subtract(recorded);
        require(input.amount().compareTo(outstanding) <= 0, "ERR_DIRECT_OVERPAYMENT");

        long id = jdbc.queryForObject("""
                INSERT INTO direct_receipt_cases(booking_id,host_id,receipt_ref,body_hash,amount,currency,method,
                    paid_at,evidence_digest,state)
                VALUES (?,?,?,?,?,?,?,?,?,'ATTESTED') RETURNING id
                """, Long.class, input.bookingId(), input.hostId(), input.receiptRef(), bodyHash, input.amount(),
                input.currency(), input.method().name(), Timestamp.from(input.paidAt()), input.evidenceDigest());
        advanceVersion(input.bookingId());
        appendEvent(id, input.bookingId(), input.hostId(), "ATTESTED", input.idempotencyKey(), bodyHash,
                input.evidenceDigest(), input.expectedVersion());
        return get(id, input.bookingId());
    }

    @Transactional
    public CaseView guestDisposition(long bookingId, long guestId, long caseId, Disposition disposition,
            String idempotencyKey, String evidenceDigest, long expectedVersion) {
        Objects.requireNonNull(disposition);
        String auditDigest=disposition==Disposition.ACKNOWLEDGE
                ? LedgerService.digest(List.of("GUEST_ACKNOWLEDGMENT",bookingId,guestId,caseId,expectedVersion))
                : evidenceDigest;
        if(disposition!=Disposition.ACKNOWLEDGE) hash(auditDigest);
        transitionActor(bookingId, guestId, caseId, idempotencyKey, auditDigest, expectedVersion,
                switch (disposition) {
                    case ACKNOWLEDGE -> "ACKNOWLEDGED";
                    case CONTEST -> "CONTESTED";
                    case REFUSE -> "REFUSED";
                },
                disposition.name(), "SELECT guest_id FROM bookings WHERE id=? AND guest_id=?");
        if(disposition==Disposition.ACKNOWLEDGE) appendDirectMemo(bookingId,caseId);
        return get(caseId, bookingId);
    }

    @Transactional
    public CaseView resolve(long bookingId, long adminId, long caseId, AdminDecision decision, String resolutionDigest,
            String idempotencyKey, long expectedVersion) {
        Objects.requireNonNull(decision);
        hash(resolutionDigest);
        transitionActor(bookingId, adminId, caseId, idempotencyKey, resolutionDigest, expectedVersion,
                "RESOLVED", decision.name(),
                "SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'");
        if(decision==AdminDecision.ACCEPT_RECEIPT) appendDirectMemo(bookingId,caseId);
        auditRead(bookingId,adminId,caseId,"RESOLVE");
        return get(caseId, bookingId);
    }

    @Transactional
    public CaseView reverse(long bookingId, long adminId, long caseId, String reversalDigest,
            String idempotencyKey, long expectedVersion) {
        hash(reversalDigest);
        lock(bookingId);
        require(jdbc.queryForObject("""
                SELECT count(*) FROM ledger_reservations r LEFT JOIN ledger_reserve_movements m ON m.reservation_id=r.id
                WHERE r.booking_id=? AND r.source='HOST_ATTESTED' AND (m.action IS NULL OR m.action='CONSUME')
                """,Integer.class,bookingId)==0,"ERR_DIRECT_RECEIPT_RESERVED");
        reverseDirectMemoIfPresent(bookingId,adminId,caseId,reversalDigest);
        transitionActor(bookingId, adminId, caseId, idempotencyKey, reversalDigest, expectedVersion,
                "REVERSED", reversalDigest,
                "SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'");
        auditRead(bookingId,adminId,caseId,"RESOLVE");
        return get(caseId, bookingId);
    }

    @Transactional
    public List<CaseView> listForGuest(long bookingId, long guestId) {
        require(jdbc.queryForList("SELECT id FROM bookings WHERE id=? AND guest_id=?", Long.class,
                bookingId, guestId).size() == 1, "ERR_NOT_FOUND");
        return list(bookingId,guestId);
    }

    @Transactional
    public List<CaseView> listForHost(long bookingId, long hostId) {
        require(jdbc.queryForList("""
                SELECT b.id FROM bookings b JOIN properties p ON p.id=b.property_id
                WHERE b.id=? AND p.host_id=?
                """, Long.class, bookingId, hostId).size() == 1, "ERR_NOT_FOUND");
        return list(bookingId,hostId);
    }

    @Transactional(readOnly=true)
    public BalanceView balanceForActor(long bookingId,long actor) {
        require(!jdbc.queryForList("""
                SELECT b.id FROM bookings b JOIN properties p ON p.id=b.property_id
                WHERE b.id=? AND (b.guest_id=? OR p.host_id=? OR EXISTS
                    (SELECT 1 FROM users u WHERE u.id=? AND u.role='ADMIN' AND u.status='ACTIVE'))
                """,Long.class,bookingId,actor,actor,actor).isEmpty(),"ERR_NOT_FOUND");
        var r=jdbc.queryForMap("""
                SELECT s.balance_due_host,
                  coalesce((SELECT sum(c.amount) FROM direct_receipt_cases c WHERE c.booking_id=s.booking_id
                    AND c.state<>'REVERSED'),0)+coalesce((SELECT sum(pc.amount) FROM payment_collections pc
                     WHERE pc.booking_id=s.booking_id AND pc.source='HOST_ATTESTED' AND pc.disposition='MEMO'
                       AND NOT EXISTS (SELECT 1 FROM direct_receipt_cases x WHERE x.booking_id=pc.booking_id AND x.receipt_ref=pc.receipt_ref)),0) AS attested,
                  coalesce((SELECT sum(c.amount) FROM direct_receipt_cases c WHERE c.booking_id=s.booking_id
                    AND c.state IN ('ACKNOWLEDGED','RESOLVED') AND c.guest_disposition='ACKNOWLEDGE'),0) AS acknowledged,
                  coalesce((SELECT sum(c.amount) FROM direct_receipt_cases c WHERE c.booking_id=s.booking_id
                    AND (c.state IN ('CONTESTED','REFUSED') OR (c.state='RESOLVED' AND c.guest_disposition='CONTEST'))),0) AS disputed,
                  l.version FROM booking_financial_snapshots s JOIN payment_settlement_locks l ON l.booking_id=s.booking_id
                WHERE s.booking_id=?
                """,bookingId);
        BigDecimal due=(BigDecimal)r.get("balance_due_host"),ack=(BigDecimal)r.get("acknowledged");
        return new BalanceView(bookingId,due,(BigDecimal)r.get("attested"),ack,(BigDecimal)r.get("disputed"),
                due.subtract(ack).max(BigDecimal.ZERO),((Number)r.get("version")).longValue());
    }

    @Transactional
    public List<AdminCaseView> listForAdmin(long bookingId,long adminId) {
        require(!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,adminId).isEmpty(),"ERR_FORBIDDEN");
        var rows=jdbc.query("""
                SELECT c.*,l.version AS booking_version FROM direct_receipt_cases c
                JOIN payment_settlement_locks l ON l.booking_id=c.booking_id WHERE c.booking_id=? ORDER BY c.id
                """,(r,n)->new AdminCaseView(r.getLong("id"),r.getLong("booking_id"),r.getString("receipt_ref"),
                    r.getBigDecimal("amount"),r.getString("method"),r.getTimestamp("paid_at").toInstant(),
                    r.getString("state"),r.getString("guest_disposition"),r.getString("evidence_digest"),r.getLong("booking_version")),bookingId);
        rows.forEach(c->auditRead(bookingId,adminId,c.id(),"REVIEW"));
        return rows;
    }

    private void transitionActor(long booking, long actor, long caseId, String key, String evidence,
            long expectedVersion, String event, String disposition, String identitySql) {
        require(booking > 0 && actor > 0 && expectedVersion >= 0, "ERR_INVALID_REQUEST");
        text(key); hash(evidence); lock(booking);
        boolean authorized = event.equals("RESOLVED") || event.equals("REVERSED")
                ? !jdbc.queryForList(identitySql, Long.class, actor).isEmpty()
                : !jdbc.queryForList(identitySql, Long.class, booking, actor).isEmpty();
        require(authorized, "ERR_FORBIDDEN");
        String body = LedgerService.digest(List.of(booking, actor, caseId, event, disposition, evidence,
                expectedVersion));
        var replay = jdbc.query("""
                SELECT e.body_hash FROM direct_receipt_case_events e
                WHERE e.actor_id=? AND e.booking_id=? AND e.idempotency_key=?
                """, (r, n) -> r.getString(1), actor, booking, key);
        if (!replay.isEmpty()) {
            require(body.equals(replay.getFirst()), "ERR_IDEMPOTENCY_CONFLICT");
            return;
        }
        var rows = jdbc.query("SELECT state,version FROM direct_receipt_cases WHERE id=? AND booking_id=? FOR UPDATE",
                (r, n) -> Map.entry(r.getString(1), r.getLong(2)), caseId, booking);
        require(!rows.isEmpty(), "ERR_NOT_FOUND");
        var current = rows.getFirst();
        long version = jdbc.queryForObject(
                "SELECT version FROM payment_settlement_locks WHERE booking_id=?", Long.class, booking);
        require(version == expectedVersion, "ERR_VERSION_CONFLICT");
        String nextState = event.equals("RESOLVED") ? "RESOLVED" : event;
        require(!current.getKey().equals("REVERSED") && !current.getKey().equals("RESOLVED")
                && (event.equals("ACKNOWLEDGED") || event.equals("CONTESTED") || event.equals("REFUSED")
                    ? current.getKey().equals("ATTESTED")
                    : event.equals("RESOLVED") ? current.getKey().equals("CONTESTED")
                    || current.getKey().equals("REFUSED") : event.equals("REVERSED")),
                "ERR_INVALID_STATE");
        jdbc.update("UPDATE direct_receipt_cases SET state=?,guest_disposition=?,resolution_digest=?,version=version+1 WHERE id=?",
                nextState, event.equals("ACKNOWLEDGED") || event.equals("CONTESTED") || event.equals("REFUSED")
                        ? disposition : event.equals("RESOLVED")
                            ? (disposition.equals(AdminDecision.ACCEPT_RECEIPT.name()) ? "ACKNOWLEDGE" : "CONTEST")
                            : "CONTEST",
                event.equals("RESOLVED") || event.equals("REVERSED") ? evidence : null, caseId);
        advanceVersion(booking);
        appendEvent(caseId, booking, actor, event, key, body, evidence, expectedVersion);
    }

    private void appendEvent(long caseId, long booking, long actor, String type, String key,
            String bodyHash, String evidence, long version) {
        jdbc.update("""
                INSERT INTO direct_receipt_case_events(case_id,booking_id,actor_id,event_type,idempotency_key,
                    body_hash,evidence_digest,expected_version) VALUES (?,?,?,?,?,?,?,?)
                """, caseId, booking, actor, type, key, bodyHash, evidence, version);
    }

    private List<CaseView> list(long booking,long actor) {
        var cases=jdbc.query("SELECT c.*,psl.version AS booking_version FROM direct_receipt_cases c JOIN payment_settlement_locks psl ON psl.booking_id=c.booking_id WHERE c.booking_id=? ORDER BY c.id",
                (r, n) -> view(r), booking);
        cases.forEach(c->auditRead(booking,actor,c.id(),"READ"));
        return cases;
    }
    private void auditRead(long booking,long actor,long caseId,String reason) {
        jdbc.update("""
                INSERT INTO private_evidence_access_log(booking_id,actor_id,resource_type,resource_id,access_reason)
                VALUES (?,?,'DIRECT_RECEIPT',?,?)
                """,booking,actor,caseId,reason);
    }
    private void appendDirectMemo(long booking,long caseId) {
        var receipt=jdbc.queryForMap("SELECT * FROM direct_receipt_cases WHERE id=? AND booking_id=?",caseId,booking);
        require(jdbc.queryForObject("""
                SELECT count(*) FROM direct_receipt_cases earlier
                WHERE earlier.booking_id=? AND earlier.state<>'REVERSED'
                  AND (earlier.paid_at<(SELECT paid_at FROM direct_receipt_cases WHERE id=?)
                    OR (earlier.paid_at=(SELECT paid_at FROM direct_receipt_cases WHERE id=?) AND earlier.id<?))
                  AND NOT EXISTS (SELECT 1 FROM payment_collections pc WHERE pc.booking_id=earlier.booking_id
                    AND pc.receipt_ref=earlier.receipt_ref AND pc.source='HOST_ATTESTED' AND pc.disposition='MEMO')
                """,Integer.class,booking,caseId,caseId,caseId)==0,"ERR_DIRECT_RECEIPT_ORDER");
        primitives.recordHostMemo(booking,((Number)receipt.get("host_id")).longValue(),
                PaymentPrimitivesService.DirectMethod.valueOf((String)receipt.get("method")),
                (String)receipt.get("receipt_ref"),(BigDecimal)receipt.get("amount"),
                (String)receipt.get("currency"),((Timestamp)receipt.get("paid_at")).toInstant(),
                (String)receipt.get("evidence_digest"),"direct-case:"+caseId);
    }
    private void reverseDirectMemoIfPresent(long booking,long admin,long caseId,String evidence) {
        var journals=jdbc.query("""
                SELECT j.id FROM direct_receipt_cases c JOIN payment_collections pc
                  ON pc.booking_id=c.booking_id AND pc.receipt_ref=c.receipt_ref
                JOIN ledger_journals j ON j.collection_id=pc.id
                WHERE c.id=? AND c.booking_id=?
                """,(r,n)->r.getLong(1),caseId,booking);
        if(!journals.isEmpty()) ledger.reverse(booking,admin,journals.getFirst(),
                "direct-receipt-reversal:"+caseId,"direct-receipt-reversal:"+caseId,evidence);
    }
    private CaseView get(long id, long booking) {
        return jdbc.queryForObject("SELECT c.*,psl.version AS booking_version FROM direct_receipt_cases c JOIN payment_settlement_locks psl ON psl.booking_id=c.booking_id WHERE c.id=? AND c.booking_id=?",
                (r, n) -> view(r), id, booking);
    }
    private static CaseView view(java.sql.ResultSet r) throws java.sql.SQLException {
        return new CaseView(r.getLong("id"), r.getLong("booking_id"), r.getBigDecimal("amount"),
                r.getString("currency"), Method.valueOf(r.getString("method")), r.getTimestamp("paid_at").toInstant(),
                r.getString("state"), r.getString("guest_disposition"), r.getLong("booking_version"));
    }
    private void advanceVersion(long booking) {
        jdbc.update("UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=?", booking);
    }
    private void lock(long booking) {
        require(!jdbc.queryForList("SELECT id FROM bookings WHERE id=? FOR UPDATE", Long.class, booking).isEmpty(),
                "ERR_NOT_FOUND");
    }
    private static void require(boolean valid, String code) {
        if (!valid) throw new PaymentPrimitiveException(code);
    }
    private static void text(String value) {
        require(value != null && !value.isBlank() && value.length() <= 200, "ERR_INVALID_REQUEST");
    }
    private static void hash(String value) { LedgerService.hashFormat(value); }
}
