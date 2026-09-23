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

## Phase 00 state

- No database: persistence auto-configuration is excluded in `application.yml` until Phase 02.
- No authentication: the security baseline denies everything except `/actuator/health`
  and `/actuator/info`. JWT and RBAC arrive in Phase 03.
- Domain rules that do not need infrastructure (money arithmetic, transaction status
  transitions, aggregations, in-memory balances) are already implemented and tested.
