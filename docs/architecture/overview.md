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
common/       cross-cutting building blocks (money, api, config, concurrency)
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
- **No authentication**: the security baseline denies everything except `/actuator/health`,
  `/actuator/info` and the customer API, which is open until Phase 03 adds JWT and RBAC.
  The application must not be exposed outside a development machine until then.
- **Domain rules** that do not need infrastructure (money arithmetic, transaction status
  transitions, aggregations, in-memory balances) are implemented and tested from Phase 00.
