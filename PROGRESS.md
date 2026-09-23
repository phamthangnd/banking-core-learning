# PROGRESS.md — BankCore State Machine

This file is the source of truth for execution state.

## State

CURRENT_PHASE: 04
CURRENT_TASK: customer-account-management
STATUS: READY
BLOCKERS: NONE

## Phase status

| Phase | Name | Status |
|---|---|---|
| 00 | Project Bootstrap & Java Comeback | DONE |
| 01 | Spring Boot Foundation | DONE |
| 02 | PostgreSQL + JPA + Flyway | DONE |
| 03 | Authentication + JWT + RBAC | DONE |
| 04 | Customer + Account Management | READY |
| 05 | Core Banking Transactions | PENDING |
| 06 | Ledger + Concurrency + Idempotency | PENDING |
| 07 | Files + Notifications + Audit | PENDING |
| 08 | Search + Master Data + Import/Export | PENDING |
| 09 | Redis + Kafka + Async Processing | PENDING |
| 10 | Testing + Security Hardening | PENDING |
| 11 | Observability + Performance | PENDING |
| 12 | Docker + CI/CD + Production | PENDING |
| 13 | Microservices Evolution | PENDING |

## Execution rules

- Only one phase is ACTIVE at a time.
- A phase becomes DONE only after its acceptance criteria pass.
- If blocked, record the blocker and stop instead of inventing a workaround.
- Every completed phase must include tests and documentation.
- Keep this file concise; detailed instructions belong in `phases/`.

## Completion checklist

- [x] Phase 00 complete
- [x] Phase 01 complete
- [x] Phase 02 complete
- [x] Phase 03 complete
- [ ] Phase 04 complete
- [ ] Phase 05 complete
- [ ] Phase 06 complete
- [ ] Phase 07 complete
- [ ] Phase 08 complete
- [ ] Phase 09 complete
- [ ] Phase 10 complete
- [ ] Phase 11 complete
- [ ] Phase 12 complete
- [ ] Phase 13 complete

## Phase log

### Phase 00 — Project Bootstrap & Java Comeback (DONE, 2026-09-23)

Delivered:
- Gradle wrapper committed (8.14) with a Java 21 toolchain; `./gradlew` is the only supported build entry point.
- Code quality baseline: `-Xlint:all -parameters`, UTF-8, JaCoCo report on every `test` run.
- Spring Boot skeleton starts with no external infrastructure; PostgreSQL/JPA/Flyway auto-configuration is excluded in `application.yml` until Phase 02.
- Configuration profiles: `local` (default), `test`, `prod`.
- Security baseline (`common/config/SecurityConfig`): stateless, `/actuator/health` and `/actuator/info` public, everything else denied until Phase 03 adds JWT + RBAC. `UserDetailsServiceAutoConfiguration` is excluded so no generated password is ever logged.
- Java exercises with tests:
  - `common/money/Money` — BigDecimal value object, explicit ISO-4217 currency, HALF_EVEN rounding, `Comparable`, `sum`.
  - `transaction/domain/TransactionStatus` — lifecycle PENDING -> POSTED -> REVERSED / PENDING -> FAILED, exhaustive switch transition table, terminal states.
  - `transaction/domain/TransactionRecord` + `TransactionAnalytics` — immutable record, stream aggregations (totals per currency, net position, largest, half-open time ranges, counts per status).
  - `common/concurrency/InMemoryBalanceStore` — atomic read-modify-write via `ConcurrentHashMap.compute`, overdraft rejection.
- Documentation: README local-startup section, `docs/learning/phase-00-java-comeback.md`, package structure in `docs/architecture/overview.md`, updated `gradle-wrapper-note.md`.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 68 tests, 0 failures, 0 skipped, no compiler warnings.
- JaCoCo: 94.9% instruction, 91.1% branch coverage.
- `./gradlew bootRun` — application started in ~1.2s; `GET /actuator/health` returned 200 `{"status":"UP"}`, `GET /actuator/info` returned 200, `GET /api/v1/anything` returned 403. No password or secret in the startup log.

Acceptance criteria:
- [x] application starts
- [x] test suite runs
- [x] package structure exists
- [x] Java exercises are tested
- [x] README explains local startup

Deliberately deferred (not part of Phase 00):
- REST controllers, DTOs, validation and the `@ControllerAdvice` error contract -> Phase 01.
- PostgreSQL, Flyway, JPA entities and Testcontainers integration tests -> Phase 02.
- Authentication, JWT, RBAC -> Phase 03.

