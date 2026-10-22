package com.stayhub.booking.dto;

import com.stayhub.booking.BookingStatus;
import com.stayhub.payment.PaymentMethod;
import com.stayhub.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private Long propertyId;
    private String propertyTitle;
    private String propertyCity;
    private String propertyImageUrl;
    private Long hostId;
    private String hostName;
    private Long guestId;
    private String guestName;
    private String guestEmail;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer guests;
    private long nights;
    private BigDecimal nightlyPrice;
    private BigDecimal subtotal;
    private BigDecimal cleaningFee;
    private BigDecimal serviceFee;
    private String discountCode;
    private BigDecimal discountAmount;
    private BigDecimal totalPrice;
    private String paymentMode;
    private BigDecimal dueOnline;
    private BigDecimal balanceDueHost;
    private BookingStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String transactionId;
    private String checkoutUrl;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant cancelledAt;
}
