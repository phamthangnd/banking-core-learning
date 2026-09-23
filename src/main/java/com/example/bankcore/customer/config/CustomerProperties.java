package com.example.bankcore.customer.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable rules of the customer module, bound from {@code bankcore.customer.*}.
 *
 * <p>Java/Spring note: binding to a {@code record} gives immutable, constructor-injected
 * configuration — the values cannot drift at runtime. {@code @Validated} makes the constraints
 * below fail at <em>startup</em>, so a typo in a configuration file stops the application instead
 * of silently changing a business rule.
 *
 * @param minimumAgeYears  minimum age to become a customer
 * @param defaultPageSize  page size used when a client does not ask for one
 * @param maxPageSize      hard ceiling on the page size a client may request
 */
@ConfigurationProperties(prefix = "bankcore.customer")
@Validated
public record CustomerProperties(
        @Min(0) @Max(150) @DefaultValue("18") int minimumAgeYears,
        @Min(1) @Max(200) @DefaultValue("20") int defaultPageSize,
        @Min(1) @Max(500) @DefaultValue("100") int maxPageSize
) {
}
