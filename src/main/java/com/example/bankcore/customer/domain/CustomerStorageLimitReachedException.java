package com.example.bankcore.customer.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

/**
 * Raised when the in-memory store is full.
 *
 * <p>Phase 01 keeps customers in heap memory, so the store must have a hard ceiling: an
 * unbounded in-memory collection is an out-of-memory incident waiting for enough traffic.
 */
public class CustomerStorageLimitReachedException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public CustomerStorageLimitReachedException(int limit) {
        super(ErrorCode.STORAGE_LIMIT_REACHED,
                "Customer storage limit of %d records reached".formatted(limit));
    }
}
