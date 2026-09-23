package com.example.bankcore.common.exception;

import com.example.bankcore.common.api.ErrorCode;

import java.util.Objects;

/**
 * Base class for failures that are part of the domain, not bugs.
 *
 * <p>A business exception carries an {@link ErrorCode} so the web layer can translate it into a
 * stable API error without knowing anything about the module that threw it. Domain and
 * application code therefore never import web or HTTP types (CLAUDE.md section 2).
 *
 * <p>Messages are safe to return to clients: they must never contain secrets, tokens or full
 * account data (CLAUDE.md section 4).
 */
public abstract class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
