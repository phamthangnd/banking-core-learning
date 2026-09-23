# Transaction API

Base path: `/api/v1/transactions`, plus `/api/v1/accounts/{id}/transactions` for a statement.

## Authorization

| Operation | Permission | Roles |
|---|---|---|
| Read transactions and history | `transaction:read` | ADMIN, OFFICER, TELLER |
| Deposit, withdraw, transfer | `transaction:write` | ADMIN, OFFICER, TELLER |

A teller moves money over the counter, so `TELLER` holds both.

## The rule behind the module

**A balance is never changed without the corresponding business transaction being written in the
same database transaction.** Both accounts and the transaction row commit together, or neither
does. That is what makes a transfer atomic: a crash between the debit and the credit rolls both
back rather than destroying money.

## Endpoints

| Method | Path | Success | Purpose |
|---|---|---|---|
| POST | `/transactions/deposit` | 201 + `Location` | Money in, one account credited |
| POST | `/transactions/withdraw` | 201 + `Location` | Money out, one account debited |
| POST | `/transactions/transfer` | 201 + `Location` | Between two accounts of this bank |
| GET | `/transactions/{id}` | 200 | One transaction |
| GET | `/transactions/by-reference/{reference}` | 200 | Lookup by the reference a customer quotes |
| GET | `/transactions` | 200 | Search (paginated) |
| GET | `/accounts/{accountId}/transactions` | 200 | One account's statement |

```json
POST /api/v1/transactions/transfer
{ "sourceAccountId": "…", "targetAccountId": "…", "amount": 400, "currency": "VND",
  "description": "rent" }
```

```json
{
  "id": "…",
  "reference": "TXN-20260923-000000002",
  "type": "TRANSFER",
  "status": "POSTED",
  "currency": "VND",
  "amount": 400.0000,
  "sourceAccountId": "…", "targetAccountId": "…",
  "sourceBalanceAfter": 600.0000, "targetBalanceAfter": 400.0000,
  "occurredAt": "…", "postedAt": "…"
}
```

`sourceBalanceAfter` and `targetBalanceAfter` are the balances at posting time. A statement has
to show what the balance was then, not what it is now.

### Search parameters

`page`, `size`, `direction`, `accountId`, `type`, `status`, `from`, `to`. History is always
sorted by business time; only the direction is a client's choice. `from`/`to` is a half-open
range, so adjacent periods tile without double-counting.

An account's history is everything that touched it **on either side** — one query, so paging over
money-in and money-out together is meaningful.

## References

`TXN-<yyyyMMdd>-<9-digit sequence>`, for example `TXN-20260923-000000002`. The date is formatted
in UTC so the reference does not depend on where the server runs; the sequence comes from the
database and cannot collide.

## Rules

1. Amount must be positive; the transaction *type* carries the direction.
2. Every account involved must be `ACTIVE`. A frozen, pending or closed account cannot transact.
3. The amount's currency must match the account's.
4. A withdrawal or transfer may not exceed the **available** balance (balance plus any granted
   overdraft).
5. A transfer needs two different accounts, and both must be in the same currency —
   cross-currency transfers are refused rather than converted at a guessed rate.

## Failures are recorded, not swallowed

A rejected movement is written as a `FAILED` transaction with a `failureReason`, so "why did my
payment not go through" is answerable and the attempt appears in the account's history.

Because the refusal rolls its own transaction back, the `FAILED` row is committed in a separate
transaction (`REQUIRES_NEW`). Without that, the record of the failure would disappear together
with the failure.

Failed transactions have no `postedAt` and no balance snapshots: nothing moved.

## Immutability

A posted transaction is never edited. Corrections are compensating transactions, and the status
may only move forward along `PENDING → POSTED → REVERSED` or `PENDING → FAILED`.

This is enforced three times over:

1. the domain record has no setters, and `withStatus` only allows lifecycle moves;
2. every JPA column except `status` and `posted_at` is `updatable = false`;
3. database triggers reject an `UPDATE` that touches any other column, reject a change to a
   terminal status, and reject `DELETE` outright.

The third one is the one that still holds during an incident, when someone is issuing SQL by
hand.

## Errors

| HTTP | `error.code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Non-positive amount, more than 4 decimals, transfer to the same account |
| 401 / 403 | `AUTHENTICATION_REQUIRED` / `ACCESS_DENIED` | Missing token or permission |
| 404 | `ACCOUNT_NOT_FOUND` / `TRANSACTION_NOT_FOUND` | No such account or transaction |
| 409 | `CONCURRENT_MODIFICATION` | Two writers changed the same account |
| 422 | `TRANSACTION_REJECTED` | Inactive account, currency mismatch, insufficient funds, cross-currency transfer |

## Idempotency

Movements accept an optional `Idempotency-Key` header.

| Situation | Result |
|---|---|
| Same key, same request, first attempt finished | The original transaction is returned |
| Same key, **different** request | `409 IDEMPOTENCY_KEY_CONFLICT` |
| Same key while the first attempt is still running | `409 IDEMPOTENT_REQUEST_IN_PROGRESS` |
| Same key after a rejected attempt | Allowed; the key is released when an attempt fails |
| No key | Every request is its own movement |

Refusing the second case matters: answering it with the earlier result would tell the caller that
the request it just sent had been carried out.

## Reversal

`POST /api/v1/transactions/{id}/reverse` posts the mirror image as a **new** transaction and marks
the original `REVERSED`. Nothing is edited: the history keeps both the mistake and the correction.
Only a posted transaction can be reversed, and the reversal itself can fail — if the money has
already been spent, nothing happens at all.

## Ledger

Every posted movement writes a balanced pair of ledger entries; see
[ledger-api.md](ledger-api.md) for the entries and the reconciliation reports.

## Known gaps, by design

- **The ledger starts at Phase 06.** Balances created before it exists do not reconcile against
  it. A production migration would post opening-balance entries; this project records the gap
  instead of inventing history.
- No scheduled or future-dated transactions, no fees, no interest, no foreign exchange.
