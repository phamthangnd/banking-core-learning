package com.example.bankcore.auth.application;

/**
 * Input and output models of {@link AuthService}.
 *
 * <p>Raw passwords and raw tokens appear here and nowhere else in the application's vocabulary.
 * They are never put into a domain model, a log statement or a response body other than the one
 * that must return them.
 */
public final class AuthCommands {

    private AuthCommands() {
    }

    public record Register(String username, String email, String password, String fullName) {
    }

    public record Login(String username, String password) {
    }

    public record ChangePassword(String currentPassword, String newPassword) {
    }

    public record ResetPassword(String token, String newPassword) {
    }

    /**
     * The pair handed back after a successful login or refresh.
     *
     * @param accessToken          short-lived signed JWT
     * @param accessTokenExpiresIn lifetime in seconds
     * @param refreshToken         long-lived opaque token; single use, rotated on every refresh
     */
    public record Tokens(String accessToken, long accessTokenExpiresIn, String refreshToken) {
    }
}
