package com.example.bankcore.customer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over {@link CustomerEntity}.
 *
 * <p>Spring Data generates the implementation at runtime: {@code findByEmail} is derived from
 * the method name, and {@link JpaSpecificationExecutor} adds the methods that take a
 * {@code Specification} — the dynamic filters live in {@link CustomerSpecifications}.
 */
public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID>,
        JpaSpecificationExecutor<CustomerEntity> {

    Optional<CustomerEntity> findByEmail(String email);
}
