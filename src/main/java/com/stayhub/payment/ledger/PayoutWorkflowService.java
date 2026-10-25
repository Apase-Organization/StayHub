package com.stayhub.payment.ledger;

import static com.stayhub.payment.ledger.LedgerService.Account.ONLINE_CASH;
import static com.stayhub.payment.ledger.LedgerService.Account.PAYOUT_RESERVED;
import static com.stayhub.payment.ledger.LedgerService.Line.credit;
import static com.stayhub.payment.ledger.LedgerService.Line.debit;

import com.stayhub.payment.ledger.PaymentPrimitivesService.MoneySource;
import com.stayhub.payment.ledger.PaymentPrimitivesService.Movement;
import com.stayhub.payment.ledger.PaymentPrimitivesService.ReserveInput;
import com.stayhub.payment.ledger.PaymentPrimitivesService.ReserveKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manual, proof-backed payout state machine. No bank transfer is initiated here. */
@Service
public class PayoutWorkflowService {
    public enum State { REQUESTED, APPROVED, PROCESSING, PAID, FAILED }
    public record View(long id,long bookingId,long reservationId,BigDecimal amount,String currency,State state,
            long makerId,Long checkerId,String transferRef,String proofDigest,long version) {}

    private final JdbcTemplate jdbc;
    private final PaymentPrimitivesService primitives;
    private final LedgerService ledger;
    private final ArrivalEligibilityService operations;

    @Autowired
    public PayoutWorkflowService(JdbcTemplate jdbc,PaymentPrimitivesService primitives,LedgerService ledger,
            ArrivalEligibilityService operations) {
        this.jdbc=jdbc;this.primitives=primitives;this.ledger=ledger;this.operations=operations;
    }

    @Transactional
    public View request(long booking,long maker,String key) {
        admin(maker);text(key);ledger.lock(booking);
        String digest=eligibilityDigest(booking,maker);
        var prior=jdbc.query("SELECT id,body_hash FROM payout_workflow_cases WHERE booking_id=? AND idempotency_key=?",
                (r,n)->Map.entry(r.getLong(1),r.getString(2)),booking,key);
        if(!prior.isEmpty()) {
            require(prior.getFirst().getValue().equals(LedgerService.digest(List.of(booking,maker,digest))),"ERR_IDEMPOTENCY_CONFLICT");
            return get(prior.getFirst().getKey());
        }
        var snapshot=primitives.snapshot(booking);
        BigDecimal payoutAllocated=jdbc.queryForObject("""
                SELECT coalesce(sum(r.amount),0) FROM ledger_reservations r LEFT JOIN ledger_reserve_movements m ON m.reservation_id=r.id
                WHERE r.booking_id=? AND r.kind='PAYOUT' AND r.source='ONLINE_VERIFIED' AND (m.action IS NULL OR m.action='CONSUME')
                """,BigDecimal.class,booking);
        BigDecimal amount=snapshot.heldHostPayable().subtract(payoutAllocated);
        require(amount.signum()>0,"ERR_PAYOUT_NOT_ELIGIBLE");
        var liquidityRows=jdbc.query("""
                SELECT id,available_online FROM refund_liquidity_snapshots
                WHERE (as_of AT TIME ZONE 'Asia/Ho_Chi_Minh')::date=(clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                ORDER BY as_of DESC,id DESC LIMIT 1 FOR UPDATE
                """,(r,n)->Map.entry(r.getLong("id"),r.getBigDecimal("available_online")));
        require(!liquidityRows.isEmpty(),"ERR_LIQUIDITY_UNAVAILABLE");
        long snapshotId=liquidityRows.getFirst().getKey();
        BigDecimal allocated=jdbc.queryForObject("""
                SELECT coalesce((SELECT sum(amount) FROM refund_liquidity_allocations WHERE snapshot_id=? AND state IN ('RESERVED','CONSUMED')),0)
                  + coalesce((SELECT sum(amount) FROM payout_liquidity_allocations WHERE snapshot_id=? AND state IN ('RESERVED','CONSUMED')),0)
                """,BigDecimal.class,snapshotId,snapshotId);
        BigDecimal liquid=liquidityRows.getFirst().getValue().subtract(allocated);
        require(liquid.signum()>=0,"ERR_LIQUIDITY_UNAVAILABLE");
        long reservation=primitives.reserve(new ReserveInput(booking,maker,ReserveKind.PAYOUT,MoneySource.ONLINE_VERIFIED,
                amount,"VND",snapshot.heldHostPayable(),liquid,digest,primitives.settlementVersion(booking)),"payout:"+key);
        jdbc.update("INSERT INTO payout_liquidity_allocations(reservation_id,snapshot_id,amount,state) VALUES (?,?,?,'RESERVED')",
                reservation,snapshotId,amount);
        String body=LedgerService.digest(List.of(booking,maker,digest));
        long id=jdbc.queryForObject("""
                INSERT INTO payout_workflow_cases(booking_id,reservation_id,liquidity_snapshot_id,amount,currency,
                    eligibility_digest,state,maker_id,idempotency_key,body_hash)
                VALUES (?,?,?,?,'VND',?,'REQUESTED',?,?,?) RETURNING id
                """,Long.class,booking,reservation,snapshotId,amount,digest,maker,key,body);
        event(id,maker,"REQUESTED",key,body,digest,0);
        return get(id);
    }

