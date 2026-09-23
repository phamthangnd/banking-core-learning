package com.example.bankcore.auth.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the authenticated user's id from the security context.
 *
 * <p>The id comes from the token's {@code sub} claim, never from a request parameter or body.
 * Taking a user id from the request is how "change any user's password" bugs happen: the caller
 * would be choosing whose data to act on.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<UUID> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException ex) {
            // A principal that is not a user id (for example an anonymous token).
            return Optional.empty();
        }
    }

    public static UUID requireId() {
        return id().orElseThrow(() -> new IllegalStateException("no authenticated user in the security context"));
    }
}
