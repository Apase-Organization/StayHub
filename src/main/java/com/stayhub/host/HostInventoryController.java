package com.stayhub.host;

import com.stayhub.auth.UserPrincipal;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/properties")
public class HostInventoryController {
    private static final ZoneId HCM=ZoneId.of("Asia/Ho_Chi_Minh");
    private final HostInventoryDashboardService dashboard;

    @GetMapping("/{propertyId}/inventory")
    public String inventory(@PathVariable long propertyId,@AuthenticationPrincipal UserPrincipal host,Model model) {
        LocalDate start=LocalDate.now(HCM);
        LocalDate end=start.plusDays(6);
        model.addAttribute("overview",dashboard.property(host.getId(),propertyId,start,end));
        return "host/property-inventory";
    }
}
