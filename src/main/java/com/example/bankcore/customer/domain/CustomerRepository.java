package com.example.bankcore.customer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for customers.
 *
 * <p>The interface lives in the domain and is implemented by infrastructure, so business code
 * depends on an abstraction it owns rather than on a storage technology. Phase 01 stores
 * customers in memory; Phase 02 swaps in a JPA adapter without touching the service.
 *
 * <p>{@code findAll} is intentionally bounded: an unbounded "give me everything" call is the
 * seed of a production incident. Real pagination arrives with the database in Phase 02
 * (CLAUDE.md section 5).
 */
public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByEmail(String normalizedEmail);

    /** At most {@code limit} customers, ordered by creation time (oldest first). */
    List<Customer> findAll(int limit);

    long count();
}
