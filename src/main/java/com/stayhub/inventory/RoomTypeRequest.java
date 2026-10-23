package com.stayhub.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomTypeRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull InventorySaleMode saleMode,
        @NotNull @Min(1) @Max(10000) Integer quantity,
        @NotNull @Min(1) Integer maxAdults,
        @NotNull @Min(1) Integer maxOccupants) {
    public enum InventorySaleMode { WHOLE_PLACE, ROOM_BASED }
}
