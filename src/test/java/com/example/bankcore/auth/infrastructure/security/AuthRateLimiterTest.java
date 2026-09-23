package com.example.bankcore.auth.infrastructure.security;

import com.example.bankcore.auth.config.AuthProperties;
import com.example.bankcore.auth.domain.AuthExceptions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    private static final Instant START = Instant.parse("2026-06-15T09:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final AuthRateLimiter limiter = new AuthRateLimiter(properties(3, Duration.ofMinutes(1)), clock);

    private static AuthProperties properties(int maxAttempts, Duration window) {
        return new AuthProperties(
                new AuthProperties.Jwt("", "bankcore", Duration.ofMinutes(15)),
                new AuthProperties.RefreshToken(Duration.ofDays(30)),
                new AuthProperties.PasswordReset(Duration.ofMinutes(30)),
                new AuthProperties.Lockout(5, Duration.ofMinutes(15)),
                new AuthProperties.RateLimit(maxAttempts, window),
                new AuthProperties.Password(12));
    }

    @Test
    void shouldAllowUpToTheLimitThenReject() {
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");

        assertThatThrownBy(() -> limiter.checkAllowed("login", "10.0.0.1"))
                .isInstanceOf(AuthExceptions.TooManyAttemptsException.class);
    }

    @Test
    void shouldCountClientsIndependently() {
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");

        assertThatCode(() -> limiter.checkAllowed("login", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void shouldCountEndpointsIndependently() {
        // Hammering login must not consume the budget for password resets.
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");

        assertThatCode(() -> limiter.checkAllowed("password-reset", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void shouldStartANewWindowAfterItExpires() {
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        assertThatCode(() -> limiter.checkAllowed("login", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void shouldClearTheCounterAfterASuccess() {
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.checkAllowed("login", "10.0.0.1");
        limiter.reset("login", "10.0.0.1");

        assertThatCode(() -> {
            limiter.checkAllowed("login", "10.0.0.1");
            limiter.checkAllowed("login", "10.0.0.1");
            limiter.checkAllowed("login", "10.0.0.1");
        }).doesNotThrowAnyException();
    }

    /** A clock the test moves by hand, so the window behaviour is tested without sleeping. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
