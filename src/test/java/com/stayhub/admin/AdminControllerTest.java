package com.stayhub.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stayhub.admin.dto.AdminOverviewDto;
import com.stayhub.admin.dto.DashboardStatsResponse;
import com.stayhub.admin.dto.HostDetailDto;
import com.stayhub.admin.dto.HostPerformanceSummaryDto;
import com.stayhub.admin.dto.PropertyAdminViewDto;
import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.user.UserService;
import com.stayhub.user.dto.UserResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminController adminController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    @Test
    void adminRoot_redirectsToDashboard() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void viewDashboard_populatesModelAndRendersDashboard() throws Exception {
        when(adminService.getOverviewData()).thenReturn(AdminOverviewDto.builder().totalUsers(10).build());
        when(adminService.getDashboardStats()).thenReturn(DashboardStatsResponse.builder().totalUsers(10).build());
        when(adminService.getHostPerformances(eq(""), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(adminService.getBookings(eq(""), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(adminService.getPropertiesAdmin(eq(""), eq(""), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userService.getUsers(0, 10))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin/dashboard?tab=overview"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("tab", "overview", "stats", "hostPage", "bookingPage", "propertyPage", "userPage"));
    }

    @Test
    void viewHostDetail_populatesModelAndRendersHostDetail() throws Exception {
        HostDetailDto detail = HostDetailDto.builder()
                .hostProfile(HostPerformanceSummaryDto.builder().hostId(10L).fullName("Host A").build())
                .build();

        when(adminService.getHostDetail(10L)).thenReturn(detail);

        mockMvc.perform(get("/admin/hosts/10?subtab=properties"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/host-detail"))
                .andExpect(model().attribute("subtab", "properties"))
                .andExpect(model().attributeExists("hostDetail"));
    }

    @Test
    void viewDashboardAndBookings_rendersBookingsPage() throws Exception {
        when(adminService.getBookings(eq(""), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(adminService.getDashboardStats()).thenReturn(DashboardStatsResponse.builder().build());

        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/bookings"))
                .andExpect(model().attributeExists("bookingPage", "stats"));
    }
}
