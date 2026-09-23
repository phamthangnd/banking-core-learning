# Ledger API

Base path: `/api/v1/ledger`. Requires `transaction:read`.

## What the ledger is

Every posted movement writes a **balanced pair** of entries: equal debits and credits, per
currency. The account balance is the operational number; the ledger is the record that proves it.

| Movement | Debit | Credit |
|---|---|---|
| Deposit | bank cash | customer account |
| Withdrawal | customer account | bank cash |
| Transfer | source account | target account |

Deposits and withdrawals have a **system account** leg (`CASH` or `CLEARING`) because the money
crosses the bank's boundary. Without it a deposit would be a single credit, and "total debits
equal total credits" would be false by construction.

Entries carry a positive amount and a direction — never a signed amount — so "sum the debits" and
"sum the credits" stay two separate, checkable questions.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/ledger/transactions/{transactionId}/entries` | The entries behind one movement |
| GET | `/ledger/accounts/{accountId}/entries` | An account's entries, newest first |
| GET | `/ledger/reconciliation` | Whole ledger: do debits equal credits? |
| GET | `/ledger/accounts/{accountId}/reconciliation` | Stored balance vs the balance the ledger implies |

```json
GET /api/v1/ledger/reconciliation
{ "balanced": true, "debits": { "VND": 1250.0000 }, "credits": { "VND": 1250.0000 } }
```

```json
GET /api/v1/ledger/accounts/{id}/reconciliation
{ "accountNumber": "90040010000053", "currency": "VND",
  "storedBalance": 700.0000, "derivedBalance": 700.0000, "reconciled": true }
```

`storedBalance` and `derivedBalance` are two independent records of the same fact. When they
disagree, the difference is exactly how much money has been created or lost.

## Append-only

Ledger entries are never updated and never deleted. The domain has no mutators, every JPA column
is insert-only, and database triggers reject `UPDATE` and `DELETE` with *"ledger entries are
append-only"* — the guarantee that still holds when somebody runs SQL by hand.

## Known gaps, by design

- The ledger records movements from Phase 06 onward. Balances that existed before it do not
  reconcile against it; a production migration would post opening-balance entries.
- Only `CASH` and `CLEARING` system accounts exist. Fees, interest and suspense accounts would
  each need their own.
- There is no periodic reconciliation job or alert yet — the reports exist, but nothing calls
  them on a schedule. That belongs with observability in Phase 11.
