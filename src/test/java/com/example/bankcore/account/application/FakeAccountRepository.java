package com.example.bankcore.account.application;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountSearchQuery;
import com.example.bankcore.account.domain.AccountStatus;
import com.example.bankcore.common.pagination.PageResult;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory stand-in for {@link AccountRepository}, used by the service unit tests. */
class FakeAccountRepository implements AccountRepository {

    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1_000_000);

    @Override
    public Account save(Account account) {
        accounts.put(account.id(), account);
        return account;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        // A single-threaded fake has nothing to lock against.
        return findById(id);
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return accounts.values().stream()
                .filter(account -> account.accountNumber().equals(accountNumber))
                .findFirst();
    }

    @Override
    public PageResult<Account> search(AccountSearchQuery query) {
        List<Account> matches = accounts.values().stream()
                .filter(account -> query.customerIdOrEmpty()
                        .map(id -> account.customerId().equals(id)).orElse(true))
                .filter(account -> query.statusOrEmpty()
                        .map(status -> account.status() == status).orElse(true))
                .filter(account -> query.accountTypeOrEmpty()
                        .map(type -> account.accountType() == type).orElse(true))
                .filter(account -> query.currencyOrEmpty()
                        .map(currency -> account.currency().equals(currency)).orElse(true))
                .sorted(Comparator.comparing(Account::openedAt).thenComparing(Account::id))
                .toList();

        int from = (int) Math.min(query.page().offset(), matches.size());
        int to = Math.min(from + query.page().size(), matches.size());

        return new PageResult<>(matches.subList(from, to), query.page().page(),
                query.page().size(), matches.size());
    }

    @Override
    public boolean existsOpenAccountForCustomer(UUID customerId) {
        return accounts.values().stream()
                .anyMatch(account -> account.customerId().equals(customerId)
                        && account.status() != AccountStatus.CLOSED);
    }

    @Override
    public long nextAccountNumberSequence() {
        return sequence.incrementAndGet();
    }
}
