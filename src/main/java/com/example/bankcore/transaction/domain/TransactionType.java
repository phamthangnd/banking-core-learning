package com.example.bankcore.transaction.domain;

/**
 * Kinds of money movement.
 *
 * <p>Each shape is different, and the database enforces it: a deposit has a target and no source,
 * a withdrawal a source and no target, a transfer both, and they must differ.
 */
public enum TransactionType {

    /** Money enters the bank and lands on one account. */
    DEPOSIT,

    /** Money leaves one account and the bank. */
    WITHDRAWAL,

    /** Money moves between two accounts of this bank. */
    TRANSFER;

    public boolean hasSource() {
        return this != DEPOSIT;
    }

    public boolean hasTarget() {
        return this != WITHDRAWAL;
    }
}
