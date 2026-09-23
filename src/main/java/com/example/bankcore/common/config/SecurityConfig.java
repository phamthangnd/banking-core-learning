package com.example.bankcore.common.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 00 security baseline.
 *
 * <p>There is no authentication yet: Phase 03 introduces JWT, refresh-token rotation and RBAC.
 * The baseline is "deny everything", and each phase opens exactly what it needs, so no endpoint
 * is ever public by accident. Phase 01 opens the customer API; that is a known, temporary gap,
 * recorded in PROGRESS.md.
 *
 * <p>The API is stateless, so sessions and CSRF tokens (which protect cookie-based browser
 * sessions) are switched off deliberately rather than by accident.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        // Phase 01: the customer API has no authentication yet. Phase 03 replaces
                        // this line with authenticated, role-checked access. Until then the
                        // application must not be exposed outside a development machine.
                        .requestMatchers("/api/v1/customers/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
