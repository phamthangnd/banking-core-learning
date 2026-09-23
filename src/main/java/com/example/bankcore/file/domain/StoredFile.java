package com.example.bankcore.file.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadata for one file in object storage.
 *
 * <p>The bytes live in the object store; this record is the permission boundary and the audit
 * trail. Nothing downloads a file without first finding it here, which is what makes access
 * checkable.
 *
 * @param id            identity
 * @param storageKey    key inside the bucket; generated, never derived from the uploaded name
 * @param originalName  sanitised name to offer back on download
 * @param contentType   validated against the category's allow-list
 * @param sizeBytes     actual size of the stored object
 * @param checksum      SHA-256 of the content
 * @param category      what the file is for, which decides its rules
 * @param uploadedBy    user who uploaded it
 * @param customerId    customer the file belongs to, or {@code null}
 * @param createdAt     upload time (UTC)
 * @param deletedAt     when it was soft-deleted, or {@code null}
 */
public record StoredFile(
        UUID id,
        String storageKey,
        String originalName,
        String contentType,
        long sizeBytes,
        String checksum,
        FileCategory category,
        UUID uploadedBy,
        UUID customerId,
        Instant createdAt,
        Instant deletedAt
) {

    public StoredFile {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(originalName, "originalName must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(checksum, "checksum must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(uploadedBy, "uploadedBy must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }

    public static StoredFile uploaded(UUID id, String storageKey, String originalName,
                                      String contentType, long sizeBytes, String checksum,
                                      FileCategory category, UUID uploadedBy, UUID customerId,
                                      Instant now) {
        return new StoredFile(id, storageKey, originalName, contentType, sizeBytes, checksum,
                category, uploadedBy, customerId, now, null);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Marks the file deleted without removing the row.
     *
     * <p>A KYC document that has been relied on is evidence; deleting the record of it would
     * remove the proof that the check was ever made.
     */
    public StoredFile delete(Instant now) {
        return isDeleted() ? this : new StoredFile(id, storageKey, originalName, contentType,
                sizeBytes, checksum, category, uploadedBy, customerId, createdAt, now);
    }
}
