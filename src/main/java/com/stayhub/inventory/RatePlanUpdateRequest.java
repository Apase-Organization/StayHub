package com.stayhub.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record RatePlanUpdateRequest(@NotBlank @Size(max = 255) String name,
                                    @NotBlank @Size(max = 100) String policyVersion,
                                    @NotNull @DecimalMin(value = "1") @Digits(integer = 19, fraction = 0) BigDecimal nightlyPrice,
                                    @NotEmpty Set<RatePlanRequest.PaymentMode> allowedPaymentModes,
                                    Set<LocalDate> stopSaleDates,
                                    @NotNull Long expectedVersion) {}
