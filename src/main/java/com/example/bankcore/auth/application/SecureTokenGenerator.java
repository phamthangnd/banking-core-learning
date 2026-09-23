package com.example.bankcore.auth.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates opaque bearer tokens (refresh and password-reset).
 *
 * <p>256 bits from {@link SecureRandom}, URL-safe Base64, no padding. Two properties matter:
 * the generator is cryptographically secure ({@code java.util.Random} is not — its state can be
 * recovered from a couple of outputs), and the value carries no structure an attacker could
 * exploit or a developer could accidentally rely on.
 */
@Component
public class SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
