package com.stayhub.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyType;
import com.stayhub.support.PostgreSqlIntegrationTest;
import com.stayhub.user.User;
import com.stayhub.user.UserRepository;
import com.stayhub.user.UserRole;
import com.stayhub.user.UserStatus;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties={"app.mail.enabled=true","app.notification.outbox.poll-delay-ms=60000",
        "app.notification.outbox.initial-delay-ms=60000"})
class NotificationOutboxDispatcherSmokeTest extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired NotificationOutboxDispatcher dispatcher;
    @MockBean JavaMailSender mailSender;

    @BeforeEach void sender() {
        jdbc.execute("TRUNCATE booking_notification_outbox,refund_notification_outbox,payout_notification_outbox RESTART IDENTITY");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    private long enqueue(String prefix) {
        String nonce=prefix+"-"+UUID.randomUUID();
        User host=users.save(User.builder().email(nonce+"-host@test.invalid").fullName("Host")
                .passwordHash("test").role(UserRole.HOST).status(UserStatus.ACTIVE).build());
        User guest=users.save(User.builder().email(nonce+"-guest@test.invalid").fullName("Guest")
                .passwordHash("test").role(UserRole.GUEST).status(UserStatus.ACTIVE).build());
        Property property=properties.save(Property.builder().host(host).title("Outbox house").description("Frozen")
                .city("Da Lat").address("Address").pricePerNight(BigDecimal.valueOf(1_000_000))
                .cleaningFee(BigDecimal.ZERO).maxGuests(2).bedrooms(1).beds(1).bathrooms(1)
                .ratingAvg(BigDecimal.ZERO).propertyType(PropertyType.VILLA).status(PropertyStatus.ACTIVE).build());
        long booking=jdbc.queryForObject("""
                INSERT INTO bookings(property_id,guest_id,check_in_date,check_out_date,guests,nightly_price,cleaning_fee,
                    service_fee,subtotal_price,discount_amount,total_price,status,cancelled_at,created_at,updated_at)
                VALUES (?,?,?, ?,1,1000000,0,0,1000000,0,1000000,'PENDING_PAYMENT',NULL,clock_timestamp(),clock_timestamp())
                RETURNING id
                """,Long.class,property.getId(),guest.getId(),LocalDate.now().plusDays(3),LocalDate.now().plusDays(4));
        jdbc.update("UPDATE bookings SET status='CONFIRMED' WHERE id=?",booking);
        return jdbc.queryForObject("SELECT id FROM booking_notification_outbox WHERE booking_id=?",Long.class,booking);
    }

    @Test void deliveryIsAfterCommitAndSmtpFailureRetriesAreBounded() {
        long success=enqueue("notify-success");
        assertThat(jdbc.queryForObject("SELECT dispatched_at FROM booking_notification_outbox WHERE id=?",java.sql.Timestamp.class,success))
                .isNull();
        assertThat(dispatcher.dispatchBatch(10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT dispatched_at FROM booking_notification_outbox WHERE id=?",java.sql.Timestamp.class,success))
                .isNotNull();

        long failed=enqueue("notify-fail");
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));
        assertThat(dispatcher.dispatchBatch(10)).isZero();
        assertThat(dispatcher.dispatchBatch(10)).isZero();
        assertThat(dispatcher.dispatchBatch(10)).isZero();
        assertThat(jdbc.queryForObject("SELECT attempts FROM booking_notification_outbox WHERE id=?",Integer.class,failed)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM booking_notification_outbox WHERE id=?",String.class,failed))
                .isEqualTo("SMTP_DELIVERY_FAILED");
        assertThat(dispatcher.dispatchBatch(10)).isZero();
        verify(mailSender,times(4)).send(any(MimeMessage.class));
    }
}
