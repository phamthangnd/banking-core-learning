package com.example.bankcore.auth.infrastructure.persistence;

import com.example.bankcore.auth.domain.PasswordResetToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA mapping of a password-reset token. Holds the hash, never the raw token. */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordResetTokenEntity() {
    }

    private PasswordResetTokenEntity(PasswordResetToken token) {
        this.id = token.id();
        this.userId = token.userId();
        this.tokenHash = token.tokenHash();
        this.createdAt = token.createdAt();
        applyState(token);
    }

    static PasswordResetTokenEntity fromDomain(PasswordResetToken token) {
        return new PasswordResetTokenEntity(token);
    }

    void applyState(PasswordResetToken token) {
        this.expiresAt = token.expiresAt();
        this.usedAt = token.usedAt();
    }

    PasswordResetToken toDomain() {
        return new PasswordResetToken(id, userId, tokenHash, createdAt, expiresAt, usedAt);
    }
}
