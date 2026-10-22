package com.stayhub.search.dto;

import com.stayhub.property.PropertyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class SearchCriteria {
    private String location;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Integer guests;
    private Integer rooms;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<PropertyType> type;
    private Integer bedrooms;
    private Integer beds;
    private Integer bathrooms;
    private List<Long> amenities;
    private BigDecimal rating;
}
