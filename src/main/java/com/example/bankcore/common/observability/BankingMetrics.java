package com.example.bankcore.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * The handful of metrics that answer "is the bank working?".
 *
 * <p>Deliberately few. A dashboard with four hundred graphs is a dashboard nobody reads during an
 * incident, and every metric costs cardinality — the number of distinct label combinations, which
 * is what makes a metrics backend fall over.
 *
 * <p>Tags are bounded on purpose: {@code type}, {@code currency} and {@code outcome} have a handful
 * of values each. An account id or a customer id as a tag would create one time series per
 * account, which is how a monitoring system is taken down by the thing it monitors.
 *
 * <p><b>No amounts.</b> Metrics are scraped by infrastructure with different access rules than the
 * API; the count of transfers is operational, the value of them is financial data
 * (CLAUDE.md section 4).
 */
@Component
public class BankingMetrics {

    private final MeterRegistry registry;

    public BankingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Counts a money movement by type and outcome — the rate and the error rate in one metric. */
    public void transactionPosted(String type, String currency) {
        Counter.builder("bankcore.transactions")
                .description("Money movements attempted")
                .tag("type", type)
                .tag("currency", currency)
                .tag("outcome", "posted")
                .register(registry)
                .increment();
    }

    public void transactionRejected(String type, String reason) {
        Counter.builder("bankcore.transactions")
                .description("Money movements attempted")
                .tag("type", type)
                // The reason is a bounded label, not the free-text message: an unbounded tag
                // would create a new time series for every distinct failure string.
                .tag("currency", "n/a")
                .tag("outcome", "rejected")
                .register(registry)
                .increment();

        Counter.builder("bankcore.transactions.rejections")
                .description("Rejected movements by reason category")
                .tag("reason", reasonCategory(reason))
                .register(registry)
                .increment();
    }

    /** Authentication outcomes: the shape of an attack shows up here before anywhere else. */
    public void loginAttempt(String outcome) {
        Counter.builder("bankcore.logins")
                .description("Login attempts by outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordTransactionDuration(String type, long nanos) {
        Timer.builder("bankcore.transaction.duration")
                .description("Time to post a money movement")
                .tag("type", type)
                // Percentiles, not an average: an average hides the slow tail that users notice.
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Folds a free-text reason into one of a few buckets.
     *
     * <p>Keeps the label's cardinality bounded while still distinguishing "customers keep running
     * out of money" from "somebody is transferring to frozen accounts".
     */
    private static String reasonCategory(String reason) {
        if (reason == null) {
            return "other";
        }
        String lower = reason.toLowerCase(java.util.Locale.ROOT);

        if (lower.contains("insufficient")) {
            return "insufficient_funds";
        }
        if (lower.contains("cannot transact") || lower.contains("frozen") || lower.contains("closed")) {
            return "account_state";
        }
        if (lower.contains("currency")) {
            return "currency";
        }
        return "other";
    }
}
