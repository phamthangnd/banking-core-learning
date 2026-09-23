# PROGRESS.md — BankCore State Machine

This file is the source of truth for execution state.

## State

CURRENT_PHASE: 11
CURRENT_TASK: observability-performance
STATUS: READY
BLOCKERS: NONE

## Phase status

| Phase | Name | Status |
|---|---|---|
| 00 | Project Bootstrap & Java Comeback | DONE |
| 01 | Spring Boot Foundation | DONE |
| 02 | PostgreSQL + JPA + Flyway | DONE |
| 03 | Authentication + JWT + RBAC | DONE |
| 04 | Customer + Account Management | DONE |
| 05 | Core Banking Transactions | DONE |
| 06 | Ledger + Concurrency + Idempotency | DONE |
| 07 | Files + Notifications + Audit | DONE |
| 08 | Search + Master Data + Import/Export | DONE |
| 09 | Redis + Kafka + Async Processing | DONE |
| 10 | Testing + Security Hardening | DONE |
| 11 | Observability + Performance | READY |
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
- [x] Phase 04 complete
- [x] Phase 05 complete
- [x] Phase 06 complete
- [x] Phase 07 complete
- [x] Phase 08 complete
- [x] Phase 09 complete
- [x] Phase 10 complete
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

### Phase 04 — Customer + Account Management (DONE, 2026-09-23)

Delivered:
- Schema (`V4`): `customers` gains `kyc_status`, `kyc_reviewed_at` and `avatar_file_id`; new `accounts` table with a sequence for account numbers, a foreign key to `customers`, and check constraints for status, type, currency, `overdraft_limit >= 0`, `balance >= -overdraft_limit`, `closed_at` consistency and a zero balance on closure. Indexes on `(customer_id, status)` and `(status, opened_at)`. `V5` seeds `account:read`, `account:write`, `account:close` and `customer:kyc`.
- Customer profile: `KycStatus` state machine (PENDING -> VERIFIED/REJECTED, back to PENDING for resubmission or re-verification), `decideKyc` behind its own `customer:kyc` permission, and an avatar file reference. Closing a customer now requires that no open account remains.
- `account` module, package by feature: `Account` domain record with the balance rules, `AccountStatus` (`canTransact()`), `AccountType` (whether an overdraft is possible at all), `AccountNumberGenerator` (prefix + database sequence + Luhn check digit), `AccountSearchQuery`/`AccountSortField`, `AccountRepository` port, JPA entity/adapter with Specifications, `AccountService`, `AccountProperties` and `AccountController`.
- Module boundary: `CustomerAccountsPort` is declared in `customer/domain` and implemented by `account/infrastructure/CustomerAccountsAdapter`, so the dependency arrow points one way and the customer module compiles without the account module.
- Balance representation: `Money` (BigDecimal + explicit currency) in the domain, `NUMERIC(19,4)` plus a currency column in the database; `availableBalance` is returned alongside `balance` so clients never compute it themselves.
- Lifecycle as commands (`/activate`, `/freeze`, `/unfreeze`, `/close`) rather than a status field a client can write; activation requires verified KYC; closing requires a zero balance and is idempotent.
- Documentation: `docs/api/account-api.md`, `docs/learning/phase-04-customers-accounts.md`, updated `docs/api/customer-api.md`, `docs/architecture/overview.md` and README.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 280 tests, 0 failures, 0 skipped, no compiler warnings. JaCoCo: 94.8% instruction, 82.9% branch.
- Test layers: `AccountTest` (24 invariant tests), `AccountNumberGeneratorTest` (including mutating every digit of a generated number and requiring each typo to be rejected), `AccountServiceTest`, `JpaAccountRepositoryTest` (round-trip, exact `NUMERIC` amounts, unique number, foreign key, and the check constraints exercised with raw SQL), `AccountApiIntegrationTest`, `AccountAuthorizationIntegrationTest`, plus the extended `CustomerServiceTest` KYC cases.
- Manual verification against the running application: Flyway applied V4 and V5; opening an account returned `PENDING` with a valid number `90040010000013`; activation before KYC returned 422 and succeeded after verification; an overdraft above the configured maximum (0 by default) and any overdraft on a SAVINGS account were both rejected; one customer held two accounts in VND and USD; closing the customer while accounts were open returned 422 and succeeded once they were closed; a one-digit typo in an account number returned 404 while the correct number returned 200; freeze/unfreeze/close behaved as specified. A hand-written `UPDATE accounts SET balance = -1` was rejected by `ck_accounts_balance_within_overdraft`. No ERROR lines and no secrets in the log.

