package com.example.bankcore.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findByName(String name);
}
