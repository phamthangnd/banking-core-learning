package com.example.bankcore.user.infrastructure.persistence;

import com.example.bankcore.user.domain.Role;
import com.example.bankcore.user.domain.User;
import com.example.bankcore.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** JPA mapping of the {@code users} table. Never exposed outside this module. */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserEntity() {
    }

    private UserEntity(User user, Set<RoleEntity> roleEntities) {
        this.id = user.id();
        this.createdAt = user.createdAt();
        applyState(user, roleEntities);
    }

    public static UserEntity fromDomain(User user, Set<RoleEntity> roleEntities) {
        return new UserEntity(user, roleEntities);
    }

    /** Copies mutable state onto a managed entity; Hibernate's dirty checking writes the UPDATE. */
    public void applyState(User user, Set<RoleEntity> roleEntities) {
        this.username = user.username();
        this.email = user.email();
        this.passwordHash = user.passwordHash();
        this.fullName = user.fullName();
        this.status = user.status();
        this.failedLoginAttempts = user.failedLoginAttempts();
        this.lockedUntil = user.lockedUntil();
        this.passwordChangedAt = user.passwordChangedAt();
        this.updatedAt = user.updatedAt();
        this.roles = new LinkedHashSet<>(roleEntities);
    }

    public User toDomain() {
        Set<Role> domainRoles = roles.stream()
                .map(RoleEntity::toDomain)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new User(id, username, email, passwordHash, fullName, status, failedLoginAttempts,
                lockedUntil, passwordChangedAt, domainRoles, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }
}
