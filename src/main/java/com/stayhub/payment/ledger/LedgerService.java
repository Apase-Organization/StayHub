package com.stayhub.payment.ledger;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trusted internal append-only accounting. Not an authorization or cash-pool API.
 * Caller composes lifecycle changes in the same transaction; no bank/provider calls.
 */
@Service
public class LedgerService {
    public enum Source { ONLINE_VERIFIED, HOST_ATTESTED, ADJUSTMENT }
    public enum Account {
        ONLINE_CASH, COMMISSION_DUE, COMMISSION_RESERVED, COMMISSION_EARNED,
        HOST_HELD_PAYABLE, DISCOUNT_FUNDING_EXPENSE, PROVIDER_COST, TAX_LIABILITY,
        REFUND_RESERVED, PAYOUT_RESERVED, RECOVERY, MEMO_HOST_DIRECT, MEMO_GUEST_DIRECT
    }
    public record Line(Account account, BigDecimal debit, BigDecimal credit) {
        public Line {
            Objects.requireNonNull(account);
            debit = money(debit); credit = money(credit);
            require((debit.signum()>0 && credit.signum()==0) || (credit.signum()>0 && debit.signum()==0), "ERR_INVALID_REQUEST");
        }
        public static Line debit(Account a, BigDecimal v) { return new Line(a,v,BigDecimal.ZERO); }
        public static Line credit(Account a, BigDecimal v) { return new Line(a,BigDecimal.ZERO,v); }
    }
    private static final JsonMapper CANONICAL = JsonMapper.builder().findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final JdbcTemplate jdbc;
    public LedgerService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    static void require(boolean ok, String code) { if (!ok) throw new PaymentPrimitiveException(code); }
    static void text(String value) { require(value!=null && !value.isBlank() && value.length()<=200,"ERR_INVALID_REQUEST"); }
    static void hashFormat(String value) { require(value!=null && value.matches("[a-f0-9]{64}"),"ERR_INVALID_REQUEST"); }
    static BigDecimal money(BigDecimal value) {
        require(value!=null && value.signum()>=0 && value.compareTo(new BigDecimal("9999999999999999999"))<=0,"ERR_INVALID_REQUEST");
        try { return value.setScale(0,java.math.RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new PaymentPrimitiveException("ERR_INVALID_REQUEST"); }
    }
    static String digest(Object semanticBody) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(CANONICAL.writeValueAsString(semanticBody).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Cannot canonicalize internal financial command",ex); }
    }
    void lock(long booking) {
        require(booking>0,"ERR_INVALID_REQUEST");
        require(!jdbc.queryForList("SELECT id FROM bookings WHERE id=? FOR UPDATE",Long.class,booking).isEmpty(),"ERR_NOT_FOUND");
    }
    Long replay(long actor, String operation, long booking, String key, String hash) {
        require(actor>0,"ERR_INVALID_REQUEST"); text(key);
        var rows=jdbc.query("""
                SELECT body_hash,result_id FROM payment_primitive_keys
                WHERE actor_id=? AND operation=? AND booking_id=? AND key=?
                """,(r,n)->Map.entry(r.getString(1),r.getLong(2)),actor,operation,booking,key);
        if (rows.isEmpty()) return null;
        require(rows.getFirst().getKey().equals(hash),"ERR_IDEMPOTENCY_CONFLICT");
        return rows.getFirst().getValue();
    }
    void remember(long actor,String operation,long booking,String key,String hash,long result) {
        jdbc.update("INSERT INTO payment_primitive_keys(actor_id,operation,booking_id,key,body_hash,result_id) VALUES (?,?,?,?,?,?)",
                actor,operation,booking,key,hash,result);
    }

    /** Explicit adjustment; metadata digest binds reviewed evidence, not permission
     * to recognize earnings. This never increases reserve availability. */
    @Transactional
    public long postAdjustment(long booking,long actor,String event,String key,String metadataDigest,List<Line> lines) {
        return post(booking,actor,event,key,Source.ADJUSTMENT,null,null,metadataDigest,lines);
    }

    @Transactional
    public long reverse(long booking,long actor,long original,String event,String key,String metadataDigest) {
        lock(booking);
        var headers=jdbc.queryForList("SELECT id FROM ledger_journals WHERE id=? AND booking_id=?",Long.class,original,booking);
        require(!headers.isEmpty(),"ERR_INVALID_REQUEST");
        var inverse=jdbc.query("SELECT account,credit,debit FROM ledger_lines WHERE journal_id=? ORDER BY id",
                (r,n)->new Line(Account.valueOf(r.getString(1)),r.getBigDecimal(2),r.getBigDecimal(3)),original);
        return post(booking,actor,event,key,Source.ADJUSTMENT,null,original,metadataDigest,inverse);
    }

    // Package-private receipt poster: only the trusted primitive service supplies
    // collection bindings; arbitrary public adjustment cannot impersonate it.
    long post(long booking,long actor,String event,String key,Source source,Long collection,Long reversal,
              String metadataDigest,List<Line> supplied) {
        lock(booking); text(event); hashFormat(metadataDigest); Objects.requireNonNull(source);
        require(supplied!=null && supplied.size()>=2,"ERR_INVALID_REQUEST");
        var lines=supplied.stream().sorted(Comparator.comparing((Line l)->l.account().name())
                .thenComparing((Line l)->l.debit()).thenComparing((Line l)->l.credit())).toList();
        var debit=lines.stream().map(Line::debit).reduce(BigDecimal.ZERO,BigDecimal::add);
        var credit=lines.stream().map(Line::credit).reduce(BigDecimal.ZERO,BigDecimal::add);
        require(debit.compareTo(credit)==0,"ERR_INVALID_REQUEST");
        String hash=digest(java.util.Arrays.asList(booking,actor,event,source,collection,reversal,metadataDigest,lines));
        Long existing=replay(actor,"JOURNAL",booking,key,hash);
        if(existing!=null) return existing;
        // Global event lock also handles a conflicting event in another booking.
        // Sorted command body hash is checked after ON CONFLICT, never silently
        // accepting the other actor/resource/payload as a successful replay.
        Long id=jdbc.query("""
                INSERT INTO ledger_journals(booking_id,actor_id,event_key,body_hash,currency,source,collection_id,reversal_of,metadata_digest)
                VALUES (?,?,?,?,'VND',?,?,?,?) ON CONFLICT (event_key) DO NOTHING RETURNING id
                """,(r,n)->r.getLong(1),booking,actor,event,hash,source.name(),collection,reversal,metadataDigest)
                .stream().findFirst().orElse(null);
        if(id==null) {
            var other=jdbc.queryForMap("SELECT id,body_hash FROM ledger_journals WHERE event_key=?",event);
            require(hash.equals(other.get("body_hash")),"ERR_IDEMPOTENCY_CONFLICT");
            id=((Number)other.get("id")).longValue();
        } else {
            for(Line line:lines) jdbc.update("""
                    INSERT INTO ledger_lines(journal_id,booking_id,currency,account,debit,credit) VALUES (?,?,'VND',?,?,?)
                    """,id,booking,line.account().name(),line.debit(),line.credit());
        }
        remember(actor,"JOURNAL",booking,key,hash,id);
        return id;
    }
}
