# BankCore

A core-banking backend built as a **modular monolith** in Java 21 and Spring Boot, with one
service extracted at the end to demonstrate when that is worth doing.

It was written phase by phase as a way back into modern Java and enterprise backend engineering,
so the code is deliberately explicit and the reasoning behind each decision is written down next
to it. All fourteen phases are complete; `PROGRESS.md` records what each one delivered, how it was
verified, and what was deliberately left out.

**391 tests**, 13 Flyway migrations, integration tests against real PostgreSQL and MinIO
containers.

---

## What it does

| Area | Capabilities |
|---|---|
| **Auth** | Registration, login, logout, JWT access tokens, rotating hashed refresh tokens with reuse detection, password change / forgot / reset, account lockout, rate limiting |
| **RBAC** | 4 roles, 19 permissions, checks on service methods so they apply to every caller |
| **Customers** | CRUD, KYC lifecycle, avatar reference, paginated and filtered search |
| **Accounts** | Lifecycle (pending → active ↔ frozen → closed), account numbers with a check digit, `BigDecimal` balances with explicit currency, overdrafts by account type |
| **Transactions** | Deposits, withdrawals, transfers — atomic, with an immutable history and recorded failures |
| **Ledger** | Double-entry entries per movement, reconciliation reports, reversals as compensating transactions |
| **Concurrency** | Row locks in a fixed order, optimistic locking, idempotency keys |
| **Files** | Upload/download via MinIO with allow-list and magic-byte validation |
| **Notifications** | Per-user inbox, email port, and an extracted service consuming Kafka events |
| **Audit** | Append-only trail of actor, action, resource, outcome and trace id |
| **Reporting** | Master data, bulk import with row-level errors, streaming Excel and PDF export |
| **Async** | Transactional outbox, idempotent consumers, retries and a dead-letter topic |
| **Operations** | Structured JSON logs, Micrometer metrics, Prometheus/Grafana, health probes, Docker image, GitHub Actions |

---

## Stack

Java 21 · Spring Boot 3.4 · Gradle (wrapper committed) · PostgreSQL 16 · Flyway · Spring Data JPA ·
Spring Security + JWT (Nimbus) · Redis · Kafka · MinIO (S3 API) · Apache POI · PDFBox ·
Micrometer / Prometheus / Grafana · Testcontainers · JUnit 5 / AssertJ / Mockito · Docker ·
GitHub Actions

---

## Quick start

### Prerequisites

- **JDK 21.** The Gradle toolchain requires exactly 21; the JDK that launches Gradle may differ.
- **Docker.** PostgreSQL runs in a container, and the integration tests start their own through
  Testcontainers.
- No global Gradle: the committed wrapper pins the version.

```bash
java -version

# If your default JDK is not 21, point Gradle at one explicitly.
# macOS with an Apple-registered JDK:
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# macOS with a Homebrew JDK, which java_home does not list:
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

### Run it

```bash
docker compose up -d postgres

export BANKCORE_JWT_SECRET="$(openssl rand -base64 32)"
BANKCORE_BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-password' ./gradlew bootRun
```

The application starts on http://localhost:8080 with the `local` profile. Flyway migrates the
schema on startup; Hibernate runs with `ddl-auto=validate` and never touches the schema itself.

No user is seeded — a password in a migration would be a committed secret. The bootstrap variable
creates the first administrator **only when the database has none**, so it does nothing on a
database that has already been started once. Change that password immediately and unset the
variable.

If you are starting from an existing database and cannot log in, that is why: the administrator
already exists with whatever password it was created with.

Without `BANKCORE_JWT_SECRET` a random key is generated per run locally, so tokens stop working
after a restart. Outside `local` and `test` the application refuses to start without it.

### Check it

```bash
curl http://localhost:8080/actuator/health          # {"status":"UP",...}
curl http://localhost:8080/actuator/info            # application name and status
```

### Call the API

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"choose-a-strong-password"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

curl "http://localhost:8080/api/v1/customers?page=0&size=20" -H "Authorization: Bearer $TOKEN"
```

Everything except `/actuator/health`, `/actuator/info` and the public auth endpoints needs a
bearer token, and each operation additionally needs a permission — a `TELLER` may read customers,
an `OFFICER` may also create and close them.

### Optional services

