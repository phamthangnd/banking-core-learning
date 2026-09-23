package com.example.bankcore.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empties the application tables between tests.
 *
 * <p>The Testcontainers PostgreSQL is shared by the whole JVM, so rows left by one test class
 * would otherwise leak into the next. Deleting through the repositories is not enough any more:
 * accounts reference customers, and that foreign key is deliberately {@code RESTRICT} — deleting
 * a customer who still owns an account must fail in production, so it fails in tests too.
 *
 * <p>A single {@code TRUNCATE ... CASCADE} clears the whole set regardless of order. Reference
 * data seeded by migrations — roles and permissions — is left alone, because the migrations are
 * what own it.
 *
 * <p>This only ever runs against the throwaway container supplied by {@code @ServiceConnection}:
 * no test knows a connection string, so none can reach a real database.
 *
 * <p>Not a scanned component; it is provided by {@link PostgresIntegrationTest}.
 */
public class DatabaseCleaner {

    private static final String TABLES = String.join(", ",
            "accounts", "customers", "refresh_tokens", "password_reset_tokens", "user_roles", "users");

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void clear() {
        jdbcTemplate.execute("TRUNCATE TABLE " + TABLES + " CASCADE");
    }
}
