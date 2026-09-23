package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable record of one money movement.
 *
 * <p>Written once, in the same database transaction as the balance change it describes. Nothing
 * about it is ever edited afterwards: a mistake is corrected by posting a compensating
 * transaction, which is why a bank statement can be trusted and an "UPDATE the amount" cannot
 * (CLAUDE.md section 3). The database enforces this with a trigger as well.
 *
 * <p>{@code sourceBalanceAfter} and {@code targetBalanceAfter} are captured at posting time.
 * A statement has to show the balance as it was, not as it is now.
 *
 * @param id                  identity
 * @param reference           customer-facing reference
 * @param type                what kind of movement this is
 * @param status              lifecycle state
 * @param amount              always positive; the type says which way the money went
 * @param sourceAccountId     account debited, or {@code null} for a deposit
 * @param targetAccountId     account credited, or {@code null} for a withdrawal
 * @param sourceBalanceAfter  source balance after posting, or {@code null}
 * @param targetBalanceAfter  target balance after posting, or {@code null}
 * @param description         free text from the caller; never sensitive data
 * @param failureReason       why a FAILED transaction failed, or {@code null}
 * @param occurredAt          business time of the movement (UTC)
 * @param postedAt            when it was posted, or {@code null}
 * @param createdAt           row creation timestamp (UTC)
 */
public record Transaction(
        UUID id,
        String reference,
        TransactionType type,
        TransactionStatus status,
        Money amount,
        UUID sourceAccountId,
        UUID targetAccountId,
        Money sourceBalanceAfter,
        Money targetBalanceAfter,
        String description,
        String failureReason,
        Instant occurredAt,
        Instant postedAt,
        Instant createdAt
) {

    public Transaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive; the type carries the direction");
        }
        if (type.hasSource() != (sourceAccountId != null)) {
            throw new IllegalArgumentException(type + " has the wrong source account");
        }
        if (type.hasTarget() != (targetAccountId != null)) {
            throw new IllegalArgumentException(type + " has the wrong target account");
        }
        if (type == TransactionType.TRANSFER && sourceAccountId.equals(targetAccountId)) {
            throw new IllegalArgumentException("a transfer needs two different accounts");
        }
    }

    /** A successfully posted movement. */
    public static Transaction posted(UUID id, String reference, TransactionType type, Money amount,
                                     UUID sourceAccountId, UUID targetAccountId,
                                     Money sourceBalanceAfter, Money targetBalanceAfter,
                                     String description, Instant now) {
        return new Transaction(id, reference, type, TransactionStatus.POSTED, amount,
                sourceAccountId, targetAccountId, sourceBalanceAfter, targetBalanceAfter,
                description, null, now, now, now);
    }

    /**
     * A rejected movement.
     *
     * <p>Failures are recorded, not swallowed: "why did my transfer not arrive" must be
     * answerable, and a rejected attempt is part of an account's history.
     */
    public static Transaction failed(UUID id, String reference, TransactionType type, Money amount,
                                     UUID sourceAccountId, UUID targetAccountId,
                                     String description, String failureReason, Instant now) {
        return new Transaction(id, reference, type, TransactionStatus.FAILED, amount,
                sourceAccountId, targetAccountId, null, null,
                description, failureReason, now, null, now);
    }

    public String currency() {
        return amount.currency();
    }

    public boolean isPosted() {
        return status == TransactionStatus.POSTED;
    }

    /** Whether this movement touches the given account, on either side. */
    public boolean touches(UUID accountId) {
        return accountId.equals(sourceAccountId) || accountId.equals(targetAccountId);
    }

    /**
     * Returns a copy in the target state.
     *
     * <p>The only mutation a transaction ever undergoes, and only along the lifecycle the status
     * enum permits.
     */
    public Transaction withStatus(TransactionStatus target, Instant now) {
        return new Transaction(id, reference, type, status.transitionTo(target), amount,
                sourceAccountId, targetAccountId, sourceBalanceAfter, targetBalanceAfter,
                description, failureReason, occurredAt,
                target == TransactionStatus.POSTED ? now : postedAt, createdAt);
    }
}
