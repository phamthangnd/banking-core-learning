package com.example.bankcore.ledger.domain;

/**
 * The bank's own side of a movement that crosses its boundary.
 *
 * <p>A deposit credits a customer — but double-entry needs the other half, and the money came
 * from outside. These are the bank's internal positions that absorb it, so every transaction has
 * two balanced legs and the ledger invariant holds for deposits and withdrawals too.
 */
public enum SystemAccount {

    /** Physical or counter cash handed over at a branch. */
    CASH,

    /** Value in transit to or from another institution. */
    CLEARING
}
