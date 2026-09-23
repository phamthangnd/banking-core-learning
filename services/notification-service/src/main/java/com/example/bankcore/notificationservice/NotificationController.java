package com.example.bankcore.notificationservice;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The service's read API.
 *
 * <p>Authentication is deliberately absent here: in a real deployment this service sits behind
 * the same gateway as the monolith and validates the same JWT, with the signing key shared by
 * the identity provider rather than by a library. Writing a second, subtly different
 * authentication implementation would be the worst of both worlds, so this endpoint takes the
 * user id as a path variable and is reachable only from inside the network.
 *
 * <p>That is a real gap, recorded in the ADR rather than papered over.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationController(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @GetMapping("/users/{userId}")
    public Map<String, Object> inbox(@PathVariable UUID userId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        var result = notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<Map<String, Object>> content = result.getContent().stream()
                .map(NotificationController::toResponse)
                .toList();

        return Map.of(
                "data", content,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "unread", notifications.countByUserIdAndReadAtIsNull(userId));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable UUID id) {
        return notifications.findById(id)
                .map(notification -> {
                    notification.markRead(clock.instant());
                    return ResponseEntity.ok(toResponse(notifications.save(notification)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static Map<String, Object> toResponse(Notification notification) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", notification.getId());
        response.put("type", notification.getType());
        response.put("title", notification.getTitle());
        response.put("body", notification.getBody());
        response.put("read", notification.isRead());
        response.put("createdAt", notification.getCreatedAt());
        if (notification.getResourceId() != null) {
            response.put("resourceType", notification.getResourceType());
            response.put("resourceId", notification.getResourceId());
        }
        return response;
    }
}
