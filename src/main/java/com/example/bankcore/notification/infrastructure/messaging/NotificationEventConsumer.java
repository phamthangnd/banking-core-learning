package com.example.bankcore.notification.infrastructure.messaging;

import com.example.bankcore.common.events.domain.DomainEvents;
import com.example.bankcore.common.events.infrastructure.IdempotentConsumer;
import com.example.bankcore.common.trace.CorrelationId;
import com.example.bankcore.notification.application.NotificationService;
import com.example.bankcore.notification.domain.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Turns a posted transaction into a notification for the account's owner.
 *
 * <p>This is why the transaction path publishes an event instead of creating the notification
 * inline: a slow or failing notification must not hold up, or fail, the movement of money.
 *
 * <p>Retries and the dead-letter topic are configured on the listener container
 * ({@code KafkaConsumerConfig}); this method only has to be idempotent, which
 * {@link IdempotentConsumer} provides.
 */
@Component
@ConditionalOnProperty(name = "bankcore.events.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventConsumer {

    private static final String CONSUMER_NAME = "notification-consumer";

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notifications;
    private final IdempotentConsumer idempotency;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(NotificationService notifications, IdempotentConsumer idempotency,
                                     ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = DomainEvents.TOPIC_TRANSACTIONS, groupId = "bankcore-notifications")
    public void onTransactionEvent(ConsumerRecord<String, String> record) throws Exception {
        UUID eventId = headerAsUuid(record, "eventId");
        if (eventId == null) {
            log.warn("Transaction event without an eventId header; ignoring");
            return;
        }

        JsonNode payload = objectMapper.readTree(record.value());

        idempotency.handle(eventId, CONSUMER_NAME, header(record, CorrelationId.HEADER), ignored -> {
            String ownerId = payload.path("ownerUserId").asText(null);
            if (ownerId == null || ownerId.isBlank()) {
                return;
            }

            notifications.notify(UUID.fromString(ownerId), NotificationType.TRANSACTION,
                    "Transaction " + payload.path("reference").asText(""),
                    // A pointer, not a copy: no amount, no balance.
                    "A %s was posted on your account".formatted(payload.path("type").asText("transaction")
                            .toLowerCase(java.util.Locale.ROOT)),
                    "TRANSACTION", UUID.fromString(payload.path("transactionId").asText()));
        });
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static UUID headerAsUuid(ConsumerRecord<String, String> record, String name) {
        String value = header(record, name);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
