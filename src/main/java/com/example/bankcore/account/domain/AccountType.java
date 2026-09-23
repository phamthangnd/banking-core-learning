package com.example.bankcore.account.domain;

import java.math.BigDecimal;

/**
 * Kinds of account the bank offers.
 *
 * <p>The type decides the rules, which is why it is an enum with behaviour rather than a label.
 * The most important one here is whether the account may go below zero at all: an overdraft is a
 * credit product, and granting one by accident is how a bank lends money it never agreed to lend
 * (CLAUDE.md section 3 — no negative balance unless the account type permits it).
 */
public enum AccountType {

    /** Everyday account. May be granted an overdraft. */
    CHECKING(true),

    /** Interest-bearing account. Never goes negative. */
    SAVINGS(false),

    /** Fixed-term deposit. Never goes negative, and withdrawals are restricted (Phase 05). */
    TERM_DEPOSIT(false);

    private final boolean overdraftAllowed;

    AccountType(boolean overdraftAllowed) {
        this.overdraftAllowed = overdraftAllowed;
    }

    public boolean isOverdraftAllowed() {
        return overdraftAllowed;
    }

    /** The largest overdraft this type may be granted. Zero means "must never go negative". */
    public BigDecimal maximumOverdraftLimit(BigDecimal configuredMaximum) {
        return overdraftAllowed ? configuredMaximum : BigDecimal.ZERO;
    }
}
