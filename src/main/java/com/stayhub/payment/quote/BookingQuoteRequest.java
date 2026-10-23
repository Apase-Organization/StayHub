package com.stayhub.payment.quote;

import com.stayhub.inventory.InventoryBookingRequest;
import com.stayhub.inventory.InventoryBookingRequest.Item;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record BookingQuoteRequest(@Positive long propertyId,
                                  @NotNull LocalDate checkIn,
                                  @NotNull LocalDate checkOut,
                                  @NotBlank String currency,
                                  @NotNull InventoryBookingRequest.PaymentMode paymentMode,
                                  @Positive int rooms,
                                  @Positive int adults,
                                  @NotNull List<Integer> childrenAges,
                                  @NotEmpty @Size(max=20) List<@NotNull @Valid Item> items,
                                  @Size(max=50) String discountCode) {}
