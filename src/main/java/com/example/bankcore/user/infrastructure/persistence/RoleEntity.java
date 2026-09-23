package com.example.bankcore.user.infrastructure.persistence;

import com.example.bankcore.user.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA mapping of a role and the permissions it grants.
 *
 * <p>The association is {@code LAZY}. Callers that need the permissions ask for them with an
 * entity graph, so the fetching decision is visible at the query rather than hidden in the
 * mapping — which is how N+1 problems stay findable.
 */
@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<PermissionEntity> permissions = new LinkedHashSet<>();

    protected RoleEntity() {
    }

    public Role toDomain() {
        return new Role(id, name, permissions.stream()
                .map(PermissionEntity::getName)
                .collect(Collectors.toUnmodifiableSet()));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
