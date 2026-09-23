package com.example.bankcore.common.idempotency.persistence;

import com.example.bankcore.common.idempotency.IdempotencyRecord;
import com.example.bankcore.common.idempotency.IdempotencyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adapter for idempotency keys.
 *
 * <p>Every method commits in its own transaction ({@link Propagation#REQUIRES_NEW}), and that is
 * the whole design:
 *
 * <ul>
 *   <li>the <b>claim</b> must be visible to other requests <em>before</em> the slow work starts,
 *       otherwise two concurrent retries would both pass and both move the money;</li>
 *   <li>the <b>release</b> after a failure must survive the rollback that caused it, or the key
 *       would stay claimed forever and the client could never retry — the same pattern as the
 *       failed-login counter in Phase 03 and the failed transaction in Phase 05.</li>
 * </ul>
 *
 * <p>Uniqueness is enforced by the unique index, not by a look-up first: between a check and an
 * insert there is a window, and concurrent retries live exactly in that window.
 */
@Repository
public class JpaIdempotencyRepository implements IdempotencyRepository {

    private final IdempotencyKeyJpaRepository keys;

    public JpaIdempotencyRepository(IdempotencyKeyJpaRepository keys) {
        this.keys = keys;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyRecord> claim(IdempotencyRecord record) {
        int claimed = keys.claim(record.id(), record.scope(), record.key(),
                record.requestFingerprint(), record.createdAt());

        // Zero rows means someone else got there first. That is the mechanism working.
        return claimed == 1 ? Optional.of(record) : Optional.empty();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyRecord> find(String scope, String key) {
        return keys.findByScopeAndIdempotencyKey(scope, key).map(IdempotencyKeyEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord complete(IdempotencyRecord record) {
        IdempotencyKeyEntity entity = keys.findById(record.id())
                .orElseGet(() -> IdempotencyKeyEntity.fromDomain(record));
        entity.applyState(record);
        return keys.save(entity).toDomain();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(IdempotencyRecord record) {
        keys.findById(record.id()).ifPresent(keys::delete);
    }
}
