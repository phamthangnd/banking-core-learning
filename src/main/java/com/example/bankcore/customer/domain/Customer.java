package com.example.bankcore.customer.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A customer of the bank.
 *
 * <p>Immutable: every change produces a new instance, so a customer object can never be half
 * updated and can be shared freely between threads. Phase 02 turns this into a JPA entity behind
 * the same repository port; the REST layer keeps talking to DTOs either way (CLAUDE.md section 2).
 *
 * <p>Emails are normalised to lower case here rather than in a service, so that uniqueness checks
 * and lookups cannot disagree about what "the same email" means.
 *
 * @param id           identity, assigned once
 * @param fullName     display name
 * @param email        normalised (lower-case, trimmed) email address; unique across customers
 * @param phoneNumber  contact number in E.164-ish format
 * @param dateOfBirth  used for the minimum-age rule
 * @param status       lifecycle state
 * @param createdAt    creation timestamp (UTC)
 * @param updatedAt    timestamp of the last change (UTC)
 */
public record Customer(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public Customer {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        fullName = requireText(fullName, "fullName").strip();
        phoneNumber = requireText(phoneNumber, "phoneNumber").strip();
        email = normalizeEmail(email);
    }

    /** Creates a new active customer. */
    public static Customer register(UUID id, String fullName, String email, String phoneNumber,
                                    LocalDate dateOfBirth, Instant now) {
        return new Customer(id, fullName, email, phoneNumber, dateOfBirth,
                CustomerStatus.ACTIVE, now, now);
    }

    /** Returns a copy with updated contact details. Identity, birth date and status are unchanged. */
    public Customer withContactDetails(String newFullName, String newEmail, String newPhoneNumber, Instant now) {
        return new Customer(id, newFullName, newEmail, newPhoneNumber, dateOfBirth, status, createdAt, now);
    }

    /** Returns a closed copy. Closing an already closed customer is a no-op, so it is idempotent. */
    public Customer close(Instant now) {
        if (status.isClosed()) {
            return this;
        }
        return new Customer(id, fullName, email, phoneNumber, dateOfBirth,
                CustomerStatus.CLOSED, createdAt, now);
    }

    public boolean isClosed() {
        return status.isClosed();
    }

    /** Normalises an email for storage and comparison. */
    public static String normalizeEmail(String email) {
        String normalized = requireText(email, "email").strip().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new IllegalArgumentException("email must contain @");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
