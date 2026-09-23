# Phase 02 — PostgreSQL, JPA and Flyway Notes

Persistence concepts tied to the code written in Phase 02, with measurements taken on a local
PostgreSQL 16 loaded with 50 000 customers.

## Flyway owns the schema

`ddl-auto=validate` and Flyway are two halves of one rule: the schema is a versioned artifact,
and the application only checks that it matches.

- `ddl-auto=update` looks convenient and is the classic production incident: Hibernate guesses a
  DDL statement, runs it on startup, and nobody reviewed it. It also never drops or renames,
  so the schema silently accumulates.
- A migration that has been applied is **never edited**. Flyway records a checksum; changing an
  applied file makes validation fail on the next start. Corrections ship as a new version — the
  same principle as correcting a posted transaction with a reversal instead of an edit.
- `flyway_schema_history` is the audit trail: which version, when, by whom, how long it took.
- Flyway 10 split per-database support into modules; `flyway-core` alone answers
  "Unsupported Database: PostgreSQL". The project therefore also depends on
  `flyway-database-postgresql`.

## Entity lifecycle and the persistence context

An entity is in one of four states: **transient** (new, unknown to JPA), **managed** (attached to
a persistence context), **detached** (was managed, context closed), **removed**.

The part that surprises returning Java developers: **there is no update statement anywhere.**

```java
CustomerEntity entity = jpaRepository.findById(id).orElseThrow(); // managed
entity.applyState(customer);                                      // just a setter
// at flush/commit Hibernate compares with its snapshot and issues the UPDATE
```

That is dirty checking. It only works while the entity is managed, which is why
`JpaCustomerRepository.save` loads the row first and mutates it, instead of building a fresh
entity from the domain record. Building a detached entity would also reset `@Version` to 0 and
quietly defeat optimistic locking.

`open-in-view: false` closes the persistence context when the service method returns. With it
enabled, lazy loading keeps working inside the view layer, and every unnoticed lazy load turns
into an extra query while the JSON is being written.

## Optimistic locking

`@Version` maps to the `version` column. Hibernate adds `WHERE id = ? AND version = ?` to every
update and increments the column. If another transaction changed the row in between, zero rows
match, and Spring raises `OptimisticLockingFailureException` — mapped here to HTTP 409, telling
the client to re-read and retry.

The alternative, "last write wins", is exactly the lost update that Phase 00 demonstrated with
threads, one layer down.

## N+1 queries

There are no relationships in Phase 02, so there is no N+1 yet — but the mechanism is worth
naming before Phase 04 adds accounts to customers:

```java
List<Customer> customers = repo.findAll();      // 1 query
for (Customer c : customers) c.getAccounts();   // N more, one per customer, lazily
```

Symptoms: a page that is fast with 10 rows and unusable with 500. Detection: log the SQL
(`org.hibernate.SQL: DEBUG`) and count. Fixes: a `JOIN FETCH` query, an `@EntityGraph`, or a
second query that loads all children at once.

`open-in-view: false` matters here: it forces the loading decision into the service, where it is
visible, instead of hiding it in JSON serialisation.

## Transactions

`@Transactional` on the service, not on the controller or the repository: the service method is
the unit of work — everything inside it commits together or not at all.

- `readOnly = true` on reads lets Hibernate skip dirty checking and flushing, and marks the
  transaction so it can be routed to a read replica later.
- Spring's `@Transactional` is implemented with a proxy, so calling one method of the same bean
  from another **bypasses it**. That is the single most common "my transaction did not apply"
  bug.
- Checked exceptions do not roll back by default; unchecked ones do. Financial code must never
  rely on that default by accident.

## Why not `(:param is null or column = :param)`

The obvious way to write an optional filter in JPQL fails on PostgreSQL:

```
ERROR: function lower(bytea) does not exist
```

