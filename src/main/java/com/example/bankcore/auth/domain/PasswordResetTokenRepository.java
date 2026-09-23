package com.example.bankcore.auth.domain;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Invalidates outstanding tokens for a user, so only the newest request can be redeemed. */
    int invalidateAllForUser(java.util.UUID userId, Instant now);
}
