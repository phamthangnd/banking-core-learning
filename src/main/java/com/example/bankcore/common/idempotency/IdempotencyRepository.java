package com.example.bankcore.common.idempotency;

import java.util.Optional;

/** Persistence port for idempotency keys. */
public interface IdempotencyRepository {

    /**
     * Claims a key, committing immediately in its own transaction.
     *
     * <p>Must return empty when the key already exists. The uniqueness is decided by the
     * database, not by a check in application code: two concurrent retries both reach this
     * point, and only an atomic insert can pick a winner.
     */
    Optional<IdempotencyRecord> claim(IdempotencyRecord record);

    Optional<IdempotencyRecord> find(String scope, String key);

    IdempotencyRecord complete(IdempotencyRecord record);

    /** Releases a claim whose operation failed, so the client may legitimately retry. */
    void release(IdempotencyRecord record);
}
