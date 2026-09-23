package com.example.bankcore.auth.application;

import com.example.bankcore.auth.config.AuthProperties;
import com.example.bankcore.auth.domain.RefreshTokenRepository;
import com.example.bankcore.user.domain.User;
import com.example.bankcore.user.domain.UserRepository;
import com.example.bankcore.user.domain.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Writes security facts that must survive the failure that caused them.
 *
 * <p>This exists because of a trap that is easy to miss and expensive to get wrong. A failed
 * login ends with an exception, and an unchecked exception rolls the transaction back — taking
 * the incremented failure counter with it. The account would never lock, and the lockout
 * protection would silently do nothing. The same applies to revoking every session after
 * detecting a replayed refresh token: the revocation must outlive the rejection.
 *
 * <p>{@link Propagation#REQUIRES_NEW} suspends the caller's transaction and commits these writes
 * in one of their own. It has to be a separate bean: Spring's transaction support is proxy-based,
 * so calling a {@code REQUIRES_NEW} method on {@code this} from inside the same class would
 * quietly do nothing at all.
 */
@Component
public class SecurityStateRecorder {

    private static final Logger log = LoggerFactory.getLogger(SecurityStateRecorder.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuthProperties properties;
    private final Clock clock;

    public SecurityStateRecorder(UserRepository users, RefreshTokenRepository refreshTokens,
                                 AuthProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Records one failed login attempt, locking the account at the configured threshold.
     *
     * @return {@code true} if this attempt locked the account
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailedLogin(User user) {
        User failed = user.withFailedLogin(properties.lockout().maxAttempts(),
                properties.lockout().duration(), clock.instant());

        users.save(failed);

        boolean locked = failed.status() == UserStatus.LOCKED;
        log.info("Failed login recorded: id={} locked={}", user.id(), locked);
        return locked;
    }

    /** Revokes every active session of a user, committing independently of the caller. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllSessions(UUID userId) {
        return refreshTokens.revokeAllForUser(userId, clock.instant());
    }
}
