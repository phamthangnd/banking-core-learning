package com.example.bankcore.common.events.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What a consumer has already handled.
 *
 * <p>The composite primary key is the mechanism: a second delivery tries to insert the same
 * {@code (event, consumer)} pair and the database refuses it. Checking first and then inserting
 * has a race exactly where redeliveries live.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @EmbeddedId
    private ProcessedEventId id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    ProcessedEventEntity(UUID eventId, String consumer, Instant processedAt) {
        this.id = new ProcessedEventId(eventId, consumer);
        this.processedAt = processedAt;
    }

    /** Composite key: one row per event per consumer. */
    @Embeddable
    public static class ProcessedEventId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "event_id", nullable = false)
        private UUID eventId;

        @Column(name = "consumer", nullable = false, length = 80)
        private String consumer;

        protected ProcessedEventId() {
        }

        ProcessedEventId(UUID eventId, String consumer) {
            this.eventId = eventId;
            this.consumer = consumer;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProcessedEventId that)) {
                return false;
            }
            return Objects.equals(eventId, that.eventId) && Objects.equals(consumer, that.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumer);
        }
    }
}
