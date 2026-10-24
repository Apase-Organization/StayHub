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
        if(!guest.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_GUEST")))
            throw new com.stayhub.common.exception.ConflictException("ERR_FORBIDDEN","Guest role is required to request a booking quote.");
        return ApiResponse.success(service.quote(guest.getId(), request), "Booking quote created.");
    }
}
