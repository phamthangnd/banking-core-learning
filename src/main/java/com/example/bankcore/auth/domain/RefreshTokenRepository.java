package com.example.bankcore.auth.domain;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for refresh tokens. */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findById(UUID id);

    /**
     * Revokes every token still active for a user.
     *
     * <p>Used on logout-everywhere, on a password change, and as the breach response when a
     * rotated token is presented again.
     *
     * @return number of tokens revoked
     */
    int revokeAllForUser(UUID userId, java.time.Instant now);
}
