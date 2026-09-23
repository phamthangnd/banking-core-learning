package com.example.bankcore.user.infrastructure.persistence;

import com.example.bankcore.user.domain.Role;
import com.example.bankcore.user.domain.User;
import com.example.bankcore.user.domain.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter translating the {@link UserRepository} port onto JPA.
 *
 * <p>The methods are transactional because {@link #save} is a read-modify-write across several
 * Spring Data calls. Without one transaction around the whole sequence each call would run in its
 * own — a lost-update hazard, and the reason the lazily mapped {@code roles.permissions} would be
 * unreadable ("no Session") by the time the entity is converted back to a domain record. A caller
 * that already has a transaction simply joins it.
 */
@Repository
@Transactional(readOnly = true)
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository users;
    private final RoleJpaRepository roles;

    public JpaUserRepository(UserJpaRepository users, RoleJpaRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Override
    @Transactional
    public User save(User user) {
        Set<RoleEntity> roleEntities = resolveRoles(user.roles());

        UserEntity entity = users.findWithRolesById(user.id())
                .map(existing -> {
                    existing.applyState(user, roleEntities);
                    return existing;
                })
                .orElseGet(() -> UserEntity.fromDomain(user, roleEntities));

        return users.save(entity).toDomain();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return users.findWithRolesById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String normalizedUsername) {
        return users.findByUsername(normalizedUsername).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return users.findByEmail(normalizedEmail).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByUsername(String normalizedUsername) {
        return users.existsByUsername(normalizedUsername);
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return users.existsByEmail(normalizedEmail);
    }

    @Override
    public boolean existsByRole(String roleName) {
        return users.existsByRoleName(roleName);
    }

    /**
     * Resolves domain roles to managed entities.
     *
     * <p>Roles are reference data owned by a migration, so a user can only be linked to a role
     * that already exists. An unknown name is a bug, not a user error.
     */
    private Set<RoleEntity> resolveRoles(Set<Role> domainRoles) {
        Set<RoleEntity> entities = new LinkedHashSet<>();
        for (Role role : domainRoles) {
            entities.add(roles.findByName(role.name())
                    .orElseThrow(() -> new IllegalStateException("unknown role: " + role.name())));
        }
        return entities;
    }
}
