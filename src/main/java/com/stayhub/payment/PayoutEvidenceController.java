package com.stayhub.payment;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.ledger.ArrivalEligibilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stores evidence for a completed manual transfer; it does not perform one. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/bookings/{bookingId}/payouts/{reservationId}/proof")
public class PayoutEvidenceController {
    private final ArrivalEligibilityService operations;
    public record ProofRequest(@Positive long makerId,@NotBlank String transferRef,@NotBlank String proofDigest,
            @NotNull java.time.Instant executedAt,@NotBlank String idempotencyKey) {}
    public record ProofResponse(long proofId) {}

    @PostMapping
    public ApiResponse<ProofResponse> record(@PathVariable long bookingId,@PathVariable long reservationId,
            @Valid @RequestBody ProofRequest request,@AuthenticationPrincipal UserPrincipal principal) {
        if(principal==null||principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
        long proof=operations.recordManualPayoutProof(new ArrivalEligibilityService.PayoutProofCommand(bookingId,
                reservationId,request.makerId(),principal.getId(),request.transferRef(),request.proofDigest(),
                request.executedAt(),request.idempotencyKey()));
        return ApiResponse.success(new ProofResponse(proof),"Manual payout proof recorded.");
    }
}
