# PROGRESS.md — BankCore State Machine

This file is the source of truth for execution state.

## State

CURRENT_PHASE: 03
CURRENT_TASK: authentication-jwt-rbac
STATUS: READY
BLOCKERS: NONE

## Phase status

| Phase | Name | Status |
|---|---|---|
| 00 | Project Bootstrap & Java Comeback | DONE |
| 01 | Spring Boot Foundation | DONE |
| 02 | PostgreSQL + JPA + Flyway | DONE |
| 03 | Authentication + JWT + RBAC | READY |
| 04 | Customer + Account Management | PENDING |
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
- [ ] Phase 03 complete
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
