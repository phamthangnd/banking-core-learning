package com.example.bankcore.auth.infrastructure.persistence;

import com.example.bankcore.auth.domain.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA mapping of a stored refresh token. Holds the hash, never the raw token. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    protected RefreshTokenEntity() {
    }

    private RefreshTokenEntity(RefreshToken token) {
        this.id = token.id();
        this.userId = token.userId();
        this.tokenHash = token.tokenHash();
        this.issuedAt = token.issuedAt();
        this.expiresAt = token.expiresAt();
        applyState(token);
    }

    static RefreshTokenEntity fromDomain(RefreshToken token) {
        return new RefreshTokenEntity(token);
    }

    void applyState(RefreshToken token) {
        this.revokedAt = token.revokedAt();
        this.replacedBy = token.replacedBy();
    }

    RefreshToken toDomain() {
        return new RefreshToken(id, userId, tokenHash, issuedAt, expiresAt, revokedAt, replacedBy);
    }
}
