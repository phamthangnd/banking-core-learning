package com.example.bankcore.transaction.web.dto;

import com.example.bankcore.common.money.Money;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionStatus;
import com.example.bankcore.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response body for a transaction. */
public record TransactionResponse(
        UUID id,
        String reference,
        TransactionType type,
        TransactionStatus status,
        String currency,
        BigDecimal amount,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal sourceBalanceAfter,
        BigDecimal targetBalanceAfter,
        String description,
        String failureReason,
        Instant occurredAt,
        Instant postedAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.reference(),
                transaction.type(),
                transaction.status(),
                transaction.currency(),
                transaction.amount().amount(),
                transaction.sourceAccountId(),
                transaction.targetAccountId(),
                amountOf(transaction.sourceBalanceAfter()),
                amountOf(transaction.targetBalanceAfter()),
                transaction.description(),
                transaction.failureReason(),
                transaction.occurredAt(),
                transaction.postedAt());
    }

    private static BigDecimal amountOf(Money money) {
        return money == null ? null : money.amount();
    }
}
