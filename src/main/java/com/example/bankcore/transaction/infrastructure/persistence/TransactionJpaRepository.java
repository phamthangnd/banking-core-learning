package com.example.bankcore.transaction.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID>,
        JpaSpecificationExecutor<TransactionEntity> {

    Optional<TransactionEntity> findByReference(String reference);

    @Query(value = "select nextval('transaction_reference_seq')", nativeQuery = true)
    long nextReferenceSequence();
}
