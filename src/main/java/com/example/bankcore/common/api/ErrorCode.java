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

    /** The addressed account does not exist. */
    ACCOUNT_NOT_FOUND(Category.NOT_FOUND),

    /** An account rule rejected the request: wrong lifecycle state, non-zero balance, and so on. */
    ACCOUNT_RULE_VIOLATED(Category.BUSINESS_RULE),

    /** Two concurrent writers modified the same record; the loser must retry. */
    CONCURRENT_MODIFICATION(Category.CONFLICT),

    /** The addressed user does not exist. */
    USER_NOT_FOUND(Category.NOT_FOUND),

    /** Username or email already registered. */
    USER_ALREADY_EXISTS(Category.CONFLICT),

    /**
     * Authentication failed.
     *
     * <p>Deliberately one code for "no such user", "wrong password" and "wrong token": telling
     * the two apart hands an attacker a user-enumeration oracle.
     */
    INVALID_CREDENTIALS(Category.UNAUTHENTICATED),

    /** The account is locked or disabled, so credentials cannot be used at all. */
    ACCOUNT_NOT_ACTIVE(Category.FORBIDDEN),

    /** No or invalid bearer token on a protected endpoint. */
    AUTHENTICATION_REQUIRED(Category.UNAUTHENTICATED),

    /** Authenticated, but lacking the required permission. */
    ACCESS_DENIED(Category.FORBIDDEN),

    /** Refresh or password-reset token is unknown, expired, used or revoked. */
    INVALID_TOKEN(Category.UNAUTHENTICATED),

    /** A rejected password: too short, too common, or the same as the current one. */
    WEAK_PASSWORD(Category.BUSINESS_RULE),

    /** Too many attempts in the rate-limiting window. */
    TOO_MANY_REQUESTS(Category.RATE_LIMITED),

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
        UNAUTHENTICATED,
        FORBIDDEN,
        RATE_LIMITED,
        INTERNAL
    }
}
