package com.example.bankcore.common.idempotency;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

/** Failures of the idempotency mechanism. */
public final class IdempotencyExceptions {

    private IdempotencyExceptions() {
    }

    /**
     * The key was used before with a different request.
     *
     * <p>Answering with the earlier result would be worse than an error: the caller would believe
     * the request it just sent had been carried out.
     */
    public static class KeyConflictException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public KeyConflictException() {
            super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    "This idempotency key was already used with a different request");
        }
    }

    /** A first attempt under this key is still running; retrying now could duplicate the effect. */
    public static class RequestInProgressException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public RequestInProgressException() {
            super(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS,
                    "A request with this idempotency key is still being processed");
        }
    }
}
