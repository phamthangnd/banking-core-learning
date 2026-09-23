# Phase 03 — Authentication, JWT and RBAC Notes

Security concepts tied to the code written in Phase 03.

## Authentication vs authorization

Two different questions, answered in two different places:

- **Authentication** — who is this? The bearer token is verified, its claims become an
  `Authentication` in the security context. Done by the filter chain.
- **Authorization** — may they do this? `@PreAuthorize` on the service method checks a
  permission. Done at the service boundary, so it applies to every caller.

Path rules in `SecurityConfig` are a coarse first layer ("is this path public?"); they are not a
substitute for the method checks, because a batch import or a message consumer never passes a
controller.

## Password storage

`PasswordEncoderFactories.createDelegatingPasswordEncoder()` produces hashes prefixed with the
algorithm: `{bcrypt}$2a$10$…`. Two consequences worth understanding:

- The algorithm can change later; old hashes still verify because each one says how it was made.
- BCrypt is **deliberately slow**, and that is the point. A stolen hash is attacked offline, at
  the attacker's pace, so the only defence is making each guess expensive. It also salts every
  hash, which is why two identical passwords produce different stored values and why a rainbow
  table is useless.

Compare with `TokenHasher`, which uses plain SHA-256 for refresh tokens. The opposite reasoning
applies: those tokens are 256 bits of cryptographic randomness, so guessing is not a threat, and
a slow hash would only add latency to every refresh. The property that matters — a database dump
contains nothing usable — is achieved either way.

**Never** store a password reversibly encrypted. Encryption implies a key, a key implies someone
who can decrypt, and a password nobody can read is the entire requirement.

## Access token vs refresh token

| | Access token | Refresh token |
|---|---|---|
| Form | signed JWT | opaque random value |
| Checked against the database | no | yes, on every use |
| Lifetime | 15 minutes | 30 days |
| Revocable | no | yes |

The trade is explicit: statelessness makes the access token cheap to verify (a signature check,
no I/O) and impossible to revoke. Keeping it short bounds the damage, and everything that must be
revocable lives on the refresh token.

The obvious alternative — check a revocation list on every request — gives back revocation and
gives up the reason for using a JWT in the first place.

## Refresh rotation and reuse detection

Each refresh token works exactly once. A refresh returns a new one and marks the old one revoked,
recording which token replaced it (`replaced_by`).

That chain is what makes theft detectable. If a stolen token is used, the legitimate client's next
refresh presents an already-rotated token — or the reverse. Either way the server sees a token
that was used twice, which cannot happen legitimately, and revokes **every** session of that user.

The alternative, a long-lived reusable refresh token, is a 30-day password that nobody can notice
being stolen.

## What the API refuses to reveal

Several endpoints deliberately give away less than they could:

| Situation | Response | Why |
|---|---|---|
| Unknown user vs wrong password | identical 401 | otherwise login is a user-enumeration oracle |
| Unknown user login | still runs a password hash | otherwise the *timing* enumerates users |
| Registration conflict | "username or email" | saying which reveals a registered address |
| `/password/forgot` for any address | 202 | otherwise it tests which emails are registered |
| Logout of an unknown token | 204 | otherwise it tests which tokens exist |

These cost nothing for a legitimate user, who already knows their own account.

## Lockout and rate limiting

Different attacks, so both are needed:

- **Lockout** (5 failures → 15 minutes) stops guessing many passwords against one account.
  It expires by itself, so a locked-out user is not a support ticket, and an attacker gains
  nothing permanent by locking someone out.
- **Rate limiting** (10 per minute per client and endpoint) stops spraying one common password
  across many accounts, which never trips a single account's counter, and stops hammering
  `/refresh` and `/password/reset`, which have no counter at all.

Keys include the endpoint so a burst of logins cannot consume the budget for password resets.

## The transaction trap that made lockout silently useless

A failed login ends by throwing. `AuthService.login` is `@Transactional`. An unchecked exception
rolls that transaction back — **including the incremented failure counter**. The account would
never lock, and nothing would look broken: the test that caught it asserted `LOCKED` and got
`ACTIVE`.

The same applies to revoking sessions after detecting a replayed refresh token: the revocation
must outlive the rejection that follows it.

`SecurityStateRecorder` writes these facts with `Propagation.REQUIRES_NEW`, which suspends the
caller's transaction and commits in one of its own. It must be a **separate bean**: Spring's
transaction support is proxy-based, so calling a `REQUIRES_NEW` method on `this` does nothing at
all — the single most common way this pattern is written wrong.

## Secrets and configuration

The signing key has **no default**. Outside `local`/`test` the application refuses to start
without `BANKCORE_JWT_SECRET`; locally it generates a random key per run, so tokens simply stop
working across restarts.

A committed fallback secret would be a production key in a public repository — and every
deployment that forgot to override it would share the same one, meaning anyone could mint tokens
for anyone.

The same reasoning drives `AdminBootstrap`: no seeded administrator, because a password in a
migration is a committed secret, and no default password, because default credentials are the
most exploited weakness in self-hosted software.

## Logging

Logged: user ids, trace ids, outcomes, error codes.
Never logged: passwords, access tokens, refresh tokens, token hashes, reset links, JWT secrets.

This was verified rather than assumed — the Phase 03 manual check greps the application log for
every password, token and key used during the session and expects zero hits.

Note `LoggingPasswordResetTokenSender`: it would be very convenient to log the reset link for
local testing, and that is exactly the convenience that puts credentials into log aggregators.
The token leaves through the port and nowhere else; tests capture it by replacing the port.

## JWT pitfalls avoided

- **Algorithm confusion.** `NimbusJwtDecoder…macAlgorithm(HS256)` pins the algorithm. A decoder
  that trusts the token's own `alg` header is the classic JWT vulnerability — including `alg:
  none`.
- **Issuer not checked.** `JwtValidators.createDefaultWithIssuer` rejects tokens minted elsewhere.
- **Authority prefixes.** Spring's default converter reads `scope`/`scp` and prefixes everything
  with `SCOPE_`, which would turn `ROLE_ADMIN` into `SCOPE_ROLE_ADMIN` and break every role check.
  The converter here reads the `authorities` claim verbatim.
- **Sensitive claims.** A JWT is signed, not encrypted. Anything in it is readable by the holder.

## CSRF, cookies and why it is disabled here

CSRF protection defends **cookie-based** sessions: the browser attaches cookies automatically, so
a hostile page can make a request that carries the victim's credentials. A bearer token in an
`Authorization` header is never attached automatically, so there is nothing to forge.

Disabling CSRF is therefore correct **because** the API is stateless and token-based. Moving tokens
into cookies would make it required again — the two decisions are linked, and switching one
silently without the other is how this goes wrong.

## Checkpoint answers (see `docs/learning/checkpoints.md`)

- **Authentication vs authorization?** Who you are vs what you may do. The first produces an
  identity; the second checks a permission against it.
- **Access token vs refresh token?** A short-lived signed statement that is never looked up,
  versus a long-lived stored secret that is checked and can be revoked.
- **Why hash passwords?** So a database breach does not hand over credentials. Slow, salted
  hashing makes an offline attack expensive; encryption would be reversible, which defeats the
  purpose.
