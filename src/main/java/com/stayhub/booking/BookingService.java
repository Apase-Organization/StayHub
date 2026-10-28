package com.stayhub.booking;

import com.stayhub.booking.dto.AvailabilityRequest;
import com.stayhub.booking.dto.AvailabilityResponse;
import com.stayhub.booking.dto.BookingCreateRequest;
import com.stayhub.booking.dto.BookingPriceQuote;
import com.stayhub.booking.dto.BookingResponse;
import java.time.LocalDate;
import java.util.List;

public interface BookingService {
    BookingResponse createBooking(Long guestId, BookingCreateRequest request);

    BookingResponse createBooking(Long guestId, BookingCreateRequest request, String ipAddress);

    AvailabilityResponse checkAvailability(AvailabilityRequest request);

    BookingPriceQuote quote(Long propertyId, LocalDate checkInDate, LocalDate checkOutDate, Integer guests);

    BookingResponse getBookingForGuest(Long guestId, Long bookingId);

    BookingResponse getBookingForHost(Long hostId, Long bookingId);

    List<BookingResponse> getGuestBookings(Long guestId);

    List<BookingResponse> getBookingRequestsByHost(Long hostId);

    List<BookingResponse> getRecentConfirmedBookingsByHost(Long hostId);

    BookingResponse acceptBooking(Long hostId, Long bookingId);

    BookingResponse rejectBooking(Long hostId, Long bookingId);

    BookingResponse completeBooking(Long hostId, Long bookingId);

    BookingResponse cancelBooking(Long guestId, Long bookingId);
}
