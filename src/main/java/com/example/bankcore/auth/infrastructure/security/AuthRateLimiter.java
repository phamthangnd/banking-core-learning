package com.example.bankcore.auth.infrastructure.security;

import com.example.bankcore.auth.config.AuthProperties;
import com.example.bankcore.auth.domain.AuthExceptions;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for authentication endpoints (CLAUDE.md section 4).
 *
 * <p>Account lockout stops guessing against <em>one</em> account; this stops the other shapes of
 * the same attack — spraying one common password across many accounts, or hammering the refresh
 * and reset endpoints, neither of which touches a login counter.
 *
 * <p>Honest limitations, both deliberate for this phase:
 * <ul>
 *   <li><b>Per instance.</b> The counters live in this JVM's heap, so two instances allow twice
 *       the traffic. Phase 09 moves them to Redis, which is where a shared counter belongs.</li>
 *   <li><b>Fixed window.</b> A client can spend its whole budget at the end of one window and
 *       again at the start of the next. A sliding window or token bucket smooths that; the fixed
 *       window is chosen here because it is obvious, and being obvious is worth more than being
 *       precise for a first line of defence.</li>
 * </ul>
 *
 * <p>Keys are scoped per endpoint and per client so that a burst of logins cannot consume the
 * budget for password resets.
 */
@Component
public class AuthRateLimiter {


    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AuthProperties properties;
    private final Clock clock;

    protected AuthRateLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Counts one attempt against {@code action} by {@code client}.
     *
     * @throws AuthExceptions.TooManyAttemptsException when the window's budget is exhausted
     */
    public void checkAllowed(String action, String client) {
        String key = action + "|" + client;
        Instant now = clock.instant();
        int max = properties.rateLimit().maxAttempts();

        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || current.hasExpired(now)) {
                return new Window(now.plus(properties.rateLimit().window()));
            }
            return current;
        });

        if (window.count().incrementAndGet() > max) {
            throw new AuthExceptions.TooManyAttemptsException();
        }

        // Opportunistic cleanup: without it the map grows with every distinct client seen.
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().hasExpired(now));
        }
    }

    /** Clears the counter after a legitimate success, so a valid user is not punished. */
    public void reset(String action, String client) {
        windows.remove(action + "|" + client);
    }

    private record Window(Instant expiresAt, AtomicInteger count) {

        Window(Instant expiresAt) {
            this(expiresAt, new AtomicInteger());
        }

        boolean hasExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
