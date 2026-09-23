package com.example.bankcore.customer.application;

import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.customer.domain.CustomerSearchQuery;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory stand-in for {@link CustomerRepository}, used by the service unit tests.
 *
 * <p>A hand-written fake rather than a mock: the business rules under test depend on how the
 * store actually behaves (an email lookup must find what a save stored), and a pile of
 * {@code when(...).thenReturn(...)} stubs would encode assumptions instead of verifying them.
 *
 * <p>It lives in the test sources on purpose. Production has exactly one repository
 * implementation — the JPA adapter.
 */
class FakeCustomerRepository implements CustomerRepository {

    private final Map<UUID, Customer> customers = new LinkedHashMap<>();

    @Override
    public Customer save(Customer customer) {
        customers.put(customer.id(), customer);
        return customer;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public Optional<Customer> findByEmail(String normalizedEmail) {
        return customers.values().stream()
                .filter(customer -> customer.email().equals(normalizedEmail))
                .findFirst();
    }

    @Override
    public PageResult<Customer> search(CustomerSearchQuery query) {
        List<Customer> matches = customers.values().stream()
                .filter(customer -> query.statusOrEmpty()
                        .map(status -> customer.status() == status).orElse(true))
                .filter(customer -> query.emailOrEmpty()
                        .map(email -> customer.email().equals(email)).orElse(true))
                .filter(customer -> query.nameFragmentOrEmpty()
                        .map(fragment -> customer.fullName().toLowerCase(Locale.ROOT)
                                .contains(fragment.toLowerCase(Locale.ROOT)))
                        .orElse(true))
                .sorted(Comparator.comparing(Customer::createdAt).thenComparing(Customer::id))
                .toList();

        int from = (int) Math.min(query.page().offset(), matches.size());
        int to = Math.min(from + query.page().size(), matches.size());

        return new PageResult<>(matches.subList(from, to), query.page().page(),
                query.page().size(), matches.size());
    }

    long count() {
        return customers.size();
    }
}
