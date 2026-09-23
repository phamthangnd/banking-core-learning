package com.example.bankcore.common.events.infrastructure;

import com.example.bankcore.common.events.domain.OutboxEvent;
import com.example.bankcore.common.events.domain.OutboxRepository;
import com.example.bankcore.common.trace.CorrelationId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Moves pending outbox rows onto Kafka.
 *
 * <p>Runs on a schedule rather than being triggered by the write, so it also picks up events
 * left behind by a crash. Each row is published and marked separately: one broken event must not
 * block the ones behind it.
 *
 * <p>This is where <b>at-least-once</b> delivery comes from. If the broker accepts a message and
 * the process dies before the row is marked published, the event is sent again after a restart.
 * That is unavoidable without a distributed transaction, and it is why every consumer deduplicates.
 *
 * <p>After {@code maxAttempts} failures a row is parked as FAILED rather than retried forever —
 * the outbox's dead-letter equivalent, visible in the metrics and to an operator.
 */
@Component
@ConditionalOnProperty(name = "bankcore.events.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka, Clock clock,
                           @org.springframework.beans.factory.annotation.Value(
                                   "${bankcore.events.batch-size:100}") int batchSize,
                           @org.springframework.beans.factory.annotation.Value(
                                   "${bankcore.events.max-attempts:5}") int maxAttempts) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${bankcore.events.publish-interval-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findPending(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(event.topic(), event.messageKey(), event.payload());

            // Headers the consumer needs: the event id for deduplication, the type to dispatch on,
            // and the trace id so one request can be followed across the boundary.
            record.headers().add("eventId", event.id().toString().getBytes());
            record.headers().add("eventType", event.eventType().getBytes());
            if (event.traceId() != null) {
                record.headers().add(CorrelationId.HEADER, event.traceId().getBytes());
            }

            kafka.send(record).get();
            outbox.save(event.published(clock.instant()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            outbox.save(event.failed("interrupted", maxAttempts, clock.instant()));
        } catch (Exception ex) {
            OutboxEvent updated = outbox.save(event.failed(ex.getMessage(), maxAttempts, clock.instant()));

            if (updated.status() == com.example.bankcore.common.events.domain.OutboxStatus.FAILED) {
                log.error("Outbox event gave up after {} attempts: id={} type={}",
                        updated.attempts(), updated.id(), updated.eventType(), ex);
            } else {
                log.warn("Outbox publish failed, will retry: id={} attempt={}",
                        updated.id(), updated.attempts());
            }
        }
    }
}
