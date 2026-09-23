# Observability and Performance

## Investigating an incident

The three signals are joined by one identifier. A customer says "my transfer failed around
14:05":

1. **The response they saw** carries `traceId` in its body and in the `X-Trace-Id` header.
2. **The logs** are JSON with that `traceId` on every line of the request, including the lines
   written inside a Kafka consumer, because the id travels on the event.
3. **The audit trail** (`/api/v1/audit/events`) records the same `traceId` against the action,
   the actor and the outcome.

So the investigation is three lookups on one value, not a text search across a week of logs.

## Structured logs

`local` and `test` print a readable line. Everywhere else, one JSON object per line:

```json
{"@timestamp":"2026-09-23T07:09:23.023Z","level":"INFO","logger_name":"c.e.b.a.application.AuthService",
 "message":"Login succeeded: id=21bdfca6-…","traceId":"52f0a5a4-…","service":"bankcore"}
```

An aggregator can then answer "every ERROR with this traceId" as a field query rather than a
regular expression over a formatted string.

**What is never logged**: passwords, tokens, token hashes, reset links, balances, amounts, full
account numbers, file content. `org.hibernate.orm.jdbc.bind` is pinned to WARN in the logging
configuration *and* in the production profile, because raising it prints every bound parameter —
including personal data and password hashes.

## Metrics

Exposed at `/actuator/prometheus`, which **requires a token unless `bankcore.metrics.public` is
on**. Metrics say how much traffic the bank takes, how often logins fail and how many movements
are rejected — operational detail an anonymous caller has no business reading. It is enabled in
the `local` profile, where nothing outside the machine can reach the port; in production the
management port is bound to an internal interface instead.

Deliberately few: a dashboard with four hundred graphs is one
nobody reads during an incident.

| Metric | Type | Answers |
|---|---|---|
| `bankcore.transactions{type,currency,outcome}` | counter | Throughput and rejection rate |
| `bankcore.transactions.rejections{reason}` | counter | *Why* movements are refused |
| `bankcore.logins{outcome}` | counter | The shape of an attack, before anywhere else |
| `bankcore.transaction.duration{type}` | timer, p50/p95/p99 | Latency, including the tail |
| `bankcore.outbox.pending` | gauge | Is asynchronous processing keeping up? |
| `bankcore.outbox.failed` | gauge | Events nothing will retry — always a person's problem |
| `hikaricp.connections.*` | gauge | Pool saturation and wait time |
| `http.server.requests` | timer | Per endpoint and status, from Spring |

**Cardinality is bounded on purpose.** Every tag has a handful of values. An account id or a
customer id as a tag would create one time series per account, which is how a monitoring system
is taken down by the thing it monitors. The rejection reason is folded into categories for the
same reason.

**No amounts in metrics.** They are scraped by infrastructure with different access rules from
the API: the count of transfers is operational, their value is financial data.

Percentiles rather than averages: an average hides the slow tail, and the slow tail is what users
experience.

## Health probes

`/actuator/health/liveness` and `/actuator/health/readiness` are separate, because an
orchestrator needs to tell "still starting" from "broken". A failing readiness probe takes an
instance out of the load balancer; a failing liveness probe restarts it. Confusing the two
produces restart loops whenever a dependency is slow.

## Slow queries

Hibernate logs any statement over `BANKCORE_SLOW_QUERY_MS` (default 200 ms) with its SQL, so a
slow query appears in the log with the trace id of the request that ran it rather than having to
be reproduced.

`BANKCORE_HIBERNATE_STATISTICS=true` counts queries per session, which turns an N+1 suspicion
into a number. It is off by default because it is not free.

## Connection pool

```yaml
maximum-pool-size: 10          # (2 x cores) + spindles, as a rule of thumb
connection-timeout: 5000       # fail fast rather than queue threads
max-lifetime: 1500000          # below PostgreSQL's own idle timeout
leak-detection-threshold: 20000
```

Bigger is not better. PostgreSQL handles a small number of busy connections far better than a
large number of mostly-idle ones, and every connection costs a backend process. Raise the size
against a measurement of `hikaricp.connections.pending`, not a guess.

The leak detection threshold logs the stack that held a connection for more than twenty seconds,
which is how a leak is found rather than inferred.

## Documented performance considerations

| API | Consideration |
|---|---|
| `GET /customers` | Offset paging; name search is a leading-wildcard `LIKE` and always scans. Measured in Phase 02: 9.66 ms against 0.016 ms for an indexed lookup at 50 000 rows |
| `GET /accounts` | Filtered by `(customer_id, status)`, which a composite index serves |
| `GET /transactions`, account statements | Indexed on both sides plus `occurred_at`; deep offset pages are expensive — an `OFFSET 40000` needed an external merge sort of 5 976 kB versus 4.3 ms for the keyset equivalent |
| Statement export | Keyset paging and a streaming workbook, so memory is flat however long the history is |
| Master data | Cached with explicit eviction; Redis when several instances run |
| Transfers | Row locks in a fixed order; contention shows up as pool wait time, not as corruption |

## Local stack

```bash
docker compose up -d postgres prometheus grafana
```

Prometheus scrapes the application on the host at `host.docker.internal:8080`; Grafana comes up at
http://localhost:3000 with the datasource already provisioned.

Scraping is pull-based on purpose: the monitoring system decides when to ask, a struggling
application cannot flood it, and an instance that stops answering is detectable by its absence.

## Known gaps

- **No alerting rules.** The metrics exist; nothing pages anyone. `bankcore.outbox.failed > 0`
  and a rising `bankcore.outbox.pending` are the first two that should.
- No dashboards are provisioned beyond the datasource.
- No distributed tracing backend. The trace id is propagated, but there is no Zipkin or Tempo to
  view a span tree in.
- The reconciliation reports from Phase 06 are not run on a schedule.
