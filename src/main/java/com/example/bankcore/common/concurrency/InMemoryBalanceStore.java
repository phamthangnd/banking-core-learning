package com.example.bankcore.common.concurrency;

import com.example.bankcore.common.money.Money;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory balances — a Phase 00 concurrency exercise, not the production ledger.
 *
 * <p>The real ledger arrives in Phase 06 with database transactions, optimistic locking and
 * idempotency keys. What this class teaches is the underlying hazard: {@code read, modify,
 * write} is three steps, and two threads interleaving those steps lose one of the updates.
 *
 * <p>The fix used here is {@link ConcurrentHashMap#compute}, which applies the whole
 * read-modify-write as one atomic operation on the key. Note what that does <em>not</em> give
 * you: atomicity across two different keys. A transfer touches two accounts, so it needs a
 * real transaction boundary — hence Phase 06.
 *
 * <p>The remapping function must stay short and side-effect free: it runs while the map holds
 * a lock on that bin, and touching the same map inside it can deadlock.
 */
public class InMemoryBalanceStore {

    private final Map<String, Money> balances = new ConcurrentHashMap<>();

    /** Current balance, or empty if the account has never been touched. */
    public Optional<Money> balanceOf(String accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        return Optional.ofNullable(balances.get(accountId));
    }

    /** Adds money to an account and returns the new balance. */
    public Money credit(String accountId, Money amount) {
        requirePositive(amount);
        Objects.requireNonNull(accountId, "accountId must not be null");

        return balances.compute(accountId, (key, current) ->
                current == null ? amount : current.add(amount));
    }

    /**
     * Removes money from an account and returns the new balance.
     *
     * @throws InsufficientFundsException if the account does not hold enough money
     */
    public Money debit(String accountId, Money amount) {
        requirePositive(amount);
        Objects.requireNonNull(accountId, "accountId must not be null");

        return balances.compute(accountId, (key, current) -> {
            Money balance = current == null ? Money.zero(amount.currency()) : current;
            Money next = balance.subtract(amount);
            if (next.isNegative()) {
                throw new InsufficientFundsException(accountId, balance, amount);
            }
            return next;
        });
    }

    private static void requirePositive(Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