Acceptance criteria:
- [x] customer can own multiple accounts
- [x] account lifecycle is validated
- [x] authorization is enforced
- [x] integration tests pass

Banking rules verified:
- [x] BigDecimal for money, `NUMERIC(19,4)` in storage, no floating point anywhere
- [x] currency is explicit and fixed at opening
- [x] no negative balance unless the account type and a granted overdraft permit it, enforced in the domain and by a check constraint
- [x] closed accounts cannot transact (`AccountStatus.canTransact()`, exercised by `credit`/`debit`)

Issue found and fixed during the phase:
- Adding the `accounts` foreign key broke six existing test classes that cleaned up with `customerJpaRepository.deleteAll()`. The constraint is correct — deleting a customer who still owns an account must fail — so the tests gained a shared `DatabaseCleaner` that truncates the application tables in one statement and leaves migration-seeded reference data alone.

Deliberately deferred:
- Money movement: deposits, withdrawals and transfers -> Phase 05; ledger, double-entry, concurrency and idempotency -> Phase 06.
- Interest, fees and term-deposit maturity rules are not modelled.
- Avatar upload and file validation -> Phase 07 (only the file reference exists today).

### Phase 05 — Core Banking Transactions (DONE, 2026-09-23)

Delivered:
- Schema (`V6`): `transactions` with a reference sequence, foreign keys to `accounts`, check constraints for type, status, currency, positive amount, the shape of each transaction type, and `posted_at` consistency across the lifecycle. Indexes for statements on both sides plus status. Two triggers make the table append-only: any UPDATE touching an immutable column is refused, a terminal status cannot change, and DELETE is refused outright. `V7` seeds `transaction:read` and `transaction:write`.
- `transaction` module: `Transaction` domain record (immutable, shape validated), `TransactionType`, `TransactionReferenceGenerator` (`TXN-<UTC date>-<sequence>`), `TransactionSearchQuery`, repository port, JPA entity/adapter with Specifications, `TransactionService` (deposit/withdraw/transfer/history), `TransactionReferences`, `TransactionFailureRecorder` and `TransactionController`.
- Every balance change happens in the same `@Transactional` unit of work as the transaction row that explains it; a transfer's debit, credit and record commit together or not at all.
- Rejections are recorded as FAILED transactions committed with `REQUIRES_NEW`, so the refusal's rollback does not erase the evidence of it.
- Rules enforced: positive amount, account must be ACTIVE, currency must match, available funds (balance plus granted overdraft) respected, transfer needs two different accounts in the same currency (no silent FX).
- The Phase 00 learning artefacts `TransactionRecord` and `TransactionAnalytics` were retired: the real `Transaction` model supersedes them, and carrying two parallel transaction models would have been worse than losing the exercises. `TransactionStatus` and `TransactionDirection` from Phase 00 are now production code.
- Documentation: `docs/api/transaction-api.md`, `docs/learning/phase-05-transactions.md`, README.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 301 tests, 0 failures, 0 skipped, no compiler warnings. JaCoCo: 94.4% instruction, 81.1% branch.
- Tests: `TransactionTest`, `TransactionReferenceGeneratorTest`, `TransactionApiIntegrationTest` (deposits, withdrawals, transfers, history and authorization — success and failure paths, each failure asserting that the balances did not move), `TransactionImmutabilityTest` (raw SQL against the triggers and check constraints).
- Manual verification against the running application: Flyway applied V6 and V7; a deposit of 1000 produced `TXN-20260923-000000001` with `targetBalanceAfter=1000`; a transfer of 400 left 600 and 400 with the total conserved at 1000; an over-limit withdrawal returned 422, left both balances untouched and appeared in the history as FAILED with its reason; the account statement showed all three entries newest first. `UPDATE transactions SET amount = 1` was refused with "transaction … is immutable; post a compensating transaction instead" and `DELETE` with "financial records must not be deleted". No ERROR lines and no secrets in the log.

