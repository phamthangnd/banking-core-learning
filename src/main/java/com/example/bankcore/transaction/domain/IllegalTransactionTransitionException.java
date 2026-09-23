package com.example.bankcore.transaction.domain;

/**
 * Thrown when a transaction is asked to move into a state it cannot legally reach.
 *
 * <p>A financial record must never end up in an impossible state, so an invalid transition is a
 * hard failure rather than a silently ignored no-op (CLAUDE.md section 3).
 */
public class IllegalTransactionTransitionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public IllegalTransactionTransitionException(TransactionStatus from, TransactionStatus to) {
        super("illegal transaction transition: %s -> %s".formatted(from, to));
    }
}
