package com.stayhub.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stayhub.admin.dto.AdminOverviewDto;
import com.stayhub.admin.dto.AnomalyAlertDto;
import com.stayhub.admin.dto.HostDetailDto;
import com.stayhub.admin.dto.HostPerformanceSummaryDto;
import com.stayhub.admin.dto.PropertyAdminViewDto;
import com.stayhub.user.UserService;
import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
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
class AdminRestControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminRestController adminRestController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminRestController).build();
    }

    @Test
    void getOverview_returnsSuccessfulResponse() throws Exception {
        AdminOverviewDto overview = AdminOverviewDto.builder()
                .totalUsers(150)
                .activeHosts(12)
                .activeProperties(30)
                .totalBookings(85)
                .totalGmv(new BigDecimal("95000000"))
                .platformCommission(new BigDecimal("9500000"))
                .alertsCount(2)
                .build();

        when(adminService.getOverviewData()).thenReturn(overview);

        mockMvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(150))
                .andExpect(jsonPath("$.data.activeHosts").value(12))
                .andExpect(jsonPath("$.data.totalBookings").value(85))
                .andExpect(jsonPath("$.data.totalGmv").value(95000000));
    }

    @Test
    void getHosts_returnsPagedHostPerformances() throws Exception {
        HostPerformanceSummaryDto host = HostPerformanceSummaryDto.builder()
                .hostId(10L)
                .fullName("Nguyen Van Host")
                .email("host@stayhub.com")
                .status(UserStatus.ACTIVE)
                .propertiesCount(3)
                .bookingsCount(20)
                .grossHostRevenue(new BigDecimal("35000000"))
                .commissionEarned(new BigDecimal("3500000"))
                .averageRating(4.8)
                .build();

        when(adminService.getHostPerformances(eq(""), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(host), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/admin/hosts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].hostId").value(10))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Nguyen Van Host"))
                .andExpect(jsonPath("$.data.content[0].averageRating").value(4.8));
    }

    @Test
    void getHostDetail_returnsDetailedHostData() throws Exception {
        HostPerformanceSummaryDto hostProfile = HostPerformanceSummaryDto.builder()
                .hostId(10L)
                .fullName("Nguyen Van Host")
                .email("host@stayhub.com")
                .status(UserStatus.ACTIVE)
                .build();

        HostDetailDto hostDetail = HostDetailDto.builder()
                .hostProfile(hostProfile)
                .properties(List.of())
                .recentBookings(List.of())
                .reviews(List.of())
                .anomalies(List.of())
                .build();

        when(adminService.getHostDetail(10L)).thenReturn(hostDetail);

        mockMvc.perform(get("/api/v1/admin/hosts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hostProfile.hostId").value(10))
                .andExpect(jsonPath("$.data.hostProfile.fullName").value("Nguyen Van Host"));
    }

    @Test
    void getAnomalies_returnsList() throws Exception {
        AnomalyAlertDto alert = AnomalyAlertDto.builder()
                .type("EXPIRED_PENDING_BOOKING")
                .severity("MEDIUM")
                .entityType("BOOKING")
                .entityId(5L)
                .title("Đơn giữ chỗ quá hạn")
                .description("Quá 30 phút chưa thanh toán")
                .build();

        when(adminService.getSystemAnomalies()).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/admin/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].type").value("EXPIRED_PENDING_BOOKING"))
                .andExpect(jsonPath("$.data[0].entityId").value(5));
    }

    @Test
    void getProperties_returnsPagedProperties() throws Exception {
        PropertyAdminViewDto prop = PropertyAdminViewDto.builder()
                .id(1L)
                .title("Luxury Villa Da Nang")
                .city("Da Nang")
                .pricePerNight(new BigDecimal("2500000"))
                .build();

        when(adminService.getPropertiesAdmin(eq(""), eq(""), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(prop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/admin/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Luxury Villa Da Nang"));
    }

    @Test
    void lockUser_invokesUserService() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/15/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User locked successfully"));

        verify(userService).lockUser(15L);
    }
}
