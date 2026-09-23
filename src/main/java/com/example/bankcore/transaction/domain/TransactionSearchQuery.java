package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.pagination.PageRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter criteria for transaction history.
 *
 * @param accountId matches the transaction on either side (money in or out)
 * @param type      kind of movement
 * @param status    lifecycle state
 * @param from      inclusive lower bound on {@code occurredAt}
 * @param to        exclusive upper bound on {@code occurredAt}
 * @param page      page and sort specification
 */
public record TransactionSearchQuery(
        UUID accountId, TransactionType type, TransactionStatus status,
        Instant from, Instant to, PageRequest page
) {

    public TransactionSearchQuery {
        Objects.requireNonNull(page, "page must not be null");
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
    }

    public static TransactionSearchQuery forAccount(UUID accountId, PageRequest page) {
        return new TransactionSearchQuery(accountId, null, null, null, null, page);
    }

    public Optional<UUID> accountIdOrEmpty() {
        return Optional.ofNullable(accountId);
    }

    public Optional<TransactionType> typeOrEmpty() {
        return Optional.ofNullable(type);
    }

    public Optional<TransactionStatus> statusOrEmpty() {
        return Optional.ofNullable(status);
    }

    public Optional<Instant> fromOrEmpty() {
        return Optional.ofNullable(from);
    }

    public Optional<Instant> toOrEmpty() {
        return Optional.ofNullable(to);
    }
}
