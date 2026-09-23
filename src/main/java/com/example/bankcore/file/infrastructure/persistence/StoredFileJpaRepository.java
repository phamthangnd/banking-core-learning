package com.example.bankcore.file.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoredFileJpaRepository extends JpaRepository<StoredFileEntity, UUID> {

    List<StoredFileEntity> findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID customerId);
}
