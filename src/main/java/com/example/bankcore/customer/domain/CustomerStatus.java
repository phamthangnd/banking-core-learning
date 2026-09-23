package com.example.bankcore.customer.domain;

/**
 * Lifecycle of a customer.
 *
 * <p>A customer is never physically removed: closing is a state change, so history, audit trail
 * and any future account or transaction references stay intact (CLAUDE.md section 5).
 */
public enum CustomerStatus {

    /** Normal customer, may be read and updated. */
    ACTIVE,

    /** Closed customer, kept for history. Read-only. */
    CLOSED;

    public boolean isClosed() {
        return this == CLOSED;
    }
}
