package com.example.bankcore.transaction.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a financial transaction.
 *
 * <pre>
 *   PENDING ──▶ POSTED ──▶ REVERSED
 *      │
 *      └──────▶ FAILED
 * </pre>
 *
 * <p>Banking rule: a posted transaction is an immutable business record. It is never edited or
 * deleted; a correction is expressed by a compensating entry, which moves the original to
 * {@link #REVERSED}. {@link #FAILED} and {@link #REVERSED} are terminal.
 *
 * <p>Java note: the transition table lives in a {@code switch} expression over {@code this}.
 * The compiler checks that every constant is handled, so adding a new status without deciding
 * its transitions is a compile error rather than a runtime surprise.
 */
public enum TransactionStatus {

    /** Accepted for processing, no money has moved yet. */
    PENDING,

    /** Money has moved and the entries are recorded in the ledger. */
    POSTED,

    /** Never applied; no ledger impact. */
    FAILED,

    /** Applied, then compensated by a reversal transaction. */
    REVERSED;

    /**
     * States reachable from this one. Returns a fresh mutable set, so callers cannot corrupt
     * the transition table.
     */
    public Set<TransactionStatus> allowedNextStates() {
        return switch (this) {
            case PENDING -> EnumSet.of(POSTED, FAILED);
            case POSTED -> EnumSet.of(REVERSED);
            case FAILED, REVERSED -> EnumSet.noneOf(TransactionStatus.class);
        };
    }

    public boolean isTerminal() {
        return allowedNextStates().isEmpty();
    }

    public boolean canTransitionTo(TransactionStatus target) {
        return target != null && allowedNextStates().contains(target);
    }

    /**
     * Returns {@code target} if the move is legal.
     *
     * @throws IllegalTransactionTransitionException if the move is not part of the lifecycle
     */
    public TransactionStatus transitionTo(TransactionStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalTransactionTransitionException(this, target);
        }
        return target;
    }
}
