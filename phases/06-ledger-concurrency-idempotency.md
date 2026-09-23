# Phase 06 — Ledger + Concurrency + Idempotency

## Build
- double-entry ledger
- debit/credit entries
- ledger invariant
- idempotency key
- optimistic locking
- pessimistic locking example
- concurrency tests

## Invariants
- total debits == total credits
- one idempotency key cannot create multiple financial effects
- concurrent updates cannot corrupt balances

## Acceptance
- concurrent transfer tests pass
- duplicate request tests pass
- ledger balances reconcile
- failed transactions leave no partial state
