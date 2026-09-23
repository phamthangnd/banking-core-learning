package com.example.bankcore.file.infrastructure.persistence;

import com.example.bankcore.file.domain.FileRepository;
import com.example.bankcore.file.domain.StoredFile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaFileRepository implements FileRepository {

    private final StoredFileJpaRepository files;

    public JpaFileRepository(StoredFileJpaRepository files) {
        this.files = files;
    }

    @Override
    @Transactional
    public StoredFile save(StoredFile file) {
        StoredFileEntity entity = files.findById(file.id())
                .map(existing -> {
                    existing.applyState(file);
                    return existing;
                })
                .orElseGet(() -> StoredFileEntity.fromDomain(file));

        return files.save(entity).toDomain();
    }

    @Override
    public Optional<StoredFile> findById(UUID id) {
        return files.findById(id).map(StoredFileEntity::toDomain);
    }

    @Override
    public List<StoredFile> findByCustomerId(UUID customerId) {
        return files.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId)
                .stream().map(StoredFileEntity::toDomain).toList();
    }
}
