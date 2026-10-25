package com.stayhub.review;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingRepository;
import com.stayhub.booking.BookingStatus;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.review.dto.ReviewCreateRequest;
import com.stayhub.review.dto.ReviewResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final ReviewMapper reviewMapper;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public ReviewResponse createReview(Long guestId, Long bookingId, ReviewCreateRequest request) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .filter(found -> found.getGuest().getId().equals(guestId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessException("ERR_REVIEW_NOT_ALLOWED", "Only completed bookings can be reviewed.");
        }
        boolean inventoryBooking=!jdbc.queryForList("SELECT booking_id FROM booking_financial_snapshots WHERE booking_id=?",
                Long.class,bookingId).isEmpty();
        if(inventoryBooking) {
            Instant completedAt=booking.getCompletedAt();
            Instant now=Instant.now();
            if(completedAt==null || now.isBefore(completedAt) || !now.isBefore(completedAt.plusSeconds(14L*24*3600)))
                throw new BusinessException("ERR_REVIEW_WINDOW_CLOSED","The approved review window is closed or has not opened.");
            int rooms=jdbc.queryForObject("""
                    SELECT count(*) FROM room_allocations ra JOIN booking_items bi ON bi.id=ra.item_id WHERE bi.booking_id=?
                    """,Integer.class,bookingId);
            int eligible=jdbc.queryForObject("""
                    SELECT count(*) FROM booking_arrivals a JOIN booking_items bi ON bi.id=a.item_id
                    WHERE bi.booking_id=? AND a.state IN ('ACKNOWLEDGED','ADMIN_VERIFIED') AND NOT a.needs_review
                    """,Integer.class,bookingId);
            if(rooms==0||rooms!=eligible
                    ||jdbc.queryForObject("SELECT count(*) FROM booking_settlement_blocks WHERE booking_id=? AND active",Integer.class,bookingId)>0
                    ||jdbc.queryForObject("SELECT count(*) FROM direct_receipt_cases WHERE booking_id=? AND state IN ('ATTESTED','CONTESTED','REFUSED')",Integer.class,bookingId)>0
                    ||jdbc.queryForObject("SELECT count(*) FROM refund_cases WHERE booking_id=? AND state IN ('REQUESTED','APPROVED','PROCESSING')",Integer.class,bookingId)>0
                    ||jdbc.queryForObject("SELECT count(*) FROM payment_collections WHERE booking_id=? AND disposition='RECONCILE'",Integer.class,bookingId)>0
                    ||jdbc.queryForObject("SELECT count(*) FROM payout_recovery_cases WHERE booking_id=? AND state='OPEN'",Integer.class,bookingId)>0)
                throw new BusinessException("ERR_REVIEW_NOT_ALLOWED","Verified actual-stay evidence is required before review.");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new BusinessException("ERR_REVIEW_EXISTS", "This booking has already been reviewed.");
        }

        Property property = propertyRepository.findById(booking.getProperty().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        Review review = Review.builder()
                .booking(booking)
                .property(property)
                .guest(booking.getGuest())
                .rating(request.getRating())
                .comment(normalizeComment(request.getComment()))
                .build();

        try {
            Review saved = reviewRepository.saveAndFlush(review);
            updatePropertyRating(property.getId());
            return reviewMapper.toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("ERR_REVIEW_EXISTS", "This booking has already been reviewed.");
        }
    }

    @Override
    public List<ReviewResponse> getPropertyReviews(Long propertyId) {
        return reviewRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId).stream()
                .map(reviewMapper::toResponse)
                .toList();
    }

    @Override
    public Optional<ReviewResponse> getReviewByBooking(Long bookingId) {
        return reviewRepository.findByBookingId(bookingId).map(reviewMapper::toResponse);
    }

    @Override
    public boolean hasReview(Long bookingId) {
        return reviewRepository.existsByBookingId(bookingId);
    }

    private void updatePropertyRating(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        BigDecimal average = BigDecimal.valueOf(reviewRepository.calculateAverageRating(propertyId))
                .setScale(2, RoundingMode.HALF_UP);
        property.setRatingAvg(average);
        propertyRepository.save(property);
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }
}
