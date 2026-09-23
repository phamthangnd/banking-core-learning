package com.example.bankcore.account.domain;

import com.example.bankcore.common.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A bank account.
 *
 * <p>Banking rules this type enforces (CLAUDE.md section 3):
 * <ul>
 *   <li>the balance is a {@link Money}: {@link BigDecimal} with an explicit currency, never a
 *       floating-point number;</li>
 *   <li>the balance may not fall below the granted overdraft, and an account type that does not
 *       allow an overdraft may not have one at all;</li>
 *   <li>money only moves on an {@code ACTIVE} account;</li>
 *   <li>an account is closed, never deleted, and only with a zero balance.</li>
 * </ul>
 *
 * <p>Phase 04 models the account and its lifecycle; Phase 05 moves money across accounts and
 * Phase 06 makes those movements concurrent, idempotent and double-entry safe. {@link #credit}
 * and {@link #debit} exist here because the balance rules belong to the account itself, not to
 * whatever service happens to call them.
 *
 * @param id             identity
 * @param accountNumber  human-facing number, unique across the bank
 * @param customerId     owner; one customer may own many accounts
 * @param accountType    product type, which decides whether an overdraft is possible at all
 * @param balance        current balance with its currency
 * @param overdraftLimit how far below zero the balance may go, as a non-negative amount
 * @param status         lifecycle state
 * @param openedAt       when the account was opened
 * @param activatedAt    when it first became active, or {@code null}
 * @param closedAt       when it was closed, or {@code null}
 * @param createdAt      row creation timestamp (UTC)
 * @param updatedAt      timestamp of the last change (UTC)
 */
public record Account(
        UUID id,
        String accountNumber,
        UUID customerId,
        AccountType accountType,
        Money balance,
        Money overdraftLimit,
        AccountStatus status,
        Instant openedAt,
        Instant activatedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public Account {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(accountType, "accountType must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
        Objects.requireNonNull(overdraftLimit, "overdraftLimit must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (accountNumber.isBlank()) {
            throw new IllegalArgumentException("accountNumber must not be blank");
        }
        if (!balance.currency().equals(overdraftLimit.currency())) {
            throw new IllegalArgumentException("balance and overdraftLimit must share a currency");
        }
        if (overdraftLimit.isNegative()) {
            throw new IllegalArgumentException("overdraftLimit must not be negative; it is a distance below zero");
        }
        if (!accountType.isOverdraftAllowed() && overdraftLimit.isPositive()) {
            throw new IllegalArgumentException(
                    "account type %s must never go negative".formatted(accountType));
        }
        if (balance.compareTo(overdraftLimit.negate()) < 0) {
            throw new IllegalArgumentException("balance must not fall below the overdraft limit");
        }
    }

    /** Opens an account. It starts {@code PENDING} and holds nothing until it is activated. */
    public static Account open(UUID id, String accountNumber, UUID customerId, AccountType accountType,
                               String currency, Money overdraftLimit, Instant now) {
        return new Account(id, accountNumber, customerId, accountType,
                Money.zero(currency), overdraftLimit, AccountStatus.PENDING,
                now, null, null, now, now);
    }

    public String currency() {
        return balance.currency();
    }

    /** Whether money may move on this account right now. */
    public boolean canTransact() {
        return status.canTransact();
    }

    /**
     * Amount that may still be withdrawn: the balance plus any granted overdraft.
     *
     * <p>Distinct from the balance on purpose — "how much is there" and "how much can be taken"
     * are different questions, and confusing them is how overdrafts get granted by accident.
     */
    public Money availableBalance() {
        return balance.add(overdraftLimit);
    }

    /** Returns a copy with the amount added. Used by Phase 05; the rules live here. */
    public Account credit(Money amount, Instant now) {
        requireTransactable();
        requirePositive(amount);
        requireSameCurrency(amount);

        return withBalance(balance.add(amount), now);
    }

    /**
     * Returns a copy with the amount removed.
     *
     * @throws AccountExceptions.AccountRuleViolationException if it would breach the overdraft
     */
    public Account debit(Money amount, Instant now) {
        requireTransactable();
        requirePositive(amount);
        requireSameCurrency(amount);

        Money next = balance.subtract(amount);
        if (next.compareTo(overdraftLimit.negate()) < 0) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "Insufficient available balance on account " + accountNumber);
        }

        return withBalance(next, now);
    }

    /**
     * Activates the account.
     *
     * <p>The caller is responsible for checking that the owner's KYC is verified: the account
     * aggregate cannot see the customer, and reaching across module boundaries to find out is
     * exactly what the boundaries exist to prevent.
     */
    public Account activate(Instant now) {
        requireTransition(AccountStatus.ACTIVE);

        return new Account(id, accountNumber, customerId, accountType, balance, overdraftLimit,
                AccountStatus.ACTIVE, openedAt, activatedAt == null ? now : activatedAt, closedAt,
                createdAt, now);
    }

    public Account freeze(Instant now) {
        requireTransition(AccountStatus.FROZEN);
        return withStatus(AccountStatus.FROZEN, now);
    }

    public Account unfreeze(Instant now) {
        requireTransition(AccountStatus.ACTIVE);
        return withStatus(AccountStatus.ACTIVE, now);
    }

    /**
     * Closes the account.
     *
     * <p>Only with a zero balance: closing an account that still holds money would strand it, and
     * closing one that is overdrawn would write off a debt. Closing an already closed account is
     * a no-op, which makes the operation idempotent.
     */
    public Account close(Instant now) {
        if (status == AccountStatus.CLOSED) {
            return this;
        }

        requireTransition(AccountStatus.CLOSED);

        if (!balance.isZero()) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "An account can only be closed with a zero balance");
        }

        return new Account(id, accountNumber, customerId, accountType, balance, overdraftLimit,
                AccountStatus.CLOSED, openedAt, activatedAt, now, createdAt, now);
    }

    /** Grants or changes an overdraft. Rejected outright for a type that may not go negative. */
    public Account withOverdraftLimit(Money newLimit, Instant now) {
        if (!accountType.isOverdraftAllowed() && newLimit.isPositive()) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "Account type %s cannot have an overdraft".formatted(accountType));
        }
        if (newLimit.isNegative()) {
            throw new AccountExceptions.AccountRuleViolationException("Overdraft limit must not be negative");
        }
        if (balance.compareTo(newLimit.negate()) < 0) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "The new overdraft limit is already breached by the current balance");
        }

        return new Account(id, accountNumber, customerId, accountType, balance, newLimit,
                status, openedAt, activatedAt, closedAt, createdAt, now);
    }

    private Account withBalance(Money newBalance, Instant now) {
        return new Account(id, accountNumber, customerId, accountType, newBalance, overdraftLimit,
                status, openedAt, activatedAt, closedAt, createdAt, now);
    }

    private Account withStatus(AccountStatus newStatus, Instant now) {
        return new Account(id, accountNumber, customerId, accountType, balance, overdraftLimit,
                newStatus, openedAt, activatedAt, closedAt, createdAt, now);
    }

    private void requireTransition(AccountStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new AccountExceptions.IllegalAccountTransitionException(status, target);
        }
    }

    private void requireTransactable() {
        if (!canTransact()) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "Account %s is %s and cannot transact".formatted(accountNumber, status));
        }
    }

    private void requireSameCurrency(Money amount) {
        if (!amount.currency().equals(currency())) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "Amount is in %s but the account is in %s".formatted(amount.currency(), currency()));
        }
    }

    private static void requirePositive(Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (!amount.isPositive()) {
            throw new AccountExceptions.AccountRuleViolationException("Amount must be positive");
        }
    }
}
