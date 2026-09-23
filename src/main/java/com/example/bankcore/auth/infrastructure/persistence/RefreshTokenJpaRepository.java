package com.example.bankcore.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Revokes every still-active token of a user in one statement.
     *
     * <p>A bulk update bypasses the persistence context, so entities already loaded in this
     * transaction would keep their stale state — {@code clearAutomatically} tells Hibernate to
     * flush first and drop them, and {@code flushAutomatically} keeps pending changes from being
     * overwritten. The alternative, loading every token and updating it one by one, is a loop of
     * statements for something the database does in one.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :now
             where t.userId = :userId
               and t.revokedAt is null
               and t.expiresAt > :now
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
