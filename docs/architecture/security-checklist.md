# Security Checklist

Each item names where it is implemented and what checks it. A claim nothing verifies stops being
true the first time somebody edits a configuration file.

| Control | Where | Verified by |
|---|---|---|
| Password hashing | `JwtConfig.passwordEncoder` — delegating encoder, BCrypt, salted per password | `AuthFlowIntegrationTest` asserts the stored hash starts with `{bcrypt}$2` and never contains the password |
| JWT validation | `JwtConfig.jwtDecoder` — algorithm pinned to HS256, issuer checked, expiry checked | `SecurityHardeningTest` rejects a garbage token, an `alg=none` token and Basic auth |
| Signing key | No default; the application refuses to start without `BANKCORE_JWT_SECRET` outside local/test | `JwtConfigTest` |
| Authorization | `@PreAuthorize` on every service method, plus deny-by-default path rules | `AuthorizationIntegrationTest`, `AccountAuthorizationIntegrationTest`, per-module tests |
| Input validation | Bean Validation at the boundary, business rules in services | `SecurityHardeningTest`, every controller test |
| Rate limiting | `AuthRateLimiter`, `RedisRateLimiter` when several instances run | `AuthRateLimiterTest`, manual 429 check in Phase 03 |
| Account lockout | `User.withFailedLogin` + `SecurityStateRecorder` in its own transaction | `AuthFlowIntegrationTest` |
| File validation | Allow-list per category, magic bytes, size ceiling, generated key, sanitised name | `FileValidationTest`, `FileUploadIntegrationTest` |
| CORS policy | `SecurityHeadersConfig` — exact origins, empty by default, no credentials | `SecurityHardeningTest` asserts a hostile origin gets no allow header |
| Secure headers | `SecurityConfig.headers` — HSTS, CSP, `nosniff`, `DENY`, `no-referrer`, Permissions-Policy | `SecurityHardeningTest` |
| Secret management | Every credential from the environment; nothing committed | `JwtConfigTest`, `AdminBootstrapTest` |
| Sensitive logging | Identifiers only; never passwords, tokens, balances or file content | Phase 03 and 07 manual log greps; `FileUploadIntegrationTest` asserts the audit trail has no file content |
| Audit trail | Append-only, enforced by triggers | `FileUploadIntegrationTest`, trigger tests |
| Financial immutability | Transaction and ledger tables append-only, enforced by triggers | `TransactionImmutabilityTest` |
| Idempotency | Keys settled by a unique index; consumers deduplicate | `ConcurrencyAndIdempotencyIntegrationTest`, `OutboxIntegrationTest` |

## Response headers

```
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; sandbox
Strict-Transport-Security: max-age=31536000 ; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()
```

The CSP is as strict as it goes because a JSON API loads nothing: if a response is ever rendered
as a document, nothing in it may run. The legacy XSS auditor is explicitly **disabled** — it
introduced vulnerabilities of its own, and the CSP is the real control.

## Actuator

Only `health` and `info` are exposed, and health shows no details to anonymous callers. Every
other endpoint is surface: `/env` and `/configprops` print configuration including secrets,
`/heapdump` hands over the entire heap. `SecurityHardeningTest` asserts each of them is
unreachable.

## Production profile

- `server.server-header` blank — the product and version tell an attacker which exploits to try.
- `include-stacktrace: never`, `include-message: never` — a stack trace is a map of the internals.
- `forward-headers-strategy: framework` — behind a TLS-terminating proxy the application must be
  told the real scheme before HSTS or absolute URLs are correct.
- Hibernate SQL and parameter logging pinned to WARN: parameter binding prints personal data and,
  on the auth path, password hashes.

## Dependency scanning

```bash
./gradlew dependencyCheckAnalyze
```

Fails on any dependency with a CVSS score of 7 or higher. It is a **separate task**, not part of
`check`: the first run downloads the National Vulnerability Database, which takes several minutes
and needs network access, and a build that cannot run offline is a build people start skipping.
CI should run it on a schedule with an `NVD_API_KEY`.

## Coverage gate

`check` fails below 80% instruction and 65% branch coverage. The gate exists to catch a collapse,
not to make every refactor a negotiation — current figures are 85% and 70%.

## Known gaps

- **No penetration test.** These are unit and integration tests written by the same person who
  wrote the code; they cannot find what that person did not think of.
- No virus scanning on uploads, no signed download URLs.
- No secret rotation procedure and no vault integration.
- The Kafka publisher and consumers are not covered end to end; they need a broker.
- No web application firewall, no bot protection, no CAPTCHA on the auth endpoints.
