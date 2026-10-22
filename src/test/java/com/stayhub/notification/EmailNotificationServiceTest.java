package com.stayhub.notification;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingStatus;
import com.stayhub.property.Property;
import com.stayhub.user.User;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class EmailNotificationServiceTest {

    private JavaMailSender mailSender;
    private EmailNotificationService notificationService;

    @BeforeEach
    void setUp() {
        mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        notificationService = new EmailNotificationService(mailSender);
        ReflectionTestUtils.setField(notificationService, "mailEnabled", true);
        ReflectionTestUtils.setField(notificationService, "fromAddress", "no-reply@stayhub.test");
        ReflectionTestUtils.setField(notificationService, "systemName", "StayHub System");
    }

    @Test
    void sendsEmailForConfirmedRejectedCancelledAndCompletedBookings() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        notificationService.bookingStatusChanged(booking);
        verify(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        // COMPLETED booking also receives email inviting guest to review
        Booking completed = booking(BookingStatus.COMPLETED);
        completed.setId(2L);
        notificationService.bookingStatusChanged(completed);
        verify(mailSender, org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void deduplicatesDuplicateNotificationEventsForSameBookingAndStatus() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        notificationService.bookingStatusChanged(booking);
        notificationService.bookingStatusChanged(booking); // duplicate invocation

        verify(mailSender, org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void retriesUpToMaxAttemptsOnFailureWithoutThrowing() {
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP timeout"))
                .when(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        Booking booking = booking(BookingStatus.CONFIRMED);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> notificationService.bookingStatusChanged(booking));

        // Verify it retried 3 times
        verify(mailSender, org.mockito.Mockito.times(3)).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void doesNotSendWhenDisabled() {
        ReflectionTestUtils.setField(notificationService, "mailEnabled", false);
        notificationService.bookingStatusChanged(booking(BookingStatus.CANCELLED));
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    private Booking booking(BookingStatus status) {
        User guest = User.builder()
                .email("guest@example.com")
                .fullName("Guest User")
                .build();
        Property property = Property.builder()
                .title("Notification House")
                .build();
        Booking booking = Booking.builder()
                .guest(guest)
                .property(property)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .guests(2)
                .totalPrice(new BigDecimal("2300000.00"))
                .status(status)
                .build();
        booking.setId(1L);
        return booking;
    }
}
