package com.example.bankcore.transaction.web.dto;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.transaction.application.TransactionCommands;
import com.example.bankcore.transaction.domain.TransactionStatus;
import com.example.bankcore.transaction.domain.TransactionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request bodies and query parameters of the transaction endpoints. */
public final class TransactionRequests {

    private TransactionRequests() {
    }

    /**
     * Amounts are {@link BigDecimal} and bounded to four decimals, matching the column. A
     * {@code double} here would lose precision before validation could even run.
     */
    public record Deposit(
            @NotNull(message = "must not be null") UUID accountId,
            @NotNull(message = "must not be null")
            @DecimalMin(value = "0.0001", message = "must be positive")
            @Digits(integer = 15, fraction = 4, message = "must have at most 4 decimal places")
            BigDecimal amount,
            @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter ISO-4217 code") String currency,
            @Size(max = 255, message = "must be at most 255 characters") String description
    ) {
        public TransactionCommands.Deposit toCommand() {
            return new TransactionCommands.Deposit(accountId, amount, currency, description);
        }
    }

    public record Withdraw(
            @NotNull(message = "must not be null") UUID accountId,
            @NotNull(message = "must not be null")
            @DecimalMin(value = "0.0001", message = "must be positive")
            @Digits(integer = 15, fraction = 4, message = "must have at most 4 decimal places")
            BigDecimal amount,
            @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter ISO-4217 code") String currency,
            @Size(max = 255, message = "must be at most 255 characters") String description
    ) {
        public TransactionCommands.Withdraw toCommand() {
            return new TransactionCommands.Withdraw(accountId, amount, currency, description);
        }
    }

    public record Transfer(
            @NotNull(message = "must not be null") UUID sourceAccountId,
            @NotNull(message = "must not be null") UUID targetAccountId,
            @NotNull(message = "must not be null")
            @DecimalMin(value = "0.0001", message = "must be positive")
            @Digits(integer = 15, fraction = 4, message = "must have at most 4 decimal places")
            BigDecimal amount,
            @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter ISO-4217 code") String currency,
            @Size(max = 255, message = "must be at most 255 characters") String description
    ) {
        /**
         * A transfer needs two different accounts. Structural, so it is answered at the boundary
         * with a 400 rather than travelling into the service as a business rejection.
         */
        @AssertTrue(message = "must differ from sourceAccountId")
        public boolean isTargetAccountIdDifferent() {
            return sourceAccountId == null || !sourceAccountId.equals(targetAccountId);
        }

        public TransactionCommands.Transfer toCommand() {
            return new TransactionCommands.Transfer(sourceAccountId, targetAccountId, amount,
                    currency, description);
        }
    }

    /** @param reason free-text note recorded in the compensating transaction's description */
    public record Reverse(
            @Size(max = 200, message = "must be at most 200 characters") String reason
    ) {
    }

    /** Query parameters of the history endpoints. */
    public record SearchTransactions(
            @Min(value = 0, message = "must not be negative") Integer page,
            @Min(value = 1, message = "must be at least 1") Integer size,
            SortDirection direction,
            UUID accountId,
            TransactionType type,
            TransactionStatus status,
            Instant from,
            Instant to
    ) {
        /** History is always sorted by business time; the client chooses only the direction. */
        public PageRequest toPageRequest(int defaultPageSize) {
            return new PageRequest(
                    page == null ? 0 : page,
                    size == null ? defaultPageSize : size,
                    "occurredAt",
                    direction == null ? SortDirection.DESC : direction);
        }
    }
}