### Phase 01 — Spring Boot Foundation (DONE, 2026-09-23)

Delivered:
- Standardized API envelope (`common/api`): one `ApiResponse<T>` shape for success and failure, with `data`, `metadata`, `error`, `timestamp` and `traceId`. Stable `ErrorCode` enum whose *category* (not HTTP status) is what domain code carries.
- `common/web/GlobalExceptionHandler` — the single place mapping failures to HTTP: 400 validation/malformed, 404 not found, 409 conflict, 422 business rule, 500 opaque. Stack traces are logged, never returned.
- Correlation ids (`common/trace` + `CorrelationIdFilter`): `X-Trace-Id` honoured or generated, sanitised against log forging, put in the MDC and cleared in a `finally`, included in every log line and every response.
- `common/config/TimeConfig` — injectable UTC `Clock`, which is what makes the age rules testable.
- Customer module, package by feature:
  - `domain` — immutable `Customer` record (email normalisation, soft close), `CustomerStatus`, `CustomerRepository` port, four business exceptions.
  - `application` — `CustomerService` with all business rules, taking commands rather than web DTOs.
  - `infrastructure` — `InMemoryCustomerRepository` (`ConcurrentHashMap`, bounded reads).
  - `web` — `CustomerController` + request/response DTOs with Bean Validation. No business logic, no try/catch.
  - `config` — `CustomerProperties` record bound to `bankcore.customer.*` and validated at startup.
- Business rules: unique email (case-insensitive), minimum age (configurable, default 18), no future birth date, closed customers are read-only, in-memory capacity ceiling. Customers are closed, never hard-deleted, and closing is idempotent.
- Documentation: `docs/api/customer-api.md` (full contract incl. errors and known gaps), `docs/learning/phase-01-spring-foundation.md`, README Customer API section.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 112 tests, 0 failures, 0 skipped, no compiler warnings.
- JaCoCo: 97.6% instruction, 89.8% branch coverage.
- Test layers: `CustomerServiceTest` (17 business-rule tests, fixed clock), `CustomerTest`, `InMemoryCustomerRepositoryTest`, `CustomerControllerTest` (13 web-slice tests), `CustomerApiIntegrationTest` (4 end-to-end flows).
- Manual smoke test against a running application: POST 201 + `Location`, GET 200, list with metadata, PUT 200, DELETE 204 twice (idempotent), PUT on a closed customer 422, duplicate email 409, underage 422, invalid body 400 with 4 sorted field errors, malformed JSON 400, unknown id 404, `/api/v1/other` still 403. Logs carried the trace id; no email address, password or token appeared in them.

Acceptance criteria:
- [x] CRUD API works
- [x] validation errors are standardized
- [x] business logic is outside controllers
- [x] unit tests pass

Known gap, accepted and recorded:
- `/api/v1/customers/**` is open without authentication because Phase 03 owns auth. The baseline still denies every other path. The application must not be exposed outside a development machine until Phase 03 is done.

Deliberately deferred:
- Pagination, sorting, filtering, indexes and real persistence -> Phase 02.
- JWT, refresh-token rotation, RBAC, rate limiting -> Phase 03.
- Generated OpenAPI document -> Phase 08.

### Phase 02 — PostgreSQL + JPA + Flyway (DONE, 2026-09-23)

