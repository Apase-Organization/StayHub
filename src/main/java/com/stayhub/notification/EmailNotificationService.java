package com.stayhub.notification;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService implements NotificationService {

    private static final Set<BookingStatus> NOTIFIABLE_STATUSES = Set.of(
            BookingStatus.CONFIRMED, BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.COMPLETED);
    private static final int MAX_RETRIES = 3;

    private final JavaMailSender mailSender;
    private final Set<String> sentNotifications = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@stayhub.local}")
    private String fromAddress;

    @Value("${app.mail.system-name:StayHub System}")
    private String systemName;

    @Override
    public void bookingStatusChanged(Booking booking) {
        if (!NOTIFIABLE_STATUSES.contains(booking.getStatus())) {
            return;
        }

        String dedupeKey = booking.getId() + ":" + booking.getStatus();
        if (!sentNotifications.add(dedupeKey)) {
            log.info("Duplicate notification ignored for booking {} with status {}.", booking.getId(), booking.getStatus());
            return;
        }

        if (!mailEnabled) {
            log.info("Mail notification disabled. Booking {} changed to {}.", booking.getId(), booking.getStatus());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, systemName);
            helper.setTo(booking.getGuest().getEmail());
            helper.setSubject("StayHub booking " + booking.getStatus().name().toLowerCase());
            helper.setText(buildBookingMessage(booking), EmailTemplateRenderer.bookingStatusEmail(
                    booking.getGuest().getFullName(),
                    booking.getProperty().getTitle(),
                    booking.getStatus().name(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getGuests(),
                    booking.getTotalPrice()
            ));

            int attempts = 0;
            boolean sent = false;
            while (attempts < MAX_RETRIES && !sent) {
                attempts++;
                try {
                    mailSender.send(message);
                    sent = true;
                    log.info("Sent booking {} notification to {} for booking {} (attempt {}/{})",
                            booking.getStatus(), booking.getGuest().getEmail(), booking.getId(), attempts, MAX_RETRIES);
                } catch (Exception ex) {
                    log.warn("Attempt {}/{} failed sending notification for booking {}: {}",
                            attempts, MAX_RETRIES, booking.getId(), ex.getMessage());
                    if (attempts >= MAX_RETRIES) {
                        log.error("Exhausted retries ({}) sending notification for booking {}. Safely dropped without transaction rollback.",
                                MAX_RETRIES, booking.getId(), ex);
                    }
                }
            }
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            log.warn("Unable to prepare booking status notification for booking {}", booking.getId(), exception);
        }
    }

    private String buildBookingMessage(Booking booking) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return "Hello " + booking.getGuest().getFullName() + ",\n\n"
                + "Your booking for " + booking.getProperty().getTitle() + " is now " + booking.getStatus() + ".\n"
                + "Check-in: " + formatter.format(booking.getCheckInDate()) + "\n"
                + "Check-out: " + formatter.format(booking.getCheckOutDate()) + "\n"
                + "Guests: " + booking.getGuests() + "\n"
                + "Total: " + booking.getTotalPrice() + "\n\n"
                + "StayHub";
    }
}
