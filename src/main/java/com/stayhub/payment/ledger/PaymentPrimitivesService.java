package com.stayhub.payment.ledger;

import static com.stayhub.payment.ledger.LedgerService.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal, server-trusted commands, deliberately not connected to legacy
 * PaymentService, controllers or provider requests. Callers102/082 must compose
 * authenticated consent, original all-item hold and lifecycle in the SAME tx.
 * A VerifiedReceipt may only be constructed after independent provider verification;
 * this class does not verify signatures, authenticate quotes or confirm bookings.
 */
@Service
public class PaymentPrimitivesService {
    public enum Mode { FULL_PREPAY, HYBRID_DEPOSIT }
    public enum ReceiptIntent { REQUIRED_COLLECTION, RECONCILE_ONLY }
    public enum DirectMethod { CASH, BANK_TRANSFER }
    public enum ReserveKind { REFUND, PAYOUT }
    public enum MoneySource { ONLINE_VERIFIED, HOST_ATTESTED }
    public enum Movement { RELEASE, CONSUME }
    public enum CreationOutcome { CREATED, CREATION_FAILED }
    public record SnapshotInput(long bookingId,long actorId,Mode mode,String currency,
            BigDecimal roomCharges,BigDecimal cleaningFee,BigDecimal discount,long version,
            String policyVersion,String fixtureVersion,String quoteHash,Instant originalHoldExpiresAt) {}
    public record FinancialSnapshot(long bookingId,long guestId,long actorId,Mode mode,String currency,
            BigDecimal roomCharges,BigDecimal cleaningFee,BigDecimal subtotal,BigDecimal discount,
            BigDecimal totalStay,BigDecimal dueOnline,BigDecimal balanceDueHost,BigDecimal grossCommission,
            BigDecimal netRetention,BigDecimal hostEntitlement,BigDecimal heldHostPayable,long version,
            String policyVersion,String fixtureVersion,String quoteHash,Instant originalCreatedAt,Instant holdExpiresAt) {}
    public record Attempt(long id,long bookingId,int ordinal,String providerRef,String merchant,String configVersion,
            BigDecimal expectedAmount,String currency,Instant holdExpiresAt) {}
    public record VerifiedReceipt(long bookingId,long actorId,long attemptId,String receiptRef,BigDecimal amount,
            String currency,Instant occurredAt,String evidenceDigest,ReceiptIntent intent) {}
    public record CollectionResult(long id,String disposition) {}
    /** Bounds are cumulative reviewed source policy entitlement and CURRENT liquid
     * availability, supplied by future085/112 under the same booking lock. The
     * digest identifies that reviewed basis; it is not an authorization token.
     * Payout requires explicit eligible held entitlement, never direct B.
     */
    public record ReserveInput(long bookingId,long actorId,ReserveKind kind,MoneySource source,BigDecimal amount,
            String currency,BigDecimal policyBound,BigDecimal liquidityBound,String eligibilityDigest,long expectedVersion) {}
    private final JdbcTemplate jdbc;
    private final LedgerService ledger;
    private final Clock clock;
    @Autowired public PaymentPrimitivesService(JdbcTemplate jdbc,LedgerService ledger) {
        this(jdbc,ledger,Clock.systemUTC());
    }
    public PaymentPrimitivesService(JdbcTemplate jdbc,LedgerService ledger,Clock clock) {
        this.jdbc=jdbc; this.ledger=ledger; this.clock=Objects.requireNonNull(clock);
    }
    private static void currency(String currency) { require("VND".equals(currency),"ERR_INVALID_REQUEST"); }
    private static BigDecimal positive(BigDecimal amount) {
        var v=money(amount); require(v.signum()>0,"ERR_NONPOSITIVE_BOOKING_AMOUNT"); return v;
    }

