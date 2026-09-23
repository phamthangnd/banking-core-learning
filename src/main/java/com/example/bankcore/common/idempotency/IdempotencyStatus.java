package com.example.bankcore.common.idempotency;

/** Whether the first attempt under a key is still running or has produced its effect. */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
