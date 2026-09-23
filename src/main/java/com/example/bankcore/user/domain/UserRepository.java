package com.example.bankcore.user.domain;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for users. Lookups return the user with roles and permissions loaded. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String normalizedUsername);

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByUsername(String normalizedUsername);

    boolean existsByEmail(String normalizedEmail);

    /** Whether any user holds the given role — used to decide if bootstrapping is needed. */
    boolean existsByRole(String roleName);
}
