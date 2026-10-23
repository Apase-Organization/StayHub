package com.stayhub.payment.quote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ConflictException;
import com.stayhub.config.VnpayProperties;
import com.stayhub.inventory.InventoryBookingRequest;
import com.stayhub.inventory.InventoryBookingService;
import com.stayhub.payment.PaymentMethod;
import com.stayhub.payment.VnpayPaymentRequest;
import com.stayhub.payment.VnpayPaymentService;
import com.stayhub.payment.ledger.PaymentPrimitivesService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCheckoutService {
    private static final JsonMapper CANONICAL=JsonMapper.builder().findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final JdbcTemplate jdbc;
    private final BookingQuoteService quotes;
    private final InventoryBookingService bookings;
    private final PaymentPrimitivesService primitives;
    private final VnpayPaymentService vnpay;
    private final VnpayProperties properties;

    public InventoryCheckoutService(JdbcTemplate jdbc,BookingQuoteService quotes,InventoryBookingService bookings,
                                    PaymentPrimitivesService primitives,VnpayPaymentService vnpay,VnpayProperties properties) {
        this.jdbc=jdbc; this.quotes=quotes; this.bookings=bookings; this.primitives=primitives;
        this.vnpay=vnpay; this.properties=properties;
    }

    @Transactional
    public BookingCreatedResponse create(long guestId,BookingConsentRequest consent,String idempotencyKey,String ipAddress) {
        if(idempotencyKey==null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,100}"))
            throw new BusinessException("ERR_INVALID_REQUEST","Idempotency-Key is required.");
        String bodyHash=hash(new ConsentBinding(guestId,consent.quoteId(),consent.acceptedPolicyVersion(),
                consent.acceptedFixtureVersion(),consent.acceptedQuoteHash()));
        var replay=jdbc.query("""
                SELECT id,inventory_request_hash,inventory_hold_expires_at,status FROM bookings
                WHERE guest_id=? AND inventory_idempotency_key=?
                """,(rs,row)->{
                    if(!bodyHash.equals(rs.getString("inventory_request_hash")))
                        throw new ConflictException("ERR_IDEMPOTENCY_CONFLICT","Idempotency-Key was used with a different quote.");
                    return new Existing(rs.getLong("id"),rs.getTimestamp("inventory_hold_expires_at").toInstant(),rs.getString("status"));
                },guestId,idempotencyKey);
        if(!replay.isEmpty()) {
            BookingQuoteResponse prior=quotes.storedQuote(guestId,consent.quoteId(),consent.acceptedQuoteHash());
            return replay(replay.getFirst(),prior,ipAddress);
        }
        var accepted=quotes.verifyConsent(guestId,consent.quoteId(),consent.acceptedPolicyVersion(),
                consent.acceptedFixtureVersion(),consent.acceptedQuoteHash());

        if(properties.getTmnCode()==null || properties.getTmnCode().isBlank()
                || properties.getConfigVersion()==null || properties.getConfigVersion().isBlank())
            throw new ConflictException("ERR_PAYMENT_CONFIG","VNPay merchant and config version must be set before checkout.");
        InventoryBookingRequest selection=new InventoryBookingRequest(accepted.selection().propertyId(),
                accepted.selection().checkIn(),accepted.selection().checkOut(),accepted.selection().currency(),
                accepted.selection().paymentMode(),idempotencyKey,accepted.selection().items());
        var quote=accepted.quote();
        var pricing=new InventoryBookingService.BookingPricing(quote.roomCharges(),quote.cleaningFee(),quote.subtotal(),
                quote.discount(),quote.totalStay(),accepted.discountCodeId(),accepted.discountCode(),quote.quoteHash(),
                quote.policyVersion(),quote.fixtureVersion(),bodyHash);
        var hold=bookings.createQuoted(guestId,selection,pricing);
        var mode=PaymentPrimitivesService.Mode.valueOf(quote.paymentMode().name());
        var financial=primitives.createSnapshot(new PaymentPrimitivesService.SnapshotInput(hold.bookingId(),guestId,mode,
                quote.currency(),quote.roomCharges(),quote.cleaningFee(),quote.discount(),0,
                quote.policyVersion(),quote.fixtureVersion(),quote.quoteHash(),hold.expiresAt()),idempotencyKey);
        String attemptKey="initial-"+hash(idempotencyKey).substring(0,48);
        var attempt=primitives.allocateAttempt(hold.bookingId(),guestId,properties.getTmnCode(),properties.getConfigVersion(),attemptKey);
        jdbc.update("""
                INSERT INTO payments(booking_id,payment_method,status,amount,provider,currency,provider_txn_ref)
                VALUES (?,?,'PENDING',?,'VNPAY',?,?)
                """,hold.bookingId(),PaymentMethod.VNPAY.name(),financial.dueOnline(),financial.currency(),attempt.providerRef());
        String checkoutUrl=vnpay.buildPaymentUrl(new VnpayPaymentRequest(attempt.providerRef(),attempt.expectedAmount(),
                "StayHub booking #"+hold.bookingId(),ipAddress));
        primitives.recordCreationOutcome(hold.bookingId(),guestId,attempt.id(),PaymentPrimitivesService.CreationOutcome.CREATED);
        quotes.consumeQuote(accepted.quoteId(),hold.bookingId(),guestId);
        return response(hold.bookingId(),hold.expiresAt(),attempt.id(),"PENDING_PAYMENT",quote,checkoutUrl,false);
    }

    private BookingCreatedResponse replay(Existing existing,BookingQuoteResponse quote,String ipAddress) {
        var attempt=jdbc.query("SELECT id,provider_ref,expected_amount FROM payment_attempts WHERE booking_id=? ORDER BY ordinal LIMIT 1",
                (rs,row)->new AttemptRef(rs.getLong("id"),rs.getString("provider_ref"),rs.getBigDecimal("expected_amount")),existing.id())
                .stream().findFirst().orElseThrow(()->new IllegalStateException("Initial payment attempt is missing"));
        String url=null;
        if("PENDING_PAYMENT".equals(existing.status()) && existing.expiresAt().isAfter(java.time.Instant.now()))
            url=vnpay.buildPaymentUrl(new VnpayPaymentRequest(attempt.reference(),attempt.amount(),
                    "StayHub booking #"+existing.id(),ipAddress));
        return response(existing.id(),existing.expiresAt(),attempt.id(),existing.status(),quote,url,true);
    }
    private BookingCreatedResponse response(long id,java.time.Instant expires,long attemptId,
                                            String status,BookingQuoteResponse quote,String url,boolean replayed) {
        java.time.Instant created=expires.minusSeconds(900);
        return new BookingCreatedResponse(id,quote.policyVersion(),quote.fixtureVersion(),quote.quoteHash(),
                status,quote.paymentMode(),quote.currency(),quote.totalStay(),quote.dueOnline(),
                quote.balanceDueHost(),created,expires,attemptId,0,url,replayed);
    }
    private static String hash(Object body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(CANONICAL.writeValueAsString(body).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception exception) { throw new IllegalStateException("Cannot hash consent",exception); }
    }
    private record ConsentBinding(long actorId,String quoteId,String policyVersion,String fixtureVersion,String quoteHash) {}
    private record Existing(long id,java.time.Instant expiresAt,String status) {}
    private record AttemptRef(long id,String reference,BigDecimal amount) {}
}
