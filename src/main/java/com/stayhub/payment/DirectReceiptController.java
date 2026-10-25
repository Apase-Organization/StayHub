package com.stayhub.payment;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.ledger.DirectReceiptService;
import com.stayhub.payment.ledger.PrivateEvidenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direct Host money reports are private evidence, not provider payment status. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookings/{bookingId}/direct-receipts")
public class DirectReceiptController {
    private final DirectReceiptService receipts;
    private final PrivateEvidenceService evidence;

    public record AttestRequest(@NotBlank String receiptRef, @NotNull @Positive BigDecimal amount,
            @NotBlank String currency, @NotNull DirectReceiptService.Method method, @NotNull Instant paidAt,
            @NotBlank String evidenceDigest, @NotBlank String idempotencyKey,
            @PositiveOrZero long expectedVersion) {}
    public record GuestDispositionRequest(@NotNull DirectReceiptService.Disposition disposition,
            @NotBlank String idempotencyKey, String evidenceDigest,
            @PositiveOrZero long expectedVersion) {}
    public record ResolveRequest(@NotNull DirectReceiptService.AdminDecision decision, @NotBlank String resolutionDigest, @NotBlank String idempotencyKey,
            @PositiveOrZero long expectedVersion) {}

    @PostMapping
    public ApiResponse<DirectReceiptService.CaseView> attest(@PathVariable long bookingId,
            @Valid @RequestBody AttestRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DIRECT_RECEIPT,request.evidenceDigest());
        return ApiResponse.success(receipts.attest(new DirectReceiptService.Attestation(bookingId, principal.getId(),
                request.receiptRef(), request.amount(), request.currency(), request.method(), request.paidAt(),
                request.evidenceDigest(), request.idempotencyKey(), request.expectedVersion())),
                "Direct receipt attested for reconciliation.");
    }

    @GetMapping
    public ApiResponse<List<DirectReceiptService.CaseView>> list(@PathVariable long bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean host = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HOST"));
        var result = host ? receipts.listForHost(bookingId, principal.getId())
                : receipts.listForGuest(bookingId, principal.getId());
        return ApiResponse.success(result, "Direct receipt evidence retrieved.");
    }

    @GetMapping("/summary")
    public ApiResponse<DirectReceiptService.BalanceView> balance(@PathVariable long bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(receipts.balanceForActor(bookingId,principal.getId()),
                "Direct balance evidence summary retrieved.");
    }

    @PostMapping("/{caseId}/guest-disposition")
    public ApiResponse<DirectReceiptService.CaseView> guestDisposition(@PathVariable long bookingId,
            @PathVariable long caseId, @Valid @RequestBody GuestDispositionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if(request.disposition()!=DirectReceiptService.Disposition.ACKNOWLEDGE)
            evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DIRECT_RECEIPT,request.evidenceDigest());
        return ApiResponse.success(receipts.guestDisposition(bookingId, principal.getId(), caseId,
                request.disposition(), request.idempotencyKey(), request.evidenceDigest(), request.expectedVersion()),
                "Guest disposition recorded.");
    }

    @PostMapping("/{caseId}/resolve")
    public ApiResponse<DirectReceiptService.CaseView> resolve(@PathVariable long bookingId,
            @PathVariable long caseId, @Valid @RequestBody ResolveRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
        }
        evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DIRECT_RECEIPT,request.resolutionDigest());
        return ApiResponse.success(receipts.resolve(bookingId, principal.getId(), caseId, request.decision(),
                request.resolutionDigest(), request.idempotencyKey(), request.expectedVersion()),
                "Direct receipt case resolved.");
    }

    @PostMapping("/{caseId}/reverse")
    public ApiResponse<DirectReceiptService.CaseView> reverse(@PathVariable long bookingId,
            @PathVariable long caseId, @Valid @RequestBody ResolveRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
        }
        evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DIRECT_RECEIPT,request.resolutionDigest());
        return ApiResponse.success(receipts.reverse(bookingId, principal.getId(), caseId,
                request.resolutionDigest(), request.idempotencyKey(), request.expectedVersion()),
                "Direct receipt reversal recorded.");
    }
}
