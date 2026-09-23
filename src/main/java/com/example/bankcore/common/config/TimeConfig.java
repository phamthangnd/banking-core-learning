package com.example.bankcore.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Makes "now" an injectable dependency.
 *
 * <p>Code that calls {@code Instant.now()} directly cannot be tested without sleeping or
 * accepting flakiness. Injecting a {@link Clock} lets tests pin time to a fixed instant, and
 * pins the whole application to UTC — banking timestamps are stored in UTC, never in the
 * server's local zone.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
