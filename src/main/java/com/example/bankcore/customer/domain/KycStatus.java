package com.example.bankcore.customer.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Know Your Customer state.
 *
 * <pre>
 *   PENDING ──▶ VERIFIED
 *      │            │
 *      ▼            ▼
 *   REJECTED ──▶ PENDING (resubmission)
 * </pre>
 *
 * <p>KYC is a regulatory obligation, not a formality: a bank must establish who a customer is
 * before it lets them hold an account. In this codebase that shows up as a hard rule — an account
 * cannot be activated for a customer who is not {@link #VERIFIED}.
 *
 * <p>A verified customer can be sent back to {@link #PENDING} for periodic re-verification, which
 * real anti-money-laundering rules require; the decision is never silently undone.
 */
public enum KycStatus {

    /** Submitted or not yet reviewed. The default for a new customer. */
    PENDING,

    /** Identity established. Accounts may be activated. */
    VERIFIED,

    /** Review failed. The customer may resubmit, which puts them back in {@link #PENDING}. */
    REJECTED;

    public Set<KycStatus> allowedNextStates() {
        return switch (this) {
            case PENDING -> EnumSet.of(VERIFIED, REJECTED);
            case VERIFIED -> EnumSet.of(PENDING);
            case REJECTED -> EnumSet.of(PENDING);
        };
    }

    public boolean canTransitionTo(KycStatus target) {
        return target != null && allowedNextStates().contains(target);
    }

    public boolean isVerified() {
        return this == VERIFIED;
    }
}
