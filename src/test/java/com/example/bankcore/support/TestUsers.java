package com.example.bankcore.support;

import com.example.bankcore.user.domain.Role;
import com.example.bankcore.user.domain.RoleRepository;
import com.example.bankcore.user.domain.User;
import com.example.bankcore.user.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Creates users with real password hashes and real roles for integration tests.
 *
 * <p>Tests authenticate through the actual login endpoint rather than by stubbing a security
 * context: an authorization test that fakes the token proves nothing about the filter chain,
 * the claims or the converter.
 *
 * <p>Not a scanned component — tests pull it in with {@code @Import(TestUsers.class)}, so it
 * exists only where it is asked for and can never leak into the production context.
 */
public class TestUsers {

    public static final String PASSWORD = "Integration-Test-Passw0rd";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public TestUsers(UserRepository users, RoleRepository roles,
                     PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public User create(String username, String roleName) {
        return create(username, roleName, PASSWORD);
    }

    public User create(String username, String roleName, String rawPassword) {
        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("missing seeded role: " + roleName));

        return users.save(User.register(UUID.randomUUID(), username,
                username + "@example.com", passwordEncoder.encode(rawPassword),
                "Test " + username, Set.of(role), clock.instant()));
    }
}
