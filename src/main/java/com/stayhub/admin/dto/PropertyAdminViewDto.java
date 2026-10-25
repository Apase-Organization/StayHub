package com.stayhub.admin.dto;

import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyAdminViewDto {
    private Long id;
    private String title;
    private Long hostId;
    private String hostName;
    private String hostEmail;
    private String city;
    private String address;
    private PropertyType propertyType;
    private BigDecimal pricePerNight;
    private BigDecimal cleaningFee;
    private PropertyStatus status;
    private BigDecimal ratingAvg;
    private long totalBookings;
    private Instant createdAt;
}
