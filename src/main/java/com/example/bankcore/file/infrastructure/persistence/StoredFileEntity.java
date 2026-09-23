package com.example.bankcore.file.infrastructure.persistence;

import com.example.bankcore.file.domain.FileCategory;
import com.example.bankcore.file.domain.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA mapping of file metadata. Only the deletion timestamp ever changes. */
@Entity
@Table(name = "stored_files")
public class StoredFileEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "storage_key", nullable = false, length = 255, updatable = false)
    private String storageKey;

    @Column(name = "original_name", nullable = false, length = 255, updatable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "checksum", nullable = false, length = 64, updatable = false)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30, updatable = false)
    private FileCategory category;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected StoredFileEntity() {
    }

    private StoredFileEntity(StoredFile file) {
        this.id = file.id();
        this.storageKey = file.storageKey();
        this.originalName = file.originalName();
        this.contentType = file.contentType();
        this.sizeBytes = file.sizeBytes();
        this.checksum = file.checksum();
        this.category = file.category();
        this.uploadedBy = file.uploadedBy();
        this.customerId = file.customerId();
        this.createdAt = file.createdAt();
        applyState(file);
    }

    static StoredFileEntity fromDomain(StoredFile file) {
        return new StoredFileEntity(file);
    }

    void applyState(StoredFile file) {
        this.deletedAt = file.deletedAt();
    }

    StoredFile toDomain() {
        return new StoredFile(id, storageKey, originalName, contentType, sizeBytes, checksum,
                category, uploadedBy, customerId, createdAt, deletedAt);
    }
}
