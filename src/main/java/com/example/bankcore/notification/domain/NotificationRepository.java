package com.example.bankcore.notification.domain;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for the notification inbox. */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    PageResult<Notification> findForUser(UUID userId, Boolean unreadOnly, PageRequest page);

    long countUnread(UUID userId);

    /** Marks every unread notification of a user read. Returns how many changed. */
    int markAllRead(UUID userId, java.time.Instant now);
}
