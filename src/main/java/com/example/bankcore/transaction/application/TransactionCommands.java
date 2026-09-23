package com.example.bankcore.transaction.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Input models of {@link TransactionService}. */
public final class TransactionCommands {

    private TransactionCommands() {
    }

    public record Deposit(UUID accountId, BigDecimal amount, String currency, String description) {
    }

    public record Withdraw(UUID accountId, BigDecimal amount, String currency, String description) {
    }

    public record Transfer(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount,
                           String currency, String description) {
    }
}
