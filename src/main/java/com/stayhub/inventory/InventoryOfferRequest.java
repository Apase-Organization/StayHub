package com.stayhub.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record InventoryOfferRequest(LocalDate checkIn, LocalDate checkOut,
                                    @NotBlank String currency,
                                    @NotNull InventoryBookingRequest.PaymentMode paymentMode,
                                    @NotEmpty @Size(max = 20) List<@NotNull @Valid RoomOccupancy> roomOccupancies) {
    public record RoomOccupancy(@Min(1) int adults,
                                @NotNull List<@NotNull @Min(0) @Max(17) Integer> childrenAges) {}
}
