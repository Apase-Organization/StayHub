package com.stayhub.payment;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.ledger.PrivateEvidenceService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookings/{bookingId}/private-evidence")
public class PrivateEvidenceController {
    private final PrivateEvidenceService evidence;

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PrivateEvidenceService.ObjectView> upload(@PathVariable long bookingId,
            @RequestParam PrivateEvidenceService.Purpose purpose,@RequestParam @NotNull MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) throws java.io.IOException {
        return ApiResponse.success(evidence.store(bookingId,principal.getId(),purpose,file.getOriginalFilename(),file.getBytes()),
                "Private evidence uploaded.");
    }

    @GetMapping
    public ApiResponse<java.util.List<PrivateEvidenceService.ObjectView>> list(@PathVariable long bookingId,
            @RequestParam PrivateEvidenceService.Purpose purpose,@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(evidence.list(bookingId,principal.getId(),purpose),"Private evidence index retrieved.");
    }

    @GetMapping("/{objectId}")
    public ResponseEntity<byte[]> download(@PathVariable long bookingId,@PathVariable long objectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        var object=evidence.download(bookingId,objectId,principal.getId());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(object.mediaType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"evidence\"")
                .body(object.content());
    }
}
