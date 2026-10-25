package com.stayhub.admin.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOverviewDto {
    private long totalUsers;
    private long activeHosts;
    private long activeProperties;
    private long totalBookings;
    private BigDecimal totalGmv;
    private BigDecimal platformCommission;
    private long alertsCount;
    @Builder.Default
    private List<AnomalyAlertDto> urgentAlerts = new ArrayList<>();
    @Builder.Default
    private List<HostPerformanceSummaryDto> topHosts = new ArrayList<>();
}
