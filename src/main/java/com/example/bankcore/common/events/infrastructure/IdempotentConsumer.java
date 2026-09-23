package com.example.bankcore.common.events.infrastructure;

import com.example.bankcore.common.events.domain.OutboxRepository;
import com.example.bankcore.common.trace.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs a consumer's work at most once per event.
 *
 * <p>At-least-once delivery is not a flaw to be fixed at the broker; it is what a network can
 * promise. A consumer will see the same event again after a crash, a rebalance or a retry, and it
 * is the consumer's job to make that harmless.
 *
 * <p>The claim is an insert with {@code ON CONFLICT DO NOTHING}: whichever delivery inserts the
 * row does the work, the others do nothing. Checking "have I seen this?" and then working has a
 * race in exactly the situation redeliveries create.
 *
 * <p>The claim is released if the work throws, so a genuine failure is retried rather than
 * silently marked done.
 */
@Component
public class IdempotentConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

    private final OutboxRepository outbox;

    public IdempotentConsumer(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    /**
     * @param eventId  identity of the event, taken from the message header
     * @param consumer name of this consumer, so two consumers can each handle the same event
     * @param traceId  correlation id carried from the producer, restored for the duration
     * @param work     what to do, at most once
     */
    public void handle(UUID eventId, String consumer, String traceId, Consumer<UUID> work) {
        if (!outbox.markProcessed(eventId, consumer)) {
            log.debug("Duplicate delivery ignored: eventId={} consumer={}", eventId, consumer);
            return;
        }

        boolean hadTrace = traceId != null && !traceId.isBlank();
        if (hadTrace) {
            // Continues the request's trace into the consumer, so one id spans both sides.
            CorrelationId.set(traceId);
        }

        try {
            work.accept(eventId);
        } finally {
            if (hadTrace) {
                CorrelationId.clear();
            }
        }
    }
}
