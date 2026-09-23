# ADR-002: Which services to extract, and which not to

## Status

Accepted. Supersedes nothing; extends ADR-001 (modular monolith first).

## Context

Phase 13 asks when and how to extract services. The phase's own rule is the starting point:

> Do not split services merely because it is fashionable. Document the reason, trade-offs,
> operational cost and consistency implications.

The monolith has enforced module boundaries since Phase 01: each module owns its domain, declares
its ports, and reaches other modules only through an interface it declares itself
(`CustomerAccountsPort`) or through an event. That is what makes extraction a mechanical job
rather than an archaeology project — and, more importantly, what makes *not* extracting cheap.

## What extraction actually costs

Every one of these is paid per service, on the first day, before any benefit arrives:

| Cost | What it means in practice |
|---|---|
| A network between modules | A method call becomes a request that can be slow, time out, or half-succeed |
| No shared transaction | "Debit and credit atomically" stops being available; sagas and compensation replace it |
| Two deployments | Two pipelines, two sets of credentials, two rollbacks, two on-call runbooks |
| Versioned contracts | A field cannot simply be renamed; both sides must tolerate both shapes for a release |
| Distributed debugging | One trace id across processes, and a log aggregator that can join them |
| Eventual consistency | A read can legitimately be stale, and every screen must decide what to do about that |

A monolith pays none of these. The bar for extraction is therefore not "could we?" but "does a
specific problem exist that only a separate deployment solves?".

## Bounded contexts

The module boundaries drawn in Phases 01–09 are already the contexts. Data ownership matters more
than code:

| Context | Owns | Reads from others |
|---|---|---|
| **Auth / User** | users, roles, permissions, refresh and reset tokens | nothing |
| **Customer** | customers, KYC state | asks the account context whether a customer still holds open accounts |
| **Account** | accounts, balances | reads customer KYC before activating an account |
| **Transaction / Ledger** | transactions, ledger entries, idempotency keys | locks and updates account balances |
| **Notification** | notifications | nothing — only consumes events |
| **Reporting** | nothing; it reads | customers, accounts, transactions |
| **File / Audit** | stored file metadata, audit events | nothing |

## Decision, per candidate

### Notification — **extract** ✅ (done)

The only one with a case that does not need arguing:

- **It owns nothing anyone else needs.** No other module reads notifications, so there is no
  query that would become a network call.
- **It already communicates by event.** Phase 09 made the transaction path publish
  `TransactionPosted` to the outbox; the notification consumer was already the only reader.
  Extraction moved a consumer, not a dependency.
- **Its failure must not be the bank's failure.** A slow email provider must never slow a
  transfer. In the monolith that is a discipline; as a separate process it is a fact.
- **It scales differently.** Notification volume follows fan-out, not transaction volume.
- **Eventual consistency is already acceptable here.** A notification arriving a second late is
  not a defect; a balance arriving a second late is.

Extracted in `services/notification-service`: its own database, its own migrations, its own
consumer group, and no shared code with the monolith — a shared library would recreate the
coupling and force the two to be released together.

### Reporting — **extract later**, when a reason appears ⏳

The plausible next candidate, for one reason only: exports are long-running and memory-hungry
(Phase 08), and a large statement export competing with transaction processing for the same heap
and the same connection pool is a real problem.

Not yet, because it is not a problem yet, and because reporting reads data three other contexts
own. Extracting it means either a read replica or an event-sourced projection — real work, worth
doing when the contention is measured rather than imagined.

### Auth — **do not extract** ❌

Tempting, because "an identity service" sounds like an obvious boundary. Against it:

- Every request already validates the JWT locally with no call to anything. Authentication is
  *already* decoupled at runtime; a separate service would add a deployment without removing a
  dependency.
- Users, roles and permissions are read on almost every request. Making that a network call adds
  latency to everything.
- The extraction people actually want here is a managed identity provider (OIDC), not a
  hand-written auth service. If the requirement arrives, adopt one — do not build one.

### Customer and Account — **do not extract** ❌

These two are the strongest argument for staying together. Opening an account reads customer KYC;
closing a customer checks for open accounts. Split them and:

- both rules cross a network, so both can fail halfway;
- "no account may be activated for an unverified customer" becomes eventually consistent, which
  means an account can be active for a customer whose KYC was just rejected;
- the cross-module port (`CustomerAccountsPort`) becomes a synchronous call in the customer
  close path, which is a rule that can now time out.

Nothing is gained, and a correctness property is traded for a deployment boundary.

### Transaction and Ledger — **do not extract** ❌❌

The strongest no in the list.

A transfer debits one account, credits another, writes a transaction and writes two balanced
ledger entries — **in one database transaction**. That single property is what makes the money
safe, and it is exactly what a service boundary takes away. Splitting the ledger from the
transaction means replacing an ACID transaction with a saga: compensating entries, in-doubt
states, a reconciliation process for the gaps, and a window in which the books do not balance.

Banks do run distributed ledgers. They do it when one database genuinely cannot hold the volume,
and they pay for it with an entire discipline of reconciliation. This system is nowhere near that
point, and adopting the cost without the need would be the exact mistake the phase's rule warns
about.

## Contracts

The only contract between the monolith and the extracted service is the Kafka event:

```json
{ "transactionId": "…", "reference": "TXN-20260923-000000004",
  "type": "TRANSFER", "currency": "VND", "accountId": "…", "ownerUserId": null }
```

Rules that keep it a contract rather than a distributed method call:

- **Identifiers and facts only** — no balances, amounts or personal data. A topic is readable by
  everything holding the credentials to subscribe and is retained for days.
- **Additive changes only.** A consumer ignores fields it does not know, so a producer may add
  one. Removing or renaming a field is two releases, exactly like a database column.
- **The consumer tolerates absence.** `ownerUserId` is null today because no customer-to-user
  link exists; the consumer accepts the event and produces nothing rather than failing forever
  against a fact that will not appear.
- **At-least-once, therefore idempotent.** Both sides deduplicate by event id.

## Consistency implications

| Before | After |
|---|---|
| A notification existed the moment the transfer committed | It exists a short time later, and briefly does not |
| A failed notification rolled back nothing | It is retried; after the retries it lands in a dead-letter topic |
| One database, one backup, one point-in-time restore | Two, which can be restored to different points |

That last row is the cost people forget. Two databases cannot be restored to a consistent
instant, so a recovery can produce notifications for transactions that no longer exist, or the
reverse. For notifications that is tolerable. It is precisely why the ledger stays where it is.

## Operational cost accepted

One more deployment, one more database, one more consumer group with its own lag to watch, one
more dead-letter topic to drain, and the trace id now has to be followed across two log streams —
which is why Phase 09 propagates it on the event header.

## Known gaps in the extraction

- The service's read API has **no authentication**. In a real deployment it sits behind the same
  gateway and validates the same JWT with a key from the identity provider; writing a second,
  subtly different authentication implementation would be worse than either. Recorded rather than
  papered over.
- The monolith still contains its own notification module. Both consume the same topic with
  different group ids, which is the standard strangler arrangement: run both, compare, then
  remove the old path. The removal is not done.
- There is no integration test spanning both processes; that needs a Kafka container and both
  applications running.

## Consequences

- One service extracted, for reasons written down and testable against reality.
- Five contexts deliberately left in place, each with the specific property that extraction would
  break.
- The module boundaries remain the thing that makes this reversible: the decision can be revisited
  when a measurement, rather than a preference, says so.
