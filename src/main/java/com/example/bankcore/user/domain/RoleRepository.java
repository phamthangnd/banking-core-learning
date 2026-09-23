package com.example.bankcore.user.domain;

import java.util.Optional;

/** Read-only access to the role catalogue, which is seeded by a migration. */
public interface RoleRepository {

    Optional<Role> findByName(String name);
}
