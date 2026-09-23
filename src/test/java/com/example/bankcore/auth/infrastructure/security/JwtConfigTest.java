package com.example.bankcore.auth.infrastructure.security;

import com.example.bankcore.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The signing-key rules, which are the difference between a secret and a published constant.
 */
class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    private static AuthProperties withSecret(String secret) {
        return new AuthProperties(
                new AuthProperties.Jwt(secret, "bankcore", Duration.ofMinutes(15)),
                new AuthProperties.RefreshToken(Duration.ofDays(30)),
                new AuthProperties.PasswordReset(Duration.ofMinutes(30)),
                new AuthProperties.Lockout(5, Duration.ofMinutes(15)),
                new AuthProperties.RateLimit(10, Duration.ofMinutes(1)),
                new AuthProperties.Password(12));
    }

    private static MockEnvironment profile(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void shouldUseTheConfiguredSecret() {
        var key = config.jwtSigningKey(withSecret("a-secret-that-is-long-enough-for-hs256"), profile("prod"));

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void shouldRefuseToStartWithoutASecretOutsideLocalAndTest() {
        assertThatThrownBy(() -> config.jwtSigningKey(withSecret(""), profile("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BANKCORE_JWT_SECRET");
    }

    @Test
    void shouldGenerateARandomKeyLocally() {
        // Convenience for development only: tokens stop working across restarts, which is
        // harmless, whereas a committed fallback secret would be a published production key.
        var first = config.jwtSigningKey(withSecret(""), profile("local"));
        var second = config.jwtSigningKey(withSecret(""), profile("local"));

        assertThat(first.getEncoded()).hasSize(32);
        assertThat(first.getEncoded()).isNotEqualTo(second.getEncoded());
    }

    @Test
    void shouldRejectAKeyShorterThanTheAlgorithmRequires() {
        assertThatThrownBy(() -> config.jwtSigningKey(withSecret("too-short"), profile("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }
}
