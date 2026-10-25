package com.stayhub.payment.ledger;

import static com.stayhub.payment.ledger.LedgerService.Account.ONLINE_CASH;
import static com.stayhub.payment.ledger.LedgerService.Account.REFUND_RESERVED;
import static com.stayhub.payment.ledger.LedgerService.Line.credit;
import static com.stayhub.payment.ledger.LedgerService.Line.debit;

import com.stayhub.payment.ledger.PaymentPrimitivesService.MoneySource;
import com.stayhub.payment.ledger.PaymentPrimitivesService.Movement;
import com.stayhub.payment.ledger.PaymentPrimitivesService.ReserveInput;
import com.stayhub.payment.ledger.PaymentPrimitivesService.ReserveKind;
import com.stayhub.inventory.InventoryCapacityService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manual, source-specific full-refund workflow. It never calls a bank or gateway. */
@Service
public class RefundWorkflowService {
    public enum Source { ONLINE_VERIFIED, HOST_ATTESTED }
    public enum PolicyBasis { FULL_COLLECTED_FREE_WINDOW, FULL_COLLECTED_HOST_UNABLE, LATE_UNUSED_CLEANING }
    public enum State { REQUESTED, APPROVED, PROCESSING, PAID, FAILED }
    public record RefundView(long id,long bookingId,Source source,BigDecimal amount,String currency,
            State state,long makerId,Long checkerId,String transferRef,String proofDigest,long version,
            long slaConfigId,int minimumBusinessDays,int maximumBusinessDays,String timezone,String calendarVersion) {}

    private static final ZoneId HCM=ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final PaymentPrimitivesService primitives;
    private final LedgerService ledger;
    private final InventoryCapacityService inventory;
    private final Clock clock;

    @Autowired
    public RefundWorkflowService(JdbcTemplate jdbc,PaymentPrimitivesService primitives,LedgerService ledger,
            InventoryCapacityService inventory) {
        this(jdbc,primitives,ledger,inventory,Clock.systemUTC());
    }
    RefundWorkflowService(JdbcTemplate jdbc,PaymentPrimitivesService primitives,LedgerService ledger,Clock clock) {
        this(jdbc,primitives,ledger,null,clock);
    }
    private RefundWorkflowService(JdbcTemplate jdbc,PaymentPrimitivesService primitives,LedgerService ledger,
            InventoryCapacityService inventory,Clock clock) {
        this.jdbc=jdbc;this.primitives=primitives;this.ledger=ledger;this.inventory=inventory;this.clock=Objects.requireNonNull(clock);
    }

