package com.stayhub.booking;

import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.payment.Payment;
import com.stayhub.property.PropertyImage;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        Payment payment = booking.getPayment();
        String coverImageUrl = booking.getProperty().getImages().stream()
                .filter(PropertyImage::isCover)
                .findFirst()
                .or(() -> booking.getProperty().getImages().stream().findFirst())
                .map(PropertyImage::getImageUrl)
                .orElse(null);
        return BookingResponse.builder()
                .id(booking.getId())
                .propertyId(booking.getProperty().getId())
                .propertyTitle(booking.getProperty().getTitle())
                .propertyCity(booking.getProperty().getCity())
                .propertyImageUrl(coverImageUrl)
                .hostId(booking.getProperty().getHost().getId())
                .hostName(booking.getProperty().getHost().getFullName())
                .guestId(booking.getGuest().getId())
                .guestName(booking.getGuest().getFullName())
                .guestEmail(booking.getGuest().getEmail())
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .guests(booking.getGuests())
                .nights(nights)
                .nightlyPrice(booking.getNightlyPrice())
                .subtotal(booking.getNightlyPrice().multiply(BigDecimal.valueOf(nights)))
                .cleaningFee(booking.getCleaningFee())
                .serviceFee(booking.getServiceFee())
                .discountAmount(booking.getDiscountAmount())
                .totalPrice(booking.getTotalPrice())
                .paymentMode("FULL_PREPAY")
                .dueOnline(booking.getTotalPrice())
                .balanceDueHost(BigDecimal.ZERO)
                .status(booking.getStatus())
                .paymentMethod(payment != null ? payment.getPaymentMethod() : null)
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .transactionId(payment != null ? payment.getTransactionId() : null)
                .paidAt(payment != null ? payment.getPaidAt() : null)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .cancelledAt(booking.getCancelledAt())
                .build();
    }
}
