# ADR-0001: Phase 1 Persistence & Status Tracking

- **Status:** Accepted
- **Date:** 2026-08-20
- **Phase:** SPEC Phase 1
- **Deciders:** Product owner (confirmed recommendations)

## Context

Phase 0 delivers notifications through Kafka + Mailpit with no durable record or status API. Phase 1 requires every notification to be persisted with lifecycle status (`QUEUED` → `PROCESSING` → `SENT` / `FAILED`) and queryable via REST.

## Decision

1. **Database:** PostgreSQL in Docker Compose for all environments (no H2).
2. **ORM:** Spring Data JPA (Hibernate via Spring Boot 3.3 BOM).
3. **Module:** New shared library `everdeliver-persistence` (entity, repository, Flyway migrations) depended on by `everdeliver-api` and `everdeliver-worker`.
4. **Schema:** Table `notifications` with UUID `id`, `channel`, `status`, `recipient`, `subject`, `body`, nullable `provider_message_id` / `retry_count` / `last_error`, and timestamps (`created_at`, `updated_at`, `sent_at`). No `campaign_id` until Phase 6.
5. **Status writes:** Worker updates Postgres directly. No status Kafka topic in Phase 1.
6. **Migrations:** Flyway versioned SQL. **API owns migrations**; worker sets `spring.flyway.enabled=false`. Hibernate `ddl-auto=validate`.
7. **Dual-write (DB + Kafka):** Transactional outbox deferred to Phase 2. API inserts `QUEUED`, then blocks on the Kafka send future (short timeout). Publish failure surfaces as HTTP 500; a stuck `QUEUED` row is a known Phase 1 limitation.

## Alternatives considered

- **H2 for local / Postgres for prod** — schema drift risk; rejected because SPEC acceptance requires Postgres in Compose.
- **Spring JDBC** — more boilerplate for CRUD/status; JPA fits current Spring Boot style.
- **Persistence only in API or only in worker** — duplicates schema/config; shared module preferred.
- **Status events on Kafka** — useful for live feeds (Phase 4); unnecessary complexity for Phase 1.
- **Transactional outbox now** — correct long-term pattern; overlaps Phase 2 retry/DLQ work; deferred.

## Consequences

- Both API and worker need Postgres connectivity; Compose gains a `postgres` service with healthcheck.
- Kafka payload (`NotificationRequest`) gains an `id` so the worker can update the correct row.
- Worker swallows delivery failures after marking `FAILED` so offsets commit (no hot-loop). Retry/DLQ remains Phase 2.
- Clients can poll `GET /api/v1/notifications/{id}` and list with `status` / `since` / `limit`.

## References

- [SPEC.md](../SPEC.md) — Phase 1
- [ARCHITECTURE.md](../ARCHITECTURE.md)
- [DATA_MODEL.md](../DATA_MODEL.md)
