package com.example.bankcore.ledger.infrastructure.persistence;

import com.example.bankcore.common.money.Money;
import com.example.bankcore.ledger.domain.LedgerEntries;
import com.example.bankcore.ledger.domain.LedgerEntry;
import com.example.bankcore.ledger.domain.LedgerRepository;
import com.example.bankcore.transaction.domain.TransactionDirection;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Adapter translating the {@link LedgerRepository} port onto JPA. */
@Repository
@Transactional(readOnly = true)
public class JpaLedgerRepository implements LedgerRepository {

    private final LedgerEntryJpaRepository entries;

    public JpaLedgerRepository(LedgerEntryJpaRepository entries) {
        this.entries = entries;
    }

    /**
     * Appends a group after checking that it balances.
     *
     * <p>The check happens here rather than being left to callers: this is the only door into the
     * ledger, so putting the invariant on it means no caller can bypass it by forgetting.
     */
    @Override
    @Transactional
    public List<LedgerEntry> append(List<LedgerEntry> group) {
        LedgerEntries.requireBalanced(group);

        return entries.saveAll(group.stream().map(LedgerEntryEntity::fromDomain).toList())
                .stream().map(LedgerEntryEntity::toDomain).toList();
    }

    @Override
    public List<LedgerEntry> findByTransactionId(UUID transactionId) {
        return entries.findByTransactionIdOrderByEntryIndex(transactionId)
                .stream().map(LedgerEntryEntity::toDomain).toList();
    }

    @Override
    public List<LedgerEntry> findByAccountId(UUID accountId) {
        return entries.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream().map(LedgerEntryEntity::toDomain).toList();
    }

    @Override
    public Map<String, Money> totalDebits() {
        return totals(TransactionDirection.DEBIT);
    }

    @Override
    public Map<String, Money> totalCredits() {
        return totals(TransactionDirection.CREDIT);
    }

    @Override
    public Money balanceOfAccount(UUID accountId, String currency) {
        BigDecimal derived = entries.derivedBalance(accountId, currency);
        return new Money(derived == null ? BigDecimal.ZERO : derived, currency);
    }

    private Map<String, Money> totals(TransactionDirection direction) {
        Map<String, Money> totals = new TreeMap<>();

        for (Object[] row : entries.totalsByCurrency(direction)) {
            String currency = (String) row[0];
            totals.put(currency, new Money((BigDecimal) row[1], currency));
        }

        return totals;
    }
}
