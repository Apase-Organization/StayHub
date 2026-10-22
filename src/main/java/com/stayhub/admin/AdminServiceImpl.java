package com.stayhub.admin;

import com.stayhub.admin.dto.DashboardStatsResponse;
import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingMapper;
import com.stayhub.booking.BookingRepository;
import com.stayhub.booking.BookingStatus;
import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

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
}
