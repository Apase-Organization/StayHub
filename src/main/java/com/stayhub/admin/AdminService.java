package com.stayhub.admin;

import com.stayhub.admin.dto.AdminOverviewDto;
import com.stayhub.admin.dto.AnomalyAlertDto;
import com.stayhub.admin.dto.DashboardStatsResponse;
import com.stayhub.admin.dto.HostDetailDto;
import com.stayhub.admin.dto.HostPerformanceSummaryDto;
import com.stayhub.admin.dto.PropertyAdminViewDto;
import com.stayhub.booking.dto.BookingResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {
    DashboardStatsResponse getDashboardStats();
    Page<BookingResponse> getBookings(String keyword, String status, Pageable pageable);

    AdminOverviewDto getOverviewData();
    Page<HostPerformanceSummaryDto> getHostPerformances(String keyword, String status, Pageable pageable);
    HostDetailDto getHostDetail(Long hostId);
    List<AnomalyAlertDto> getSystemAnomalies();
    Page<PropertyAdminViewDto> getPropertiesAdmin(String keyword, String status, String city, Pageable pageable);
}
