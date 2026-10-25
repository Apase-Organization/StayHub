package com.stayhub.admin;

import com.stayhub.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    @GetMapping
    public String adminRoot() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String viewDashboard(@RequestParam(defaultValue = "overview") String tab,
                                @RequestParam(defaultValue = "") String keyword,
                                @RequestParam(defaultValue = "") String status,
                                @RequestParam(defaultValue = "") String city,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                Model model) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        model.addAttribute("tab", tab);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("city", city);
        model.addAttribute("overview", adminService.getOverviewData());
        model.addAttribute("stats", adminService.getDashboardStats());
        model.addAttribute("hostPage", adminService.getHostPerformances(keyword, status, PageRequest.of(safePage, safeSize)));
        model.addAttribute("bookingPage", adminService.getBookings(keyword, status, PageRequest.of(safePage, safeSize)));
        model.addAttribute("propertyPage", adminService.getPropertiesAdmin(keyword, status, city, PageRequest.of(safePage, safeSize)));
        model.addAttribute("userPage", userService.getUsers(safePage, safeSize));
        model.addAttribute("bookingStatuses", List.of("PENDING", "CONFIRMED", "CANCELLED", "REJECTED", "COMPLETED"));
        model.addAttribute("propertyStatuses", List.of("ACTIVE", "INACTIVE", "DRAFT"));

        return "admin/dashboard";
    }

    @GetMapping("/hosts/{id}")
    public String viewHostDetail(@PathVariable Long id,
                                 @RequestParam(defaultValue = "financial") String subtab,
                                 Model model) {
        model.addAttribute("hostDetail", adminService.getHostDetail(id));
        model.addAttribute("subtab", subtab);
        return "admin/host-detail";
    }

    @GetMapping("/bookings")
    public String viewDashboardAndBookings(@RequestParam(defaultValue = "") String keyword,
                                           @RequestParam(defaultValue = "") String status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           Model model) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var bookingPage = adminService.getBookings(keyword, status, PageRequest.of(safePage, safeSize));
        model.addAttribute("stats", adminService.getDashboardStats());
        model.addAttribute("bookingPage", bookingPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("bookingStatuses", List.of("PENDING", "CONFIRMED", "CANCELLED", "REJECTED", "COMPLETED"));
        return "admin/bookings";
    }

    @GetMapping("/users")
    public String viewUser(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           Model model) {
        model.addAttribute("userPage", userService.getUsers(page, size));
        return "admin/users";
    }
}
