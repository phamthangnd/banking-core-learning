package com.example.bankcore.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes bearer secrets (refresh and password-reset tokens) for storage.
 *
 * <p>Why SHA-256 here and BCrypt for passwords: BCrypt is deliberately slow to make guessing a
 * human-chosen password expensive. These tokens are 256 bits of output from a cryptographically
 * secure random generator — guessing is not the threat, so the slow hash would only cost
 * latency on every refresh. The property that matters is the same: a stolen database dump
 * contains no usable token.
 *
 * <p>The comparison is by hash equality on an indexed column, so no timing-safe compare is
 * needed: the attacker never learns anything from a lookup miss.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    /** Returns the lower-case hex SHA-256 of the raw token (64 characters). */
    public static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the platform; this cannot happen on a valid JVM.
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