    /** Admin accepts a Host-originated inability case, cancels the whole confirmed booking and frees every room atomically. */
    @Transactional
    public long recordHostUnable(long booking,long admin,String evidenceDigest,String key,long expectedVersion) {
        admin(admin);LedgerService.hashFormat(evidenceDigest);text(key);require(key.length()<=180&&expectedVersion>=0,"ERR_INVALID_REQUEST");lock(booking);
        var bookingRows=jdbc.query("SELECT status FROM bookings WHERE id=?",(r,n)->r.getString(1),booking);
        require(!bookingRows.isEmpty(),"ERR_NOT_FOUND");
        require(bookingRows.getFirst().equals("CONFIRMED"),"ERR_INVALID_STATE");
        require(jdbc.queryForObject("SELECT count(*) FROM booking_arrivals WHERE booking_id=?",Integer.class,booking)==0,
                "ERR_HOST_UNABLE_AFTER_ARRIVAL");
        String body=LedgerService.digest(List.of("HOST_UNABLE",booking,admin,evidenceDigest,expectedVersion));
        var replay=jdbc.query("SELECT body_hash,block_id FROM booking_settlement_block_events WHERE actor_id=? AND booking_id=? AND idempotency_key=?",
                (r,n)->Map.entry(r.getString(1),r.getLong(2)),admin,booking,key);
        if(!replay.isEmpty()) {
            require(replay.getFirst().getKey().equals(body),"ERR_IDEMPOTENCY_CONFLICT");
            return replay.getFirst().getValue();
        }
        require(primitives.settlementVersion(booking)==expectedVersion,"ERR_VERSION_CONFLICT");
        long block=jdbc.queryForObject("""
                INSERT INTO booking_settlement_blocks(booking_id,reason_code,evidence_digest,actor_id)
                VALUES (?,'OPERATIONS',?,?) RETURNING id
                """,Long.class,booking,evidenceDigest,admin);
        jdbc.update("""
                INSERT INTO booking_settlement_block_events(block_id,booking_id,actor_id,event_type,evidence_digest,
                    idempotency_key,body_hash,expected_version) VALUES (?,?,?,'OPENED',?,?,?,?)
                """,block,booking,admin,evidenceDigest,key,body,expectedVersion);
        if(inventory!=null&&jdbc.queryForObject("SELECT count(*) FROM booking_items WHERE booking_id=?",Integer.class,booking)>0)
            inventory.releaseAll(booking);
        int changed=jdbc.update("UPDATE bookings SET status='CANCELLED',cancelled_at=clock_timestamp(),inventory_version=inventory_version+1 WHERE id=? AND status='CONFIRMED'",booking);
        require(changed==1,"ERR_VERSION_CONFLICT");
        String resolutionKey=key+":resolved";
        String resolutionBody=LedgerService.digest(List.of("HOST_UNABLE_RESOLVED",booking,block,admin,evidenceDigest,expectedVersion));
        jdbc.update("UPDATE booking_settlement_blocks SET active=false,resolved_at=clock_timestamp(),resolution_digest=?,resolution_actor_id=? WHERE id=?",
                evidenceDigest,admin,block);
        jdbc.update("""
                INSERT INTO booking_settlement_block_events(block_id,booking_id,actor_id,event_type,evidence_digest,
                    idempotency_key,body_hash,expected_version) VALUES (?,?,?,'RESOLVED',?,?,?,?)
                """,block,booking,admin,evidenceDigest,resolutionKey,resolutionBody,expectedVersion);
        jdbc.update("UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=?",booking);
        return block;
    }

    /** Only the approved >=48h full-collected branch is automated here. Other
     * cancellation allocations fail closed until their source/charge snapshots exist. */
    @Transactional
    public RefundView requestFreeWindowRefund(long booking,long maker,Source source,String key) {
        return requestPolicyRefund(booking,maker,source,PolicyBasis.FULL_COLLECTED_FREE_WINDOW,key,
                LedgerService.digest(List.of("legacy-internal-free-window-request",booking,maker,key)));
    }

