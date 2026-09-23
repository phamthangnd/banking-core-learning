# Architecture Overview

BankCore starts as a Modular Monolith.

Principles:
- package by feature
- explicit module boundaries
- domain-driven business rules
- REST API at the boundary
- PostgreSQL as source of truth for transactional state
- Redis for non-authoritative ephemeral/cache state
- Kafka for asynchronous integration events
- MinIO for object storage

See `CLAUDE.md` for non-negotiable engineering rules.

## Package structure

Package by feature under `com.example.bankcore`:

```
common/       cross-cutting building blocks (money, api, pagination, web, config)
user/         users, roles and permissions
auth/         authentication, tokens, password flows, rate limiting
customer/     customer profile and KYC
account/      bank accounts and their lifecycle
transaction/  transaction module (domain rules today, persistence + API from Phase 05)
```

Each feature module owns its own `domain` (rules and value objects) and, once it has an API,
its own `web`/`application`/`persistence` packages. Modules from the target list in `CLAUDE.md`
(auth, user, customer, account, ledger, notification, file, audit, masterdata, report, dashboard,
settings) are created in the phase that first needs them, never up front as empty shells.

## Module layering

Each feature module is layered, with the dependency arrow pointing inward:

```
web             controllers and DTOs; no business logic
  -> application   services holding the business rules; take commands, not web types
    -> domain        model, value objects, repository ports, business exceptions
      <- infrastructure   adapters implementing the ports (JPA, later Redis/Kafka/S3)
```

The repository interface lives in `domain` and its implementation in `infrastructure`, which is
what let Phase 02 replace the in-memory store with PostgreSQL without changing a business rule
or an API response.

Cross-cutting building blocks live in `common`: the API envelope and error codes, the global
exception handler, correlation ids, pagination types and money.

## Current state

- **Persistence**: PostgreSQL, schema owned by Flyway, Hibernate restricted to `validate`.
  Optimistic locking via `@Version`. Integration tests run on Testcontainers.
- **Authentication and authorization**: JWT access tokens (15 min) plus stored, hashed, rotating
  refresh tokens (30 days) with reuse detection. The security baseline denies by default:
  `/actuator/health`, `/actuator/info` and the auth endpoints are public, everything else needs a
  bearer token, and each service operation additionally requires a permission via
  `@PreAuthorize`. Roles and permissions are seeded by migration `V3`.
- **Domain rules** that do not need infrastructure (money arithmetic, transaction status
  transitions, aggregations, in-memory balances) are implemented and tested from Phase 00.
- **Module boundaries**: where one module needs a fact from another, the *needing* module
  declares a port and the other implements it (`CustomerAccountsPort` in `customer/domain`,
  implemented by `account/infrastructure`). The dependency arrow stays one-directional, so the
  customer module compiles without the account module existing.
- **Banking invariants live twice**: in the domain type that owns the data, and as database
  check constraints (`balance >= -overdraft_limit`, a closed account holds nothing). The first
  fails early and readably; the second is the only one that holds under concurrency.

## Services

The system is one deployable, plus one extracted service:

```
bankcore                      the modular monolith
services/notification-service the notification context, extracted in Phase 13
```

They share no code and no database. The only contract between them is the
`bankcore.transactions` Kafka topic. Which contexts were extracted, which were deliberately not,
and what each decision costs: [adr/ADR-002-service-extraction.md](adr/ADR-002-service-extraction.md).
