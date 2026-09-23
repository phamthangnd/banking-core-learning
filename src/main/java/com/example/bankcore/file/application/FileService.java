package com.example.bankcore.file.application;

import com.example.bankcore.audit.application.AuditService;
import com.example.bankcore.audit.domain.AuditOutcome;
import com.example.bankcore.file.domain.FileCategory;
import com.example.bankcore.file.domain.FileNotFoundException;
import com.example.bankcore.file.domain.FileRepository;
import com.example.bankcore.file.domain.FileValidation;
import com.example.bankcore.file.domain.InvalidFileException;
import com.example.bankcore.file.domain.ObjectStorage;
import com.example.bankcore.file.domain.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Uploading, downloading and deleting files.
 *
 * <p>Order of operations on upload: validate, then store the bytes, then record the metadata.
 * If the metadata write fails the object is removed again, because an object with no row is
 * invisible — nothing can ever find it to check permissions on it, and it would sit in the
 * bucket forever.
 */
@Service
@Transactional(readOnly = true)
public class FileService {

    private static final int MAGIC_BYTES = 16;

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileRepository files;
    private final ObjectStorage storage;
    private final AuditService audit;
    private final Clock clock;

    public FileService(FileRepository files, ObjectStorage storage, AuditService audit, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAuthority('file:write')")
    public StoredFile upload(FileCategory category, String originalName, String contentType,
                             byte[] content, UUID uploadedBy, UUID customerId) {

        String normalizedType = FileValidation.normalizeContentType(contentType);
        byte[] head = Arrays.copyOf(content, Math.min(MAGIC_BYTES, content.length));

        try {
            FileValidation.validate(category, normalizedType, content.length, head);
        } catch (InvalidFileException rejected) {
            audit.record("FILE_UPLOAD", "FILE", null, AuditOutcome.FAILURE, rejected.getMessage());
            throw rejected;
        }

        String safeName = FileValidation.sanitizeFileName(originalName);
        UUID id = UUID.randomUUID();
        // The key is generated and includes the category, so an uploaded name can never decide
        // where the object lands.
        String storageKey = "%s/%s/%s".formatted(category.name().toLowerCase(java.util.Locale.ROOT), id, safeName);

        storage.put(storageKey, content, normalizedType);

        try {
            StoredFile saved = files.save(StoredFile.uploaded(id, storageKey, safeName, normalizedType,
                    content.length, checksum(content), category, uploadedBy, customerId, clock.instant()));

            audit.recordSuccess("FILE_UPLOAD", "FILE", saved.id().toString());
            log.info("File uploaded: id={} category={} size={}", saved.id(), category, content.length);
            return saved;
        } catch (RuntimeException ex) {
            // An object nothing points at can never be found, checked or deleted again.
            storage.delete(storageKey);
            throw ex;
        }
    }

    @PreAuthorize("hasAuthority('file:read')")
    public StoredFile metadata(UUID id) {
        return files.findById(id)
                .filter(file -> !file.isDeleted())
                .orElseThrow(() -> new FileNotFoundException(id));
    }

    @PreAuthorize("hasAuthority('file:read')")
    public InputStream download(UUID id) {
        StoredFile file = metadata(id);
        audit.recordSuccess("FILE_DOWNLOAD", "FILE", id.toString());
        return storage.get(file.storageKey());
    }

    @PreAuthorize("hasAuthority('file:read')")
    public List<StoredFile> listForCustomer(UUID customerId) {
        return files.findByCustomerId(customerId);
    }

    /**
     * Soft-deletes a file: the metadata stays, the object goes.
     *
     * <p>The row is evidence that the file existed and who uploaded it — for a KYC document, that
     * the check was made at all. The bytes are removed because keeping personal data longer than
     * needed is its own problem.
     */
    @Transactional
    @PreAuthorize("hasAuthority('file:write')")
    public void delete(UUID id) {
        StoredFile file = metadata(id);

        files.save(file.delete(clock.instant()));
        storage.delete(file.storageKey());

        audit.recordSuccess("FILE_DELETE", "FILE", id.toString());
        log.info("File deleted: id={}", id);
    }

    private static String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
