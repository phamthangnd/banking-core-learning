package com.example.bankcore.account.web.dto;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountStatus;
import com.example.bankcore.account.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for an account.
 *
 * <p>Amounts are rendered as JSON numbers produced from {@link BigDecimal}, so the exact decimal
 * value survives serialisation. {@code currency} is a sibling field rather than being glued into
 * a string, so a client never has to parse an amount out of text.
 *
 * <p>{@code availableBalance} is included alongside {@code balance} because they answer different
 * questions — what is there, versus what may be taken — and a client that computes it itself will
 * eventually compute it wrongly.
 */
public record AccountResponse(
        UUID id,
        String accountNumber,
        UUID customerId,
        AccountType accountType,
        String currency,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal overdraftLimit,
        AccountStatus status,
        boolean canTransact,
        Instant openedAt,
        Instant activatedAt,
        Instant closedAt,
        Instant updatedAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id(),
                account.accountNumber(),
                account.customerId(),
                account.accountType(),
                account.currency(),
                account.balance().amount(),
                account.availableBalance().amount(),
                account.overdraftLimit().amount(),
                account.status(),
                account.canTransact(),
                account.openedAt(),
                account.activatedAt(),
                account.closedAt(),
                account.updatedAt());
    }
}
