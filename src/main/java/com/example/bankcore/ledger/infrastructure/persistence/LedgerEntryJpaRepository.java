package com.example.bankcore.ledger.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    List<LedgerEntryEntity> findByTransactionIdOrderByEntryIndex(UUID transactionId);

    List<LedgerEntryEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    /**
     * Totals per currency for one direction.
     *
     * <p>Summed in the database rather than in Java: reconciliation has to consider every entry,
     * and loading a whole ledger into memory to add it up stops working long before the ledger
     * gets interesting.
     */
    @Query("""
            select e.currency, sum(e.amount) from LedgerEntryEntity e
             where e.direction = :direction
             group by e.currency
            """)
    List<Object[]> totalsByCurrency(@Param("direction")
                                    com.example.bankcore.transaction.domain.TransactionDirection direction);

    /**
     * An account's balance derived from its own entries.
     *
     * <p>Credits add, debits subtract. Comparing this with the balance stored on the account is
     * the reconciliation that proves the two never drifted apart.
     */
    @Query("""
            select coalesce(sum(case when e.direction = com.example.bankcore.transaction.domain.TransactionDirection.CREDIT
                                     then e.amount else -e.amount end), 0)
              from LedgerEntryEntity e
             where e.accountId = :accountId and e.currency = :currency
            """)
    BigDecimal derivedBalance(@Param("accountId") UUID accountId, @Param("currency") String currency);
}
