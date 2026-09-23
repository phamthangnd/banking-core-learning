package com.example.bankcore.common.api;

public record ApiResponse<T>(
        boolean success,
        T data,
        String traceId
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }
}
