# ADR-001: Modular Monolith First

## Decision
Start with a modular monolith.

## Why
The project is primarily a learning vehicle and needs strong domain boundaries before distributed-system complexity is introduced.

## Consequences
Positive:
- simpler local development
- one deployment
- easier transactions
- easier debugging

Negative:
- requires discipline to preserve boundaries
- later extraction needs explicit contracts
