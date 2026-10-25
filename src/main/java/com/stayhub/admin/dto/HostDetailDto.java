package com.stayhub.admin.dto;

import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.review.dto.ReviewResponse;
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
public class HostDetailDto {
    private HostPerformanceSummaryDto hostProfile;
    @Builder.Default
    private List<PropertyAdminViewDto> properties = new ArrayList<>();
    @Builder.Default
    private List<BookingResponse> recentBookings = new ArrayList<>();
    @Builder.Default
    private List<ReviewResponse> reviews = new ArrayList<>();
    @Builder.Default
    private List<AnomalyAlertDto> anomalies = new ArrayList<>();
}
