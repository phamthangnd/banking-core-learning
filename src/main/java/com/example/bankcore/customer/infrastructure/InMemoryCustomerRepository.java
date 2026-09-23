package com.example.bankcore.customer.infrastructure;

import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory adapter for {@link CustomerRepository} — Phase 01 only.
 *
 * <p>State lives in the heap and disappears on restart. It exists so the REST layer, validation
 * and business rules can be built and tested before PostgreSQL arrives in Phase 02, at which
 * point a JPA adapter replaces this class and nothing else changes.
 *
 * <p>Stored values are immutable records, so returning them directly cannot let a caller mutate
 * the store. A {@link ConcurrentHashMap} keeps concurrent reads and writes safe; the uniqueness
 * of an email is still the service's job, since that spans two keys and cannot be made atomic
 * here — a real unique index takes over in Phase 02.
 */
@Repository
public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<UUID, Customer> customers = new ConcurrentHashMap<>();

    @Override
    public Customer save(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        customers.put(customer.id(), customer);
        return customer;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public Optional<Customer> findByEmail(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
        return customers.values().stream()
                .filter(customer -> customer.email().equals(normalizedEmail))
                .findFirst();
    }

    @Override
    public List<Customer> findAll(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }

        return customers.values().stream()
                .sorted(Comparator.comparing(Customer::createdAt).thenComparing(Customer::id))
                .limit(limit)
                .toList();
    }

    @Override
    public long count() {
        return customers.size();
    }
}
