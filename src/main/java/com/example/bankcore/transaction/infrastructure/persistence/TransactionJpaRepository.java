package com.example.bankcore.transaction.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID>,
        JpaSpecificationExecutor<TransactionEntity> {

    Optional<TransactionEntity> findByReference(String reference);

    @Query(value = "select nextval('transaction_reference_seq')", nativeQuery = true)
    long nextReferenceSequence();

    /**
     * The first keyset page of an account's history.
     *
     * <p>Split from {@link #findAfter} rather than passing a null cursor: PostgreSQL cannot infer
     * the type of a null bind parameter used in a comparison, and the query fails with
     * "could not determine data type of parameter". The same lesson as the optional filters in
     * Phase 02 — two clear queries beat one with a null-handling trick.
     */
    @Query("""
            select t from TransactionEntity t
             where t.sourceAccountId = :accountId or t.targetAccountId = :accountId
             order by t.occurredAt asc, t.id asc
            """)
    java.util.List<TransactionEntity> findFirstPage(
            @org.springframework.data.repository.query.Param("accountId") java.util.UUID accountId,
            org.springframework.data.domain.Pageable limit);

    /**
     * The next keyset page, strictly after {@code (afterTime, afterId)}.
     *
     * <p>The id is the tiebreaker: rows sharing a timestamp would otherwise be skipped or repeated
     * at a batch boundary.
     */
    @Query("""
            select t from TransactionEntity t
             where (t.sourceAccountId = :accountId or t.targetAccountId = :accountId)
               and (t.occurredAt > :afterTime
                    or (t.occurredAt = :afterTime and t.id > :afterId))
             order by t.occurredAt asc, t.id asc
            """)
    java.util.List<TransactionEntity> findAfter(
            @org.springframework.data.repository.query.Param("accountId") java.util.UUID accountId,
            @org.springframework.data.repository.query.Param("afterTime") java.time.Instant afterTime,
            @org.springframework.data.repository.query.Param("afterId") java.util.UUID afterId,
            org.springframework.data.domain.Pageable limit);
}
