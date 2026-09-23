package com.example.bankcore.audit.domain;

/**
 * How an audited action ended.
 *
 * <p>{@link #DENIED} is kept apart from {@link #FAILURE} on purpose: a permission refusal is a
 * security signal — a known account reaching for something outside its role — while a failure is
 * usually just a rule saying no.
 */
public enum AuditOutcome {
    SUCCESS,
    FAILURE,
    DENIED
}
