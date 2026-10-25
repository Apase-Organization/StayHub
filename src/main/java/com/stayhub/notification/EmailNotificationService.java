package com.stayhub.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** SMTP boundary for the durable outbox dispatcher. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService implements NotificationService {
    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;
    @Value("${app.mail.from:no-reply@stayhub.local}")
    private String fromAddress;
    @Value("${app.mail.system-name:StayHub System}")
    private String systemName;

    /** Status writes are recorded by the DB outbox trigger in the caller's tx. */
    @Override
    public void bookingStatusChanged(com.stayhub.booking.Booking booking) {
        log.debug("Booking {} status notification is queued transactionally by the outbox",booking.getId());
    }

    public boolean isMailEnabled() { return mailEnabled; }

    public void sendOutboxEmail(String recipient,String subject,String plainText,String html)
            throws MessagingException,UnsupportedEncodingException {
        if(!mailEnabled) throw new IllegalStateException("mail transport is disabled");
        MimeMessage message=mailSender.createMimeMessage();
        MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
        helper.setFrom(fromAddress,systemName);
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(plainText,html);
        mailSender.send(message);
    }
}
