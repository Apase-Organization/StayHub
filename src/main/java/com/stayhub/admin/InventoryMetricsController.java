package com.stayhub.admin;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import com.stayhub.payment.ledger.PaymentPrimitiveException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/inventory/metrics")
public class InventoryMetricsController {
    private final InventoryFinancialMetricsService metrics;

    @GetMapping
    public ApiResponse<InventoryFinancialMetricsService.Metrics> get(@AuthenticationPrincipal UserPrincipal principal) {
        if(principal==null||principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new PaymentPrimitiveException("ERR_FORBIDDEN");
        return ApiResponse.success(metrics.read(),"Inventory and source-aware finance metrics retrieved.");
    }
}
