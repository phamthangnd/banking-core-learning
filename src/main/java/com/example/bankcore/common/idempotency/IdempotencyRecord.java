package com.example.bankcore.common.idempotency;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The record of one idempotency key.
 *
 * @param id                  identity
 * @param scope               which operation family the key belongs to
 * @param key                 the client-supplied key
 * @param requestFingerprint  SHA-256 of the canonical request
 * @param status              IN_PROGRESS while the first attempt runs, COMPLETED afterwards
 * @param transactionId       the financial effect this key produced, once completed
 * @param createdAt           when the key was first seen
 * @param completedAt         when the effect was recorded, or {@code null}
 */
public record IdempotencyRecord(
        UUID id,
        String scope,
        String key,
        String requestFingerprint,
        IdempotencyStatus status,
        UUID transactionId,
        Instant createdAt,
        Instant completedAt
) {

    public IdempotencyRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static IdempotencyRecord started(UUID id, String scope, String key,
                                            String requestFingerprint, Instant now) {
        return new IdempotencyRecord(id, scope, key, requestFingerprint,
                IdempotencyStatus.IN_PROGRESS, null, now, null);
    }

    public IdempotencyRecord completedWith(UUID effectId, Instant now) {
        return new IdempotencyRecord(id, scope, key, requestFingerprint,
                IdempotencyStatus.COMPLETED, effectId, createdAt, now);
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    public boolean matches(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }
}
