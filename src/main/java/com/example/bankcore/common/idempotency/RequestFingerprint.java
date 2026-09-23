package com.example.bankcore.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Fingerprints a request so a reused key can be told from a genuine retry.
 *
 * <p>Without it, a client that reuses a key for a different transfer would be answered with the
 * <em>previous</em> transfer's result and would believe its new request had been carried out.
 * With it, that case is an error and the client learns about its bug.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    /** SHA-256 of the canonical parts of a request, in the order given. */
    public static String of(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            canonical.append(part == null ? "" : part.toString()).append('|');
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
