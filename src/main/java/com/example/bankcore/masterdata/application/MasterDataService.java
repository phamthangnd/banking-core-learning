package com.example.bankcore.masterdata.application;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;
import com.example.bankcore.masterdata.domain.MasterDataEntry;
import com.example.bankcore.masterdata.domain.MasterDataRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reference data.
 *
 * <p>Read constantly and changed rarely, which is the textbook case for caching. The cache is
 * declarative and evicted on every write, so a change is visible immediately rather than after a
 * timeout — reference data that is stale for five minutes produces support tickets nobody can
 * reproduce. Redis replaces the in-memory cache in Phase 09.
 */
@Service
@Transactional(readOnly = true)
public class MasterDataService {

    private final MasterDataRepository repository;
    private final Clock clock;

    public MasterDataService(MasterDataRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Cacheable(value = "masterDataTypes")
    @PreAuthorize("hasAuthority('masterdata:read')")
    public List<String> types() {
        return repository.findTypes();
    }

    @Cacheable(value = "masterData", key = "#type + ':' + #activeOnly")
    @PreAuthorize("hasAuthority('masterdata:read')")
    public List<MasterDataEntry> byType(String type, boolean activeOnly) {
        return repository.findByType(normalize(type), activeOnly);
    }

    @PreAuthorize("hasAuthority('masterdata:read')")
    public MasterDataEntry byCode(String type, String code) {
        return repository.findByTypeAndCode(normalize(type), normalize(code))
                .orElseThrow(() -> new MasterDataNotFoundException(type, code));
    }

    /** Whether a code exists and may still be chosen. Used by validation elsewhere. */
    public boolean isValidCode(String type, String code) {
        return repository.findByTypeAndCode(normalize(type), normalize(code))
                .filter(MasterDataEntry::active)
                .isPresent();
    }

    @Transactional
    @CacheEvict(value = {"masterData", "masterDataTypes"}, allEntries = true)
    @PreAuthorize("hasAuthority('masterdata:write')")
    public MasterDataEntry create(String type, String code, String label, String description, int sortOrder) {
        String normalizedType = normalize(type);
        String normalizedCode = normalize(code);

        if (repository.findByTypeAndCode(normalizedType, normalizedCode).isPresent()) {
            throw new DuplicateMasterDataException(normalizedType, normalizedCode);
        }

        return repository.save(MasterDataEntry.create(UUID.randomUUID(), normalizedType,
                normalizedCode, label, description, sortOrder, clock.instant()));
    }

    /**
     * Updates the presentation of an entry.
     *
     * <p>The type and code are identity and cannot change: other records reference the code, and
     * renaming it would silently repoint them.
     */
    @Transactional
    @CacheEvict(value = {"masterData", "masterDataTypes"}, allEntries = true)
    @PreAuthorize("hasAuthority('masterdata:write')")
    public MasterDataEntry update(UUID id, String label, String description, int sortOrder, boolean active) {
        MasterDataEntry existing = repository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("id", id.toString()));

        return repository.save(existing.update(label, description, sortOrder, active, clock.instant()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    public static class MasterDataNotFoundException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public MasterDataNotFoundException(String type, String code) {
            super(ErrorCode.MASTER_DATA_NOT_FOUND, "No %s entry with code %s".formatted(type, code));
        }
    }

    public static class DuplicateMasterDataException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public DuplicateMasterDataException(String type, String code) {
            super(ErrorCode.MASTER_DATA_DUPLICATE, "%s already has an entry with code %s".formatted(type, code));
        }
    }
}
