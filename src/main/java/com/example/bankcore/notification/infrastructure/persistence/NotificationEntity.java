package com.example.bankcore.notification.infrastructure.persistence;

import com.example.bankcore.notification.domain.Notification;
import com.example.bankcore.notification.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 150, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, length = 1000, updatable = false)
    private String body;

    @Column(name = "resource_type", length = 40, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    /** The only mutable column: read state is the only thing about a notification that changes. */
    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationEntity() {
    }

    private NotificationEntity(Notification notification) {
        this.id = notification.id();
        this.userId = notification.userId();
        this.type = notification.type();
        this.title = notification.title();
        this.body = notification.body();
        this.resourceType = notification.resourceType();
        this.resourceId = notification.resourceId();
        this.createdAt = notification.createdAt();
        applyState(notification);
    }

    static NotificationEntity fromDomain(Notification notification) {
        return new NotificationEntity(notification);
    }

    void applyState(Notification notification) {
        this.readAt = notification.readAt();
    }

    Notification toDomain() {
        return new Notification(id, userId, type, title, body, resourceType, resourceId, readAt, createdAt);
    }
}
