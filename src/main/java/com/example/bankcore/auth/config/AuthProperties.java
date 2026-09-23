package com.example.bankcore.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Authentication settings, bound from {@code bankcore.auth.*}.
 *
 * <p>The defaults encode deliberate security trade-offs, each documented below. They are
 * configuration rather than constants so an operator can tighten them without a release.
 *
 * @param jwt           access-token settings
 * @param refreshToken  refresh-token settings
 * @param passwordReset password-reset settings
 * @param lockout       account-lockout settings
 * @param rateLimit     per-client throttling of authentication endpoints
 * @param password      password policy
 */
@ConfigurationProperties(prefix = "bankcore.auth")
@Validated
public record AuthProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue RefreshToken refreshToken,
        @DefaultValue PasswordReset passwordReset,
        @DefaultValue Lockout lockout,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Password password
) {

    /**
     * @param secret  HMAC key, at least 32 characters (256 bits for HS256). Supplied by the
     *                environment; blank is allowed only outside production, where a random key
     *                is generated at startup.
     * @param issuer  {@code iss} claim, so a token from another system is rejected
     * @param ttl     how long an access token stays valid. Short on purpose: an access token is
     *                not checked against the database on every request, so its lifetime is the
     *                window in which a revoked user can still act.
     */
    public record Jwt(
            @DefaultValue("") String secret,
            @DefaultValue("bankcore") @NotBlank String issuer,
            @DefaultValue("15m") Duration ttl
    ) {
    }

    /**
     * @param ttl how long a refresh token remains usable. Long-lived, but single-use: every
     *            refresh rotates it, and presenting a rotated token revokes the whole chain.
     */
    public record RefreshToken(@DefaultValue("30d") Duration ttl) {
    }

    /** @param ttl lifetime of a password-reset token; short, because it is a bearer secret. */
    public record PasswordReset(@DefaultValue("30m") Duration ttl) {
    }

    /**
     * @param maxAttempts consecutive failed logins before the account locks
     * @param duration    how long the lock lasts; it expires by itself, so a locked-out user is
     *                    not a support ticket and an attacker gains nothing permanent
     */
    public record Lockout(
            @Min(1) @DefaultValue("5") int maxAttempts,
            @DefaultValue("15m") Duration duration
    ) {
    }

    /**
     * @param maxAttempts requests allowed per window, per client and endpoint
     * @param window      length of the window
     */
    public record RateLimit(
            @Min(1) @DefaultValue("10") int maxAttempts,
            @DefaultValue("1m") Duration window
    ) {
    }

    /**
     * @param minLength minimum password length. Length beats composition rules: NIST dropped
     *                  the "one uppercase, one symbol" requirements because they push people
     *                  towards predictable substitutions.
     */
    public record Password(@Min(8) @DefaultValue("12") int minLength) {
    }
}
