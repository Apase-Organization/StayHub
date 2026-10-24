package com.stayhub.payment.quote;

import com.stayhub.inventory.InventoryBookingRequest;
import java.math.BigDecimal;
import java.time.Instant;

public record BookingCreatedResponse(long id, String policyVersion, String fixtureVersion, String quoteHash,
                                     String bookingStatus, InventoryBookingRequest.PaymentMode paymentMode,
                                     String currency, BigDecimal totalStay, BigDecimal dueOnline,
                                     BigDecimal balanceDueHost, Instant createdAt, Instant holdExpiresAt,
                                     long paymentAttemptId, long expectedVersion, String checkoutUrl,
                                     boolean replayed) {}
