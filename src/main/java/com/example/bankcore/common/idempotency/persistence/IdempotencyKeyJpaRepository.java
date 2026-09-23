package com.example.bankcore.common.idempotency.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    Optional<IdempotencyKeyEntity> findByScopeAndIdempotencyKey(String scope, String idempotencyKey);

    /**
     * Claims a key, or does nothing if it is already taken.
     *
     * <p>{@code ON CONFLICT DO NOTHING} rather than an insert wrapped in a try/catch: catching a
     * constraint violation does not un-mark the transaction as rollback-only, so the commit that
     * follows fails with {@code UnexpectedRollbackException}. Letting the database decide without
     * raising keeps the losing attempt's transaction usable.
     *
     * @return 1 when this caller claimed the key, 0 when someone else already had it
     */
    @Modifying
    @Query(value = """
            insert into idempotency_keys
                (id, scope, idempotency_key, request_fingerprint, status, created_at)
            values (:id, :scope, :key, :fingerprint, 'IN_PROGRESS', :createdAt)
            on conflict (scope, idempotency_key) do nothing
            """, nativeQuery = true)
    int claim(@Param("id") UUID id,
              @Param("scope") String scope,
              @Param("key") String key,
              @Param("fingerprint") String fingerprint,
              @Param("createdAt") Instant createdAt);
}
