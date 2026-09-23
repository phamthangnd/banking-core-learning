package com.example.bankcore.common.events.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An event waiting to be published, written in the same transaction as the change it describes.
 *
 * @param id          identity; also the deduplication key consumers use
 * @param topic       where it belongs
 * @param messageKey  partition key, so events about one aggregate keep their order
 * @param eventType   what happened
 * @param payload     JSON body; carries ids and facts, never balances or credentials
 * @param traceId     correlation id of the request that produced it
 * @param status      pending, published, or failed after exhausting retries
 * @param attempts    how many publish attempts have been made
 * @param lastError   why the last attempt failed
 */
public record OutboxEvent(
        UUID id,
        String topic,
        String messageKey,
        String eventType,
        String payload,
        String traceId,
        OutboxStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant publishedAt
) {

    private static final int MAX_ERROR_LENGTH = 500;

    public OutboxEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(messageKey, "messageKey must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (lastError != null && lastError.length() > MAX_ERROR_LENGTH) {
            lastError = lastError.substring(0, MAX_ERROR_LENGTH);
        }
    }

    public static OutboxEvent pending(UUID id, String topic, String messageKey, String eventType,
                                      String payload, String traceId, Instant now) {
        return new OutboxEvent(id, topic, messageKey, eventType, payload, traceId,
                OutboxStatus.PENDING, 0, null, now, null);
    }

    public OutboxEvent published(Instant now) {
        return new OutboxEvent(id, topic, messageKey, eventType, payload, traceId,
                OutboxStatus.PUBLISHED, attempts + 1, null, createdAt, now);
    }

    /**
     * Records a failed attempt.
     *
     * <p>Stays PENDING until the attempt limit is reached, so a broker that is briefly down
     * resolves itself. After the limit the row is parked as FAILED for a human to look at rather
     * than retried forever.
     */
    public OutboxEvent failed(String error, int maxAttempts, Instant now) {
        int nextAttempts = attempts + 1;
        OutboxStatus nextStatus = nextAttempts >= maxAttempts ? OutboxStatus.FAILED : OutboxStatus.PENDING;

        return new OutboxEvent(id, topic, messageKey, eventType, payload, traceId,
                nextStatus, nextAttempts, error, createdAt, null);
    }
}
