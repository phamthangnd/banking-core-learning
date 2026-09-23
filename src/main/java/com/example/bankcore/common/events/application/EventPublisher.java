package com.example.bankcore.common.events.application;

import com.example.bankcore.common.events.domain.OutboxEvent;
import com.example.bankcore.common.events.domain.OutboxRepository;
import com.example.bankcore.common.trace.CorrelationId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Records an event to be published after the current transaction commits.
 *
 * <p>It writes to the outbox table, not to Kafka. That is the whole point: the event and the
 * business change are one transaction, so an event can never describe something that rolled back,
 * and a crash between the commit and the publish loses nothing — the row is still there.
 *
 * <p>{@code MANDATORY} propagation makes the guarantee enforceable: calling this outside a
 * transaction is a bug, and it fails immediately instead of quietly publishing something that was
 * never persisted.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EventPublisher(OutboxRepository outbox, ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param topic      where the event belongs
     * @param messageKey partition key; events about one aggregate keep their order
     * @param eventType  what happened
     * @param payload    a record or map carrying ids and facts — never balances or credentials
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public OutboxEvent publish(String topic, String messageKey, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            // An event that cannot be serialised is a programming error, and failing the business
            // transaction is better than committing it with a silently missing event.
            throw new IllegalArgumentException("Event payload could not be serialised", ex);
        }

        OutboxEvent event = outbox.append(OutboxEvent.pending(UUID.randomUUID(), topic, messageKey,
                eventType, json, CorrelationId.currentOrNull(), clock.instant()));

        log.debug("Event queued in the outbox: type={} topic={}", eventType, topic);
        return event;
    }
}
