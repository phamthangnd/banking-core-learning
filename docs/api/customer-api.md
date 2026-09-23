# Customer API (Phase 01)

Base path: `/api/v1/customers`

## Authentication

**None yet.** Phase 03 introduces JWT authentication and RBAC and closes these endpoints.
Until then the application must not be exposed outside a development machine. The security
baseline denies every other path by default; the customer module is opened explicitly in
`SecurityConfig`.

## Authorization

Not applicable in Phase 01. Planned in Phase 03: `ROLE_TELLER` for reads, `ROLE_OFFICER` for
writes, with authorization enforced at the service boundary, not only in the controller.

## Response envelope

Every response uses the same structure.

Success:

```json
{
  "success": true,
  "data": { },
  "metadata": { },
  "timestamp": "2026-06-15T09:00:00Z",
  "traceId": "c8320173-d220-431f-b688-ff1cc79a7693"
}
```

Failure:

```json
{
  "success": false,
  "error": {
    "code": "CUSTOMER_RULE_VIOLATED",
    "message": "Customer must be at least 18 years old",
    "details": [ { "field": "email", "message": "must be a valid email address" } ]
  },
  "timestamp": "2026-06-15T09:00:00Z",
  "traceId": "64bc2717-8795-4a1b-af96-1259ddf05abc"
}
```

`metadata`, `details` and null fields are omitted when empty.
`traceId` is also returned in the `X-Trace-Id` response header and appears in every log line of
that request. An inbound `X-Trace-Id` is honoured when it is at most 64 characters of
`[A-Za-z0-9_.:-]`; anything else is replaced with a generated id.

## Endpoints

| Method | Path | Success | Purpose |
|---|---|---|---|
| POST | `/api/v1/customers` | 201 + `Location` | Register a customer |
| GET | `/api/v1/customers/{id}` | 200 | Read one customer |
| GET | `/api/v1/customers` | 200 | List customers (capped, not paginated yet) |
| PUT | `/api/v1/customers/{id}` | 200 | Update contact details |
| DELETE | `/api/v1/customers/{id}` | 204 | Close a customer (soft close) |

### POST /api/v1/customers

```json
{
  "fullName": "Alice Nguyen",
  "email": "alice@example.com",
  "phoneNumber": "+84 90 123 4567",
  "dateOfBirth": "1990-01-01"
}
```

Field rules (Bean Validation, HTTP 400 on failure):

| Field | Rule |
|---|---|
| `fullName` | not blank, at most 150 characters |
| `email` | not blank, valid email, at most 255 characters |
| `phoneNumber` | not blank, matches `\+?[0-9 .-]{6,20}` |
| `dateOfBirth` | not null, in the past |

The email is normalised (trimmed, lower-cased) before it is stored or compared.

### PUT /api/v1/customers/{id}

Accepts `fullName`, `email` and `phoneNumber`. Identity and `dateOfBirth` cannot be changed.

### DELETE /api/v1/customers/{id}

A **soft close**: the record is kept and its `status` becomes `CLOSED`. Customers are never
hard-deleted, so history and later account or transaction references stay intact.

**Idempotent**: closing an already closed customer returns 204 again and changes nothing —
including `updatedAt`.

## Errors

| HTTP | `error.code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Field-level validation failed; `details` lists the fields |
| 400 | `MALFORMED_REQUEST` | Body could not be parsed at all |
| 404 | `CUSTOMER_NOT_FOUND` | No customer with that id |
| 409 | `CUSTOMER_EMAIL_ALREADY_USED` | Email already registered to another customer |
| 422 | `CUSTOMER_RULE_VIOLATED` | Below the minimum age, future birth date, or update of a closed customer |
| 422 | `STORAGE_LIMIT_REACHED` | In-memory store is full (Phase 01 only) |
| 500 | `INTERNAL_ERROR` | Anything unexpected; details stay in the logs |

Error codes are part of the contract: clients branch on `error.code`, never on `error.message`.

Validation errors never echo the rejected value — a request body may contain personal or secret
data, and error responses travel into logs and support tickets.

## Business rules

1. Email is unique across customers, compared case-insensitively.
2. A customer must be at least `bankcore.customer.minimum-age-years` (default 18) years old.
3. A birth date in the future is rejected.
4. A closed customer is read-only.
5. The Phase 01 in-memory store holds at most `bankcore.customer.max-records` customers.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `bankcore.customer.minimum-age-years` | 18 | Minimum age to register |
| `bankcore.customer.max-records` | 10000 | Capacity of the in-memory store |
| `bankcore.customer.max-list-size` | 100 | Maximum customers returned by the list endpoint |

Values are bound to a validated record and checked at startup: a bad value stops the
application instead of quietly changing a business rule.

## Known gaps, by design

- No authentication or authorization → Phase 03.
- No pagination, sorting or filtering; the list endpoint is capped and says so in
  `metadata.paginated` → Phase 02.
- Storage is in memory and is lost on restart → Phase 02.
- No OpenAPI document yet → Phase 08 adds the generated contract.
