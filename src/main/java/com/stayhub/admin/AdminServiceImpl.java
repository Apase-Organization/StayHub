package com.stayhub.admin;

import com.stayhub.admin.dto.AdminOverviewDto;
import com.stayhub.admin.dto.AnomalyAlertDto;
import com.stayhub.admin.dto.DashboardStatsResponse;
import com.stayhub.admin.dto.HostDetailDto;
import com.stayhub.admin.dto.HostPerformanceSummaryDto;
import com.stayhub.admin.dto.PropertyAdminViewDto;
import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingMapper;
import com.stayhub.booking.BookingRepository;
import com.stayhub.booking.BookingStatus;
import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.review.Review;
import com.stayhub.review.ReviewMapper;
import com.stayhub.review.ReviewRepository;
import com.stayhub.review.dto.ReviewResponse;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final ReviewRepository reviewRepository;
    private final BookingMapper bookingMapper;
    private final ReviewMapper reviewMapper;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long activeHosts = userRepository.countByRoleAndStatus(UserRole.HOST, UserStatus.ACTIVE);
        long totalBookings = bookingRepository.count();

        double platformRevenue = bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED)
                .map(Booking::getServiceFee)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeHosts(activeHosts)
                .totalBookings(totalBookings)
                .platformRevenue(platformRevenue)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOverviewDto getOverviewData() {
        long totalUsers = userRepository.count();
        long activeHosts = userRepository.countByRoleAndStatus(UserRole.HOST, UserStatus.ACTIVE);
        long activeProperties = propertyRepository.countByStatus(PropertyStatus.ACTIVE);
        long totalBookings = bookingRepository.count();

        List<Booking> paidBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED)
                .toList();

        BigDecimal totalGmv = paidBookings.stream()
                .map(Booking::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal platformCommission = paidBookings.stream()
                .map(Booking::getServiceFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AnomalyAlertDto> alerts = getSystemAnomalies();
        Page<HostPerformanceSummaryDto> hostPage = getHostPerformances("", "", PageRequest.of(0, 5));

        return AdminOverviewDto.builder()
                .totalUsers(totalUsers)
                .activeHosts(activeHosts)
                .activeProperties(activeProperties)
                .totalBookings(totalBookings)
                .totalGmv(totalGmv)
                .platformCommission(platformCommission)
                .alertsCount(alerts.size())
                .urgentAlerts(alerts.stream().limit(5).toList())
                .topHosts(hostPage.getContent())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HostPerformanceSummaryDto> getHostPerformances(String keyword, String status, Pageable pageable) {
        List<User> hosts = userRepository.findByRole(UserRole.HOST);
        Stream<User> stream = hosts.stream();

        if (status != null && !status.isBlank()) {
            try {
                UserStatus targetStatus = UserStatus.valueOf(status.trim().toUpperCase());
                stream = stream.filter(u -> u.getStatus() == targetStatus);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            stream = stream.filter(u ->
                    (u.getFullName() != null && u.getFullName().toLowerCase().contains(lower)) ||
                    (u.getEmail() != null && u.getEmail().toLowerCase().contains(lower)) ||
                    (u.getPhone() != null && u.getPhone().toLowerCase().contains(lower)) ||
                    u.getId().toString().contains(lower)
            );
        }

        List<HostPerformanceSummaryDto> dtoList = stream
                .map(this::buildHostPerformanceSummary)
                .sorted(Comparator.comparing(HostPerformanceSummaryDto::getGrossHostRevenue, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int total = dtoList.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<HostPerformanceSummaryDto> paged = start <= end ? dtoList.subList(start, end) : List.of();

        return new PageImpl<>(paged, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public HostDetailDto getHostDetail(Long hostId) {
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Host not found with ID: " + hostId));

        HostPerformanceSummaryDto hostProfile = buildHostPerformanceSummary(host);

        List<Property> properties = propertyRepository.findByHostId(hostId);
        List<PropertyAdminViewDto> propertyDtos = properties.stream()
                .map(this::mapPropertyToAdminView)
                .toList();

        List<Booking> bookings = bookingRepository.findByPropertyHostIdOrderByCreatedAtDesc(hostId);
        List<BookingResponse> bookingDtos = bookings.stream()
                .map(bookingMapper::toResponse)
                .toList();

        List<Review> reviews = reviewRepository.findByHostIdOrderByCreatedAtDesc(hostId);
        List<ReviewResponse> reviewDtos = reviews.stream()
                .map(reviewMapper::toResponse)
                .toList();

        List<AnomalyAlertDto> hostAnomalies = new ArrayList<>();
        if (hostProfile.getBookingsCount() >= 3 && hostProfile.getCancellationRate() >= 30.0) {
            hostAnomalies.add(AnomalyAlertDto.builder()
                    .type("SUSPICIOUS_CANCELLATION")
                    .severity("HIGH")
                    .entityType("HOST")
                    .entityId(hostId)
                    .title("Tỷ lệ huỷ phòng cao bất thường")
                    .description("Host này có tỷ lệ huỷ đơn lên tới " + hostProfile.getCancellationRate() + "% (" + hostProfile.getCancelledBookingsCount() + "/" + hostProfile.getBookingsCount() + " đơn).")
                    .createdAt(Instant.now())
                    .build());
        }

        for (Property p : properties) {
            if (p.getRatingAvg() != null && p.getRatingAvg().compareTo(BigDecimal.valueOf(3.0)) < 0 && p.getRatingAvg().compareTo(BigDecimal.ZERO) > 0) {
                hostAnomalies.add(AnomalyAlertDto.builder()
                        .type("LOW_RATING")
                        .severity("MEDIUM")
                        .entityType("PROPERTY")
                        .entityId(p.getId())
                        .title("Chỗ nghỉ có điểm đánh giá thấp")
                        .description("Chỗ nghỉ \"" + p.getTitle() + "\" có điểm đánh giá thấp (" + p.getRatingAvg() + "/5 sao).")
                        .createdAt(Instant.now())
                        .build());
            }
        }

        return HostDetailDto.builder()
                .hostProfile(hostProfile)
                .properties(propertyDtos)
                .recentBookings(bookingDtos)
                .reviews(reviewDtos)
                .anomalies(hostAnomalies)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnomalyAlertDto> getSystemAnomalies() {
        List<AnomalyAlertDto> alerts = new ArrayList<>();

        // 1. Expired pending payment bookings (older than 30 minutes)
        Instant expiryThreshold = Instant.now().minus(Duration.ofMinutes(30));
        List<Booking> pendingBookings = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING_PAYMENT, expiryThreshold);
        for (Booking b : pendingBookings) {
            alerts.add(AnomalyAlertDto.builder()
                    .type("EXPIRED_PENDING_BOOKING")
                    .severity("MEDIUM")
                    .entityType("BOOKING")
                    .entityId(b.getId())
                    .title("Đơn giữ chỗ quá hạn thanh toán")
                    .description("Đơn #" + b.getId() + " tại \"" + (b.getProperty() != null ? b.getProperty().getTitle() : "N/A") + "\" tạo lúc " + b.getCreatedAt() + " chưa được thanh toán.")
                    .createdAt(b.getCreatedAt())
                    .build());
        }

        // 2. Properties with low rating
        List<Property> lowRatedProperties = propertyRepository.findByRatingAvgLessThanAndStatus(BigDecimal.valueOf(3.0), PropertyStatus.ACTIVE);
        for (Property p : lowRatedProperties) {
            if (p.getRatingAvg() != null && p.getRatingAvg().compareTo(BigDecimal.ZERO) > 0) {
                alerts.add(AnomalyAlertDto.builder()
                        .type("LOW_RATING")
                        .severity("HIGH")
                        .entityType("PROPERTY")
                        .entityId(p.getId())
                        .title("Chỗ nghỉ rating dưới 3 sao")
                        .description("Chỗ nghỉ \"" + p.getTitle() + "\" tại " + p.getCity() + " chỉ đạt " + p.getRatingAvg() + " sao.")
                        .createdAt(p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt())
                        .build());
            }
        }

        // 3. High value cancelled bookings (> 5,000,000 VND)
        List<Booking> cancelledBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
                .filter(b -> b.getTotalPrice() != null && b.getTotalPrice().compareTo(BigDecimal.valueOf(5000000)) >= 0)
                .sorted(Comparator.comparing(Booking::getCreatedAt).reversed())
                .limit(10)
                .toList();

        for (Booking b : cancelledBookings) {
            alerts.add(AnomalyAlertDto.builder()
                    .type("SUSPICIOUS_CANCELLATION")
                    .severity("HIGH")
                    .entityType("BOOKING")
                    .entityId(b.getId())
                    .title("Đơn hàng giá trị cao bị huỷ")
                    .description("Đơn #" + b.getId() + " trị giá " + String.format("%,d VND", b.getTotalPrice().longValue()) + " đã bị huỷ.")
                    .createdAt(b.getCancelledAt() != null ? b.getCancelledAt() : b.getCreatedAt())
                    .build());
        }

        return alerts;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyAdminViewDto> getPropertiesAdmin(String keyword, String status, String city, Pageable pageable) {
        List<Property> properties = propertyRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Stream<Property> stream = properties.stream();

        if (status != null && !status.isBlank()) {
            try {
                PropertyStatus targetStatus = PropertyStatus.valueOf(status.trim().toUpperCase());
                stream = stream.filter(p -> p.getStatus() == targetStatus);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (city != null && !city.isBlank()) {
            String lowerCity = city.trim().toLowerCase();
            stream = stream.filter(p -> p.getCity() != null && p.getCity().toLowerCase().contains(lowerCity));
        }

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            stream = stream.filter(p ->
                    (p.getTitle() != null && p.getTitle().toLowerCase().contains(lower)) ||
                    (p.getAddress() != null && p.getAddress().toLowerCase().contains(lower)) ||
                    (p.getHost() != null && p.getHost().getFullName() != null && p.getHost().getFullName().toLowerCase().contains(lower)) ||
                    p.getId().toString().contains(lower)
            );
        }

        List<PropertyAdminViewDto> dtoList = stream
                .map(this::mapPropertyToAdminView)
                .toList();

        int total = dtoList.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<PropertyAdminViewDto> paged = start <= end ? dtoList.subList(start, end) : List.of();

        return new PageImpl<>(paged, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookings(String keyword, String status, Pageable pageable) {
        List<Booking> bookings = bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Stream<Booking> stream = bookings.stream();

        if (status != null && !status.isBlank()) {
            try {
                BookingStatus targetStatus = BookingStatus.valueOf(status.trim().toUpperCase());
                stream = stream.filter(b -> b.getStatus() == targetStatus);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            stream = stream.filter(b ->
                    (b.getGuest() != null && (
                            (b.getGuest().getFullName() != null && b.getGuest().getFullName().toLowerCase().contains(lower)) ||
                            (b.getGuest().getEmail() != null && b.getGuest().getEmail().toLowerCase().contains(lower))
                    )) ||
                    (b.getProperty() != null && (
                            (b.getProperty().getTitle() != null && b.getProperty().getTitle().toLowerCase().contains(lower)) ||
                            (b.getProperty().getCity() != null && b.getProperty().getCity().toLowerCase().contains(lower)) ||
                            (b.getProperty().getHost() != null && b.getProperty().getHost().getFullName() != null && b.getProperty().getHost().getFullName().toLowerCase().contains(lower))
                    )) ||
                    (b.getId() != null && b.getId().toString().contains(lower))
            );
        }

        List<BookingResponse> filtered = stream
                .map(bookingMapper::toResponse)
                .toList();

        int total = filtered.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<BookingResponse> paged = start <= end ? filtered.subList(start, end) : List.of();

        return new PageImpl<>(paged, pageable, total);
    }

    private HostPerformanceSummaryDto buildHostPerformanceSummary(User host) {
        Long hostId = host.getId();
        long propertiesCount = propertyRepository.countByHostId(hostId);
        long bookingsCount = bookingRepository.countByHostId(hostId);
        long completedBookingsCount = bookingRepository.countByHostIdAndStatus(hostId, BookingStatus.COMPLETED);
        long cancelledBookingsCount = bookingRepository.countByHostIdAndStatus(hostId, BookingStatus.CANCELLED);
        BigDecimal grossHostRevenue = bookingRepository.sumGrossRevenueByHostId(hostId);
        BigDecimal commissionEarned = bookingRepository.sumCommissionByHostId(hostId);
        Double averageRating = reviewRepository.calculateAverageRatingByHostId(hostId);

        return HostPerformanceSummaryDto.builder()
                .hostId(hostId)
                .fullName(host.getFullName())
                .email(host.getEmail())
                .phone(host.getPhone())
                .status(host.getStatus())
                .propertiesCount(propertiesCount)
                .bookingsCount(bookingsCount)
                .completedBookingsCount(completedBookingsCount)
                .cancelledBookingsCount(cancelledBookingsCount)
                .grossHostRevenue(grossHostRevenue != null ? grossHostRevenue : BigDecimal.ZERO)
                .commissionEarned(commissionEarned != null ? commissionEarned : BigDecimal.ZERO)
                .averageRating(averageRating != null ? Math.round(averageRating * 10.0) / 10.0 : 0.0)
                .build();
    }

    private PropertyAdminViewDto mapPropertyToAdminView(Property p) {
        return PropertyAdminViewDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .hostId(p.getHost() != null ? p.getHost().getId() : null)
                .hostName(p.getHost() != null ? p.getHost().getFullName() : "")
                .hostEmail(p.getHost() != null ? p.getHost().getEmail() : "")
                .city(p.getCity())
                .address(p.getAddress())
                .propertyType(p.getPropertyType())
                .pricePerNight(p.getPricePerNight())
                .cleaningFee(p.getCleaningFee())
                .status(p.getStatus())
                .ratingAvg(p.getRatingAvg())
                .totalBookings(bookingRepository.findByPropertyIdAndStatus(p.getId(), BookingStatus.CONFIRMED).size() +
                               bookingRepository.findByPropertyIdAndStatus(p.getId(), BookingStatus.COMPLETED).size())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
