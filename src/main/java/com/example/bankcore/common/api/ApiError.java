package com.example.bankcore.common.api;

import java.util.List;
import java.util.Objects;

/**
 * Error payload of the standard API envelope.
 *
 * @param code    stable machine-readable code; clients branch on this
 * @param message human-readable explanation, safe to display
 * @param details field-level validation failures; {@code null} when not applicable
 */
public record ApiError(ErrorCode code, String message, List<ValidationError> details) {

    public ApiError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        details = details == null ? null : List.copyOf(details);
    }

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError validation(String message, List<ValidationError> details) {
        return new ApiError(ErrorCode.VALIDATION_FAILED, message, details);
    }
}
