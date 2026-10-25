package com.stayhub.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.stayhub.admin.dto.AdminOverviewDto;
import com.stayhub.admin.dto.AnomalyAlertDto;
import com.stayhub.admin.dto.HostDetailDto;
import com.stayhub.admin.dto.HostPerformanceSummaryDto;
import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingMapper;
import com.stayhub.booking.BookingRepository;
import com.stayhub.booking.BookingStatus;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import com.stayhub.review.Review;
import com.stayhub.review.ReviewMapper;
import com.stayhub.review.ReviewRepository;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminServiceExtendedTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BookingMapper bookingMapper;

    private ReviewMapper reviewMapper;
    private AdminServiceImpl adminService;

    private User host1;
    private User host2;

    @BeforeEach
    void setUp() {
        reviewMapper = new ReviewMapper();
        adminService = new AdminServiceImpl(
                userRepository,
                bookingRepository,
                propertyRepository,
                reviewRepository,
                bookingMapper,
                reviewMapper
        );

        host1 = User.builder()
                .email("host1@stayhub.com")
                .fullName("Host One")
                .phone("0912345678")
                .role(UserRole.HOST)
                .status(UserStatus.ACTIVE)
                .build();
        host1.setId(10L);

        host2 = User.builder()
                .email("host2@stayhub.com")
                .fullName("Host Two")
                .phone("0987654321")
                .role(UserRole.HOST)
                .status(UserStatus.LOCKED)
                .build();
        host2.setId(20L);
    }

    @Test
    void getOverviewData_calculatesGmvAndCommissionCorrectly() {
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByRoleAndStatus(UserRole.HOST, UserStatus.ACTIVE)).thenReturn(10L);
        when(propertyRepository.countByStatus(PropertyStatus.ACTIVE)).thenReturn(25L);
        when(bookingRepository.count()).thenReturn(50L);

        Booking b1 = Booking.builder()
                .status(BookingStatus.CONFIRMED)
                .totalPrice(new BigDecimal("1500000"))
                .serviceFee(new BigDecimal("150000"))
                .build();
        Booking b2 = Booking.builder()
                .status(BookingStatus.COMPLETED)
                .totalPrice(new BigDecimal("2000000"))
                .serviceFee(new BigDecimal("200000"))
                .build();
        Booking b3 = Booking.builder()
                .status(BookingStatus.CANCELLED)
                .totalPrice(new BigDecimal("3000000"))
                .serviceFee(new BigDecimal("300000"))
                .build();

        when(bookingRepository.findAll()).thenReturn(List.of(b1, b2, b3));
        when(userRepository.findByRole(UserRole.HOST)).thenReturn(List.of(host1));

        AdminOverviewDto overview = adminService.getOverviewData();

        assertThat(overview.getTotalUsers()).isEqualTo(100L);
        assertThat(overview.getActiveHosts()).isEqualTo(10L);
        assertThat(overview.getActiveProperties()).isEqualTo(25L);
        assertThat(overview.getTotalBookings()).isEqualTo(50L);
        assertThat(overview.getTotalGmv()).isEqualByComparingTo("3500000");
        assertThat(overview.getPlatformCommission()).isEqualByComparingTo("350000");
    }

    @Test
    void getHostPerformances_filtersAndComputesRates() {
        when(userRepository.findByRole(UserRole.HOST)).thenReturn(List.of(host1, host2));

        when(propertyRepository.countByHostId(10L)).thenReturn(3L);
        when(bookingRepository.countByHostId(10L)).thenReturn(10L);
        when(bookingRepository.countByHostIdAndStatus(10L, BookingStatus.COMPLETED)).thenReturn(8L);
        when(bookingRepository.countByHostIdAndStatus(10L, BookingStatus.CANCELLED)).thenReturn(2L);
        when(bookingRepository.sumGrossRevenueByHostId(10L)).thenReturn(new BigDecimal("25000000"));
        when(bookingRepository.sumCommissionByHostId(10L)).thenReturn(new BigDecimal("2500000"));
        when(reviewRepository.calculateAverageRatingByHostId(10L)).thenReturn(4.85);

        when(propertyRepository.countByHostId(20L)).thenReturn(1L);
        when(bookingRepository.countByHostId(20L)).thenReturn(0L);
        when(bookingRepository.countByHostIdAndStatus(20L, BookingStatus.COMPLETED)).thenReturn(0L);
        when(bookingRepository.countByHostIdAndStatus(20L, BookingStatus.CANCELLED)).thenReturn(0L);
        when(bookingRepository.sumGrossRevenueByHostId(20L)).thenReturn(BigDecimal.ZERO);
        when(bookingRepository.sumCommissionByHostId(20L)).thenReturn(BigDecimal.ZERO);
        when(reviewRepository.calculateAverageRatingByHostId(20L)).thenReturn(0.0);

        Page<HostPerformanceSummaryDto> page = adminService.getHostPerformances("", "", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        HostPerformanceSummaryDto firstHost = page.getContent().get(0);
        assertThat(firstHost.getHostId()).isEqualTo(10L);
        assertThat(firstHost.getGrossHostRevenue()).isEqualByComparingTo("25000000");
        assertThat(firstHost.getCompletionRate()).isEqualTo(80.0);
        assertThat(firstHost.getCancellationRate()).isEqualTo(20.0);
        assertThat(firstHost.getAverageRating()).isEqualTo(4.9);

        // Test zero bookings handling for host2
        HostPerformanceSummaryDto secondHost = page.getContent().get(1);
        assertThat(secondHost.getHostId()).isEqualTo(20L);
        assertThat(secondHost.getCompletionRate()).isEqualTo(0.0);
        assertThat(secondHost.getCancellationRate()).isEqualTo(0.0);
    }

    @Test
    void getHostDetail_returnsDetailedProfileAndAnomalies() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(host1));
        when(propertyRepository.countByHostId(10L)).thenReturn(2L);
        when(bookingRepository.countByHostId(10L)).thenReturn(10L);
        when(bookingRepository.countByHostIdAndStatus(10L, BookingStatus.COMPLETED)).thenReturn(4L);
        when(bookingRepository.countByHostIdAndStatus(10L, BookingStatus.CANCELLED)).thenReturn(5L); // 50% cancellation
        when(bookingRepository.sumGrossRevenueByHostId(10L)).thenReturn(new BigDecimal("12000000"));
        when(bookingRepository.sumCommissionByHostId(10L)).thenReturn(new BigDecimal("1200000"));
        when(reviewRepository.calculateAverageRatingByHostId(10L)).thenReturn(2.5);

        Property lowRatingProperty = Property.builder()
                .title("Low Rating Villa")
                .city("Da Nang")
                .ratingAvg(new BigDecimal("2.50"))
                .pricePerNight(new BigDecimal("1000000"))
                .cleaningFee(new BigDecimal("100000"))
                .propertyType(PropertyType.VILLA)
                .status(PropertyStatus.ACTIVE)
                .host(host1)
                .build();
        lowRatingProperty.setId(101L);

        when(propertyRepository.findByHostId(10L)).thenReturn(List.of(lowRatingProperty));
        when(bookingRepository.findByPropertyHostIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(reviewRepository.findByHostIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        HostDetailDto detail = adminService.getHostDetail(10L);

        assertThat(detail.getHostProfile().getHostId()).isEqualTo(10L);
        assertThat(detail.getProperties()).hasSize(1);
        assertThat(detail.getProperties().get(0).getTitle()).isEqualTo("Low Rating Villa");

        // High cancellation rate (> 30%) and low rating (< 3.0) should both trigger anomalies
        assertThat(detail.getAnomalies()).hasSize(2);
        assertThat(detail.getAnomalies())
                .extracting(AnomalyAlertDto::getType)
                .containsExactlyInAnyOrder("SUSPICIOUS_CANCELLATION", "LOW_RATING");
    }

    @Test
    void getHostDetail_throwsWhenNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getHostDetail(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Host not found with ID: 999");
    }
}