Delivered:
- Flyway owns the schema: `V1__create_customers_table.sql` creates `customers` with a primary key, a `ck_customers_status` check constraint, the unique index `ux_customers_email`, the composite index `ix_customers_status_created_at` and the expression index `ix_customers_full_name_lower`. Hibernate runs with `ddl-auto=validate` in every profile and never issues DDL.
- `CustomerEntity` + `CustomerJpaRepository` + `JpaCustomerRepository` adapter in `customer/infrastructure/persistence`. The entity stays inside that package; the service and the API keep working with the immutable domain record and DTOs.
- `@Version` optimistic locking. `save` loads the row and mutates the managed entity so Hibernate's dirty checking writes the UPDATE and the version is incremented; a concurrent modification surfaces as 409 `CONCURRENT_MODIFICATION`.
- Pagination, sorting and filtering: `common/pagination` (`PageRequest`, `PageResult`, `SortDirection`) keeps Spring Data types out of the domain port; `CustomerSortField` is an allow-list so a raw string can never reach a sort clause; `CustomerSpecifications` builds the WHERE clause with the Criteria API so absent filters contribute nothing to the SQL.
- `GET /api/v1/customers` now returns a page with `page`, `size`, `totalElements`, `totalPages` and `hasNext` metadata; requested page size is capped at `bankcore.customer.max-page-size`. Ordering always carries a secondary sort on the id so paging is stable.
- Transactions declared on the service: `@Transactional(readOnly = true)` on the class, `@Transactional` on the write methods.
- The in-memory repository and the `STORAGE_LIMIT_REACHED` rule are gone; the service unit tests use a hand-written `FakeCustomerRepository` in the test sources.
- New handlers: `DataIntegrityViolationException` -> 409 (the unique index, not the service check, is what makes uniqueness true under concurrency), `OptimisticLockingFailureException` -> 409, `RequestRejectedException` -> 400 instead of a 500 from the HTTP firewall. Type-conversion field errors no longer echo internal class names.
- Documentation: `docs/learning/phase-02-postgres-jpa-flyway.md` (entity lifecycle, dirty checking, N+1, transactions, indexes and measured query plans), updated `docs/api/customer-api.md`, `docs/architecture/overview.md` and README.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 127 tests, 0 failures, 0 skipped, no compiler warnings. JaCoCo: 96.1% instruction, 88.4% branch.
- Integration tests run against PostgreSQL 16 in Testcontainers: `FlywaySchemaTest` (migration history, columns, indexes, check constraint), `JpaCustomerRepositoryTest` (round-trip mapping, in-place update, version increment, unique-index rejection, paging, sorting, filtering), `CustomerApiIntegrationTest`, `BankCoreApplicationTests`, `ActuatorEndpointTest`.
- Manual verification against the Docker Compose PostgreSQL: Flyway migrated an empty schema on first start and only validated on the second; 7 customers created through the API were present in `psql`; paging (`page=0..2`, `size=3`), sorting, `name`/`email`/`status` filters and the size cap (requested 999 -> 100) all behaved as documented; data survived an application restart.
- Query plans measured on 50 000 rows: email lookup 0.016 ms (index scan on `ux_customers_email`), status+created_at page 0.040 ms (backward index scan, no sort step), name `LIKE '%...%'` 9.66 ms (sequential scan, 50 006 rows filtered). Deep pagination `OFFSET 40000` needed an external merge sort of 5 976 kB (23.5 ms) versus 4.3 ms for the keyset equivalent. The load-test rows were removed afterwards.

Acceptance criteria:
- [x] schema created only through Flyway
- [x] CRUD persisted in PostgreSQL
- [x] paginated search works
- [x] integration tests use Testcontainers

Environment notes worth keeping:
- Flyway 10 splits per-database support into modules; `flyway-database-postgresql` is required or startup fails with "Unsupported Database: PostgreSQL".
- docker-java (used by Testcontainers) requests Docker API 1.32 by default, which Docker Engine 29 rejects with HTTP 400; the failure appears as "Could not find a valid Docker environment". The test task pins `-Dapi.version=1.44` and points `DOCKER_HOST` at Docker Desktop's socket when it is not already set.
- `(:param is null or column = :param)` JPQL does not work on PostgreSQL for parameters used inside functions (`function lower(bytea) does not exist`), which is why the search uses Specifications.

Known gap, still open:
- `/api/v1/customers/**` remains unauthenticated until Phase 03. The application must not be exposed outside a development machine.

Deliberately deferred:
- Keyset pagination for deep pages, and trigram/full-text search for name lookups -> Phase 08.
- JWT, refresh-token rotation, RBAC, rate limiting -> Phase 03.

### Phase 03 — Authentication + JWT + RBAC (DONE, 2026-09-23)

