package com.example.bankcore.notification.infrastructure;

import com.example.bankcore.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * The default when no mail server is configured: records that an email would have been sent.
 *
 * <p>Better than a no-op, because the intent is still visible in the log, and better than
 * requiring SMTP to run the application locally. The body is not logged — it may contain a reset
 * link or other content the log must not keep.
 */
@Component
@ConditionalOnMissingBean(SmtpEmailSender.class)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Email suppressed (no mail server configured): subject={}", subject);
    }
}
