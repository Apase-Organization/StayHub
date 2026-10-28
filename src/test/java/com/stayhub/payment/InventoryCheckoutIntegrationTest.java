package com.stayhub.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.auth.UserPrincipal;
import com.stayhub.config.VnpayProperties;
import com.stayhub.payment.ledger.PaymentPrimitivesService;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.boot.test.context.SpringBootTest;

@AutoConfigureMockMvc
@TestPropertySource(properties={"app.payment.quote-validity-seconds=300", "app.payment.demo-return-verification-enabled=true"})
@ActiveProfiles("test")
@SpringBootTest
class InventoryCheckoutIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    static { POSTGRES.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @AfterAll static void stopDatabase() { POSTGRES.stop(); }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper mapper;
    @Autowired VnpayProperties vnpay;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired com.stayhub.payment.InventoryPaymentLifecycleService paymentLifecycle;
    @Autowired com.stayhub.inventory.InventoryBookingService inventoryBookings;
    @Autowired com.stayhub.inventory.InventoryDiscountReservationService discountReservations;
    @Autowired PaymentPrimitivesService primitives;

    long guest,host,property,type,plan;
    UserPrincipal principal;

    @BeforeEach void seed() {
        vnpay.setTmnCode("TESTMERCHANT");
        vnpay.setConfigVersion("test-v1");
        vnpay.setHashSecret("test-secret");
        vnpay.setPayUrl("https://sandbox.example/checkout");
        vnpay.setReturnUrl("https://stayhub.example/payments/vnpay/return");
        guest=users.save(User.builder().email("quote-"+java.util.UUID.randomUUID()+"@test.local")
                .passwordHash("hash").fullName("Quote Guest").role(UserRole.GUEST).status(UserStatus.ACTIVE).build()).getId();
        host=users.save(User.builder().email("host-"+java.util.UUID.randomUUID()+"@test.local")
                .passwordHash("hash").fullName("Quote Host").role(UserRole.HOST).status(UserStatus.ACTIVE).build()).getId();
        property=jdbc.queryForObject("""
                INSERT INTO properties(host_id,title,description,address,city,price_per_night,cleaning_fee,max_guests,
                    bedrooms,beds,bathrooms,property_type,status,sale_mode)
                VALUES (?, 'Quote stay','description','address','Da Nang',1000000,100000,4,2,2,2,'HOUSE','ACTIVE','ROOM_BASED') RETURNING id
                """,Long.class,host);
        type=jdbc.queryForObject("""
                INSERT INTO room_types(property_id,sale_mode,name,base_quantity,max_adults,max_occupants)
                VALUES (?,'ROOM_BASED','Deluxe',2,2,3) RETURNING id
                """,Long.class,property);
        plan=jdbc.queryForObject("""
                INSERT INTO rate_plans(property_id,room_type_id,name,policy_version,nightly_price)
                VALUES (?,?, 'Flexible','s0-policy-v1',1000000) RETURNING id
                """,Long.class,property,type);
        jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?, '2030-12-10',2)",type);
        principal=UserPrincipal.create(users.findById(guest).orElseThrow());
    }

    @Test void quoteConsentCreatesOneHybridHoldAndSameKeyReplays() throws Exception {
        String quoteBody="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-11","currency":"VND",
                 "paymentMode":"HYBRID_DEPOSIT","rooms":1,"adults":2,"childrenAges":[],
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":1,
                    "roomAllocations":[{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quoteBody.replace("HYBRID_DEPOSIT","FULL_PREPAY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dueOnline").value(1100000))
                .andExpect(jsonPath("$.data.balanceDueHost").value(0));
        String quoteJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quoteBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dueOnline").value(220000))
                .andExpect(jsonPath("$.data.balanceDueHost").value(880000)).andReturn().getResponse().getContentAsString();
        JsonNode quote=mapper.readTree(quoteJson).path("data");
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(quote.path("quoteId").asText(),quote.path("policyVersion").asText(),
                quote.path("fixtureVersion").asText(),quote.path("quoteHash").asText());
        String bookingJson=mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf())
                        .header("Idempotency-Key","quote-submit-1").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.bookingStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.paymentMode").value("HYBRID_DEPOSIT"))
                .andExpect(jsonPath("$.data.checkoutUrl").value(org.hamcrest.Matchers.startsWith("https://sandbox.example/checkout?")))
                .andReturn().getResponse().getContentAsString();
        long bookingId=mapper.readTree(bookingJson).path("data").path("id").asLong();
        mvc.perform(get("/payments/vnpay/return").param("vnp_TxnRef","SH110-read-only")
                        .param("vnp_ResponseCode","00"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id=?",String.class,bookingId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_provider_events WHERE booking_id=?",Integer.class,bookingId)).isZero();
        mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf()).header("Idempotency-Key","quote-submit-1")
                        .contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(bookingId))
                .andExpect(jsonPath("$.data.replayed").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE property_id=?",Integer.class,property)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_commitments c JOIN booking_items i ON i.id=c.item_id WHERE i.booking_id=? AND c.state='HELD'",Integer.class,bookingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_attempts WHERE booking_id=?",Integer.class,bookingId)).isEqualTo(1);

        var callback=signedIpn(bookingId,"22000000","TXN-QUOTE-1");
        String verifyUrl="/api/v1/payments/vnpay/verify";
        String signedBody=mapper.writeValueAsString(Map.of("params",asMap(callback)));
        mvc.perform(post(verifyUrl).with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(signedBody))
                .andExpect(status().isForbidden());
        mvc.perform(post(verifyUrl).with(user(UserPrincipal.create(users.findById(host).orElseThrow()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(signedBody))
                .andExpect(status().isNotFound());
        Map<String,String> badSignature=new TreeMap<>(asMap(callback));
        badSignature.put("vnp_Amount","1");
        mvc.perform(post(verifyUrl).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("params",badSignature))))
                .andExpect(status().isBadRequest());
        var wrongAmount=signedIpn(bookingId,"10000000","RETURN-WRONG-AMOUNT");
        mvc.perform(post(verifyUrl).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("params",asMap(wrongAmount)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("PENDING_PAYMENT");
        // Standard return omits outbound Version/Command/CurrCode, exactly as signedIpn supplies.
        mvc.perform(post(verifyUrl).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(signedBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));
        mvc.perform(post(verifyUrl).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(signedBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_commitments c JOIN booking_items i ON i.id=c.item_id WHERE i.booking_id=?",String.class,bookingId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT disposition FROM payment_collections WHERE booking_id=? AND disposition='ACCEPTED'",String.class,bookingId)).isEqualTo("ACCEPTED");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/payments/vnpay/ipn")
                        .params(callback))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='ACCEPTED'",Integer.class,bookingId)).isEqualTo(1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/payments/vnpay/ipn")
                        .params(signedIpn(bookingId,"22000000","TXN-EXTRA-2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='RECONCILE'",Integer.class,bookingId)).isEqualTo(2);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/payments/bookings/{id}/status",bookingId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentMode").value("HYBRID_DEPOSIT"))
                .andExpect(jsonPath("$.data.onlinePaymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.onlinePaidVerified").value(220000))
                .andExpect(jsonPath("$.data.balanceDueHost").value(880000))
                .andExpect(jsonPath("$.data.directOutstanding").value(880000))
                .andExpect(jsonPath("$.data.directReceiptStatus").value("OUTSTANDING"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_provider_events WHERE booking_id=? AND disposition='ACCEPTED'",Integer.class,bookingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_payment_outbox WHERE booking_id=? AND event_type='PAYMENT_CONFIRMED'",Integer.class,bookingId)).isEqualTo(1);
    }

    @Test void hostCanBookAnotherHostsPropertyButSelfBookingIsRejectedClearly() throws Exception {
        String body="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-11","currency":"VND",
                 "paymentMode":"FULL_PREPAY","rooms":1,"adults":2,"childrenAges":[],
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":1,
                    "roomAllocations":[{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        UserPrincipal hostPrincipal=UserPrincipal.create(users.findById(host).orElseThrow());
        mvc.perform(post("/api/v1/booking-quotes").with(user(hostPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_SELF_BOOKING"));

        long otherHost=users.save(User.builder().email("other-host-"+java.util.UUID.randomUUID()+"@test.local")
                .passwordHash("hash").fullName("Other Host").role(UserRole.HOST).status(UserStatus.ACTIVE).build()).getId();
        jdbc.update("UPDATE properties SET host_id=? WHERE id=?",otherHost,property);
        JsonNode quote=mapper.readTree(mvc.perform(post("/api/v1/booking-quotes").with(user(hostPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(quote.path("quoteId").asText(),quote.path("policyVersion").asText(),
                quote.path("fixtureVersion").asText(),quote.path("quoteHash").asText());
        mvc.perform(post("/api/v1/bookings").with(user(hostPrincipal)).with(csrf())
                        .header("Idempotency-Key","host-books-other-property").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.checkoutUrl")
                        .value(org.hamcrest.Matchers.startsWith("https://sandbox.example/checkout?")));
    }

    @Test void twoRoomQuoteReconcilesFullAndHybridFundingSnapshots() throws Exception {
        String body="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-11","currency":"VND",
                 "paymentMode":"FULL_PREPAY","rooms":2,"adults":4,"childrenAges":[],
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":2,
                    "roomAllocations":[{"adults":2,"childrenAges":[]},{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        String fullJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roomCharges").value(2000000))
                .andExpect(jsonPath("$.data.cleaningFee").value(100000))
                .andExpect(jsonPath("$.data.totalStay").value(2100000))
                .andExpect(jsonPath("$.data.dueOnline").value(2100000))
                .andExpect(jsonPath("$.data.balanceDueHost").value(0)).andReturn().getResponse().getContentAsString();
        String hybridJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("FULL_PREPAY","HYBRID_DEPOSIT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalStay").value(2100000))
                .andExpect(jsonPath("$.data.dueOnline").value(420000))
                .andExpect(jsonPath("$.data.balanceDueHost").value(1680000)).andReturn().getResponse().getContentAsString();
        JsonNode hybrid=mapper.readTree(hybridJson).path("data");
        assertThat(mapper.readTree(fullJson).path("data").path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(hybrid.path("quoteId").asText(),hybrid.path("policyVersion").asText(),
                hybrid.path("fixtureVersion").asText(),hybrid.path("quoteHash").asText());
        String created=mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf())
                        .header("Idempotency-Key","two-room-hybrid").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId=mapper.readTree(created).path("data").path("id").asLong();
        var snapshot=jdbc.queryForMap("SELECT * FROM booking_financial_snapshots WHERE booking_id=?",bookingId);
        assertThat((BigDecimal)snapshot.get("room_charges")).isEqualByComparingTo("2000000");
        assertThat((BigDecimal)snapshot.get("cleaning_fee")).isEqualByComparingTo("100000");
        assertThat((BigDecimal)snapshot.get("due_online")).isEqualByComparingTo("420000");
        assertThat((BigDecimal)snapshot.get("balance_due_host")).isEqualByComparingTo("1680000");
        assertThat((BigDecimal)snapshot.get("gross_commission")).isEqualByComparingTo("315000");
        assertThat((BigDecimal)snapshot.get("net_retention")).isEqualByComparingTo("315000");
        assertThat((BigDecimal)snapshot.get("held_host_payable")).isEqualByComparingTo("105000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_allocations ra JOIN booking_items i ON i.id=ra.item_id WHERE i.booking_id=?",Integer.class,bookingId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sum(room_charge) FROM nightly_snapshots ns JOIN booking_items i ON i.id=ns.item_id WHERE i.booking_id=?",BigDecimal.class,bookingId)).isEqualByComparingTo("2000000");
        assertThat(jdbc.queryForObject("SELECT sum(fees) FROM nightly_snapshots ns JOIN booking_items i ON i.id=ns.item_id WHERE i.booking_id=?",BigDecimal.class,bookingId)).isEqualByComparingTo("100000");
    }

    @Test void frozenSixPointEightMillionFixtureMatchesBothModesAndFunding() throws Exception {
        jdbc.update("UPDATE rate_plans SET nightly_price=1750000 WHERE id=?",plan);
        jdbc.update("UPDATE properties SET cleaning_fee=0 WHERE id=?",property);
        jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?, '2030-12-11',2)",type);
        jdbc.update("""
                INSERT INTO discount_codes(code,type,value,cap,minimum_amount,starts_at,ends_at,usage_limit,used_count,per_user_limit,active)
                VALUES ('FIXTURE200','FIXED',200000,200000,0,clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',2,0,1,true),
                       ('OVERFUND','FIXED',1050001,1050001,0,clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',1,0,1,true)
                """);
        String body="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-12","currency":"VND",
                 "paymentMode":"FULL_PREPAY","rooms":2,"adults":4,"childrenAges":[],"discountCode":"FIXTURE200",
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":2,
                    "roomAllocations":[{"adults":2,"childrenAges":[]},{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subtotal").value(7000000))
                .andExpect(jsonPath("$.data.discount").value(200000)).andExpect(jsonPath("$.data.totalStay").value(6800000))
                .andExpect(jsonPath("$.data.dueOnline").value(6800000)).andExpect(jsonPath("$.data.balanceDueHost").value(0));
        String hybridJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("FULL_PREPAY","HYBRID_DEPOSIT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dueOnline").value(1360000))
                .andExpect(jsonPath("$.data.balanceDueHost").value(5440000)).andReturn().getResponse().getContentAsString();
        String overfund=body.replace("FIXTURE200","OVERFUND").replace("FULL_PREPAY","HYBRID_DEPOSIT");
        mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(overfund))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.errorCode").value("ERR_DISCOUNT_FUNDING"));
        JsonNode hybrid=mapper.readTree(hybridJson).path("data");
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(hybrid.path("quoteId").asText(),hybrid.path("policyVersion").asText(),
                hybrid.path("fixtureVersion").asText(),hybrid.path("quoteHash").asText());
        String created=mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf())
                        .header("Idempotency-Key","fixture-hybrid-6m8").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId=mapper.readTree(created).path("data").path("id").asLong();
        var snapshot=jdbc.queryForMap("SELECT * FROM booking_financial_snapshots WHERE booking_id=?",bookingId);
        assertThat((BigDecimal)snapshot.get("room_charges")).isEqualByComparingTo("7000000");
        assertThat((BigDecimal)snapshot.get("discount")).isEqualByComparingTo("200000");
        assertThat((BigDecimal)snapshot.get("total_stay")).isEqualByComparingTo("6800000");
        assertThat((BigDecimal)snapshot.get("due_online")).isEqualByComparingTo("1360000");
        assertThat((BigDecimal)snapshot.get("balance_due_host")).isEqualByComparingTo("5440000");
        assertThat((BigDecimal)snapshot.get("gross_commission")).isEqualByComparingTo("1050000");
        assertThat((BigDecimal)snapshot.get("net_retention")).isEqualByComparingTo("850000");
        assertThat((BigDecimal)snapshot.get("held_host_payable")).isEqualByComparingTo("510000");
        assertThat(jdbc.queryForObject("SELECT sum(discount_allocation) FROM nightly_snapshots ns JOIN booking_items i ON i.id=ns.item_id WHERE i.booking_id=?",BigDecimal.class,bookingId)).isEqualByComparingTo("200000");
        assertThat(jdbc.queryForObject("SELECT sum(room_charge+fees+taxes-discount_allocation) FROM nightly_snapshots ns JOIN booking_items i ON i.id=ns.item_id WHERE i.booking_id=?",BigDecimal.class,bookingId)).isEqualByComparingTo("6800000");
    }

    @Test void discountQuotaReservesWithHoldAndConsumesOnFirstVerifiedCollection() throws Exception {
        jdbc.update("""
                INSERT INTO discount_codes(code,type,value,cap,minimum_amount,starts_at,ends_at,usage_limit,used_count,per_user_limit,active)
                VALUES ('SAVE100','FIXED',100000,100000,0,clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',1,0,1,true)
                """);
        String quoteBody="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-11","currency":"VND",
                 "paymentMode":"HYBRID_DEPOSIT","rooms":1,"adults":2,"childrenAges":[],"discountCode":"SAVE100",
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":1,
                    "roomAllocations":[{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        String quoteJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quoteBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.discount").value(100000))
                .andExpect(jsonPath("$.data.totalStay").value(1000000))
                .andExpect(jsonPath("$.data.dueOnline").value(200000)).andReturn().getResponse().getContentAsString();
        JsonNode quote=mapper.readTree(quoteJson).path("data");
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(quote.path("quoteId").asText(),quote.path("policyVersion").asText(),
                quote.path("fixtureVersion").asText(),quote.path("quoteHash").asText());
        String created=mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf())
                        .header("Idempotency-Key","discount-booking-1").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId=mapper.readTree(created).path("data").path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM discount_codes WHERE code='SAVE100'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used_count FROM discount_codes WHERE code='SAVE100'",Integer.class)).isZero();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/payments/vnpay/ipn")
                        .params(signedIpn(bookingId,"20000000","TXN-DISCOUNT-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM discount_codes WHERE code='SAVE100'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT used_count FROM discount_codes WHERE code='SAVE100'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_discount_reservations WHERE booking_id=?",String.class,bookingId)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM bookings WHERE id=?",BigDecimal.class,bookingId)).isEqualByComparingTo("100000");
        assertThat(jdbc.queryForObject("SELECT sum(discount_allocation) FROM nightly_snapshots ns JOIN booking_items i ON i.id=ns.item_id WHERE i.booking_id=?",BigDecimal.class,bookingId))
                .isEqualByComparingTo("100000");
    }

    @Test void expiryAndVerifiedIpnRaceCannotConfirmOrResurrectReleasedInventory() throws Exception {
        String quoteBody="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-11","currency":"VND",
                 "paymentMode":"FULL_PREPAY","rooms":1,"adults":2,"childrenAges":[],
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":1,
                    "roomAllocations":[{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        String quoteJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quoteBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode quote=mapper.readTree(quoteJson).path("data");
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(quote.path("quoteId").asText(),quote.path("policyVersion").asText(),
                quote.path("fixtureVersion").asText(),quote.path("quoteHash").asText());
        String bookingJson=mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf())
                        .header("Idempotency-Key","expiry-ipn-race").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId=mapper.readTree(bookingJson).path("data").path("id").asLong();
        jdbc.update("UPDATE bookings SET inventory_hold_expires_at=clock_timestamp()-interval '1 second' WHERE id=?",bookingId);
        var callback=signedIpn(bookingId,"110000000","TXN-EXPIRY-RACE");
        var gate=new CyclicBarrier(2);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var expiry=pool.submit(()->runAtGate(gate,()->{ inventoryBookings.expire(bookingId); return null; }));
            var ipn=pool.submit(()->runAtGate(gate,()->paymentLifecycle.handleInventoryIpn(
                    callback.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->e.getValue().getFirst())))));
            expiry.get(10,TimeUnit.SECONDS);
            assertThat(ipn.get(10,TimeUnit.SECONDS)).isNotNull();
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_commitments c JOIN booking_items i ON i.id=c.item_id WHERE i.booking_id=?",String.class,bookingId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='ACCEPTED'",Integer.class,bookingId)).isZero();
    }

    @Test void threeFailedAttemptsReleaseHoldEarlyAndLateSuccessIsReconciliationOnly() throws Exception {
        jdbc.update("""
                INSERT INTO discount_codes(code,type,value,cap,minimum_amount,starts_at,ends_at,usage_limit,used_count,per_user_limit,active)
                VALUES ('SAVEFAIL','FIXED',100000,100000,0,clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',1,0,1,true)
                """);
        String quoteBody="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-11","currency":"VND",
                 "paymentMode":"FULL_PREPAY","rooms":1,"adults":2,"childrenAges":[],"discountCode":"SAVEFAIL",
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":1,
                    "roomAllocations":[{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        String quoteJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quoteBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode quote=mapper.readTree(quoteJson).path("data");
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(quote.path("quoteId").asText(),quote.path("policyVersion").asText(),
                quote.path("fixtureVersion").asText(),quote.path("quoteHash").asText());
        String bookingJson=mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf())
                        .header("Idempotency-Key","three-failed-attempts").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId=mapper.readTree(bookingJson).path("data").path("id").asLong();
        long firstId=jdbc.queryForObject("SELECT id FROM payment_attempts WHERE booking_id=? AND ordinal=1",Long.class,bookingId);
        String firstRef=jdbc.queryForObject("SELECT provider_ref FROM payment_attempts WHERE id=?",String.class,firstId);
        var second=primitives.allocateAttempt(bookingId,guest,"TESTMERCHANT","test-v1","retry-attempt-2");
        var third=primitives.allocateAttempt(bookingId,guest,"TESTMERCHANT","test-v1","retry-attempt-3");
        primitives.recordCreationOutcome(bookingId,guest,second.id(),PaymentPrimitivesService.CreationOutcome.CREATED);
        primitives.recordCreationOutcome(bookingId,guest,third.id(),PaymentPrimitivesService.CreationOutcome.CREATED);

        for(String ref:java.util.List.of(firstRef,second.providerRef(),third.providerRef())) {
            var failed=signedIpn(ref,"100000000","24","05","FAIL-"+ref.substring(ref.length()-3));
            assertThat(paymentLifecycle.handleInventoryIpn(asMap(failed)).RspCode()).isEqualTo("00");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(inventoryBookings.findExpired(100)).contains(bookingId);

        inventoryBookings.expire(bookingId);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_commitments c JOIN booking_items i ON i.id=c.item_id WHERE i.booking_id=?",String.class,bookingId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_discount_reservations WHERE booking_id=?",String.class,bookingId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM discount_codes WHERE code='SAVEFAIL'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT used_count FROM discount_codes WHERE code='SAVEFAIL'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id=?",String.class,bookingId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_payment_outbox WHERE booking_id=? AND event_type='PAYMENT_FAILED' AND payment_status='FAILED'",Integer.class,bookingId)).isEqualTo(1);
        mvc.perform(get("/api/v1/payments/bookings/{id}/status",bookingId).with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.onlinePaymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.onlinePaidVerified").value(0));

        var lateSuccess=signedIpn(firstRef,"100000000","00","00","LATE-SUCCESS-1");
        assertThat(paymentLifecycle.handleInventoryIpn(asMap(lateSuccess)).RspCode()).isEqualTo("00");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='ACCEPTED'",Integer.class,bookingId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='RECONCILE'",Integer.class,bookingId)).isEqualTo(1);
        mvc.perform(get("/api/v1/payments/bookings/{id}/status",bookingId).with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.onlinePaymentStatus").value("RECONCILE"))
                .andExpect(jsonPath("$.data.onlinePaidVerified").value(0));
    }

    @Test void reconciliationReceiptIsNotAThirdTerminalFailure() throws Exception {
        String quoteBody="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-11","currency":"VND",
                 "paymentMode":"FULL_PREPAY","rooms":1,"adults":2,"childrenAges":[],
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":1,
                    "roomAllocations":[{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        String quoteJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quoteBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode quote=mapper.readTree(quoteJson).path("data");
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(quote.path("quoteId").asText(),quote.path("policyVersion").asText(),
                quote.path("fixtureVersion").asText(),quote.path("quoteHash").asText());
        String created=mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf())
                        .header("Idempotency-Key","reconcile-not-failure").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId=mapper.readTree(created).path("data").path("id").asLong();
        String firstRef=jdbc.queryForObject("SELECT provider_ref FROM payment_attempts WHERE booking_id=? AND ordinal=1",String.class,bookingId);
        var second=primitives.allocateAttempt(bookingId,guest,"TESTMERCHANT","test-v1","reconcile-retry-2");
        var third=primitives.allocateAttempt(bookingId,guest,"TESTMERCHANT","test-v1","reconcile-retry-3");
        primitives.recordCreationOutcome(bookingId,guest,second.id(),PaymentPrimitivesService.CreationOutcome.CREATED);
        primitives.recordCreationOutcome(bookingId,guest,third.id(),PaymentPrimitivesService.CreationOutcome.CREATED);
        Map<String,String> tampered=asMap(signedIpn(firstRef,"110000000","24","05","TAMPERED-1"));
        tampered.put("vnp_Amount","100000000"); // signature intentionally no longer matches the envelope
        assertThat(paymentLifecycle.handleInventoryIpn(tampered).RspCode()).isEqualTo("97");
        Map<String,String> wrongMerchant=asMap(signedIpn(firstRef,"110000000","24","05","WRONG-MERCHANT-1"));
        wrongMerchant.put("vnp_TmnCode","OTHER-MERCHANT");
        assertThat(paymentLifecycle.handleInventoryIpn(resign(wrongMerchant)).RspCode()).isEqualTo("97");
        Map<String,String> wrongCommand=asMap(signedIpn(firstRef,"110000000","24","05","WRONG-COMMAND-1"));
        wrongCommand.put("vnp_Command","refund");
        assertThat(paymentLifecycle.handleInventoryIpn(resign(wrongCommand)).RspCode()).isEqualTo("97");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_provider_events WHERE booking_id=?",Integer.class,bookingId)).isZero();
        for(String ref:java.util.List.of(firstRef,second.providerRef())) {
            var failed=signedIpn(ref,"110000000","24","05","FAIL-"+ref.substring(ref.length()-3));
            assertThat(paymentLifecycle.handleInventoryIpn(asMap(failed)).RspCode()).isEqualTo("00");
        }
        Map<String,String> wrongCurrency=asMap(signedIpn(third.providerRef(),"110000000","00","00","WRONG-CURRENCY-3"));
        wrongCurrency.put("vnp_CurrCode","USD");
        assertThat(paymentLifecycle.handleInventoryIpn(resign(wrongCurrency)).RspCode()).isEqualTo("00");
        assertThat(jdbc.queryForObject("SELECT disposition FROM inventory_provider_events WHERE provider_transaction_no='WRONG-CURRENCY-3'",String.class)).isEqualTo("RECONCILE");
        var wrongAmount=signedIpn(third.providerRef(),"109000000","00","00","WRONG-AMOUNT-3");
        assertThat(paymentLifecycle.handleInventoryIpn(asMap(wrongAmount)).RspCode()).isEqualTo("00");
        assertThat(jdbc.queryForObject("SELECT disposition FROM inventory_provider_events WHERE provider_transaction_no='WRONG-AMOUNT-3'",String.class)).isEqualTo("RECONCILE");
        assertThat(inventoryBookings.findExpired(100)).doesNotContain(bookingId);
        inventoryBookings.expire(bookingId);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT state FROM inventory_commitments c JOIN booking_items i ON i.id=c.item_id WHERE i.booking_id=?",String.class,bookingId)).isEqualTo("HELD");

        var exactSuccess=signedIpn(third.providerRef(),"110000000","00","00","CORRECT-AMOUNT-3");
        assertThat(paymentLifecycle.handleInventoryIpn(asMap(exactSuccess)).RspCode()).isEqualTo("00");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,bookingId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='ACCEPTED'",Integer.class,bookingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_payment_outbox WHERE booking_id=? AND event_type='PAYMENT_CONFIRMED'",Integer.class,bookingId)).isEqualTo(1);
    }

    @Test void competingDiscountReservationsSerializeAtTheDatabaseQuotaRow() throws Exception {
        long codeId=jdbc.queryForObject("""
                INSERT INTO discount_codes(code,type,value,cap,minimum_amount,starts_at,ends_at,usage_limit,used_count,per_user_limit,active)
                VALUES ('RACE1','FIXED',100000,100000,0,clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',1,0,1,true)
                RETURNING id
                """,Long.class);
        long otherGuest=users.save(User.builder().email("guest-race-"+java.util.UUID.randomUUID()+"@test.local")
                .passwordHash("hash").fullName("Other guest").role(UserRole.GUEST).status(UserStatus.ACTIVE).build()).getId();
        long bookingA=cancelledBooking(guest);
        long bookingB=cancelledBooking(otherGuest);
        var tx=new TransactionTemplate(transactionManager);
        var barrier=new CyclicBarrier(2);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->reserve(tx,barrier,bookingA,guest,codeId));
            var b=pool.submit(()->reserve(tx,barrier,bookingB,otherGuest,codeId));
            assertThat(java.util.List.of(a.get(8,TimeUnit.SECONDS),b.get(8,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(8,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM discount_codes WHERE id=?",Integer.class,codeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_discount_reservations WHERE discount_code_id=? AND state='RESERVED'",Integer.class,codeId)).isEqualTo(1);
    }

    @Test void discountRoundingIsFullyAllocatedAndZeroOnlineDueIsRejected() throws Exception {
        jdbc.update("""
                INSERT INTO discount_codes(code,type,value,cap,minimum_amount,starts_at,ends_at,usage_limit,used_count,per_user_limit,active)
                VALUES ('ONEVND','FIXED',1,1,0,clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',2,0,1,true)
                """);
        jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?, '2030-12-11',2),(?, '2030-12-12',2)",type,type);
        String body="""
                {"propertyId":%d,"checkIn":"2030-12-10","checkOut":"2030-12-13","currency":"VND",
                 "paymentMode":"FULL_PREPAY","rooms":1,"adults":2,"childrenAges":[],"discountCode":"ONEVND",
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":1,
                    "roomAllocations":[{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        String quoteJson=mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subtotal").value(3100000))
                .andExpect(jsonPath("$.data.discount").value(1)).andExpect(jsonPath("$.data.totalStay").value(3099999))
                .andReturn().getResponse().getContentAsString();
        JsonNode quote=mapper.readTree(quoteJson).path("data");
        String consent="""
                {"quoteId":"%s","acceptedPolicyVersion":"%s","acceptedFixtureVersion":"%s","acceptedQuoteHash":"%s"}
                """.formatted(quote.path("quoteId").asText(),quote.path("policyVersion").asText(),
                quote.path("fixtureVersion").asText(),quote.path("quoteHash").asText());
        String created=mvc.perform(post("/api/v1/bookings").with(user(principal)).with(csrf())
                        .header("Idempotency-Key","rounding-one-vnd").contentType(MediaType.APPLICATION_JSON).content(consent))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId=mapper.readTree(created).path("data").path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT sum(discount_allocation) FROM nightly_snapshots ns JOIN booking_items i ON i.id=ns.item_id WHERE i.booking_id=?",BigDecimal.class,bookingId)).isEqualByComparingTo("1");
        assertThat(jdbc.queryForList("SELECT discount_allocation FROM nightly_snapshots ns JOIN booking_items i ON i.id=ns.item_id WHERE i.booking_id=? ORDER BY stay_date",BigDecimal.class,bookingId))
                .containsExactly(new BigDecimal("1"),BigDecimal.ZERO,BigDecimal.ZERO);

        jdbc.update("UPDATE rate_plans SET nightly_price=1 WHERE id=?",plan);
        jdbc.update("UPDATE properties SET cleaning_fee=0 WHERE id=?",property);
        jdbc.update("INSERT INTO type_day_inventory(room_type_id,stay_date,total) VALUES (?, '2031-01-10',2)",type);
        String zeroDue="""
                {"propertyId":%d,"checkIn":"2031-01-10","checkOut":"2031-01-11","currency":"VND",
                 "paymentMode":"HYBRID_DEPOSIT","rooms":1,"adults":2,"childrenAges":[],
                 "items":[{"roomTypeId":%d,"ratePlanId":%d,"quantity":1,
                    "roomAllocations":[{"adults":2,"childrenAges":[]}]}]}
                """.formatted(property,type,plan);
        int quoteCountBefore=jdbc.queryForObject("SELECT count(*) FROM inventory_quotes WHERE actor_id=?",Integer.class,guest);
        int attemptCountBefore=jdbc.queryForObject("SELECT count(*) FROM payment_attempts",Integer.class);
        mvc.perform(post("/api/v1/booking-quotes").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(zeroDue))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.errorCode").value("ERR_NONPOSITIVE_BOOKING_AMOUNT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_quotes WHERE actor_id=?",Integer.class,guest)).isEqualTo(quoteCountBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_attempts",Integer.class)).isEqualTo(attemptCountBefore);
    }

    private boolean reserve(TransactionTemplate tx,CyclicBarrier barrier,long bookingId,long guestId,long codeId) {
        try {
            tx.execute(status->{
                try { barrier.await(3,TimeUnit.SECONDS); }
                catch(Exception exception) { throw new IllegalStateException(exception); }
                discountReservations.reserve(bookingId,guestId,codeId,"RACE1",new BigDecimal("100000"),Instant.now().plusSeconds(900));
                return null;
            });
            return true;
        } catch(org.springframework.dao.DataIntegrityViolationException rejected) { return false; }
    }

    private long cancelledBooking(long guestId) {
        return jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,
                    service_fee,subtotal_price,discount_amount,total_price,status,cancelled_at)
                VALUES (?,?, '2030-12-10','2030-12-11',1,1000000,0,0,1000000,0,1000000,'CANCELLED',clock_timestamp())
                RETURNING id
                """,Long.class,property,guestId);
    }

    private org.springframework.util.MultiValueMap<String,String> signedIpn(long bookingId,String amount,String transactionNo) {
        String ref=jdbc.queryForObject("SELECT provider_ref FROM payment_attempts WHERE booking_id=? ORDER BY ordinal LIMIT 1",String.class,bookingId);
        return signedIpn(ref,amount,"00","00",transactionNo);
    }
    private org.springframework.util.MultiValueMap<String,String> signedIpn(String ref,String amount,String responseCode,
                                                                            String transactionStatus,String transactionNo) {
        Map<String,String> params=new TreeMap<>();
        // PAY IPN does not require outbound request Version, Command or CurrCode.
        params.put("vnp_TmnCode","TESTMERCHANT");
        params.put("vnp_TxnRef",ref); params.put("vnp_Amount",amount);
        params.put("vnp_BankCode","NCB"); params.put("vnp_BankTranNo","BANK-"+transactionNo);
        params.put("vnp_CardType","ATM"); params.put("vnp_OrderInfo","Thanh toan dat phong");
        params.put("vnp_ResponseCode",responseCode); params.put("vnp_TransactionStatus",transactionStatus);
        params.put("vnp_TransactionNo",transactionNo);
        params.put("vnp_PayDate",DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
        String query=params.entrySet().stream().map(entry->URLEncoder.encode(entry.getKey(),StandardCharsets.UTF_8)+"="+
                URLEncoder.encode(entry.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        params.put("vnp_SecureHash",new VnpayPaymentService(vnpay).hmacSha512("test-secret",query));
        org.springframework.util.LinkedMultiValueMap<String,String> multi=new org.springframework.util.LinkedMultiValueMap<>();
        params.forEach(multi::add);
        return multi;
    }
    private Map<String,String> asMap(org.springframework.util.MultiValueMap<String,String> params) {
        return params.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,entry->entry.getValue().getFirst()));
    }
    private Map<String,String> resign(Map<String,String> unsigned) {
        Map<String,String> params=new TreeMap<>(unsigned);
        params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");
        String query=params.entrySet().stream().map(entry->URLEncoder.encode(entry.getKey(),StandardCharsets.UTF_8)+"="+
                URLEncoder.encode(entry.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        params.put("vnp_SecureHash",new VnpayPaymentService(vnpay).hmacSha512("test-secret",query));
        return params;
    }
    private <T> T runAtGate(CyclicBarrier gate,java.util.concurrent.Callable<T> work) {
        try { gate.await(3,TimeUnit.SECONDS); return work.call(); }
        catch(Exception exception) { throw new IllegalStateException(exception); }
    }
}
