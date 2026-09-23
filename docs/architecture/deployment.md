# Deployment

## Running the whole stack on a clean machine

```bash
git clone <repository> && cd bankcore-java
docker compose up -d postgres minio prometheus grafana
export BANKCORE_JWT_SECRET="$(openssl rand -base64 32)"
BANKCORE_BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-password' ./gradlew bootRun
```

Nothing else is needed: JDK 21 comes from the Gradle toolchain, Gradle from the committed
wrapper, and every other dependency runs in a container.

| Service | Port | Purpose |
|---|---|---|
| Application | 8080 | The API |
| PostgreSQL | 5432 | Source of truth |
| MinIO | 9000 / 9001 | Object storage, console |
| Prometheus | 9090 | Metrics |
| Grafana | 3000 | Dashboards |
| MailHog | 8025 | Captured email |

Running the application itself as a container:

```bash
docker build -t bankcore:local .
docker run -d --name bankcore --network bankcore-java_default -p 8080:8080 \
  -e BANKCORE_DB_URL=jdbc:postgresql://postgres:5432/bankcore \
  -e BANKCORE_DB_USERNAME=bankcore -e BANKCORE_DB_PASSWORD=bankcore \
  -e BANKCORE_JWT_SECRET="$(openssl rand -base64 32)" \
  bankcore:local
```

## The image

| Decision | Reason |
|---|---|
| Multi-stage build | The build happens in the image, so the result does not depend on the machine that produced it |
| JRE, not JDK, at runtime | The compiler and debugger are attack surface the application never uses |
| Non-root user (uid 1001) | A container process running as root is root on the host kernel |
| `MaxRAMPercentage=75` | Without it the JVM sizes its heap from host memory and is killed by a limit it never knew about |
| `ExitOnOutOfMemoryError` | A JVM thrashing after an OOM is worse than one an orchestrator can restart |
| `HEALTHCHECK` on readiness | Answers "can it take traffic", which is what routing needs |
| Tests **not** run in the build | They need PostgreSQL and MinIO; that belongs in CI, and a build that silently skipped them would be worse than one that never claimed to run them |

## Profiles

| Profile | Used for | Differences |
|---|---|---|
| `local` | A developer's machine | Verbose logging, readable log lines, metrics reachable without a token, health details shown |
| `test` | Automated tests | Quiet, no external infrastructure, datasource from Testcontainers |
| `staging` | Pre-production | Production settings, plus DEBUG application logging and a reachable metrics endpoint |
| `prod` | Production | Minimal actuator, no health details, no server header, no stack traces, JSON logs |

Staging is deliberately **not** a second development environment. It exists to find what only
appears under production configuration, so anything relaxed there is a class of bug customers
will be first to see.

## Configuration

Every secret comes from the environment; none is committed.

| Variable | Required | Notes |
|---|---|---|
| `BANKCORE_DB_URL`, `BANKCORE_DB_USERNAME`, `BANKCORE_DB_PASSWORD` | yes in staging/prod | |
| `BANKCORE_JWT_SECRET` | yes outside local/test | At least 32 characters; the application refuses to start without it |
| `BANKCORE_BOOTSTRAP_ADMIN_PASSWORD` | first start only | Creates the first administrator, then unset it |
| `BANKCORE_S3_*` | for file storage | |
| `BANKCORE_REDIS_ENABLED`, `BANKCORE_KAFKA_SERVERS`, `BANKCORE_EVENTS_ENABLED` | optional | Off by default |
| `BANKCORE_CORS_ORIGINS` | if a browser client exists | Exact origins, comma-separated |

## Migrations

Flyway runs at startup, inside the application, before it accepts traffic.

**The rule that makes rolling deployments safe: a migration must work with the version of the
code that is already running.** During a rolling update both versions are live at once, so:

- Adding a column, a table or an index is safe.
- Dropping or renaming a column is **two releases**: release one stops using it, release two
  removes it. Doing both at once breaks every instance that has not been replaced yet.
- A migration is never edited after it has been applied — Flyway records a checksum and refuses
  to start against a changed one. Corrections ship as a new version, like a reversal corrects a
  posted transaction.
- Long-running changes (a backfill, an index on a large table) belong outside the startup path.
  PostgreSQL's `CREATE INDEX CONCURRENTLY` exists for this, and a deployment that waits ten
  minutes on a migration looks exactly like a deployment that has hung.

Verify before deploying:

```bash
./gradlew flywayInfo    # or: docker exec <container> curl -s localhost:8080/actuator/health
```

## Deployment procedure

1. CI builds, tests and pushes `ghcr.io/<repo>:<commit-sha>` from `main`.
2. Deploy that **sha tag**, never `latest`: a tag that moves cannot identify what is running and
   cannot be rolled back to.
3. The orchestrator starts new instances alongside the old ones. Each runs its migrations, then
   reports readiness.
4. Traffic moves to an instance only when `/actuator/health/readiness` passes.
5. Old instances are drained and stopped.

The application is stateless — sessions are bearer tokens, not server state — so instances can be
added and removed freely.

## Rollback

**If the release contained no migration**, redeploy the previous sha tag. That is the whole
procedure, and it is why images are tagged by commit.

**If it contained a migration**, do not roll the schema back. Restoring an earlier schema means
discarding rows written since, which for a bank means discarding transactions. Instead:

1. Redeploy the previous image, if the older code still works against the new schema — which it
   does when the migration only added things, which is why the rule above exists.
2. If it does not, roll forward: a new migration that corrects the problem, and a new release.

Before either, check what the deployment actually did:

```bash
# Which migrations are applied
psql -c "select version, description, installed_on, success from flyway_schema_history order by installed_rank desc limit 5"

# Whether the ledger still balances
curl -H "Authorization: Bearer <token>" https://<host>/api/v1/ledger/reconciliation
```

A failed deployment that left money in an inconsistent state is a different problem from a failed
deployment, and the reconciliation endpoint is how the two are told apart.

## Known gaps

- No Kubernetes manifests or Helm chart; the procedure above describes what an orchestrator
  should do, not a specific one.
- No blue/green or canary configuration.
- No automated smoke test after deployment; readiness says the application started, not that it
  works.
- No backup or restore procedure for PostgreSQL.
- The image is a fat jar rather than layered, so a release ships every dependency again.
