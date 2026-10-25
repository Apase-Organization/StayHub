package com.stayhub.payment.quote;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/booking-quotes")
public class BookingQuoteController {
    private final BookingQuoteService service;

    @PostMapping
    public ApiResponse<BookingQuoteResponse> quote(@AuthenticationPrincipal UserPrincipal guest,
                                                   @Valid @RequestBody BookingQuoteRequest request) {
        if(!canBookAsGuest(guest))
            throw new com.stayhub.common.exception.ConflictException("ERR_FORBIDDEN","Sign in with a Guest or Host account to request a booking quote.");
        return ApiResponse.success(service.quote(guest.getId(), request), "Booking quote created.");
    }

    private boolean canBookAsGuest(UserPrincipal principal) {
        return principal!=null&&(principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_GUEST"))
                ||principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_HOST")));
    }
}
