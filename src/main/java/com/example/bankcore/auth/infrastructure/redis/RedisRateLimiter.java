package com.example.bankcore.auth.infrastructure.redis;

import com.example.bankcore.auth.config.AuthProperties;
import com.example.bankcore.auth.domain.AuthExceptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limiting across instances.
 *
 * <p>The heap-based limiter from Phase 03 counts per instance, so two instances allow twice the
 * traffic and an attacker only has to spread requests across them. A counter in Redis is shared,
 * which is what the control was supposed to be all along.
 *
 * <p>{@code INCR} returns the new value atomically, so there is no read-modify-write race between
 * concurrent requests. The expiry is set only on the first increment of a window — resetting it
 * on every request would extend the window forever under sustained load and never let it reset.
 *
 * <p>If Redis is unreachable the request is allowed through. That is a deliberate choice: a rate
 * limiter is a protection, not an authorisation, and failing every login because the cache is
 * down would turn a degraded dependency into a full outage. Lockout and password hashing still
 * apply.
 */
@Component
@Primary
@ConditionalOnProperty(name = "bankcore.redis.enabled", havingValue = "true")
public class RedisRateLimiter extends com.example.bankcore.auth.infrastructure.security.AuthRateLimiter {

    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public RedisRateLimiter(StringRedisTemplate redis, AuthProperties properties, java.time.Clock clock) {
        super(properties, clock);
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void checkAllowed(String action, String client) {
        String key = KEY_PREFIX + action + ":" + client;
        Duration window = properties.rateLimit().window();

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }

            if (count != null && count > properties.rateLimit().maxAttempts()) {
                throw new AuthExceptions.TooManyAttemptsException();
            }
        } catch (AuthExceptions.TooManyAttemptsException rejected) {
            throw rejected;
        } catch (RuntimeException redisUnavailable) {
            // Fail open: lockout and hashing still protect the account.
            org.slf4j.LoggerFactory.getLogger(RedisRateLimiter.class)
                    .warn("Rate limiter unavailable, allowing the request: {}", redisUnavailable.getMessage());
        }
    }

    @Override
    public void reset(String action, String client) {
        try {
            redis.delete(KEY_PREFIX + action + ":" + client);
        } catch (RuntimeException ignored) {
            // Nothing to do: the window expires on its own.
        }
    }
}
