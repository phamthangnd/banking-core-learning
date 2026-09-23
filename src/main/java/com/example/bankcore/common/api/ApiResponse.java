package com.example.bankcore.common.api;

import com.example.bankcore.common.trace.CorrelationId;

import java.time.Instant;
import java.util.Map;

/**
 * The single response envelope for every endpoint (CLAUDE.md section 6).
 *
 * <p>Success:
 * <pre>{@code {"success":true,"data":{...},"timestamp":"...","traceId":"..."}}</pre>
 *
 * <p>Failure:
 * <pre>{@code {"success":false,"error":{"code":"...","message":"...","details":[...]},
 *             "timestamp":"...","traceId":"..."}}</pre>
 *
 * <p>One shape for both outcomes means clients parse one structure, and {@code traceId} is
 * always present so any response can be traced back to its log lines. Null fields are omitted
 * from the JSON ({@code spring.jackson.default-property-inclusion: non_null}).
 *
 * <p>The HTTP status still carries the outcome; {@code success} only makes it explicit in the
 * body for clients that log payloads without statuses.
 *
 * @param <T> type of the payload
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        Map<String, Object> metadata,
        ApiError error,
        Instant timestamp,
        String traceId
) {

    public ApiResponse {
        metadata = metadata == null ? null : Map.copyOf(metadata);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now(), CorrelationId.currentOrNull());
    }

    /** Success with metadata — pagination, counts, or anything a client needs besides the data. */
    public static <T> ApiResponse<T> success(T data, Map<String, Object> metadata) {
        return new ApiResponse<>(true, data, metadata, null, Instant.now(), CorrelationId.currentOrNull());
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return new ApiResponse<>(false, null, null, error, Instant.now(), CorrelationId.currentOrNull());
    }
}
