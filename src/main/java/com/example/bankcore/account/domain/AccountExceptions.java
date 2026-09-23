package com.example.bankcore.account.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

import java.util.UUID;

/** Failures of the account module. */
public final class AccountExceptions {

    private AccountExceptions() {
    }

    public static class AccountNotFoundException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public AccountNotFoundException(UUID id) {
            super(ErrorCode.ACCOUNT_NOT_FOUND, "Account %s was not found".formatted(id));
        }

        public AccountNotFoundException(String accountNumber) {
            super(ErrorCode.ACCOUNT_NOT_FOUND, "Account %s was not found".formatted(accountNumber));
        }
    }

    /** An account lifecycle move that the state machine does not allow. */
    public static class IllegalAccountTransitionException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public IllegalAccountTransitionException(AccountStatus from, AccountStatus to) {
            super(ErrorCode.ACCOUNT_RULE_VIOLATED,
                    "Account status cannot change from %s to %s".formatted(from, to));
        }
    }

    /** A well-formed request that an account rule rejects. */
    public static class AccountRuleViolationException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public AccountRuleViolationException(String message) {
            super(ErrorCode.ACCOUNT_RULE_VIOLATED, message);
        }
    }
}
