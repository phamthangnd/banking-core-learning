package com.example.bankcore.customer.infrastructure;

import com.example.bankcore.customer.domain.Customer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryCustomerRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    private final InMemoryCustomerRepository repository = new InMemoryCustomerRepository();

    private Customer save(String email, Instant createdAt) {
        return repository.save(Customer.register(UUID.randomUUID(), "Customer", email,
                "+84901234567", LocalDate.of(1990, 1, 1), createdAt));
    }

    @Test
    void shouldStoreAndFindById() {
        Customer saved = save("a@example.com", NOW);

        assertThat(repository.findById(saved.id())).contains(saved);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldFindByNormalizedEmail() {
        save("a@example.com", NOW);

        assertThat(repository.findByEmail("a@example.com")).isPresent();
        assertThat(repository.findByEmail("unknown@example.com")).isEmpty();
    }

    @Test
    void shouldReplaceOnSaveOfSameId() {
        Customer saved = save("a@example.com", NOW);

        repository.save(saved.withContactDetails("Renamed", "a@example.com", "+84900000000", NOW.plusSeconds(60)));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(saved.id()).orElseThrow().fullName()).isEqualTo("Renamed");
    }

    @Test
    void shouldListOldestFirstAndRespectTheLimit() {
        save("c@example.com", NOW.plusSeconds(200));
        save("a@example.com", NOW);
        save("b@example.com", NOW.plusSeconds(100));

        assertThat(repository.findAll(10)).extracting(Customer::email)
                .containsExactly("a@example.com", "b@example.com", "c@example.com");
        assertThat(repository.findAll(2)).extracting(Customer::email)
                .containsExactly("a@example.com", "b@example.com");
        assertThat(repository.findAll(0)).isEmpty();
    }

    @Test
    void shouldRejectNegativeLimit() {
        assertThatThrownBy(() -> repository.findAll(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
