package com.example.bankcore.notification.infrastructure.persistence;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.notification.domain.Notification;
import com.example.bankcore.notification.domain.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaNotificationRepository implements NotificationRepository {

    private final NotificationJpaRepository notifications;

    public JpaNotificationRepository(NotificationJpaRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    @Transactional
    public Notification save(Notification notification) {
        NotificationEntity entity = notifications.findById(notification.id())
                .map(existing -> {
                    existing.applyState(notification);
                    return existing;
                })
                .orElseGet(() -> NotificationEntity.fromDomain(notification));

        return notifications.save(entity).toDomain();
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return notifications.findById(id).map(NotificationEntity::toDomain);
    }

    @Override
    public PageResult<Notification> findForUser(UUID userId, Boolean unreadOnly, PageRequest page) {
        Pageable pageable = toPageable(page);

        Page<NotificationEntity> result = Boolean.TRUE.equals(unreadOnly)
                ? notifications.findByUserIdAndReadAtIsNull(userId, pageable)
                : notifications.findByUserId(userId, pageable);

        return new PageResult<>(result.getContent().stream().map(NotificationEntity::toDomain).toList(),
                page.page(), page.size(), result.getTotalElements());
    }

    @Override
    public long countUnread(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId, Instant now) {
        return notifications.markAllRead(userId, now);
    }

    private static Pageable toPageable(PageRequest request) {
        Sort.Direction direction = request.direction() == SortDirection.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        return org.springframework.data.domain.PageRequest.of(request.page(), request.size(),
                Sort.by(direction, request.sortProperty()).and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
