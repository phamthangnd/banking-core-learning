package com.example.bankcore.ledger.domain;

import com.example.bankcore.common.money.Money;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistence port for the ledger. Append-only: there is no update and no delete. */
public interface LedgerRepository {

    /**
     * Appends a balanced group of entries.
     *
     * @throws UnbalancedLedgerException if the group does not balance
     */
    List<LedgerEntry> append(List<LedgerEntry> entries);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    List<LedgerEntry> findByAccountId(UUID accountId);

    /** Sum of every debit in the ledger, per currency. */
    Map<String, Money> totalDebits();

    /** Sum of every credit in the ledger, per currency. */
    Map<String, Money> totalCredits();

    /** Balance of an account derived from its entries, for reconciliation against the account row. */
    Money balanceOfAccount(UUID accountId, String currency);
}
