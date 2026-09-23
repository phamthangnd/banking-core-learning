package com.example.bankcore.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * <p>Why a container and not H2: an in-memory database with a different SQL dialect proves the
 * code runs somewhere, not that it runs in production. Migrations, constraints, index
 * behaviour, types like {@code TIMESTAMPTZ} and the driver itself are only honestly tested
 * against the real engine (CLAUDE.md section 7).
 *
 * <p>The container is a JVM-wide singleton started once and deliberately <em>not</em> annotated
 * with {@code @Container}: the JUnit extension would stop it after each test class, paying the
 * startup cost again for the next one. Testcontainers' Ryuk sidecar removes it when the JVM
 * exits.
 *
 * <p>{@code @ServiceConnection} hands the container's JDBC URL, user and password to Spring
 * Boot's datasource auto-configuration, so no test knows a connection string and no test can
 * accidentally point at a developer's local database.
 */
@ActiveProfiles("test")
@Import(PostgresIntegrationTest.CleanerConfiguration.class)
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    /** Makes {@link DatabaseCleaner} injectable in every test that extends this class. */
    @TestConfiguration
    public static class CleanerConfiguration {

        @Bean
        public DatabaseCleaner databaseCleaner(JdbcTemplate jdbcTemplate) {
            return new DatabaseCleaner(jdbcTemplate);
        }
    }
}