```bash
docker compose up -d minio                     # file storage
docker compose up -d prometheus grafana        # metrics at :9090, dashboards at :3000
docker compose up -d kafka redis               # then set BANKCORE_EVENTS_ENABLED=true / BANKCORE_REDIS_ENABLED=true
docker compose up -d mailhog                   # captured email at :8025
```

Redis and Kafka are **off by default**: the application runs without them, using in-memory
equivalents. With more than one instance they must be on, because a per-instance cache or rate
limiter is not the control it appears to be.

### Run the tests

```bash
./gradlew test          # or: ./gradlew build  (adds the coverage gate)
```

Docker must be running. Integration tests start their own PostgreSQL and MinIO containers and
never touch the database from `docker compose`. The Kafka paths are tested without a broker — the
outbox and the consumer deduplication are exercised directly, which is where the behaviour that
matters lives.

Reports: `build/reports/tests/test/index.html`, `build/reports/jacoco/test/html/index.html`

```bash
./gradlew dependencyCheckAnalyze   # OWASP scan; the first run downloads the NVD database
```

---

## Documentation

### API contracts

| Document | Covers |
|---|---|
| [auth-api.md](docs/api/auth-api.md) | Registration, login, tokens, password flows, roles and permissions |
| [customer-api.md](docs/api/customer-api.md) | Customer CRUD, search, KYC |
| [account-api.md](docs/api/account-api.md) | Account lifecycle, numbers, balances, overdrafts |
| [transaction-api.md](docs/api/transaction-api.md) | Deposits, withdrawals, transfers, history, idempotency, reversal |
| [ledger-api.md](docs/api/ledger-api.md) | Ledger entries and reconciliation |
| [files-notifications-audit-api.md](docs/api/files-notifications-audit-api.md) | Uploads, the inbox, the audit trail |
| [masterdata-reports-api.md](docs/api/masterdata-reports-api.md) | Reference data, import, export |
| [api-guidelines.md](docs/api/api-guidelines.md) | The conventions all of the above follow |

### Architecture and operations

| Document | Covers |
|---|---|
| [overview.md](docs/architecture/overview.md) | Modules, layering, current state |
| [ADR-001](docs/architecture/adr/ADR-001-modular-monolith.md) | Why a modular monolith first |
| [ADR-002](docs/architecture/adr/ADR-002-service-extraction.md) | Which service was extracted, which were not, and why |
| [async-and-caching.md](docs/architecture/async-and-caching.md) | Outbox, at-least-once delivery, idempotent consumers, Redis |
| [security-checklist.md](docs/architecture/security-checklist.md) | Every control, where it lives, and the test that proves it |
| [observability.md](docs/architecture/observability.md) | Investigating an incident, metrics, pool tuning, performance notes |
| [deployment.md](docs/architecture/deployment.md) | Image, profiles, migrations, deployment and rollback |
| [financial-invariants.md](docs/database/financial-invariants.md) | The rules the money code must never break |

### Learning notes

Written for a developer returning to Java. Each one explains the concepts behind that phase's
code, with the mistakes that were actually made along the way.

[Java fundamentals](docs/learning/phase-00-java-comeback.md) ·
[Spring foundation](docs/learning/phase-01-spring-foundation.md) ·
[PostgreSQL, JPA, Flyway](docs/learning/phase-02-postgres-jpa-flyway.md) ·
[Auth, JWT, RBAC](docs/learning/phase-03-auth-jwt-rbac.md) ·
[Customers and accounts](docs/learning/phase-04-customers-accounts.md) ·
[Transactions](docs/learning/phase-05-transactions.md) ·
[Ledger, concurrency, idempotency](docs/learning/phase-06-ledger-concurrency-idempotency.md) ·
[Checkpoint questions](docs/learning/checkpoints.md)

---

## Structure

```
src/main/java/com/example/bankcore/
  common/        API envelope, errors, pagination, money, idempotency, events, trace, config
  user/          users, roles, permissions
  auth/          authentication, tokens, password flows, rate limiting
  customer/      customer profile and KYC
  account/       accounts and their lifecycle
  transaction/   deposits, withdrawals, transfers
  ledger/        double-entry entries and reconciliation
  file/          object storage and upload validation
  notification/  the inbox and the email port
  audit/         the append-only trail
  masterdata/    editable reference data
  report/        import and export

services/notification-service/   extracted in Phase 13: own database, own deployment
```

Each module is layered `web → application → domain ← infrastructure`. The repository interface
lives in `domain` and its implementation in `infrastructure`, so the dependency arrow points
inward — which is what let Phase 02 swap an in-memory store for PostgreSQL without touching a
business rule.

