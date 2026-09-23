package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.money.Money;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Read-only aggregations over transaction records.
 *
 * <p>Java note: every method here is a stream pipeline — a source, zero or more lazy
 * intermediate operations, and exactly one terminal operation. Nothing is computed until the
 * terminal operation runs, and no input collection is ever modified.
 */
public final class TransactionAnalytics {

    private TransactionAnalytics() {
    }

    /**
     * Total posted amount per currency.
     *
     * <p>Currencies are never mixed: each bucket is summed independently. The result is sorted
     * by currency code so output is deterministic (useful in tests and reports).
     */
    public static Map<String, Money> postedTotalsByCurrency(Collection<TransactionRecord> records) {
        Objects.requireNonNull(records, "records must not be null");
        return records.stream()
                .filter(TransactionRecord::isPosted)
                .collect(Collectors.toMap(
                        TransactionRecord::currency,
                        TransactionRecord::amount,
                        Money::add,
                        TreeMap::new));
    }

    /**
     * Net position of an account: posted credits minus posted debits, in one currency.
     *
     * <p>This is the arithmetic a ledger balance is built on. Phase 06 makes it durable,
     * concurrent and double-entry safe; here it only has to be correct.
     */
    public static Money netPosition(Collection<TransactionRecord> records, String accountId, String currency) {
        Objects.requireNonNull(records, "records must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");

        return records.stream()
                .filter(TransactionRecord::isPosted)
                .filter(record -> record.accountId().equals(accountId))
                .filter(record -> record.currency().equals(currency))
                .map(record -> record.direction() == TransactionDirection.CREDIT
                        ? record.amount()
                        : record.amount().negate())
                .reduce(Money.zero(currency), Money::add);
    }

    /**
     * Largest posted transaction in a currency, if there is one.
     *
     * <p>Java note: {@link Optional} models "there may be no answer" in a return type. It is
     * the right tool here and the wrong tool for fields or method parameters.
     */
    public static Optional<TransactionRecord> largestPosted(Collection<TransactionRecord> records, String currency) {
        Objects.requireNonNull(records, "records must not be null");
        return records.stream()
                .filter(TransactionRecord::isPosted)
                .filter(record -> record.currency().equals(currency))
                .max(Comparator.comparing(TransactionRecord::amount));
    }

    /**
     * Records whose business timestamp falls in {@code [from, to)} — inclusive start, exclusive
     * end, the convention that makes adjacent periods tile without overlapping.
     */
    public static List<TransactionRecord> occurredBetween(
            Collection<TransactionRecord> records, Instant from, Instant to) {
        Objects.requireNonNull(records, "records must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }

        return records.stream()
                .filter(record -> !record.occurredAt().isBefore(from))
                .filter(record -> record.occurredAt().isBefore(to))
                .sorted(Comparator.comparing(TransactionRecord::occurredAt))
                .toList();
    }

    /** The {@code limit} most recent records, newest first. */
    public static List<TransactionRecord> mostRecent(Collection<TransactionRecord> records, int limit) {
        Objects.requireNonNull(records, "records must not be null");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }

        return records.stream()
                .sorted(Comparator.comparing(TransactionRecord::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    /** Counts records per status, including statuses with no records. */
    public static Map<TransactionStatus, Long> countByStatus(Collection<TransactionRecord> records) {
        Objects.requireNonNull(records, "records must not be null");

        Map<TransactionStatus, Long> counted = records.stream()
                .collect(Collectors.groupingBy(TransactionRecord::status, Collectors.counting()));

        Map<TransactionStatus, Long> result = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus status : TransactionStatus.values()) {
            result.put(status, counted.getOrDefault(status, 0L));
        }
        return Collections.unmodifiableMap(result);
    }
}
