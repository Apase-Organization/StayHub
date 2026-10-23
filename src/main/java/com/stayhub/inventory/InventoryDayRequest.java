package com.stayhub.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InventoryDayRequest(@NotNull LocalDate date, @Min(0) int total,
                                 @Min(0) int outOfOrder, boolean stopSale,
                                 @Min(0) long expectedVersion) {}
