package com.example.bankcore.auth.web.dto;

import com.example.bankcore.auth.application.AuthCommands;
import com.example.bankcore.user.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response bodies of the authentication endpoints.
 *
 * <p>No response type here has a password field, and none ever will: a DTO is the place where a
 * careless addition to the domain model would otherwise leak straight into an API.
 */
public final class AuthResponses {

    private AuthResponses() {
    }

    /**
     * @param accessToken  bearer token for the {@code Authorization} header
     * @param tokenType    always {@code Bearer}; part of the contract clients expect
     * @param expiresIn    access-token lifetime in seconds
     * @param refreshToken single-use token for obtaining the next pair
     */
    public record TokenPair(String accessToken, String tokenType, long expiresIn, String refreshToken) {

        public static TokenPair from(AuthCommands.Tokens tokens) {
            return new TokenPair(tokens.accessToken(), "Bearer",
                    tokens.accessTokenExpiresIn(), tokens.refreshToken());
        }
    }

    /** The authenticated user's own profile. */
    public record UserProfile(
            UUID id,
            String username,
            String email,
            String fullName,
            String status,
            Set<String> roles,
            Set<String> permissions,
            Instant createdAt
    ) {
        public static UserProfile from(User user) {
            return new UserProfile(user.id(), user.username(), user.email(), user.fullName(),
                    user.status().name(), user.roleNames(),
                    user.roles().stream()
                            .flatMap(role -> role.permissions().stream())
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    user.createdAt());
        }
    }
}
