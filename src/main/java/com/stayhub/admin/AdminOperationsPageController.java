package com.stayhub.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Admin operations view over source-aware metrics and manual payout/refund APIs. */
@Controller
@RequiredArgsConstructor
public class AdminOperationsPageController {
    private final InventoryFinancialMetricsService metrics;

    @GetMapping("/admin/financial-operations")
    public String financialOperations(Authentication authentication,Model model) {
        model.addAttribute("metrics",metrics.read());
        return "admin/financial-operations";
    }
}
