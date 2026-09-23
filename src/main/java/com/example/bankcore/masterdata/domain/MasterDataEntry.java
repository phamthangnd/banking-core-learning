package com.example.bankcore.masterdata.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One piece of reference data: a branch, a document type, a currency, a category.
 *
 * <p>Retiring an entry sets {@code active = false} rather than deleting it. Existing records may
 * still point at it, and a report from last year has to keep making sense.
 *
 * @param type        which catalogue this belongs to
 * @param code        stable machine-readable key; upper case, never renamed
 * @param label       human-readable name, free to change
 * @param sortOrder   presentation order, so clients never sort by label and get it wrong per locale
 * @param active      whether it may still be chosen
 */
public record MasterDataEntry(
        UUID id,
        String type,
        String code,
        String label,
        String description,
        int sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public MasterDataEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        type = normalizeCode(type, "type");
        code = normalizeCode(code, "code");
        label = requireText(label, "label");
    }

    public static MasterDataEntry create(UUID id, String type, String code, String label,
                                         String description, int sortOrder, Instant now) {
        return new MasterDataEntry(id, type, code, label, description, sortOrder, true, now, now);
    }

    /** The code is identity; only the presentation and the active flag may change. */
    public MasterDataEntry update(String newLabel, String newDescription, int newSortOrder,
                                  boolean newActive, Instant now) {
        return new MasterDataEntry(id, type, code, newLabel, newDescription, newSortOrder,
                newActive, createdAt, now);
    }

    private static String normalizeCode(String value, String field) {
        String normalized = requireText(value, field).strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException(field + " may contain only letters, digits and underscores");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
