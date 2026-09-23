package com.example.bankcore.common.events.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventJpaRepository
        extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.ProcessedEventId> {

    /**
     * Claims an event for a consumer, or does nothing if it was already claimed.
     *
     * <p>{@code ON CONFLICT DO NOTHING} rather than an insert in a try/catch: a caught constraint
     * violation leaves the transaction marked rollback-only, as Phase 06 found the hard way.
     *
     * @return 1 if this call claimed it, 0 if it was already processed
     */
    @Modifying
    @Query(value = """
            insert into processed_events (event_id, consumer, processed_at)
            values (:eventId, :consumer, :processedAt)
            on conflict (event_id, consumer) do nothing
            """, nativeQuery = true)
    int claim(@Param("eventId") UUID eventId, @Param("consumer") String consumer,
              @Param("processedAt") Instant processedAt);

    @Query(value = "select count(*) from processed_events where event_id = :eventId and consumer = :consumer",
            nativeQuery = true)
    long countProcessed(@Param("eventId") UUID eventId, @Param("consumer") String consumer);
}
