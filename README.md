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
- Docker + Docker Compose — only needed from Phase 02 onward, when PostgreSQL is introduced
- No global Gradle installation: the committed wrapper (`./gradlew`) pins the Gradle version

Check your JDK:

```bash
java -version
```

If your default JDK is not 21, point Gradle at one explicitly:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Run the application

```bash
./gradlew bootRun
```

The application starts on http://localhost:8080 with the `local` profile.
Phase 00 needs no database, no Redis and no Kafka: the persistence auto-configuration is
deliberately switched off in `application.yml` until Phase 02 introduces PostgreSQL and Flyway.

### Verify it is up

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP", ...}`.
`GET /actuator/info` reports the application name and the current phase.
Every other path returns `403` — Phase 00 has no authentication yet, so nothing is public by
default. JWT authentication and RBAC arrive in Phase 03.

### Run the tests

```bash
./gradlew test
```

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

### Supporting infrastructure (used from Phase 02 onward)

```bash
docker compose up -d postgres
```

## Current state

Phase 00 is complete: Gradle wrapper, Spring Boot skeleton, configuration profiles, health
endpoint, security baseline, Java fundamentals exercises and their tests.
See `PROGRESS.md` for the active phase and `docs/learning/phase-00-java-comeback.md` for the
Java notes that go with the Phase 00 code.
