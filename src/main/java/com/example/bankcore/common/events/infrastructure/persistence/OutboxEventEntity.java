package com.example.bankcore.common.events.infrastructure.persistence;

import com.example.bankcore.common.events.domain.OutboxEvent;
import com.example.bankcore.common.events.domain.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "topic", nullable = false, length = 100, updatable = false)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 100, updatable = false)
    private String messageKey;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {
    }

    private OutboxEventEntity(OutboxEvent event) {
        this.id = event.id();
        this.topic = event.topic();
        this.messageKey = event.messageKey();
        this.eventType = event.eventType();
        this.payload = event.payload();
        this.traceId = event.traceId();
        this.createdAt = event.createdAt();
        applyState(event);
    }

    static OutboxEventEntity fromDomain(OutboxEvent event) {
        return new OutboxEventEntity(event);
    }

    void applyState(OutboxEvent event) {
        this.status = event.status();
        this.attempts = event.attempts();
        this.lastError = event.lastError();
        this.publishedAt = event.publishedAt();
    }

    OutboxEvent toDomain() {
        return new OutboxEvent(id, topic, messageKey, eventType, payload, traceId, status,
                attempts, lastError, createdAt, publishedAt);
    }
}
