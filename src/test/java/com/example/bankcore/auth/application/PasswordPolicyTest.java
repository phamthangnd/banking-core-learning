package com.example.bankcore.auth.application;

import com.example.bankcore.auth.config.AuthProperties;
import com.example.bankcore.auth.domain.AuthExceptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(new AuthProperties(
            new AuthProperties.Jwt("", "bankcore", Duration.ofMinutes(15)),
            new AuthProperties.RefreshToken(Duration.ofDays(30)),
            new AuthProperties.PasswordReset(Duration.ofMinutes(30)),
            new AuthProperties.Lockout(5, Duration.ofMinutes(15)),
            new AuthProperties.RateLimit(10, Duration.ofMinutes(1)),
            new AuthProperties.Password(12)));

    @Test
    void shouldAcceptALongPassphrase() {
        assertThatCode(() -> policy.validate("correct horse battery staple", "alice", "alice@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTooShort() {
        assertThatThrownBy(() -> policy.validate("Short1!", "alice", "alice@example.com"))
                .isInstanceOf(AuthExceptions.WeakPasswordException.class)
                .hasMessageContaining("12");
    }

    @Test
    void shouldRejectBlank() {
        assertThatThrownBy(() -> policy.validate("   ", "alice", "alice@example.com"))
                .isInstanceOf(AuthExceptions.WeakPasswordException.class);
    }

    @Test
    void shouldRejectACommonPassword() {
        assertThatThrownBy(() -> policy.validate("passw0rd", "alice", "alice@example.com"))
                .isInstanceOf(AuthExceptions.WeakPasswordException.class);
    }

    @Test
    void shouldRejectAPasswordContainingTheUsername() {
        assertThatThrownBy(() -> policy.validate("my-alice-password", "alice", "alice@example.com"))
                .isInstanceOf(AuthExceptions.WeakPasswordException.class)
                .hasMessageContaining("username");
    }

    @Test
    void shouldRejectAPasswordContainingTheEmailLocalPart() {
        assertThatThrownBy(() -> policy.validate("thomas-anderson-1999", "neo", "thomas@example.com"))
                .isInstanceOf(AuthExceptions.WeakPasswordException.class)
                .hasMessageContaining("email");
    }
}
