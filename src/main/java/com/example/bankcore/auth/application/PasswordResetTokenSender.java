package com.example.bankcore.auth.application;

import com.example.bankcore.user.domain.User;

/**
 * Delivers a password-reset token to its owner.
 *
 * <p>A port, because delivery is infrastructure: Phase 07 adds email. It exists now so the raw
 * token leaves {@code AuthService} through exactly one, auditable path — and so that path is
 * never "write it into the log" (CLAUDE.md section 4).
 */
public interface PasswordResetTokenSender {

    /**
     * @param user     recipient
     * @param rawToken the token itself; must never be logged, stored or echoed in a response
     */
    void send(User user, String rawToken);
}
