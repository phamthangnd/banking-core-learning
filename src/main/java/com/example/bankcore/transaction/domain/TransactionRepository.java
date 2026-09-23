package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for transactions.
 *
 * <p>There is no {@code delete} and no general {@code update}: the history is append-only, and
 * the only change a record ever undergoes is a status transition.
 */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByReference(String reference);

    PageResult<Transaction> search(TransactionSearchQuery query);

    long nextReferenceSequence();
}
