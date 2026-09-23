package com.example.bankcore.ledger.application;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountExceptions;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.ledger.domain.LedgerEntry;
import com.example.bankcore.ledger.domain.LedgerRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reading and reconciling the ledger.
 *
 * <p>Reconciliation is the question a bank has to be able to answer at any moment: does what the
 * ledger says agree with what the accounts say, and do the debits still equal the credits? Two
 * numbers that are supposed to be equal are only useful if something actually compares them.
 */
@Service
@Transactional(readOnly = true)
public class LedgerService {

    private final LedgerRepository ledger;
    private final AccountRepository accounts;

    public LedgerService(LedgerRepository ledger, AccountRepository accounts) {
        this.ledger = ledger;
        this.accounts = accounts;
    }

    @PreAuthorize("hasAuthority('transaction:read')")
    public List<LedgerEntry> entriesOfTransaction(UUID transactionId) {
        return ledger.findByTransactionId(transactionId);
    }

    @PreAuthorize("hasAuthority('transaction:read')")
    public List<LedgerEntry> entriesOfAccount(UUID accountId) {
        return ledger.findByAccountId(accountId);
    }

    /**
     * Whole-ledger reconciliation: debits against credits, per currency.
     *
     * @return a report that says whether it balances and by how much it does not
     */
    @PreAuthorize("hasAuthority('transaction:read')")
    public LedgerReconciliation reconcile() {
        Map<String, Money> debits = ledger.totalDebits();
        Map<String, Money> credits = ledger.totalCredits();

        boolean balanced = debits.keySet().equals(credits.keySet())
                && debits.entrySet().stream()
                .allMatch(entry -> entry.getValue().compareTo(credits.get(entry.getKey())) == 0);

        return new LedgerReconciliation(balanced, debits, credits);
    }

    /**
     * Reconciles one account: the balance derived from its ledger entries against the balance
     * stored on the account row.
     *
     * <p>They are two independent records of the same fact. If they ever disagree, one of them is
     * wrong and the difference is the amount of money that has been created or lost.
     */
    @PreAuthorize("hasAuthority('transaction:read')")
    public AccountReconciliation reconcileAccount(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountExceptions.AccountNotFoundException(accountId));

        Money derived = ledger.balanceOfAccount(accountId, account.currency());

        return new AccountReconciliation(accountId, account.accountNumber(), account.currency(),
                account.balance(), derived, account.balance().compareTo(derived) == 0);
    }

    /**
     * @param balanced whether debits equal credits in every currency
     * @param debits   total debits per currency
     * @param credits  total credits per currency
     */
    public record LedgerReconciliation(boolean balanced, Map<String, Money> debits, Map<String, Money> credits) {
    }

    /**
     * @param storedBalance  the balance on the account row
     * @param derivedBalance the balance implied by the ledger entries
     * @param reconciled     whether the two agree
     */
    public record AccountReconciliation(UUID accountId, String accountNumber, String currency,
                                        Money storedBalance, Money derivedBalance, boolean reconciled) {
    }
}
