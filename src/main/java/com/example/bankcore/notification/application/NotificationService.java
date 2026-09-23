package com.example.bankcore.notification.application;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.notification.domain.Notification;
import com.example.bankcore.notification.domain.NotificationRepository;
import com.example.bankcore.notification.domain.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * The notification inbox.
 *
 * <p>Authorization here is ownership, not a permission: every method takes the recipient's id
 * from the caller's own token, and reading or changing someone else's notification is a 404
 * rather than a 403 — telling an attacker that a notification exists but is not theirs is itself
 * a small leak.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Creates a notification for a user. Called by other modules, never by a client directly. */
    @Transactional
    public Notification notify(UUID userId, NotificationType type, String title, String body,
                               String resourceType, UUID resourceId) {
        Notification created = notifications.save(Notification.create(UUID.randomUUID(), userId,
                type, title, body, resourceType, resourceId, clock.instant()));

        log.debug("Notification created: userId={} type={}", userId, type);
        return created;
    }

    public PageResult<Notification> inbox(UUID userId, Boolean unreadOnly, PageRequest page) {
        return notifications.findForUser(userId, unreadOnly, page);
    }

    public long unreadCount(UUID userId) {
        return notifications.countUnread(userId);
    }

    @Transactional
    public Notification markRead(UUID userId, UUID notificationId) {
        Notification notification = requireOwned(userId, notificationId);
        return notifications.save(notification.markRead(clock.instant()));
    }

    @Transactional
    public Notification markUnread(UUID userId, UUID notificationId) {
        Notification notification = requireOwned(userId, notificationId);
        return notifications.save(notification.markUnread());
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, clock.instant());
    }

    private Notification requireOwned(UUID userId, UUID notificationId) {
        return notifications.findById(notificationId)
                .filter(notification -> notification.userId().equals(userId))
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    /** Also used when the notification exists but belongs to someone else. */
    public static class NotificationNotFoundException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public NotificationNotFoundException(UUID id) {
            super(ErrorCode.NOTIFICATION_NOT_FOUND, "Notification %s was not found".formatted(id));
        }
    }
}
