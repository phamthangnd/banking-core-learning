package com.example.bankcore.auth.application;

import com.example.bankcore.audit.application.AuditService;
import com.example.bankcore.audit.domain.AuditOutcome;
import com.example.bankcore.auth.config.AuthProperties;
import com.example.bankcore.common.observability.BankingMetrics;
import com.example.bankcore.auth.domain.AuthExceptions;
import com.example.bankcore.auth.domain.PasswordResetToken;
import com.example.bankcore.auth.domain.PasswordResetTokenRepository;
import com.example.bankcore.auth.domain.RefreshToken;
import com.example.bankcore.auth.domain.RefreshTokenRepository;
import com.example.bankcore.auth.domain.TokenHasher;
import com.example.bankcore.user.domain.Role;
import com.example.bankcore.user.domain.RoleRepository;
import com.example.bankcore.user.domain.User;
import com.example.bankcore.user.domain.UserNotFoundException;
import com.example.bankcore.user.domain.UserRepository;
import com.example.bankcore.user.domain.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The authentication use cases: register, log in, refresh, log out, and the password flows.
 *
 * <p>Logging rule applied throughout: identifiers yes, secrets never. No password, token,
 * token hash or reset link appears in a log statement, and failures are logged by user id or
 * by nothing at all (CLAUDE.md section 4).
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    /** Role granted to anyone who registers themselves. Least privilege by default. */
    public static final String DEFAULT_ROLE = "CUSTOMER";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecureTokenGenerator tokenGenerator;
    private final PasswordResetTokenSender resetTokenSender;
    private final JwtService jwtService;
    private final SecurityStateRecorder securityStateRecorder;
    private final AuditService audit;
    private final BankingMetrics metrics;
    private final AuthProperties properties;
    private final Clock clock;

    @SuppressWarnings("java:S107") // An orchestrating service legitimately has many collaborators.
    public AuthService(UserRepository users,
                       RoleRepository roles,
                       RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository resetTokens,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy,
                       SecureTokenGenerator tokenGenerator,
                       PasswordResetTokenSender resetTokenSender,
                       JwtService jwtService,
                       SecurityStateRecorder securityStateRecorder,
                       AuditService audit,
                       BankingMetrics metrics,
                       AuthProperties properties,
                       Clock clock) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenGenerator = tokenGenerator;
        this.resetTokenSender = resetTokenSender;
        this.jwtService = jwtService;
        this.securityStateRecorder = securityStateRecorder;
        this.audit = audit;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    /** Registers a self-service user with the default role. */
    @Transactional
    public User register(AuthCommands.Register command) {
        String username = User.normalizeUsername(command.username());
        String email = User.normalizeEmail(command.email());

        if (users.existsByUsername(username) || users.existsByEmail(email)) {
            // One exception for both cases: saying which one is taken is a user-enumeration oracle.
            throw new AuthExceptions.UserAlreadyExistsException();
        }

        passwordPolicy.validate(command.password(), username, email);

        Role defaultRole = roles.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("default role " + DEFAULT_ROLE + " is missing"));

        Instant now = clock.instant();
        User user = User.register(UUID.randomUUID(), username, email,
                passwordEncoder.encode(command.password()), command.fullName(),
                Set.of(defaultRole), now);

        User saved = users.save(user);
        log.info("User registered: id={} role={}", saved.id(), DEFAULT_ROLE);
        audit.recordSuccess("USER_REGISTERED", "USER", saved.id().toString());
        return saved;
    }

    /**
     * Verifies credentials and issues a token pair.
     *
     * <p>Failure handling here is where most of the security lives:
     * <ul>
     *   <li>an unknown user still runs a password hash, so the response time does not reveal
     *       whether the account exists;</li>
     *   <li>every failure returns the same error;</li>
     *   <li>consecutive failures lock the account temporarily, which is what makes online
     *       password guessing impractical.</li>
     * </ul>
     */
    @Transactional
    public AuthCommands.Tokens login(AuthCommands.Login command) {
        String username = User.normalizeUsername(command.username());
        Optional<User> found = users.findByUsername(username);

        if (found.isEmpty()) {
            // Spend the same work as a real verification to avoid a timing oracle.
            passwordEncoder.matches(command.password(), dummyHash());
            log.info("Login failed: unknown username");
            // Audited without the username: the trail must not become a list of guessed accounts.
            audit.record("LOGIN", "USER", null, AuditOutcome.FAILURE, "unknown username");
            metrics.loginAttempt("unknown_user");
            throw new AuthExceptions.InvalidCredentialsException();
        }

        User user = found.get();

        if (user.status() == UserStatus.DISABLED) {
            log.info("Login refused for disabled account: id={}", user.id());
            throw new AuthExceptions.AccountNotActiveException("Account is disabled");
        }

        if (user.isLocked(clock)) {
            log.info("Login refused for locked account: id={}", user.id());
            throw new AuthExceptions.AccountNotActiveException(
                    "Account is temporarily locked after repeated failed logins");
        }

        if (!passwordEncoder.matches(command.password(), user.passwordHash())) {
            // Committed in its own transaction: the exception below rolls this one back, and a
            // failure counter that disappears with the failure protects nobody.
            securityStateRecorder.recordFailedLogin(user);
            audit.record("LOGIN", "USER", user.id().toString(), AuditOutcome.FAILURE, "wrong password");
            metrics.loginAttempt("bad_password");
            throw new AuthExceptions.InvalidCredentialsException();
        }

        User authenticated = users.save(user.withSuccessfulLogin(clock.instant()));
        log.info("Login succeeded: id={}", authenticated.id());
        audit.recordSuccess("LOGIN", "USER", authenticated.id().toString());
        metrics.loginAttempt("success");

        return issuePair(authenticated).tokens();
    }

    /**
     * Exchanges a refresh token for a new pair, rotating it.
     *
     * <p>Rotation plus reuse detection: each refresh token works once. Presenting one that has
     * already been rotated means two parties hold it — the legitimate client and a thief — so
     * every token of that user is revoked and both are forced to log in again. Losing a session
     * is a far better outcome than letting a stolen token live for 30 days.
     */
    @Transactional
    public AuthCommands.Tokens refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(TokenHasher.hash(rawRefreshToken))
                .orElseThrow(AuthExceptions.InvalidTokenException::new);

        Instant now = clock.instant();

        if (stored.isRevoked()) {
            // A token that was already rotated has been presented again: it leaked. End every
            // session of this user, in a transaction that survives the rejection below.
            int revoked = securityStateRecorder.revokeAllSessions(stored.userId());
            log.warn("Refresh token reuse detected: userId={} revokedSessions={}", stored.userId(), revoked);
            throw new AuthExceptions.InvalidTokenException();
        }

        if (stored.isExpired(clock)) {
            log.info("Refresh token expired: userId={}", stored.userId());
            throw new AuthExceptions.InvalidTokenException();
        }

        User user = users.findById(stored.userId()).orElseThrow(UserNotFoundException::new);

        if (!user.canAuthenticate(clock)) {
            securityStateRecorder.revokeAllSessions(user.id());
            throw new AuthExceptions.AccountNotActiveException("Account is not active");
        }

        IssuedPair issued = issuePair(user);
        refreshTokens.save(stored.rotateInto(issued.stored().id(), now));
        log.info("Refresh token rotated: userId={}", user.id());

        return issued.tokens();
    }

    /**
     * Ends a session.
     *
     * <p>Revoking an unknown token is not an error: logging out must always appear to succeed,
     * or the endpoint becomes an oracle for guessing valid tokens.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(TokenHasher.hash(rawRefreshToken))
                .ifPresent(token -> {
                    refreshTokens.save(token.revoke(clock.instant()));
                    log.info("Logout: userId={}", token.userId());
                });
    }

    /** Revokes every session of a user — "log out everywhere". */
    @Transactional
    public void logoutAll(UUID userId) {
        int revoked = refreshTokens.revokeAllForUser(userId, clock.instant());
        log.info("Logout from all sessions: userId={} revokedSessions={}", userId, revoked);
    }

    /**
     * Changes the password of the authenticated user.
     *
     * <p>The current password is required, so a stolen access token alone cannot lock the owner
     * out. All refresh tokens are revoked afterwards: a password change is how a user reacts to
     * a suspected compromise, and it must actually end the attacker's sessions.
     */
    @Transactional
    public void changePassword(UUID userId, AuthCommands.ChangePassword command) {
        User user = users.findById(userId).orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(command.currentPassword(), user.passwordHash())) {
            log.info("Password change refused, current password did not match: id={}", userId);
            throw new AuthExceptions.InvalidCredentialsException();
        }

        applyNewPassword(user, command.newPassword());
        log.info("Password changed: id={}", userId);
        audit.recordSuccess("PASSWORD_CHANGE", "USER", userId.toString());
    }

    /**
     * Starts a password reset.
     *
     * <p>Always behaves the same way whether or not the address exists — the caller cannot learn
     * which emails are registered. Any previously issued token is invalidated, so only the most
     * recent request can be redeemed.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        Optional<User> found;
        try {
            found = users.findByEmail(User.normalizeEmail(email));
        } catch (IllegalArgumentException | NullPointerException ex) {
            // A malformed address is treated exactly like an unknown one.
            return;
        }

        if (found.isEmpty()) {
            log.info("Password reset requested for an unknown address");
            return;
        }

        User user = found.get();
        Instant now = clock.instant();
        resetTokens.invalidateAllForUser(user.id(), now);

        String rawToken = tokenGenerator.generate();
        resetTokens.save(PasswordResetToken.issue(UUID.randomUUID(), user.id(),
                TokenHasher.hash(rawToken), now, now.plus(properties.passwordReset().ttl())));

        // The raw token leaves through the sender port and nowhere else. It is never logged.
        resetTokenSender.send(user, rawToken);
        log.info("Password reset token issued: userId={}", user.id());
    }

    /** Redeems a reset token. The token is single use and every session is revoked afterwards. */
    @Transactional
    public void resetPassword(AuthCommands.ResetPassword command) {
        PasswordResetToken token = resetTokens.findByTokenHash(TokenHasher.hash(command.token()))
                .orElseThrow(AuthExceptions.InvalidTokenException::new);

        if (!token.isUsable(clock)) {
            log.info("Password reset refused, token not usable: userId={}", token.userId());
            throw new AuthExceptions.InvalidTokenException();
        }

        User user = users.findById(token.userId()).orElseThrow(UserNotFoundException::new);

        applyNewPassword(user, command.newPassword());
        resetTokens.save(token.markUsed(clock.instant()));

        log.info("Password reset completed: userId={}", user.id());
    }

    public User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private void applyNewPassword(User user, String newPassword) {
        passwordPolicy.validate(newPassword, user.username(), user.email());

        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new AuthExceptions.WeakPasswordException("New password must differ from the current one");
        }

        Instant now = clock.instant();
        User updated = user.withPasswordHash(passwordEncoder.encode(newPassword), now);
        // A locked account unlocks when its owner proves control by setting a new password.
        users.save(updated.status() == UserStatus.LOCKED ? updated.withSuccessfulLogin(now) : updated);

        refreshTokens.revokeAllForUser(user.id(), now);
    }

    /** Mints an access token and a fresh refresh token, keeping the stored row for the caller. */
    private IssuedPair issuePair(User user) {
        JwtService.IssuedAccessToken accessToken = jwtService.issue(user);

        String rawRefreshToken = tokenGenerator.generate();
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.save(RefreshToken.issue(UUID.randomUUID(), user.id(),
                TokenHasher.hash(rawRefreshToken), now, now.plus(properties.refreshToken().ttl())));

        return new IssuedPair(
                new AuthCommands.Tokens(accessToken.value(), accessToken.ttlSeconds(), rawRefreshToken),
                stored);
    }

    private record IssuedPair(AuthCommands.Tokens tokens, RefreshToken stored) {
    }

    /**
     * A structurally valid BCrypt hash that matches nothing, used to keep the timing of an
     * unknown-user login the same as a real one.
     */
    private static String dummyHash() {
        return "{bcrypt}$2a$12$0000000000000000000000000000000000000000000000000000u";
    }
}
