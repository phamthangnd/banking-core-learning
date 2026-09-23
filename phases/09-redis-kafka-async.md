# Phase 09 — Redis + Kafka + Async Processing

## Build
Redis:
- cache
- OTP
- rate limiting
- distributed lock where justified

Kafka:
- transaction events
- audit events
- notification events
- consumer retry
- dead-letter handling

## Acceptance
- events are published after successful business transactions
- consumers are idempotent
- failures are observable
- cache invalidation is explicit
