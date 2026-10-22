package com.stayhub.booking;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.payment.PaymentRepository;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import com.stayhub.review.ReviewRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class BookingWebIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private UserRepository userRepository;

    private User guest;
    private User host;
    private Property property;
    private UsernamePasswordAuthenticationToken authentication;
    private UsernamePasswordAuthenticationToken hostAuthentication;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
        host = userRepository.save(user("booking-web-host@example.com", UserRole.HOST));
        guest = userRepository.save(user("booking-web-guest@example.com", UserRole.GUEST));
        property = propertyRepository.save(property(host));
        UserPrincipal principal = UserPrincipal.create(guest);
        authentication = new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());
        UserPrincipal hostPrincipal = UserPrincipal.create(host);
        hostAuthentication = new UsernamePasswordAuthenticationToken(
                hostPrincipal, hostPrincipal.getPassword(), hostPrincipal.getAuthorities());
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
    void availabilityApiReturnsSuccessForFreeDates() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/check-availability")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %d,
                                  "checkInDate": "%s",
                                  "checkOutDate": "%s",
                                  "guests": 2
                                }
                                """.formatted(property.getId(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.priceQuote.totalPrice").value(2300000.00));
    }

    @Test
    void availabilityApiReturnsRoomNotAvailableForConflicts() throws Exception {
        createExistingBooking(LocalDate.now().plusDays(8), LocalDate.now().plusDays(10));

        mockMvc.perform(post("/api/v1/bookings/check-availability")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %d,
                                  "checkInDate": "%s",
                                  "checkOutDate": "%s",
                                  "guests": 2
                                }
                                """.formatted(property.getId(), LocalDate.now().plusDays(9), LocalDate.now().plusDays(11))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ERR_ROOM_NOT_AVAILABLE"));
    }

    @Test
    void mvcBookingFlowCreatesPendingBookingAndShowsPaymentPage() throws Exception {
        mockMvc.perform(get("/properties/{id}/book", property.getId())
                        .param("checkInDate", LocalDate.now().plusDays(12).toString())
                        .param("checkOutDate", LocalDate.now().plusDays(14).toString())
                        .param("guests", "2")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/booking"))
                .andExpect(model().attributeExists("property", "bookingRequest", "priceQuote"));

        mockMvc.perform(post("/bookings")
                        .with(authentication(authentication))
                        .with(csrf())
                        .param("propertyId", property.getId().toString())
                        .param("checkInDate", LocalDate.now().plusDays(12).toString())
                        .param("checkOutDate", LocalDate.now().plusDays(14).toString())
                        .param("guests", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?*"));

        Booking booking = bookingRepository.findAll().getFirst();
        mockMvc.perform(get("/bookings")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/my-bookings"))
                .andExpect(model().attribute("tab", "pending"))
                .andExpect(model().attributeExists("bookings"));

        mockMvc.perform(get("/bookings/{id}/payment", booking.getId())
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/payment"))
                .andExpect(model().attributeExists("booking"));

        mockMvc.perform(get("/bookings")
                        .param("tab", "pending")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/my-bookings"))
                .andExpect(model().attributeExists("bookings"));

        mockMvc.perform(get("/bookings/{id}", booking.getId())
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/booking-detail"))
                .andExpect(model().attribute("viewer", "guest"));
    }

    @Test
    void mvcBookingFlowSupportsDualPaymentModeSelection() throws Exception {
        mockMvc.perform(get("/properties/{id}/book", property.getId())
                        .param("checkInDate", LocalDate.now().plusDays(15).toString())
                        .param("checkOutDate", LocalDate.now().plusDays(17).toString())
                        .param("guests", "2")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/booking"))
                .andExpect(model().attributeExists("property", "bookingRequest", "priceQuote"));

        mockMvc.perform(post("/bookings")
                        .with(authentication(authentication))
                        .with(csrf())
                        .param("propertyId", property.getId().toString())
                        .param("checkInDate", LocalDate.now().plusDays(15).toString())
                        .param("checkOutDate", LocalDate.now().plusDays(17).toString())
                        .param("guests", "2")
                        .param("paymentMode", "HYBRID_DEPOSIT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?*"));
    }

    @Test
    void hostCanReadRequestsAndAcceptBookingThroughApiAndMvc() throws Exception {
        var booking = createExistingBooking(LocalDate.now().plusDays(20), LocalDate.now().plusDays(22));

        mockMvc.perform(get("/api/v1/bookings/host/requests")
                        .with(authentication(hostAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(booking.getId()));

        mockMvc.perform(post("/api/v1/bookings/host/{id}/accept", booking.getId())
                        .with(authentication(hostAuthentication))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(get("/host/bookings/{id}", booking.getId())
                        .with(authentication(hostAuthentication)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/booking-detail"))
                .andExpect(model().attribute("viewer", "host"));
    }

    @Test
    void guestCanCancelBookingThroughMvc() throws Exception {
        var booking = bookingService.createBooking(guest.getId(), bookingRequest(
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(32), 2));

        mockMvc.perform(post("/bookings/{id}/cancel", booking.getId())
                        .with(authentication(authentication))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/bookings/*"));
    }

    @Test
    void guestCanReviewCompletedBookingAndPropertyDetailShowsReviews() throws Exception {
        var booking = createExistingBooking(LocalDate.now().plusDays(40), LocalDate.now().plusDays(42));
        bookingService.acceptBooking(host.getId(), booking.getId());
        bookingService.completeBooking(host.getId(), booking.getId());

        mockMvc.perform(post("/bookings/{id}/reviews", booking.getId())
                        .with(authentication(authentication))
                        .with(csrf())
                        .param("rating", "5")
                        .param("comment", "A very reliable stay."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/bookings/*"));

        mockMvc.perform(get("/properties/{id}", property.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("property/property-detail"))
                .andExpect(model().attributeExists("reviews"));
    }

    private Booking createExistingBooking(LocalDate checkInDate, LocalDate checkOutDate) {
        return bookingRepository.save(Booking.builder()
                .property(property)
                .guest(guest)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .guests(2)
                .nightlyPrice(new BigDecimal("1000000.00"))
                .cleaningFee(new BigDecimal("100000.00"))
                .serviceFee(new BigDecimal("200000.00"))
                .subtotalPrice(new BigDecimal("2300000.00"))
                .discountAmount(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("2300000.00"))
                .status(BookingStatus.PENDING)
                .build());
    }

    private com.stayhub.booking.dto.BookingCreateRequest bookingRequest(LocalDate checkInDate, LocalDate checkOutDate, int guests) {
        var request = new com.stayhub.booking.dto.BookingCreateRequest();
        request.setPropertyId(property.getId());
        request.setCheckInDate(checkInDate);
        request.setCheckOutDate(checkOutDate);
        request.setGuests(guests);
        return request;
    }

    private User user(String email, UserRole role) {
        return User.builder()
                .email(email)
                .passwordHash("password-hash")
                .fullName(role.name() + " User")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Property property(User host) {
        return Property.builder()
                .host(host)
                .title("Booking Web House")
                .description("A bright stay near the beach.")
                .address("1 Ocean Street")
                .city("Da Nang")
                .pricePerNight(new BigDecimal("1000000.00"))
                .cleaningFee(new BigDecimal("100000.00"))
                .maxGuests(4)
                .bedrooms(2)
                .beds(2)
                .bathrooms(2)
                .propertyType(PropertyType.HOUSE)
                .status(PropertyStatus.ACTIVE)
                .ratingAvg(BigDecimal.ZERO)
                .build();
    }
}
