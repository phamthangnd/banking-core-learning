package com.example.bankcore.common.events.domain;

import java.util.List;
import java.util.UUID;

/** Persistence port for the outbox. */
public interface OutboxRepository {

    OutboxEvent append(OutboxEvent event);

    OutboxEvent save(OutboxEvent event);

    /** Oldest pending events first, so ordering is preserved as far as the topic allows. */
    List<OutboxEvent> findPending(int limit);

    long countByStatus(OutboxStatus status);

    /** Records that a consumer handled an event. False if it had already been recorded. */
    boolean markProcessed(UUID eventId, String consumer);

    boolean isProcessed(UUID eventId, String consumer);
}
