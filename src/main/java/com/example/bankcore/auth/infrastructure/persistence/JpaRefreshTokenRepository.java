package com.example.bankcore.auth.infrastructure.persistence;

import com.example.bankcore.auth.domain.RefreshToken;
import com.example.bankcore.auth.domain.RefreshTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository tokens;

    public JpaRefreshTokenRepository(RefreshTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken token) {
        RefreshTokenEntity entity = tokens.findById(token.id())
                .map(existing -> {
                    existing.applyState(token);
                    return existing;
                })
                .orElseGet(() -> RefreshTokenEntity.fromDomain(token));

        return tokens.save(entity).toDomain();
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    @Override
    public Optional<RefreshToken> findById(UUID id) {
        return tokens.findById(id).map(RefreshTokenEntity::toDomain);
    }

    @Override
    @Transactional
    public int revokeAllForUser(UUID userId, Instant now) {
        return tokens.revokeAllForUser(userId, now);
    }
}
