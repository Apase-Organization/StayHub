package com.stayhub.auth;

import com.stayhub.auth.dto.RegisterRequest;
import com.stayhub.auth.dto.VerifyRegistrationRequest;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.DuplicateEmailException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public String showLoginForm() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String processRegistration(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                                      BindingResult bindingResult,
                                      Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            authService.requestRegistrationOtp(request);
            return "redirect:/register/verify?email=" + request.getEmail();
        } catch (DuplicateEmailException exception) {
            bindingResult.rejectValue("email", exception.getErrorCode(), "Email này đã được đăng ký.");
            request.setPassword(null);
            request.setConfirmPassword(null);
            return "auth/register";
        } catch (BusinessException exception) {
            rejectRegistrationBusinessError(exception, bindingResult);
            request.setPassword(null);
            request.setConfirmPassword(null);
            return "auth/register";
        }
    }

    @GetMapping("/register/verify")
    public String showVerifyRegistrationForm(@RequestParam String email, Model model) {
        VerifyRegistrationRequest request = new VerifyRegistrationRequest();
        request.setEmail(email);
        model.addAttribute("verifyRegistrationRequest", request);
        return "auth/verify-registration";
    }

    @PostMapping("/register/verify")
    public String verifyRegistration(@Valid @ModelAttribute("verifyRegistrationRequest") VerifyRegistrationRequest request,
                                     BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "auth/verify-registration";
        }
        try {
            authService.verifyRegistration(request.getEmail(), request.getOtp());
            return "redirect:/login?registered=true";
        } catch (DuplicateEmailException exception) {
            bindingResult.rejectValue("email", exception.getErrorCode(), "Email này đã được đăng ký.");
            return "auth/verify-registration";
        } catch (BusinessException exception) {
            bindingResult.rejectValue("otp", exception.getErrorCode(), com.stayhub.common.exception.MvcExceptionHandler.displayMessage(exception.getMessage()));
            return "auth/verify-registration";
        }
    }

    private void rejectRegistrationBusinessError(BusinessException exception, BindingResult bindingResult) {
        if ("ERR_USERNAME_EXISTS".equals(exception.getErrorCode())) {
            bindingResult.rejectValue("username", exception.getErrorCode(), com.stayhub.common.exception.MvcExceptionHandler.displayMessage(exception.getMessage()));
        } else if ("ERR_PASSWORD_MISMATCH".equals(exception.getErrorCode())) {
            bindingResult.rejectValue("confirmPassword", exception.getErrorCode(), com.stayhub.common.exception.MvcExceptionHandler.displayMessage(exception.getMessage()));
        } else {
            bindingResult.reject(exception.getErrorCode(), com.stayhub.common.exception.MvcExceptionHandler.displayMessage(exception.getMessage()));
        }
    }
}
