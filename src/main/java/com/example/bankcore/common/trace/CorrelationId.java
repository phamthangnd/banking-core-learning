package com.example.bankcore.common.trace;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * Access to the correlation (trace) id of the request being handled.
 *
 * <p>The id is stored in SLF4J's {@link MDC}, a thread-local map that the logging pattern can
 * read, so every log line produced while handling a request carries the same id. The same id is
 * returned in the API response and in the {@code X-Trace-Id} header, which is what makes a
 * customer-reported problem findable in the logs.
 *
 * <p>Note the limitation: MDC is bound to the current thread and does not follow work handed to
 * another thread. Phase 09 and Phase 11 revisit propagation for async processing.
 */
public final class CorrelationId {

    /** Key used both in the MDC and in the logging pattern. */
    public static final String MDC_KEY = "traceId";

    /** Inbound/outbound HTTP header carrying the id. */
    public static final String HEADER = "X-Trace-Id";

    private CorrelationId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static void set(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /** Current id, or empty when running outside a request (for example in a unit test). */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }

    /** Current id, or {@code null} when there is none — convenient for response envelopes. */
    public static String currentOrNull() {
        return MDC.get(MDC_KEY);
    }
}
