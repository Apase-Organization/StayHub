package com.stayhub.booking.dto;

import com.stayhub.common.validation.DateRangeRequest;
import com.stayhub.common.validation.ValidDateRange;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
@ValidDateRange
public class BookingCreateRequest implements DateRangeRequest {
    @NotNull
    private Long propertyId;

    @NotNull
    @FutureOrPresent
    private LocalDate checkInDate;

    @NotNull
    private LocalDate checkOutDate;

    @NotNull
    @Min(1)
    private Integer guests;

    private String discountCode;

    private String paymentMode = "FULL_PREPAY";
}
