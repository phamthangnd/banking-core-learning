# Phase 05 — Core Banking Transactions Notes

## What `@Transactional` actually buys here

A transfer is three writes: debit the source, credit the target, insert the transaction row. The
annotation on `TransactionService.transfer` makes them one unit of work — all three commit, or
none do.

Without it, each repository call would commit on its own, and a failure after the debit would
have destroyed money. This is the difference between "the code looks right" and "the system
cannot lose money", and it is one annotation wide.

The same boundary is what keeps the module's core rule true: **a balance is never changed without
the transaction row that explains it**, because they commit together.

## Recording a failure that rolls back

A rejected withdrawal ends by throwing, and an unchecked exception rolls the transaction back —
including any `FAILED` row written inside it. The record of the failure would vanish with the
failure.

`TransactionFailureRecorder` writes it with `Propagation.REQUIRES_NEW`, in its own bean, which is
the exact pattern Phase 03 needed for the failed-login counter. Seeing it twice is the point: any
fact that must outlive the failure that produced it needs its own transaction.

## Positive amounts and a type that carries direction

The amount column is `CHECK (amount > 0)` and the domain record refuses a non-positive value.
Direction lives in `TransactionType` and in which account column is filled.

A signed amount looks simpler and is worse: every query then has to remember the sign convention,
and "SUM(amount)" quietly means different things for different rows. With unsigned amounts and an
explicit type, `SUM` over debits and `SUM` over credits are two clear questions — which is
exactly what Phase 06's double-entry check needs.

## Shape constraints in the database

```sql
CHECK (
  (transaction_type = 'DEPOSIT'    AND source_account_id IS NULL     AND target_account_id IS NOT NULL) OR
  (transaction_type = 'WITHDRAWAL' AND source_account_id IS NOT NULL AND target_account_id IS NULL) OR
  (transaction_type = 'TRANSFER'   AND source_account_id IS NOT NULL AND target_account_id IS NOT NULL
                                   AND source_account_id <> target_account_id)
)
```

A deposit with a source account is not a deposit; it is a corrupt row that will eventually be
summed into a report. The domain rejects it, and so does the table, so no code path — including
a hand-written `INSERT` — can create one.

## Immutability, enforced by a trigger

The domain has no setters and the JPA mapping marks every column but `status` and `posted_at` as
`updatable = false`. Both help; neither survives someone running SQL by hand during an incident.

```sql
CREATE TRIGGER trg_transactions_forbid_mutation BEFORE UPDATE ON transactions …
CREATE TRIGGER trg_transactions_forbid_delete   BEFORE DELETE ON transactions …
```

The tests prove it by bypassing the application entirely: `UPDATE transactions SET amount = 1`
raises *"transaction … is immutable; post a compensating transaction instead"*, and `DELETE`
raises *"financial records must not be deleted"*.

This is the same reasoning as the account balance constraint in Phase 04 — an invariant that
matters belongs where nothing can route around it.

## Balances captured at posting time

`source_balance_after` and `target_balance_after` are stored, not recomputed. A statement must
show the balance as it was at that moment; deriving it later from a running total means the
statement and the ledger start disagreeing the first time anything is posted out of order.

## A constraint that was wrong, and what it taught

The first version said:

```sql
CHECK ((status = 'POSTED') = (posted_at IS NOT NULL))
```

It broke the moment a transaction was reversed: a `REVERSED` transaction *was* posted, so it
keeps its `posted_at`, and the constraint refused it. The fix:

```sql
CHECK ((status IN ('POSTED', 'REVERSED')) = (posted_at IS NOT NULL))
```

The lesson is about writing constraints against the *lifecycle*, not against the current state.
The test caught it, which is the second half of the lesson.

## Currency is never converted silently

A cross-currency transfer is refused. Converting would need a rate, a source for that rate, a
spread and a rounding policy — and getting any of them silently wrong loses money in a way that
only shows up in reconciliation weeks later. Refusing is honest; FX is its own feature.

## What is deliberately missing

Repeating a request posts a second transaction: there is no idempotency yet. That is not an
oversight, it is the boundary with Phase 06, which adds idempotency keys, the double-entry
ledger, and the locking that makes concurrent transfers safe. Optimistic locking on accounts
already turns a lost update into a visible 409 rather than a corrupted balance.

## Checkpoint answers (see `docs/learning/checkpoints.md`)

- **What makes a transfer atomic?** One database transaction around the debit, the credit and the
  record of both — so a failure anywhere rolls the whole thing back.
- **Why is concurrency dangerous for balances?** Read-modify-write on a shared row loses updates:
  two transfers reading the same balance and writing their own result leave only one of them
  applied, and the money from the other is created or destroyed.
- **Why must financial records be immutable?** A record that can be edited cannot be evidence.
  Corrections are new entries, so the history of both the mistake and the fix survives.
