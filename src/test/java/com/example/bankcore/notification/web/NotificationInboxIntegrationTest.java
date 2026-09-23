package com.example.bankcore.notification.web;

import com.example.bankcore.notification.application.NotificationService;
import com.example.bankcore.notification.domain.Notification;
import com.example.bankcore.notification.domain.NotificationType;
import com.example.bankcore.support.DatabaseCleaner;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.support.TestUsers;
import com.example.bankcore.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The inbox: read/unread state, counts, and the fact that it is strictly per user. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class NotificationInboxIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        databaseCleaner.clear();
        owner = testUsers.create("inbox-owner", "CUSTOMER");
        other = testUsers.create("inbox-other", "CUSTOMER");
    }

    private Notification notifyOwner(String title) {
        return notifications.notify(owner.id(), NotificationType.TRANSACTION, title,
                "A transfer was posted to your account", "TRANSACTION", java.util.UUID.randomUUID());
    }

    @Test
    @WithMockUser
    void shouldListTheCallersOwnNotificationsNewestFirst() throws Exception {
        notifyOwner("first");
        notifyOwner("second");

        mockMvc.perform(get("/api/v1/notifications").with(asOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.metadata.unread").value(2))
                .andExpect(jsonPath("$.data[0].read").value(false));
    }

    @Test
    void shouldMarkOneNotificationReadAndBackToUnread() throws Exception {
        Notification notification = notifyOwner("mark me");

        mockMvc.perform(post("/api/v1/notifications/{id}/read", notification.id()).with(asOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(asOwner()))
                .andExpect(jsonPath("$.data.unread").value(0));

        mockMvc.perform(post("/api/v1/notifications/{id}/unread", notification.id()).with(asOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(false));

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(asOwner()))
                .andExpect(jsonPath("$.data.unread").value(1));
    }

    @Test
    void markingReadTwiceShouldChangeNothing() throws Exception {
        Notification notification = notifyOwner("idempotent");

        String first = mockMvc.perform(post("/api/v1/notifications/{id}/read", notification.id()).with(asOwner()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/v1/notifications/{id}/read", notification.id()).with(asOwner()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // The timestamp must not move: the user read it once.
        assertThat(first.replaceAll("\"timestamp\":\"[^\"]+\"", "").replaceAll("\"traceId\":\"[^\"]+\"", ""))
                .isEqualTo(second.replaceAll("\"timestamp\":\"[^\"]+\"", "").replaceAll("\"traceId\":\"[^\"]+\"", ""));
    }

    @Test
    void shouldFilterToUnreadOnly() throws Exception {
        Notification read = notifyOwner("read one");
        notifyOwner("unread one");
        notifications.markRead(owner.id(), read.id());

        mockMvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").with(asOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("unread one"));
    }

    @Test
    void shouldMarkEverythingRead() throws Exception {
        notifyOwner("a");
        notifyOwner("b");
        notifyOwner("c");

        mockMvc.perform(post("/api/v1/notifications/read-all").with(asOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(3));

        assertThat(notifications.unreadCount(owner.id())).isZero();
    }

    @Test
    void shouldNotShowOrTouchAnotherUsersNotifications() throws Exception {
        Notification theirs = notifyOwner("private");

        mockMvc.perform(get("/api/v1/notifications").with(asOther()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // A 404 rather than a 403: confirming that it exists would already be a leak.
        mockMvc.perform(post("/api/v1/notifications/{id}/read", theirs.id()).with(asOther()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asOwner() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .jwt().jwt(jwt -> jwt.subject(owner.id().toString()));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asOther() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .jwt().jwt(jwt -> jwt.subject(other.id().toString()));
    }
}
