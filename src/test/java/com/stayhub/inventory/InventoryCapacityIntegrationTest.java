package com.stayhub.inventory;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Isolated new-inventory proof, NOT runtime multi-room checkout/cutover evidence. */
class InventoryCapacityIntegrationTest {
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate db;
    static TransactionTemplate tx;
    static InventoryCapacityService capacity;
    static long host;
    long property, type, plan, booking, item;
    static final LocalDate DAY = LocalDate.of(2030, 12, 10);

    @BeforeAll static void start() {
        PG.start();
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        db = new JdbcTemplate(ds);
        var manager = new DataSourceTransactionManager(ds);
        tx = new TransactionTemplate(manager);
        var proxy = new org.springframework.aop.framework.ProxyFactory(new InventoryCapacityService(db));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        capacity = (InventoryCapacityService) proxy.getProxy();
        host = db.queryForObject("INSERT INTO users(email,password_hash,full_name,role,status) VALUES ('inventory099@test.local','hash','Inventory099','HOST','ACTIVE') RETURNING id", Long.class);
    }
    @AfterAll static void stop() { PG.stop(); }

    @BeforeEach void seed() {
        property = db.queryForObject("INSERT INTO properties(host_id,title,description,address,city,price_per_night,max_guests,bedrooms,beds,bathrooms,property_type,sale_mode) VALUES (?, '099','099','099','099',1000000,4,1,1,1,'HOUSE','ROOM_BASED') RETURNING id", Long.class, host);
        type = db.queryForObject("INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants) VALUES (?,'ROOM_BASED','Deluxe',1,2,4) RETURNING id", Long.class, property);
        plan = db.queryForObject("INSERT INTO rate_plans(property_id,room_type_id,name,policy_version) VALUES (?,?,'Standard','s0-policy-v1') RETURNING id", Long.class, property,type);
        booking = booking("CANCELLED");
        item = item(booking, plan);
        db.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?, ?, 1)",type,DAY);
    }
    long booking(String status) {
        return db.queryForObject("INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,service_fee,subtotal_price,total_price,status,cancelled_at) VALUES (?,?,?,? ,1,1000000,0,0,1000000,1000000,?,CASE WHEN ?='CANCELLED' THEN now() END) RETURNING id",Long.class,property,host,DAY,DAY.plusDays(1),status,status);
    }
    long item(long b, long p) {
        return tx.execute(s -> {
            long i = db.queryForObject("INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,type_plan_policy_snapshot) VALUES (?,?,?,?,1,'{}') RETURNING id",Long.class,b,property,type,p);
            db.update("INSERT INTO room_allocations(item_id,room_ordinal,adults,children_ages) VALUES (?,1,1,'[0,17]')",i);
            db.update("INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,policy_snapshot) SELECT ?,1,d::date,1000000,'{}' FROM bookings b CROSS JOIN LATERAL generate_series(b.check_in_date::timestamp,(b.check_out_date-1)::timestamp,interval '1 day') d WHERE b.id=?",i,b);
            return i;
        });
    }
    void hold(long i) {
        db.update("INSERT INTO inventory_commitments(item_id,room_type_id,stay_date,quantity,state,expires_at) VALUES (?,?,?,1,'HELD',clock_timestamp()+interval '15 minutes')",i,type,DAY);
    }
    @Test void additiveSchemaAndLegacyExclusionRemain() {
        assertThat(db.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename IN ('room_types','rate_plans','type_day_inventory','booking_items','room_allocations','nightly_snapshots','inventory_commitments')",Integer.class)).isEqualTo(7);
        booking("PENDING_PAYMENT");
        assertThatThrownBy(() -> booking("CONFIRMED")).hasMessageContaining("uq_bookings_active_date_overlap");
        assertThat(db.queryForObject("SELECT count(*) FROM properties WHERE sale_mode IS NULL",Integer.class)).isNotNull();
    }
    @Test void holdConversionReleaseAndStopSale() {
        hold(item);
        db.update("UPDATE type_day_inventory SET stop_sale=true WHERE room_type_id=?",type);
        db.update("UPDATE inventory_commitments SET state='CONFIRMED' WHERE item_id=?",item);
        assertThatThrownBy(() -> db.update("UPDATE type_day_inventory SET out_of_order=1 WHERE room_type_id=?",type)).hasMessageContaining("capacity");
        db.update("UPDATE inventory_commitments SET state='RELEASED' WHERE item_id=?",item);
        assertThatThrownBy(() -> db.update("UPDATE inventory_commitments SET state='HELD' WHERE item_id=?",item)).hasMessageContaining("transition");
        long second = item(booking("CANCELLED"),plan);
        assertThatThrownBy(() -> hold(second)).hasMessageContaining("stop_sale");
    }
    @Test void expiredHoldCountsUntilReleasedAndCannotConfirm() {
        db.update("INSERT INTO inventory_commitments VALUES (?,?,?,1,'HELD',clock_timestamp()-interval '1 second')",item,type,DAY);
        long second = item(booking("CANCELLED"),plan);
        assertThatThrownBy(() -> hold(second)).hasMessageContaining("capacity");
        assertThatThrownBy(() -> db.update("UPDATE inventory_commitments SET state='CONFIRMED' WHERE item_id=?",item)).hasMessageContaining("expired");
        db.update("UPDATE inventory_commitments SET state='RELEASED' WHERE item_id=?",item);
        hold(second);
    }
    @Test void lastUnitAcrossSharedPlansReadCommitted() throws Exception {
        long otherPlan = db.queryForObject("INSERT INTO rate_plans(property_id,room_type_id,name,policy_version) VALUES (?,?,'Other','s0-policy-v1') RETURNING id",Long.class,property,type);
        long second = item(booking("CANCELLED"),otherPlan);
        var gate = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> a = () -> compete(item,gate);
            Callable<Boolean> b = () -> compete(second,gate);
            var one = pool.submit(a); var two = pool.submit(b);
            assertThat(java.util.List.of(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(db.queryForObject("SELECT sum(quantity) FROM inventory_commitments WHERE room_type_id=? AND state IN ('HELD','CONFIRMED')",Integer.class,type)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    boolean compete(long i, CyclicBarrier gate) {
        try {
            return tx.execute(s -> {
                assertThat(db.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("read committed");
                try { gate.await(2,TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException(e); }
                hold(i); return true;
            });
        } catch (org.springframework.dao.DataIntegrityViolationException e) { return false; }
    }
    @Test void missingCalendarFailsClosedAndMultiItemRollsBack() {
        long second = item(booking("CANCELLED"),plan);
        assertThatThrownBy(() -> tx.execute(s -> { hold(item); hold(second); return null; })).hasMessageContaining("capacity");
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_commitments WHERE room_type_id=?",Integer.class,type)).isZero();
        db.update("DELETE FROM type_day_inventory WHERE room_type_id=?",type);
        assertThatThrownBy(() -> hold(item)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void allocationDatesMoneyAndScopedReferencesRejectInvalidWrites() {
        assertThatThrownBy(() -> db.update("UPDATE room_allocations SET children_ages='[1.5]' WHERE item_id=?",item)).hasMessageContaining("children");
        assertThatThrownBy(() -> db.update("UPDATE room_allocations SET adults=0 WHERE item_id=?",item)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE nightly_snapshots SET room_charge=1.1 WHERE item_id=?",item)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE nightly_snapshots SET stay_date=? WHERE item_id=?",DAY.plusDays(1),item)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tx.execute(s -> { db.update("DELETE FROM room_allocations WHERE item_id=?",item); return null; })).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThatThrownBy(() -> db.update("UPDATE booking_items SET property_id=property_id+99999 WHERE id=?",item)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void wholePlaceRequiresExplicitModeOneTypeAndOneUnit() {
        assertThatThrownBy(() -> db.update("UPDATE properties SET sale_mode='WHOLE_PLACE' WHERE id=?",property)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        long p = db.queryForObject("INSERT INTO properties(host_id,title,description,address,city,price_per_night,max_guests,bedrooms,beds,bathrooms,property_type,sale_mode) VALUES (?,'whole','whole','whole','whole',1000000,4,4,4,1,'HOUSE','WHOLE_PLACE') RETURNING id",Long.class,host);
        long t = db.queryForObject("INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants) VALUES (?,'WHOLE_PLACE','Whole',1,4,4) RETURNING id",Long.class,p);
        db.update("UPDATE room_types SET status='ARCHIVED' WHERE id=?",t);
        assertThatThrownBy(() -> db.update("INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants) VALUES (?,'WHOLE_PLACE','Second',1,4,4)",p)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        db.update("UPDATE room_types SET status='ACTIVE',max_adults=2,max_occupants=3 WHERE id=?",t);
        assertThat(db.queryForObject("SELECT status FROM room_types WHERE id=?",String.class,t)).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> db.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?,?,2)",t,DAY)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void servicePrimitivesConvertAndReleaseSameRowsWithVersionGuard() {
        capacity.holdAll(booking,java.time.Instant.now().plusSeconds(900));
        long version = db.queryForObject("SELECT version FROM type_day_inventory WHERE room_type_id=?",Long.class,type);
        capacity.updateDay(type,DAY,1,0,true,version);
        assertThatThrownBy(() -> capacity.updateDay(type,DAY,1,0,false,version)).hasMessageContaining("version conflict");
        capacity.confirmAll(booking);
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_commitments WHERE item_id=? AND state='CONFIRMED'",Integer.class,item)).isEqualTo(1);
        capacity.releaseAll(booking);
        capacity.releaseAll(booking);
        assertThatThrownBy(() -> capacity.confirmAll(booking)).hasMessageContaining("all-item active hold");
    }
    @Test void serviceRollsBackAllItemsAndMissingDayFailsClosed() {
        item(booking,plan);
        assertThatThrownBy(() -> capacity.holdAll(booking,java.time.Instant.now().plusSeconds(900))).hasMessageContaining("capacity");
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_commitments WHERE room_type_id=?",Integer.class,type)).isZero();
        db.update("DELETE FROM type_day_inventory WHERE room_type_id=?",type);
        assertThatThrownBy(() -> capacity.holdAll(booking,java.time.Instant.now().plusSeconds(900))).isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
    }
    @Test void directSqlCannotInsertConfirmedDeleteOrExtendCommitment() {
        assertThatThrownBy(() -> db.update("INSERT INTO inventory_commitments VALUES (?,?,?,1,'CONFIRMED',now()+interval '15 minutes')",item,type,DAY)).hasMessageContaining("must be HELD");
        hold(item);
        assertThatThrownBy(() -> db.update("DELETE FROM inventory_commitments WHERE item_id=?",item)).hasMessageContaining("not delete");
        assertThatThrownBy(() -> db.update("UPDATE inventory_commitments SET expires_at=expires_at+interval '1 second' WHERE item_id=?",item)).hasMessageContaining("deadline");
    }
    @Test void deferredShapeAndWholeVndDomainAreActuallyEnforced() {
        for (String invalid : java.util.List.of("[18]","[-1]","[null]","[true]","{}","[1.0]")) {
            assertThatThrownBy(() -> db.update("UPDATE room_allocations SET children_ages=?::jsonb WHERE item_id=?",invalid,item)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        for (String invalid : java.util.List.of("1.001","-1","10000000000000000000")) {
            assertThatThrownBy(() -> db.queryForObject("SELECT ?::numeric::inventory_vnd",java.math.BigDecimal.class,new java.math.BigDecimal(invalid))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> tx.execute(s -> { db.update("DELETE FROM nightly_snapshots WHERE item_id=?",item); return null; })).hasMessageContaining("snapshot delete");
        assertThatThrownBy(() -> tx.execute(s -> { db.update("UPDATE room_allocations SET room_ordinal=2 WHERE item_id=?",item); return null; })).hasMessageContaining("allocation key");
        assertThatThrownBy(() -> db.update("UPDATE bookings SET check_out_date=check_out_date+1 WHERE id=?",booking)).hasMessageContaining("immutable inventory booking range");
    }
    @Test void capacityReductionSerializesAgainstUncommittedHold() throws Exception {
        assertThat(blockedRace(() -> hold(item),
                () -> db.update("UPDATE type_day_inventory SET out_of_order=1 WHERE room_type_id=?",type)))
                .contains("capacity");
    }
    @Test void capacityReductionWinsFirstAndAdmissionWaitsThenRejects() throws Exception {
        assertThat(blockedRace(
                () -> db.update("UPDATE type_day_inventory SET out_of_order=1 WHERE room_type_id=?",type),
                () -> hold(item))).contains("capacity");
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_commitments WHERE item_id=?",Integer.class,item)).isZero();
    }

    /** The first transaction commits ONLY after PostgreSQL reports the second
     * backend waiting on a lock held by that exact first backend. No sleep-based
     * inference from a submitted task or from elapsed time. */
    String blockedRace(Runnable first, Runnable second) throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        var pid = new CompletableFuture<Integer>();
        var result = new java.util.concurrent.atomic.AtomicReference<Future<String>>();
        try {
            tx.execute(s -> {
                int blocker = db.queryForObject("SELECT pg_backend_pid()",Integer.class);
                first.run();
                result.set(pool.submit(() -> {
                    try {
                        tx.execute(inner -> {
                            db.execute("SET LOCAL statement_timeout='5s'");
                            pid.complete(db.queryForObject("SELECT pg_backend_pid()",Integer.class));
                            second.run(); return null;
                        });
                        return "COMMITTED";
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        return e.getMostSpecificCause().getMessage();
                    }
                }));
                try { awaitBlocked(pid.get(2,TimeUnit.SECONDS),blocker); }
                catch (Exception e) { throw new IllegalStateException(e); }
                return null;
            });
            return result.get().get(6,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(6,TimeUnit.SECONDS)).isTrue(); }
    }
    void awaitBlocked(int waiter, int blocker) throws InterruptedException {
        long end = System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        do {
            db.execute("SELECT pg_stat_clear_snapshot()");
            Boolean blocked = db.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                    WHERE pid=? AND wait_event_type='Lock'
                    AND ? = ANY(pg_blocking_pids(pid)))
                    """,Boolean.class,waiter,blocker);
            if (Boolean.TRUE.equals(blocked)) return;
            Thread.sleep(5);
        } while (System.nanoTime()<end);
        throw new AssertionError("PostgreSQL did not observe backend "+waiter+" blocked by "+blocker);
    }

    void archive(boolean typeArchive) {
        db.update(typeArchive ? "UPDATE room_types SET status='ARCHIVED' WHERE id=?"
                : "UPDATE rate_plans SET status='ARCHIVED' WHERE id=?",typeArchive ? type : plan);
    }
    void admission(boolean service) {
        if (service) capacity.holdAll(booking,java.time.Instant.now().plusSeconds(900));
        else hold(item);
    }
    @ParameterizedTest
    @CsvSource({"true,true","true,false","false,true","false,false"})
    void archiveWinsAndNewAdmissionWaitsThenRejects(boolean typeArchive, boolean service) throws Exception {
        assertThat(blockedRace(() -> archive(typeArchive),() -> admission(service))).contains("archived");
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_commitments WHERE item_id=?",Integer.class,item)).isZero();
    }
    @ParameterizedTest
    @CsvSource({"true,true","true,false","false,true","false,false"})
    void admissionWinsAndArchiveWaitsExistingConversionAndReleaseRemainAllowed(boolean typeArchive, boolean service) throws Exception {
        assertThat(blockedRace(() -> admission(service),() -> archive(typeArchive))).isEqualTo("COMMITTED");
        capacity.confirmAll(booking);
        capacity.releaseAll(booking);
        assertThat(db.queryForObject("SELECT state FROM inventory_commitments WHERE item_id=?",String.class,item)).isEqualTo("RELEASED");
    }
    @Test void confirmedSnapshotCannotBeDeletedAndReplacedAtSameKey() {
        capacity.holdAll(booking,java.time.Instant.now().plusSeconds(900));
        capacity.confirmAll(booking);
        assertThatThrownBy(() -> tx.execute(s -> {
            db.update("DELETE FROM nightly_snapshots WHERE item_id=?",item);
            db.update("INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,policy_snapshot) VALUES (?,1,?,1,'{}')",item,DAY);
            return null;
        })).hasMessageContaining("snapshot delete");
        assertThat(db.queryForObject("SELECT room_charge FROM nightly_snapshots WHERE item_id=?",java.math.BigDecimal.class,item)).isEqualByComparingTo("1000000");
    }
    @Test void snapshotConstructionRollbackNeedsNoDelete() {
        long b = booking("CANCELLED");
        tx.execute(s -> { item(b,plan); s.setRollbackOnly(); return null; });
        assertThat(db.queryForObject("SELECT count(*) FROM booking_items WHERE booking_id=?",Integer.class,b)).isZero();
    }
    @Test void hostCanChangeCurrentOccupancyAndQuantityWithoutRewritingBookedRights() {
        capacity.holdAll(booking,java.time.Instant.now().plusSeconds(900));
        db.update("UPDATE room_types SET max_adults=1,max_occupants=1,base_quantity=2 WHERE id=?",type);
        assertThat(db.queryForObject("SELECT max_occupants_snapshot FROM booking_items WHERE id=?",Integer.class,item)).isEqualTo(4);
        capacity.confirmAll(booking);
        db.update("UPDATE room_allocations SET adults=1 WHERE item_id=?",item);
        capacity.releaseAll(booking);
        // Old booking allocations remain valid against captured rights, but a new
        // admission must meet the current capacity, even for a prebuilt selection.
        long b = booking("CANCELLED");
        db.update("UPDATE room_types SET max_occupants=4 WHERE id=?",type);
        long i = item(b,plan);
        db.update("UPDATE room_types SET max_occupants=1 WHERE id=?",type);
        assertThatThrownBy(() -> hold(i)).hasMessageContaining("current occupancy");
        db.update("UPDATE room_types SET max_adults=3,max_occupants=5 WHERE id=?",type);
        hold(i);
        assertThat(db.queryForObject("SELECT max_occupants_snapshot FROM booking_items WHERE id=?",Integer.class,i)).isEqualTo(4);
    }
    @Test void occupancyReductionWinsAdmissionWaitsThenRejects() throws Exception {
        assertThat(blockedRace(
                () -> db.update("UPDATE room_types SET max_adults=1,max_occupants=1 WHERE id=?",type),
                () -> admission(true))).contains("current occupancy");
    }
    @Test void admissionWinsOccupancyReductionWaitsAndPreservesExistingHold() throws Exception {
        assertThat(blockedRace(() -> admission(false),
                () -> db.update("UPDATE room_types SET max_adults=1,max_occupants=1 WHERE id=?",type)))
                .isEqualTo("COMMITTED");
        capacity.confirmAll(booking);
        capacity.releaseAll(booking);
    }
    @Test void freshSelectionCanUseReducedHostOccupancyLimit() {
        db.update("UPDATE room_types SET max_adults=1,max_occupants=1 WHERE id=?",type);
        long b = booking("CANCELLED");
        long i = tx.execute(s -> {
            long id = db.queryForObject("INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,type_plan_policy_snapshot) VALUES (?,?,?,?,1,'{}') RETURNING id",Long.class,b,property,type,plan);
            db.update("INSERT INTO room_allocations VALUES (?,1,1,'[]')",id);
            db.update("INSERT INTO nightly_snapshots(item_id,room_ordinal,stay_date,room_charge,policy_snapshot) VALUES (?,1,?,1000000,'{}')",id,DAY);
            return id;
        });
        capacity.holdAll(b,java.time.Instant.now().plusSeconds(900));
        assertThat(db.queryForObject("SELECT max_occupants_snapshot FROM booking_items WHERE id=?",Integer.class,i)).isEqualTo(1);
    }
    @Test void compositeForeignKeysRejectCrossPropertyTypeAndPlanOnInsert() {
        long other = db.queryForObject("INSERT INTO properties(host_id,title,description,address,city,price_per_night,max_guests,bedrooms,beds,bathrooms,property_type) VALUES (?,'legacy','legacy','legacy','legacy',1,1,1,1,1,'HOUSE') RETURNING id",Long.class,host);
        assertThat(db.queryForObject("SELECT sale_mode FROM properties WHERE id=?",String.class,other)).isNull();
        assertThatThrownBy(() -> db.update("INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants) VALUES (?,'ROOM_BASED','Implicit',1,1,1)",other)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO rate_plans(property_id,room_type_id,name,policy_version) VALUES (?,?,'Wrong','v1')",other,type)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,type_plan_policy_snapshot) VALUES (?,?,?,?,1,'{}')",booking,other,type,plan)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO booking_items(booking_id,property_id,room_type_id,rate_plan_id,quantity,type_plan_policy_snapshot) VALUES (?,?,?,?,1,'{}')",booking,property,type,plan+99999)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void allNightsFailAtomicallyThenConvertWithoutDoubleCounting() {
        long b = booking("CANCELLED");
        db.update("UPDATE bookings SET check_out_date=check_out_date+1 WHERE id=?",b);
        long i = item(b,plan);
        db.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?,?,0)",type,DAY.plusDays(1));
        assertThatThrownBy(() -> capacity.holdAll(b,java.time.Instant.now().plusSeconds(900))).hasMessageContaining("capacity");
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_commitments WHERE item_id=?",Integer.class,i)).isZero();
        db.update("UPDATE type_day_inventory SET total=1 WHERE room_type_id=?",type);
        capacity.holdAll(b,java.time.Instant.now().plusSeconds(900));
        capacity.confirmAll(b);
        assertThat(db.queryForObject("SELECT count(*) FROM inventory_commitments WHERE item_id=? AND state='CONFIRMED'",Integer.class,i)).isEqualTo(2);
        assertThatThrownBy(() -> hold(item)).hasMessageContaining("capacity");
    }
    @Test void archivedPlanStopsNewHoldButDoesNotBlockExistingConversion() {
        capacity.holdAll(booking,java.time.Instant.now().plusSeconds(900));
        db.update("UPDATE rate_plans SET status='ARCHIVED' WHERE id=?",plan);
        capacity.confirmAll(booking);
        capacity.releaseAll(booking);
        long second = item(booking("CANCELLED"),plan);
        assertThatThrownBy(() -> hold(second)).hasMessageContaining("archived");
    }
}
