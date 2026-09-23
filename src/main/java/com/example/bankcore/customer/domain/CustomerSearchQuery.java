package com.example.bankcore.customer.domain;

import com.example.bankcore.common.pagination.PageRequest;

import java.util.Objects;
import java.util.Optional;

/**
 * Filter criteria for a customer search, plus the page to return.
 *
 * <p>Every filter is optional; an absent filter means "do not restrict on this". The type uses
 * {@link Optional} accessors so a caller cannot forget that a criterion may be missing, while
 * the components themselves stay nullable for easy construction.
 *
 * @param nameFragment case-insensitive fragment of the full name, or {@code null}
 * @param email        exact (normalised) email address, or {@code null}
 * @param status       lifecycle state, or {@code null}
 * @param page         page and sort specification
 */
public record CustomerSearchQuery(String nameFragment, String email, CustomerStatus status, PageRequest page) {

    public CustomerSearchQuery {
        Objects.requireNonNull(page, "page must not be null");
        nameFragment = blankToNull(nameFragment);
        email = email == null || email.isBlank() ? null : Customer.normalizeEmail(email);
    }

    public Optional<String> nameFragmentOrEmpty() {
        return Optional.ofNullable(nameFragment);
    }

    public Optional<String> emailOrEmpty() {
        return Optional.ofNullable(email);
    }

    public Optional<CustomerStatus> statusOrEmpty() {
        return Optional.ofNullable(status);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
