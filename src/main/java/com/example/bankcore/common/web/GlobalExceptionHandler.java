package com.example.bankcore.common.web;

import com.example.bankcore.common.api.ApiError;
import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.api.ValidationError;
import com.example.bankcore.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Comparator;
import java.util.List;

/**
 * Translates exceptions into the standard API envelope.
 *
 * <p>This is the only place that knows how a failure becomes an HTTP status, which keeps
 * controllers free of try/catch and keeps domain code free of web types.
 *
 * <p>Two rules matter more than the mapping itself:
 * <ul>
 *   <li>a failure is never swallowed — everything either maps to a deliberate status or falls
 *       through to 500 and is logged;</li>
 *   <li>internal details (stack traces, SQL, class names) never reach the client; they are
 *       logged against the trace id that the client did receive.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation failures on a request body or parameter object. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        List<ValidationError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(
                        error.getField(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message))
                .toList();

        log.debug("Request validation failed with {} field error(s)", details.size());

        return respond(HttpStatus.BAD_REQUEST,
                ApiError.validation("Request validation failed", details));
    }

    /** Body that could not be parsed at all: broken JSON, wrong types, missing body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
        // The parser message can quote the payload, so it is logged but not returned.
        log.debug("Unreadable request body: {}", exception.getMostSpecificCause().getMessage());

        return respond(HttpStatus.BAD_REQUEST,
                ApiError.of(ErrorCode.MALFORMED_REQUEST, "Request body could not be parsed"));
    }

    /** Path or query parameter of the wrong type, for example a malformed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        var detail = new ValidationError(exception.getName(), "has an invalid format");

        return respond(HttpStatus.BAD_REQUEST,
                ApiError.validation("Request validation failed", List.of(detail)));
    }

    /** Every domain failure, mapped through the error code's category. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        ErrorCode code = exception.errorCode();
        HttpStatus status = statusFor(code);

        log.info("Business rule rejected the request: code={} status={}", code, status.value());

        return respond(status, ApiError.of(code, exception.getMessage()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException exception) {
        return respond(HttpStatus.NOT_FOUND,
                ApiError.of(ErrorCode.CUSTOMER_NOT_FOUND, "Resource not found"));
    }

    /** Anything unforeseen: logged in full, reported as an opaque 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled exception while processing a request", exception);

        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiError.of(ErrorCode.INTERNAL_ERROR, "Unexpected server error"));
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code.category()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status).body(ApiResponse.failure(error));
    }
}
