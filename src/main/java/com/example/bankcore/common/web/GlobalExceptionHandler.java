package com.example.bankcore.common.web;

import com.example.bankcore.common.api.ApiError;
import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.api.ValidationError;
import com.example.bankcore.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
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

    /**
     * Bean Validation failures on a request body, a query-parameter object or a form.
     *
     * <p>{@code MethodArgumentNotValidException} extends {@link BindException}, so handling the
     * parent covers both the {@code @RequestBody} and the {@code @ModelAttribute} cases.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(BindException exception) {
        List<ValidationError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toValidationError)
                .sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message))
                .toList();

        log.debug("Request validation failed with {} field error(s): {}",
                details.size(), exception.getMessage());

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

    /**
     * Spring Security's {@code StrictHttpFirewall} rejected the request before it reached the
     * application — a header with control characters, a suspicious URL encoding, and similar.
     *
     * <p>This is a malformed, usually hostile request, not a server fault: it answers 400, and
     * it is logged at WARN without a stack trace, because an attacker must not be able to fill
     * the error log by looping over bad requests.
     */
    @ExceptionHandler(RequestRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleRejectedRequest(RequestRejectedException exception) {
        log.warn("Request rejected by the HTTP firewall: {}", exception.getMessage());

        return respond(HttpStatus.BAD_REQUEST,
                ApiError.of(ErrorCode.MALFORMED_REQUEST, "Request was rejected as malformed"));
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

    /**
     * The database rejected a write that violates a constraint.
     *
     * <p>The unique index on {@code email} is the real guarantee: an application-level check can
     * always be raced by a second concurrent request, and only the database can settle it. The
     * check in the service is a friendly fast path, not the enforcement.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException exception) {
        // The driver message can quote column values, so it is logged, never returned.
        log.warn("Database constraint rejected a write: {}", exception.getMostSpecificCause().getMessage());

        return respond(HttpStatus.CONFLICT,
                ApiError.of(ErrorCode.CUSTOMER_EMAIL_ALREADY_USED, "Email address is already registered"));
    }

    /**
     * Two transactions modified the same row; the second one lost.
     *
     * <p>Optimistic locking turns a lost update into a visible 409 instead of silently
     * overwriting the other writer's change. The client's correct response is to re-read and
     * retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException exception) {
        log.warn("Optimistic locking conflict: {}", exception.getMessage());

        return respond(HttpStatus.CONFLICT,
                ApiError.of(ErrorCode.CONCURRENT_MODIFICATION,
                        "The record was modified concurrently, please retry"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException exception) {
        return respond(HttpStatus.NOT_FOUND,
                ApiError.of(ErrorCode.CUSTOMER_NOT_FOUND, "Resource not found"));
    }

    /**
     * A {@code @PreAuthorize} check on a service method refused the call.
     *
     * <p>Denials raised inside the filter chain are handled by {@code RestAccessDeniedHandler};
     * this one comes from method security, travels up through the controller, and would
     * otherwise be swallowed by the catch-all below and reported as a 500 — an authorization
     * failure disguised as a server fault, which is both wrong and alarming.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Access denied by method security: principal={}",
                authentication == null ? "anonymous" : authentication.getName());

        return respond(HttpStatus.FORBIDDEN,
                ApiError.of(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action"));
    }

    /** Anything unforeseen: logged in full, reported as an opaque 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled exception while processing a request", exception);

        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiError.of(ErrorCode.INTERNAL_ERROR, "Unexpected server error"));
    }

    private static ValidationError toValidationError(FieldError error) {
        // A binding failure is a type-conversion error, and Spring's default message for it
        // quotes the target class ("failed to convert ... to type com.example...Foo").
        // That is an internal detail: the client gets a generic message, the log keeps the rest.
        if (error.isBindingFailure()) {
            return new ValidationError(error.getField(), "has an invalid value");
        }

        return new ValidationError(
                error.getField(),
                error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code.category()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status).body(ApiResponse.failure(error));
    }
}
