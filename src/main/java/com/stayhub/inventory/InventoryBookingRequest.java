package com.stayhub.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record InventoryBookingRequest(@Positive long propertyId,
                                      @NotNull LocalDate checkIn,
                                      @NotNull LocalDate checkOut,
                                      @NotBlank String currency,
                                      @NotNull PaymentMode paymentMode,
                                      @NotBlank @Size(max = 100) String idempotencyKey,
                                      @NotEmpty @Size(max = 20) List<@NotNull @Valid Item> items) {
    public enum PaymentMode { FULL_PREPAY, HYBRID_DEPOSIT }
    public record Item(@Positive long roomTypeId, @Positive long ratePlanId, @Positive int quantity,
                       @NotEmpty List<@NotNull @Valid RoomAllocation> roomAllocations) {}
    public record RoomAllocation(@Positive int adults, @NotNull List<@NotNull @Min(0) @Max(17) Integer> childrenAges) {}
}