A null bind parameter inside a function has no type the server can infer. Even where it does
work, a dead `OR ... IS NULL` predicate still has to be evaluated and prevents the planner from
using an index on that column.

`CustomerSpecifications` builds the predicate with the Criteria API instead, so an absent filter
contributes nothing at all to the SQL.

## Indexes and query plans

Measured with `EXPLAIN ANALYZE` on 50 000 rows:

| Query | Plan | Time |
|---|---|---|
| `email = ?` | Index Scan using `ux_customers_email` | **0.016 ms** |
| `status = ? ORDER BY created_at DESC LIMIT 20` | Index Scan Backward using `ix_customers_status_created_at` | **0.040 ms** |
| `lower(full_name) LIKE '%test 25000%'` | Seq Scan, 50 006 rows removed by filter | **9.66 ms** |

Lessons:

- A composite index on `(status, created_at)` serves the filter **and** the sort: the rows come
  out of the index already ordered, so there is no sort step at all.
- A **leading wildcard** defeats every B-tree index, including the expression index on
  `lower(full_name)`. 240× slower here, and it grows with the table. Real text search needs a
  trigram (`pg_trgm`) or full-text index — Phase 08.
- An expression index is only used when the query uses the same expression. `lower(full_name)`
  in the index and `lower(full_name)` in the query, not `full_name ILIKE ...`.

### Deep pagination

`OFFSET` is not free: the database produces and discards every skipped row.

| Query | Plan | Time |
|---|---|---|
| `ORDER BY created_at DESC OFFSET 40000 LIMIT 20` | Seq Scan + **external merge sort, 5 976 kB on disk** | 23.5 ms |
| `WHERE created_at < ? ORDER BY created_at DESC LIMIT 20` | Seq Scan + top-N heapsort in 27 kB | 4.3 ms |

Page 1 and page 2000 cost the same to a client and very different amounts to the server. The fix
is keyset ("seek") pagination — carry the last seen sort value instead of an offset. Worth doing
when a list is genuinely deep; the offset-based API here is honest about its limits.

### Stable ordering

`Sort.by(direction, property).and(Sort.by(ASC, "id"))` — a secondary sort on the primary key.
Without a tiebreaker, rows with equal sort values may come back in a different order for each
page, so a row can appear on two pages or on none.

## Testcontainers

Integration tests run against a real PostgreSQL in Docker, not H2.

- An in-memory database with a different dialect proves the code runs *somewhere*. Migrations,
  constraints, `TIMESTAMPTZ`, index behaviour and the driver itself only get tested honestly
  against the real engine.
- `@ServiceConnection` hands the container's JDBC URL to Spring Boot, so no test knows a
  connection string and none can accidentally hit a developer's database.
- The container is started once per JVM (a static field, deliberately not `@Container`, which
  would stop it after each class). Ryuk, the Testcontainers sidecar, removes it at exit.
- Each test clears the table first: the container is shared, so leftovers would make assertions
  depend on execution order.

### Environment gotcha met in this phase

docker-java (used by Testcontainers) asks for Docker API version 1.32 by default. Docker Engine
29 rejects anything below 1.40 with HTTP 400, and the failure surfaces as the unhelpful
"Could not find a valid Docker environment". The build pins `-Dapi.version=1.44` for the test
JVM.

## Checkpoint answers (see `docs/learning/checkpoints.md`)

- **What is the persistence context?** A first-level cache of managed entities within a
  transaction. It tracks changes and writes them at flush time; the same identity is returned
  for the same row.
- **Lazy vs eager?** Lazy loads an association on first access, eager loads it with the parent.
  Eager everywhere means fetching data nobody asked for; lazy everywhere means N+1 if you loop.
  Decide per query with a fetch join.
- **What causes N+1?** Loading a collection of parents, then touching a lazy association per
  parent, so one query becomes 1 + N.
- **Why use migrations?** The schema becomes reviewable, versioned and repeatable, identical on
  every environment, instead of a side effect of application startup.
