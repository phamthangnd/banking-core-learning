package com.example.bankcore.customer.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    private static Customer sample() {
        return Customer.register(UUID.randomUUID(), "Alice Nguyen", "Alice@Example.com",
                " +84 90 123 4567 ", LocalDate.of(1990, 1, 1), NOW);
    }

    @Test
    void shouldNormalizeEmailAndTrimText() {
        Customer customer = sample();

        assertThat(customer.email()).isEqualTo("alice@example.com");
        assertThat(customer.phoneNumber()).isEqualTo("+84 90 123 4567");
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> Customer.register(UUID.randomUUID(), "  ", "a@b.com", "+84901234567",
                LocalDate.of(1990, 1, 1), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullName");
    }

    @Test
    void shouldRejectEmailWithoutAtSign() {
        assertThatThrownBy(() -> Customer.normalizeEmail("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@");
    }

    @Test
    void shouldCreateANewInstanceOnContactChange() {
        Customer original = sample();

        Customer updated = original.withContactDetails("Alice Tran", "new@example.com", "+84 91 000 0000", LATER);

        assertThat(original.fullName()).isEqualTo("Alice Nguyen");
        assertThat(updated.fullName()).isEqualTo("Alice Tran");
        assertThat(updated.createdAt()).isEqualTo(NOW);
        assertThat(updated.updatedAt()).isEqualTo(LATER);
        assertThat(updated.dateOfBirth()).isEqualTo(original.dateOfBirth());
    }

    @Test
    void shouldCloseIdempotently() {
        Customer original = sample();

        Customer closed = original.close(LATER);
        Customer closedAgain = closed.close(LATER.plusSeconds(60));

        assertThat(closed.isClosed()).isTrue();
        assertThat(closed.updatedAt()).isEqualTo(LATER);
        assertThat(closedAgain).isSameAs(closed);
    }
}
