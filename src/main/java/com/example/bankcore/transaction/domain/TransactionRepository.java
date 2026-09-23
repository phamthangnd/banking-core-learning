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

    /**
     * One keyset page of an account's history, oldest first.
     *
     * <p>Used by the export, which walks the whole history in batches. Offset paging would make
     * the database produce and discard every earlier row on each batch, so exporting a long
     * history would cost time quadratic in its length.
     *
     * @param after only rows strictly after this position, or {@code null} to start
     */
    java.util.List<Transaction> findAfter(java.util.UUID accountId,
                                          com.example.bankcore.common.pagination.Cursor after,
                                          int limit);
}
