package com.example.bankcore;

import com.example.bankcore.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application context must start against a real PostgreSQL, which also proves that Flyway
 * migrated the schema and that Hibernate's {@code ddl-auto=validate} accepted the mapping.
 */
@SpringBootTest
class BankCoreApplicationTests extends PostgresIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldStartApplicationContext() {
        assertThat(context).isNotNull();
        assertThat(context.getEnvironment().getActiveProfiles()).contains("test");
    }

    @Test
    void shouldRegisterSecurityBaseline() {
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isNotEmpty();
    }
}
