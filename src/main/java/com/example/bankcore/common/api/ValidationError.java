package com.example.bankcore.common.api;

/**
 * One field-level validation failure.
 *
 * <p>Deliberately carries no rejected value: request bodies may contain passwords, tokens or
 * account data, and an error response is logged and forwarded far more often than a request body
 * (CLAUDE.md section 4).
 *
 * @param field   path of the offending field, for example {@code email}
 * @param message why it was rejected
 */
public record ValidationError(String field, String message) {
}
