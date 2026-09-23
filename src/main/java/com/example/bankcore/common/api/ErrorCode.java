package com.example.bankcore.common.api;

/**
 * Stable, machine-readable error codes returned to API clients.
 *
 * <p>The code is part of the API contract: clients branch on it, so a code is never renamed or
 * reused for a different meaning. The human-readable message may change freely.
 *
 * <p>Each code declares a {@link Category} instead of an HTTP status, so that this enum stays
 * free of web-framework types and can be used from domain code. The web layer
 * ({@code common.web.GlobalExceptionHandler}) owns the mapping from category to HTTP status.
 */
public enum ErrorCode {

    /** Request body or parameters failed Bean Validation. */
    VALIDATION_FAILED(Category.VALIDATION),

    /** Request could not be parsed at all (malformed JSON, wrong type). */
    MALFORMED_REQUEST(Category.VALIDATION),

    /** The addressed resource does not exist. */
    CUSTOMER_NOT_FOUND(Category.NOT_FOUND),

    /** Another customer already uses the given email address. */
    CUSTOMER_EMAIL_ALREADY_USED(Category.CONFLICT),

    /** A domain rule rejected an otherwise well-formed request. */
    CUSTOMER_RULE_VIOLATED(Category.BUSINESS_RULE),

    /** Two concurrent writers modified the same record; the loser must retry. */
    CONCURRENT_MODIFICATION(Category.CONFLICT),

    /** Anything unexpected. Details stay in the logs, never in the response. */
    INTERNAL_ERROR(Category.INTERNAL);

    private final Category category;

    ErrorCode(Category category) {
        this.category = category;
    }

    public Category category() {
        return category;
    }

    /** Kind of failure, translated into an HTTP status by the web layer. */
    public enum Category {
        VALIDATION,
        NOT_FOUND,
        CONFLICT,
        BUSINESS_RULE,
        INTERNAL
    }
}
