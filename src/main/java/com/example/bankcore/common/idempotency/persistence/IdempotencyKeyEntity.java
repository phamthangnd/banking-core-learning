package com.example.bankcore.common.idempotency.persistence;

import com.example.bankcore.common.idempotency.IdempotencyRecord;
import com.example.bankcore.common.idempotency.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scope", nullable = false, length = 50, updatable = false)
    private String scope;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyKeyEntity() {
    }

    private IdempotencyKeyEntity(IdempotencyRecord record) {
        this.id = record.id();
        this.scope = record.scope();
        this.idempotencyKey = record.key();
        this.requestFingerprint = record.requestFingerprint();
        this.createdAt = record.createdAt();
        applyState(record);
    }

    static IdempotencyKeyEntity fromDomain(IdempotencyRecord record) {
        return new IdempotencyKeyEntity(record);
    }

    void applyState(IdempotencyRecord record) {
        this.status = record.status();
        this.transactionId = record.transactionId();
        this.completedAt = record.completedAt();
    }

    IdempotencyRecord toDomain() {
        return new IdempotencyRecord(id, scope, idempotencyKey, requestFingerprint, status,
                transactionId, createdAt, completedAt);
    }
}
