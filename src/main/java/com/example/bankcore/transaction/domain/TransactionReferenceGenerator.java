package com.example.bankcore.transaction.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Builds the customer-facing transaction reference, for example {@code TXN-20260923-000001234}.
 *
 * <p>The date makes a reference easy to place in a conversation; the sequence value, taken from
 * the database, makes it unique. Unlike an account number this one deliberately carries a fact,
 * because a reference identifies an event that already happened and whose date cannot change.
 */
public final class TransactionReferenceGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    private TransactionReferenceGenerator() {
    }

    public static String generate(Instant occurredAt, long sequence) {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        return "TXN-%s-%09d".formatted(DATE.format(occurredAt), sequence);
    }
}
