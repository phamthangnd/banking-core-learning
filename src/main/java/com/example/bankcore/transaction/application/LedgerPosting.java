package com.example.bankcore.transaction.application;

import com.example.bankcore.common.money.Money;
import com.example.bankcore.ledger.domain.LedgerEntry;
import com.example.bankcore.ledger.domain.LedgerRepository;
import com.example.bankcore.ledger.domain.SystemAccount;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionDirection;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Turns a posted transaction into its balanced pair of ledger entries.
 *
 * <p>Every movement has exactly two legs:
 * <ul>
 *   <li><b>deposit</b> — debit the bank's cash position, credit the customer;</li>
 *   <li><b>withdrawal</b> — debit the customer, credit the bank's cash position;</li>
 *   <li><b>transfer</b> — debit the source customer, credit the target customer.</li>
 * </ul>
 *
 * <p>The bank leg is what makes deposits and withdrawals balance. Without it a deposit would be a
 * single credit and the ledger invariant would be false the first time anyone paid money in —
 * the money has to come from somewhere, and "outside the bank" is still an account in
 * double-entry bookkeeping.
 *
 * <p>The debit is always written first so a reader of the ledger sees a consistent order.
 */
@Component
public class LedgerPosting {

    private final LedgerRepository ledger;

    public LedgerPosting(LedgerRepository ledger) {
        this.ledger = ledger;
    }

    public List<LedgerEntry> post(Transaction transaction, Money sourceBalanceAfter,
                                  Money targetBalanceAfter, Instant now) {
        Money amount = transaction.amount();

        List<LedgerEntry> entries = switch (transaction.type()) {
            case DEPOSIT -> List.of(
                    LedgerEntry.forSystem(UUID.randomUUID(), transaction.id(), 0,
                            SystemAccount.CASH, TransactionDirection.DEBIT, amount, now),
                    LedgerEntry.forAccount(UUID.randomUUID(), transaction.id(), 1,
                            transaction.targetAccountId(), TransactionDirection.CREDIT,
                            amount, targetBalanceAfter, now));

            case WITHDRAWAL -> List.of(
                    LedgerEntry.forAccount(UUID.randomUUID(), transaction.id(), 0,
                            transaction.sourceAccountId(), TransactionDirection.DEBIT,
                            amount, sourceBalanceAfter, now),
                    LedgerEntry.forSystem(UUID.randomUUID(), transaction.id(), 1,
                            SystemAccount.CASH, TransactionDirection.CREDIT, amount, now));

            case TRANSFER -> List.of(
                    LedgerEntry.forAccount(UUID.randomUUID(), transaction.id(), 0,
                            transaction.sourceAccountId(), TransactionDirection.DEBIT,
                            amount, sourceBalanceAfter, now),
                    LedgerEntry.forAccount(UUID.randomUUID(), transaction.id(), 1,
                            transaction.targetAccountId(), TransactionDirection.CREDIT,
                            amount, targetBalanceAfter, now));
        };

        // append() checks that the group balances before writing anything.
        return ledger.append(entries);
    }
}
