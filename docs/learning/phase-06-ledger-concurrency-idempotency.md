# Phase 06 — Ledger, Concurrency and Idempotency Notes

## Double-entry, and why a deposit needs a bank leg

Every movement writes two entries whose debits and credits are equal. For a transfer that is
obvious: debit the source, credit the target.

A deposit is where the idea gets tested. The customer is credited — but credited *from where*?
Money does not appear. The other leg is the bank's own cash position, a `SystemAccount`, and
without it a deposit would be a single credit and the invariant would be false the first time
anyone paid money in.

That is the whole insight of double-entry: every movement has two sides, and "outside the bank"
is still one of them.

## The invariant has to be checked somewhere

```java
public List<LedgerEntry> append(List<LedgerEntry> group) {
    LedgerEntries.requireBalanced(group);
    …
}
```

The check lives on the only door into the ledger, not in the callers. A rule that each caller
must remember is a rule that will eventually be forgotten by one of them.

Balancing is per currency: 100 VND of debit does not offset 100 USD of credit, however equal the
numbers look.

## Optimistic vs pessimistic locking

| | Optimistic (`@Version`) | Pessimistic (`SELECT … FOR UPDATE`) |
|---|---|---|
| Detects conflict | after the work, at write time | before the work, by waiting |
| Cost when uncontended | none | a row lock |
| Cost when contended | retries, and work thrown away | waiting |
| Right for | rare conflicts | balances |

Phase 04 gave accounts a `@Version`, which turns a lost update into a visible 409. That is the
right default for, say, editing a customer.

It is the wrong tool for a balance under load: every concurrent transfer on a hot account would
fail and retry, and the useful work would be done several times over. Phase 06 therefore takes a
row lock on every account whose balance is about to change. The second writer waits instead of
racing, and the concurrent-withdrawal test comes out at exactly ten successes out of twenty
attempts against a balance of 100 — not "about ten".

The `@Version` column stays: it protects the paths that do not lock, and it costs nothing.

## Lock ordering, or the deadlock you will definitely write

Two transfers, A→B and B→A, at the same time. Each locks its source, then wants the other's —
which the other is holding. Neither can proceed, and the database kills one after a timeout.

```java
if (sourceId.compareTo(targetId) < 0) {
    source = lock(sourceId); target = lock(targetId);
} else {
    target = lock(targetId); source = lock(sourceId);
}
```

Locks are taken in a fixed order — by id, not by the caller's idea of source and target. With
that, a cycle cannot form. The test runs twenty opposite-direction transfers concurrently and
expects all twenty to succeed; without the ordering it fails, sometimes.

## Idempotency

The scenario is mundane: a client posts a transfer, the connection drops, the client retries.
Without a key the money moves twice, and the client cannot tell "it did not happen" from "I did
not hear about it".

The mechanism:

1. **Claim the key** with an insert that commits immediately, before the work starts. If the
   claim loses to the unique index, another attempt owns the key.
2. **Replay** — same key, same request fingerprint, work already finished: answer with the
   original transaction.
3. **Conflict** — same key, *different* request: refuse with 409. Answering with the earlier
   result would tell the caller that the request it just sent had been carried out. This is the
   part that is easy to leave out and expensive to leave out.
4. **In progress** — the first attempt is still running: refuse, because the effect may be about
   to happen.
5. **Release on failure**, so a client whose request was rejected for a fixable reason can retry.

Two details that are load-bearing:

- The claim and the release each commit in **their own transaction**. A claim that is invisible
  until the end protects nothing, and a release that rolls back with the failure would lock the
  key forever. The same `REQUIRES_NEW` shape as Phase 03's lockout counter and Phase 05's failed
  transaction — the third time this pattern has been needed.
- Uniqueness is decided by the **database**, not by a look-up followed by an insert. Concurrent
  retries live exactly in the window between those two steps.

## A constraint violation does not un-mark a rollback

The first version of the claim wrapped the insert in a try/catch:

```java
try { return Optional.of(keys.saveAndFlush(entity).toDomain()); }
catch (DataIntegrityViolationException ex) { return Optional.empty(); }
```

It failed with `UnexpectedRollbackException: Transaction silently rolled back because it has been
marked as rollback-only`. Catching the exception does not undo the rollback mark the failed
statement left on the transaction; the commit afterwards still fails.

The fix is to not raise at all:

```sql
insert into idempotency_keys (…) values (…) on conflict (scope, idempotency_key) do nothing
```

Zero rows affected means somebody else claimed it. The general lesson: inside a transaction,
"catch and continue" does not exist for a statement the database refused.

## Reconciliation

Two independent records of the same fact: the balance stored on the account row, and the balance
implied by that account's ledger entries. `GET /api/v1/ledger/accounts/{id}/reconciliation`
compares them. If they ever disagree, one of them is wrong and the difference is exactly how
much money has been created or lost.

`GET /api/v1/ledger/reconciliation` asks the whole-ledger question: do total debits still equal
total credits, per currency? Both sums are computed in the database — reconciliation has to
consider every entry, and loading a ledger into memory to add it up stops working long before
the ledger gets interesting.

The manual check after this phase found exactly what it should: accounts funded in **Phase 05**,
before the ledger existed, do not reconcile, while an account that has only ever existed under
the ledger reconciles to the cent. A real migration would post opening-balance entries for the
pre-ledger balances; that gap is recorded rather than papered over.

## Failed transactions leave no partial state

The rejected-withdrawal test asserts three things: the exception, an empty ledger for that
account, and an unchanged balance. The FAILED transaction row still exists, because it was
written in its own transaction — the attempt is history, the money never moved, and the ledger
never saw it.

## Checkpoint answers (see `docs/learning/checkpoints.md`)

- **What is double-entry accounting?** Every movement is recorded twice, as a debit and an equal
  credit, so the books balance by construction and a one-sided mistake is detectable.
- **What is idempotency?** The same request, repeated, produces the same single effect. It is
  what makes retrying safe, and retrying is what clients do when the network is unreliable.
- **Why can a consumer receive the same message twice?** Because at-least-once delivery is the
  only thing a network can promise; the receiver, not the sender, has to make the duplicate
  harmless.
