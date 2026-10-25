package com.stayhub.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** One Host save action, applied atomically to a sorted set of type/day rows. */
public record InventoryDaysRequest(@NotEmpty @Size(max=31) List<@NotNull @Valid InventoryDayRequest> days) {}
