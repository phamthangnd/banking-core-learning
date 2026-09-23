package com.example.bankcore.auth.infrastructure.persistence;

import com.example.bankcore.auth.domain.PasswordResetToken;
import com.example.bankcore.auth.domain.PasswordResetTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository tokens;

    public JpaPasswordResetTokenRepository(PasswordResetTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenEntity entity = tokens.findById(token.id())
                .map(existing -> {
                    existing.applyState(token);
                    return existing;
                })
                .orElseGet(() -> PasswordResetTokenEntity.fromDomain(token));

        return tokens.save(entity).toDomain();
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    @Transactional
    public int invalidateAllForUser(UUID userId, Instant now) {
        return tokens.invalidateAllForUser(userId, now);
    }
}
