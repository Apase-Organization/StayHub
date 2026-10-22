package com.stayhub.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stayhub.admin.dto.DashboardStatsResponse;
import com.stayhub.auth.UserPrincipal;
import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingRepository;
import com.stayhub.booking.BookingStatus;
import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.payment.PaymentRepository;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminWebIntegrationTest extends PostgreSqlIntegrationTest {

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

    private User admin;
    private User host;
    private User guest;
    private Property property;
    private UsernamePasswordAuthenticationToken adminAuth;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(User.builder()
                .fullName("Admin User")
                .email("admin.test@stayhub.local")
                .passwordHash("password")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        host = userRepository.save(User.builder()
                .fullName("Host User")
                .email("host.test@stayhub.local")
                .passwordHash("password")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build());

        guest = userRepository.save(User.builder()
                .fullName("Guest User")
                .email("guest.test@stayhub.local")
                .passwordHash("password")
                .role(UserRole.GUEST)
                .status(UserStatus.ACTIVE)
                .build());

        property = propertyRepository.save(Property.builder()
                .host(host)
                .title("Luxury Seaview Suite")
                .description("Stunning view")
                .city("Da Nang")
                .address("123 Vo Nguyen Giap")
                .pricePerNight(new BigDecimal("1500000"))
                .cleaningFee(new BigDecimal("100000"))
                .maxGuests(4)
                .bedrooms(2)
                .beds(2)
                .bathrooms(2)
                .ratingAvg(new BigDecimal("4.9"))
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .build());

        UserPrincipal principal = UserPrincipal.create(admin);
        adminAuth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminCanViewDashboardWithRealStatsAndBookingsList() throws Exception {
        Booking booking = bookingRepository.save(Booking.builder()
                .guest(guest)
                .property(property)
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(5))
                .guests(2)
                .nightlyPrice(new BigDecimal("1500000"))
                .cleaningFee(new BigDecimal("100000"))
                .serviceFee(new BigDecimal("225000"))
                .subtotalPrice(new BigDecimal("4825000"))
                .discountAmount(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("4825000"))
                .status(BookingStatus.CONFIRMED)
                .build());

        var result = mockMvc.perform(get("/admin/bookings")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/bookings"))
                .andExpect(model().attributeExists("stats", "bookingPage"))
                .andReturn();

        DashboardStatsResponse stats = (DashboardStatsResponse) result.getModelAndView().getModel().get("stats");
        assertThat(stats.getTotalUsers()).isEqualTo(3);
        assertThat(stats.getActiveHosts()).isEqualTo(1);
        assertThat(stats.getTotalBookings()).isEqualTo(1);
        assertThat(stats.getPlatformRevenue()).isEqualTo(225000.0);

        @SuppressWarnings("unchecked")
        Page<BookingResponse> bookingPage = (Page<BookingResponse>) result.getModelAndView().getModel().get("bookingPage");
        assertThat(bookingPage.getTotalElements()).isEqualTo(1);
        assertThat(bookingPage.getContent().getFirst().getPropertyTitle()).isEqualTo("Luxury Seaview Suite");
    }

    @Test
    void adminCanFilterBookingsByStatusAndKeyword() throws Exception {
        bookingRepository.save(Booking.builder()
                .guest(guest)
                .property(property)
                .checkInDate(LocalDate.now().plusDays(3))
                .checkOutDate(LocalDate.now().plusDays(5))
                .guests(2)
                .nightlyPrice(new BigDecimal("1500000"))
                .cleaningFee(new BigDecimal("100000"))
                .serviceFee(new BigDecimal("225000"))
                .subtotalPrice(new BigDecimal("3325000"))
                .discountAmount(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("3325000"))
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingRepository.save(Booking.builder()
                .guest(guest)
                .property(property)
                .checkInDate(LocalDate.now().plusDays(10))
                .checkOutDate(LocalDate.now().plusDays(12))
                .guests(2)
                .nightlyPrice(new BigDecimal("1500000"))
                .cleaningFee(new BigDecimal("100000"))
                .serviceFee(new BigDecimal("225000"))
                .subtotalPrice(new BigDecimal("3325000"))
                .discountAmount(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("3325000"))
                .status(BookingStatus.CANCELLED)
                .cancelledAt(java.time.Instant.now())
                .build());

        // Filter by status CONFIRMED
        var confirmedResult = mockMvc.perform(get("/admin/bookings")
                        .param("status", "CONFIRMED")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Page<BookingResponse> confirmedPage = (Page<BookingResponse>) confirmedResult.getModelAndView().getModel().get("bookingPage");
        assertThat(confirmedPage.getTotalElements()).isEqualTo(1);
        assertThat(confirmedPage.getContent().getFirst().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Filter by keyword "Da Nang"
        var keywordResult = mockMvc.perform(get("/admin/bookings")
                        .param("keyword", "Da Nang")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Page<BookingResponse> keywordPage = (Page<BookingResponse>) keywordResult.getModelAndView().getModel().get("bookingPage");
        assertThat(keywordPage.getTotalElements()).isEqualTo(2);
    }
}
