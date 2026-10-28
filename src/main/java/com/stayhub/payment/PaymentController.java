package com.stayhub.payment;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.dto.PaymentStatusResponse;
import com.stayhub.payment.dto.VnpayIpnResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Controller
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final InventoryPaymentLifecycleService inventoryPaymentLifecycle;
    private final InventoryPaymentStatusService inventoryPaymentStatus;

    @Value("${APP_LOCAL_URL:http://localhost:8080}")
    private String appLocalUrl;

    @Value("${app.payment.demo-return-verification-enabled:false}")
    private boolean demoReturnVerificationEnabled;

    public record SignedReturnRequest(Map<String,String> params) {}

    /** Demo only. SecurityConfig requires both an authenticated session and CSRF for this POST. */
    @ResponseBody
    @PostMapping("/api/v1/payments/vnpay/verify")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> verifyReturn(@RequestBody SignedReturnRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if(!demoReturnVerificationEnabled) return ResponseEntity.notFound().build();
        if(principal==null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Map<String,String> params=request.params();
        if(params==null || params.get("vnp_TxnRef")==null || params.values().stream().anyMatch(java.util.Objects::isNull))
            return ResponseEntity.badRequest().build();
        String reference=params.get("vnp_TxnRef");
        // This lookup throws for non-owners before the lifecycle can record or mutate anything.
        PaymentStatusResponse owned=inventoryPaymentStatus.findByAttemptReference(principal.getId(),reference);
        if(owned==null) {
            paymentService.getPaymentStatusByTxnRef(principal.getId(),reference);
            // Legacy IPN has no inventory hold checks: never broaden its trust through this demo endpoint.
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        VnpayIpnResponse result=inventoryPaymentLifecycle.handleInventoryIpn(params);
        if(result==null || !("00".equals(result.RspCode()) || "02".equals(result.RspCode())))
            return ResponseEntity.badRequest().build();
        // Provider acknowledgement is NOT payment success (it may mean reconciliation).
        return ResponseEntity.ok(ApiResponse.success(inventoryPaymentStatus.findByAttemptReference(principal.getId(),reference),
                "Signed return processed; consult verified payment status."));
    }

    @GetMapping("/payments/vnpay/return")
    public String vnpayReturn(@RequestParam Map<String, String> params, Model model) {
        // GET remains read-only, including when demo POST verification is enabled.
        model.addAttribute("demoReturnVerificationEnabled",demoReturnVerificationEnabled);
        model.addAttribute("signedReturnParams",params);
        model.addAttribute("txnRef", params.get("vnp_TxnRef"));
        model.addAttribute("responseCode", params.get("vnp_ResponseCode"));
        model.addAttribute("localBookingsUrl", appLocalUrl.replaceAll("/+$", "") + "/bookings?tab=pending");
        return "payment/vnpay-return";
    }

    @ResponseBody
    @GetMapping("/api/v1/payments/vnpay/ipn")
    public VnpayIpnResponse vnpayIpn(@RequestParam Map<String, String> params) {
        VnpayIpnResponse inventoryResponse=inventoryPaymentLifecycle.handleInventoryIpn(params);
        if(inventoryResponse!=null) return inventoryResponse;
        return paymentService.handleVnpayIpn(params);
    }

    @ResponseBody
    @GetMapping("/api/v1/payments/bookings/{bookingId}/status")
    public ApiResponse<PaymentStatusResponse> bookingPaymentStatus(@PathVariable Long bookingId,
                                                                     @AuthenticationPrincipal UserPrincipal principal) {
        PaymentStatusResponse inventoryStatus=inventoryPaymentStatus.findForGuest(principal.getId(),bookingId);
        if(inventoryStatus!=null) return ApiResponse.success(inventoryStatus,"Payment status retrieved successfully.");
        return ApiResponse.success(paymentService.getPaymentStatus(principal.getId(), bookingId), "Payment status retrieved successfully.");
    }

    @ResponseBody
    @GetMapping("/api/v1/payments/vnpay/status")
    public ApiResponse<PaymentStatusResponse> vnpayPaymentStatus(@RequestParam String txnRef,
                                                                 @AuthenticationPrincipal UserPrincipal principal) {
        PaymentStatusResponse inventoryStatus=inventoryPaymentStatus.findByAttemptReference(principal.getId(),txnRef);
        if(inventoryStatus!=null) return ApiResponse.success(inventoryStatus,"Payment status retrieved successfully.");
        return ApiResponse.success(paymentService.getPaymentStatusByTxnRef(principal.getId(),txnRef), "Payment status retrieved successfully.");
    }
}
