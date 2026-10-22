package com.stayhub.admin;

import com.stayhub.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final AdminService adminService;
    private final UserService userService;

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
