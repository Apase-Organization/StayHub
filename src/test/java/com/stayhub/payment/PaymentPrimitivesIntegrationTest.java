package com.stayhub.payment;

import static org.assertj.core.api.Assertions.*;

import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.payment.ledger.*;
import static com.stayhub.payment.ledger.PaymentPrimitivesService.*;
import static com.stayhub.payment.ledger.LedgerService.Account.*;
import com.stayhub.payment.ledger.LedgerService.Line;
import com.stayhub.booking.*;
import com.stayhub.property.*;
import com.stayhub.user.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentPrimitivesIntegrationTest extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PaymentPrimitivesService service;
    @Autowired LedgerService ledger;
    @Autowired BookingRepository bookings;
    @Autowired PropertyRepository properties;
    @Autowired UserRepository users;
    long booking,guest,host,property;
    final List<Long> otherBookings=new ArrayList<>();
    static final String HASH="a".repeat(64);

    @BeforeEach void fixture() {
        String suffix=UUID.randomUUID().toString();
        var h=users.save(User.builder().email("h110"+suffix+"@example.test").fullName("Host")
                .passwordHash("test").role(UserRole.HOST).status(UserStatus.ACTIVE).build());
        var g=users.save(User.builder().email("g110"+suffix+"@example.test").fullName("Guest")
                .passwordHash("test").role(UserRole.GUEST).status(UserStatus.ACTIVE).build());
        var p=properties.save(Property.builder().host(h).title("Ledger primitive fixture").description("Test")
                .city("Da Lat").address("Test").pricePerNight(bd(1000000)).cleaningFee(BigDecimal.ZERO)
                .maxGuests(2).bedrooms(1).beds(1).bathrooms(1).ratingAvg(BigDecimal.ZERO)
                .propertyType(PropertyType.VILLA).status(PropertyStatus.ACTIVE).build());
        host=h.getId(); guest=g.getId(); property=p.getId();
        booking=newBooking();
    }
    private long newBooking() {
        long id=bookings.save(Booking.builder().property(properties.findById(property).orElseThrow())
                .guest(users.findById(guest).orElseThrow()).checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(6)).guests(1).nightlyPrice(bd(1000000))
                .cleaningFee(BigDecimal.ZERO).serviceFee(BigDecimal.ZERO).totalPrice(bd(1000000))
                // Keep the legacy active property exclusion intact, as in099.
                // Real admission/lifecycle authority is explicitly future102/082.
                .status(BookingStatus.CANCELLED).cancelledAt(Instant.now()).build()).getId();
        otherBookings.add(id); return id;
    }
    @AfterEach void cleanup() {
        // Test database owner teardown only. Production history is never deleted
        // by services. CASCADE reaches new children, not legacy parent bookings.
        jdbc.execute("TRUNCATE booking_financial_snapshots CASCADE");
        for(long id:otherBookings) bookings.deleteById(id);
        properties.deleteById(property); users.deleteById(guest); users.deleteById(host);
    }
    static BigDecimal bd(long value) { return BigDecimal.valueOf(value); }
    private <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactions).execute(s->action.get()); }
    private Instant expiry(long id) {
        return jdbc.queryForObject("SELECT created_at+interval '900 seconds' FROM bookings WHERE id=?",Timestamp.class,id).toInstant();
    }
    private SnapshotInput input(long id,Mode mode,long room,long discount) {
        return new SnapshotInput(id,guest,mode,"VND",bd(room),BigDecimal.ZERO,bd(discount),0,
                "s0-policy-v1","s0-fixtures-v1",HASH,expiry(id));
    }
    private FinancialSnapshot snapshot(Mode mode,long room,long discount) {
        return service.createSnapshot(input(booking,mode,room,discount),"snapshot");
    }
    private Attempt attempt() { return service.allocateAttempt(booking,guest,"sandbox-merchant","test-config-v1",UUID.randomUUID().toString()); }
    private CollectionResult collect(Attempt a,long amount,String reference) {
        return service.recordVerifiedReceipt(new VerifiedReceipt(booking,guest,a.id(),reference,bd(amount),"VND",
                Instant.now().minusSeconds(1),HASH,ReceiptIntent.REQUIRED_COLLECTION),reference);
    }
    private ReserveInput reserveInput(long amount,long policy,long liquid,ReserveKind kind,MoneySource source) {
        return new ReserveInput(booking,guest,kind,source,bd(amount),"VND",bd(policy),bd(liquid),HASH,service.settlementVersion(booking));
    }
    private BigDecimal account(String name) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(l.credit-l.debit),0) FROM ledger_lines l JOIN ledger_journals j ON j.id=l.journal_id
                WHERE j.booking_id=? AND l.account=?
                """,BigDecimal.class,booking,name);
    }
    private static void business(Runnable action,String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PaymentPrimitiveException.class,e->assertThat(e.errorCode()).isEqualTo(code));
    }
    private static void databaseReject(Runnable action) {
        assertThatThrownBy(action::run).satisfies(e->{
            Throwable root=e; while(root.getCause()!=null) root=root.getCause();
            assertThat(root).isInstanceOf(SQLException.class);
            assertThat(((SQLException)root).getSQLState()).isIn("23514","23503","23505");
        });
    }

    @Test void wholeVndAcceptsIntegerWithoutRounding() {
        assertThat(jdbc.queryForObject("SELECT 1000000::ledger_vnd", java.math.BigDecimal.class))
                .isEqualByComparingTo("1000000");
    }

    @Test void fractionalVndIsRejectedBeforeTypmodRounding() {
        databaseReject(() -> tx(() -> jdbc.queryForObject("SELECT 1.001::ledger_vnd", BigDecimal.class)));
    }

    @Test void fullReceiptPostsRealBalancedFrozenJournal() {
        var s=snapshot(Mode.FULL_PREPAY,1000000,0); var a=attempt();
        assertThat(a.expectedAmount()).isEqualByComparingTo(s.dueOnline());
        assertThat(collect(a,1000000,"full").disposition()).isEqualTo("ACCEPTED");
        assertThat(account("ONLINE_CASH")).isEqualByComparingTo("-1000000");
        assertThat(account("COMMISSION_RESERVED")).isEqualByComparingTo("150000");
        assertThat(account("HOST_HELD_PAYABLE")).isEqualByComparingTo("850000");
        assertThat(account("COMMISSION_EARNED")).isZero();
        assertThat(jdbc.queryForObject("SELECT sum(debit-credit) FROM ledger_lines WHERE booking_id=?",BigDecimal.class,booking)).isZero();
    }
    @Test void hybridDirectMemoNeverBecomesPlatformCash() {
        var s=snapshot(Mode.HYBRID_DEPOSIT,1000000,0); collect(attempt(),200000,"hybrid");
        service.recordHostMemo(booking,host,DirectMethod.CASH,"direct",bd(800000),"VND",Instant.now().minusSeconds(1),HASH,"direct");
        assertThat(s.balanceDueHost()).isEqualByComparingTo("800000");
        assertThat(account("ONLINE_CASH")).isEqualByComparingTo("-200000");
        assertThat(account("COMMISSION_RESERVED")).isEqualByComparingTo("150000");
        assertThat(account("HOST_HELD_PAYABLE")).isEqualByComparingTo("50000");
        assertThat(account("MEMO_HOST_DIRECT")).isEqualByComparingTo("-800000");
        databaseReject(()->jdbc.update("""
                INSERT INTO ledger_reservations(booking_id,actor_id,kind,source,amount,currency,policy_bound,liquidity_bound,eligibility_digest)
                VALUES (?,?,'REFUND','ONLINE_VERIFIED',200001,'VND',1000000,1000000,?)
                """,booking,guest,HASH));
    }
    @Test void fundedFullSixPointEightMillionPreservesGrossFundingNetAndHost() {
        var s=snapshot(Mode.FULL_PREPAY,7000000,200000); collect(attempt(),6800000,"funded");
        assertThat(s.netRetention()).isEqualByComparingTo("850000");
        assertThat(account("ONLINE_CASH")).isEqualByComparingTo("-6800000");
        assertThat(account("COMMISSION_RESERVED")).isEqualByComparingTo("1050000");
        assertThat(account("DISCOUNT_FUNDING_EXPENSE")).isEqualByComparingTo("-200000");
        assertThat(account("HOST_HELD_PAYABLE")).isEqualByComparingTo("5950000");
    }
    @Test void fundedHybridExpectedAmountIsOnlineNotTotal() {
        var s=snapshot(Mode.HYBRID_DEPOSIT,7000000,200000); var a=attempt();
        assertThat(a.expectedAmount()).isEqualByComparingTo("1360000");
        assertThat(collect(a,6800000,"wrong-total").disposition()).isEqualTo("RECONCILE");
        assertThat(account("ONLINE_CASH")).isZero();
        assertThat(collect(a,1360000,"exact-online").disposition()).isEqualTo("ACCEPTED");
        assertThat(s.balanceDueHost()).isEqualByComparingTo("5440000");
        assertThat(account("HOST_HELD_PAYABLE")).isEqualByComparingTo("510000");
    }
    @Test void snapshotReplayAndInvalidPoliciesAndFrozenHistory() {
        var i=input(booking,Mode.HYBRID_DEPOSIT,1000000,0);
        assertThat(service.createSnapshot(i,"s")).isEqualTo(service.createSnapshot(i,"s"));
        business(()->service.createSnapshot(input(booking,Mode.FULL_PREPAY,1000000,0),"s"),"ERR_IDEMPOTENCY_CONFLICT");
        databaseReject(()->jdbc.update("UPDATE booking_financial_snapshots SET quote_hash=? WHERE booking_id=?","b".repeat(64),booking));
        databaseReject(()->jdbc.update("DELETE FROM booking_financial_snapshots WHERE booking_id=?",booking));
        databaseReject(()->jdbc.update("UPDATE bookings SET created_at=created_at+interval '1 second' WHERE id=?",booking));
        long b=newBooking();
        business(()->service.createSnapshot(input(b,Mode.FULL_PREPAY,1000000,150001),"bad"),"ERR_DISCOUNT_FUNDING");
        business(()->service.createSnapshot(input(b,Mode.HYBRID_DEPOSIT,1,0),"bad"),"ERR_NONPOSITIVE_BOOKING_AMOUNT");
        business(()->service.createSnapshot(new SnapshotInput(b,guest,Mode.FULL_PREPAY,"USD",bd(1000000),BigDecimal.ZERO,BigDecimal.ZERO,
                0,"s0-policy-v1","s0-fixtures-v1",HASH,expiry(b)),"bad"),"ERR_INVALID_REQUEST");
    }
    @Test void halfUpCommissionAndDepositAreComputedInProduction() {
        var s=snapshot(Mode.HYBRID_DEPOSIT,10,0);
        assertThat(s.grossCommission()).isEqualByComparingTo("2");
        assertThat(s.dueOnline()).isEqualByComparingTo("2");
        assertThat(s.heldHostPayable()).isZero();
        collect(attempt(),2,"rounded");
    }
    @Test void attemptReplayOriginalDeadlineAndCreationFailureCounts() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0);
        var a=service.allocateAttempt(booking,guest,"merchant","config","a");
        assertThat(service.allocateAttempt(booking,guest,"merchant","config","a")).isEqualTo(a);
        business(()->service.allocateAttempt(booking,guest,"changed","config","a"),"ERR_IDEMPOTENCY_CONFLICT");
        service.recordCreationOutcome(booking,guest,a.id(),CreationOutcome.CREATION_FAILED);
        service.recordCreationOutcome(booking,guest,a.id(),CreationOutcome.CREATION_FAILED);
        business(()->service.recordCreationOutcome(booking,guest,a.id(),CreationOutcome.CREATED),"ERR_IDEMPOTENCY_CONFLICT");
        assertThat(attempt().holdExpiresAt()).isEqualTo(a.holdExpiresAt()); attempt();
        business(this::attempt,"ERR_PAYMENT_ATTEMPTS_EXHAUSTED");
        databaseReject(()->jdbc.update("UPDATE payment_attempts SET expected_amount=1 WHERE id=?",a.id()));
    }
    @Test void injectedClockAtExactExpiryRejectsAndLateReceiptIsRetainedOnly() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); var a=attempt();
        var expired=new PaymentPrimitivesService(jdbc,ledger,Clock.fixed(expiry(booking),ZoneOffset.UTC));
        business(()->tx(()->expired.allocateAttempt(booking,guest,"merchant","config","late")),"ERR_HOLD_EXPIRED");
        var late=tx(()->expired.recordVerifiedReceipt(new VerifiedReceipt(booking,guest,a.id(),"late",bd(200000),"VND",
                Instant.now().minusSeconds(1),HASH,ReceiptIntent.REQUIRED_COLLECTION),"late"));
        assertThat(late.disposition()).isEqualTo("RECONCILE");
        assertThat(account("ONLINE_CASH")).isZero();
    }
    @Test void databaseDeadlineCannotBeExtendedByCallerOrInjectedClock() {
        jdbc.update("UPDATE bookings SET created_at=clock_timestamp()-interval '901 seconds' WHERE id=?",booking);
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0);
        var rewind=new PaymentPrimitivesService(jdbc,ledger,Clock.fixed(expiry(booking).minusSeconds(1),ZoneOffset.UTC));
        databaseReject(()->tx(()->rewind.allocateAttempt(booking,guest,"merchant","config","rewind")));
    }
    @Test void duplicateReceiptSameSemanticBodyAndExtraAttemptCannotCreditTwice() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); var a=attempt(); var b=attempt();
        var input=new VerifiedReceipt(booking,guest,a.id(),"receipt",bd(200000),"VND",Instant.now().minusSeconds(1),HASH,ReceiptIntent.REQUIRED_COLLECTION);
        var first=service.recordVerifiedReceipt(input,"one");
        assertThat(service.recordVerifiedReceipt(input,"one")).isEqualTo(first);
        assertThat(service.recordVerifiedReceipt(input,"other-key")).isEqualTo(first);
        business(()->service.recordVerifiedReceipt(new VerifiedReceipt(booking,guest,a.id(),"receipt",bd(200001),"VND",input.occurredAt(),HASH,input.intent()),"one"),"ERR_IDEMPOTENCY_CONFLICT");
        assertThat(collect(b,200000,"extra").disposition()).isEqualTo("RECONCILE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_journals WHERE booking_id=?",Integer.class,booking)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=?",Integer.class,booking)).isEqualTo(2);
    }
    private long rawJournal(String event) {
        return jdbc.queryForObject("""
                INSERT INTO ledger_journals(booking_id,actor_id,event_key,body_hash,currency,source,metadata_digest)
                VALUES (?,?,?,?,'VND','ADJUSTMENT',?) RETURNING id
                """,Long.class,booking,guest,event,HASH,HASH);
    }
    private void rawLine(long id,String account,long debit,long credit) {
        jdbc.update("INSERT INTO ledger_lines(journal_id,booking_id,currency,account,debit,credit) VALUES (?,?,'VND',?,?,?)",
                id,booking,account,debit,credit);
    }
    @Test void deferredDatabaseBalanceRejectsHeaderOnlyUnbalancedAndOneSidedInvalidLines() {
        snapshot(Mode.FULL_PREPAY,1000000,0);
        databaseReject(()->tx(()->rawJournal("empty")));
        databaseReject(()->tx(()->{ long id=rawJournal("unbalanced"); rawLine(id,"ONLINE_CASH",10,0); rawLine(id,"RECOVERY",0,9); return id; }));
        databaseReject(()->tx(()->{ long id=rawJournal("zero"); rawLine(id,"ONLINE_CASH",0,0); return id; }));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_journals WHERE booking_id=?",Integer.class,booking)).isZero();
    }
    @Test void journalImmutabilityIncludesNewLinesDeleteReplaceAndCurrencyScope() {
        snapshot(Mode.FULL_PREPAY,1000000,0);
        long id=ledger.postAdjustment(booking,guest,"adjust","adjust",HASH,List.of(Line.debit(ONLINE_CASH,bd(10)),Line.credit(RECOVERY,bd(10))));
        databaseReject(()->jdbc.update("DELETE FROM ledger_lines WHERE journal_id=?",id));
        databaseReject(()->jdbc.update("DELETE FROM ledger_journals WHERE id=?",id));
        databaseReject(()->jdbc.update("UPDATE ledger_journals SET event_key='replacement' WHERE id=?",id));
        databaseReject(()->rawLine(id,"ONLINE_CASH",1,0));
        databaseReject(()->tx(()->{ long j=rawJournal("currency"); jdbc.update("""
                INSERT INTO ledger_lines(journal_id,booking_id,currency,account,debit,credit) VALUES (?,?,'USD','ONLINE_CASH',1,0)
                """,j,booking); return j; }));
        // Even a balanced arbitrary cash journal cannot forge the collection pool.
        business(()->service.reserve(reserveInput(1,100,100,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED),"forged-pool"),"ERR_REFUND_POOL_EXCEEDED");
    }
    @Test void directMemoDatabaseRejectsCashAccountEvenWhenBalanced() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0);
        databaseReject(()->tx(()->{ long id=jdbc.queryForObject("""
                INSERT INTO ledger_journals(booking_id,actor_id,event_key,body_hash,currency,source,metadata_digest)
                VALUES (?,?,'memo-cash',?,'VND','HOST_ATTESTED',?) RETURNING id
                """,Long.class,booking,host,HASH,HASH);
            rawLine(id,"ONLINE_CASH",1,0); rawLine(id,"RECOVERY",0,1); return id; }));
    }
    @Test void eventIdempotencyConflictsAcrossBodyActorAndBookingAndReversalIsAppendOnly() {
        snapshot(Mode.FULL_PREPAY,1000000,0);
        var lines=List.of(Line.debit(ONLINE_CASH,bd(10)),Line.credit(RECOVERY,bd(10)));
        long id=ledger.postAdjustment(booking,guest,"event","key",HASH,lines);
        assertThat(ledger.postAdjustment(booking,guest,"event","key",HASH,lines)).isEqualTo(id);
        business(()->ledger.postAdjustment(booking,host,"event","other",HASH,lines),"ERR_IDEMPOTENCY_CONFLICT");
        business(()->ledger.postAdjustment(booking,guest,"event","other",HASH,List.of(Line.debit(ONLINE_CASH,bd(11)),Line.credit(RECOVERY,bd(11)))),"ERR_IDEMPOTENCY_CONFLICT");
        long other=newBooking(); service.createSnapshot(input(other,Mode.FULL_PREPAY,1000000,0),"snapshot");
        business(()->ledger.postAdjustment(other,guest,"event","key",HASH,lines),"ERR_IDEMPOTENCY_CONFLICT");
        business(()->ledger.reverse(other,guest,id,"cross-reverse","r",HASH),"ERR_INVALID_REQUEST");
        long inverse=ledger.reverse(booking,guest,id,"reverse","r",HASH);
        assertThat(inverse).isNotEqualTo(id);
        assertThat(account("ONLINE_CASH")).isZero();
        databaseReject(()->tx(()->{long j=jdbc.queryForObject("""
                INSERT INTO ledger_journals(booking_id,actor_id,event_key,body_hash,currency,source,reversal_of,metadata_digest)
                VALUES (?,?,'wrong-inverse',?,'VND','ADJUSTMENT',?,?) RETURNING id
                """,Long.class,booking,guest,HASH,inverse,HASH);
            rawLine(j,"ONLINE_CASH",9,0); rawLine(j,"RECOVERY",0,9); return j;}));
    }
    @Test void sourcePoolsConsumeReleaseAndCritical130000Boundary() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); collect(attempt(),200000,"pool");
        var i=reserveInput(50000,200000,200000,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED);
        long first=service.reserve(i,"r1"); assertThat(service.reserve(i,"r1")).isEqualTo(first);
        service.moveReserve(booking,guest,first,Movement.CONSUME,HASH,"paid-accounting",service.settlementVersion(booking));
        service.reserve(reserveInput(20000,200000,150000,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED),"r2");
        business(()->service.reserve(reserveInput(130001,200000,200000,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED),"over"),"ERR_REFUND_POOL_EXCEEDED");
        long last=service.reserve(reserveInput(130000,200000,130000,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED),"boundary");
        service.moveReserve(booking,guest,last,Movement.RELEASE,HASH,"release",service.settlementVersion(booking));
        service.reserve(reserveInput(130000,200000,130000,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED),"reuse");
        databaseReject(()->jdbc.update("DELETE FROM ledger_reservations WHERE id=?",last));
    }
    @Test void payoutCannotIncludeDirectAndVersionAndPolicyLiquidityRemainSeparate() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); collect(attempt(),200000,"payout-pool");
        business(()->service.reserve(reserveInput(1,800000,800000,ReserveKind.PAYOUT,MoneySource.HOST_ATTESTED),"direct"),"ERR_PAYOUT_NOT_ELIGIBLE");
        business(()->service.reserve(reserveInput(50001,850000,200000,ReserveKind.PAYOUT,MoneySource.ONLINE_VERIFIED),"too-much"),"ERR_PAYOUT_NOT_ELIGIBLE");
        business(()->service.reserve(reserveInput(18001,18000,200000,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED),"policy"),"ERR_REFUND_POOL_EXCEEDED");
        business(()->service.reserve(reserveInput(100,200000,99,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED),"liquid"),"ERR_REFUND_POOL_EXCEEDED");
        var stale=reserveInput(1,200000,200000,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED);
        service.reserve(reserveInput(50000,50000,200000,ReserveKind.PAYOUT,MoneySource.ONLINE_VERIFIED),"held-only");
        business(()->service.reserve(stale,"stale"),"ERR_VERSION_CONFLICT");
    }
    private List<Boolean> race(int workers,java.util.function.IntConsumer task) throws Exception {
        var ready=new CountDownLatch(workers); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(workers)) {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(int i=0;i<workers;i++) { final int n=i; futures.add(executor.submit(()->{
                ready.countDown(); if(!start.await(5,TimeUnit.SECONDS)) throw new AssertionError("race start timeout");
                try { task.accept(n); return true; }
                catch(PaymentPrimitiveException e) { assertThat(e.errorCode()).isIn("ERR_PAYMENT_ATTEMPTS_EXHAUSTED","ERR_VERSION_CONFLICT"); return false; }
                catch(org.springframework.dao.DataIntegrityViolationException e) {
                    assertThat(((SQLException)e.getRootCause()).getSQLState()).isEqualTo("23514"); return false;
                }
            })); }
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Boolean> results=new ArrayList<>(); for(var f:futures) results.add(f.get(5,TimeUnit.SECONDS));
            return results;
        }
    }
    @Test void concurrentAttemptAllocationsHaveExactlyThreeOrdinalsIncludingFirst() throws Exception {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0);
        assertThat(race(6,n->service.allocateAttempt(booking,guest,"merchant","config","race-"+n))).filteredOn(v->v).hasSize(3);
        assertThat(jdbc.queryForList("SELECT ordinal FROM payment_attempts WHERE booking_id=? ORDER BY ordinal",Integer.class,booking)).containsExactly(1,2,3);
    }
    @Test void concurrentReserveSqlCannotOverdrawAcceptedCollectionEvenWithoutServiceVersion() throws Exception {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); collect(attempt(),200000,"race-pool");
        assertThat(race(2,n->jdbc.update("""
                INSERT INTO ledger_reservations(booking_id,actor_id,kind,source,amount,currency,policy_bound,liquidity_bound,eligibility_digest)
                VALUES (?,?,'REFUND','ONLINE_VERIFIED',130000,'VND',200000,200000,?)
                """,booking,guest,HASH))).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM ledger_reservations WHERE booking_id=?",BigDecimal.class,booking)).isEqualByComparingTo("130000");
    }

    @Test void concurrentVerifiedReceiptsRecordBothButOnlyOneAcceptedCredit() throws Exception {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); var a=attempt(); var b=attempt();
        assertThat(race(2,n->collect(n==0?a:b,200000,"concurrent-receipt-"+n))).containsOnly(true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=?",Integer.class,booking)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='ACCEPTED'",Integer.class,booking)).isEqualTo(1);
        assertThat(account("ONLINE_CASH")).isEqualByComparingTo("-200000");
    }

    @Test void observedDatabaseLockWaitRechecksPoolAfterWinningReserveCommits() throws Exception {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); collect(attempt(),200000,"observed-pool");
        var pid=new java.util.concurrent.atomic.AtomicInteger();
        var submitted=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            Future<Boolean> waiter=tx(()->{
                jdbc.queryForObject("SELECT id FROM bookings WHERE id=? FOR UPDATE",Long.class,booking);
                int blocker=jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class);
                Future<Boolean> f=executor.submit(()->{
                    try {
                        return tx(()->{
                            jdbc.execute("SET LOCAL statement_timeout='5s'");
                            pid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class)); submitted.countDown();
                            jdbc.update("""
                                INSERT INTO ledger_reservations(booking_id,actor_id,kind,source,amount,currency,policy_bound,liquidity_bound,eligibility_digest)
                                VALUES (?,?,'REFUND','ONLINE_VERIFIED',130000,'VND',200000,200000,?)
                                """,booking,guest,HASH);
                            return true;
                        });
                    } catch(org.springframework.dao.DataIntegrityViolationException ex) {
                        assertThat(((SQLException)ex.getRootCause()).getSQLState()).isEqualTo("23514"); return false;
                    }
                });
                try { assertThat(submitted.await(5,TimeUnit.SECONDS)).isTrue(); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                boolean observed=false; long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while(System.nanoTime()<end) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    observed=Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock'
                              AND ?=ANY(pg_blocking_pids(pid)))
                            """,Boolean.class,pid.get(),blocker));
                    if(observed) break;
                    try { Thread.sleep(5); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                }
                assertThat(observed).as("PostgreSQL identifies exact waiter and blocker, not timing/latch inference").isTrue();
                service.reserve(reserveInput(130000,200000,200000,ReserveKind.REFUND,MoneySource.ONLINE_VERIFIED),"winner");
                return f;
            });
            assertThat(waiter.get(5,TimeUnit.SECONDS)).isFalse();
        }
    }

    @Test void snapshotWholeMoneyVersionHashDeadlineAndSelfActorCannotBeClaimedByCaller() {
        var i=input(booking,Mode.HYBRID_DEPOSIT,1000000,0);
        business(()->service.createSnapshot(new SnapshotInput(booking,guest,i.mode(),"VND",new BigDecimal("1.001"),BigDecimal.ZERO,
                BigDecimal.ZERO,0,i.policyVersion(),i.fixtureVersion(),HASH,expiry(booking)),"fraction"),"ERR_INVALID_REQUEST");
        business(()->service.createSnapshot(new SnapshotInput(booking,guest,i.mode(),"VND",bd(1000000),BigDecimal.ZERO,
                BigDecimal.ZERO,-1,i.policyVersion(),i.fixtureVersion(),HASH,expiry(booking)),"negative-version"),"ERR_INVALID_REQUEST");
        business(()->service.createSnapshot(new SnapshotInput(booking,guest,i.mode(),"VND",bd(1000000),BigDecimal.ZERO,
                BigDecimal.ZERO,0,"unapproved",i.fixtureVersion(),HASH,expiry(booking)),"version"),"ERR_PAYMENT_CONFIG");
        business(()->service.createSnapshot(new SnapshotInput(booking,guest,i.mode(),"VND",bd(1000000),BigDecimal.ZERO,
                BigDecimal.ZERO,0,i.policyVersion(),i.fixtureVersion(),"NOT-A-HASH",expiry(booking)),"hash"),"ERR_INVALID_REQUEST");
        business(()->service.createSnapshot(new SnapshotInput(booking,guest,i.mode(),"VND",bd(1000000),BigDecimal.ZERO,
                BigDecimal.ZERO,0,i.policyVersion(),i.fixtureVersion(),HASH,expiry(booking).plusSeconds(1)),"extension"),"ERR_PAYMENT_CONFIG");
        jdbc.update("UPDATE properties SET host_id=? WHERE id=?",guest,property);
        business(()->service.createSnapshot(i,"self"),"ERR_SELF_BOOKING");
    }

    @Test void directSqlCannotBypassAttemptCurrencySnapshotBindingOrJournalCollectionAtomicity() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); var a=attempt();
        databaseReject(()->jdbc.update("""
                INSERT INTO payment_attempts(booking_id,actor_id,ordinal,provider_ref,merchant,config_version,expected_amount,currency,hold_expires_at)
                SELECT booking_id,actor_id,2,'bad-expected','merchant','config',total_stay,'VND',hold_expires_at
                FROM booking_financial_snapshots WHERE booking_id=?
                """,booking));
        databaseReject(()->jdbc.update("""
                INSERT INTO payment_collections(booking_id,actor_id,attempt_id,source,method,disposition,receipt_ref,body_hash,amount,currency,occurred_at,evidence_digest)
                VALUES (?,?,?,'ONLINE_VERIFIED','PROVIDER','ACCEPTED','no-journal',?,200000,'VND',clock_timestamp(),?)
                """,booking,guest,a.id(),HASH,HASH));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=?",Integer.class,booking)).isZero();
        databaseReject(()->jdbc.update("UPDATE payment_settlement_locks SET version=0 WHERE booking_id=?",booking));
        databaseReject(()->jdbc.update("DELETE FROM payment_settlement_locks WHERE booking_id=?",booking));
    }

    @Test void journalLineCrossBookingAndWrongReceiptAttemptScopeReject() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0); var a=attempt();
        long other=newBooking(); service.createSnapshot(input(other,Mode.HYBRID_DEPOSIT,1000000,0),"s2");
        business(()->service.recordVerifiedReceipt(new VerifiedReceipt(other,guest,a.id(),"cross",bd(200000),"VND",
                Instant.now().minusSeconds(1),HASH,ReceiptIntent.REQUIRED_COLLECTION),"cross"),"ERR_INVALID_REQUEST");
        databaseReject(()->tx(()->{ long j=rawJournal("cross-line");
            jdbc.update("INSERT INTO ledger_lines(journal_id,booking_id,currency,account,debit,credit) VALUES (?,?,'VND','ONLINE_CASH',1,0)",j,other);
            return j; }));
    }

    @Test void memoReversalAndCorrectionStayInMemoNamespaceAcrossTheChain() {
        snapshot(Mode.HYBRID_DEPOSIT,1000000,0);
        var memo=service.recordHostMemo(booking,host,DirectMethod.BANK_TRANSFER,"memo-chain",bd(300000),"VND",
                Instant.now().minusSeconds(1),HASH,"memo-chain");
        long original=jdbc.queryForObject("SELECT id FROM ledger_journals WHERE collection_id=?",Long.class,memo.id());
        long reversal=ledger.reverse(booking,host,original,"memo-reverse","r1",HASH);
        assertThat(account("MEMO_HOST_DIRECT")).isZero();
        ledger.reverse(booking,host,reversal,"memo-correct-reversal","r2",HASH);
        assertThat(account("MEMO_HOST_DIRECT")).isEqualByComparingTo("-300000");
        assertThat(account("ONLINE_CASH")).isZero();
    }

    @Test void foreignConstructionXidCannotAuthorizeAnotherConnectionToAppendPostedLines() throws Exception {
        snapshot(Mode.FULL_PREPAY,1000000,0);
        var dataSource=Objects.requireNonNull(jdbc.getDataSource());
        // Keep B's transaction open throughout A's construction and commit. Its
        // real XID is supplied maliciously, not guessed or mocked by the test.
        try(var connectionB=dataSource.getConnection(); var connectionA=dataSource.getConnection()) {
            connectionB.setAutoCommit(false);
            connectionA.setAutoCommit(false);
            try {
                long xidB;
                try(var statement=connectionB.createStatement(); var row=statement.executeQuery("SELECT txid_current()")) {
                    assertThat(row.next()).isTrue(); xidB=row.getLong(1);
                }
                long xidA;
                try(var statement=connectionA.createStatement(); var row=statement.executeQuery("SELECT txid_current()")) {
                    assertThat(row.next()).isTrue(); xidA=row.getLong(1);
                }
                assertThat(xidA).isNotEqualTo(xidB);
                long journal;
                try(var statement=connectionA.prepareStatement("""
                        INSERT INTO ledger_journals(booking_id,actor_id,event_key,body_hash,currency,source,metadata_digest,construction_xid)
                        VALUES (?,?,'foreign-xid',?,'VND','ADJUSTMENT',?,?) RETURNING id,construction_xid
                        """)) {
                    statement.setLong(1,booking); statement.setLong(2,guest);
                    statement.setString(3,HASH); statement.setString(4,HASH); statement.setLong(5,xidB);
                    try(var row=statement.executeQuery()) {
                        assertThat(row.next()).isTrue(); journal=row.getLong(1);
                        assertThat(row.getLong(2)).as("DB must stamp actual constructing A, ignoring caller's B XID").isEqualTo(xidA);
                    }
                }
                // The actual constructing transaction must still be able to
                // insert a balanced original journal and pass deferred checks.
                try(var statement=connectionA.prepareStatement("""
                        INSERT INTO ledger_lines(journal_id,booking_id,currency,account,debit,credit)
                        VALUES (?,?,'VND','ONLINE_CASH',10,0),(?,?,'VND','RECOVERY',0,10)
                        """)) {
                    statement.setLong(1,journal); statement.setLong(2,booking);
                    statement.setLong(3,journal); statement.setLong(4,booking);
                    assertThat(statement.executeUpdate()).isEqualTo(2);
                }
                connectionA.commit();
                var originalHeader=jdbc.queryForMap("SELECT * FROM ledger_journals WHERE id=?",journal);
                var originalLines=jdbc.queryForList("SELECT * FROM ledger_lines WHERE journal_id=? ORDER BY id",journal);
                // Both rows together would preserve debit=credit; rejection must
                // be the construction boundary, not a balance/deferred failure.
                try(var statement=connectionB.prepareStatement("""
                        INSERT INTO ledger_lines(journal_id,booking_id,currency,account,debit,credit)
                        VALUES (?,?,'VND','ONLINE_CASH',1,0),(?,?,'VND','RECOVERY',0,1)
                        """)) {
                    statement.setLong(1,journal); statement.setLong(2,booking);
                    statement.setLong(3,journal); statement.setLong(4,booking);
                    assertThatThrownBy(statement::executeUpdate).isInstanceOfSatisfying(SQLException.class,e->{
                        assertThat(e.getSQLState()).isEqualTo("23514");
                        assertThat(e.getMessage()).contains("posted journal closed to new lines");
                    });
                }
                connectionB.rollback();
                assertThat(jdbc.queryForMap("SELECT * FROM ledger_journals WHERE id=?",journal)).isEqualTo(originalHeader);
                assertThat(jdbc.queryForList("SELECT * FROM ledger_lines WHERE journal_id=? ORDER BY id",journal)).isEqualTo(originalLines);
                assertThat(originalLines).hasSize(2);
                assertThat(account("ONLINE_CASH")).isEqualByComparingTo("-10");
                assertThat(account("RECOVERY")).isEqualByComparingTo("10");
            } finally {
                connectionA.rollback(); connectionB.rollback();
            }
        }
    }
}
