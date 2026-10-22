package com.stayhub.admin;

import com.stayhub.admin.dto.DashboardStatsResponse;
import com.stayhub.booking.dto.BookingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {
    DashboardStatsResponse getDashboardStats();
    Page<BookingResponse> getBookings(String keyword, String status, Pageable pageable);
}
