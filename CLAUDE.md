# CLAUDE.md — BankCore Engineering Constitution

## 1. Mission

Build BankCore as a production-oriented Java 21 / Spring Boot Modular Monolith for learning
enterprise backend engineering through a core-banking domain.

The code must be understandable to a developer returning to Java after a long break.
Prefer explicit, maintainable designs over clever abstractions.

## 2. Non-negotiable architecture

- Architecture: Modular Monolith, package-by-feature.
- Domain boundaries must be explicit.
- Do not create a giant shared service layer.
- Controllers must not contain business logic.
- Domain/business rules belong in application/domain services.
- Repositories abstract persistence.
- DTOs are used at API boundaries.
- Never expose JPA entities directly from REST APIs.
- Use immutable request/response models where practical.
- Keep infrastructure concerns out of domain logic.

Target modules:

auth
user
customer
account
transaction
ledger
notification
file
audit
masterdata
report
dashboard
settings
common

## 3. Banking rules

- Monetary values use `BigDecimal`, never float/double.
- Currency must be explicit.
- Financial transactions are immutable business records.
- Corrections use reversal/compensating transactions.
- Transfers must preserve double-entry accounting.
- Total debits must equal total credits.
- Critical operations must be idempotent.
- Balance-changing operations require explicit transaction boundaries.
- Concurrency must be designed, not assumed away.
- Never silently swallow a failed financial operation.

## 4. Security

Never log:
- passwords
- access tokens
- refresh tokens
- OTP values
- secrets
- sensitive banking data

Use:
- password hashing
- JWT access token
- refresh-token rotation/management
- RBAC
- input validation
- authorization at service/API boundaries
- secure file validation
- rate limiting for authentication-sensitive endpoints

## 5. Database

- PostgreSQL is the primary database.
- Flyway owns schema evolution.
- Production must not use Hibernate schema creation.
- Prefer `ddl-auto=validate`.
- Every important query must consider indexes.
- Detect and prevent N+1 queries.
- Pagination is mandatory for large collections.
- Financial records must not be hard-deleted.

## 6. API standards

Base path:

`/api/v1`

Responses should have a consistent structure.

Success:
- data
- metadata where required
- trace/correlation information where useful

Errors:
- code
- message
- timestamp
- traceId
- validation details where applicable

Use correct HTTP semantics.

## 7. Testing

Required layers:
- unit tests for business rules
- integration tests for persistence/security
- API/controller tests where useful
- Testcontainers for realistic infrastructure integration
- concurrency tests for balance/transfer paths
- idempotency tests for transaction APIs

Critical banking flows must have tests before being considered complete.

## 8. Observability

Use:
- structured logs
- correlation/trace ID
- Micrometer metrics
- Spring Boot Actuator
- Prometheus/Grafana later in the roadmap

Errors must be diagnosable without exposing secrets.

## 9. Dependency discipline

Do not add a dependency merely because it is popular.
Before adding one:
1. Explain why it is needed.
2. Check whether Spring/Java already provides the capability.
3. Keep the dependency scoped to the correct module.

## 10. Git discipline

Conventional commits:

feat:
fix:
refactor:
test:
docs:
build:
ci:
chore:

Prefer small commits aligned with completed tasks.

## 11. AI execution rules

Before changing code:
1. Read `PROGRESS.md`.
2. Read the active phase file.
3. Inspect existing code.
4. Preserve existing architecture.
5. Update tests with implementation.
6. Update `PROGRESS.md` after a completed task.
7. Never claim a task is complete without verification.

If requirements conflict:
- preserve banking correctness
- preserve security
- preserve backward compatibility
- ask for clarification when a business rule is genuinely ambiguous

## 12. Definition of Done

A feature is not complete because it compiles.

A feature is complete only when applicable:
- domain model exists
- migration exists
- API contract exists
- validation exists
- authorization exists
- business rules exist
- error handling exists
- tests exist
- audit/observability considered
- documentation updated
- acceptance criteria verified
