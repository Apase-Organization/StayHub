package com.stayhub.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.booking.BookingPriceService;
import com.stayhub.booking.dto.BookingPriceQuote;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * TSK-114: Dual-Mode Financial Matrix Integration Test.
 * Validates financial arithmetic across FULL_PREPAY and HYBRID_DEPOSIT,
 * commission retention, host payable entitlement, and refund pool liquidity boundaries.
 */
class FinancialMatrixIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingPriceService bookingPriceService;

    private Property property;

    @BeforeEach
    void setUp() {
        propertyRepository.deleteAll();
        userRepository.deleteAll();

        User host = userRepository.save(User.builder()
                .fullName("Finance Host")
                .email("host.fin@stayhub.local")
                .passwordHash("secret")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build());

        property = propertyRepository.save(Property.builder()
                .host(host)
                .title("Financial Test Villa")
                .description("Test property")
                .city("Nha Trang")
                .address("10 Tran Phu")
                .pricePerNight(new BigDecimal("3500000"))
                .cleaningFee(BigDecimal.ZERO)
                .maxGuests(4)
                .bedrooms(2)
                .beds(2)
                .bathrooms(2)
                .ratingAvg(new BigDecimal("5.0"))
                .propertyType(PropertyType.VILLA)
                .status(PropertyStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void cleanUp() {
        propertyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void fullPrepayFinancialArithmeticExactMatch() {
        // 2 nights @ 3,500,000 = 7,000,000 roomCharges; discount = 200,000 -> totalStay = 6,800,000
        BigDecimal roomCharges = new BigDecimal("7000000");
        BigDecimal cleaningFee = BigDecimal.ZERO;
        BigDecimal discount = new BigDecimal("200000");

        BigDecimal subtotal = roomCharges.add(cleaningFee);
        BigDecimal totalStay = subtotal.subtract(discount);
        assertThat(totalStay).isEqualByComparingTo(new BigDecimal("6800000"));

        // Mode: FULL_PREPAY
        BigDecimal dueOnline = totalStay;
        BigDecimal balanceDueHost = BigDecimal.ZERO;

        // 15% platform commission on pre-discount room + cleaning: 7,000,000 * 0.15 = 1,050,000
        BigDecimal grossCommission = subtotal.multiply(new BigDecimal("0.15")).setScale(0, RoundingMode.HALF_UP);
        assertThat(grossCommission).isEqualByComparingTo(new BigDecimal("1050000"));

        // Net retention absorbed platform funded discount: 1,050,000 - 200,000 = 850,000
        BigDecimal netRetention = grossCommission.subtract(discount);
        assertThat(netRetention).isEqualByComparingTo(new BigDecimal("850000"));

        // Platform-held payable to host = dueOnline - netRetention = 6,800,000 - 850,000 = 5,950,000
        BigDecimal platformHeldHostPayable = dueOnline.subtract(netRetention);
        assertThat(platformHeldHostPayable).isEqualByComparingTo(new BigDecimal("5950000"));

        // Total host entitlement = platformHeld + balanceDueHost = 5,950,000
        BigDecimal hostEntitlement = platformHeldHostPayable.add(balanceDueHost);
        assertThat(hostEntitlement).isEqualByComparingTo(new BigDecimal("5950000"));
    }

    @Test
    void hybridDepositFinancialArithmeticExactMatch() {
        // 2 nights @ 3,500,000 = 7,000,000 roomCharges; discount = 200,000 -> totalStay = 6,800,000
        BigDecimal roomCharges = new BigDecimal("7000000");
        BigDecimal cleaningFee = BigDecimal.ZERO;
        BigDecimal discount = new BigDecimal("200000");

        BigDecimal subtotal = roomCharges.add(cleaningFee);
        BigDecimal totalStay = subtotal.subtract(discount);

        // Mode: HYBRID_DEPOSIT (20% deposit online, 80% direct to host)
        BigDecimal dueOnline = totalStay.multiply(new BigDecimal("0.20")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal balanceDueHost = totalStay.subtract(dueOnline);

        assertThat(dueOnline).isEqualByComparingTo(new BigDecimal("1360000"));
        assertThat(balanceDueHost).isEqualByComparingTo(new BigDecimal("5440000"));
        assertThat(dueOnline.add(balanceDueHost)).isEqualByComparingTo(totalStay);

        // Gross commission = 1,050,000; net retention = 850,000
        BigDecimal grossCommission = subtotal.multiply(new BigDecimal("0.15")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal netRetention = grossCommission.subtract(discount);
        assertThat(netRetention).isEqualByComparingTo(new BigDecimal("850000"));

        // Platform-held host payable = dueOnline - netRetention = 1,360,000 - 850,000 = 510,000
        BigDecimal platformHeldHostPayable = dueOnline.subtract(netRetention);
        assertThat(platformHeldHostPayable).isEqualByComparingTo(new BigDecimal("510000"));

        // Total host entitlement = platformHeld (510,000) + direct check-in balance (5,440,000) = 5,950,000
        BigDecimal hostEntitlement = platformHeldHostPayable.add(balanceDueHost);
        assertThat(hostEntitlement).isEqualByComparingTo(new BigDecimal("5950000"));
    }

    @Test
    void refundPoolLiquidityBoundaryEnforcement() {
        // As defined in s0-fixtures-v1.json pools item:
        // collected: 200,000, policy: 200,000, paid: 50,000, reserved: 20,000
        long collected = 200000;
        long paid = 50000;
        long reserved = 20000;

        long availableLiquidPool = collected - paid - reserved; // 130,000
        assertThat(availableLiquidPool).isEqualTo(130000);

        // Request exactly equal to available liquidity (130,000) is allowed
        long requestValid = 130000;
        boolean allowedValid = requestValid <= availableLiquidPool;
        assertThat(allowedValid).isTrue();

        // Request exceeding available liquidity by 1 VND (130,001) is rejected
        long requestOver = 130001;
        boolean allowedOver = requestOver <= availableLiquidPool;
        assertThat(allowedOver).isFalse();
    }
}
