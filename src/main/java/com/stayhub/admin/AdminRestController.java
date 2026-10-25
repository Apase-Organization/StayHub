package com.stayhub.admin;

import com.stayhub.admin.dto.AdminOverviewDto;
import com.stayhub.admin.dto.AnomalyAlertDto;
import com.stayhub.admin.dto.HostDetailDto;
import com.stayhub.admin.dto.HostPerformanceSummaryDto;
import com.stayhub.admin.dto.PropertyAdminViewDto;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserService;
import com.stayhub.user.dto.UserResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminRestController {

    private final UserService userService;
    private final AdminService adminService;

    @GetMapping("/dashboard/overview")
    public ApiResponse<AdminOverviewDto> getOverview() {
        return ApiResponse.success(adminService.getOverviewData(), "Dashboard overview retrieved successfully");
    }

    @GetMapping("/hosts")
    public ApiResponse<Page<HostPerformanceSummaryDto>> getHosts(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ApiResponse.success(
                adminService.getHostPerformances(keyword, status, PageRequest.of(safePage, safeSize)),
                "Host performances retrieved successfully"
        );
    }

    @GetMapping("/hosts/{id}")
    public ApiResponse<HostDetailDto> getHostDetail(@PathVariable Long id) {
        return ApiResponse.success(adminService.getHostDetail(id), "Host details retrieved successfully");
    }

    @GetMapping("/anomalies")
    public ApiResponse<List<AnomalyAlertDto>> getAnomalies() {
        return ApiResponse.success(adminService.getSystemAnomalies(), "System anomalies retrieved successfully");
    }

    @GetMapping("/properties")
    public ApiResponse<Page<PropertyAdminViewDto>> getProperties(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ApiResponse.success(
                adminService.getPropertiesAdmin(keyword, status, city, PageRequest.of(safePage, safeSize)),
                "Properties retrieved successfully"
        );
    }

    @PostMapping("/users/{id}/lock")
    public ApiResponse<Void> lockUser(@PathVariable Long id) {
        userService.lockUser(id);
        return ApiResponse.success(null, "User locked successfully");
    }

    @PostMapping("/users/{id}/unlock")
    public ApiResponse<Void> unlockUser(@PathVariable Long id) {
        userService.unlockUser(id);
        return ApiResponse.success(null, "User unlocked successfully");
    }

    @PostMapping("/users/{id}/role")
    public ApiResponse<UserResponse> changeRole(@PathVariable Long id, @RequestParam UserRole role) {
        return ApiResponse.success(userService.changeRole(id, role), "User role updated successfully");
    }
}
