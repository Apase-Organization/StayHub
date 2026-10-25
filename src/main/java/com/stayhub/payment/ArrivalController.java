package com.stayhub.payment;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.ledger.ArrivalEligibilityService;
import com.stayhub.payment.ledger.PrivateEvidenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
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
@RequestMapping("/api/v1/bookings/{bookingId}/arrivals")
public class ArrivalController {
    private final ArrivalEligibilityService arrivals;
    private final PrivateEvidenceService evidence;
    public record ReportRequest(@Positive long itemId, @Positive int roomOrdinal, @NotNull Instant actualAt,
            @NotBlank String evidenceDigest, @NotBlank String idempotencyKey,
            @PositiveOrZero long expectedVersion) {}
    public record DecisionRequest(@NotNull ArrivalEligibilityService.GuestDecision decision,
            String evidenceDigest, @NotBlank String idempotencyKey,
            @PositiveOrZero long expectedVersion) {}
    public record VerifyRequest(@Positive long itemId, @Positive int roomOrdinal,
            @NotBlank String evidenceDigest, @NotBlank String idempotencyKey,
            @PositiveOrZero long expectedVersion) {}
    public record ResolveBlockRequest(@NotBlank String evidenceDigest, @NotBlank String idempotencyKey,
            @PositiveOrZero long expectedVersion) {}
    public record ResolveRecoveryRequest(@NotNull ArrivalEligibilityService.RecoveryOutcome outcome,
            Long checkerId,String transferRef,@NotBlank String evidenceDigest,@NotBlank String idempotencyKey,
            @PositiveOrZero long expectedVersion) {}
    public record NoShowRequest(@NotBlank String evidenceDigest,@NotBlank String idempotencyKey,
            @PositiveOrZero long expectedVersion) {}

    @PostMapping
    public ApiResponse<ArrivalEligibilityService.ArrivalView> report(@PathVariable long bookingId,
            @Valid @RequestBody ReportRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.ARRIVAL,request.evidenceDigest());
        return ApiResponse.success(arrivals.report(new ArrivalEligibilityService.ArrivalCommand(bookingId,
                request.itemId(),request.roomOrdinal(),principal.getId(),request.actualAt(),request.evidenceDigest(),
                request.idempotencyKey(),request.expectedVersion())),"Arrival evidence recorded.");
    }

    @PostMapping("/no-show")
    public ApiResponse<Long> noShow(@PathVariable long bookingId,@Valid @RequestBody NoShowRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if(principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
        evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DISPUTE,request.evidenceDigest());
        return ApiResponse.success(arrivals.recordNoShow(bookingId,principal.getId(),request.evidenceDigest(),
                request.idempotencyKey(),request.expectedVersion()),"No-show case recorded and settlement blocked.");
    }

    @PostMapping("/{itemId}/{roomOrdinal}/guest-decision")
    public ApiResponse<ArrivalEligibilityService.ArrivalView> guestDecision(@PathVariable long bookingId,
            @PathVariable long itemId,@PathVariable int roomOrdinal,@Valid @RequestBody DecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if(request.decision()==ArrivalEligibilityService.GuestDecision.CONTEST)
            evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DISPUTE,request.evidenceDigest());
        return ApiResponse.success(arrivals.guestDecision(bookingId,itemId,roomOrdinal,principal.getId(),
                request.decision(),request.evidenceDigest(),request.idempotencyKey(),request.expectedVersion()),
                "Guest arrival disposition recorded.");
    }

    @PostMapping("/admin-verify")
    public ApiResponse<ArrivalEligibilityService.ArrivalView> adminVerify(@PathVariable long bookingId,
            @Valid @RequestBody VerifyRequest request,@AuthenticationPrincipal UserPrincipal principal) {
        if(principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
        evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.ARRIVAL,request.evidenceDigest());
        return ApiResponse.success(arrivals.adminVerify(bookingId,request.itemId(),request.roomOrdinal(),
                principal.getId(),request.evidenceDigest(),request.idempotencyKey(),request.expectedVersion()),
                "Audited arrival verification recorded.");
    }

    @GetMapping("/eligibility")
    public ApiResponse<ArrivalEligibilityService.Eligibility> eligibility(@PathVariable long bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Service exposes only aggregate blockers and counts, never private evidence handles.
        return ApiResponse.success(arrivals.eligibilityForActor(bookingId,principal.getId()),
                "Payout eligibility retrieved.");
    }

    @GetMapping("/rooms")
    public ApiResponse<java.util.List<ArrivalEligibilityService.RoomEvidence>> rooms(@PathVariable long bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(arrivals.roomsForActor(bookingId,principal.getId()),"Per-room arrival evidence retrieved.");
    }

    @PostMapping("/blocks/{blockId}/resolve")
    public ApiResponse<Void> resolveBlock(@PathVariable long bookingId,@PathVariable long blockId,
            @Valid @RequestBody ResolveBlockRequest request,@AuthenticationPrincipal UserPrincipal principal) {
        if(principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
        evidence.requireEvidenceUploadedByActor(bookingId,principal.getId(),PrivateEvidenceService.Purpose.DISPUTE,request.evidenceDigest());
        arrivals.resolveBlock(bookingId,blockId,principal.getId(),request.evidenceDigest(),request.idempotencyKey(),request.expectedVersion());
        return ApiResponse.success(null,"Settlement block resolved.");
    }

    @GetMapping("/recovery-cases")
    public ApiResponse<java.util.List<ArrivalEligibilityService.RecoveryView>> recoveries(@PathVariable long bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(arrivals.recoveriesForActor(bookingId,principal.getId()),"Recovery cases retrieved.");
    }

    @PostMapping("/recovery-cases/{caseId}/resolve")
    public ApiResponse<ArrivalEligibilityService.RecoveryView> resolveRecovery(@PathVariable long bookingId,
            @PathVariable long caseId,@Valid @RequestBody ResolveRecoveryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if(principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new com.stayhub.payment.ledger.PaymentPrimitiveException("ERR_FORBIDDEN");
        long proofUploader=request.outcome()==ArrivalEligibilityService.RecoveryOutcome.MANUAL_RECOVERY_DOCUMENTED
                &&request.checkerId()!=null?request.checkerId():principal.getId();
        evidence.requireEvidenceUploadedByActor(bookingId,proofUploader,PrivateEvidenceService.Purpose.PAYOUT,request.evidenceDigest());
        return ApiResponse.success(arrivals.resolveRecovery(bookingId,caseId,principal.getId(),request.checkerId(),
                request.transferRef(),request.outcome(),request.evidenceDigest(),request.idempotencyKey(),request.expectedVersion()),
                "Recovery case disposition recorded.");
    }
}
