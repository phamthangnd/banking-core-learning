package com.example.bankcore.auth.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored refresh token. The raw value is never held here — only its hash.
 *
 * @param id         identity
 * @param userId     owner
 * @param tokenHash  SHA-256 hex of the raw token
 * @param issuedAt   when it was created
 * @param expiresAt  when it stops being usable
 * @param revokedAt  when it was revoked, or {@code null}
 * @param replacedBy the token that replaced it when rotated, or {@code null}
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedBy
) {

    public RefreshToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static RefreshToken issue(UUID id, UUID userId, String tokenHash, Instant now, Instant expiresAt) {
        return new RefreshToken(id, userId, tokenHash, now, expiresAt, null, null);
    }

    public boolean isExpired(Clock clock) {
        return !expiresAt.isAfter(clock.instant());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Usable exactly once, while it is neither expired nor revoked. */
    public boolean isUsable(Clock clock) {
        return !isRevoked() && !isExpired(clock);
    }

    public RefreshToken revoke(Instant now) {
        return isRevoked() ? this : new RefreshToken(id, userId, tokenHash, issuedAt, expiresAt, now, replacedBy);
    }

    /** Marks this token as rotated into {@code successorId} and revokes it in the same step. */
    public RefreshToken rotateInto(UUID successorId, Instant now) {
        return new RefreshToken(id, userId, tokenHash, issuedAt, expiresAt,
                revokedAt == null ? now : revokedAt, successorId);
    }
}
