package com.example.bankcore.account.infrastructure.persistence;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountSearchQuery;
import com.example.bankcore.account.domain.AccountStatus;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.pagination.SortDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Adapter translating the {@link AccountRepository} port onto JPA. */
@Repository
@Transactional(readOnly = true)
public class JpaAccountRepository implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    public JpaAccountRepository(AccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Account save(Account account) {
        Objects.requireNonNull(account, "account must not be null");

        AccountEntity entity = jpaRepository.findById(account.id())
                .map(existing -> {
                    existing.applyState(account);
                    return existing;
                })
                .orElseGet(() -> AccountEntity.fromDomain(account));

        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return jpaRepository.findById(id).map(AccountEntity::toDomain);
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return jpaRepository.findByIdForUpdate(id).map(AccountEntity::toDomain);
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        return jpaRepository.findByAccountNumber(accountNumber).map(AccountEntity::toDomain);
    }

    @Override
    public PageResult<Account> search(AccountSearchQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        Page<AccountEntity> page = jpaRepository.findAll(
                AccountSpecifications.from(query), toPageable(query.page()));

        return new PageResult<>(
                page.getContent().stream().map(AccountEntity::toDomain).toList(),
                query.page().page(), query.page().size(), page.getTotalElements());
    }

    @Override
    public boolean existsOpenAccountForCustomer(UUID customerId) {
        return jpaRepository.existsByCustomerIdAndStatusNot(customerId, AccountStatus.CLOSED);
    }

    @Override
    @Transactional
    public long nextAccountNumberSequence() {
        return jpaRepository.nextAccountNumberSequence();
    }

    private static Pageable toPageable(PageRequest request) {
        Sort.Direction direction = request.direction() == SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // A secondary sort on the primary key keeps paging stable when sort values tie.
        Sort sort = Sort.by(direction, request.sortProperty()).and(Sort.by(Sort.Direction.ASC, "id"));

        return org.springframework.data.domain.PageRequest.of(request.page(), request.size(), sort);
    }
}
