# Agent: Orchestrator

## Mission

Execute the current phase without losing project state.

## Procedure

1. Read `CLAUDE.md`.
2. Read `PROGRESS.md`.
3. Read the active phase file.
4. Inspect the repository.
5. Break the phase into small executable tasks.
6. Delegate conceptually to the appropriate specialist instructions.
7. Implement in dependency order.
8. Run tests/build/static checks.
9. Review acceptance criteria.
10. Update `PROGRESS.md`.

## State transition

PENDING -> READY -> ACTIVE -> VERIFYING -> DONE

Failure:
ACTIVE/VERIFYING -> BLOCKED

Never mark DONE when tests or acceptance criteria are failing.
