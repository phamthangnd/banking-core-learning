package com.example.bankcore.auth.infrastructure.security;

import com.example.bankcore.auth.application.JwtService;
import com.example.bankcore.auth.config.AuthProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Signing key, encoder/decoder and password encoder.
 *
 * <p>The signing algorithm is HMAC-SHA256: one shared secret both signs and verifies, which is
 * the right shape for a single application. The moment a second service needs to verify tokens
 * it did not issue, this must become an asymmetric key pair (RS256/ES256) so the verifier only
 * ever holds a public key — a Phase 13 concern.
 */
@Configuration
public class JwtConfig {

    /** HS256 requires a key of at least 256 bits. */
    private static final int MINIMUM_SECRET_LENGTH = 32;

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    /**
     * Password hashing.
     *
     * <p>A delegating encoder stores the algorithm in the hash itself ({@code {bcrypt}$2a$12$…}),
     * so the application can move to a newer algorithm later and still verify old hashes. BCrypt
     * at strength 12 is deliberately slow: that cost is what makes an offline attack on a stolen
     * hash expensive. A general-purpose fast hash (SHA-256, MD5) would be a vulnerability here,
     * exactly the opposite of the reasoning for token hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The HMAC key.
     *
     * <p>Supplied by the environment. If it is missing the application refuses to start, unless
     * it is running locally or under test, where a random key is generated instead — tokens then
     * simply stop working across restarts, which is a harmless annoyance. A committed default
     * secret, by contrast, is a production key in a public repository.
     */
    @Bean
    public SecretKeySpec jwtSigningKey(AuthProperties properties, Environment environment) {
        String secret = properties.jwt().secret();

        if (secret == null || secret.isBlank()) {
            if (isProductionLike(environment)) {
                throw new IllegalStateException(
                        "bankcore.auth.jwt.secret (env BANKCORE_JWT_SECRET) must be set outside local/test");
            }

            byte[] generated = new byte[MINIMUM_SECRET_LENGTH];
            new SecureRandom().nextBytes(generated);
            log.warn("No JWT secret configured; generated a random one for this run. "
                    + "Tokens will not survive a restart. Set BANKCORE_JWT_SECRET for a stable key.");
            return new SecretKeySpec(generated, "HmacSHA256");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "bankcore.auth.jwt.secret must be at least " + MINIMUM_SECRET_LENGTH + " characters");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKeySpec signingKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    }

    /**
     * Verification.
     *
     * <p>{@code macAlgorithm} pins the accepted algorithm: without it, a decoder that accepts
     * whatever the token's header claims is the classic JWT vulnerability. Issuer and expiry are
     * validated on every request.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKeySpec signingKey, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators
                .createDefaultWithIssuer(properties.jwt().issuer()));

        return decoder;
    }

    /**
     * Turns the {@code authorities} claim into Spring Security authorities verbatim.
     *
     * <p>The default converter reads {@code scope}/{@code scp} and prefixes everything with
     * {@code SCOPE_}, which would turn {@code ROLE_ADMIN} into {@code SCOPE_ROLE_ADMIN} and break
     * every {@code hasRole} check.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName(JwtService.AUTHORITIES_CLAIM);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
            return authorities == null ? List.<GrantedAuthority>of() : authorities;
        });
        // The principal name is the user id (the token's subject), which is what services log.
        converter.setPrincipalClaimName("sub");

        return converter;
    }

    private static boolean isProductionLike(Environment environment) {
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        if (active.isEmpty()) {
            active = Arrays.asList(environment.getDefaultProfiles());
        }
        return active.stream().noneMatch(profile -> profile.equals("local") || profile.equals("test"));
    }
}
