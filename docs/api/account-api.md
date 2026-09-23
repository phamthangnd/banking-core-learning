# Account API

Base paths: `/api/v1/accounts` and `/api/v1/customers/{customerId}/accounts`

## Authentication and authorization

Bearer token required. Permissions are checked on the service methods, so they apply to every
caller, not only to HTTP requests.

| Operation | Permission | Roles |
|---|---|---|
| Read accounts | `account:read` | ADMIN, OFFICER, TELLER |
| Open, activate, freeze, unfreeze, set overdraft | `account:write` | ADMIN, OFFICER |
| Close | `account:close` | ADMIN, OFFICER |
| Decide customer KYC | `customer:kyc` | ADMIN, OFFICER |

## Lifecycle

```
PENDING ──▶ ACTIVE ◀──▶ FROZEN
   │           │           │
   └───────────┴───────────┴──▶ CLOSED
```

- An account opens as `PENDING`; it holds nothing and cannot transact.
- **Activation requires the owner's KYC to be `VERIFIED`.** That is a rule about two aggregates,
  so it lives in the service, not in the account itself.
- `FROZEN` is reversible (an investigation); `CLOSED` is terminal (an ending).
- Only an `ACTIVE` account may move money — `canTransact` in the response says so directly.
- **Closing requires a zero balance.** Closing an account with money on it would strand it;
  closing an overdrawn one would write off a debt. Closing twice is idempotent.
- Accounts are closed, never deleted.

Lifecycle changes are explicit commands (`POST /activate`, `/freeze`, `/close`) rather than a
`PATCH` that writes a status field: a state machine has rules, and letting a client set the next
state directly invites skipping them.

## Endpoints

| Method | Path | Success | Purpose |
|---|---|---|---|
| POST | `/customers/{customerId}/accounts` | 201 + `Location` | Open an account |
| GET | `/customers/{customerId}/accounts` | 200 | That customer's accounts (paginated) |
| GET | `/accounts` | 200 | Search accounts (paginated) |
| GET | `/accounts/{id}` | 200 | One account |
| GET | `/accounts/by-number/{accountNumber}` | 200 | Lookup by customer-facing number |
| POST | `/accounts/{id}/activate` | 200 | `PENDING`/`FROZEN` → `ACTIVE` |
| POST | `/accounts/{id}/freeze` | 200 | `ACTIVE` → `FROZEN` |
| POST | `/accounts/{id}/unfreeze` | 200 | `FROZEN` → `ACTIVE` |
| PUT | `/accounts/{id}/overdraft-limit` | 200 | Grant or change an overdraft |
| POST | `/accounts/{id}/close` | 200 | → `CLOSED` (idempotent) |

### POST /customers/{customerId}/accounts

```json
{ "accountType": "CHECKING", "currency": "VND", "overdraftLimit": 0 }
```

`accountType` is one of `CHECKING`, `SAVINGS`, `TERM_DEPOSIT`. `currency` defaults to
`bankcore.account.default-currency`. `overdraftLimit` defaults to none.

### Response

```json
{
  "id": "…",
  "accountNumber": "90040010000013",
  "customerId": "…",
  "accountType": "CHECKING",
  "currency": "VND",
  "balance": 0.0000,
  "availableBalance": 0.0000,
  "overdraftLimit": 0.0000,
  "status": "PENDING",
  "canTransact": false,
  "openedAt": "…", "activatedAt": null, "closedAt": null, "updatedAt": "…"
}
```

`balance` and `availableBalance` are both present because they answer different questions — what
is there, versus what may be taken. A client that computes the second one itself will eventually
compute it wrongly.

### Search parameters

`page`, `size`, `sort` (`OPENED_AT`, `UPDATED_AT`, `ACCOUNT_NUMBER`, `BALANCE`, `STATUS`),
`direction`, `customerId`, `status`, `accountType`, `currency`. Metadata reports `page`, `size`,
`totalElements`, `totalPages` and `hasNext`.

## Account numbers

`<prefix><9-digit sequence><Luhn check digit>`, for example `90040010000013`.

- The unique part comes from a **database sequence**, which cannot collide even under
  concurrency. Generating randomly and retrying works right up until it does not.
- The trailing **check digit** catches every single-digit typo and almost every transposition of
  adjacent digits — the difference between a validation error and money sent to a stranger.
- The number carries **no meaning**: no branch, no customer, no date. Facts encoded in an
  identifier have to change when the facts do.

## Money

- Amounts are `BigDecimal` end to end and `NUMERIC(19,4)` in the database. Never a floating-point
  type, at any layer.
- Currency is explicit and stored next to the amount. An account's currency is fixed at opening.
- One customer may own several accounts in different currencies; amounts in different currencies
  are never combined.

## Overdrafts

`overdraftLimit` is how far below zero the balance may go, expressed as a **positive** number.

- `SAVINGS` and `TERM_DEPOSIT` may never go negative; requesting an overdraft on one is rejected.
- `CHECKING` may be granted one up to `bankcore.account.maximum-overdraft-limit` (default `0`, so
  overdrafts are off until someone deliberately turns them on).
- Lowering a limit below an existing debt is rejected — it would put the account in breach
  retroactively.
- The database enforces `balance >= -overdraft_limit` with a check constraint, so a bug in
  application code cannot create money silently.

## KYC

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/customers/{id}/kyc` | `customer:kyc` |
| PUT | `/api/v1/customers/{id}/avatar` | `customer:write` |

```
PENDING ──▶ VERIFIED ──▶ PENDING (re-verification)
   └──────▶ REJECTED ──▶ PENDING (resubmission)
```

Deciding whether the bank may do business with someone is a compliance action with its own
permission, not an attribute an ordinary update can set in passing. `VERIFIED → REJECTED` is not
a legal move; re-verification goes back through `PENDING`.

The avatar is stored as a bare file id. The file module (Phase 07) owns the bytes, the validation
and the storage location; a URL here would freeze a decision that belongs elsewhere.

## Errors

| HTTP | `error.code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` / `MALFORMED_REQUEST` | Malformed request or unknown enum value |
| 401 / 403 | `AUTHENTICATION_REQUIRED` / `ACCESS_DENIED` | Missing token or missing permission |
| 404 | `ACCOUNT_NOT_FOUND` / `CUSTOMER_NOT_FOUND` | No such account or customer |
| 409 | `CONCURRENT_MODIFICATION` | Two writers changed the same account |
| 422 | `ACCOUNT_RULE_VIOLATED` | KYC not verified, illegal transition, non-zero balance on close, overdraft not allowed or above the maximum |
| 422 | `CUSTOMER_RULE_VIOLATED` | Illegal KYC decision, or closing a customer that still holds open accounts |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `bankcore.account.number-prefix` | `9004` | Leading digits of every account number |
| `bankcore.account.default-currency` | `VND` | Used when a request names none |
| `bankcore.account.maximum-overdraft-limit` | `0` | Ceiling for types that allow an overdraft |

## Known gaps, by design

- **No money movement yet.** `credit`/`debit` exist on the account and enforce the balance rules,
  but there is no deposit, withdrawal or transfer endpoint — Phase 05, with the ledger and
  double-entry accounting in Phase 06.
- Interest, fees and term-deposit maturity rules are not modelled.
- The avatar is a reference only; upload and validation arrive in Phase 07.
