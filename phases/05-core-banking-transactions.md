# Phase 05 — Core Banking Transactions

## Build
- deposit
- withdrawal
- transfer
- transaction reference
- transaction status
- transaction history

## Critical
Use database transactions.
Never update balances without recording the corresponding business transaction.

## Acceptance
- deposit works
- withdrawal validates available funds
- transfer is atomic
- failures roll back
- transaction history is immutable
- tests cover success and failure paths
