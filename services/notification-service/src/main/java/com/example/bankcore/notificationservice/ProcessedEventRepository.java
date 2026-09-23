package com.example.bankcore.notificationservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Claims an event, or does nothing if it was already claimed.
     *
     * <p>The same mechanism as in the monolith, for the same reason: at-least-once delivery
     * guarantees a second copy eventually arrives, and only the database can decide which
     * delivery does the work.
     */
    @Modifying
    @Query(value = """
            insert into processed_events (event_id, processed_at)
            values (:eventId, :processedAt) on conflict (event_id) do nothing
            """, nativeQuery = true)
    int claim(@Param("eventId") UUID eventId, @Param("processedAt") Instant processedAt);
}
