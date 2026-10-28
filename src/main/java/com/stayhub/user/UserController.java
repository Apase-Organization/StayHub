package com.stayhub.user;

import com.stayhub.auth.AuthService;
import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.DuplicateEmailException;
import com.stayhub.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping
    public String showProfile(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        var user = userService.findById(principal.getId());
        if (!model.containsAttribute("profileRequest")) {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setEmail(user.getEmail());
            request.setFullName(user.getFullName());
            request.setPhone(user.getPhone());
            model.addAttribute("profileRequest", request);
        }
        model.addAttribute("user", user);
        model.addAttribute("pendingEmailChange", authService.getPendingEmailChange(principal.getId()));
        return "user/profile";
    }

    @PostMapping
    public String updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                @Valid @ModelAttribute("profileRequest") UpdateProfileRequest request,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", userService.findById(principal.getId()));
            model.addAttribute("pendingEmailChange", authService.getPendingEmailChange(principal.getId()));
            return "user/profile";
        }
        String currentEmail = userService.findById(principal.getId()).getEmail();
        var updatedUser = userService.updateProfile(principal.getId(), request);
        refreshAuthenticatedPrincipal(principal, updatedUser.getEmail(), updatedUser.getFullName());
        if (!currentEmail.equals(request.getEmail())) {
            try {
                authService.requestEmailChangeOtp(principal.getId(), request.getEmail());
                redirectAttributes.addFlashAttribute("message", "Thông tin hồ sơ đã lưu. Nhập mã xác minh được gửi đến email mới để hoàn tất thay đổi.");
            } catch (DuplicateEmailException exception) {
                bindingResult.rejectValue("email", exception.getErrorCode(), "Email này đã được đăng ký.");
                model.addAttribute("user", userService.findById(principal.getId()));
                model.addAttribute("pendingEmailChange", authService.getPendingEmailChange(principal.getId()));
                return "user/profile";
            } catch (BusinessException exception) {
                bindingResult.reject("emailChange", com.stayhub.common.exception.MvcExceptionHandler.displayMessage(exception.getMessage()));
                model.addAttribute("user", userService.findById(principal.getId()));
                model.addAttribute("pendingEmailChange", authService.getPendingEmailChange(principal.getId()));
                return "user/profile";
            }
        } else {
            redirectAttributes.addFlashAttribute("message", "Đã cập nhật hồ sơ thành công.");
        }
        return "redirect:/profile";
    }

    @PostMapping("/email/verify")
    public String verifyEmailChange(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestParam("otp") String otp,
                                    RedirectAttributes redirectAttributes) {
        try {
            String updatedEmail = authService.verifyEmailChangeOtp(principal.getId(), otp);
            refreshAuthenticatedPrincipal(principal, updatedEmail, principal.getFullName());
            redirectAttributes.addFlashAttribute("message", "Đã xác minh và cập nhật email. Địa chỉ mới sẽ nhận các thông báo đặt phòng tiếp theo.");
        } catch (DuplicateEmailException exception) {
            redirectAttributes.addFlashAttribute("emailChangeError", "Email này đã được đăng ký bởi tài khoản khác.");
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute("emailChangeError", com.stayhub.common.exception.MvcExceptionHandler.displayMessage(exception.getMessage()));
        }
        return "redirect:/profile";
    }

    private void refreshAuthenticatedPrincipal(UserPrincipal principal, String email, String fullName) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal refreshedPrincipal = new UserPrincipal(
                principal.getId(), email, principal.getPassword(), principal.getAuthorities(),
                principal.getStatus(), fullName, principal.getAttributes(), principal.getNameAttributeKey());
        UsernamePasswordAuthenticationToken refreshed = new UsernamePasswordAuthenticationToken(
                refreshedPrincipal, current.getCredentials(), principal.getAuthorities());
        refreshed.setDetails(current.getDetails());
        SecurityContextHolder.getContext().setAuthentication(refreshed);
    }
}
