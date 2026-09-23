package com.example.bankcore.customer.infrastructure.persistence;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerSearchQuery;
import com.example.bankcore.customer.domain.CustomerStatus;
import com.example.bankcore.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The JPA adapter against a real PostgreSQL: mapping, constraints, paging and sorting.
 */
@SpringBootTest
class JpaCustomerRepositoryTest extends PostgresIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JpaCustomerRepository repository;

    @Autowired
    private CustomerJpaRepository jpaRepository;

    @BeforeEach
    void clearDatabase() {
        jpaRepository.deleteAll();
    }

    private Customer persist(String name, String email, Instant createdAt) {
        return repository.save(Customer.register(UUID.randomUUID(), name, email,
                "+84901234567", LocalDate.of(1990, 1, 1), createdAt));
    }

    @Test
    void shouldRoundTripEveryFieldThroughPostgres() {
        Customer saved = persist("Alice Nguyen", "alice@example.com", BASE);

        Customer loaded = repository.findById(saved.id()).orElseThrow();

        assertThat(loaded.fullName()).isEqualTo("Alice Nguyen");
        assertThat(loaded.email()).isEqualTo("alice@example.com");
        assertThat(loaded.phoneNumber()).isEqualTo("+84901234567");
        assertThat(loaded.dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(loaded.status()).isEqualTo(CustomerStatus.ACTIVE);
        // TIMESTAMPTZ keeps the instant, not a local reading of a clock.
        assertThat(loaded.createdAt()).isEqualTo(BASE);
        assertThat(loaded.updatedAt()).isEqualTo(BASE);
    }

    @Test
    void shouldUpdateInPlaceInsteadOfInsertingASecondRow() {
        Customer saved = persist("Alice Nguyen", "alice@example.com", BASE);

        repository.save(saved.withContactDetails("Alice Tran", "alice@example.com",
                "+84900000000", BASE.plusSeconds(60)));

        assertThat(jpaRepository.count()).isEqualTo(1);
        Customer loaded = repository.findById(saved.id()).orElseThrow();
        assertThat(loaded.fullName()).isEqualTo("Alice Tran");
        assertThat(loaded.createdAt()).isEqualTo(BASE);
        assertThat(loaded.updatedAt()).isEqualTo(BASE.plusSeconds(60));
    }

    @Test
    void shouldIncrementTheVersionOnEveryUpdate() {
        Customer saved = persist("Alice Nguyen", "alice@example.com", BASE);
        long initialVersion = jpaRepository.findById(saved.id()).orElseThrow().getVersion();

        repository.save(saved.withContactDetails("Alice Tran", "alice@example.com",
                "+84900000000", BASE.plusSeconds(60)));

        long updatedVersion = jpaRepository.findById(saved.id()).orElseThrow().getVersion();
        assertThat(updatedVersion).isGreaterThan(initialVersion);
    }

    @Test
    void shouldLetTheDatabaseRejectADuplicateEmail() {
        persist("Alice Nguyen", "alice@example.com", BASE);

        // The service checks first, but only the unique index makes uniqueness true under
        // concurrency. Writing straight through the adapter proves the index is really there.
        assertThatThrownBy(() -> persist("Someone Else", "alice@example.com", BASE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldFindByNormalizedEmail() {
        persist("Alice Nguyen", "alice@example.com", BASE);

        assertThat(repository.findByEmail("alice@example.com")).isPresent();
        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void shouldPageThroughResultsInAStableOrder() {
        for (int i = 0; i < 7; i++) {
            persist("Customer %d".formatted(i), "customer%d@example.com".formatted(i), BASE.plusSeconds(i));
        }

        var first = repository.search(query(null, null, null, 0, 3, "createdAt", SortDirection.ASC));
        var second = repository.search(query(null, null, null, 1, 3, "createdAt", SortDirection.ASC));
        var third = repository.search(query(null, null, null, 2, 3, "createdAt", SortDirection.ASC));

        assertThat(first.content()).hasSize(3);
        assertThat(second.content()).hasSize(3);
        assertThat(third.content()).hasSize(1);
        assertThat(first.totalElements()).isEqualTo(7);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(third.hasNext()).isFalse();

        // No row appears on two pages and none is skipped.
        assertThat(first.content()).doesNotContainAnyElementsOf(second.content());
        assertThat(first.content().get(0).fullName()).isEqualTo("Customer 0");
        assertThat(third.content().get(0).fullName()).isEqualTo("Customer 6");
    }

    @Test
    void shouldSortDescending() {
        persist("Aaa", "a@example.com", BASE);
        persist("Bbb", "b@example.com", BASE.plusSeconds(10));

        var page = repository.search(query(null, null, null, 0, 10, "fullName", SortDirection.DESC));

        assertThat(page.content()).extracting(Customer::fullName).containsExactly("Bbb", "Aaa");
    }

    @Test
    void shouldFilterByNameFragmentCaseInsensitively() {
        persist("Alice Nguyen", "alice@example.com", BASE);
        persist("Bob Tran", "bob@example.com", BASE.plusSeconds(10));

        var page = repository.search(query("nGuY", null, null, 0, 10, "createdAt", SortDirection.ASC));

        assertThat(page.content()).extracting(Customer::fullName).containsExactly("Alice Nguyen");
    }

    @Test
    void shouldFilterByEmailAndStatus() {
        Customer alice = persist("Alice Nguyen", "alice@example.com", BASE);
        persist("Bob Tran", "bob@example.com", BASE.plusSeconds(10));
        repository.save(alice.close(BASE.plusSeconds(20)));

        assertThat(repository.search(query(null, "alice@example.com", null, 0, 10, "createdAt", SortDirection.ASC))
                .content()).hasSize(1);
        assertThat(repository.search(query(null, null, CustomerStatus.CLOSED, 0, 10, "createdAt", SortDirection.ASC))
                .content()).extracting(Customer::email).containsExactly("alice@example.com");
        assertThat(repository.search(query(null, null, CustomerStatus.ACTIVE, 0, 10, "createdAt", SortDirection.ASC))
                .content()).extracting(Customer::email).containsExactly("bob@example.com");
    }

    @Test
    void shouldReturnAnEmptyPageBeyondTheLastOne() {
        persist("Alice Nguyen", "alice@example.com", BASE);

        var page = repository.search(query(null, null, null, 5, 10, "createdAt", SortDirection.ASC));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    private static CustomerSearchQuery query(String name, String email, CustomerStatus status,
                                             int page, int size, String sortProperty, SortDirection direction) {
        return new CustomerSearchQuery(name, email, status,
                new PageRequest(page, size, sortProperty, direction));
    }
}
