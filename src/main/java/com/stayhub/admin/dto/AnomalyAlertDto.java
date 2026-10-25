package com.stayhub.admin.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyAlertDto {
    private String type; // SUSPICIOUS_CANCELLATION, LOW_RATING, EXPIRED_PENDING_BOOKING, POTENTIAL_SELF_BOOKING
    private String severity; // HIGH, MEDIUM, LOW
    private String entityType; // BOOKING, PROPERTY, HOST
    private Long entityId;
    private String title;
    private String description;
    private Instant createdAt;
}