Where one module needs a fact from another, the *needing* module declares a port and the other
implements it (`CustomerAccountsPort`). No cycles.

---

## Configuration

Every secret comes from the environment; none is committed.

| Variable | Required | Notes |
|---|---|---|
| `BANKCORE_JWT_SECRET` | outside local/test | At least 32 characters |
| `BANKCORE_DB_URL` / `_USERNAME` / `_PASSWORD` | staging, prod | |
| `BANKCORE_BOOTSTRAP_ADMIN_PASSWORD` | first start only | Creates the first administrator |
| `BANKCORE_S3_ENDPOINT` / `_BUCKET` / `_ACCESS_KEY` / `_SECRET_KEY` | for files | Defaults match the local MinIO |
| `BANKCORE_REDIS_ENABLED` / `_HOST` / `_PORT` | optional | Shared cache and rate limiter |
| `BANKCORE_EVENTS_ENABLED`, `BANKCORE_KAFKA_SERVERS` | optional | Outbox publisher and consumers |
| `BANKCORE_CORS_ORIGINS` | if a browser client exists | Exact origins; empty means none |
| `BANKCORE_DB_POOL_SIZE`, `BANKCORE_SLOW_QUERY_MS` | optional | Tuning |

### Profiles

| Profile | Purpose |
|---|---|
| `local` (default) | Readable logs, health details, metrics reachable without a token |
| `test` | Quiet, no external infrastructure, datasource from Testcontainers |
| `staging` | Production settings plus DEBUG application logging |
| `prod` | Minimal actuator, no health details, no server header, no stack traces, JSON logs |

```bash
SPRING_PROFILES_ACTIVE=staging ./gradlew bootRun
```

---

## Design decisions worth knowing

A few that shaped everything else. The reasoning for each is in the code and the linked document.

- **Money is `BigDecimal` with an explicit currency**, end to end — DTO, domain and
  `NUMERIC(19,4)` in the database. Never a floating-point type at any layer.
- **Banking invariants live twice**: in the domain type that owns the data, and as a database
  constraint. `balance >= -overdraft_limit` is a check constraint, so a bug cannot create money
  quietly. Transactions, ledger entries and audit events are append-only, enforced by triggers
  that still hold when somebody runs SQL by hand.
- **A balance never changes without the transaction row that explains it**, in the same database
  transaction. That is what makes a transfer atomic.
- **Facts that must survive a failure get their own transaction.** A failed-login counter, a
  rejected transaction and an audit entry are all written with `REQUIRES_NEW`, because the
  exception that caused them rolls everything else back.
- **Uniqueness and idempotency are settled by the database**, not by a check followed by an
  insert — the race lives exactly between those two steps.
- **The API never says more than it should.** Unknown user and wrong password give the same
  answer; a reused idempotency key with a different body is a 409 rather than someone else's
  result.
- **Logs, metrics and events carry identifiers, never amounts or secrets.** One trace id joins
  the response, every log line and the audit entry.

---

## Status

All fourteen phases are complete — see `PROGRESS.md` for each phase's deliverables, verification
and deferred work, and `phases/` for the original specifications.

### Known gaps, recorded rather than hidden

- **No OpenAPI/Swagger document.** The API contracts are hand-written Markdown.
- **The ledger starts at Phase 06**, so balances created before it do not reconcile against it. A
  production migration would post opening-balance entries.
- **Kafka publisher and consumers are not covered end to end** — they need a broker, which is why
  branch coverage sits at 70%.
- **`dependencyCheckAnalyze` is wired but has not been run**; it needs the NVD download.
- **No penetration test.** These tests were written by the same author as the code and cannot
  find what that author did not think of.
- **The extracted service's read API has no authentication**; it belongs behind the same gateway
  validating the same token, and a second implementation would be worse than either.
- **The monolith still contains its own notification module.** Both consume the same topic under
  different group ids — the strangler arrangement. Removing the old path is not done.
- No virus scanning on uploads, no signed download URLs, no vault integration, no Kubernetes
  manifests, no alerting rules, no backup and restore procedure.

---

## Working on it

`CLAUDE.md` holds the engineering rules this project is built to: architecture boundaries,
banking rules, security requirements, testing expectations and the Definition of Done. They are
not suggestions — most of the decisions above exist because of them.

Commits follow conventional commits, one per phase.
