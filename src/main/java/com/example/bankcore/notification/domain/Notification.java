package com.example.bankcore.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One message in a user's inbox.
 *
 * <p>A notification is a <em>pointer</em>, not a copy: it says what happened and which record it
 * concerns, and the client fetches the record if the user is allowed to see it. Copying the
 * amount or the balance into the message would duplicate sensitive data into a store with weaker
 * access rules, and it would go stale.
 *
 * @param id           identity
 * @param userId       recipient
 * @param type         what kind of event this is
 * @param title        short headline
 * @param body         readable message, free of sensitive detail
 * @param resourceType kind of record it concerns, or {@code null}
 * @param resourceId   which record, or {@code null}
 * @param readAt       when the user read it, or {@code null}
 * @param createdAt    when it was created (UTC)
 */
public record Notification(
        UUID id,
        UUID userId,
        NotificationType type,
        String title,
        String body,
        String resourceType,
        UUID resourceId,
        Instant readAt,
        Instant createdAt
) {

    public Notification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Notification create(UUID id, UUID userId, NotificationType type, String title,
                                      String body, String resourceType, UUID resourceId, Instant now) {
        return new Notification(id, userId, type, title, body, resourceType, resourceId, null, now);
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Marking an already read notification read again changes nothing, so it is idempotent. */
    public Notification markRead(Instant now) {
        return isRead() ? this
                : new Notification(id, userId, type, title, body, resourceType, resourceId, now, createdAt);
    }

    public Notification markUnread() {
        return !isRead() ? this
                : new Notification(id, userId, type, title, body, resourceType, resourceId, null, createdAt);
    }
}
