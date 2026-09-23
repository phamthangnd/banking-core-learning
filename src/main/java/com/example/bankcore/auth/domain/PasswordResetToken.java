package com.example.bankcore.auth.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single-use password reset token, stored hashed.
 *
 * @param id        identity
 * @param userId    owner
 * @param tokenHash SHA-256 hex of the raw token
 * @param createdAt when it was issued
 * @param expiresAt when it stops being usable
 * @param usedAt    when it was redeemed, or {@code null}
 */
public record PasswordResetToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt,
        Instant usedAt
) {

    public PasswordResetToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static PasswordResetToken issue(UUID id, UUID userId, String tokenHash,
                                           Instant now, Instant expiresAt) {
        return new PasswordResetToken(id, userId, tokenHash, now, expiresAt, null);
    }

    public boolean isUsable(Clock clock) {
        return usedAt == null && expiresAt.isAfter(clock.instant());
    }

    public PasswordResetToken markUsed(Instant now) {
        return new PasswordResetToken(id, userId, tokenHash, createdAt, expiresAt, now);
    }
}
