package com.example.bankcore.user.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An application user.
 *
 * <p>Immutable, like every domain model here. Note what the record does <em>not</em> contain:
 * a plaintext password. Only the hash exists in memory, and even that never leaves the user and
 * auth modules — no DTO exposes it (CLAUDE.md section 4).
 *
 * @param id                   identity
 * @param username             normalised (lower case, trimmed) login name
 * @param email                normalised email address
 * @param passwordHash         hash with its algorithm prefix, e.g. {@code {bcrypt}$2a$12$...}
 * @param fullName             display name
 * @param status               lifecycle state
 * @param failedLoginAttempts  consecutive failed logins since the last success
 * @param lockedUntil          end of a temporary lock, or {@code null}
 * @param passwordChangedAt    when the password was last set
 * @param roles                granted roles
 * @param createdAt            creation timestamp (UTC)
 * @param updatedAt            timestamp of the last change (UTC)
 */
public record User(
        UUID id,
        String username,
        String email,
        String passwordHash,
        String fullName,
        UserStatus status,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant passwordChangedAt,
        Set<Role> roles,
        Instant createdAt,
        Instant updatedAt
) {

    public User {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        username = normalizeUsername(username);
        email = normalizeEmail(email);
        passwordHash = requireText(passwordHash, "passwordHash");
        fullName = requireText(fullName, "fullName").strip();
        roles = roles == null ? Set.of() : Set.copyOf(roles);

        if (failedLoginAttempts < 0) {
            throw new IllegalArgumentException("failedLoginAttempts must not be negative");
        }
    }

    public static User register(UUID id, String username, String email, String passwordHash,
                                String fullName, Set<Role> roles, Instant now) {
        return new User(id, username, email, passwordHash, fullName, UserStatus.ACTIVE,
                0, null, now, roles, now, now);
    }

    /**
     * Authorities this user carries in a token: one per role ({@code ROLE_TELLER}) plus every
     * permission the roles grant ({@code customer:read}).
     *
     * <p>Both are put in the same set because Spring Security has a single notion of an
     * authority; the {@code ROLE_} prefix is the only thing that distinguishes them.
     */
    public Set<String> authorities() {
        Set<String> authorities = new LinkedHashSet<>();
        roles.stream().map(Role::authority).forEach(authorities::add);
        roles.stream().flatMap(role -> role.permissions().stream()).forEach(authorities::add);
        return Set.copyOf(authorities);
    }

    public Set<String> roleNames() {
        return roles.stream().map(Role::name).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether this account may authenticate right now.
     *
     * <p>A temporary lock expires by itself: the check is against the clock, so no scheduled job
     * is needed to unlock accounts.
     */
    public boolean canAuthenticate(Clock clock) {
        if (status == UserStatus.DISABLED) {
            return false;
        }
        return !isLocked(clock);
    }

    public boolean isLocked(Clock clock) {
        return lockedUntil != null && lockedUntil.isAfter(clock.instant());
    }

    /** Records a failed login, locking the account once the threshold is reached. */
    public User withFailedLogin(int maxAttempts, java.time.Duration lockDuration, Instant now) {
        int attempts = failedLoginAttempts + 1;
        boolean shouldLock = attempts >= maxAttempts;

        return new User(id, username, email, passwordHash, fullName,
                shouldLock ? UserStatus.LOCKED : status,
                shouldLock ? 0 : attempts,
                shouldLock ? now.plus(lockDuration) : lockedUntil,
                passwordChangedAt, roles, createdAt, now);
    }

    /** Clears the failure counter and any expired lock after a successful login. */
    public User withSuccessfulLogin(Instant now) {
        if (failedLoginAttempts == 0 && lockedUntil == null && status == UserStatus.ACTIVE) {
            return this;
        }
        return new User(id, username, email, passwordHash, fullName,
                status == UserStatus.LOCKED ? UserStatus.ACTIVE : status,
                0, null, passwordChangedAt, roles, createdAt, now);
    }

    /** Replaces the password hash. Callers must revoke existing refresh tokens afterwards. */
    public User withPasswordHash(String newPasswordHash, Instant now) {
        return new User(id, username, email, newPasswordHash, fullName, status,
                0, null, now, roles, createdAt, now);
    }

    public User withRoles(Set<Role> newRoles, Instant now) {
        return new User(id, username, email, passwordHash, fullName, status,
                failedLoginAttempts, lockedUntil, passwordChangedAt, newRoles, createdAt, now);
    }

    public static String normalizeUsername(String username) {
        return requireText(username, "username").strip().toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        String normalized = requireText(email, "email").strip().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new IllegalArgumentException("email must contain @");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
