package com.example.bankcore.transaction.domain;

/**
 * Side of a double-entry posting.
 *
 * <p>Amounts are always stored unsigned; this direction carries the sign. That keeps
 * "total debits equal total credits" (CLAUDE.md section 3) checkable by summing each side.
 */
public enum TransactionDirection {

    DEBIT,
    CREDIT;

    public TransactionDirection opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