Acceptance criteria:
- [x] deposit works
- [x] withdrawal validates available funds
- [x] transfer is atomic
- [x] failures roll back
- [x] transaction history is immutable
- [x] tests cover success and failure paths

Issue found and fixed during the phase:
- The first `posted_at` check constraint read `(status = 'POSTED') = (posted_at IS NOT NULL)`, which refused a reversal — a REVERSED transaction was posted and keeps its timestamp. Constraints have to be written against the lifecycle, not the current state.

Deliberately deferred:
- Idempotency keys, the double-entry ledger, the locking strategy and concurrency tests -> Phase 06.
- A reversal endpoint (the status and the database transition exist; the compensating-transaction flow is Phase 06).
- Scheduled or future-dated transactions, fees, interest and foreign exchange.

### Phase 06 — Ledger + Concurrency + Idempotency (DONE, 2026-09-23)

Delivered:
- Schema (`V8`): `ledger_entries` (balanced pairs, positive amounts with an explicit direction, exactly one of an account or a system account per entry, append-only triggers on UPDATE and DELETE) and `idempotency_keys` with a unique index on `(scope, key)`.
- `ledger` module: `LedgerEntry`, `SystemAccount` (CASH/CLEARING for the bank's side of a deposit or withdrawal), `LedgerEntries.requireBalanced` (the invariant, per currency), port and JPA adapter with database-side totals, `LedgerService` (reconciliation) and `LedgerController`.
- `LedgerPosting` turns every posted transaction into its balanced pair; `LedgerRepository.append` checks the invariant, so no caller can bypass it.
- Idempotency in `common/idempotency`: record, status, port, `RequestFingerprint`, JPA adapter and `IdempotentTransactions`. Claim commits immediately via `INSERT … ON CONFLICT DO NOTHING`, replay returns the original transaction, a key reused with a different request is a 409, and a failed attempt releases its key.
- Concurrency: `AccountRepository.findByIdForUpdate` (`SELECT … FOR UPDATE`) on every balance-changing path, with transfers acquiring both locks in id order so opposite-direction transfers cannot deadlock. Optimistic `@Version` stays for the paths that do not lock.
- Reversal: `POST /transactions/{id}/reverse` posts the mirror image as a new transaction and marks the original REVERSED, which is the compensating-transaction rule from CLAUDE.md section 3.
- Documentation: `docs/api/ledger-api.md`, `docs/learning/phase-06-ledger-concurrency-idempotency.md`, updated `docs/api/transaction-api.md` and README.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 324 tests, 0 failures, 0 skipped, no compiler warnings. JaCoCo: 92.9% instruction, 80.4% branch.
- `ConcurrencyAndIdempotencyIntegrationTest` runs real threads against a real PostgreSQL: 16 concurrent deposits lose nothing; 20 concurrent withdrawals against a balance of 100 produce exactly 10 successes and a balance of 0; 20 opposite-direction transfers all succeed with the total conserved; concurrent work leaves the ledger balanced and both accounts reconciled; a repeated request with the same idempotency key returns the same transaction id and moves the money once; the same key with a different amount is a 409; 8 concurrent retries produce exactly one effect; a failed request releases its key; a rejected withdrawal writes no ledger entries and leaves the balance untouched; a reversal returns the money and leaves the ledger balanced.
- `LedgerEntriesTest` covers the invariant including the case where equal numbers in different currencies must not net off.
- Manual verification: the same idempotency key twice returned the same transaction id and reference, the same key with a different amount returned `IDEMPOTENCY_KEY_CONFLICT`, whole-ledger reconciliation reported balanced, a reversal produced a compensating transaction and left the original REVERSED with the ledger still balanced, and an account created after the ledger existed reconciled exactly (stored 700.0 = derived 700.0). `UPDATE`/`DELETE` on `ledger_entries` were refused with "ledger entries are append-only".

Acceptance criteria:
- [x] concurrent transfer tests pass
- [x] duplicate request tests pass
- [x] ledger balances reconcile
- [x] failed transactions leave no partial state

Invariants verified:
- [x] total debits == total credits (per currency, in the domain and in the reconciliation report)
- [x] one idempotency key cannot create multiple financial effects
- [x] concurrent updates cannot corrupt balances

Issue found and fixed during the phase:
- The first idempotency claim caught `DataIntegrityViolationException` inside its own transaction. Catching it does not clear the rollback-only mark the failed statement leaves, so the commit afterwards threw `UnexpectedRollbackException`. Replaced with `INSERT … ON CONFLICT DO NOTHING`, which never raises.

Known gap, recorded honestly:
- The ledger starts at Phase 06, so balances created in Phase 05 do not reconcile against it. The manual check confirmed both halves of this: the pre-ledger accounts show a mismatch, and an account created afterwards reconciles exactly. A production migration would post opening-balance entries; inventing that history here would have been worse than recording the gap.

Deliberately deferred:
- A scheduled reconciliation job and alerting on a mismatch -> Phase 11.
- Fee, interest and suspense system accounts.

### Phase 07 — Files + Notifications + Audit (DONE, 2026-09-23)

Delivered:
- Schema (`V9`): `stored_files` (metadata, generated storage key, checksum, category, soft delete), `notifications` (with a partial index for the unread query) and `audit_events` (append-only via triggers on UPDATE and DELETE). `V10` seeds `file:read`, `file:write` and `audit:read`.
- `file` module: `FileCategory` with per-category type allow-list and size ceiling, `FileValidation` (allow-list, magic bytes, size, generated key, sanitised name), `StoredFile`, `ObjectStorage` port, `S3ObjectStorage` adapter (AWS SDK against MinIO), JPA adapter, `FileService` and `FileController`. Downloads are always an attachment with `nosniff`.
- `notification` module: `Notification` with idempotent read/unread transitions, repository with a bulk "mark all read", `NotificationService` (ownership-based access, 404 for someone else's notification), `NotificationController`, plus the `EmailSender` port with SMTP and logging implementations.
- `audit` module: `AuditEvent` (actor, action, resource, outcome, trace id, non-sensitive detail), append-only repository, `AuditService` writing with `REQUIRES_NEW` so a failed action still leaves a record, and `AuditController` behind `audit:read`.
- Auditing wired into registration, login success and failure, password change, deposits, withdrawals, transfers, reversals, rejected movements and every file operation.
- `DatabaseCleaner` extended to the new tables; `TRUNCATE` is the intended asymmetry — production cannot delete a financial or audit record, a test can start clean.
- Documentation: `docs/api/files-notifications-audit-api.md`, README.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 354 tests, 0 failures, 0 skipped, no compiler warnings.
- `FileValidationTest` covers the allow-list, magic-byte mismatch (a PHP script announced as `image/png`), size ceiling, empty file, content-type normalisation and path traversal in the filename.
- `FileUploadIntegrationTest` runs against a real MinIO container: bytes round-trip unchanged, an attachment disposition and `nosniff` are set, content that belies its type is rejected, a path-like filename is stripped, soft delete hides the file, `file:write` is required to upload, both the successful upload and the rejection appear in the audit trail with actor and trace id, and the trail contains no file content.
- `NotificationInboxIntegrationTest` covers listing, unread filtering, read/unread toggling, idempotent marking, mark-all, the unread count, and that another user's notification is invisible and returns 404.

Acceptance criteria:
- [x] invalid files are rejected
- [x] audit records identify actor/action/resource/time
- [x] sensitive data is excluded from logs
- [x] notification read/unread state works

Issues found and fixed during the phase:
- `S3ObjectStorage`'s startup check threw when MinIO was unreachable, which took the whole application down with it. Object storage is one feature among many; the check now logs and the file endpoints fail on their own when used.
- The MinIO image is not pullable from Docker Hub in this environment. Both the test container and `docker-compose.yml` now use `quay.io/minio/minio`, which is MinIO's own registry.

Deliberately deferred:
- Virus scanning, signed download URLs and a CDN.
- Moving notification delivery onto a queue -> Phase 09.
- Audit retention and export.

### Phase 08 — Search + Master Data + Import/Export (DONE, 2026-09-23)

Delivered:
- Schema (`V11`): `master_data` (type + code identity, label, sort order, active flag, optimistic locking) seeded with branches, document types, currencies and transaction categories. `V12` seeds `masterdata:read`, `masterdata:write`, `customer:import` and `report:export`.
- `masterdata` module: domain record where the code is identity and only presentation changes, JPA adapter, `MasterDataService` with `@Cacheable`/`@CacheEvict` (in-memory for now; Redis in Phase 09) and `MasterDataController`.
- `common/pagination`: `Cursor` (opaque Base64 `instant|id`) and `KeysetPage`, alongside the existing offset paging. The trade-off is documented — keyset cannot jump to a page number, offset cannot stay cheap at depth.
- Keyset reads on the transaction repository, split into a first-page query and an after-cursor query.
- `report` module: `ImportReport` with row-level errors, `CustomerImportService` (POI, row-independent, reusing `CustomerService` so no rule can be bypassed), `StatementExportService` (SXSSF streaming workbook and a paged PDFBox document, both written straight to the response stream) and `ReportController`.
- Dynamic multi-criteria search already existed from Phases 02, 04, 05 and 07 via Specifications; this phase adds the reference data those filters choose from.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 363 tests, 0 failures, 0 skipped, no compiler warnings.
- `ImportExportIntegrationTest`: valid rows import; a file with a missing name, an underage customer and a malformed date imports the two good rows and reports three errors with the spreadsheet's own line numbers; a duplicate email is a row error; a non-spreadsheet upload is `IMPORT_FAILED`; blank rows are skipped; the Excel statement has one row per transaction under a named header; the PDF starts with `%PDF-`; a 520-transaction history — past the 500-row batch boundary — exports with no duplicated or missing reference; export requires `report:export`.

Acceptance criteria:
- [x] multi-criteria search works
- [x] large datasets remain paginated
- [x] import reports row-level errors
- [x] export does not load unbounded data into memory

Issue found and fixed during the phase:
- The first keyset query passed a null cursor as a bind parameter, and PostgreSQL rejected it with "could not determine data type of parameter" — the same failure mode as the optional filters in Phase 02. Split into two queries rather than handling the null in SQL.

Dependencies added, with justification:
- `org.apache.poi:poi-ooxml` — Java has no built-in xlsx support, and SXSSF's streaming writer is what keeps the export's memory flat.
- `org.apache.pdfbox:pdfbox` — no built-in PDF support either.

Deliberately deferred:
- Asynchronous import with a job id to poll -> Phase 09.
- CSV import, a validating dry run, and a PDF with branding and an embedded font for non-ASCII text.

### Phase 09 — Redis + Kafka + Async Processing (DONE, 2026-09-23)

Delivered:
- Schema (`V13`): `outbox_events` (with a partial index on pending rows) and `processed_events` (composite key of event and consumer).
- Transactional outbox in `common/events`: `OutboxEvent`, `EventPublisher` with `Propagation.MANDATORY` so an event can never be written outside the transaction it belongs to, JPA adapter with `append` joining the caller's transaction, and `OutboxPublisher` draining pending rows to Kafka on a schedule with retry and a FAILED park after five attempts.
- `IdempotentConsumer`: claims each event with `INSERT … ON CONFLICT DO NOTHING`, restores the producer's trace id for the duration of the work, and releases nothing on success so a redelivery does nothing. Deduplication is per consumer.
- `KafkaConsumerConfig`: exponential backoff for transient failures, no retries for unparseable messages, and a `<topic>.DLT` dead-letter topic.
- `NotificationEventConsumer` turns a posted transaction into a notification, so a slow notification cannot hold up a transfer.
- Transaction posting now writes a `TransactionPosted` event carrying identifiers only — no amount, no balance. `AccountOwnerLookup` is a port; `NoCustomerUserLink` returns nothing because no customer-to-user link exists yet, and inventing one would risk notifying the wrong person.
- Redis: `RedisConfig` (shared cache with a TTL on everything and no null caching) and `RedisRateLimiter` (atomic `INCR`, expiry set once per window, fails open when Redis is down). Both behind `bankcore.redis.enabled`, off by default, with the in-memory implementations as the fallback.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 373 tests, 0 failures, no compiler warnings.
- `OutboxIntegrationTest`: a posted transaction queues exactly one event with the right topic, type and key; the payload contains no amount or balance; a rejected transaction queues nothing because the event rolls back with it; the request's trace id travels on the event; publishing outside a transaction throws `IllegalTransactionStateException`; an event delivered five times is handled once; eight concurrent deliveries produce exactly one effect; two consumers each handle the same event once; a failing publish stays PENDING until the attempt limit and is then parked as FAILED.
- Documentation: `docs/architecture/async-and-caching.md`.

Acceptance criteria:
- [x] events are published after successful business transactions (written in the same transaction, published after commit)
- [x] consumers are idempotent
- [x] failures are observable (parked outbox rows, dead-letter topic, logged with trace ids)
- [x] cache invalidation is explicit (`@CacheEvict` on every write)

Issue found and fixed during the phase:
- The outbox and deduplication tables were not in `DatabaseCleaner`, so published counts leaked between tests. Both are now truncated.

Deliberately deferred:
- Change data capture instead of a polling publisher.
- A lock or `SKIP LOCKED` claim so several instances can run the publisher.
- Replay tooling for parked rows and dead-letter records.
- Asynchronous bulk import with a pollable job id.

### Phase 10 — Testing + Security Hardening (DONE, 2026-09-23)

Delivered:
- CORS policy (`SecurityHeadersConfig`): exact origins only, **empty by default**, credentials off because tokens travel in the Authorization header rather than cookies. `setAllowedOrigins` rather than patterns, since a pattern invites a wildcard.
- Security headers on every response: strict CSP, HSTS for a year including subdomains, `nosniff`, `X-Frame-Options: DENY`, `no-referrer`, a Permissions-Policy denying camera/microphone/geolocation/payment, and the legacy XSS auditor explicitly disabled.
- Actuator reduced to `health` and `info` with no health details for anonymous callers.
- Production profile hardened: no server header, no stack traces or messages in error responses, forward-headers strategy for a TLS-terminating proxy, Hibernate SQL and parameter logging pinned to WARN.
- `SecurityHardeningTest`: the checklist as executable assertions — headers present, a hostile origin refused, six dangerous actuator endpoints unreachable, garbage and `alg=none` tokens rejected, error responses free of class names and stack frames, validation refusing malformed registration, a registration response never echoing the password, the HTTP firewall answering 400, and unauthenticated errors still using the standard envelope.
- OWASP dependency-check wired into the build as `dependencyCheckAnalyze`, failing at CVSS 7.0, deliberately outside `check` because the first run needs the NVD download.
- JaCoCo coverage gate on `check`: 80% instruction, 65% branch.
- `docs/architecture/security-checklist.md` maps every checklist item to its implementation and the test that proves it.

Verification:
- `./gradlew clean build` — BUILD SUCCESSFUL, 391 tests, 0 failures, no compiler warnings, coverage gate passed.
- Coverage: 85.5% instruction, 70.0% branch.
- The full suite now spans unit tests (domain rules, validation, policies), integration tests on real PostgreSQL and MinIO, API tests through the whole filter chain, security tests, concurrency tests with real threads, and idempotency tests with concurrent retries.

Acceptance criteria:
- [x] critical business paths are tested end-to-end (auth, customer, account lifecycle, deposits/withdrawals/transfers, ledger reconciliation, idempotency, files, notifications, audit, import/export)

Security checklist:
- [x] password hashing · [x] JWT validation · [x] authorization · [x] input validation · [x] rate limiting · [x] file validation · [x] CORS policy · [x] secure headers · [x] secret management · [x] sensitive logging prevention

Honest notes:
- Branch coverage fell from 80% to 70% in Phase 09: the Kafka publisher and consumers need a broker to exercise and are not covered end to end. The gate was set to 65% to reflect that rather than hiding it.
- `dependencyCheckAnalyze` is wired but was not run in this session; it needs to download the NVD database.
- These tests were written by the same author as the code, so they cannot find what that author did not think of. No penetration test has been done.

Deliberately deferred:
- Virus scanning, signed download URLs, secret rotation and vault integration, a web application firewall, and CAPTCHA on the auth endpoints.
