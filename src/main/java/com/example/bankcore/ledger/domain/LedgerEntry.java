package com.example.bankcore.ledger.domain;

import com.example.bankcore.common.money.Money;
import com.example.bankcore.transaction.domain.TransactionDirection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One side of a money movement.
 *
 * <p>Entries are written in balanced groups and never changed afterwards — a correction is a new
 * transaction with its own entries. The amount is always positive; {@link #direction} says which
 * way it went, which keeps "sum the debits" and "sum the credits" two separate, checkable
 * questions.
 *
 * @param id            identity
 * @param transactionId the movement this entry belongs to
 * @param entryIndex    position within that transaction
 * @param accountId     customer account, or {@code null} for the bank's own leg
 * @param systemAccount the bank's position, or {@code null} for a customer leg
 * @param direction     debit or credit
 * @param amount        always positive
 * @param balanceAfter  customer account balance after this entry, or {@code null} for a bank leg
 * @param createdAt     when the entry was written (UTC)
 */
public record LedgerEntry(
        UUID id,
        UUID transactionId,
        int entryIndex,
        UUID accountId,
        SystemAccount systemAccount,
        TransactionDirection direction,
        Money amount,
        Money balanceAfter,
        Instant createdAt
) {

    public LedgerEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive; direction carries the sign");
        }
        if ((accountId == null) == (systemAccount == null)) {
            throw new IllegalArgumentException("an entry belongs to exactly one of an account or a system account");
        }
        if ((accountId == null) != (balanceAfter == null)) {
            throw new IllegalArgumentException("balanceAfter belongs to a customer account leg only");
        }
    }

    public static LedgerEntry forAccount(UUID id, UUID transactionId, int entryIndex, UUID accountId,
                                         TransactionDirection direction, Money amount,
                                         Money balanceAfter, Instant now) {
        return new LedgerEntry(id, transactionId, entryIndex, accountId, null,
                direction, amount, balanceAfter, now);
    }

    public static LedgerEntry forSystem(UUID id, UUID transactionId, int entryIndex,
                                        SystemAccount systemAccount, TransactionDirection direction,
                                        Money amount, Instant now) {
        return new LedgerEntry(id, transactionId, entryIndex, null, systemAccount,
                direction, amount, null, now);
    }

    public boolean isDebit() {
        return direction == TransactionDirection.DEBIT;
    }

    public String currency() {
        return amount.currency();
    }
}
