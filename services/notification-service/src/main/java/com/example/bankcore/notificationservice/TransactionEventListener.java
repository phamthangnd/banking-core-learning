package com.example.bankcore.notificationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/**
 * The service's only connection to the rest of the bank.
 *
 * <p>It reads an event and writes a notification. It does not call the monolith, and the monolith
 * does not call it — which is exactly what makes a slow or failing notification unable to hold up
 * a transfer, and what lets either side be deployed without the other.
 *
 * <p>The contract is the event's shape, and it is treated as a contract: unknown fields are
 * ignored, absent optional fields are tolerated, and the listener never assumes more than the
 * producer promised. A consumer that breaks when the producer adds a field has turned an event
 * into a distributed method call.
 */
@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final NotificationRepository notifications;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TransactionEventListener(NotificationRepository notifications,
                                    ProcessedEventRepository processedEvents,
                                    ObjectMapper objectMapper, Clock clock) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @KafkaListener(topics = "bankcore.transactions", groupId = "notification-service")
    @Transactional
    public void onTransactionPosted(ConsumerRecord<String, String> record) throws Exception {
        UUID eventId = headerAsUuid(record);
        if (eventId == null) {
            // Without an id the event cannot be deduplicated, so handling it risks duplicates.
            // Dropping it is the safer failure, and it is loud in the log.
            log.warn("Transaction event without an eventId header; ignoring");
            return;
        }

        if (processedEvents.claim(eventId, clock.instant()) != 1) {
            log.debug("Duplicate delivery ignored: eventId={}", eventId);
            return;
        }

        JsonNode payload = objectMapper.readTree(record.value());
        String ownerUserId = payload.path("ownerUserId").asText(null);

        if (ownerUserId == null || ownerUserId.isBlank()) {
            // The monolith has no customer-to-user link yet, so most events name no recipient.
            // Eventual consistency in practice: the event is accepted and simply produces
            // nothing, rather than being retried forever against a fact that will not appear.
            return;
        }

        notifications.save(new Notification(UUID.randomUUID(), UUID.fromString(ownerUserId),
                "TRANSACTION",
                "Transaction " + payload.path("reference").asText(""),
                "A %s was posted on your account".formatted(
                        payload.path("type").asText("transaction").toLowerCase(Locale.ROOT)),
                "TRANSACTION",
                UUID.fromString(payload.path("transactionId").asText()),
                clock.instant()));

        log.info("Notification created from event: eventId={}", eventId);
    }

    private static UUID headerAsUuid(ConsumerRecord<String, String> record) {
        var header = record.headers().lastHeader("eventId");
        if (header == null) {
            return null;
        }
        try {
            return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
