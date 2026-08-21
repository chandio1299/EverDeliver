# Architecture Decision Records (ADRs)

## Purpose

Short, dated records of **technical decisions we locked** so future you (and any AI) do not re-litigate them or invent conflicting designs.

Use an ADR when a checkbox in [SPEC.md](../SPEC.md) is confirmed, or when something material changes (DB, Kafka layout, auth, provider SDK).

## How to add one

1. Copy [0000-template.md](0000-template.md)
2. Rename to `NNNN-short-title.md` (sequential, e.g. `0001-postgresql-jpa.md`)
3. Fill Status, Context, Decision, Consequences
4. Add a row to the Index table below
5. Link from [ARCHITECTURE.md](../ARCHITECTURE.md) if topology changed
6. Check the matching box in SPEC

## Index

| ID | Title | Status |
|---|---|---|
| [0001](0001-phase1-persistence.md) | Phase 1 Persistence & Status Tracking | Accepted |
| [0002](0002-phase2-retry-dlq.md) | Phase 2 Retry Topics & Dead Letter Queue | Accepted |
| [0003](0003-phase3-multi-channel.md) | Phase 3 Multi-Channel Delivery | Accepted |

## Rules for AIs

- Do **not** treat a recommendation in chat as locked until an ADR exists **or** the SPEC checkbox is explicitly checked by the user.
- Prefer updating an ADR’s Status to `Superseded` over deleting history.
