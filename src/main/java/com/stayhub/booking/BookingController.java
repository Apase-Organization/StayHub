package com.stayhub.booking;

import com.stayhub.auth.CustomOAuth2UserService;
import com.stayhub.auth.UserPrincipal;
import com.stayhub.booking.dto.BookingCreateRequest;
import com.stayhub.booking.dto.BookingResponse;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.ResourceNotFoundException;
import com.stayhub.payment.ledger.ArrivalEligibilityService;
import com.stayhub.payment.ledger.DirectReceiptService;
import com.stayhub.property.PropertyService;
import com.stayhub.review.ReviewService;
import com.stayhub.review.dto.ReviewCreateRequest;
import com.stayhub.user.User;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final PropertyService propertyService;
    private final ReviewService reviewService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final DirectReceiptService directReceipts;
    private final ArrivalEligibilityService arrivals;

    @GetMapping("/properties/{id}/book")
    public String showBookingPage(@PathVariable Long id,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDate,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOutDate,
                                  @RequestParam(required = false) Integer guests,
                                  Authentication authentication,
                                  Model model) {
        UserPrincipal principal = currentPrincipal(authentication);
        BookingCreateRequest request = new BookingCreateRequest();
        request.setPropertyId(id);
        request.setCheckInDate(checkInDate != null ? checkInDate : LocalDate.now().plusDays(1));
        request.setCheckOutDate(checkOutDate != null ? checkOutDate : LocalDate.now().plusDays(2));
        request.setGuests(guests != null ? guests : 1);
        addBookingPageModel(model, request, principal, null);
        return "booking/booking";
    }

    @PostMapping("/bookings")
    public String createBooking(@Valid @ModelAttribute("bookingRequest") BookingCreateRequest request,
                                BindingResult bindingResult,
                                Authentication authentication,
                                HttpServletRequest httpRequest,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        UserPrincipal principal = currentPrincipal(authentication);
        if (bindingResult.hasErrors()) {
            addBookingPageModel(model, request, principal, "Please review your booking details.");
            return "booking/booking";
        }
        try {
            var booking = bookingService.createBooking(principal.getId(), request, clientIp(httpRequest));
            return "redirect:" + booking.getCheckoutUrl();
        } catch (BusinessException exception) {
            addBookingPageModel(model, request, principal, exception.getMessage());
            return "booking/booking";
        }
    }

    @GetMapping("/bookings/{id}/payment")
    public String showPaymentSuccess(@PathVariable Long id,
                                     Authentication authentication,
                                     Model model) {
        UserPrincipal principal = currentPrincipal(authentication);
        model.addAttribute("booking", bookingService.getBookingForGuest(principal.getId(), id));
        if (!model.containsAttribute("paymentMessage")) {
            model.addAttribute("paymentMessage", "Mock payment already completed for this booking.");
        }
        return "booking/payment";
    }

    @GetMapping("/bookings")
    public String showMyBookings(Authentication authentication,
                                 @RequestParam(defaultValue = "pending") String tab,
                                 Model model) {
        UserPrincipal principal = currentPrincipal(authentication);
        List<BookingResponse> bookings = bookingService.getGuestBookings(principal.getId());
        model.addAttribute("tab", tab);
        model.addAttribute("allBookings", bookings);
        model.addAttribute("bookings", filterBookings(bookings, tab));
        model.addAttribute("upcomingCount", filterBookings(bookings, "upcoming").size());
        model.addAttribute("pendingCount", filterBookings(bookings, "pending").size());
        model.addAttribute("completedCount", filterBookings(bookings, "completed").size());
        model.addAttribute("cancelledCount", filterBookings(bookings, "cancelled").size());
        return "booking/my-bookings";
    }

    @GetMapping("/bookings/{id}")
    public String showBookingDetail(@PathVariable Long id,
                                    Authentication authentication,
                                    Model model) {
        UserPrincipal principal = currentPrincipal(authentication);
        BookingResponse booking = bookingService.getBookingForGuest(principal.getId(), id);
        addBookingDetailModel(model, booking, "guest",principal.getId());
        return "booking/booking-detail";
    }

    @PostMapping("/bookings/{id}/cancel")
    public String cancelBooking(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        UserPrincipal principal = currentPrincipal(authentication);
        bookingService.cancelBooking(principal.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Booking cancelled successfully.");
        return "redirect:/bookings/" + id;
    }

    @GetMapping("/host/bookings/{id}")
    public String showHostBookingDetail(@PathVariable Long id,
                                        Authentication authentication,
                                        Model model) {
        UserPrincipal principal = currentPrincipal(authentication);
        BookingResponse booking = bookingService.getBookingForHost(principal.getId(), id);
        addBookingDetailModel(model, booking, "host",principal.getId());
        return "booking/booking-detail";
    }

    private void addBookingDetailModel(Model model, BookingResponse booking, String viewer,long actorId) {
        model.addAttribute("booking", booking);
        model.addAttribute("viewer", viewer);
        model.addAttribute("existingReview", reviewService.getReviewByBooking(booking.getId()).orElse(null));
        var rooms=arrivals.roomsForActor(booking.getId(),actorId);
        model.addAttribute("arrivalRooms",rooms);
        if(!rooms.isEmpty()) {
            model.addAttribute("arrivalEligibility",arrivals.eligibilityForActor(booking.getId(),actorId));
        }
        if(booking.getPaymentMode()!=null&&booking.getBalanceDueHost()!=null&&booking.getBalanceDueHost().signum()>0) {
            model.addAttribute("directBalance",directReceipts.balanceForActor(booking.getId(),actorId));
            model.addAttribute("directReceipts",viewer.equals("host")
                    ? directReceipts.listForHost(booking.getId(),actorId)
                    : directReceipts.listForGuest(booking.getId(),actorId));
        }
        if (!model.containsAttribute("reviewRequest")) {
            model.addAttribute("reviewRequest", new ReviewCreateRequest());
        }
    }

    private UserPrincipal currentPrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("Guest not found");
        }
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token
                && oauth2Token.getPrincipal() instanceof OAuth2User oauth2User) {
            User user = customOAuth2UserService.upsertGoogleUser(oauth2User.getAttributes());
            UserPrincipal principal = UserPrincipal.create(user, oauth2User.getAttributes(), "sub");
            OAuth2AuthenticationToken localAuthentication = new OAuth2AuthenticationToken(
                    principal,
                    principal.getAuthorities(),
                    oauth2Token.getAuthorizedClientRegistrationId()
            );
            localAuthentication.setDetails(oauth2Token.getDetails());
            SecurityContextHolder.getContext().setAuthentication(localAuthentication);
            return principal;
        }
        throw new ResourceNotFoundException("Guest not found");
    }

    private void addBookingPageModel(Model model, BookingCreateRequest request, UserPrincipal principal, String errorMessage) {
        model.addAttribute("bookingRequest", request);
        model.addAttribute("property", propertyService.getPublicProperty(request.getPropertyId()));
        model.addAttribute("currentUser", principal);
        model.addAttribute("errorMessage", errorMessage);
        try {
            model.addAttribute("priceQuote", bookingService.quote(
                    request.getPropertyId(), request.getCheckInDate(), request.getCheckOutDate(), request.getGuests()));
        } catch (BusinessException exception) {
            model.addAttribute("quoteError", exception.getMessage());
        }
    }

    private List<BookingResponse> filterBookings(List<BookingResponse> bookings, String tab) {
        return switch (tab) {
            case "pending" -> bookings.stream()
                    .filter(booking -> booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.PENDING_PAYMENT)
                    .toList();
            case "completed" -> bookings.stream()
                    .filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                    .toList();
            case "cancelled" -> bookings.stream()
                    .filter(booking -> booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.REJECTED)
                    .toList();
            default -> bookings.stream()
                    .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                    .toList();
        };
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
