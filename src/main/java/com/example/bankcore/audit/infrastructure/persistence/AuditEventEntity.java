package com.example.bankcore.audit.infrastructure.persistence;

import com.example.bankcore.audit.domain.AuditEvent;
import com.example.bankcore.audit.domain.AuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA mapping of an audit event. Every column is insert-only; the table is append-only. */
@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_name", length = 100, updatable = false)
    private String actorName;

    @Column(name = "action", nullable = false, length = 60, updatable = false)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 40, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 100, updatable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Column(name = "detail", length = 500, updatable = false)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEventEntity() {
    }

    private AuditEventEntity(AuditEvent event) {
        this.id = event.id();
        this.actorId = event.actorId();
        this.actorName = event.actorName();
        this.action = event.action();
        this.resourceType = event.resourceType();
        this.resourceId = event.resourceId();
        this.outcome = event.outcome();
        this.traceId = event.traceId();
        this.detail = event.detail();
        this.occurredAt = event.occurredAt();
    }

    static AuditEventEntity fromDomain(AuditEvent event) {
        return new AuditEventEntity(event);
    }

    AuditEvent toDomain() {
        return new AuditEvent(id, actorId, actorName, action, resourceType, resourceId,
                outcome, traceId, detail, occurredAt);
    }
}
