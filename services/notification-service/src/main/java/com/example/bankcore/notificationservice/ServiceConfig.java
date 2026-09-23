package com.example.bankcore.notificationservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** The service's own wiring. Nothing is shared with the monolith, including this. */
@Configuration
public class ServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
