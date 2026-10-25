package com.stayhub.payment;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.ledger.PayoutWorkflowService;
import com.stayhub.payment.ledger.PrivateEvidenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payouts")
public class PayoutAdminController {
    private final PayoutWorkflowService payouts;
    private final PrivateEvidenceService evidence;
    public record Request(@NotBlank String idempotencyKey) {}
    public record Transition(@NotBlank String idempotencyKey,@NotBlank String evidenceDigest,
            @PositiveOrZero long expectedVersion) {}
    public record Paid(@NotBlank String idempotencyKey,@NotBlank String transferRef,@NotBlank String proofDigest,
            @PositiveOrZero long expectedVersion) {}

    @PostMapping("/bookings/{bookingId}")
    public ApiResponse<PayoutWorkflowService.View> request(@PathVariable long bookingId,@Valid @RequestBody Request req,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        return ApiResponse.success(payouts.request(bookingId,principal.getId(),req.idempotencyKey()),"Payout request recorded.");
    }
    @GetMapping("/{id}")
    public ApiResponse<PayoutWorkflowService.View> get(@PathVariable long id,@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);return ApiResponse.success(payouts.get(id),"Payout case retrieved.");
    }
    @PostMapping("/{id}/approve")
    public ApiResponse<PayoutWorkflowService.View> approve(@PathVariable long id,@Valid @RequestBody Transition req,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);evidence.requireEvidenceUploadedByActor(payouts.get(id).bookingId(),principal.getId(),PrivateEvidenceService.Purpose.PAYOUT,req.evidenceDigest());
        return ApiResponse.success(payouts.approve(id,principal.getId(),req.idempotencyKey(),req.evidenceDigest(),req.expectedVersion()),"Payout approved.");
    }
    @PostMapping("/{id}/processing")
    public ApiResponse<PayoutWorkflowService.View> processing(@PathVariable long id,@Valid @RequestBody Transition req,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);evidence.requireEvidenceUploadedByActor(payouts.get(id).bookingId(),principal.getId(),PrivateEvidenceService.Purpose.PAYOUT,req.evidenceDigest());
        return ApiResponse.success(payouts.processing(id,principal.getId(),req.idempotencyKey(),req.evidenceDigest(),req.expectedVersion()),"Payout marked processing.");
    }
    @PostMapping("/{id}/paid")
    public ApiResponse<PayoutWorkflowService.View> paid(@PathVariable long id,@Valid @RequestBody Paid req,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);evidence.requireEvidenceUploadedByActor(payouts.get(id).bookingId(),principal.getId(),PrivateEvidenceService.Purpose.PAYOUT,req.proofDigest());
        return ApiResponse.success(payouts.paid(id,principal.getId(),req.idempotencyKey(),req.transferRef(),req.proofDigest(),req.expectedVersion()),"Payout proof recorded.");
    }
    @PostMapping("/{id}/failed")
    public ApiResponse<PayoutWorkflowService.View> failed(@PathVariable long id,@Valid @RequestBody Transition req,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);evidence.requireEvidenceUploadedByActor(payouts.get(id).bookingId(),principal.getId(),PrivateEvidenceService.Purpose.PAYOUT,req.evidenceDigest());
        return ApiResponse.success(payouts.fail(id,principal.getId(),req.idempotencyKey(),req.evidenceDigest(),req.expectedVersion()),"Payout failed; reserve released.");
    }
    private static void requireAdmin(UserPrincipal principal) {
        if(principal==null||principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
    }
}