Delivered:
- Schema (`V2`): `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `refresh_tokens`, `password_reset_tokens`, with unique indexes, check constraints and a self-referencing rotation chain (`ON DELETE SET NULL`). `V3` seeds the role/permission catalogue (ADMIN, OFFICER, TELLER, CUSTOMER).
- `user` module: immutable `User` domain record (normalisation, lockout state, authorities derived from roles), `Role`, ports and JPA adapters. Lookups on the authentication path use `@EntityGraph` to fetch roles and permissions in one query.
- `auth` module: `JwtService` (HS256, 15-min access tokens carrying `sub`/`iss`/`jti`/`authorities`), `SecureTokenGenerator` (256-bit opaque refresh and reset tokens), `TokenHasher` (SHA-256 storage), `PasswordPolicy`, `AuthService` with register/login/refresh/logout/logout-all/change/forgot/reset, `SecurityStateRecorder` (REQUIRES_NEW), `AuthRateLimiter`, `AdminBootstrap`, JPA adapters and the `AuthController`.
- Security: deny-by-default filter chain (`anyRequest().authenticated()`), only the auth endpoints and actuator health/info are public; `@EnableMethodSecurity` with `@PreAuthorize` permission checks on every `CustomerService` method; `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` so 401/403 use the same response envelope; BCrypt via a delegating encoder.
- Refresh-token lifecycle: single use, rotated on every refresh, stored hashed, chain recorded; presenting a rotated token revokes every session of that user. Password change and reset revoke all sessions.
- Anti-enumeration: identical response for unknown user and wrong password (with a dummy hash to match timing), one message for username/email conflicts, 202 for every forgot-password request, 204 for logout of an unknown token.
- Lockout (5 failures → 15 min, self-expiring) and per-client/per-endpoint rate limiting (10/min).
- Secret management: `BANKCORE_JWT_SECRET` has no default — the application refuses to start without it outside local/test, where a random key is generated per run. No seeded user; the first administrator is created once from `BANKCORE_BOOTSTRAP_ADMIN_PASSWORD`.
- Documentation: `docs/api/auth-api.md`, `docs/learning/phase-03-auth-jwt-rbac.md`, updated `docs/api/customer-api.md`, `docs/architecture/overview.md` and README.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 192 tests, 0 failures, 0 skipped, no compiler warnings. JaCoCo: 93.9% instruction, 81.0% branch.
- Test layers: `AuthFlowIntegrationTest` (20 tests: registration, login, lockout, protected endpoints, forged/garbage tokens, refresh rotation, reuse detection, logout), `AuthorizationIntegrationTest` (RBAC per role, both directions), `PasswordFlowIntegrationTest` (reset token single use, invalidation, session revocation, change-password rules), plus unit tests for `PasswordPolicy`, `TokenHasher`, `RefreshToken`, `AuthRateLimiter`, `JwtConfig` and `AdminBootstrap`.
- Manual verification against the running application: Flyway applied V2 and V3; bootstrap created the administrator; unauthenticated `/api/v1/customers` returned 401; a CUSTOMER-role token returned 403 while an ADMIN token returned 201; the decoded JWT carried exactly the seeded authorities; refresh rotated the token, replay returned 401 and revoked the successor session too; rate limiting returned 429 after 10 attempts and recovered after the window; five failed logins locked the account and the correct password then returned `ACCOUNT_NOT_ACTIVE`; password change invalidated the old password. Stored password hashes are BCrypt, stored token hashes are opaque.
- Log hygiene checked explicitly: grepping the application log for every password, JWT, refresh token and signing key used during the session returned zero hits.

Acceptance criteria:
- [x] protected endpoints require valid authentication
- [x] role/permission checks work
- [x] refresh token lifecycle is secure
- [x] password is never stored plaintext
- [x] security tests pass

Bugs found and fixed during the phase (each caught by a test, not by inspection):
- A failed login incremented the counter inside the transaction that the thrown exception then rolled back, so accounts never locked. Fixed with `SecurityStateRecorder` using `REQUIRES_NEW` in a separate bean (a self-invocation would have been a silent no-op).
- The same rollback discarded the session revocation after refresh-token reuse detection.
- `AccessDeniedException` from `@PreAuthorize` reached the controller and was reported as 500; now mapped to 403.
- Repository adapters performed read-modify-write across several Spring Data calls without a transaction, which broke lazy loading of `roles.permissions` outside a service transaction and was a lost-update hazard. All adapters are now transactional.
- `ddl-auto=validate` rejected `CHAR(64)` against the `String` mapping; the migration now uses `VARCHAR(64)`.

Known gaps, recorded deliberately:
- Access tokens cannot be revoked before they expire (15 minutes); revocation semantics live on the refresh token.
- Rate limiting is per instance (heap-based) — Redis in Phase 09.
- `X-Forwarded-For` is ignored when identifying a client; trusted-proxy configuration is Phase 12.
- Symmetric HS256 signing; asymmetric keys when a second service must verify tokens (Phase 13).
- Password-reset tokens are not delivered anywhere yet (the port logs only the user id) — email in Phase 07.
- Optional items from the phase specification not implemented: TOTP/OTP 2FA and OAuth2 social login.
