package com.stayhub.property.dto;

import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertySummary {
    private Long id;
    private String title;
    private String city;
    private BigDecimal pricePerNight;
    private BigDecimal ratingAvg;
    private PropertyType propertyType;
    private PropertyStatus status;
    private String coverImageUrl;
    private Integer nights;
    private BigDecimal totalStay;
}