    @Transactional
    public RefundView requestPolicyRefund(long booking,long maker,Source source,PolicyBasis basis,String key,String evidenceDigest) {
        Objects.requireNonNull(source);Objects.requireNonNull(basis);admin(maker);text(key);LedgerService.hashFormat(evidenceDigest);lock(booking);
        String sourceName=source.name();
        var bodyParts=jdbc.queryForList("""
                SELECT b.status,b.cancelled_at,b.check_in_date,b.check_out_date,s.currency
                FROM bookings b JOIN booking_financial_snapshots s ON s.booking_id=b.id WHERE b.id=?
                """,booking);
        require(!bodyParts.isEmpty(),"ERR_NOT_FOUND");
        var bookingRow=bodyParts.getFirst();
        require("CANCELLED".equals(bookingRow.get("status")) && bookingRow.get("cancelled_at")!=null,"ERR_REFUND_POLICY_INELIGIBLE");
        Instant cancelled=((Timestamp)bookingRow.get("cancelled_at")).toInstant();
        var checkIn=((java.sql.Date)bookingRow.get("check_in_date")).toLocalDate();
        Instant scheduled=LocalDateTime.of(checkIn,java.time.LocalTime.of(14,0)).atZone(HCM).toInstant();
        if(basis==PolicyBasis.FULL_COLLECTED_FREE_WINDOW)
            require(!cancelled.isAfter(scheduled.minusSeconds(48*3600L)),"ERR_REFUND_POLICY_INELIGIBLE");
        if(basis==PolicyBasis.FULL_COLLECTED_HOST_UNABLE)
            require(!cancelled.isAfter(scheduled),"ERR_REFUND_POLICY_INELIGIBLE");
        if(basis==PolicyBasis.LATE_UNUSED_CLEANING) {
            Instant checkout=LocalDateTime.of(((java.sql.Date)bookingRow.get("check_out_date")).toLocalDate(),
                    java.time.LocalTime.of(11,0)).atZone(HCM).toInstant();
            boolean lateCancellation=cancelled.isAfter(scheduled.minusSeconds(48*3600L));
            boolean noShow=clock.instant().compareTo(checkout)>=0;
            require(lateCancellation||noShow,"ERR_REFUND_POLICY_INELIGIBLE");
            require(jdbc.queryForObject("SELECT count(*) FROM booking_arrivals WHERE booking_id=?",Integer.class,booking)==0,
                    "ERR_REFUND_POLICY_CASE_REQUIRED");
        }
        require("VND".equals(bookingRow.get("currency")),"ERR_INVALID_REQUEST");
        require(jdbc.queryForList("""
                SELECT booking_id FROM booking_release_clearance WHERE booking_id=?
                    AND finance_legal AND operations AND liquidity
                """,Long.class,booking).size()==1,"ERR_RELEASE_CLEARANCE_BLOCKED");
        require(jdbc.queryForObject("SELECT count(*) FROM booking_settlement_blocks WHERE booking_id=? AND active",Integer.class,booking)==0,
                "ERR_SETTLEMENT_BLOCKED");
        require(jdbc.queryForObject("SELECT count(*) FROM direct_receipt_cases WHERE booking_id=? AND state IN ('ATTESTED','CONTESTED','REFUSED')",
                Integer.class,booking)==0,"ERR_SETTLEMENT_BLOCKED");
        require(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='RECONCILE'",Integer.class,booking)==0,
                "ERR_SETTLEMENT_BLOCKED");
        require(jdbc.queryForObject("SELECT count(*) FROM payout_recovery_cases WHERE booking_id=? AND state='OPEN'",Integer.class,booking)==0,
                "ERR_SETTLEMENT_BLOCKED");
        var sla=jdbc.query("SELECT id FROM refund_sla_config WHERE active",(r,n)->r.getLong(1));
        require(!sla.isEmpty(),"ERR_REFUND_SLA_UNCONFIGURED");

        BigDecimal collected= basis==PolicyBasis.LATE_UNUSED_CLEANING
                ? refundableCleaning(booking,source) : collected(booking,source);
        require(collected.signum()>0,"ERR_REFUND_POOL_EXCEEDED");
        long liquiditySnapshot=0;
        BigDecimal liquidity=collected;
        if(source==Source.ONLINE_VERIFIED) {
            var snapshots=jdbc.query("""
                    SELECT id,available_online FROM refund_liquidity_snapshots
                    WHERE (as_of AT TIME ZONE 'Asia/Ho_Chi_Minh')::date=(clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                    ORDER BY as_of DESC,id DESC LIMIT 1 FOR UPDATE
                    """,(r,n)->Map.entry(r.getLong("id"),r.getBigDecimal("available_online")));
            require(!snapshots.isEmpty(),"ERR_LIQUIDITY_UNAVAILABLE");
            liquiditySnapshot=snapshots.getFirst().getKey();
            BigDecimal allocated=jdbc.queryForObject("""
                    SELECT coalesce(sum(amount),0) FROM refund_liquidity_allocations
                    WHERE snapshot_id=? AND state IN ('RESERVED','CONSUMED')
                    """,BigDecimal.class,liquiditySnapshot);
            liquidity=snapshots.getFirst().getValue().subtract(allocated);
            require(liquidity.signum()>=0,"ERR_LIQUIDITY_UNAVAILABLE");
        }
        String digest=LedgerService.digest(List.of("S0-084-v1",basis,booking,source,collected,cancelled,scheduled,evidenceDigest));
        var prior=jdbc.query("SELECT id,body_hash FROM refund_cases WHERE booking_id=? AND idempotency_key=?",
                (r,n)->Map.entry(r.getLong(1),r.getString(2)),booking,key);
        if(!prior.isEmpty()) {
            String expected=LedgerService.digest(List.of(booking,source,basis,collected,digest,maker));
            require(expected.equals(prior.getFirst().getValue()),"ERR_IDEMPOTENCY_CONFLICT");
            return get(prior.getFirst().getKey());
        }
        long version=primitives.settlementVersion(booking);
        long reservation=primitives.reserve(new ReserveInput(booking,maker,ReserveKind.REFUND,
                MoneySource.valueOf(sourceName),collected,"VND",collected,liquidity,digest,version),"refund:"+key);
        if(source==Source.ONLINE_VERIFIED) jdbc.update("""
                INSERT INTO refund_liquidity_allocations(reservation_id,snapshot_id,amount,state)
                VALUES (?,?,?,'RESERVED')
                """,reservation,liquiditySnapshot,collected);
        String caseHash=LedgerService.digest(List.of(booking,source,basis,collected,digest,maker));
        BigDecimal commissionAdjustment=commissionAdjustment(booking,source,basis);
        long id=jdbc.queryForObject("""
                INSERT INTO refund_cases(booking_id,source,amount,currency,policy_digest,reservation_id,sla_config_id,state,
                    maker_id,idempotency_key,body_hash,policy_basis,commission_adjustment)
                VALUES (?,?,?,'VND',?,?,?,'REQUESTED',?,?,?,?,?) RETURNING id
                """,Long.class,booking,source.name(),collected,digest,reservation,sla.getFirst(),maker,key,caseHash,basis.name(),commissionAdjustment);
        int allocations=jdbc.update("""
                INSERT INTO refund_charge_allocations(refund_case_id,booking_id,source,line_key,charge_type,
                    gross_amount,discount_amount,net_amount)
                SELECT ?,booking_id,source,line_key,charge_type,sum(gross_amount),sum(discount_amount),sum(net_amount)
                FROM payment_collection_allocations
                WHERE booking_id=? AND source=? AND gross_amount IS NOT NULL
                  AND (?='FULL_COLLECTED_FREE_WINDOW' OR ?='FULL_COLLECTED_HOST_UNABLE' OR charge_type='CLEANING')
                GROUP BY booking_id,source,line_key,charge_type
                HAVING sum(net_amount)>0
                """,id,booking,source.name(),basis.name(),basis.name());
        require(allocations>0,"ERR_REFUND_POLICY_ALLOCATION_MISSING");
        event(id,maker,"REQUESTED",key,caseHash,digest,0);
        return get(id);
    }

