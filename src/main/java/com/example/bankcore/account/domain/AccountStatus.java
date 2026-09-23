package com.example.bankcore.account.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an account.
 *
 * <pre>
 *   PENDING ──▶ ACTIVE ◀──▶ FROZEN
 *      │           │           │
 *      └───────────┴───────────┴──▶ CLOSED
 * </pre>
 *
 * <p>Only an {@link #ACTIVE} account may move money. That single rule is what {@link #canTransact()}
 * expresses, and Phase 05 will lean on it for every deposit, withdrawal and transfer
 * (CLAUDE.md section 3 — closed accounts cannot transact).
 *
 * <p>{@link #FROZEN} is deliberately reversible and {@link #CLOSED} deliberately is not: a freeze
 * is an investigation, a closure is an ending. An account is never deleted, because its history
 * has to remain.
 */
public enum AccountStatus {

    /** Opened but not yet usable; waiting for KYC verification and activation. */
    PENDING,

    /** Fully usable. */
    ACTIVE,

    /** Temporarily blocked — fraud investigation, legal hold. Reversible. */
    FROZEN,

    /** Ended. Terminal: an account is closed, never deleted. */
    CLOSED;

    public Set<AccountStatus> allowedNextStates() {
        return switch (this) {
            case PENDING -> EnumSet.of(ACTIVE, CLOSED);
            case ACTIVE -> EnumSet.of(FROZEN, CLOSED);
            case FROZEN -> EnumSet.of(ACTIVE, CLOSED);
            case CLOSED -> EnumSet.noneOf(AccountStatus.class);
        };
    }

    public boolean canTransitionTo(AccountStatus target) {
        return target != null && allowedNextStates().contains(target);
    }

    /** Whether money may move on an account in this state. */
    public boolean canTransact() {
        return this == ACTIVE;
    }

    public boolean isTerminal() {
        return allowedNextStates().isEmpty();
    }
}
