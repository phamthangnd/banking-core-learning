package com.example.bankcore.file.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for file metadata. */
public interface FileRepository {

    StoredFile save(StoredFile file);

    Optional<StoredFile> findById(UUID id);

    List<StoredFile> findByCustomerId(UUID customerId);
}
