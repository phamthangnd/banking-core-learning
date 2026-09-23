package com.example.bankcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application context must start on its own, without any external infrastructure.
 * Phase 02 replaces this with a Testcontainers-backed PostgreSQL setup.
 */
@SpringBootTest
@ActiveProfiles("test")
class BankCoreApplicationTests {

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
