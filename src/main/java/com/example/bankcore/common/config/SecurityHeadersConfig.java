package com.example.bankcore.common.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * CORS policy and the response headers that harden the browser side.
 *
 * <p>The CORS rule that matters: origins are an explicit allow-list, never {@code *}. A wildcard
 * origin combined with credentials is rejected by browsers anyway, and a wildcard without them
 * still lets any site read every response the API gives to an unauthenticated request. The
 * default here is empty — an API with no browser client needs no CORS at all, and opening it
 * "just in case" is how it stays open.
 */
@Configuration
public class SecurityHeadersConfig {

    /**
     * @param allowedOrigins exact origins, for example {@code https://app.bankcore.example}.
     *                       Empty means no cross-origin browser access at all.
     */
    @ConfigurationProperties(prefix = "bankcore.cors")
    @Validated
    public record CorsProperties(
            @DefaultValue List<String> allowedOrigins,
            @DefaultValue({"GET", "POST", "PUT", "DELETE", "OPTIONS"}) @NotEmpty List<String> allowedMethods,
            @DefaultValue({"Authorization", "Content-Type", "Idempotency-Key", "X-Trace-Id"})
            @NotEmpty List<String> allowedHeaders,
            @DefaultValue("1800s") Duration maxAge
    ) {
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();

        // setAllowedOrigins, not setAllowedOriginPatterns: patterns invite a wildcard, and a
        // pattern like "https://*.example.com" also matches an attacker's subdomain if one is
        // ever obtainable.
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(properties.allowedMethods());
        configuration.setAllowedHeaders(properties.allowedHeaders());
        // The trace id is the one header a browser client has a reason to read.
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        // Tokens travel in the Authorization header, not in cookies, so credentials are not
        // needed and allowing them would only widen what a hostile origin could attempt.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
