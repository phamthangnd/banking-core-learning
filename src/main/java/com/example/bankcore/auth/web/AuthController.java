package com.example.bankcore.auth.web;

import com.example.bankcore.auth.application.AuthCommands;
import com.example.bankcore.auth.application.AuthService;
import com.example.bankcore.auth.infrastructure.security.AuthRateLimiter;
import com.example.bankcore.auth.web.dto.AuthRequests;
import com.example.bankcore.auth.web.dto.AuthResponses;
import com.example.bankcore.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Rate limiting is applied here rather than in the service because it is about the caller,
 * not about the business rule: the limiter keys on the client address and the endpoint, both of
 * which are web concepts. Every unauthenticated endpoint that can be used to guess something —
 * login, refresh, reset — is behind it.
 *
 * <p>Two deliberate choices about what these endpoints reveal:
 * <ul>
 *   <li>registration and forgot-password never say whether an account exists;</li>
 *   <li>tokens are returned in the response body, not set as cookies. A body keeps the API
 *       usable by non-browser clients and avoids CSRF entirely; the cost is that the client is
 *       responsible for storing the token somewhere a script cannot read.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponses.UserProfile>> register(
            @Valid @RequestBody AuthRequests.Register request, HttpServletRequest httpRequest) {

        rateLimiter.checkAllowed("register", clientOf(httpRequest));

        var profile = AuthResponses.UserProfile.from(authService.register(request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(profile));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponses.TokenPair> login(@Valid @RequestBody AuthRequests.Login request,
                                                      HttpServletRequest httpRequest) {
        String client = clientOf(httpRequest);
        rateLimiter.checkAllowed("login", client);

        AuthCommands.Tokens tokens = authService.login(request.toCommand());
        rateLimiter.reset("login", client);

        return ApiResponse.success(AuthResponses.TokenPair.from(tokens));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponses.TokenPair> refresh(@Valid @RequestBody AuthRequests.RefreshToken request,
                                                        HttpServletRequest httpRequest) {
        rateLimiter.checkAllowed("refresh", clientOf(httpRequest));

        return ApiResponse.success(AuthResponses.TokenPair.from(authService.refresh(request.refreshToken())));
    }

    /** Ends one session. Always 204, whether or not the token was still valid. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AuthRequests.RefreshToken request) {
        authService.logout(request.refreshToken());
    }

    /** Ends every session of the authenticated user. */
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll() {
        authService.logoutAll(CurrentUser.requireId());
    }

    /**
     * Starts a password reset.
     *
     * <p>Always 202, even for an unknown address: a different answer would turn this endpoint
     * into a way to test which email addresses are registered.
     */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword request,
                               HttpServletRequest httpRequest) {
        rateLimiter.checkAllowed("password-forgot", clientOf(httpRequest));
        authService.requestPasswordReset(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword request,
                              HttpServletRequest httpRequest) {
        rateLimiter.checkAllowed("password-reset", clientOf(httpRequest));
        authService.resetPassword(request.toCommand());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthRequests.ChangePassword request) {
        authService.changePassword(CurrentUser.requireId(), request.toCommand());
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponses.UserProfile> currentUser() {
        return ApiResponse.success(
                AuthResponses.UserProfile.from(authService.requireUser(CurrentUser.requireId())));
    }

    /**
     * Identifies the caller for rate limiting.
     *
     * <p>{@code X-Forwarded-For} is ignored on purpose: it is client-controlled, so trusting it
     * lets an attacker rotate the header and bypass the limiter entirely. Behind a reverse proxy
     * the application must be configured to trust that proxy explicitly — a deployment concern
     * for Phase 12, not a default.
     */
    private static String clientOf(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address;
    }
}
