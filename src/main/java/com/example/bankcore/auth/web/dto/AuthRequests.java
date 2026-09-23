package com.example.bankcore.auth.web.dto;

import com.example.bankcore.auth.application.AuthCommands;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request bodies of the authentication endpoints.
 *
 * <p>Bean Validation only checks shape here. Password <em>strength</em> is deliberately not an
 * annotation: it is a policy that belongs in one place ({@code PasswordPolicy}), applies equally
 * to registration, reset and change, and is configurable.
 *
 * <p>Note the upper bound on password length. It is not a strength rule — it caps the work
 * BCrypt is asked to do, so a megabyte-long password cannot become a denial-of-service.
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Register(
            @NotBlank(message = "must not be blank")
            @Size(min = 3, max = 100, message = "must be between 3 and 100 characters")
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may contain letters, digits, dot, underscore and hyphen")
            String username,

            @NotBlank(message = "must not be blank")
            @Email(message = "must be a valid email address")
            @Size(max = 255, message = "must be at most 255 characters")
            String email,

            @NotBlank(message = "must not be blank")
            @Size(max = 200, message = "must be at most 200 characters")
            String password,

            @NotBlank(message = "must not be blank")
            @Size(max = 150, message = "must be at most 150 characters")
            String fullName
    ) {
        public AuthCommands.Register toCommand() {
            return new AuthCommands.Register(username, email, password, fullName);
        }
    }

    public record Login(
            @NotBlank(message = "must not be blank") @Size(max = 100) String username,
            @NotBlank(message = "must not be blank") @Size(max = 200) String password
    ) {
        public AuthCommands.Login toCommand() {
            return new AuthCommands.Login(username, password);
        }
    }

    public record RefreshToken(
            @NotBlank(message = "must not be blank") @Size(max = 200) String refreshToken
    ) {
    }

    public record ForgotPassword(
            @NotBlank(message = "must not be blank") @Email @Size(max = 255) String email
    ) {
    }

    public record ResetPassword(
            @NotBlank(message = "must not be blank") @Size(max = 200) String token,
            @NotBlank(message = "must not be blank") @Size(max = 200) String newPassword
    ) {
        public AuthCommands.ResetPassword toCommand() {
            return new AuthCommands.ResetPassword(token, newPassword);
        }
    }

    public record ChangePassword(
            @NotBlank(message = "must not be blank") @Size(max = 200) String currentPassword,
            @NotBlank(message = "must not be blank") @Size(max = 200) String newPassword
    ) {
        public AuthCommands.ChangePassword toCommand() {
            return new AuthCommands.ChangePassword(currentPassword, newPassword);
        }
    }
}
