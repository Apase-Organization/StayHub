package com.stayhub.payment.ledger;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-room arrival evidence and a read-only payout eligibility projection. */
@Service
public class ArrivalEligibilityService {
    public enum GuestDecision { ACKNOWLEDGE, CONTEST }
    public record ArrivalCommand(long bookingId, long itemId, int roomOrdinal, long actorId,
            Instant actualAt, String evidenceDigest, String idempotencyKey, long expectedVersion) {}
    public record ArrivalView(long bookingId, long itemId, int roomOrdinal, Instant actualAt,
            String state, String evidenceDigest, long version, boolean needsReview) {}
    public record Eligibility(long bookingId, boolean eligible, Instant earliestAt,
            int requiredRooms, int verifiedRooms, List<String> blockers,long version) {}
    public enum RecoveryOutcome { RECONCILED_NO_LOSS, MANUAL_RECOVERY_DOCUMENTED }
    public record RecoveryView(long id,long bookingId,long payoutReservationId,java.math.BigDecimal exposureAmount,
            String ownerRole,String state,String outcome,long version) {}
    public record RoomEvidence(long itemId,int roomOrdinal,String roomType,String ratePlan,Instant actualAt,String evidenceDigest,
            String state,boolean needsReview,long bookingVersion) {}
    public record PayoutProofCommand(long bookingId,long reservationId,long makerId,long checkerId,
            String transferRef,String proofDigest,Instant executedAt,String idempotencyKey) {}

    private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ArrivalEligibilityService(JdbcTemplate jdbc) { this(jdbc, Clock.systemUTC()); }
    ArrivalEligibilityService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public ArrivalView report(ArrivalCommand command) {
        validate(command);
        lock(command.bookingId());
        var booking = jdbc.queryForMap("""
                SELECT b.created_at,b.check_in_date,b.check_out_date,b.status,p.host_id
                FROM bookings b JOIN properties p ON p.id=b.property_id WHERE b.id=?
                """, command.bookingId());
        require(((Number) booking.get("host_id")).longValue() == command.actorId(), "ERR_FORBIDDEN");
        require("CONFIRMED".equals(booking.get("status")), "ERR_INVALID_STATE");
        Timestamp created = (Timestamp) booking.get("created_at");
        require(!command.actualAt().isBefore(created.toInstant()) && !command.actualAt().isAfter(clock.instant()),
                "ERR_INVALID_REQUEST");
        require(jdbc.queryForList("""
                SELECT ra.item_id FROM room_allocations ra JOIN booking_items bi ON bi.id=ra.item_id
                WHERE bi.booking_id=? AND ra.item_id=? AND ra.room_ordinal=?
                """, Long.class, command.bookingId(), command.itemId(), command.roomOrdinal()).size() == 1,
                "ERR_NOT_FOUND");
        long version = bookingVersion(command.bookingId());
        String body = LedgerService.digest(List.of(command.bookingId(), command.itemId(), command.roomOrdinal(),
                command.actorId(), command.actualAt(), command.evidenceDigest(), command.expectedVersion()));
        var replay = findReplay(command.actorId(), command.bookingId(), command.idempotencyKey(), body);
        if (replay != null) return replay;
        require(version == command.expectedVersion(), "ERR_VERSION_CONFLICT");
        LocalDate in = ((java.sql.Date) booking.get("check_in_date")).toLocalDate();
        LocalDate out = ((java.sql.Date) booking.get("check_out_date")).toLocalDate();
        Instant scheduledIn = in.atTime(14, 0).atZone(HCM).toInstant();
        Instant scheduledOut = out.atTime(11, 0).atZone(HCM).toInstant();
        boolean needsReview = command.actualAt().isBefore(scheduledIn) || command.actualAt().isAfter(scheduledOut);
        jdbc.update("""
                INSERT INTO booking_arrivals(booking_id,item_id,room_ordinal,host_id,actual_at,timezone,
                    evidence_digest,state,needs_review)
                VALUES (?,?,?,?,?,'Asia/Ho_Chi_Minh',?,'REPORTED',?)
                """, command.bookingId(), command.itemId(), command.roomOrdinal(), command.actorId(),
                Timestamp.from(command.actualAt()), command.evidenceDigest(), needsReview);
        append(command, "REPORTED", body);
        advance(command.bookingId());
        return get(command.bookingId(), command.itemId(), command.roomOrdinal());
    }

    @Transactional
    public ArrivalView guestDecision(long bookingId, long itemId, int ordinal, long guestId,
            GuestDecision decision, String evidenceDigest, String key, long expectedVersion) {
        Objects.requireNonNull(decision);
        String auditDigest=decision==GuestDecision.ACKNOWLEDGE
                ? LedgerService.digest(List.of("GUEST_ARRIVAL_ACKNOWLEDGMENT",bookingId,itemId,ordinal,guestId,expectedVersion))
                : evidenceDigest;
        if(decision!=GuestDecision.ACKNOWLEDGE) LedgerService.hashFormat(auditDigest);
        return transition(bookingId, itemId, ordinal, guestId, auditDigest, key, expectedVersion,
                decision == GuestDecision.ACKNOWLEDGE ? "ACKNOWLEDGED" : "CONTESTED",
                "SELECT id FROM bookings WHERE id=? AND guest_id=?", true);
    }

