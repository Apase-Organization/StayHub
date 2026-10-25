package com.stayhub.payment;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.ledger.DirectReceiptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
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
@RequestMapping("/api/v1/admin/bookings/{bookingId}/direct-receipts")
public class DirectReceiptAdminController {
    private final DirectReceiptService receipts;
    public record ResolveRequest(@NotNull DirectReceiptService.AdminDecision decision,@NotBlank String resolutionDigest,
            @NotBlank String idempotencyKey,@PositiveOrZero long expectedVersion) {}

    @GetMapping
    public ApiResponse<List<DirectReceiptService.AdminCaseView>> list(@PathVariable long bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        admin(principal);return ApiResponse.success(receipts.listForAdmin(bookingId,principal.getId()),"Direct receipts loaded for case review.");
    }
    @PostMapping("/{caseId}/resolve")
    public ApiResponse<DirectReceiptService.CaseView> resolve(@PathVariable long bookingId,@PathVariable long caseId,
            @Valid @RequestBody ResolveRequest request,@AuthenticationPrincipal UserPrincipal principal) {
        admin(principal);return ApiResponse.success(receipts.resolve(bookingId,principal.getId(),caseId,request.decision(),
                request.resolutionDigest(),request.idempotencyKey(),request.expectedVersion()),"Direct receipt disposition recorded.");
    }
    @PostMapping("/{caseId}/reverse")
    public ApiResponse<DirectReceiptService.CaseView> reverse(@PathVariable long bookingId,@PathVariable long caseId,
            @Valid @RequestBody ResolveRequest request,@AuthenticationPrincipal UserPrincipal principal) {
        admin(principal);return ApiResponse.success(receipts.reverse(bookingId,principal.getId(),caseId,
                request.resolutionDigest(),request.idempotencyKey(),request.expectedVersion()),"Direct receipt correction recorded.");
    }
    private static void admin(UserPrincipal principal) {
        if(principal==null||principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
    }
}
