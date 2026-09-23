package com.example.bankcore.notification.web;

import com.example.bankcore.auth.web.CurrentUser;
import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.notification.application.NotificationService;
import com.example.bankcore.notification.domain.Notification;
import com.example.bankcore.notification.domain.NotificationType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The caller's own notification inbox.
 *
 * <p>Every endpoint works on the authenticated user, taken from the token. There is no
 * "notifications of user X" endpoint, because there is no reason for one and every reason not to.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> inbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unreadOnly) {

        PageRequest request = new PageRequest(Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE), "createdAt", SortDirection.DESC);

        PageResult<NotificationResponse> result = notifications
                .inbox(CurrentUser.requireId(), unreadOnly, request)
                .map(NotificationResponse::from);

        return ApiResponse.success(result.content(), Map.of(
                "page", result.page(),
                "size", result.size(),
                "totalElements", result.totalElements(),
                "totalPages", result.totalPages(),
                "hasNext", result.hasNext(),
                "unread", notifications.unreadCount(CurrentUser.requireId())));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("unread", notifications.unreadCount(CurrentUser.requireId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id) {
        return ApiResponse.success(NotificationResponse.from(
                notifications.markRead(CurrentUser.requireId(), id)));
    }

    @PostMapping("/{id}/unread")
    public ApiResponse<NotificationResponse> markUnread(@PathVariable UUID id) {
        return ApiResponse.success(NotificationResponse.from(
                notifications.markUnread(CurrentUser.requireId(), id)));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.success(Map.of("updated", notifications.markAllRead(CurrentUser.requireId())));
    }

    /** Response body for a notification. */
    public record NotificationResponse(UUID id, NotificationType type, String title, String body,
                                       String resourceType, UUID resourceId, boolean read,
                                       Instant readAt, Instant createdAt) {

        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.id(), notification.type(),
                    notification.title(), notification.body(), notification.resourceType(),
                    notification.resourceId(), notification.isRead(), notification.readAt(),
                    notification.createdAt());
        }
    }
}
