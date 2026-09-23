package com.example.bankcore.common.observability;

import com.example.bankcore.common.events.domain.OutboxRepository;
import com.example.bankcore.common.events.domain.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Gauges for the event outbox.
 *
 * <p>These are the two numbers that say whether asynchronous processing is healthy:
 *
 * <ul>
 *   <li><b>pending</b> — a queue that keeps growing means the publisher is not keeping up or the
 *       broker is unreachable. Rising is the alert, not any particular value.</li>
 *   <li><b>failed</b> — rows parked after exhausting their retries. Anything above zero is a
 *       person's problem, because nothing will retry them.</li>
 * </ul>
 *
 * <p>Gauges rather than counters: both are current states, not accumulating totals.
 */
@Component
public class OutboxMetrics {

    private final MeterRegistry registry;
    private final OutboxRepository outbox;

    public OutboxMetrics(MeterRegistry registry, OutboxRepository outbox) {
        this.registry = registry;
        this.outbox = outbox;
    }

    @PostConstruct
    void register() {
        Gauge.builder("bankcore.outbox.pending", outbox, repository -> repository.countByStatus(OutboxStatus.PENDING))
                .description("Events waiting to be published")
                .register(registry);

        Gauge.builder("bankcore.outbox.failed", outbox, repository -> repository.countByStatus(OutboxStatus.FAILED))
                .description("Events that gave up after exhausting their retries")
                .register(registry);
    }
}