    private BigDecimal refundableCleaning(long booking,Source source) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(net_amount),0) FROM payment_collection_allocations
                WHERE booking_id=? AND source=? AND charge_type='CLEANING' AND gross_amount IS NOT NULL
                """,BigDecimal.class,booking,source.name());
    }
    private BigDecimal commissionAdjustment(long booking,Source source,PolicyBasis basis) {
        String chargeFilter=basis==PolicyBasis.LATE_UNUSED_CLEANING?" AND charge_type='CLEANING'":"";
        BigDecimal basisAmount=jdbc.queryForObject("SELECT coalesce(sum(gross_amount),0) FROM payment_collection_allocations " +
                "WHERE booking_id=? AND source=? AND gross_amount IS NOT NULL AND charge_type<>'TAX'"+chargeFilter,
                BigDecimal.class,booking,source.name());
        return basisAmount.multiply(new BigDecimal("0.15")).setScale(0,RoundingMode.HALF_UP);
    }

    @Transactional
    public RefundView approve(long refundId,long checker,String key,String evidence,long expectedVersion) {
        return transition(refundId,checker,key,evidence,expectedVersion,"APPROVED",true,null,null);
    }
    @Transactional
    public RefundView processing(long refundId,long actor,String key,String evidence,long expectedVersion) {
        return transition(refundId,actor,key,evidence,expectedVersion,"PROCESSING",false,null,null);
    }
    @Transactional
    public RefundView paid(long refundId,long checker,String key,String transferRef,String proofDigest,long expectedVersion) {
        text(transferRef); LedgerService.hashFormat(proofDigest);
        var current=get(refundId);
        var state=transition(refundId,checker,key,proofDigest,expectedVersion,"PAID",false,transferRef,proofDigest);
        long booking=current.bookingId();
        primitives.moveReserve(booking,checker,currentReservation(refundId),Movement.CONSUME,proofDigest,
                "refund-paid:"+key,primitives.settlementVersion(booking));
        if(current.source()==Source.ONLINE_VERIFIED) jdbc.update("""
                UPDATE refund_liquidity_allocations SET state='CONSUMED',updated_at=clock_timestamp()
                WHERE reservation_id=? AND state='RESERVED'
                """,currentReservation(refundId));
        if(current.source()==Source.ONLINE_VERIFIED) {
            ledger.postAdjustment(booking,checker,"refund-paid:"+refundId,"refund-journal:"+key,proofDigest,
                    List.of(debit(REFUND_RESERVED,current.amount()),credit(ONLINE_CASH,current.amount())));
        }
        return state;
    }
    @Transactional
    public RefundView fail(long refundId,long actor,String key,String evidence,long expectedVersion) {
        LedgerService.hashFormat(evidence);
        var current=get(refundId);
        var failed=transition(refundId,actor,key,evidence,expectedVersion,"FAILED",false,null,null);
        primitives.moveReserve(current.bookingId(),actor,currentReservation(refundId),Movement.RELEASE,evidence,
                "refund-failed:"+key,primitives.settlementVersion(current.bookingId()));
        if(current.source()==Source.ONLINE_VERIFIED) jdbc.update("""
                UPDATE refund_liquidity_allocations SET state='RELEASED',updated_at=clock_timestamp()
                WHERE reservation_id=? AND state='RESERVED'
                """,currentReservation(refundId));
        return failed;
    }

    private RefundView transition(long id,long actor,String key,String evidence,long expectedVersion,String next,
            boolean checkerApproval,String transferRef,String proof) {
        admin(actor);text(key);LedgerService.hashFormat(evidence);
        var current=get(id);lock(current.bookingId());
        String body=LedgerService.digest(java.util.Arrays.asList(id,actor,key,evidence,expectedVersion,next,transferRef,proof));
        var replay=jdbc.query("SELECT body_hash FROM refund_case_events WHERE actor_id=? AND refund_case_id=? AND idempotency_key=?",
                (r,n)->r.getString(1),actor,id,key);
        if(!replay.isEmpty()) { require(body.equals(replay.getFirst()),"ERR_IDEMPOTENCY_CONFLICT");return get(id); }
        var row=jdbc.queryForMap("SELECT state,version,maker_id,checker_id FROM refund_cases WHERE id=? FOR UPDATE",id);
        require(((Number)row.get("version")).longValue()==expectedVersion,"ERR_VERSION_CONFLICT");
        String old=(String)row.get("state");
        require((next.equals("APPROVED")&&old.equals("REQUESTED"))
                ||(next.equals("PROCESSING")&&old.equals("APPROVED"))
                ||(next.equals("PAID")&&old.equals("PROCESSING"))
                ||(next.equals("FAILED")&&(old.equals("APPROVED")||old.equals("PROCESSING"))),"ERR_INVALID_STATE");
        long maker=((Number)row.get("maker_id")).longValue();
        Long currentChecker=(Long)row.get("checker_id");
        if(checkerApproval) require(actor!=maker,"ERR_MAKER_CHECKER_CONFLICT");
        else if(currentChecker!=null) require(actor==currentChecker,"ERR_FORBIDDEN");
        if(next.equals("PAID")) require(transferRef!=null&&proof!=null&&currentChecker!=null&&actor==currentChecker,
                "ERR_REFUND_PROOF_REQUIRED");
        jdbc.update("""
                UPDATE refund_cases SET state=?,checker_id=coalesce(checker_id,?),transfer_ref=coalesce(?,transfer_ref),
                    proof_digest=coalesce(?,proof_digest),version=version+1,updated_at=clock_timestamp() WHERE id=?
                """,next,checkerApproval?actor:null,transferRef,proof,id);
        event(id,actor,next,key,body,evidence,expectedVersion);
        return get(id);
    }
    private BigDecimal collected(long booking,Source source) {
        if(source==Source.ONLINE_VERIFIED) return jdbc.queryForObject("""
                SELECT coalesce(sum(amount),0) FROM payment_collections
                WHERE booking_id=? AND source='ONLINE_VERIFIED' AND disposition='ACCEPTED'
                """,BigDecimal.class,booking);
        return jdbc.queryForObject("""
                SELECT coalesce((SELECT sum(pc.amount) FROM payment_collections pc WHERE pc.booking_id=?
                    AND pc.source='HOST_ATTESTED' AND pc.disposition='MEMO'
                    AND NOT EXISTS (SELECT 1 FROM direct_receipt_cases x WHERE x.booking_id=pc.booking_id AND x.receipt_ref=pc.receipt_ref)),0)
                  + coalesce((SELECT sum(amount) FROM direct_receipt_cases WHERE booking_id=?
                    AND state IN ('ACKNOWLEDGED','RESOLVED') AND guest_disposition='ACKNOWLEDGE'),0)
                """,BigDecimal.class,booking,booking);
    }
    private long currentReservation(long id) {
        return jdbc.queryForObject("SELECT reservation_id FROM refund_cases WHERE id=?",Long.class,id);
    }
    private void event(long id,long actor,String type,String key,String body,String evidence,long version) {
        jdbc.update("""
                INSERT INTO refund_case_events(refund_case_id,actor_id,event_type,idempotency_key,body_hash,evidence_digest,expected_version)
                VALUES (?,?,?,?,?,?,?)
                """,id,actor,type,key,body,evidence,version);
    }
    @Transactional(readOnly=true)
    public RefundView get(long id) {
        return jdbc.queryForObject("""
                SELECT c.*,s.minimum_business_days,s.maximum_business_days,s.timezone,s.calendar_version
                FROM refund_cases c JOIN refund_sla_config s ON s.id=c.sla_config_id WHERE c.id=?
                """,(r,n)->new RefundView(r.getLong("id"),
                r.getLong("booking_id"),Source.valueOf(r.getString("source")),r.getBigDecimal("amount"),r.getString("currency"),
                State.valueOf(r.getString("state")),r.getLong("maker_id"),r.getObject("checker_id",Long.class),
                r.getString("transfer_ref"),r.getString("proof_digest"),r.getLong("version"),r.getLong("sla_config_id"),
                r.getInt("minimum_business_days"),r.getInt("maximum_business_days"),r.getString("timezone"),r.getString("calendar_version")),id);
    }
    private void lock(long booking) {
        require(!jdbc.queryForList("SELECT id FROM bookings WHERE id=? FOR UPDATE",Long.class,booking).isEmpty(),"ERR_NOT_FOUND");
    }
    private void admin(long actor) {
        require(!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,actor).isEmpty(),"ERR_FORBIDDEN");
    }
    private static void text(String value) { require(value!=null&&!value.isBlank()&&value.length()<=200,"ERR_INVALID_REQUEST"); }
    private static void require(boolean valid,String code) { if(!valid) throw new PaymentPrimitiveException(code); }
}
