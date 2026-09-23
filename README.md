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

### Try the Customer API

```bash
curl -i -X POST http://localhost:8080/api/v1/customers -H 'Content-Type: application/json' -d '{"fullName":"Alice Nguyen","email":"alice@example.com","phoneNumber":"+84 90 123 4567","dateOfBirth":"1990-01-01"}'
```

```bash
curl 'http://localhost:8080/api/v1/customers?page=0&size=20&sort=CREATED_AT&direction=DESC'
```

Full contract, including filters, sorting and paging: [docs/api/customer-api.md](docs/api/customer-api.md).

**The customer API is not authenticated yet** (Phase 03 adds JWT + RBAC), so do not expose this
application outside a development machine. Every path other than the customer API and the
health/info endpoints returns `403`: the security baseline denies by default and each phase
opens only what it needs.

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

See `PROGRESS.md` for the active phase.
