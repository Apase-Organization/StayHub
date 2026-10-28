package com.stayhub.auth;

import com.stayhub.auth.dto.RegisterRequest;
import com.stayhub.common.exception.BusinessException;
import com.stayhub.common.exception.DuplicateEmailException;
import com.stayhub.notification.EmailTemplateRenderer;
import com.stayhub.user.EmailNormalizer;
import com.stayhub.user.User;
import com.stayhub.user.UserLoginProvider;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email_canonical";
    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_users_username";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final Map<String, PendingRegistration> pendingRegistrations = new ConcurrentHashMap<>();
    private final Map<Long, PendingEmailChange> pendingEmailChanges = new ConcurrentHashMap<>();

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@stayhub.local}")
    private String fromAddress;

    @Value("${app.mail.system-name:StayHub System}")
    private String systemName;

    @Override
    public void requestRegistrationOtp(RegisterRequest request) {
        validatePasswordsMatch(request);
        String email = EmailNormalizer.normalize(request.getEmail());
        String username = normalizeUsername(request.getUsername());
        ensureUniqueAccount(email, username);

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        pendingRegistrations.put(email, new PendingRegistration(
                request.getFullName().trim(), username, email, request.getPassword(), otp,
                Instant.now().plus(10, ChronoUnit.MINUTES)));
        sendRegistrationOtp(email, request.getFullName().trim(), otp);
    }

    @Override
    @Transactional
    public void verifyRegistration(String email, String otp) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        PendingRegistration pending = pendingRegistrations.get(normalizedEmail);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            pendingRegistrations.remove(normalizedEmail);
            throw new BusinessException("ERR_OTP_EXPIRED", "The verification code has expired. Please register again.");
        }
        if (!pending.otp().equals(otp)) {
            throw new BusinessException("ERR_OTP_INVALID", "The verification code is invalid.");
        }
        ensureUniqueAccount(pending.email(), pending.username());

        User user = User.builder()
                .email(pending.email())
                .username(pending.username())
                .passwordHash(passwordEncoder.encode(pending.password()))
                .fullName(pending.fullName())
                .role(UserRole.GUEST)
                .status(UserStatus.ACTIVE)
                .loginProvider(UserLoginProvider.LOCAL)
                .build();

        try {
            userRepository.saveAndFlush(user);
            pendingRegistrations.remove(normalizedEmail);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception)) {
                throw new DuplicateEmailException();
            }
            if (isUsernameConstraintViolation(exception)) {
                throw new BusinessException("ERR_USERNAME_EXISTS", "This username is already taken.");
            }
            throw exception;
        }
    }

    @Override
    public void requestEmailChangeOtp(Long userId, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("ERR_USER_NOT_FOUND", "User account was not found."));
        String normalizedEmail = EmailNormalizer.normalize(newEmail);
        if (normalizedEmail.equals(user.getEmail())) {
            pendingEmailChanges.remove(userId);
            return;
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        pendingEmailChanges.put(userId, new PendingEmailChange(
                normalizedEmail, otp, Instant.now().plus(10, ChronoUnit.MINUTES)));
        sendEmailChangeOtp(userId, normalizedEmail, user.getFullName(), otp);
    }

    @Override
    @Transactional
    public String verifyEmailChangeOtp(Long userId, String otp) {
        PendingEmailChange pending = pendingEmailChanges.get(userId);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            pendingEmailChanges.remove(userId);
            throw new BusinessException("ERR_OTP_EXPIRED", "The verification code has expired. Please request a new code.");
        }
        if (otp == null || !pending.otp().equals(otp.trim())) {
            throw new BusinessException("ERR_OTP_INVALID", "The verification code is invalid.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("ERR_USER_NOT_FOUND", "User account was not found."));
        if (userRepository.existsByEmailAndIdNot(pending.email(), userId)) {
            throw new DuplicateEmailException();
        }
        user.setEmail(pending.email());
        try {
            userRepository.saveAndFlush(user);
            pendingEmailChanges.remove(userId);
            return user.getEmail();
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception)) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    @Override
    public String getPendingEmailChange(Long userId) {
        PendingEmailChange pending = pendingEmailChanges.get(userId);
        if (pending == null) {
            return null;
        }
        if (pending.expiresAt().isBefore(Instant.now())) {
            pendingEmailChanges.remove(userId);
            return null;
        }
        return pending.email();
    }

    private void ensureUniqueAccount(String email, String username) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("ERR_USERNAME_EXISTS", "This username is already taken.");
        }
    }

    private void validatePasswordsMatch(RegisterRequest request) {
        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("ERR_PASSWORD_MISMATCH", "Password confirmation does not match.");
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    private void sendRegistrationOtp(String email, String fullName, String otp) {
        if (!mailEnabled) {
            log.info("Mail disabled. Registration OTP for {} is {}", email, otp);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, systemName);
            helper.setTo(email);
            helper.setSubject("StayHub email verification code");
            String text = "Hello " + fullName + ",\n\n"
                    + "Your StayHub verification code is: " + otp + "\n"
                    + "This code expires in 10 minutes.\n\n"
                    + "StayHub";
            helper.setText(text, EmailTemplateRenderer.otpEmail(fullName, otp));
            mailSender.send(message);
            log.info("Sent registration OTP email to {}", email);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            pendingRegistrations.remove(email);
            log.warn("Unable to send registration OTP email to {}", email, exception);
            throw new BusinessException("ERR_MAIL_SEND", "Unable to send verification email. Please try again later.");
        }
    }

    private void sendEmailChangeOtp(Long userId, String email, String fullName, String otp) {
        if (!mailEnabled) {
            log.info("Mail disabled. Email change OTP for {} is {}", email, otp);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, systemName);
            helper.setTo(email);
            helper.setSubject("StayHub email change verification code");
            String text = "Hello " + fullName + ",\n\n"
                    + "Your StayHub email change verification code is: " + otp + "\n"
                    + "This code expires in 10 minutes. Your account email will not change until this code is verified.\n\n"
                    + "StayHub";
            helper.setText(text, EmailTemplateRenderer.otpEmail(fullName, otp));
            mailSender.send(message);
            log.info("Sent email change OTP to {}", email);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            pendingEmailChanges.remove(userId);
            log.warn("Unable to send email change OTP to {}", email, exception);
            throw new BusinessException("ERR_MAIL_SEND", "Unable to send verification email. Please try again later.");
        }
    }

    private boolean isEmailConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && isEmailConstraint(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isUsernameConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && USERNAME_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isEmailConstraint(String constraintName) {
        return constraintName != null && (
                EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintName)
                        || "users_email_key".equalsIgnoreCase(constraintName)
                        || "uk_users_email".equalsIgnoreCase(constraintName)
        );
    }

    private record PendingRegistration(String fullName, String username, String email, String password,
                                       String otp, Instant expiresAt) {
    }

    private record PendingEmailChange(String email, String otp, Instant expiresAt) {
    }
}
