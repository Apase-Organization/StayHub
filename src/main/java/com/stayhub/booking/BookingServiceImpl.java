package com.stayhub.booking;

import com.stayhub.booking.dto.AvailabilityRequest;
import com.stayhub.booking.dto.AvailabilityResponse;
import com.stayhub.booking.dto.BookingCreateRequest;
import com.stayhub.booking.dto.BookingPriceQuote;
import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.InvalidStateTransitionException;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.common.validation.StayDatePolicy;
import com.stayhub.discount.AppliedDiscount;
import com.stayhub.discount.DiscountService;
import com.stayhub.notification.NotificationService;
import com.stayhub.payment.PaymentService;
import com.stayhub.payment.ledger.ArrivalEligibilityService;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    private static final Set<BookingStatus> BLOCKING_STATUSES = Set.of(
            BookingStatus.PENDING,
            BookingStatus.PENDING_PAYMENT,
            BookingStatus.CONFIRMED
    );

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final BookingPriceService bookingPriceService;
    private final BookingMapper bookingMapper;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final DiscountService discountService;
    private final ArrivalEligibilityService arrivalEligibility;

    @Override
    @Transactional
    public BookingResponse createBooking(Long guestId, BookingCreateRequest request) {
        return createBooking(guestId, request, "127.0.0.1");
    }

    @Override
    @Transactional
    public BookingResponse createBooking(Long guestId, BookingCreateRequest request, String ipAddress) {
        validateDates(request.getCheckInDate(), request.getCheckOutDate());
        User guest = userRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found"));
        Property property = propertyRepository.findByIdAndStatusForUpdate(request.getPropertyId(), PropertyStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        validateGuests(property, request.getGuests());
        ensureAvailable(property.getId(), request.getCheckInDate(), request.getCheckOutDate());

        BookingPriceQuote quote = bookingPriceService.calculate(property, request.getCheckInDate(), request.getCheckOutDate());
        AppliedDiscount discount = discountService.apply(guestId, request.getDiscountCode(), quote.getTotalPrice());
        BigDecimal totalPrice = quote.getTotalPrice().subtract(discount.amount());
        Booking booking = Booking.builder()
                .property(property)
                .guest(guest)
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .guests(request.getGuests())
                .nightlyPrice(quote.getNightlyPrice())
                .cleaningFee(quote.getCleaningFee())
                .serviceFee(quote.getServiceFee())
                .subtotalPrice(quote.getTotalPrice())
                .discountCodeId(discount.discountCodeId())
                .discountAmount(discount.amount())
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING_PAYMENT)
                .build();

        try {
            Booking saved = bookingRepository.saveAndFlush(booking);
            saved.setPayment(paymentService.createPendingVnpayPayment(saved));
            BookingResponse response = bookingMapper.toResponse(saved);
            response.setCheckoutUrl(paymentService.buildVnpayCheckoutUrl(saved.getPayment(), ipAddress));
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw roomNotAvailable();
        }
    }

    @Override
    public AvailabilityResponse checkAvailability(AvailabilityRequest request) {
        BookingPriceQuote quote = quote(request.getPropertyId(), request.getCheckInDate(), request.getCheckOutDate(), request.getGuests());
        ensureAvailable(request.getPropertyId(), request.getCheckInDate(), request.getCheckOutDate());
        return AvailabilityResponse.builder()
                .available(true)
                .priceQuote(quote)
                .build();
    }

    @Override
    public BookingPriceQuote quote(Long propertyId, LocalDate checkInDate, LocalDate checkOutDate, Integer guests) {
        validateDates(checkInDate, checkOutDate);
        Property property = propertyRepository.findByIdAndStatus(propertyId, PropertyStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        validateGuests(property, guests);
        return bookingPriceService.calculate(property, checkInDate, checkOutDate);
    }

    @Override
    public BookingResponse getBookingForGuest(Long guestId, Long bookingId) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .filter(found -> found.getGuest().getId().equals(guestId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return bookingMapper.toResponse(booking);
    }

    @Override
    public BookingResponse getBookingForHost(Long hostId, Long bookingId) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .filter(found -> found.getProperty().getHost().getId().equals(hostId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return bookingMapper.toResponse(booking);
    }

    @Override
    public List<BookingResponse> getGuestBookings(Long guestId) {
        return bookingRepository.findByGuestId(guestId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Override
    public List<BookingResponse> getBookingRequestsByHost(Long hostId) {
        return bookingRepository.findBookingRequestsByHost(hostId, BookingStatus.PENDING).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse acceptBooking(Long hostId, Long bookingId) {
        Booking booking = findHostBookingForUpdate(hostId, bookingId);
        requireStatus(booking, BookingStatus.PENDING, "Only pending bookings can be accepted.");
        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);
        notificationService.bookingStatusChanged(saved);
        return bookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse rejectBooking(Long hostId, Long bookingId) {
        Booking booking = findHostBookingForUpdate(hostId, bookingId);
        requireStatus(booking, BookingStatus.PENDING, "Only pending bookings can be rejected.");
        booking.setStatus(BookingStatus.REJECTED);
        Booking saved = bookingRepository.save(booking);
        notificationService.bookingStatusChanged(saved);
        return bookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse completeBooking(Long hostId, Long bookingId) {
        Booking booking = findHostBookingForUpdate(hostId, bookingId);
        requireStatus(booking, BookingStatus.CONFIRMED, "Only confirmed bookings can be completed.");
        Instant completedAt=arrivalEligibility.authorizeCompletion(bookingId,hostId);
        booking.setStatus(BookingStatus.COMPLETED);
        if(completedAt!=null) {
            booking.setCompletedAt(completedAt);
            booking.setCompletedBy(hostId);
        }
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long guestId, Long bookingId) {
        Booking booking = bookingRepository.findDetailedByIdForUpdate(bookingId)
                .filter(found -> found.getGuest().getId().equals(guestId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.PENDING_PAYMENT
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidStateTransitionException("Only pending, pending payment, or confirmed bookings can be cancelled.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        Booking saved = bookingRepository.save(booking);
        notificationService.bookingStatusChanged(saved);
        return bookingMapper.toResponse(saved);
    }

    private void ensureAvailable(Long propertyId, LocalDate checkInDate, LocalDate checkOutDate) {
        if (!bookingRepository.findConflictingBookings(propertyId, checkInDate, checkOutDate, BLOCKING_STATUSES).isEmpty()) {
            throw roomNotAvailable();
        }
    }

    private void validateDates(LocalDate checkInDate, LocalDate checkOutDate) {
        StayDatePolicy.validate(checkInDate, checkOutDate, ZonedDateTime.now(StayDatePolicy.ZONE));
    }

    private void validateGuests(Property property, Integer guests) {
        if (guests == null || guests < 1) {
            throw new BusinessException("ERR_INVALID_GUESTS", "Guest count must be at least 1.");
        }
        if (guests > property.getMaxGuests()) {
            throw new BusinessException("ERR_TOO_MANY_GUESTS", "Guest count exceeds the property capacity.");
        }
    }

    private BusinessException roomNotAvailable() {
        return new BusinessException("ERR_ROOM_NOT_AVAILABLE", "The selected dates are no longer available.");
    }

    private Booking findHostBookingForUpdate(Long hostId, Long bookingId) {
        return bookingRepository.findDetailedByIdForUpdate(bookingId)
                .filter(found -> found.getProperty().getHost().getId().equals(hostId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private void requireStatus(Booking booking, BookingStatus expectedStatus, String message) {
        if (booking.getStatus() != expectedStatus) {
            throw new InvalidStateTransitionException(message);
        }
    }
}
