package com.stayhub.payment;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.ledger.PaymentPrimitiveException;
import com.stayhub.payment.ledger.RefundWorkflowService;
import com.stayhub.payment.ledger.PrivateEvidenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/admin/refunds")
public class RefundAdminController {
    private final RefundWorkflowService refunds;
    private final PrivateEvidenceService evidence;
    public record RequestRefund(@NotNull RefundWorkflowService.Source source,@NotNull RefundWorkflowService.PolicyBasis policyBasis,
            @NotBlank String evidenceDigest,@NotBlank String idempotencyKey) {}
    public record HostUnable(@NotBlank String evidenceDigest,@NotBlank String idempotencyKey,@PositiveOrZero long expectedVersion) {}
    public record Transition(@NotBlank String idempotencyKey,@NotBlank String evidenceDigest,
            @PositiveOrZero long expectedVersion) {}
    public record Paid(@NotBlank String idempotencyKey,@NotBlank String transferRef,
            @NotBlank String proofDigest,@PositiveOrZero long expectedVersion) {}

    @PostMapping("/bookings/{bookingId}")
    public ApiResponse<RefundWorkflowService.RefundView> request(@PathVariable long bookingId,
            @Valid @RequestBody RequestRefund request,@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        if(request.policyBasis()==RefundWorkflowService.PolicyBasis.FULL_COLLECTED_HOST_UNABLE)
            evidence.requireHostEvidence(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DISPUTE,request.evidenceDigest());
        else evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.REFUND,request.evidenceDigest());
        return ApiResponse.success(refunds.requestPolicyRefund(bookingId,principal.getId(),request.source(),request.policyBasis(),
                request.idempotencyKey(),request.evidenceDigest()),"Policy-scoped refund request created.");
    }
    @PostMapping("/bookings/{bookingId}/host-unable")
    public ApiResponse<Long> hostUnable(@PathVariable long bookingId,@Valid @RequestBody HostUnable request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        evidence.requireHostEvidence(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DISPUTE,request.evidenceDigest());
        return ApiResponse.success(refunds.recordHostUnable(bookingId,principal.getId(),request.evidenceDigest(),
                request.idempotencyKey(),request.expectedVersion()),"Host inability reviewed; booking cancelled and inventory released.");
    }
    @GetMapping("/{refundId}")
    public ApiResponse<RefundWorkflowService.RefundView> get(@PathVariable long refundId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        return ApiResponse.success(refunds.get(refundId),"Refund case retrieved.");
    }
    @PostMapping("/{refundId}/approve")
    public ApiResponse<RefundWorkflowService.RefundView> approve(@PathVariable long refundId,
            @Valid @RequestBody Transition request,@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        evidence.requireEvidenceUploadedByActor(refunds.get(refundId).bookingId(),principal.getId(),PrivateEvidenceService.Purpose.REFUND,request.evidenceDigest());
        return ApiResponse.success(refunds.approve(refundId,principal.getId(),request.idempotencyKey(),
                request.evidenceDigest(),request.expectedVersion()),"Refund independently approved.");
    }
    @PostMapping("/{refundId}/processing")
    public ApiResponse<RefundWorkflowService.RefundView> processing(@PathVariable long refundId,
            @Valid @RequestBody Transition request,@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        evidence.requireEvidenceUploadedByActor(refunds.get(refundId).bookingId(),principal.getId(),PrivateEvidenceService.Purpose.REFUND,request.evidenceDigest());
        return ApiResponse.success(refunds.processing(refundId,principal.getId(),request.idempotencyKey(),
                request.evidenceDigest(),request.expectedVersion()),"Refund marked processing.");
    }
    @PostMapping("/{refundId}/paid")
    public ApiResponse<RefundWorkflowService.RefundView> paid(@PathVariable long refundId,
            @Valid @RequestBody Paid request,@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        evidence.requireEvidenceUploadedByActor(refunds.get(refundId).bookingId(),principal.getId(),PrivateEvidenceService.Purpose.REFUND,request.proofDigest());
        return ApiResponse.success(refunds.paid(refundId,principal.getId(),request.idempotencyKey(),
                request.transferRef(),request.proofDigest(),request.expectedVersion()),"Manual refund proof recorded.");
    }
    @PostMapping("/{refundId}/failed")
    public ApiResponse<RefundWorkflowService.RefundView> failed(@PathVariable long refundId,
            @Valid @RequestBody Transition request,@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        evidence.requireEvidenceUploadedByActor(refunds.get(refundId).bookingId(),principal.getId(),PrivateEvidenceService.Purpose.REFUND,request.evidenceDigest());
        return ApiResponse.success(refunds.fail(refundId,principal.getId(),request.idempotencyKey(),
                request.evidenceDigest(),request.expectedVersion()),"Refund failed and its reserve was released.");
    }
    private static void requireAdmin(UserPrincipal principal) {
        if(principal==null||principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new PaymentPrimitiveException("ERR_FORBIDDEN");
    }
}
