package com.stayhub.payment.quote;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.exception.ConflictException;
import com.stayhub.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookings")
public class InventoryCheckoutController {
    private final InventoryCheckoutService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingCreatedResponse> create(@AuthenticationPrincipal UserPrincipal guest,
                                                       @RequestHeader("Idempotency-Key") String key,
                                                       @Valid @RequestBody BookingConsentRequest consent,
                                                       HttpServletRequest request) {
        if(guest==null||(!guest.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_GUEST"))
                &&!guest.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_HOST"))))
            throw new ConflictException("ERR_FORBIDDEN","Sign in with a Guest or Host account to create a booking.");
        return ApiResponse.success(service.create(guest.getId(),consent,key,request.getRemoteAddr()),"Booking created.");
    }
}
