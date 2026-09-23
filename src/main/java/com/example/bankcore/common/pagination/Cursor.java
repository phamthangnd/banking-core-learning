package com.example.bankcore.common.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Encodes the position of the last row seen, for keyset pagination.
 *
 * <p>A cursor is {@code <instant>|<id>} in URL-safe Base64. The id is the tiebreaker: without it,
 * rows sharing a timestamp would be skipped or repeated at a page boundary.
 *
 * <p>It is opaque to clients on purpose — encoded, not signed. It carries no secret, but making
 * it opaque stops callers building cursors by hand and depending on the sort key, which would
 * freeze an implementation detail into the API.
 */
public record Cursor(Instant timestamp, UUID id) {

    public String encode() {
        String raw = timestamp.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns empty for a missing or malformed cursor — a bad cursor starts from the beginning. */
    public static Optional<Cursor> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator < 0) {
                return Optional.empty();
            }
            return Optional.of(new Cursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1))));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }
}
