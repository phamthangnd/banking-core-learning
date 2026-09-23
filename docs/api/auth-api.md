# Authentication API

Base path: `/api/v1/auth`

## Model

| Token | Form | Lifetime | Stored? | Revocable |
|---|---|---|---|---|
| Access token | Signed JWT (HS256) | 15 min | No | No — it expires |
| Refresh token | Opaque random, 256 bits | 30 days | Yes, **hashed** (SHA-256) | Yes |

An access token is a signed statement the server never looks up, which is what makes it cheap
and what makes it impossible to revoke early. Everything revocable — sessions, logout, the effect
of a password change — hangs off the refresh token, which is stored and checked on every use.

Send the access token as `Authorization: Bearer <token>`.

## Endpoints

| Method | Path | Auth | Success | Purpose |
|---|---|---|---|---|
| POST | `/register` | none | 201 | Self-service registration (role `CUSTOMER`) |
| POST | `/login` | none | 200 | Exchange credentials for a token pair |
| POST | `/refresh` | none | 200 | Exchange a refresh token for a new pair |
| POST | `/logout` | none | 204 | Revoke one refresh token |
| POST | `/logout-all` | bearer | 204 | Revoke every session of the caller |
| POST | `/password/forgot` | none | 202 | Request a reset token |
| POST | `/password/reset` | none | 204 | Redeem a reset token |
| POST | `/password/change` | bearer | 204 | Change own password |
| GET | `/me` | bearer | 200 | Own profile, roles and permissions |

### POST /login

```json
{ "username": "alice", "password": "…" }
```

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshToken": "Rk9SLVRIRS1ET0NVTUVOVEFUSU9O…"
  }
}
```

### POST /refresh

Refresh tokens are **single use**. Every refresh returns a new one and invalidates the old one.

Presenting a token that was already rotated means two parties hold it — the legitimate client and
whoever took it. The response is to revoke **every** session of that user and force a fresh login.
Losing a session is a better outcome than letting a stolen token live for 30 days.

## Access token claims

```json
{
  "iss": "bankcore",
  "sub": "21bdfca6-c7bb-4c8b-8c4c-4d60ac73f935",
  "iat": 1790139295,
  "exp": 1790140195,
  "jti": "b0d978bb-c09b-44a4-a97c-c3ab22498663",
  "username": "admin",
  "authorities": ["ROLE_ADMIN", "customer:read", "customer:write", "customer:close", "…"]
}
```

`sub` is the user id, not the username, so a rename does not invalidate tokens. A JWT is signed,
not encrypted: anyone holding it can read every claim, so nothing sensitive goes in one.

## Roles and permissions

Seeded by migration `V3`. Authorization checks target **permissions**; roles are how permissions
are bundled for humans.

| Role | Permissions |
|---|---|
| `ADMIN` | every permission |
| `OFFICER` | `customer:read`, `customer:write`, `customer:close`, `profile:read` |
| `TELLER` | `customer:read`, `profile:read` |
| `CUSTOMER` | `profile:read` |

Checks are declared on the service methods (`@PreAuthorize("hasAuthority('customer:read')")`), so
they apply to every caller, not only to HTTP requests. Path rules in `SecurityConfig` add a coarse
second layer, and the default is `authenticated()` — a new endpoint is protected the moment it
exists.

## Errors

| HTTP | `error.code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` / `MALFORMED_REQUEST` | Malformed request |
| 401 | `AUTHENTICATION_REQUIRED` | No or invalid bearer token |
| 401 | `INVALID_CREDENTIALS` | Wrong username, wrong password, or wrong current password |
| 401 | `INVALID_TOKEN` | Refresh or reset token unknown, expired, used or revoked |
| 403 | `ACCESS_DENIED` | Authenticated but lacking the permission |
| 403 | `ACCOUNT_NOT_ACTIVE` | Account locked or disabled |
| 409 | `USER_ALREADY_EXISTS` | Username or email already registered |
| 422 | `WEAK_PASSWORD` | Password rejected by the policy |
| 429 | `TOO_MANY_REQUESTS` | Rate limit exceeded |

### What the API deliberately does not tell you

- Unknown user and wrong password give the **same** code and message. Distinguishing them turns
  login into a user-enumeration oracle. An unknown user still runs a password hash, so the timing
  matches too.
- Registration says "username or email is already registered" without saying which.
- `/password/forgot` answers 202 for every syntactically valid address, registered or not.
- Logging out an unknown token answers 204.

## Protections

| Control | Setting | Default |
|---|---|---|
| Password hashing | BCrypt via a delegating encoder (`{bcrypt}` prefix) | strength from Spring defaults |
| Password policy | `bankcore.auth.password.min-length` | 12 characters, deny-list, must not contain the username or email |
| Account lockout | `bankcore.auth.lockout.*` | 5 failed logins → locked 15 min, expires by itself |
| Rate limiting | `bankcore.auth.rate-limit.*` | 10 requests/min per client and endpoint |
| Refresh rotation | always | single use, reuse revokes the whole chain |
| Session revocation | automatic | on logout, password change and password reset |

Lockout and rate limiting cover different attacks: lockout stops guessing against one account,
the limiter stops spraying one password across many accounts and stops hammering `/refresh` and
`/password/reset`, which touch no login counter.

## Configuration

| Property | Env | Notes |
|---|---|---|
| `bankcore.auth.jwt.secret` | `BANKCORE_JWT_SECRET` | At least 32 characters. **No default**: outside `local`/`test` the application refuses to start without it; locally a random key is generated per run |
| `bankcore.auth.jwt.ttl` | | Access-token lifetime, default 15m |
| `bankcore.auth.refresh-token.ttl` | | Default 30d |
| `bankcore.auth.password-reset.ttl` | | Default 30m |

### First administrator

No user is seeded — a password in a migration is a committed secret. On startup, if no user holds
`ADMIN` and `BANKCORE_BOOTSTRAP_ADMIN_PASSWORD` is set, that account is created once:

```bash
BANKCORE_BOOTSTRAP_ADMIN_PASSWORD='…' ./gradlew bootRun
```

Optionally `BANKCORE_BOOTSTRAP_ADMIN_USERNAME` (default `admin`) and
`BANKCORE_BOOTSTRAP_ADMIN_EMAIL`. Change the password immediately and unset the variable.

## Known gaps, by design

- **Access tokens cannot be revoked before they expire** (15 min). Checking a revocation list on
  every request would trade the main benefit of stateless tokens for a database round trip.
  Refresh tokens carry the revocation semantics instead.
- **Rate limiting is per instance**, held in heap. Two instances allow twice the traffic; Redis
  makes the counter shared in Phase 09.
- **`X-Forwarded-For` is ignored** when identifying a client, because it is client-controlled.
  Running behind a proxy needs explicit trusted-proxy configuration — Phase 12.
- **Symmetric signing (HS256).** Fine for one application; a second service that must verify
  tokens it did not issue needs an asymmetric key pair — Phase 13.
- **Reset tokens are not delivered yet.** The sender port logs that a token was issued and drops
  it; email arrives in Phase 07. The raw token is never logged.
- No 2FA and no OAuth2 social login (both listed as optional in the phase specification).
