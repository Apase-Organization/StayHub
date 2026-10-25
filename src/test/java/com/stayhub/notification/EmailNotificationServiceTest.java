package com.stayhub.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingStatus;
import com.stayhub.property.Property;
import com.stayhub.user.User;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class EmailNotificationServiceTest {
    private JavaMailSender mailSender;
    private EmailNotificationService notifications;

    @BeforeEach void setUp() {
        mailSender=org.mockito.Mockito.mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        notifications=new EmailNotificationService(mailSender);
        ReflectionTestUtils.setField(notifications,"mailEnabled",true);
        ReflectionTestUtils.setField(notifications,"fromAddress","no-reply@stayhub.test");
        ReflectionTestUtils.setField(notifications,"systemName","StayHub System");
    }

    @Test void statusCallbackDoesNotSendInsideBookingTransaction() {
        notifications.bookingStatusChanged(booking());
        verify(mailSender,never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test void durableOutboxSenderSendsProvidedPayloadAndPropagatesSmtpFailureToDispatcher() throws Exception {
        notifications.sendOutboxEmail("guest@example.test","Booking confirmed","plain","<p>saved items</p>");
        verify(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        org.mockito.Mockito.doThrow(new MailSendException("timeout"))
                .when(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        assertThatThrownBy(()->notifications.sendOutboxEmail("guest@example.test","Booking confirmed","plain","html"))
                .isInstanceOf(MailSendException.class);
    }

    @Test void disabledTransportIsNotInvoked() {
        ReflectionTestUtils.setField(notifications,"mailEnabled",false);
        assertThatThrownBy(()->notifications.sendOutboxEmail("guest@example.test","subject","plain","html"))
                .isInstanceOf(IllegalStateException.class);
        verify(mailSender,never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    private Booking booking() {
        Booking booking=Booking.builder().guest(User.builder().email("guest@example.com").fullName("Guest").build())
                .property(Property.builder().title("Notice house").build()).status(BookingStatus.CONFIRMED).build();
        booking.setId(1L);
        return booking;
    }
}
