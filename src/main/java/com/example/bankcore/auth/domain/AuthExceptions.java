package com.example.bankcore.auth.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

/**
 * Failures of the authentication flows.
 *
 * <p>Note how little these messages say. "Invalid username or password" covers an unknown user,
 * a wrong password and a malformed request alike: distinguishing them would let an attacker
 * enumerate accounts, and the detail is of no use to a legitimate user either. The specifics go
 * to the log, against the request's trace id.
 */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    /** Wrong credentials, or no such user. Deliberately indistinguishable. */
    public static class InvalidCredentialsException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public InvalidCredentialsException() {
            super(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        }
    }

    /** The account exists but cannot authenticate: locked by failed attempts, or disabled. */
    public static class AccountNotActiveException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public AccountNotActiveException(String message) {
            super(ErrorCode.ACCOUNT_NOT_ACTIVE, message);
        }
    }

    /** A refresh or reset token that is unknown, expired, already used or revoked. */
    public static class InvalidTokenException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public InvalidTokenException() {
            super(ErrorCode.INVALID_TOKEN, "Token is invalid or has expired");
        }
    }

    /** Username or email already taken. */
    public static class UserAlreadyExistsException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public UserAlreadyExistsException() {
            super(ErrorCode.USER_ALREADY_EXISTS, "Username or email is already registered");
        }
    }

    /** The proposed password does not meet the policy. */
    public static class WeakPasswordException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public WeakPasswordException(String message) {
            super(ErrorCode.WEAK_PASSWORD, message);
        }
    }

    /** Too many attempts from the same client in the rate-limit window. */
    public static class TooManyAttemptsException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public TooManyAttemptsException() {
            super(ErrorCode.TOO_MANY_REQUESTS, "Too many attempts, please try again later");
        }
    }
}
