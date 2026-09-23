package com.example.bankcore.auth.application;

import com.example.bankcore.auth.config.AuthProperties;
import com.example.bankcore.user.domain.User;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues access tokens.
 *
 * <p>An access token is a signed statement, not a session: the server does not look it up
 * anywhere, which is what makes it cheap and what makes it impossible to revoke before it
 * expires. The lifetime is therefore short ({@code bankcore.auth.jwt.ttl}, 15 minutes by
 * default), and everything that must be revocable — sessions, logout, password changes — hangs
 * off the refresh token, which <em>is</em> stored and checked.
 *
 * <p>Claims carried:
 * <ul>
 *   <li>{@code sub} — user id, not the username, so a rename does not invalidate tokens</li>
 *   <li>{@code iss} — issuer, so a token minted by another system is rejected</li>
 *   <li>{@code iat} / {@code exp} — issued-at and expiry</li>
 *   <li>{@code jti} — unique id, useful for correlating a token in the logs</li>
 *   <li>{@code authorities} — roles and permissions, read by the authentication converter</li>
 *   <li>{@code username} — for display and logging convenience only</li>
 * </ul>
 *
 * <p>Nothing sensitive goes into a JWT: it is signed, not encrypted, and anyone holding it can
 * read every claim.
 */
@Service
public class JwtService {

    public static final String AUTHORITIES_CLAIM = "authorities";
    public static final String USERNAME_CLAIM = "username";

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().ttl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(USERNAME_CLAIM, user.username())
                .claim(AUTHORITIES_CLAIM, List.copyOf(user.authorities()))
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),
                claims)).getTokenValue();

        return new IssuedAccessToken(token, expiresAt, properties.jwt().ttl().toSeconds());
    }

    /**
     * @param value     the encoded JWT
     * @param expiresAt absolute expiry
     * @param ttlSeconds lifetime in seconds, which is what clients actually use to schedule a refresh
     */
    public record IssuedAccessToken(String value, Instant expiresAt, long ttlSeconds) {
    }
}
