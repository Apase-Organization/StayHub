package com.stayhub.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingRepository;
import com.stayhub.booking.BookingStatus;
import com.stayhub.auth.UserPrincipal;
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
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PaymentReturnWebTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private UserRepository userRepository;

    private Payment payment;
    private User guest;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();

        User host = userRepository.save(User.builder()
                .fullName("Test Host")
                .email("host.return@example.com")
                .passwordHash("secret")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build());

        guest = userRepository.save(User.builder()
                .fullName("Test Guest")
                .email("guest.return@example.com")
                .passwordHash("secret")
                .role(UserRole.GUEST)
                .status(UserStatus.ACTIVE)
                .build());

        Property property = propertyRepository.save(Property.builder()
                .host(host)
                .title("Cozy Villa")
                .description("Nice place")
                .city("Da Lat")
                .address("123 Pine St")
                .pricePerNight(BigDecimal.valueOf(1000000))
                .cleaningFee(BigDecimal.valueOf(100000))
                .propertyType(PropertyType.VILLA)
                .maxGuests(4)
                .bedrooms(2)
                .beds(2)
                .bathrooms(2)
                .ratingAvg(BigDecimal.ZERO)
                .status(PropertyStatus.ACTIVE)
                .build());

        Booking booking = bookingRepository.save(Booking.builder()
                .property(property)
                .guest(guest)
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .guests(2)
                .nightlyPrice(BigDecimal.valueOf(1000000))
                .cleaningFee(BigDecimal.valueOf(100000))
                .serviceFee(BigDecimal.valueOf(50000))
                .subtotalPrice(BigDecimal.valueOf(2150000))
                .totalPrice(BigDecimal.valueOf(2150000))
                .status(BookingStatus.PENDING_PAYMENT)
                .build());

        payment = paymentRepository.save(Payment.builder()
                .booking(booking)
                .paymentMethod(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .amount(booking.getTotalPrice())
                .provider(PaymentMethod.VNPAY.name())
                .currency("VND")
                .providerTxnRef("STAYHUB-" + booking.getId())
                .build());
    }

    @Test
    void vnpayReturnIsStrictlyReadOnlyAndDoesNotMutatePaymentStatus() throws Exception {
        // TSK-080: Redirect to return URL should render view without altering payment or booking status
        mockMvc.perform(get("/payments/vnpay/return")
                        .param("vnp_TxnRef", payment.getProviderTxnRef())
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_Amount", "215000000"))
                .andExpect(status().isOk())
                .andExpect(view().name("payment/vnpay-return"))
                .andExpect(model().attribute("txnRef", payment.getProviderTxnRef()))
                .andExpect(model().attribute("responseCode", "00"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kiểm tra lại trạng thái")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không thanh toán lần hai")));

        // Verify database entity is still PENDING (no financial mutation on browser redirect)
        Payment fresh = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(fresh.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void paymentStatusPollingEndpointReturnsPendingStatus() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/status")
                        .param("txnRef", payment.getProviderTxnRef())
                        .with(user(UserPrincipal.create(guest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.bookingStatus").value("PENDING_PAYMENT"));
    }

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
    }
}
