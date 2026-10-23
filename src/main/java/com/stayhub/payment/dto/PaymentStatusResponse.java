package com.stayhub.payment.dto;

import com.stayhub.booking.BookingStatus;
import com.stayhub.payment.PaymentStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentStatusResponse {
    private Long bookingId;
    private BookingStatus bookingStatus;
    private PaymentStatus paymentStatus;
    private String transactionId;
    private String providerTransactionNo;
    private String paymentMode;
    private String onlinePaymentStatus;
    private java.math.BigDecimal onlinePaidVerified;
    private java.math.BigDecimal balanceDueHost;
    private java.math.BigDecimal directPaidAttested;
    private java.math.BigDecimal directOutstanding;
    private String directReceiptStatus;
    private String refundStatus;
    private Long expectedVersion;
}
