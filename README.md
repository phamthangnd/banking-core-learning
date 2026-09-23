# BankCore Java — Production-Ready Core Banking Learning Project

BankCore is a production-oriented Modular Monolith built with Java 21 + Spring Boot.
It is designed for a developer returning to Java after a long period away from the ecosystem,
while learning enterprise backend engineering through a realistic core-banking domain.

## Goals

- Refresh modern Java fundamentals.
- Learn Spring Boot / Spring Security / JPA / PostgreSQL.
- Implement banking-grade transaction concepts: ACID, ledger, idempotency, concurrency.
- Add Redis, Kafka, file storage, notifications, audit, reporting and observability.
- Build tests, Docker, CI/CD and production hardening.
- Evolve from Modular Monolith toward Microservices only after the monolith is understood.

## Planned stack

- Java 21 LTS
- Spring Boot 3.x
- Gradle
- PostgreSQL
- Spring Data JPA / Hibernate
- Flyway
- Spring Security + JWT
- Redis
- Kafka
- MinIO
- Testcontainers
- JUnit 5 / Mockito / AssertJ
- OpenAPI / Swagger
- Micrometer / Prometheus / Grafana
- Docker / GitHub Actions

## How to use this repository

1. Read `CLAUDE.md`.
2. Read `PROGRESS.md`.
3. Execute phases in order under `phases/`.
4. Each phase has:
   - objectives
   - prerequisites
   - implementation tasks
   - acceptance criteria
   - tests
   - learning checkpoints
5. Use the agents under `agents/` as specialized execution roles.
6. Never skip a phase's Definition of Done unless explicitly documented in `PROGRESS.md`.

## Local startup

### Prerequisites

- JDK 21 (the Gradle toolchain requires exactly Java 21; the JDK used to launch Gradle may differ)
- Docker + Docker Compose — required: PostgreSQL runs in Docker, and the integration tests start
  their own PostgreSQL through Testcontainers
- No global Gradle installation: the committed wrapper (`./gradlew`) pins the Gradle version

Check your JDK:

```bash
java -version
```

If your default JDK is not 21, point Gradle at one explicitly:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Start PostgreSQL

```bash
docker compose up -d postgres
```

### Run the application

```bash
./gradlew bootRun
```

The application starts on http://localhost:8080 with the `local` profile and connects to the
PostgreSQL above. Flyway creates and migrates the schema on startup; Hibernate runs with
`ddl-auto=validate` and never touches the schema itself.

Redis, Kafka and MinIO in `docker-compose.yml` are not used yet — they arrive in Phase 07 and
Phase 09.

### Verify it is up

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP", ...}`.
`GET /actuator/info` reports the application name and the current phase.

### Create the first administrator

No user is seeded — a password in a migration would be a committed secret. Start the application
once with the bootstrap variable set:

```bash
BANKCORE_BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-password' ./gradlew bootRun
```

Change that password immediately and unset the variable. Set `BANKCORE_JWT_SECRET` too (at least
32 characters); locally a random key is generated per run if it is missing, so tokens stop
working after a restart.

### Log in and call the API

```bash
curl -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"choose-a-strong-password"}'
```

```bash
curl 'http://localhost:8080/api/v1/customers?page=0&size=20' -H 'Authorization: Bearer <accessToken>'
```

Full contracts: [auth](docs/api/auth-api.md), [customers](docs/api/customer-api.md),
[accounts](docs/api/account-api.md), [transactions](docs/api/transaction-api.md) and
[ledger](docs/api/ledger-api.md).

Everything except `/actuator/health`, `/actuator/info` and the public auth endpoints requires a
bearer token, and each operation additionally requires a permission: a `TELLER` may read
customers, an `OFFICER` may also create and close them.

### Run the tests

```bash
./gradlew test
```

Integration tests start their own PostgreSQL container, so Docker must be running. They never
touch the database from `docker compose`.

Coverage report (JaCoCo): `build/reports/jacoco/test/html/index.html`
Test report: `build/reports/tests/test/index.html`

### Configuration profiles

| Profile | Purpose |
|---|---|
| `local` (default) | Local development: verbose logging, health details, `metrics` endpoint exposed |
| `test` | Automated tests: quiet logging, no banner, no external infrastructure |
| `prod` | Production: minimal actuator surface, no health details, Flyway-only schema evolution |

Select a profile explicitly:

```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

## Current state

- **Phase 00 — done.** Gradle wrapper, Spring Boot skeleton, configuration profiles, health
  endpoint, security baseline, Java fundamentals exercises and their tests.
  Java notes: [docs/learning/phase-00-java-comeback.md](docs/learning/phase-00-java-comeback.md).
- **Phase 01 — done.** Customer CRUD: controller, service, domain model, repository port, DTOs
  with Bean Validation, standardized response envelope, global exception handler, correlation
  ids and typed configuration properties.
  API contract: [docs/api/customer-api.md](docs/api/customer-api.md).
  Spring notes: [docs/learning/phase-01-spring-foundation.md](docs/learning/phase-01-spring-foundation.md).
- **Phase 02 — done.** Persistence moved to PostgreSQL: Flyway migrations and indexes, JPA
  entity and adapter behind the same repository port, optimistic locking, paginated/filtered/
  sorted search, and integration tests on Testcontainers.
  Persistence notes, with measured query plans:
  [docs/learning/phase-02-postgres-jpa-flyway.md](docs/learning/phase-02-postgres-jpa-flyway.md).
- **Phase 03 — done.** Users, roles and permissions; JWT access tokens with rotating, hashed
  refresh tokens and reuse detection; registration, login, logout, password change/forgot/reset;
  account lockout, rate limiting and permission checks on service methods.
  API: [docs/api/auth-api.md](docs/api/auth-api.md).
  Security notes: [docs/learning/phase-03-auth-jwt-rbac.md](docs/learning/phase-03-auth-jwt-rbac.md).
- **Phase 04 — done.** Customer profile with KYC and an avatar reference; bank accounts with a
  checked lifecycle, sequence-plus-check-digit account numbers, `BigDecimal` balances with
  explicit currency and overdraft rules enforced by the domain *and* by database constraints.
  API: [docs/api/account-api.md](docs/api/account-api.md).
  Modelling notes: [docs/learning/phase-04-customers-accounts.md](docs/learning/phase-04-customers-accounts.md).
- **Phase 05 — done.** Deposits, withdrawals and transfers inside one database transaction, with
  an immutable transaction history enforced by database triggers, recorded failures and
  paginated statements.
  API: [docs/api/transaction-api.md](docs/api/transaction-api.md).
  Notes: [docs/learning/phase-05-transactions.md](docs/learning/phase-05-transactions.md).
- **Phase 06 — done.** Double-entry ledger with reconciliation reports, idempotency keys,
  row-level locking with a fixed lock order for transfers, reversals through compensating
  transactions, and concurrency tests that prove money is neither created nor destroyed.
  API: [docs/api/ledger-api.md](docs/api/ledger-api.md).
  Notes: [docs/learning/phase-06-ledger-concurrency-idempotency.md](docs/learning/phase-06-ledger-concurrency-idempotency.md).

See `PROGRESS.md` for the active phase.
