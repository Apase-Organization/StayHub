package com.stayhub.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomTypeUpdateRequest(@NotBlank @Size(max = 255) String name,
                                    @NotNull @Min(1) @Max(10000) Integer quantity,
                                    @NotNull @Min(1) Integer maxAdults,
                                    @NotNull @Min(1) Integer maxOccupants,
                                    @NotNull @Min(0) Long expectedVersion) {}
