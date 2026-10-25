package com.stayhub.admin.dto;

import com.stayhub.user.UserStatus;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostPerformanceSummaryDto {
    private Long hostId;
    private String fullName;
    private String email;
    private String phone;
    private UserStatus status;
    private long propertiesCount;
    private long bookingsCount;
    private long completedBookingsCount;
    private long cancelledBookingsCount;
    private BigDecimal grossHostRevenue;
    private BigDecimal commissionEarned;
    private Double averageRating;

    public double getCompletionRate() {
        if (bookingsCount == 0) {
            return 0.0;
        }
        return Math.round(((double) completedBookingsCount / bookingsCount * 100.0) * 10.0) / 10.0;
    }

    public double getCancellationRate() {
        if (bookingsCount == 0) {
            return 0.0;
        }
        return Math.round(((double) cancelledBookingsCount / bookingsCount * 100.0) * 10.0) / 10.0;
    }
}
