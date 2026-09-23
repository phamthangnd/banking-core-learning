package com.example.bankcore.customer.infrastructure.persistence;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.customer.domain.CustomerSearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter translating the domain {@link CustomerRepository} port onto JPA.
 *
 * <p>Everything persistence-specific stops here: Spring Data types, entities and the persistence
 * context. The service above it sees only domain records and the module's own page types.
 */
@Repository
public class JpaCustomerRepository implements CustomerRepository {

    private final CustomerJpaRepository jpaRepository;

    public JpaCustomerRepository(CustomerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Inserts a new customer or updates an existing one.
     *
     * <p>For an existing id the entity is loaded first and mutated, so it is <em>managed</em> by
     * the persistence context: Hibernate's dirty checking writes the UPDATE and the
     * {@code @Version} column is incremented. Building a detached entity from the domain record
     * instead would reset the version and defeat optimistic locking.
     */
    @Override
    public Customer save(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");

        CustomerEntity entity = jpaRepository.findById(customer.id())
                .map(existing -> {
                    existing.applyState(customer);
                    return existing;
                })
                .orElseGet(() -> CustomerEntity.fromDomain(customer));

        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return jpaRepository.findById(id).map(CustomerEntity::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
        return jpaRepository.findByEmail(normalizedEmail).map(CustomerEntity::toDomain);
    }

    @Override
    public PageResult<Customer> search(CustomerSearchQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        Page<CustomerEntity> page = jpaRepository.findAll(
                CustomerSpecifications.from(query), toPageable(query.page()));

        return new PageResult<>(
                page.getContent().stream().map(CustomerEntity::toDomain).toList(),
                query.page().page(),
                query.page().size(),
                page.getTotalElements());
    }

    private static Pageable toPageable(PageRequest request) {
        Sort.Direction direction = request.direction() == SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // The sort property comes from CustomerSortField, an allow-list, never from raw input.
        // A secondary sort on the primary key keeps paging stable: without a tiebreaker, rows
        // with equal sort values can shuffle between pages and be shown twice or skipped.
        Sort sort = Sort.by(direction, request.sortProperty()).and(Sort.by(Sort.Direction.ASC, "id"));

        return org.springframework.data.domain.PageRequest.of(request.page(), request.size(), sort);
    }
}
