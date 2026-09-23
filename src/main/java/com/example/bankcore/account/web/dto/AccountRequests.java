package com.example.bankcore.account.web.dto;

import com.example.bankcore.account.application.AccountCommands;
import com.example.bankcore.account.domain.AccountSortField;
import com.example.bankcore.account.domain.AccountStatus;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.SortDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

/** Request bodies and query parameters of the account endpoints. */
public final class AccountRequests {

    private AccountRequests() {
    }

    /**
     * @param accountType    product type; required, because it decides the account's rules
     * @param currency       ISO-4217 code, or absent for the configured default
     * @param overdraftLimit requested overdraft, or absent for none
     */
    public record OpenAccount(
            @NotNull(message = "must not be null") AccountType accountType,

            @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter ISO-4217 code")
            String currency,

            // Money is BigDecimal at the API boundary too: binding it to a double would already
            // have lost precision before any validation could run (CLAUDE.md section 3).
            @DecimalMin(value = "0", message = "must not be negative")
            @Digits(integer = 15, fraction = 4, message = "must have at most 4 decimal places")
            BigDecimal overdraftLimit
    ) {
        public AccountCommands.OpenAccount toCommand(UUID customerId) {
            return new AccountCommands.OpenAccount(customerId, accountType, currency, overdraftLimit);
        }
    }

    public record SetOverdraftLimit(
            @NotNull(message = "must not be null")
            @DecimalMin(value = "0", message = "must not be negative")
            @Digits(integer = 15, fraction = 4, message = "must have at most 4 decimal places")
            BigDecimal overdraftLimit
    ) {
        public AccountCommands.SetOverdraftLimit toCommand() {
            return new AccountCommands.SetOverdraftLimit(overdraftLimit);
        }
    }

    /** Query parameters of {@code GET /api/v1/accounts}. */
    public record SearchAccounts(
            @Min(value = 0, message = "must not be negative") Integer page,
            @Min(value = 1, message = "must be at least 1") Integer size,
            AccountSortField sort,
            SortDirection direction,
            UUID customerId,
            AccountStatus status,
            AccountType accountType,
            @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter ISO-4217 code") String currency
    ) {
        public PageRequest toPageRequest(int defaultPageSize) {
            return new PageRequest(
                    page == null ? 0 : page,
                    size == null ? defaultPageSize : size,
                    (sort == null ? AccountSortField.OPENED_AT : sort).property(),
                    direction == null ? SortDirection.DESC : direction);
        }
    }
}
