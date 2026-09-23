package com.example.bankcore.account.domain;

import com.example.bankcore.common.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for accounts. */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    /**
     * Loads an account and holds a row lock until the surrounding transaction ends.
     *
     * <p>Used on the paths that change a balance. Optimistic locking detects a conflict only
     * after the work is done, and under contention that means a stream of retries; a row lock
     * makes the second writer wait for the first instead. Callers that lock more than one
     * account must acquire the locks in a fixed order, or two transfers in opposite directions
     * will deadlock.
     */
    Optional<Account> findByIdForUpdate(UUID id);

    Optional<Account> findByAccountNumber(String accountNumber);

    PageResult<Account> search(AccountSearchQuery query);

    /** Whether the customer holds any account that is not closed. */
    boolean existsOpenAccountForCustomer(UUID customerId);

    /** Next value of the account-number sequence. */
    long nextAccountNumberSequence();
}
