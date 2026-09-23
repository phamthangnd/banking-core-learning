package com.example.bankcore.transaction.infrastructure.persistence;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionRepository;
import com.example.bankcore.transaction.domain.TransactionSearchQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter translating the {@link TransactionRepository} port onto JPA. */
@Repository
@Transactional(readOnly = true)
public class JpaTransactionRepository implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    public JpaTransactionRepository(TransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = jpaRepository.findById(transaction.id())
                .map(existing -> {
                    existing.applyState(transaction);
                    return existing;
                })
                .orElseGet(() -> TransactionEntity.fromDomain(transaction));

        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jpaRepository.findById(id).map(TransactionEntity::toDomain);
    }

    @Override
    public Optional<Transaction> findByReference(String reference) {
        return jpaRepository.findByReference(reference).map(TransactionEntity::toDomain);
    }

    @Override
    public PageResult<Transaction> search(TransactionSearchQuery query) {
        Page<TransactionEntity> page = jpaRepository.findAll(specification(query), toPageable(query.page()));

        return new PageResult<>(page.getContent().stream().map(TransactionEntity::toDomain).toList(),
                query.page().page(), query.page().size(), page.getTotalElements());
    }

    @Override
    @Transactional
    public long nextReferenceSequence() {
        return jpaRepository.nextReferenceSequence();
    }

    @Override
    public List<Transaction> findAfter(UUID accountId,
                                       com.example.bankcore.common.pagination.Cursor after, int limit) {
        var page = org.springframework.data.domain.PageRequest.ofSize(limit);

        var entities = after == null
                ? jpaRepository.findFirstPage(accountId, page)
                : jpaRepository.findAfter(accountId, after.timestamp(), after.id(), page);

        return entities.stream().map(TransactionEntity::toDomain).toList();
    }

    private static Specification<TransactionEntity> specification(TransactionSearchQuery query) {
        List<Specification<TransactionEntity>> predicates = new ArrayList<>();

        // An account's history is everything that touched it, on either side. Written as one OR
        // so a single query answers "money in and money out" — two queries would need merging
        // and would make paging meaningless.
        query.accountIdOrEmpty().ifPresent(accountId -> predicates.add((root, q, builder) -> {
            Predicate asSource = builder.equal(root.get("sourceAccountId"), accountId);
            Predicate asTarget = builder.equal(root.get("targetAccountId"), accountId);
            return builder.or(asSource, asTarget);
        }));

        query.typeOrEmpty().ifPresent(type ->
                predicates.add((root, q, builder) -> builder.equal(root.get("transactionType"), type)));
        query.statusOrEmpty().ifPresent(status ->
                predicates.add((root, q, builder) -> builder.equal(root.get("status"), status)));
        // Half-open range, so adjacent periods tile without double-counting.
        query.fromOrEmpty().ifPresent(from ->
                predicates.add((root, q, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), from)));
        query.toOrEmpty().ifPresent(to ->
                predicates.add((root, q, builder) -> builder.lessThan(root.get("occurredAt"), to)));

        return Specification.allOf(predicates);
    }

    private static Pageable toPageable(PageRequest request) {
        Sort.Direction direction = request.direction() == SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Sort sort = Sort.by(direction, request.sortProperty()).and(Sort.by(Sort.Direction.ASC, "id"));
        return org.springframework.data.domain.PageRequest.of(request.page(), request.size(), sort);
    }
}
