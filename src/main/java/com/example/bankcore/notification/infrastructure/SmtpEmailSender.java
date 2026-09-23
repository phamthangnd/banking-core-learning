package com.example.bankcore.notification.infrastructure;

import com.example.bankcore.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends mail over SMTP — MailHog locally, a real relay in production.
 *
 * <p>A delivery failure is logged and swallowed rather than propagated: a notification is a
 * side effect, and failing a transfer because the confirmation email bounced would be worse than
 * the missing email. The failure is logged loudly so it is not invisible, and Phase 09 moves this
 * onto a queue with proper retries.
 */
@Component
@ConditionalOnProperty(name = "bankcore.notification.email.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @org.springframework.beans.factory.annotation.Value(
                                   "${bankcore.notification.email.from:no-reply@bankcore.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            // The recipient address is personal data; the log records that mail was sent, not who to.
            log.info("Notification email sent: subject={}", subject);
        } catch (MailException ex) {
            log.error("Failed to send a notification email: subject={}", subject, ex);
        }
    }
}