    @Transactional
    public ArrivalView adminVerify(long bookingId, long itemId, int ordinal, long adminId,
            String evidenceDigest, String key, long expectedVersion) {
        LedgerService.hashFormat(evidenceDigest);
        return transition(bookingId, itemId, ordinal, adminId, evidenceDigest, key, expectedVersion,
                "ADMIN_VERIFIED", "SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'", false);
    }

    private ArrivalView transition(long bookingId, long itemId, int ordinal, long actor, String evidence,
            String key, long expectedVersion, String event, String identitySql, boolean guest) {
        require(bookingId > 0 && itemId > 0 && ordinal > 0 && actor > 0 && expectedVersion >= 0,
                "ERR_INVALID_REQUEST");
        LedgerService.hashFormat(evidence);
        require(key != null && !key.isBlank() && key.length() <= 200, "ERR_INVALID_REQUEST");
        lock(bookingId);
        boolean allowed = guest
                ? !jdbc.queryForList(identitySql, Long.class, bookingId, actor).isEmpty()
                : !jdbc.queryForList(identitySql, Long.class, actor).isEmpty();
        require(allowed, "ERR_FORBIDDEN");
        long version = bookingVersion(bookingId);
        String body = LedgerService.digest(List.of(bookingId, itemId, ordinal, actor, event, evidence, expectedVersion));
        var previous = jdbc.query("""
                SELECT a.*,e.body_hash,psl.version AS booking_version
                FROM booking_arrival_events e JOIN booking_arrivals a
                  ON a.booking_id=e.booking_id AND a.item_id=e.item_id AND a.room_ordinal=e.room_ordinal
                JOIN payment_settlement_locks psl ON psl.booking_id=a.booking_id
                WHERE e.actor_id=? AND e.booking_id=? AND e.idempotency_key=?
                """, (r,n)->Map.entry(r.getString("body_hash"),view(r,bookingId,r.getLong("item_id"),
                    r.getInt("room_ordinal"),r.getLong("booking_version"))),
                actor, bookingId, key);
        if (!previous.isEmpty()) {
            require(previous.getFirst().getKey().equals(body),"ERR_IDEMPOTENCY_CONFLICT");
            return previous.getFirst().getValue();
        }
        require(version == expectedVersion, "ERR_VERSION_CONFLICT");
        var rows = jdbc.query("""
                SELECT state,version,actual_at,evidence_digest,needs_review FROM booking_arrivals
                WHERE booking_id=? AND item_id=? AND room_ordinal=? FOR UPDATE
                """,(r,n)->Map.entry(r.getString("state"),r.getLong("version")),bookingId,itemId,ordinal);
        require(!rows.isEmpty(),"ERR_NOT_FOUND");
        String current=rows.getFirst().getKey();
        boolean validTransition=event.equals("ADMIN_VERIFIED")
                ? current.equals("REPORTED")||current.equals("CONTESTED")||current.equals("ACKNOWLEDGED")
                : event.equals("CONTESTED") ? current.equals("REPORTED")||current.equals("ACKNOWLEDGED")
                : current.equals("REPORTED");
        require(validTransition,"ERR_INVALID_STATE");
        jdbc.update("""
                UPDATE booking_arrivals SET state=?,guest_id=coalesce(?,guest_id),admin_id=coalesce(?,admin_id),
                    needs_review=CASE WHEN ? THEN false ELSE needs_review END,version=version+1
                WHERE booking_id=? AND item_id=? AND room_ordinal=?
                """,event.equals("ADMIN_VERIFIED")?"ADMIN_VERIFIED":event,
                guest?actor:null,guest?null:actor,event.equals("ADMIN_VERIFIED"),bookingId,itemId,ordinal);
        if(event.equals("CONTESTED")) {
            long block=jdbc.queryForObject("""
                    INSERT INTO booking_settlement_blocks(booking_id,reason_code,evidence_digest,actor_id)
                    VALUES (?,'DISPUTE',?,?) RETURNING id
                    """,Long.class,bookingId,evidence,actor);
            jdbc.update("""
                    INSERT INTO booking_settlement_block_events(block_id,booking_id,actor_id,event_type,
                        evidence_digest,idempotency_key,body_hash,expected_version)
                    VALUES (?,?,?,'OPENED',?,?,?,?)
                    """,block,bookingId,actor,evidence,key,body,expectedVersion);
            openPostPayoutRecoveries(bookingId,block,actor,evidence,key);
        }
        jdbc.update("""
                INSERT INTO booking_arrival_events(booking_id,item_id,room_ordinal,actor_id,event_type,
                    evidence_digest,idempotency_key,body_hash,expected_version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,bookingId,itemId,ordinal,actor,event,evidence,key,body,expectedVersion);
        advance(bookingId);
        return get(bookingId,itemId,ordinal);
    }

    @Transactional
    public void resolveBlock(long bookingId,long blockId,long adminId,String evidence,String key,long expectedVersion) {
        LedgerService.hashFormat(evidence);
        require(key!=null&&!key.isBlank()&&key.length()<=200&&expectedVersion>=0,"ERR_INVALID_REQUEST");
        lock(bookingId);
        require(!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,adminId).isEmpty(),"ERR_FORBIDDEN");
        String body=LedgerService.digest(List.of(bookingId,blockId,adminId,evidence,expectedVersion));
        var replay=jdbc.query("SELECT body_hash FROM booking_settlement_block_events WHERE actor_id=? AND booking_id=? AND idempotency_key=?",
                (r,n)->r.getString(1),adminId,bookingId,key);
        if(!replay.isEmpty()) { require(body.equals(replay.getFirst()),"ERR_IDEMPOTENCY_CONFLICT"); return; }
        require(bookingVersion(bookingId)==expectedVersion,"ERR_VERSION_CONFLICT");
        int updated=jdbc.update("""
                UPDATE booking_settlement_blocks SET active=false,resolved_at=clock_timestamp(),
                    resolution_digest=?,resolution_actor_id=? WHERE id=? AND booking_id=? AND active=true
                """,evidence,adminId,blockId,bookingId);
        require(updated==1,"ERR_NOT_FOUND");
        jdbc.update("""
                INSERT INTO booking_settlement_block_events(block_id,booking_id,actor_id,event_type,evidence_digest,
                    idempotency_key,body_hash,expected_version) VALUES (?,?,?,'RESOLVED',?,?,?,?)
                """,blockId,bookingId,adminId,evidence,key,body,expectedVersion);
        advance(bookingId);
    }

    @Transactional
    public long openDispute(long bookingId,long actor,String evidence,String key,long expectedVersion) {
        LedgerService.hashFormat(evidence);
        require(bookingId>0&&actor>0&&expectedVersion>=0&&key!=null&&!key.isBlank()&&key.length()<=200,"ERR_INVALID_REQUEST");
        lock(bookingId);
        require(!jdbc.queryForList("""
                SELECT b.id FROM bookings b JOIN properties p ON p.id=b.property_id
                WHERE b.id=? AND (b.guest_id=? OR p.host_id=? OR EXISTS
                    (SELECT 1 FROM users u WHERE u.id=? AND u.role='ADMIN' AND u.status='ACTIVE'))
                """,Long.class,bookingId,actor,actor,actor).isEmpty(),"ERR_NOT_FOUND");
        String body=LedgerService.digest(List.of(bookingId,actor,evidence,expectedVersion));
        var replay=jdbc.query("SELECT body_hash,block_id FROM booking_settlement_block_events WHERE actor_id=? AND booking_id=? AND idempotency_key=?",
                (r,n)->Map.entry(r.getString(1),r.getLong(2)),actor,bookingId,key);
        if(!replay.isEmpty()) { require(replay.getFirst().getKey().equals(body),"ERR_IDEMPOTENCY_CONFLICT");return replay.getFirst().getValue(); }
        require(bookingVersion(bookingId)==expectedVersion,"ERR_VERSION_CONFLICT");
        long block=jdbc.queryForObject("""
                INSERT INTO booking_settlement_blocks(booking_id,reason_code,evidence_digest,actor_id)
                VALUES (?,'DISPUTE',?,?) RETURNING id
                """,Long.class,bookingId,evidence,actor);
        jdbc.update("""
                INSERT INTO booking_settlement_block_events(block_id,booking_id,actor_id,event_type,evidence_digest,
                    idempotency_key,body_hash,expected_version) VALUES (?,?,?,'OPENED',?,?,?,?)
                """,block,bookingId,actor,evidence,key,body,expectedVersion);
        advance(bookingId);
        openPostPayoutRecoveries(bookingId,block,actor,evidence,key);
        return block;
    }

    /** Admin records a no-show finding after scheduled checkout; it blocks completion, review and payout. */
    @Transactional
    public long recordNoShow(long bookingId,long adminId,String evidence,String key,long expectedVersion) {
        LedgerService.hashFormat(evidence);
        require(bookingId>0&&expectedVersion>=0&&key!=null&&!key.isBlank()&&key.length()<=200,"ERR_INVALID_REQUEST");
        lock(bookingId);
        require(!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,adminId).isEmpty(),"ERR_FORBIDDEN");
        var rows=jdbc.query("""
                SELECT b.status,b.check_out_date FROM bookings b
                WHERE b.id=? AND EXISTS (SELECT 1 FROM booking_financial_snapshots s WHERE s.booking_id=b.id)
                """,(r,n)->Map.entry(r.getString("status"),r.getDate("check_out_date").toLocalDate()),bookingId);
        require(!rows.isEmpty(),"ERR_NOT_FOUND");
        require(rows.getFirst().getKey().equals("CONFIRMED"),"ERR_INVALID_STATE");
        Instant checkout=rows.getFirst().getValue().atTime(11,0).atZone(HCM).toInstant();
        require(!clock.instant().isBefore(checkout),"ERR_NO_SHOW_TOO_EARLY");
        require(jdbc.queryForObject("SELECT count(*) FROM booking_arrivals WHERE booking_id=?",Integer.class,bookingId)==0,
                "ERR_NO_SHOW_ARRIVAL_RECORDED");
        String body=LedgerService.digest(List.of("NO_SHOW",bookingId,adminId,evidence,expectedVersion));
        var replay=jdbc.query("SELECT body_hash,block_id FROM booking_settlement_block_events WHERE actor_id=? AND booking_id=? AND idempotency_key=?",
                (r,n)->Map.entry(r.getString(1),r.getLong(2)),adminId,bookingId,key);
        if(!replay.isEmpty()) {
            require(replay.getFirst().getKey().equals(body),"ERR_IDEMPOTENCY_CONFLICT");
            return replay.getFirst().getValue();
        }
        require(bookingVersion(bookingId)==expectedVersion,"ERR_VERSION_CONFLICT");
        long block=jdbc.queryForObject("""
                INSERT INTO booking_settlement_blocks(booking_id,reason_code,evidence_digest,actor_id)
                VALUES (?,'NO_SHOW',?,?) RETURNING id
                """,Long.class,bookingId,evidence,adminId);
        jdbc.update("""
                INSERT INTO booking_settlement_block_events(block_id,booking_id,actor_id,event_type,evidence_digest,
                    idempotency_key,body_hash,expected_version) VALUES (?,?,?,'OPENED',?,?,?,?)
                """,block,bookingId,adminId,evidence,key,body,expectedVersion);
        advance(bookingId);
        return block;
    }

    @Transactional
    public RecoveryView resolveRecovery(long bookingId,long caseId,long adminId,RecoveryOutcome outcome,
            String evidence,String key,long expectedVersion) {
        return resolveRecovery(bookingId,caseId,adminId,null,null,outcome,evidence,key,expectedVersion);
    }

    @Transactional
    public RecoveryView resolveRecovery(long bookingId,long caseId,long makerId,Long checkerId,String transferRef,
            RecoveryOutcome outcome,String evidence,String key,long expectedVersion) {
        Objects.requireNonNull(outcome);LedgerService.hashFormat(evidence);
        require(key!=null&&!key.isBlank()&&key.length()<=200&&expectedVersion>=0,"ERR_INVALID_REQUEST");
        lock(bookingId);
        require(!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,makerId).isEmpty(),"ERR_FORBIDDEN");
        if(outcome==RecoveryOutcome.MANUAL_RECOVERY_DOCUMENTED) {
            require(checkerId!=null&&checkerId!=makerId&&transferRef!=null&&!transferRef.isBlank()&&transferRef.length()<=200,
                    "ERR_MAKER_CHECKER_CONFLICT");
            require(!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,checkerId).isEmpty(),"ERR_FORBIDDEN");
        } else require(checkerId==null&&transferRef==null,"ERR_INVALID_REQUEST");
        var current=getRecovery(bookingId,caseId);
        String body=LedgerService.digest(java.util.Arrays.asList(bookingId,caseId,makerId,checkerId,transferRef,outcome,evidence,expectedVersion));
        var replay=jdbc.query("SELECT body_hash FROM payout_recovery_events WHERE actor_id=? AND recovery_case_id=? AND idempotency_key=?",
                (r,n)->r.getString(1),makerId,caseId,key);
        if(!replay.isEmpty()){require(body.equals(replay.getFirst()),"ERR_IDEMPOTENCY_CONFLICT");return getRecovery(bookingId,caseId);}
        require(current.version()==expectedVersion&&current.state().equals("OPEN"),"ERR_VERSION_CONFLICT");
        jdbc.update("""
                UPDATE payout_recovery_cases SET state='RESOLVED',outcome=?,resolution_digest=?,resolution_maker_id=?,
                    resolution_checker_id=?,transfer_ref=?,resolved_at=clock_timestamp(),version=version+1
                WHERE id=? AND booking_id=?
                """,outcome.name(),evidence,makerId,checkerId,transferRef,caseId,bookingId);
        jdbc.update("""
                INSERT INTO payout_recovery_events(recovery_case_id,actor_id,event_type,idempotency_key,body_hash,evidence_digest,expected_version)
                VALUES (?,?,'RESOLVED',?,?,?,?)
                """,caseId,makerId,key,body,evidence,expectedVersion);
        advance(bookingId);
        return getRecovery(bookingId,caseId);
    }

    /** Records evidence of a manually executed transfer; this method never initiates one. */
    @Transactional
    public long recordManualPayoutProof(PayoutProofCommand command) {
        require(command!=null&&command.bookingId()>0&&command.reservationId()>0&&command.makerId()>0
                &&command.checkerId()>0&&command.executedAt()!=null,"ERR_INVALID_REQUEST");
        require(command.transferRef()!=null&&!command.transferRef().isBlank()&&command.transferRef().length()<=200
                &&command.idempotencyKey()!=null&&!command.idempotencyKey().isBlank()
                &&command.idempotencyKey().length()<=200,"ERR_INVALID_REQUEST");
        LedgerService.hashFormat(command.proofDigest());
        require(!command.executedAt().isAfter(clock.instant()),"ERR_INVALID_REQUEST");
        require(command.makerId()!=command.checkerId(),"ERR_MAKER_CHECKER_CONFLICT");
        lock(command.bookingId());
        require(!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,command.makerId()).isEmpty()
                &&!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,command.checkerId()).isEmpty(),"ERR_FORBIDDEN");
        var reservation=jdbc.query("""
                SELECT r.amount,r.kind,r.source,r.actor_id FROM ledger_reservations r
                WHERE r.id=? AND r.booking_id=?
                  AND EXISTS (SELECT 1 FROM ledger_reserve_movements m WHERE m.reservation_id=r.id AND m.action='CONSUME')
                """,(r,n)->new Object[]{r.getBigDecimal("amount"),r.getString("kind"),r.getString("source"),r.getLong("actor_id")},
                command.reservationId(),command.bookingId());
        require(!reservation.isEmpty(),"ERR_PAYOUT_NOT_EXECUTED");
        Object[] r=reservation.getFirst();
        require("PAYOUT".equals(r[1])&&"ONLINE_VERIFIED".equals(r[2])&&((Number)r[3]).longValue()==command.makerId(),"ERR_PAYOUT_NOT_ELIGIBLE");
        String body=LedgerService.digest(List.of(command.bookingId(),command.reservationId(),command.makerId(),
                command.checkerId(),command.transferRef(),command.proofDigest(),command.executedAt()));
        var existingProof=jdbc.query("SELECT id,transfer_ref,proof_digest,maker_id,checker_id FROM payout_execution_proofs WHERE reservation_id=?",
                (rs,n)->new Object[]{rs.getLong("id"),rs.getString("transfer_ref"),rs.getString("proof_digest"),rs.getLong("maker_id"),rs.getLong("checker_id")},
                command.reservationId());
        if(!existingProof.isEmpty()) {
            Object[] p=existingProof.getFirst();
            require(p[1].equals(command.transferRef())&&p[2].equals(command.proofDigest())
                    &&((Number)p[3]).longValue()==command.makerId()&&((Number)p[4]).longValue()==command.checkerId(),
                    "ERR_IDEMPOTENCY_CONFLICT");
            return ((Number)p[0]).longValue();
        }
        var replay=jdbc.query("SELECT id,body_hash FROM payout_execution_proofs WHERE maker_id=? AND idempotency_key=?",
                (row,n)->Map.entry(row.getLong("id"),row.getString("body_hash")),command.makerId(),command.idempotencyKey());
        if(!replay.isEmpty()) {
            require(body.equals(replay.getFirst().getValue()),"ERR_IDEMPOTENCY_CONFLICT");
            return replay.getFirst().getKey();
        }
        long proofId=jdbc.queryForObject("""
                INSERT INTO payout_execution_proofs(booking_id,reservation_id,transfer_ref,proof_digest,maker_id,
                    checker_id,idempotency_key,body_hash,executed_at)
                VALUES (?,?,?,?,?,?,?,?,?) RETURNING id
                """,Long.class,command.bookingId(),command.reservationId(),command.transferRef(),command.proofDigest(),
                command.makerId(),command.checkerId(),command.idempotencyKey(),body,Timestamp.from(command.executedAt()));
        var activeBlocks=jdbc.queryForList("SELECT id FROM booking_settlement_blocks WHERE booking_id=? AND active ORDER BY id",
                Long.class,command.bookingId());
        for(long block:activeBlocks) openRecovery(command.bookingId(),block,command.reservationId(),
                (java.math.BigDecimal)r[0],command.checkerId(),command.proofDigest());
        return proofId;
    }

    @Transactional(readOnly=true)
    public Eligibility eligibility(long bookingId) {
        require(bookingId > 0,"ERR_INVALID_REQUEST");
        var bookingRows=jdbc.query("""
                SELECT b.status,s.held_host_payable FROM bookings b
                JOIN booking_financial_snapshots s ON s.booking_id=b.id WHERE b.id=?
                """,(r,n)->Map.entry(r.getString("status"),r.getBigDecimal("held_host_payable")),bookingId);
        require(!bookingRows.isEmpty(),"ERR_NOT_FOUND");
        int required=jdbc.queryForObject("""
                SELECT count(*) FROM room_allocations ra JOIN booking_items bi ON bi.id=ra.item_id
                WHERE bi.booking_id=?
                """,Integer.class,bookingId);
        int actual=jdbc.queryForObject("SELECT count(*) FROM booking_arrivals WHERE booking_id=?",Integer.class,bookingId);
        int verified=jdbc.queryForObject("""
                SELECT count(*) FROM booking_arrivals WHERE booking_id=? AND state IN ('ACKNOWLEDGED','ADMIN_VERIFIED')
                    AND NOT needs_review
                """,Integer.class,bookingId);
        Instant earliest=jdbc.query("""
                SELECT max(actual_at)+interval '24 hours' FROM booking_arrivals
                WHERE booking_id=? AND state IN ('ACKNOWLEDGED','ADMIN_VERIFIED') AND NOT needs_review
                """,r->r.next()&&r.getTimestamp(1)!=null?r.getTimestamp(1).toInstant():null,bookingId);
        var blockers=new java.util.ArrayList<String>();
        if(!bookingRows.getFirst().getKey().equals("CONFIRMED")&&!bookingRows.getFirst().getKey().equals("COMPLETED"))
            blockers.add("BOOKING_NOT_ELIGIBLE_STATE");
        if(bookingRows.getFirst().getValue().signum()<=0) blockers.add("NO_PLATFORM_HELD_ENTITLEMENT");
        if(required==0 || actual!=required) blockers.add("ALL_ROOMS_NOT_RECORDED");
        if(actual!=verified) blockers.add("ARRIVAL_UNACKNOWLEDGED_OR_CONTESTED");
        if(earliest==null || clock.instant().isBefore(earliest)) blockers.add("ARRIVAL_PLUS_24H_NOT_REACHED");
        if(jdbc.queryForObject("SELECT count(*) FROM booking_settlement_blocks WHERE booking_id=? AND active",Integer.class,bookingId)>0)
            blockers.add("ACTIVE_SETTLEMENT_BLOCK");
        if(jdbc.queryForObject("SELECT count(*) FROM direct_receipt_cases WHERE booking_id=? AND state IN ('CONTESTED','REFUSED')",Integer.class,bookingId)>0)
            blockers.add("DIRECT_RECEIPT_DISPUTED");
        if(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='RECONCILE'",Integer.class,bookingId)>0)
            blockers.add("UNRESOLVED_PAYMENT_RECONCILIATION");
        if(jdbc.queryForObject("SELECT count(*) FROM refund_cases WHERE booking_id=? AND state IN ('REQUESTED','APPROVED','PROCESSING')",Integer.class,bookingId)>0)
            blockers.add("REFUND_IN_PROGRESS");
        if(jdbc.queryForObject("SELECT count(*) FROM payout_recovery_cases WHERE booking_id=? AND state='OPEN'",Integer.class,bookingId)>0)
            blockers.add("POST_PAYOUT_RECOVERY_OPEN");
        var gates=jdbc.queryForList("SELECT finance_legal,operations,liquidity FROM booking_release_clearance WHERE booking_id=?",bookingId);
        if(gates.isEmpty() || !Boolean.TRUE.equals(gates.getFirst().get("finance_legal"))
                || !Boolean.TRUE.equals(gates.getFirst().get("operations")) || !Boolean.TRUE.equals(gates.getFirst().get("liquidity")))
            blockers.add("RELEASE_CLEARANCE_NOT_APPROVED");
        return new Eligibility(bookingId,blockers.isEmpty(),earliest,required,verified,List.copyOf(blockers),bookingVersion(bookingId));
    }

    private void openPostPayoutRecoveries(long booking,long block,long actor,String evidence,String key) {
        var paid=jdbc.query("""
                SELECT r.id,r.amount FROM ledger_reservations r JOIN ledger_reserve_movements m ON m.reservation_id=r.id
                JOIN payout_execution_proofs p ON p.reservation_id=r.id AND p.booking_id=r.booking_id
                WHERE r.booking_id=? AND r.kind='PAYOUT' AND m.action='CONSUME' AND r.source='ONLINE_VERIFIED'
                ORDER BY r.id
                """,(r,n)->Map.entry(r.getLong("id"),r.getBigDecimal("amount")),booking);
        for(var payout:paid) openRecovery(booking,block,payout.getKey(),payout.getValue(),actor,evidence);
    }

    private void openRecovery(long booking,long block,long reservation,java.math.BigDecimal amount,long actor,String evidence) {
        Long recovery=jdbc.query("""
                INSERT INTO payout_recovery_cases(booking_id,settlement_block_id,payout_reservation_id,
                    exposure_amount,source,evidence_digest,opened_by,state)
                VALUES (?,?,?,?,'ONLINE_VERIFIED',?,?,'OPEN') ON CONFLICT (payout_reservation_id) DO NOTHING RETURNING id
                """,(r,n)->r.getLong(1),booking,block,reservation,amount,evidence,actor).stream().findFirst().orElse(null);
        if(recovery==null) return;
        String eventKey="recovery:"+block+":"+reservation;
        String hash=LedgerService.digest(List.of(booking,recovery,block,reservation,amount,evidence));
        jdbc.update("""
                INSERT INTO payout_recovery_events(recovery_case_id,actor_id,event_type,idempotency_key,body_hash,evidence_digest,expected_version)
                VALUES (?,?,'OPENED',?,?,?,0)
                """,recovery,actor,eventKey,hash,evidence);
    }

    @Transactional(readOnly=true)
    public java.util.List<RecoveryView> recoveriesForActor(long booking,long actor) {
        require(!jdbc.queryForList("""
                SELECT b.id FROM bookings b JOIN properties p ON p.id=b.property_id
                WHERE b.id=? AND (b.guest_id=? OR p.host_id=? OR EXISTS
                  (SELECT 1 FROM users u WHERE u.id=? AND u.role='ADMIN' AND u.status='ACTIVE'))
                """,Long.class,booking,actor,actor,actor).isEmpty(),"ERR_NOT_FOUND");
        return jdbc.query("SELECT * FROM payout_recovery_cases WHERE booking_id=? ORDER BY id",
                (r,n)->new RecoveryView(r.getLong("id"),r.getLong("booking_id"),r.getLong("payout_reservation_id"),
                        r.getBigDecimal("exposure_amount"),r.getString("owner_role"),r.getString("state"),
                        r.getString("outcome"),r.getLong("version")),booking);
    }
    @Transactional(readOnly=true)
    public List<RoomEvidence> roomsForActor(long booking,long actor) {
        require(!jdbc.queryForList("""
                SELECT b.id FROM bookings b JOIN properties p ON p.id=b.property_id
                WHERE b.id=? AND (b.guest_id=? OR p.host_id=? OR EXISTS
                    (SELECT 1 FROM users u WHERE u.id=? AND u.role='ADMIN' AND u.status='ACTIVE'))
                """,Long.class,booking,actor,actor,actor).isEmpty(),"ERR_NOT_FOUND");
        return jdbc.query("""
                SELECT bi.id AS item_id,ra.room_ordinal,rt.name AS room_type,rp.name AS rate_plan,
                    a.actual_at,a.evidence_digest,a.state,a.needs_review,psl.version AS booking_version
                FROM booking_items bi JOIN room_types rt ON rt.id=bi.room_type_id
                    JOIN rate_plans rp ON rp.id=bi.rate_plan_id JOIN room_allocations ra ON ra.item_id=bi.id
                    LEFT JOIN booking_arrivals a ON a.booking_id=bi.booking_id AND a.item_id=bi.id AND a.room_ordinal=ra.room_ordinal
                    JOIN payment_settlement_locks psl ON psl.booking_id=bi.booking_id
                WHERE bi.booking_id=? ORDER BY bi.id,ra.room_ordinal
                """,(r,n)->new RoomEvidence(r.getLong("item_id"),r.getInt("room_ordinal"),r.getString("room_type"),
                    r.getString("rate_plan"),r.getTimestamp("actual_at")==null?null:r.getTimestamp("actual_at").toInstant(),r.getString("evidence_digest"),
                    r.getString("state"),r.getBoolean("needs_review"),r.getLong("booking_version")),booking);
    }
    private RecoveryView getRecovery(long booking,long id) {
        return jdbc.queryForObject("SELECT * FROM payout_recovery_cases WHERE id=? AND booking_id=?",
                (r,n)->new RecoveryView(r.getLong("id"),r.getLong("booking_id"),r.getLong("payout_reservation_id"),
                        r.getBigDecimal("exposure_amount"),r.getString("owner_role"),r.getString("state"),
                        r.getString("outcome"),r.getLong("version")),id,booking);
    }

    @Transactional(readOnly=true)
    public Eligibility eligibilityForActor(long bookingId,long actor) {
        boolean allowed=!jdbc.queryForList("""
                SELECT b.id FROM bookings b JOIN properties p ON p.id=b.property_id
                WHERE b.id=? AND (b.guest_id=? OR p.host_id=? OR EXISTS
                    (SELECT 1 FROM users u WHERE u.id=? AND u.role='ADMIN' AND u.status='ACTIVE'))
                """,Long.class,bookingId,actor,actor,actor).isEmpty();
        require(allowed,"ERR_NOT_FOUND");
        return eligibility(bookingId);
    }

    /** Returns null only for pre-inventory legacy bookings; inventory bookings
     * must prove the full stay before BookingService may persist COMPLETED. */
    @Transactional
    public Instant authorizeCompletion(long bookingId,long hostId) {
        lock(bookingId);
        var rows=jdbc.query("""
                SELECT b.status,b.check_out_date,p.host_id FROM bookings b JOIN properties p ON p.id=b.property_id
                WHERE b.id=? AND EXISTS(SELECT 1 FROM booking_financial_snapshots s WHERE s.booking_id=b.id)
                """,(r,n)->new Object[]{r.getString("status"),r.getDate("check_out_date").toLocalDate(),r.getLong("host_id")},bookingId);
        if(rows.isEmpty()) return null;
        Object[] row=rows.getFirst();
        require(((Number)row[2]).longValue()==hostId,"ERR_FORBIDDEN");
        require("CONFIRMED".equals(row[0]),"ERR_INVALID_STATE");
        Instant checkout=((LocalDate)row[1]).atTime(11,0).atZone(HCM).toInstant();
        require(!clock.instant().isBefore(checkout),"ERR_COMPLETION_TOO_EARLY");
        int rooms=jdbc.queryForObject("""
                SELECT count(*) FROM room_allocations ra JOIN booking_items bi ON bi.id=ra.item_id WHERE bi.booking_id=?
                """,Integer.class,bookingId);
        int actual=jdbc.queryForObject("""
                SELECT count(*) FROM booking_arrivals a JOIN booking_items bi ON bi.id=a.item_id
                WHERE bi.booking_id=? AND a.state IN ('ACKNOWLEDGED','ADMIN_VERIFIED') AND NOT a.needs_review
                """,Integer.class,bookingId);
        require(rooms>0&&rooms==actual,"ERR_ACTUAL_STAY_REQUIRED");
        require(jdbc.queryForObject("SELECT count(*) FROM booking_settlement_blocks WHERE booking_id=? AND active",Integer.class,bookingId)==0
                &&jdbc.queryForObject("SELECT count(*) FROM direct_receipt_cases WHERE booking_id=? AND state IN ('ATTESTED','CONTESTED','REFUSED')",Integer.class,bookingId)==0
                &&jdbc.queryForObject("SELECT count(*) FROM refund_cases WHERE booking_id=? AND state IN ('REQUESTED','APPROVED','PROCESSING')",Integer.class,bookingId)==0
                &&jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='RECONCILE'",Integer.class,bookingId)==0
                &&jdbc.queryForObject("SELECT count(*) FROM payout_recovery_cases WHERE booking_id=? AND state='OPEN'",Integer.class,bookingId)==0,
                "ERR_UNRESOLVED_STAY_CASE");
        return clock.instant();
    }

    private ArrivalView findReplay(long actor,long booking,String key,String body) {
        var rows=jdbc.query("""
                SELECT a.*,psl.version AS booking_version FROM booking_arrival_events e JOIN booking_arrivals a
                  ON a.booking_id=e.booking_id AND a.item_id=e.item_id AND a.room_ordinal=e.room_ordinal
                JOIN payment_settlement_locks psl ON psl.booking_id=a.booking_id
                WHERE e.actor_id=? AND e.booking_id=? AND e.idempotency_key=?
                """,(r,n)->Map.entry(r.getString("body_hash"),view(r,booking,r.getLong("item_id"),r.getInt("room_ordinal"),r.getLong("booking_version"))),actor,booking,key);
        if(rows.isEmpty()) return null;
        require(rows.getFirst().getKey().equals(body),"ERR_IDEMPOTENCY_CONFLICT");
        return rows.getFirst().getValue();
    }
    private ArrivalView get(long booking,long item,int ordinal) {
        return jdbc.queryForObject("""
                SELECT a.*,psl.version AS booking_version FROM booking_arrivals a
                JOIN payment_settlement_locks psl ON psl.booking_id=a.booking_id
                WHERE a.booking_id=? AND a.item_id=? AND a.room_ordinal=?
                """,(r,n)->view(r,booking,item,ordinal,r.getLong("booking_version")),booking,item,ordinal);
    }
    private ArrivalView view(java.sql.ResultSet r,long booking,long item,int ordinal,long version) throws java.sql.SQLException {
        return new ArrivalView(booking,item,ordinal,r.getTimestamp("actual_at").toInstant(),r.getString("state"),
                r.getString("evidence_digest"),version,r.getBoolean("needs_review"));
    }
    private void append(ArrivalCommand c,String event,String body) {
        jdbc.update("""
                INSERT INTO booking_arrival_events(booking_id,item_id,room_ordinal,actor_id,event_type,actual_at,
                    evidence_digest,idempotency_key,body_hash,expected_version)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,c.bookingId(),c.itemId(),c.roomOrdinal(),c.actorId(),event,Timestamp.from(c.actualAt()),
                c.evidenceDigest(),c.idempotencyKey(),body,c.expectedVersion());
    }
    private long bookingVersion(long booking) {
        return jdbc.queryForObject("SELECT version FROM payment_settlement_locks WHERE booking_id=?",Long.class,booking);
    }
    private void advance(long booking) { jdbc.update("UPDATE payment_settlement_locks SET version=version+1 WHERE booking_id=?",booking); }
    private void lock(long booking) {
        require(!jdbc.queryForList("SELECT id FROM bookings WHERE id=? FOR UPDATE",Long.class,booking).isEmpty(),"ERR_NOT_FOUND");
    }
    private static void validate(ArrivalCommand c) {
        require(c!=null && c.bookingId()>0 && c.itemId()>0 && c.roomOrdinal()>0 && c.actorId()>0
                && c.expectedVersion()>=0 && c.actualAt()!=null,"ERR_INVALID_REQUEST");
        LedgerService.hashFormat(c.evidenceDigest());
        require(c.idempotencyKey()!=null && !c.idempotencyKey().isBlank() && c.idempotencyKey().length()<=200,"ERR_INVALID_REQUEST");
    }
    private static void require(boolean ok,String code) { if(!ok) throw new PaymentPrimitiveException(code); }
}
