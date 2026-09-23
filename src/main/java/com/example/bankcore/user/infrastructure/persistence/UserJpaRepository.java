package com.example.bankcore.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over {@link UserEntity}.
 *
 * <p>Every lookup used during authentication carries an {@link EntityGraph} that fetches roles
 * and their permissions in one statement. Without it, reading a user's authorities would trigger
 * one query for the roles and one per role for its permissions — the N+1 pattern, on the hottest
 * path in the application.
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findWithRolesById(UUID id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from UserEntity u join u.roles r where r.name = :roleName")
    boolean existsByRoleName(String roleName);
}
