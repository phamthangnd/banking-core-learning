package com.example.bankcore.auth.infrastructure.security;

import com.example.bankcore.user.domain.Role;
import com.example.bankcore.user.domain.RoleRepository;
import com.example.bankcore.user.domain.User;
import com.example.bankcore.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Creates the first administrator, once, from the environment.
 *
 * <p>Bootstrapping is the awkward corner of every RBAC system: only an administrator may grant
 * the administrator role, and at first there is none. The options are all imperfect; this one
 * keeps the secret out of the repository:
 *
 * <ul>
 *   <li>A seeded admin in a migration would mean a committed password hash — a public
 *       credential, identical in every deployment.</li>
 *   <li>A hardcoded default password is the same thing with extra steps, and it is the single
 *       most exploited weakness in self-hosted software.</li>
 *   <li>Here the password comes from {@code BANKCORE_BOOTSTRAP_ADMIN_PASSWORD}, and the account
 *       is created only when no administrator exists yet. Unset the variable after the first
 *       start; nothing happens on later starts either way.</li>
 * </ul>
 *
 * <p>The password is read, hashed and dropped. It is never logged, and the account it creates
 * should have its password changed immediately.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    public static final String ADMIN_ROLE = "ADMIN";
    private static final String PASSWORD_ENV = "BANKCORE_BOOTSTRAP_ADMIN_PASSWORD";
    private static final String USERNAME_ENV = "BANKCORE_BOOTSTRAP_ADMIN_USERNAME";
    private static final String EMAIL_ENV = "BANKCORE_BOOTSTRAP_ADMIN_EMAIL";

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Environment environment;

    // Two constructors exist (the second one is for tests), so the injectable one is explicit.
    @Autowired
    public AdminBootstrap(UserRepository users, RoleRepository roles,
                          PasswordEncoder passwordEncoder, Clock clock,
                          org.springframework.core.env.Environment springEnvironment) {
        this(users, roles, passwordEncoder, clock,
                name -> springEnvironment.getProperty(name, System.getenv(name)));
    }

    AdminBootstrap(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                   Clock clock, Environment environment) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(ADMIN_ROLE)) {
            return;
        }

        String password = environment.get(PASSWORD_ENV);
        if (password == null || password.isBlank()) {
            log.warn("No administrator exists and {} is not set. "
                    + "Set it once to create the first administrator.", PASSWORD_ENV);
            return;
        }

        String username = valueOrDefault(environment.get(USERNAME_ENV), "admin");
        String email = valueOrDefault(environment.get(EMAIL_ENV), "admin@bankcore.local");

        Role adminRole = roles.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("role " + ADMIN_ROLE + " is missing"));

        User admin = User.register(UUID.randomUUID(), username, email,
                passwordEncoder.encode(password), "BankCore Administrator",
                Set.of(adminRole), clock.instant());

        User saved = users.save(admin);
        log.warn("Created the initial administrator: id={} username={}. "
                + "Change this password now and unset {}.", saved.id(), saved.username(), PASSWORD_ENV);
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Indirection over the environment so the bootstrap rules are testable without real env vars. */
    @FunctionalInterface
    interface Environment {
        String get(String name);
    }
}
