# PROGRESS.md — BankCore State Machine

This file is the source of truth for execution state.

## State

CURRENT_PHASE: 01
CURRENT_TASK: spring-boot-foundation
STATUS: READY
BLOCKERS: NONE

## Phase status

| Phase | Name | Status |
|---|---|---|
| 00 | Project Bootstrap & Java Comeback | DONE |
| 01 | Spring Boot Foundation | READY |
| 02 | PostgreSQL + JPA + Flyway | PENDING |
| 03 | Authentication + JWT + RBAC | PENDING |
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
- [ ] Phase 01 complete
- [ ] Phase 02 complete
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
