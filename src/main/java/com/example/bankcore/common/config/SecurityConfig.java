package com.example.bankcore.common.config;

import com.example.bankcore.common.web.RestAccessDeniedHandler;
import com.example.bankcore.common.web.RestAuthenticationEntryPoint;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The security baseline.
 *
 * <p>Rules, in the order they matter:
 * <ul>
 *   <li><b>Deny by default.</b> {@code anyRequest().authenticated()} is the last rule, so a new
 *       endpoint is protected the moment it exists. Public paths are listed one by one.</li>
 *   <li><b>Stateless.</b> No session is created; identity comes from the bearer token on each
 *       request. CSRF protection is therefore switched off deliberately — it defends cookie-based
 *       sessions, which this API does not have. With a cookie-based token it would be required.</li>
 *   <li><b>Layered authorization.</b> The rules here are coarse (is this path public?). The
 *       fine-grained permission checks live on the service methods via {@code @PreAuthorize},
 *       so they also apply to callers that never pass through a controller
 *       (CLAUDE.md section 4: authorization at service <em>and</em> API boundaries).</li>
 * </ul>
 *
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize}. Note that method security is
 * proxy-based: a self-invocation inside the same bean bypasses it.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Whether the metrics endpoint may be scraped without a token.
     *
     * <p>Off by default. Metrics say how much traffic the bank takes, how often logins fail and
     * how many movements are rejected — operational detail an anonymous caller has no business
     * reading. It is turned on only where the endpoint is reachable from the monitoring network
     * alone: locally by the `local` profile, and in production by binding the management port to
     * an internal interface.
     */
    @Value("${bankcore.metrics.public:false}")
    private boolean metricsPublic;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        // The API returns JSON and is never framed; denying it outright removes
                        // clickjacking as a category rather than mitigating it.
                        .frameOptions(frame -> frame.deny())
                        // Stops a browser guessing a content type other than the declared one,
                        // which is what turns an uploaded file into executable content.
                        .contentTypeOptions(withDefaults -> {})
                        // Tells browsers to use HTTPS for a year, including subdomains. Harmless
                        // over plain HTTP in development because browsers ignore it there.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        // A JSON API loads nothing, so the strictest possible policy applies:
                        // if a response is ever rendered as a document, nothing in it may run.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; sandbox"))
                        // Never leak an API path to a third-party site through the Referer header.
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // The legacy XSS auditor caused vulnerabilities of its own; modern
                        // guidance is to disable it explicitly and rely on the CSP above.
                        .xssProtection(xss -> xss.headerValue(
                                XXssProtectionHeaderWriter.HeaderValue.DISABLED))
                        // Browser features this API has no use for.
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()")))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        .requestMatchers(EndpointRequest.to("prometheus"))
                        .access((authentication, context) ->
                                new org.springframework.security.authorization.AuthorizationDecision(metricsPublic))
                        // The only endpoints reachable without a token: the ones used to get one.
                        // Rate limiting, lockout and uniform error messages protect them.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
