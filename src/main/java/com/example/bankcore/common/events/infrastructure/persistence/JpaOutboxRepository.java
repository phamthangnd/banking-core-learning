package com.example.bankcore.common.events.infrastructure.persistence;

import com.example.bankcore.common.events.domain.OutboxEvent;
import com.example.bankcore.common.events.domain.OutboxRepository;
import com.example.bankcore.common.events.domain.OutboxStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Adapter for the outbox and the consumer deduplication table. */
@Repository
@Transactional(readOnly = true)
public class JpaOutboxRepository implements OutboxRepository {

    private final OutboxEventJpaRepository events;
    private final ProcessedEventJpaRepository processed;
    private final Clock clock;

    public JpaOutboxRepository(OutboxEventJpaRepository events,
                               ProcessedEventJpaRepository processed, Clock clock) {
        this.events = events;
        this.processed = processed;
        this.clock = clock;
    }

    /**
     * Writes an event.
     *
     * <p>Deliberately joins the caller's transaction rather than starting its own: the whole
     * point of an outbox is that the event and the business change commit together.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent append(OutboxEvent event) {
        return events.save(OutboxEventEntity.fromDomain(event)).toDomain();
    }

    @Override
    @Transactional
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventEntity entity = events.findById(event.id())
                .map(existing -> {
                    existing.applyState(event);
                    return existing;
                })
                .orElseGet(() -> OutboxEventEntity.fromDomain(event));

        return events.save(entity).toDomain();
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return events.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.ofSize(limit))
                .stream().map(OutboxEventEntity::toDomain).toList();
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return events.countByStatus(status);
    }

    @Override
    @Transactional
    public boolean markProcessed(UUID eventId, String consumer) {
        return processed.claim(eventId, consumer, clock.instant()) == 1;
    }

    @Override
    public boolean isProcessed(UUID eventId, String consumer) {
        return processed.countProcessed(eventId, consumer) > 0;
    }
}
