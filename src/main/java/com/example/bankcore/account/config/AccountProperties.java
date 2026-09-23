package com.example.bankcore.account.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Account module settings, bound from {@code bankcore.account.*}.
 *
 * @param numberPrefix           bank prefix that every account number starts with
 * @param defaultCurrency        currency used when a request does not name one
 * @param maximumOverdraftLimit  largest overdraft that may be granted, for types that allow one
 */
@ConfigurationProperties(prefix = "bankcore.account")
@Validated
public record AccountProperties(
        @Pattern(regexp = "[0-9]{1,6}", message = "must be 1 to 6 digits")
        @DefaultValue("9004") String numberPrefix,

        @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code")
        @DefaultValue("VND") String defaultCurrency,

        @DecimalMin(value = "0", message = "must not be negative")
        @DefaultValue("0") BigDecimal maximumOverdraftLimit
) {
}
