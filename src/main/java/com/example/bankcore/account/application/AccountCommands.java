package com.example.bankcore.account.application;

import com.example.bankcore.account.domain.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

/** Input models of {@link AccountService}. */
public final class AccountCommands {

    private AccountCommands() {
    }

    /**
     * @param customerId     owner; must exist and must not be closed
     * @param accountType    product type
     * @param currency       ISO-4217 code, or {@code null} for the configured default
     * @param overdraftLimit requested overdraft, or {@code null} for none
     */
    public record OpenAccount(UUID customerId, AccountType accountType, String currency,
                              BigDecimal overdraftLimit) {
    }

    public record SetOverdraftLimit(BigDecimal overdraftLimit) {
    }
}
