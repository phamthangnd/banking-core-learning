package com.example.bankcore.customer.infrastructure.persistence;

import com.example.bankcore.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schema must come from Flyway and from nothing else (CLAUDE.md section 5).
 *
 * <p>That the context started at all already proves a lot: {@code ddl-auto=validate} makes
 * Hibernate compare the entity mapping against the migrated schema and refuse to start when they
 * disagree. These assertions add the parts validation does not check — that the migration really
 * ran, and that the indexes and constraints the query plans depend on exist.
 */
@SpringBootTest
class FlywaySchemaTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHaveAppliedTheMigrationHistory() {
        List<String> applied = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank",
                String.class);

        assertThat(applied).contains("1");
    }

    @Test
    void shouldHaveCreatedTheCustomersTable() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_name = 'customers' order by column_name
                """, String.class);

        assertThat(columns).containsExactly(
                "created_at", "date_of_birth", "email", "full_name", "id",
                "phone_number", "status", "updated_at", "version");
    }

    @Test
    void shouldHaveTheIndexesTheQueriesRelyOn() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'customers'", String.class);

        assertThat(indexes).contains(
                "pk_customers",
                "ux_customers_email",
                "ix_customers_status_created_at",
                "ix_customers_full_name_lower");
    }

    @Test
    void shouldEnforceTheStatusCheckConstraint() {
        Integer constraints = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'customers' and constraint_name = 'ck_customers_status'
                """, Integer.class);

        assertThat(constraints).isEqualTo(1);
    }
}
