package com.example.bankcore.account.domain;

import com.example.bankcore.common.pagination.PageRequest;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter criteria for an account search, plus the page to return. Every filter is optional.
 *
 * @param customerId  owner
 * @param status      lifecycle state
 * @param accountType product type
 * @param currency    ISO-4217 code
 * @param page        page and sort specification
 */
public record AccountSearchQuery(
        UUID customerId,
        AccountStatus status,
        AccountType accountType,
        String currency,
        PageRequest page
) {

    public AccountSearchQuery {
        Objects.requireNonNull(page, "page must not be null");
        currency = currency == null || currency.isBlank() ? null : currency.strip().toUpperCase(java.util.Locale.ROOT);
    }

    public static AccountSearchQuery forCustomer(UUID customerId, PageRequest page) {
        return new AccountSearchQuery(customerId, null, null, null, page);
    }

    public Optional<UUID> customerIdOrEmpty() {
        return Optional.ofNullable(customerId);
    }

    public Optional<AccountStatus> statusOrEmpty() {
        return Optional.ofNullable(status);
    }

    public Optional<AccountType> accountTypeOrEmpty() {
        return Optional.ofNullable(accountType);
    }

    public Optional<String> currencyOrEmpty() {
        return Optional.ofNullable(currency);
    }
}
