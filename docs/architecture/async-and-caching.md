# Asynchronous Processing and Caching

## The problem the outbox solves

A transfer has to do two things: commit to the database and publish an event. They are two
systems with no shared transaction, and both orderings are wrong:

- **Publish, then commit** — the commit fails and consumers have been told about a transfer that
  never happened.
- **Commit, then publish** — the process dies in between and the event is lost forever, silently.

Neither is acceptable when the subject is money.

The **transactional outbox** makes it one transaction:

```
BEGIN
  update balances
  insert transaction
  insert outbox_events   <- the event commits with the change it describes
COMMIT
                          <- a scheduled publisher moves pending rows to Kafka
```

`EventPublisher` uses `Propagation.MANDATORY`, so calling it outside a transaction fails
immediately rather than writing an event that was never backed by a commit. The test asserts that.

## At-least-once, and what follows from it

The publisher may send a message and die before marking the row published; after a restart it
sends it again. That is unavoidable without a distributed transaction, so **every consumer must
be idempotent** — at-least-once is the contract, not a bug to be fixed at the broker.

`IdempotentConsumer` claims each event with an insert into `processed_events`:

```sql
insert into processed_events (event_id, consumer, processed_at)
values (?, ?, ?) on conflict (event_id, consumer) do nothing
```

Whichever delivery inserts the row does the work; the others return immediately. Checking "have I
seen this?" and *then* working has a race in exactly the situation redeliveries create — the
concurrency test fires eight simultaneous deliveries and asserts the work ran once.

Deduplication is **per consumer**: two consumers must each see the event, neither twice.

## Retry and dead letters

| Failure | Treatment |
|---|---|
| Transient (database briefly unavailable) | Exponential backoff, up to 30 s |
| Permanent (malformed message) | Straight to `<topic>.DLT`, no retries |
| Publisher-side failure | Row stays `PENDING` and is retried; after 5 attempts it is parked as `FAILED` |

Retrying a malformed message forever blocks its partition and turns one bad record into an
outage, so `JsonProcessingException` and `IllegalArgumentException` are not retried. A parked
outbox row and a dead-letter record are both visible — the point is that a failure is
*observable* rather than a silent gap.

## What an event may contain

Identifiers and facts. **Never** amounts, balances, credentials or personal data.

```json
{ "transactionId": "…", "reference": "TXN-20260923-000000004",
  "type": "TRANSFER", "currency": "VND", "accountId": "…", "ownerUserId": null }
```

A consumer that needs the amount asks the API, with its own authorization. A Kafka topic is
readable by everything with the credentials to subscribe, and it is retained for days — it is the
wrong place for data that has access rules (CLAUDE.md section 4).

`ownerUserId` is null because nothing links a customer to a login user yet. `NoCustomerUserLink`
says so explicitly rather than inventing a mapping: a notification delivered to the wrong person
is a data breach.

## Redis

| Use | Why Redis rather than the in-memory version |
|---|---|
| Reference-data cache | Two instances each keep their own copy, so an eviction on one leaves the other stale |
| Rate limiting | A per-instance counter lets an attacker spread requests across instances |

Both are off by default (`bankcore.redis.enabled`) so the application runs without Redis, using
the in-memory implementations from Phases 03 and 08. With more than one instance they must be on.

Two rules in the cache configuration:

- **Everything has a TTL.** An entry with no expiry is a memory leak with a plausible excuse, and
  it outlives the bug that forgot to evict it.
- **Nulls are not cached**, so a typo does not become a lasting answer.

The rate limiter uses `INCR`, which is atomic, and sets the expiry only on the first increment of
a window — refreshing it on every request would keep extending the window under load and never
let it reset.

If Redis is unreachable the limiter **fails open**. A rate limiter is a protection, not an
authorisation; failing every login because a cache is down would turn a degraded dependency into
an outage, and account lockout and password hashing still apply. A rate limiter that failed
closed would be a denial-of-service switch.

**Redis is never the source of truth for financial state.** It is a cache and a coordination tool;
PostgreSQL holds the record.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `bankcore.events.enabled` | `false` | Outbox publisher and consumers |
| `bankcore.events.publish-interval-ms` | `1000` | How often pending rows are drained |
| `bankcore.events.max-attempts` | `5` | Attempts before a row is parked as `FAILED` |
| `bankcore.redis.enabled` | `false` | Shared cache and rate limiter |

Producers use `acks=all` with idempotence enabled; consumers do not auto-commit, so a crash
mid-work redelivers rather than skips.

## Known gaps, by design

- The publisher polls. A busy system would use logical replication (change data capture) instead
  of a scheduled query.
- Several instances would each poll the same pending rows; the publisher needs a lock or a
  `SKIP LOCKED` claim before it is run more than once.
- Failed outbox rows and dead-letter records have no replay tooling yet.
- Bulk import is still synchronous; moving it to a job with a pollable id is the obvious next use
  of this machinery.
