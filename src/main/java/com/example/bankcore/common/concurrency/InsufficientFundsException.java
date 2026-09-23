package com.example.bankcore.common.concurrency;

import com.example.bankcore.common.money.Money;

/**
 * Thrown when a debit would push a balance below zero.
 *
 * <p>A failed financial operation must be visible to the caller; it is never swallowed and
 * never turned into a silent no-op (CLAUDE.md section 3).
 */
public class InsufficientFundsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String accountId, Money balance, Money requested) {
        super("insufficient funds on account %s: balance %s %s, requested %s %s".formatted(
                accountId, balance.amount(), balance.currency(), requested.amount(), requested.currency()));
    }
}
