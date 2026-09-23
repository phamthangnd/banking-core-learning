package com.example.bankcore.user.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A named bundle of permissions.
 *
 * <p>Roles are for humans ("this person is a teller"), permissions are for code
 * ({@code customer:read}). Authorization checks target permissions, so adding a role or moving
 * a permission between roles is configuration rather than a code change.
 *
 * @param id          identity
 * @param name        role name without a prefix, for example {@code TELLER}
 * @param permissions permission names granted by this role
 */
public record Role(UUID id, String name, Set<String> permissions) {

    /** Prefix Spring Security expects on a role authority, as opposed to a plain permission. */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    public Role {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** The role as a Spring Security authority, for example {@code ROLE_TELLER}. */
    public String authority() {
        return AUTHORITY_PREFIX + name;
    }
}
