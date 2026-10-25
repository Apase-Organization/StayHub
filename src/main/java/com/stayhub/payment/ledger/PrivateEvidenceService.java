package com.stayhub.payment.ledger;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evidence bytes live in a private DB object, not in the public image/upload namespace. */
@Service
public class PrivateEvidenceService {
    public enum Purpose { DIRECT_RECEIPT, ARRIVAL, REFUND, PAYOUT, DISPUTE }
    public record ObjectView(long id,long bookingId,String purpose,String sha256,String mediaType,long size) {}
    public record Download(String filename,String mediaType,byte[] content) {}
    private static final int MAX_BYTES=5*1024*1024;
    private final JdbcTemplate jdbc;

    public PrivateEvidenceService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional
    public ObjectView store(long booking,long actor,Purpose purpose,String filename,byte[] bytes) {
        Objects.requireNonNull(purpose);
        require(bytes!=null&&bytes.length>0&&bytes.length<=MAX_BYTES,"ERR_EVIDENCE_SIZE");
        String mediaType=sniff(bytes);
        require(filename!=null&&!filename.isBlank(),"ERR_INVALID_REQUEST");
        String safe=filename.replaceAll("[\\p{Cntrl}/\\\\]","_");
        if(safe.length()>200) safe=safe.substring(safe.length()-200);
        authorize(booking,actor);
        boolean isAdmin=!jdbc.queryForList("SELECT id FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Long.class,actor).isEmpty();
        boolean isHost=!jdbc.queryForList("SELECT p.id FROM bookings b JOIN properties p ON p.id=b.property_id " +
                "JOIN users u ON u.id=p.host_id AND u.status='ACTIVE' WHERE b.id=? AND p.host_id=?",Long.class,booking,actor).isEmpty();
        boolean isGuest=!jdbc.queryForList("SELECT b.id FROM bookings b JOIN users u ON u.id=b.guest_id AND u.status='ACTIVE' " +
                "WHERE b.id=? AND b.guest_id=?",Long.class,booking,actor).isEmpty();
        boolean allowed=switch(purpose) {
            case DIRECT_RECEIPT,DISPUTE -> isAdmin||isHost||isGuest;
            case ARRIVAL -> isAdmin||isHost;
            case REFUND,PAYOUT -> isAdmin;
        };
        require(allowed,"ERR_FORBIDDEN");
        String digest=sha256(bytes);
        var found=jdbc.query("SELECT id,booking_id,purpose,sha256,media_type,octet_length(content) AS size " +
                "FROM private_evidence_objects WHERE booking_id=? AND purpose=? AND sha256=?",
                (r,n)->view(r),booking,purpose.name(),digest);
        if(!found.isEmpty()) {
            audit(booking,actor,found.getFirst().id(),"UPLOAD");
            return found.getFirst();
        }
        long id=jdbc.queryForObject("""
                INSERT INTO private_evidence_objects(booking_id,uploader_id,purpose,sha256,media_type,original_name,content)
                VALUES (?,?,?,?,?,?,?) RETURNING id
                """,Long.class,booking,actor,purpose.name(),digest,mediaType,safe,bytes);
        audit(booking,actor,id,"UPLOAD");
        return new ObjectView(id,booking,purpose.name(),digest,mediaType,bytes.length);
    }

    @Transactional(readOnly=true)
    public void requireEvidence(long booking,long actor,Purpose purpose,String digest) {
        require(digest!=null&&digest.matches("^[a-f0-9]{64}$"),"ERR_EVIDENCE_REQUIRED");
        authorize(booking,actor);
        require(jdbc.queryForObject("SELECT count(*) FROM private_evidence_objects WHERE booking_id=? AND purpose=? AND sha256=?",
                Integer.class,booking,purpose.name(),digest)>0,"ERR_EVIDENCE_REQUIRED");
    }

    @Transactional(readOnly=true)
    public void requireEvidenceUploadedByActor(long booking,long actor,Purpose purpose,String digest) {
        require(digest!=null&&digest.matches("^[a-f0-9]{64}$"),"ERR_EVIDENCE_REQUIRED");
        authorize(booking,actor);
        require(jdbc.queryForObject("SELECT count(*) FROM private_evidence_objects WHERE booking_id=? AND uploader_id=? AND purpose=? AND sha256=?",
                Integer.class,booking,actor,purpose.name(),digest)>0,"ERR_EVIDENCE_REQUIRED");
    }

    @Transactional(readOnly=true)
    public void requireHostEvidence(long booking,long actor,Purpose purpose,String digest) {
        require(digest!=null&&digest.matches("^[a-f0-9]{64}$"),"ERR_EVIDENCE_REQUIRED");
        require(jdbc.queryForObject("""
                SELECT count(*) FROM private_evidence_objects e
                JOIN bookings b ON b.id=e.booking_id JOIN properties p ON p.id=b.property_id
                WHERE e.booking_id=? AND e.purpose=? AND e.sha256=? AND e.uploader_id=p.host_id
                    AND EXISTS (SELECT 1 FROM users u WHERE u.id=? AND u.role='ADMIN' AND u.status='ACTIVE')
                """,Integer.class,booking,purpose.name(),digest,actor)>0,"ERR_EVIDENCE_REQUIRED");
    }

    @Transactional
    public Download download(long booking,long objectId,long actor) {
        authorize(booking,actor);
        var rows=jdbc.query("""
                SELECT id,booking_id,purpose,sha256,media_type,original_name,content
                FROM private_evidence_objects WHERE id=? AND booking_id=?
                """,(r,n)->new Download(r.getString("original_name"),r.getString("media_type"),r.getBytes("content")),objectId,booking);
        require(!rows.isEmpty(),"ERR_NOT_FOUND");
        audit(booking,actor,objectId,"READ");
        return rows.getFirst();
    }

    @Transactional
    public java.util.List<ObjectView> list(long booking,long actor,Purpose purpose) {
        authorize(booking,actor);
        var objects=jdbc.query("""
                SELECT id,booking_id,purpose,sha256,media_type,octet_length(content) AS size
                FROM private_evidence_objects WHERE booking_id=? AND purpose=? ORDER BY created_at,id
                """,(r,n)->view(r),booking,purpose.name());
        objects.forEach(o->audit(booking,actor,o.id(),"READ"));
        return java.util.List.copyOf(objects);
    }

    private void authorize(long booking,long actor) {
        require(jdbc.queryForObject("""
                SELECT count(*) FROM bookings b JOIN properties p ON p.id=b.property_id
                WHERE b.id=? AND ((b.guest_id=? AND EXISTS(SELECT 1 FROM users u WHERE u.id=? AND u.status='ACTIVE'))
                    OR (p.host_id=? AND EXISTS(SELECT 1 FROM users u WHERE u.id=? AND u.status='ACTIVE'))
                    OR EXISTS(SELECT 1 FROM users u WHERE u.id=? AND u.role='ADMIN' AND u.status='ACTIVE'))
                """,Integer.class,booking,actor,actor,actor,actor,actor)>0,"ERR_NOT_FOUND");
    }
    private void audit(long booking,long actor,long objectId,String reason) {
        jdbc.update("INSERT INTO private_evidence_access_log(booking_id,actor_id,resource_type,resource_id,access_reason) VALUES (?,?,'EVIDENCE_OBJECT',?,?)",
                booking,actor,objectId,reason);
    }
    private static ObjectView view(ResultSet r)throws SQLException {
        return new ObjectView(r.getLong("id"),r.getLong("booking_id"),r.getString("purpose"),r.getString("sha256"),
                r.getString("media_type"),r.getLong("size"));
    }
    private static String sniff(byte[] b) {
        if(b.length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff) return "image/jpeg";
        if(b.length>=8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10) return "image/png";
        if(b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-') return "application/pdf";
        throw new PaymentPrimitiveException("ERR_EVIDENCE_TYPE");
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
    private static void require(boolean valid,String code) { if(!valid) throw new PaymentPrimitiveException(code); }
}
