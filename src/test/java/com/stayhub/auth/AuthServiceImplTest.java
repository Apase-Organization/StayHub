package com.stayhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stayhub.auth.dto.RegisterRequest;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.DuplicateEmailException;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import java.util.Optional;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, mailSender);
        ReflectionTestUtils.setField(authService, "mailEnabled", false);
        ReflectionTestUtils.setField(authService, "fromAddress", "no-reply@stayhub.test");
        ReflectionTestUtils.setField(authService, "systemName", "StayHub System");
    }

    @Test
    void mvcNoticesAreVietnameseWithoutChangingServiceContracts() {
        assertThat(com.stayhub.common.exception.MvcExceptionHandler.displayMessage(
                "The verification code is invalid.")).isEqualTo("Mã xác minh không hợp lệ.");
        assertThat(com.stayhub.common.exception.MvcExceptionHandler.displayMessage(
                "An account with this email already exists.")).isEqualTo("Email này đã được đăng ký.");
        assertThat(com.stayhub.common.exception.MvcExceptionHandler.displayMessage("Thông báo tùy chỉnh"))
                .isEqualTo("Thông báo tùy chỉnh");
    }

    @Test
    void registrationRequiresOtpBeforeCreatingGuest() {
        RegisterRequest request = request(" User@Example.COM ");
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");

        authService.requestRegistrationOtp(request);
        verify(userRepository, never()).saveAndFlush(any(User.class));

        String otp = ((java.util.Map<String, ?>) ReflectionTestUtils.getField(authService, "pendingRegistrations"))
                .get("user@example.com")
                .toString()
                .replaceAll(".*otp=([0-9]{6}).*", "$1");
        authService.verifyRegistration("user@example.com", otp);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("testuser");
        assertThat(userCaptor.getValue().getFullName()).isEqualTo("Test User");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded");
    }

    @Test
    void registerRejectsExistingCanonicalEmail() {
        RegisterRequest request = request("USER@example.com");
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.requestRegistrationOtp(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("An account with this email already exists.");
    }

    @Test
    void registrationRejectsPasswordMismatch() {
        RegisterRequest request = request("user@example.com");
        request.setConfirmPassword("different-password");

        assertThatThrownBy(() -> authService.requestRegistrationOtp(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("ERR_PASSWORD_MISMATCH");
    }

    @Test
    void registerTranslatesConcurrentEmailConstraintViolation() {
        RegisterRequest request = request("user@example.com");
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "duplicate email",
                new SQLException(),
                "uk_users_email_canonical"
        );
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", constraintViolation));

        authService.requestRegistrationOtp(request);
        String otp = ((java.util.Map<String, ?>) ReflectionTestUtils.getField(authService, "pendingRegistrations"))
                .get("user@example.com")
                .toString()
                .replaceAll(".*otp=([0-9]{6}).*", "$1");

        assertThatThrownBy(() -> authService.verifyRegistration("user@example.com", otp))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void emailChangeTakesEffectOnlyAfterVerificationOfOtpSentToNewAddress() {
        User user = User.builder().email("old@example.com").fullName("Test User").build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByEmailAndIdNot("new@example.com", 42L)).thenReturn(false);

        authService.requestEmailChangeOtp(42L, " New@Example.com ");

        assertThat(user.getEmail()).isEqualTo("old@example.com");
        assertThat(authService.getPendingEmailChange(42L)).isEqualTo("new@example.com");
        String otp = ((java.util.Map<Long, ?>) ReflectionTestUtils.getField(authService, "pendingEmailChanges"))
                .get(42L).toString().replaceAll(".*otp=([0-9]{6}).*", "$1");

        authService.verifyEmailChangeOtp(42L, otp);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(authService.getPendingEmailChange(42L)).isNull();
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void invalidEmailChangeOtpLeavesCurrentEmailUnchanged() {
        User user = User.builder().email("old@example.com").fullName("Test User").build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        authService.requestEmailChangeOtp(42L, "new@example.com");

        String actualOtp = ((java.util.Map<Long, ?>) ReflectionTestUtils.getField(authService, "pendingEmailChanges"))
                .get(42L).toString().replaceAll(".*otp=([0-9]{6}).*", "$1");
        String wrongOtp = "000000".equals(actualOtp) ? "000001" : "000000";
        assertThatThrownBy(() -> authService.verifyEmailChangeOtp(42L, wrongOtp))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("ERR_OTP_INVALID");
        assertThat(user.getEmail()).isEqualTo("old@example.com");
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private RegisterRequest request(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setFullName(" Test User ");
        request.setUsername("TestUser");
        return request;
    }
}
