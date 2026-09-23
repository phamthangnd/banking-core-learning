package com.example.bankcore.auth.application;

import com.example.bankcore.auth.config.AuthProperties;
import com.example.bankcore.auth.domain.AuthExceptions;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Rules a new password must satisfy.
 *
 * <p>Length is the requirement that actually matters. Composition rules ("one uppercase, one
 * digit, one symbol") were dropped from the NIST guidance because they push people towards
 * predictable transformations — {@code Password1!} satisfies every one of them.
 *
 * <p>The deny-list here is a token gesture at the real control, which is checking against a
 * breached-password corpus. That belongs to a later phase; what matters now is that the check
 * lives in one place and is enforced on registration, reset and change alike.
 */
@Component
public class PasswordPolicy {

    private static final Set<String> OBVIOUSLY_WEAK = Set.of(
            "password", "passw0rd", "123456789012", "qwertyuiop12", "administrator",
            "letmein12345", "bankcore1234", "welcome12345");

    private final AuthProperties properties;

    public PasswordPolicy(AuthProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws AuthExceptions.WeakPasswordException if the password is not acceptable
     */
    public void validate(String rawPassword, String username, String email) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new AuthExceptions.WeakPasswordException("Password must not be blank");
        }

        int minLength = properties.password().minLength();
        if (rawPassword.length() < minLength) {
            throw new AuthExceptions.WeakPasswordException(
                    "Password must be at least %d characters long".formatted(minLength));
        }

        String lower = rawPassword.toLowerCase(Locale.ROOT);

        if (OBVIOUSLY_WEAK.contains(lower)) {
            throw new AuthExceptions.WeakPasswordException("Password is too common");
        }

        // A password containing the account name is guessable by anyone who knows the account.
        if (username != null && !username.isBlank() && lower.contains(username.toLowerCase(Locale.ROOT))) {
            throw new AuthExceptions.WeakPasswordException("Password must not contain the username");
        }

        if (email != null && email.contains("@")) {
            String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (localPart.length() >= 4 && lower.contains(localPart)) {
                throw new AuthExceptions.WeakPasswordException("Password must not contain the email address");
            }
        }
    }
}
