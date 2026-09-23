package com.example.bankcore.auth.infrastructure.security;

import com.example.bankcore.auth.application.PasswordResetTokenSender;
import com.example.bankcore.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder delivery until Phase 07 wires real notifications.
 *
 * <p>It records that a token was issued and drops the token itself. Writing the token to the log
 * would be the convenient thing to do for local testing and is exactly what CLAUDE.md section 4
 * forbids: logs are copied, shipped and read by people who are not the account's owner. The
 * reset flow is therefore exercised by integration tests, which capture the token through this
 * same port.
 */
@Component
public class LoggingPasswordResetTokenSender implements PasswordResetTokenSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetTokenSender.class);

    @Override
    public void send(User user, String rawToken) {
        log.info("Password reset token ready for delivery: userId={} (delivery arrives in Phase 07)",
                user.id());
    }
}