    @Transactional public View approve(long id,long checker,String key,String evidence,long version) {
        return transition(id,checker,key,evidence,version,"APPROVED",true,null,null);
    }
    @Transactional public View processing(long id,long actor,String key,String evidence,long version) {
        return transition(id,actor,key,evidence,version,"PROCESSING",false,null,null);
    }
    @Transactional public View paid(long id,long checker,String key,String ref,String proof,long version) {
        text(ref);LedgerService.hashFormat(proof);
        var current=get(id);
        var paid=transition(id,checker,key,proof,version,"PAID",false,ref,proof);
        primitives.moveReserve(current.bookingId(),checker,current.reservationId(),Movement.CONSUME,proof,
                "payout-consume:"+key,primitives.settlementVersion(current.bookingId()));
        jdbc.update("UPDATE payout_liquidity_allocations SET state='CONSUMED',updated_at=clock_timestamp() WHERE reservation_id=? AND state='RESERVED'",
                current.reservationId());
        ledger.postAdjustment(current.bookingId(),checker,"payout-paid:"+id,"payout-journal:"+key,proof,
                List.of(debit(PAYOUT_RESERVED,current.amount()),credit(ONLINE_CASH,current.amount())));
        operations.recordManualPayoutProof(new ArrivalEligibilityService.PayoutProofCommand(current.bookingId(),
                current.reservationId(),current.makerId(),checker,ref,proof,java.time.Instant.now(),"payout-proof:"+key));
        return paid;
    }
    @Transactional public View fail(long id,long actor,String key,String evidence,long version) {
        LedgerService.hashFormat(evidence);var current=get(id);
        var failed=transition(id,actor,key,evidence,version,"FAILED",false,null,null);
        primitives.moveReserve(current.bookingId(),actor,current.reservationId(),Movement.RELEASE,evidence,
                "payout-release:"+key,primitives.settlementVersion(current.bookingId()));
        jdbc.update("UPDATE payout_liquidity_allocations SET state='RELEASED',updated_at=clock_timestamp() WHERE reservation_id=? AND state='RESERVED'",
                current.reservationId());
        return failed;
    }

    private View transition(long id,long actor,String key,String evidence,long version,String next,
            boolean approval,String ref,String proof) {
        admin(actor);text(key);LedgerService.hashFormat(evidence);var current=get(id);ledger.lock(current.bookingId());
        String body=LedgerService.digest(java.util.Arrays.asList(id,actor,key,evidence,version,next,ref,proof));
        var replay=jdbc.query("SELECT body_hash FROM payout_workflow_events WHERE actor_id=? AND payout_case_id=? AND idempotency_key=?",
                (r,n)->r.getString(1),actor,id,key);
        if(!replay.isEmpty()){require(body.equals(replay.getFirst()),"ERR_IDEMPOTENCY_CONFLICT");return get(id);}
        var row=jdbc.queryForMap("SELECT state,version,maker_id,checker_id FROM payout_workflow_cases WHERE id=? FOR UPDATE",id);
        require(((Number)row.get("version")).longValue()==version,"ERR_VERSION_CONFLICT");
        String old=(String)row.get("state");
        require((next.equals("APPROVED")&&old.equals("REQUESTED"))||(next.equals("PROCESSING")&&old.equals("APPROVED"))
                ||(next.equals("PAID")&&old.equals("PROCESSING"))
                ||(next.equals("FAILED")&&(old.equals("APPROVED")||old.equals("PROCESSING"))),"ERR_INVALID_STATE");
        long maker=((Number)row.get("maker_id")).longValue();Long oldChecker=(Long)row.get("checker_id");
        if(approval) require(actor!=maker,"ERR_MAKER_CHECKER_CONFLICT");
        else if(oldChecker!=null) require(actor==oldChecker,"ERR_FORBIDDEN");
        if(next.equals("PAID")) require(oldChecker!=null&&oldChecker==actor&&ref!=null&&proof!=null,"ERR_PAYOUT_PROOF_REQUIRED");
        jdbc.update("""
                UPDATE payout_workflow_cases SET state=?,checker_id=coalesce(checker_id,?),transfer_ref=coalesce(?,transfer_ref),
                    proof_digest=coalesce(?,proof_digest),version=version+1,updated_at=clock_timestamp() WHERE id=?
                """,next,approval?actor:null,ref,proof,id);
        event(id,actor,next,key,body,evidence,version);return get(id);
    }
    private String eligibilityDigest(long booking,long actor) {
        var eligibility=operations.eligibilityForActor(booking,actor);
        require(eligibility.eligible(),"ERR_PAYOUT_NOT_ELIGIBLE");
        return LedgerService.digest(List.of("S0-086-v1",booking,eligibility.earliestAt(),
                eligibility.requiredRooms(),eligibility.verifiedRooms(),eligibility.blockers()));
    }
    private void event(long id,long actor,String type,String key,String body,String evidence,long version) {
        jdbc.update("""
                INSERT INTO payout_workflow_events(payout_case_id,actor_id,event_type,idempotency_key,body_hash,evidence_digest,expected_version)
                VALUES (?,?,?,?,?,?,?)
                """,id,actor,type,key,body,evidence,version);
    }
    @Transactional(readOnly=true)
    public View get(long id) {
        return jdbc.queryForObject("SELECT * FROM payout_workflow_cases WHERE id=?",(r,n)->new View(r.getLong("id"),
                r.getLong("booking_id"),r.getLong("reservation_id"),r.getBigDecimal("amount"),r.getString("currency"),
                State.valueOf(r.getString("state")),r.getLong("maker_id"),r.getObject("checker_id",Long.class),
                r.getString("transfer_ref"),r.getString("proof_digest"),r.getLong("version")),id);
    }
    private void admin(long id){require(!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,id).isEmpty(),"ERR_FORBIDDEN");}
    private static void text(String v){require(v!=null&&!v.isBlank()&&v.length()<=200,"ERR_INVALID_REQUEST");}
    private static void require(boolean ok,String code){if(!ok)throw new PaymentPrimitiveException(code);}
}
