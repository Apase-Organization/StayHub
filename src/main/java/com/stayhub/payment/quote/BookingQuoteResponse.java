package com.stayhub.payment.quote;

import com.stayhub.inventory.InventoryBookingRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BookingQuoteResponse(String quoteId, String policyVersion, String fixtureVersion,
                                    String quoteHash, Instant quoteExpiresAt,
                                    InventoryBookingRequest.PaymentMode paymentMode,
                                    List<InventoryBookingRequest.PaymentMode> allowedPaymentModes,
                                    String currency, BigDecimal subtotal, BigDecimal discount,
                                    BigDecimal totalStay, BigDecimal dueOnline, BigDecimal balanceDueHost,
                                    BigDecimal roomCharges, BigDecimal cleaningFee, BigDecimal taxes,
                                    List<QuoteItem> items) {
    public record QuoteItem(long roomTypeId, long ratePlanId, int quantity,
                            List<InventoryBookingRequest.RoomAllocation> roomAllocations,
                            int availableQuantity, List<NightlyPrice> nightlyPerRoom,
                            BigDecimal roomCharges) {}
    public record NightlyPrice(LocalDate date, BigDecimal roomCharge) {}
}
