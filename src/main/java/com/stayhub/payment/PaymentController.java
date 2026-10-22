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

@Controller
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @Value("${APP_LOCAL_URL:http://localhost:8080}")
    private String appLocalUrl;

    @GetMapping("/payments/vnpay/return")
    public String vnpayReturn(@RequestParam Map<String, String> params, Model model) {
        // TSK-080: Return URL is strictly READ-ONLY. Financial mutation is handled exclusively by verified IPN webhook.
        model.addAttribute("txnRef", params.get("vnp_TxnRef"));
        model.addAttribute("responseCode", params.get("vnp_ResponseCode"));
        model.addAttribute("localBookingsUrl", appLocalUrl.replaceAll("/+$", "") + "/bookings");
        return "payment/vnpay-return";
    }

    @ResponseBody
    @GetMapping("/api/v1/payments/vnpay/ipn")
    public VnpayIpnResponse vnpayIpn(@RequestParam Map<String, String> params) {
        return paymentService.handleVnpayIpn(params);
    }

    @ResponseBody
    @GetMapping("/api/v1/payments/bookings/{bookingId}/status")
    public ApiResponse<PaymentStatusResponse> bookingPaymentStatus(@PathVariable Long bookingId,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(paymentService.getPaymentStatus(principal.getId(), bookingId), "Payment status retrieved successfully.");
    }

    @ResponseBody
    @GetMapping("/api/v1/payments/vnpay/status")
    public ApiResponse<PaymentStatusResponse> vnpayPaymentStatus(@RequestParam String txnRef) {
        return ApiResponse.success(paymentService.getPaymentStatusByTxnRef(txnRef), "Payment status retrieved successfully.");
    }
}
