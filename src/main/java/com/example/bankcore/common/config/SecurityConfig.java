package com.example.bankcore.common.config;

import com.example.bankcore.common.web.RestAccessDeniedHandler;
import com.example.bankcore.common.web.RestAuthenticationEntryPoint;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
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