    @Transactional
    public FinancialSnapshot createSnapshot(SnapshotInput input,String key) {
        Objects.requireNonNull(input); Objects.requireNonNull(input.mode()); currency(input.currency());
        var room=money(input.roomCharges()); var clean=money(input.cleaningFee()); var discount=money(input.discount());
        require(input.actorId()>0 && input.version()>=0,"ERR_INVALID_REQUEST");
        require("s0-policy-v1".equals(input.policyVersion()) && "s0-fixtures-v1".equals(input.fixtureVersion()),"ERR_PAYMENT_CONFIG");
        hashFormat(input.quoteHash());
        ledger.lock(input.bookingId());
        var booking=jdbc.queryForMap("""
                SELECT b.guest_id,b.created_at,p.host_id FROM bookings b JOIN properties p ON p.id=b.property_id WHERE b.id=?
                """,input.bookingId());
        long guest=((Number)booking.get("guest_id")).longValue();
        require(guest==input.actorId(),"ERR_FORBIDDEN");
        require(guest!=((Number)booking.get("host_id")).longValue(),"ERR_SELF_BOOKING");
        Instant created=((Timestamp)booking.get("created_at")).toInstant();
        require(created.plusSeconds(900).equals(input.originalHoldExpiresAt()),"ERR_PAYMENT_CONFIG");
        BigDecimal subtotal=money(room.add(clean));
        BigDecimal gross=subtotal.multiply(new BigDecimal("0.15")).setScale(0,RoundingMode.HALF_UP);
        require(discount.compareTo(gross)<=0,"ERR_DISCOUNT_FUNDING");
        BigDecimal total=positive(subtotal.subtract(discount));
        BigDecimal online=input.mode()==Mode.FULL_PREPAY ? total : total.multiply(new BigDecimal("0.20")).setScale(0,RoundingMode.HALF_UP);
        positive(online);
        BigDecimal net=gross.subtract(discount);
        require(online.compareTo(net)>=0,"ERR_DEPOSIT_COVERAGE");
        String hash=digest(List.of(input.bookingId(),input.actorId(),input.mode(),input.currency(),room,clean,discount,
                input.version(),input.policyVersion(),input.fixtureVersion(),input.quoteHash(),created,input.originalHoldExpiresAt()));
        Long replay=ledger.replay(input.actorId(),"SNAPSHOT",input.bookingId(),key,hash);
        if(replay!=null) return snapshot(input.bookingId());
        require(jdbc.queryForList("SELECT booking_id FROM booking_financial_snapshots WHERE booking_id=?",Long.class,input.bookingId()).isEmpty(),"ERR_IDEMPOTENCY_CONFLICT");
        jdbc.update("""
                INSERT INTO booking_financial_snapshots(booking_id,guest_id,actor_id,payment_mode,currency,room_charges,
                  cleaning_fee,subtotal,discount,total_stay,due_online,balance_due_host,gross_commission,net_retention,
                  host_entitlement,held_host_payable,version,policy_version,fixture_version,quote_hash,original_created_at,hold_expires_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,input.bookingId(),guest,input.actorId(),input.mode().name(),input.currency(),room,clean,subtotal,discount,total,
                online,total.subtract(online),gross,net,subtotal.subtract(gross),online.subtract(net),input.version(),
                input.policyVersion(),input.fixtureVersion(),input.quoteHash(),Timestamp.from(created),Timestamp.from(input.originalHoldExpiresAt()));
        ledger.remember(input.actorId(),"SNAPSHOT",input.bookingId(),key,hash,input.bookingId());
        return snapshot(input.bookingId());
    }
    @Transactional(readOnly=true)
    public FinancialSnapshot snapshot(long booking) {
        return jdbc.queryForObject("SELECT * FROM booking_financial_snapshots WHERE booking_id=?",(r,n)->new FinancialSnapshot(
                r.getLong("booking_id"),r.getLong("guest_id"),r.getLong("actor_id"),Mode.valueOf(r.getString("payment_mode")),r.getString("currency"),
                r.getBigDecimal("room_charges"),r.getBigDecimal("cleaning_fee"),r.getBigDecimal("subtotal"),r.getBigDecimal("discount"),
                r.getBigDecimal("total_stay"),r.getBigDecimal("due_online"),r.getBigDecimal("balance_due_host"),r.getBigDecimal("gross_commission"),
                r.getBigDecimal("net_retention"),r.getBigDecimal("host_entitlement"),r.getBigDecimal("held_host_payable"),r.getLong("version"),
                r.getString("policy_version"),r.getString("fixture_version"),r.getString("quote_hash"),r.getTimestamp("original_created_at").toInstant(),
                r.getTimestamp("hold_expires_at").toInstant()),booking);
    }

    @Transactional
    public Attempt allocateAttempt(long booking,long actor,String merchant,String configVersion,String key) {
        text(merchant); text(configVersion); ledger.lock(booking);
        var s=snapshot(booking); require(s.actorId()==actor,"ERR_FORBIDDEN");
        String hash=digest(List.of(booking,actor,merchant,configVersion));
        Long replay=ledger.replay(actor,"ATTEMPT",booking,key,hash);
        if(replay!=null) return attempt(replay);
        require(clock.instant().isBefore(s.holdExpiresAt()),"ERR_HOLD_EXPIRED");
        int count=jdbc.queryForObject("SELECT count(*) FROM payment_attempts WHERE booking_id=?",Integer.class,booking);
        require(count<3,"ERR_PAYMENT_ATTEMPTS_EXHAUSTED");
        long id=jdbc.queryForObject("""
                INSERT INTO payment_attempts(booking_id,actor_id,ordinal,provider_ref,merchant,config_version,expected_amount,currency,hold_expires_at)
                VALUES (?,?,?,?,?,?,?,?,?) RETURNING id
                """,Long.class,booking,actor,count+1,"SH110-"+UUID.randomUUID(),merchant,configVersion,s.dueOnline(),s.currency(),Timestamp.from(s.holdExpiresAt()));
        ledger.remember(actor,"ATTEMPT",booking,key,hash,id);
        return attempt(id);
    }
    private Attempt attempt(long id) {
        return jdbc.queryForObject("SELECT * FROM payment_attempts WHERE id=?",(r,n)->new Attempt(r.getLong("id"),r.getLong("booking_id"),
                r.getInt("ordinal"),r.getString("provider_ref"),r.getString("merchant"),r.getString("config_version"),
                r.getBigDecimal("expected_amount"),r.getString("currency"),r.getTimestamp("hold_expires_at").toInstant()),id);
    }
    @Transactional
    public void recordCreationOutcome(long booking,long actor,long attemptId,CreationOutcome outcome) {
        Objects.requireNonNull(outcome); ledger.lock(booking);
        var a=attempt(attemptId);
        require(a.bookingId()==booking && snapshot(booking).actorId()==actor,"ERR_FORBIDDEN");
        jdbc.update("INSERT INTO payment_attempt_outcomes(attempt_id,outcome) VALUES (?,?) ON CONFLICT DO NOTHING",attemptId,outcome.name());
        require(outcome.name().equals(jdbc.queryForObject("SELECT outcome FROM payment_attempt_outcomes WHERE attempt_id=?",String.class,attemptId)),"ERR_IDEMPOTENCY_CONFLICT");
    }

    @Transactional
    public CollectionResult recordVerifiedReceipt(VerifiedReceipt input,String key) {
        Objects.requireNonNull(input); Objects.requireNonNull(input.intent()); currency(input.currency());
        var amount=positive(input.amount()); text(input.receiptRef()); hashFormat(input.evidenceDigest());
        require(input.occurredAt()!=null && !input.occurredAt().isAfter(clock.instant()),"ERR_INVALID_REQUEST");
        ledger.lock(input.bookingId());
        var s=snapshot(input.bookingId()); var a=attempt(input.attemptId());
        require(a.bookingId()==input.bookingId(),"ERR_INVALID_REQUEST");
        String hash=digest(List.of(input.bookingId(),input.actorId(),input.attemptId(),input.receiptRef(),amount,input.currency(),
                input.occurredAt(),input.evidenceDigest(),input.intent()));
        Long replay=ledger.replay(input.actorId(),"COLLECTION",input.bookingId(),key,hash);
        if(replay!=null) return collection(replay);
        boolean accepted=input.intent()==ReceiptIntent.REQUIRED_COLLECTION && clock.instant().isBefore(s.holdExpiresAt())
                && amount.compareTo(a.expectedAmount())==0
                && jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='ACCEPTED'",Integer.class,input.bookingId())==0;
        String disposition=accepted ? "ACCEPTED" : "RECONCILE";
        long id=insertCollection(input.bookingId(),input.actorId(),input.attemptId(),"ONLINE_VERIFIED","PROVIDER",disposition,
                input.receiptRef(),hash,amount,input.occurredAt(),input.evidenceDigest());
        if(accepted) {
            var lines=new ArrayList<Line>();
            lines.add(Line.debit(Account.ONLINE_CASH,amount));
            if(s.discount().signum()>0) lines.add(Line.debit(Account.DISCOUNT_FUNDING_EXPENSE,s.discount()));
            if(s.grossCommission().signum()>0) lines.add(Line.credit(Account.COMMISSION_RESERVED,s.grossCommission()));
            if(s.heldHostPayable().signum()>0) lines.add(Line.credit(Account.HOST_HELD_PAYABLE,s.heldHostPayable()));
            ledger.post(input.bookingId(),input.actorId(),"collection:"+id,"collection:"+id,Source.ONLINE_VERIFIED,id,null,input.evidenceDigest(),lines);
        }
        ledger.remember(input.actorId(),"COLLECTION",input.bookingId(),key,hash,id);
        return collection(id);
    }

    /** Memo primitive only, not receipt acknowledgments/reversal/dispute workflow111. */
    @Transactional
    public CollectionResult recordHostMemo(long booking,long host,DirectMethod method,String reference,BigDecimal rawAmount,
                                            String currency,Instant occurred,String evidenceDigest,String key) {
        Objects.requireNonNull(method); currency(currency); var amount=positive(rawAmount); text(reference); hashFormat(evidenceDigest);
        require(occurred!=null && !occurred.isAfter(clock.instant()),"ERR_INVALID_REQUEST");
        ledger.lock(booking);
        require(host==jdbc.queryForObject("SELECT p.host_id FROM properties p JOIN bookings b ON b.property_id=p.id WHERE b.id=?",Long.class,booking),"ERR_FORBIDDEN");
        String hash=digest(List.of(booking,host,method,reference,amount,currency,occurred,evidenceDigest));
        Long replay=ledger.replay(host,"DIRECT_MEMO",booking,key,hash);
        if(replay!=null) return collection(replay);
        long id=insertCollection(booking,host,null,"HOST_ATTESTED",method.name(),"MEMO",reference,hash,amount,occurred,evidenceDigest);
        ledger.post(booking,host,"collection:"+id,"collection:"+id,Source.HOST_ATTESTED,id,null,evidenceDigest,
                List.of(Line.debit(Account.MEMO_HOST_DIRECT,amount),Line.credit(Account.MEMO_GUEST_DIRECT,amount)));
        ledger.remember(host,"DIRECT_MEMO",booking,key,hash,id);
        return collection(id);
    }
    private long insertCollection(long booking,long actor,Long attempt,String source,String method,String disposition,
            String ref,String hash,BigDecimal amount,Instant occurred,String evidence) {
        var inserted=jdbc.query("""
                INSERT INTO payment_collections(booking_id,actor_id,attempt_id,source,method,disposition,receipt_ref,body_hash,amount,currency,occurred_at,evidence_digest)
                VALUES (?,?,?,?,?,?,?,?,?,'VND',?,?) ON CONFLICT (receipt_ref) DO NOTHING RETURNING id
                """,(r,n)->r.getLong(1),booking,actor,attempt,source,method,disposition,ref,hash,amount,Timestamp.from(occurred),evidence);
        if(!inserted.isEmpty()) return inserted.getFirst();
        var existing=jdbc.queryForMap("SELECT id,body_hash FROM payment_collections WHERE receipt_ref=?",ref);
        require(hash.equals(existing.get("body_hash")),"ERR_IDEMPOTENCY_CONFLICT");
        return ((Number)existing.get("id")).longValue();
    }
    private CollectionResult collection(long id) {
        return jdbc.queryForObject("SELECT id,disposition FROM payment_collections WHERE id=?",(r,n)->new CollectionResult(r.getLong(1),r.getString(2)),id);
    }

    @Transactional(readOnly=true)
    public long settlementVersion(long booking) {
        return jdbc.queryForObject("SELECT version FROM payment_settlement_locks WHERE booking_id=?",Long.class,booking);
    }
    @Transactional
    public long reserve(ReserveInput input,String key) {
        Objects.requireNonNull(input); Objects.requireNonNull(input.kind()); Objects.requireNonNull(input.source());
        currency(input.currency()); var amount=positive(input.amount()); var policy=money(input.policyBound()); var liquid=money(input.liquidityBound());
        hashFormat(input.eligibilityDigest()); require(input.expectedVersion()>=0,"ERR_INVALID_REQUEST");
        require(input.kind()!=ReserveKind.PAYOUT || input.source()==MoneySource.ONLINE_VERIFIED,"ERR_PAYOUT_NOT_ELIGIBLE");
        ledger.lock(input.bookingId());
        String hash=digest(List.of(input.bookingId(),input.actorId(),input.kind(),input.source(),amount,input.currency(),policy,liquid,
                input.eligibilityDigest(),input.expectedVersion()));
        Long replay=ledger.replay(input.actorId(),"RESERVE",input.bookingId(),key,hash);
        if(replay!=null) return replay;
        require(settlementVersion(input.bookingId())==input.expectedVersion(),"ERR_VERSION_CONFLICT");
        require(amount.compareTo(policy)<=0 && amount.compareTo(liquid)<=0,"ERR_REFUND_POOL_EXCEEDED");
        var totals=jdbc.queryForMap("""
                SELECT coalesce(sum(r.amount),0) AS allocated,
                  coalesce(sum(r.amount) FILTER (WHERE r.kind='PAYOUT'),0) AS payout_allocated
                FROM ledger_reservations r LEFT JOIN ledger_reserve_movements m ON m.reservation_id=r.id
                WHERE r.booking_id=? AND r.source=? AND (m.action IS NULL OR m.action='CONSUME')
                """,input.bookingId(),input.source().name());
        BigDecimal allocated=(BigDecimal)totals.get("allocated");
        BigDecimal collected=jdbc.queryForObject("""
                SELECT coalesce(sum(amount),0) FROM payment_collections WHERE booking_id=? AND source=?
                  AND disposition IN ('ACCEPTED','MEMO')
                """,BigDecimal.class,input.bookingId(),input.source().name());
        require(amount.compareTo(collected.subtract(allocated))<=0 && amount.compareTo(policy.subtract(allocated))<=0,"ERR_REFUND_POOL_EXCEEDED");
        if(input.kind()==ReserveKind.PAYOUT) require(amount.compareTo(snapshot(input.bookingId()).heldHostPayable()
                .subtract((BigDecimal)totals.get("payout_allocated")))<=0,"ERR_PAYOUT_NOT_ELIGIBLE");
        long id=jdbc.queryForObject("""
                INSERT INTO ledger_reservations(booking_id,actor_id,kind,source,amount,currency,policy_bound,liquidity_bound,eligibility_digest)
                VALUES (?,?,?,?,?,?,?,?,?) RETURNING id
                """,Long.class,input.bookingId(),input.actorId(),input.kind().name(),input.source().name(),amount,input.currency(),policy,liquid,input.eligibilityDigest());
        ledger.remember(input.actorId(),"RESERVE",input.bookingId(),key,hash,id);
        return id;
    }
    /** Full reserve release or consumption accounting only. Trusted downstream
     * execution must supply evidence after its own maker/checker/proof gates;
     * CONSUME does not execute a refund, bank transfer, or change a public PAID state. */
    @Transactional
    public long moveReserve(long booking,long actor,long reservation,Movement action,String evidence,String key,long expectedVersion) {
        Objects.requireNonNull(action); hashFormat(evidence); ledger.lock(booking);
        require(!jdbc.queryForList("SELECT id FROM ledger_reservations WHERE id=? AND booking_id=?",Long.class,reservation,booking).isEmpty(),"ERR_INVALID_REQUEST");
        String hash=digest(List.of(booking,actor,reservation,action,evidence,expectedVersion));
        Long replay=ledger.replay(actor,"RESERVE_MOVEMENT",booking,key,hash);
        if(replay!=null) return replay;
        require(expectedVersion>=0 && settlementVersion(booking)==expectedVersion,"ERR_VERSION_CONFLICT");
        require(jdbc.queryForList("SELECT id FROM ledger_reserve_movements WHERE reservation_id=?",Long.class,reservation).isEmpty(),"ERR_IDEMPOTENCY_CONFLICT");
        long id=jdbc.queryForObject("INSERT INTO ledger_reserve_movements(reservation_id,actor_id,action,evidence_digest) VALUES (?,?,?,?) RETURNING id",
                Long.class,reservation,actor,action.name(),evidence);
        ledger.remember(actor,"RESERVE_MOVEMENT",booking,key,hash,id);
        return id;
    }
}
