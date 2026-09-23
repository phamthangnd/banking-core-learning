package com.example.bankcore.auth.infrastructure.security;

import com.example.bankcore.user.domain.Role;
import com.example.bankcore.user.domain.RoleRepository;
import com.example.bankcore.user.domain.User;
import com.example.bankcore.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bootstrap rules: create at most one administrator, only from the environment, only when
 * none exists.
 */
class AdminBootstrapTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneOffset.UTC);

    private final RecordingUserRepository users = new RecordingUserRepository();
    private final RoleRepository roles = name -> Optional.of(new Role(UUID.randomUUID(), name, Set.of()));
    private final Map<String, String> env = new HashMap<>();

    @SuppressWarnings("deprecation") // NoOpPasswordEncoder keeps this test about the bootstrap rules.
    private AdminBootstrap bootstrap() {
        return new AdminBootstrap(users, roles, NoOpPasswordEncoder.getInstance(), CLOCK, env::get);
    }

    @Test
    void shouldCreateTheAdministratorWhenNoneExistsAndThePasswordIsSupplied() {
        env.put("BANKCORE_BOOTSTRAP_ADMIN_PASSWORD", "Bootstrap-Passw0rd");

        bootstrap().run(null);

        assertThat(users.saved).hasSize(1);
        assertThat(users.saved.get(0).username()).isEqualTo("admin");
        assertThat(users.saved.get(0).roleNames()).containsExactly("ADMIN");
    }

    @Test
    void shouldHonourTheConfiguredUsernameAndEmail() {
        env.put("BANKCORE_BOOTSTRAP_ADMIN_PASSWORD", "Bootstrap-Passw0rd");
        env.put("BANKCORE_BOOTSTRAP_ADMIN_USERNAME", "root");
        env.put("BANKCORE_BOOTSTRAP_ADMIN_EMAIL", "root@bank.example");

        bootstrap().run(null);

        assertThat(users.saved.get(0).username()).isEqualTo("root");
        assertThat(users.saved.get(0).email()).isEqualTo("root@bank.example");
    }

    @Test
    void shouldDoNothingWithoutAPassword() {
        // No password means no account: an administrator with a default password would be worse
        // than no administrator at all.
        bootstrap().run(null);

        assertThat(users.saved).isEmpty();
    }

    @Test
    void shouldDoNothingWhenAnAdministratorAlreadyExists() {
        env.put("BANKCORE_BOOTSTRAP_ADMIN_PASSWORD", "Bootstrap-Passw0rd");
        users.adminExists = true;

        bootstrap().run(null);

        assertThat(users.saved).isEmpty();
    }

    /** Minimal stand-in that records what the bootstrap tried to save. */
    private static final class RecordingUserRepository implements UserRepository {

        private final List<User> saved = new ArrayList<>();
        private boolean adminExists;

        @Override
        public User save(User user) {
            saved.add(user);
            return user;
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByUsername(String normalizedUsername) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByEmail(String normalizedEmail) {
            return Optional.empty();
        }

        @Override
        public boolean existsByUsername(String normalizedUsername) {
            return false;
        }

        @Override
        public boolean existsByEmail(String normalizedEmail) {
            return false;
        }

        @Override
        public boolean existsByRole(String roleName) {
            return adminExists;
        }
    }
}
