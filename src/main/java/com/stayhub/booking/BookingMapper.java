package com.stayhub.booking;

import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.payment.Payment;
import com.stayhub.property.PropertyImage;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingMapper {
    private final JdbcTemplate jdbc;

    public BookingResponse toResponse(Booking booking) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        Payment payment = booking.getPayment();
        ListingSnapshot listing=snapshot(booking.getId());
        FinancialSnapshot financial=financialSnapshot(booking.getId());
        String coverImageUrl = listing!=null ? listing.coverImage() : booking.getProperty().getImages().stream()
                .filter(PropertyImage::isCover).findFirst()
                .or(() -> booking.getProperty().getImages().stream().findFirst())
                .map(PropertyImage::getImageUrl).orElse(null);
        return BookingResponse.builder()
                .id(booking.getId())
                .propertyId(booking.getProperty().getId())
                .propertyTitle(listing!=null ? listing.title() : booking.getProperty().getTitle())
                .propertyCity(listing!=null ? listing.city() : booking.getProperty().getCity())
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
                .subtotal(financial!=null ? financial.subtotal() : booking.getNightlyPrice().multiply(BigDecimal.valueOf(nights)))
                .cleaningFee(booking.getCleaningFee())
                .serviceFee(booking.getServiceFee())
                .discountAmount(booking.getDiscountAmount())
                .totalPrice(booking.getTotalPrice())
                .paymentMode(financial!=null ? financial.mode() : "FULL_PREPAY")
                .dueOnline(financial!=null ? financial.dueOnline() : booking.getTotalPrice())
                .balanceDueHost(financial!=null ? financial.balanceDueHost() : BigDecimal.ZERO)
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

    private ListingSnapshot snapshot(long bookingId) {
        List<ListingSnapshot> rows=jdbc.query("""
                SELECT listing_snapshot->>'title' AS title,listing_snapshot->>'city' AS city,
                       listing_snapshot->>'coverImage' AS cover_image
                FROM booking_listing_snapshots WHERE booking_id=?
                """,(rs,row)->new ListingSnapshot(rs.getString("title"),rs.getString("city"),rs.getString("cover_image")),bookingId);
        return rows.isEmpty()?null:rows.getFirst();
    }

    private FinancialSnapshot financialSnapshot(long bookingId) {
        List<FinancialSnapshot> rows=jdbc.query("""
                SELECT payment_mode,subtotal,due_online,balance_due_host FROM booking_financial_snapshots WHERE booking_id=?
                """,(rs,row)->new FinancialSnapshot(rs.getString("payment_mode"),rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("due_online"),rs.getBigDecimal("balance_due_host")),bookingId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private record ListingSnapshot(String title,String city,String coverImage) {}
    private record FinancialSnapshot(String mode,BigDecimal subtotal,BigDecimal dueOnline,BigDecimal balanceDueHost) {}
}
