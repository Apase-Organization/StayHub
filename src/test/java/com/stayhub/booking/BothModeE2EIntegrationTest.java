package com.stayhub.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stayhub.admin.AdminService;
import com.stayhub.admin.dto.DashboardStatsResponse;
import com.stayhub.auth.UserPrincipal;
import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.payment.Payment;
import com.stayhub.payment.PaymentMethod;
import com.stayhub.payment.PaymentRepository;
import com.stayhub.payment.PaymentStatus;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import com.stayhub.property.dto.PropertySummary;
import com.stayhub.review.Review;
import com.stayhub.review.ReviewRepository;
import com.stayhub.search.SearchService;
import com.stayhub.search.dto.SearchCriteria;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TSK-049 & TSK-077: End-to-End Acceptance Test verifying both payment modes
 * (FULL_PREPAY and HYBRID_DEPOSIT), multi-night stay search, read-only payment adapters,
 * host request workflows, review projections, and admin dashboard metrics.
 */
@AutoConfigureMockMvc
class BothModeE2EIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private SearchService searchService;

    @Autowired
    private AdminService adminService;

    private User admin;
    private User host;
    private User guest;
    private Property property;

    private UsernamePasswordAuthenticationToken adminAuth;
    private UsernamePasswordAuthenticationToken hostAuth;
    private UsernamePasswordAuthenticationToken guestAuth;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(User.builder()
                .fullName("E2E Admin")
                .email("admin.e2e@stayhub.local")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        host = userRepository.save(User.builder()
                .fullName("E2E Host")
                .email("host.e2e@stayhub.local")
                .passwordHash("hash")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build());

        guest = userRepository.save(User.builder()
                .fullName("E2E Guest")
                .email("guest.e2e@stayhub.local")
                .passwordHash("hash")
                .role(UserRole.GUEST)
                .status(UserStatus.ACTIVE)
                .build());

        property = propertyRepository.save(Property.builder()
                .host(host)
                .title("Grand Oceanfront Villa")
                .description("Luxury villa facing Da Nang coast")
                .city("Da Nang")
                .address("88 My Khe Beach")
                .pricePerNight(new BigDecimal("2000000"))
                .cleaningFee(new BigDecimal("200000"))
                .maxGuests(6)
                .bedrooms(3)
                .beds(3)
                .bathrooms(2)
                .ratingAvg(new BigDecimal("5.0"))
                .propertyType(PropertyType.VILLA)
                .status(PropertyStatus.ACTIVE)
                .build());

        UserPrincipal adminPrinc = UserPrincipal.create(admin);
        adminAuth = new UsernamePasswordAuthenticationToken(adminPrinc, adminPrinc.getPassword(), adminPrinc.getAuthorities());

        UserPrincipal hostPrinc = UserPrincipal.create(host);
        hostAuth = new UsernamePasswordAuthenticationToken(hostPrinc, hostPrinc.getPassword(), hostPrinc.getAuthorities());

        UserPrincipal guestPrinc = UserPrincipal.create(guest);
        guestAuth = new UsernamePasswordAuthenticationToken(guestPrinc, guestPrinc.getPassword(), guestPrinc.getAuthorities());
    }

    @AfterEach
    void cleanUp() {
        reviewRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void completeEndToEndFlowFullPrepayMode() throws Exception {
        LocalDate in = LocalDate.now().plusDays(5);
        LocalDate out = LocalDate.now().plusDays(8); // 3 nights

        // Step 1: Guest searches properties with dates and room requirement
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocation("Da Nang");
        criteria.setCheckIn(in);
        criteria.setCheckOut(out);
        criteria.setGuests(2);
        criteria.setRooms(2);

        Page<PropertySummary> searchResult = searchService.search(criteria, PageRequest.of(0, 10));
        assertThat(searchResult.getContent()).hasSize(1);
        PropertySummary offer = searchResult.getContent().getFirst();
        assertThat(offer.getNights()).isEqualTo(3);
        assertThat(offer.getTotalStay()).isEqualByComparingTo(new BigDecimal("6000000")); // 2,000,000 * 3

        // Step 2: Guest navigates to booking checkout with FULL_PREPAY mode
        mockMvc.perform(get("/properties/{id}/book", property.getId())
                        .param("checkInDate", in.toString())
                        .param("checkOutDate", out.toString())
                        .param("guests", "2")
                        .with(authentication(guestAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/booking"))
                .andExpect(model().attributeExists("property", "priceQuote"));

        // Step 3: Guest submits booking with FULL_PREPAY
        mockMvc.perform(post("/bookings")
                        .with(authentication(guestAuth))
                        .with(csrf())
                        .param("propertyId", property.getId().toString())
                        .param("checkInDate", in.toString())
                        .param("checkOutDate", out.toString())
                        .param("guests", "2")
                        .param("paymentMode", "FULL_PREPAY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?*"));

        // Step 4: Verify booking created in DB in PENDING_PAYMENT status
        Booking createdBooking = bookingRepository.findAll().getFirst();
        assertThat(createdBooking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(createdBooking.getNightlyPrice()).isEqualByComparingTo(new BigDecimal("2000000"));

        // Step 5: Read-only VNPay return URL simulation
        mockMvc.perform(get("/payments/vnpay/return")
                        .param("vnp_TxnRef", "MOCK-TXN-1")
                        .param("vnp_ResponseCode", "00"))
                .andExpect(status().isOk())
                .andExpect(view().name("payment/vnpay-return"))
                .andExpect(model().attribute("responseCode", "00"));

        // Booking remains PENDING_PAYMENT (return URL did NOT mutate financial state)
        Booking afterReturn = bookingRepository.findById(createdBooking.getId()).orElseThrow();
        assertThat(afterReturn.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        // Simulate host approval workflow
        afterReturn.setStatus(BookingStatus.PENDING);
        bookingRepository.save(afterReturn);

        // Step 6: Host views request in dashboard and accepts
        mockMvc.perform(get("/host/dashboard")
                        .with(authentication(hostAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("host/dashboard"))
                .andExpect(model().attribute("pendingBookingRequests", 1));

        mockMvc.perform(post("/api/v1/bookings/host/{id}/accept", createdBooking.getId())
                        .with(authentication(hostAuth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // Step 7: Admin views dashboard & verified distinct metrics
        DashboardStatsResponse stats = adminService.getDashboardStats();
        assertThat(stats.getTotalBookings()).isEqualTo(1);
        assertThat(stats.getActiveHosts()).isEqualTo(1);
        assertThat(stats.getTotalUsers()).isEqualTo(3);
    }

    @Test
    void completeEndToEndFlowHybridDepositMode() throws Exception {
        LocalDate in = LocalDate.now().plusDays(10);
        LocalDate out = LocalDate.now().plusDays(12); // 2 nights

        // Step 1: Guest submits booking with HYBRID_DEPOSIT
        mockMvc.perform(post("/bookings")
                        .with(authentication(guestAuth))
                        .with(csrf())
                        .param("propertyId", property.getId().toString())
                        .param("checkInDate", in.toString())
                        .param("checkOutDate", out.toString())
                        .param("guests", "3")
                        .param("paymentMode", "HYBRID_DEPOSIT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?*"));

        Booking hybridBooking = bookingRepository.findAll().getFirst();
        assertThat(hybridBooking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        // Step 2: Guest checks booking details (includes payment breakdown & mode)
        mockMvc.perform(get("/bookings/{id}", hybridBooking.getId())
                        .with(authentication(guestAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/booking-detail"))
                .andExpect(model().attributeExists("booking"));

        // Step 3: Host marks booking as completed after guest checks out
        hybridBooking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(hybridBooking);

        mockMvc.perform(post("/host/bookings/{id}/complete", hybridBooking.getId())
                        .with(authentication(hostAuth))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Booking completed = bookingRepository.findById(hybridBooking.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(BookingStatus.COMPLETED);

        // Step 4: Guest posts review on completed stay
        mockMvc.perform(post("/bookings/{id}/reviews", completed.getId())
                        .with(authentication(guestAuth))
                        .with(csrf())
                        .param("rating", "5")
                        .param("comment", "Fantastic beachfront stay, seamless check-in!"))
                .andExpect(status().is3xxRedirection());

        Review review = reviewRepository.findAll().getFirst();
        assertThat(review.getRating()).isEqualTo((short) 5);
        assertThat(review.getComment()).contains("Fantastic beachfront stay");

        // Step 5: Admin dashboard accurately accounts for the completed booking
        DashboardStatsResponse adminStats = adminService.getDashboardStats();
        assertThat(adminStats.getTotalBookings()).isEqualTo(1);
    }
}
