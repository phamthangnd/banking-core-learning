package com.example.bankcore.account.domain;

import com.example.bankcore.common.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for accounts. */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    Optional<Account> findByAccountNumber(String accountNumber);

    PageResult<Account> search(AccountSearchQuery query);

    /** Whether the customer holds any account that is not closed. */
    boolean existsOpenAccountForCustomer(UUID customerId);

    /** Next value of the account-number sequence. */
    long nextAccountNumberSequence();
}
