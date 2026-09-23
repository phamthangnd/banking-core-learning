package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable in-memory view of a transaction.
 *
 * <p>Phase 00 has no database: this record exists so that domain rules (status transitions,
 * aggregations, signed positions) can be written and tested before persistence arrives in
 * Phase 02 and the real transaction module in Phase 05.
 *
 * <p>Java note: {@link Instant} is a point on the UTC timeline. Financial events are recorded in
 * UTC and only formatted into a local zone at the edge; the legacy {@code java.util.Date}
 * carries no zone information at all and must not be used.
 *
 * @param id           identity of the transaction
 * @param accountId    account the entry belongs to
 * @param amount       unsigned amount; {@code direction} carries the sign
 * @param direction    debit or credit side
 * @param status       lifecycle state
 * @param occurredAt   business timestamp in UTC
 * @param description  human-readable label, never sensitive data
 */
public record TransactionRecord(
        UUID id,
        String accountId,
        Money amount,
        TransactionDirection direction,
        TransactionStatus status,
        Instant occurredAt,
        String description
) {

    public TransactionRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");

        if (amount.isNegative()) {
            throw new IllegalArgumentException("amount must not be negative; use direction to express the side");
        }
    }

    public String currency() {
        return amount.currency();
    }

    public boolean isPosted() {
        return status == TransactionStatus.POSTED;
    }

    /**
     * Returns a copy of this record in the target state.
     *
     * <p>The original instance is never mutated: state changes create a new immutable record,
     * which is exactly how a financial history stays auditable.
     */
    public TransactionRecord withStatus(TransactionStatus target) {
        return new TransactionRecord(id, accountId, amount, direction, status.transitionTo(target),
                occurredAt, description);
    }
}
