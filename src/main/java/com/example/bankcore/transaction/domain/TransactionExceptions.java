package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

import java.util.UUID;

/** Failures of the transaction module. */
public final class TransactionExceptions {

    private TransactionExceptions() {
    }

    public static class TransactionNotFoundException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public TransactionNotFoundException(UUID id) {
            super(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction %s was not found".formatted(id));
        }

        public TransactionNotFoundException(String reference) {
            super(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction %s was not found".formatted(reference));
        }
    }

    /**
     * A movement the rules reject: an inactive account, a currency mismatch, not enough
     * available funds.
     */
    public static class TransactionRejectedException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public TransactionRejectedException(String message) {
            super(ErrorCode.TRANSACTION_REJECTED, message);
        }
    }
}
