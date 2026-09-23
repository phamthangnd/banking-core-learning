package com.example.bankcore.customer.domain;

import com.example.bankcore.common.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for customers.
 *
 * <p>The interface lives in the domain and is implemented by infrastructure, so business code
 * depends on an abstraction it owns rather than on a storage technology. Phase 01 stored
 * customers in memory; Phase 02 swapped in a JPA adapter without changing a single business
 * rule — the point of the port.
 *
 * <p>There is no "find everything" method on purpose: reads that can grow are paginated
 * (CLAUDE.md section 5).
 */
public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByEmail(String normalizedEmail);

    /** One page of customers matching the query, sorted as the query asks. */
    PageResult<Customer> search(CustomerSearchQuery query);
}
