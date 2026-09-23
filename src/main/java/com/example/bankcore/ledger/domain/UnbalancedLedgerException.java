package com.example.bankcore.ledger.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

/**
 * The ledger does not balance.
 *
 * <p>This is never a user error and never recoverable at runtime: it means the code that built
 * the entries is wrong. It aborts the transaction rather than letting an unbalanced pair reach
 * the ledger, because an unbalanced ledger cannot be repaired by later entries.
 */
public class UnbalancedLedgerException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public UnbalancedLedgerException(String message) {
        super(ErrorCode.LEDGER_UNBALANCED, message);
    }
}
