package com.example.bankcore.ledger.domain;

import com.example.bankcore.common.money.Money;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The ledger invariant, in one place.
 *
 * <p>For every transaction, and therefore for the ledger as a whole, the debits must equal the
 * credits — per currency, because amounts in different currencies are never comparable. A group
 * that does not balance is not a rounding problem, it is money appearing or disappearing, so it
 * fails loudly rather than being corrected.
 */
public final class LedgerEntries {

    private LedgerEntries() {
    }

    /**
     * @throws UnbalancedLedgerException if debits and credits differ in any currency
     */
    public static void requireBalanced(List<LedgerEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");

        if (entries.isEmpty()) {
            throw new UnbalancedLedgerException("a transaction must have ledger entries");
        }
        if (entries.size() < 2) {
            throw new UnbalancedLedgerException("double-entry needs at least two entries");
        }

        Map<String, Money> debits = totalsByCurrency(entries, true);
        Map<String, Money> credits = totalsByCurrency(entries, false);

        if (!debits.keySet().equals(credits.keySet())) {
            throw new UnbalancedLedgerException(
                    "debits and credits cover different currencies: %s vs %s"
                            .formatted(debits.keySet(), credits.keySet()));
        }

        for (Map.Entry<String, Money> debit : debits.entrySet()) {
            Money credit = credits.get(debit.getKey());
            if (debit.getValue().compareTo(credit) != 0) {
                throw new UnbalancedLedgerException(
                        "ledger does not balance in %s: debits %s, credits %s".formatted(
                                debit.getKey(), debit.getValue().amount(), credit.amount()));
            }
        }
    }

    /** Sum of debits (or credits) per currency. */
    public static Map<String, Money> totalsByCurrency(List<LedgerEntry> entries, boolean debits) {
        Map<String, Money> totals = new TreeMap<>();

        for (LedgerEntry entry : entries) {
            if (entry.isDebit() != debits) {
                continue;
            }
            totals.merge(entry.currency(), entry.amount(), Money::add);
        }

        return totals;
    }
}
