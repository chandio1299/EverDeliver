# EverDeliver — Architecture

## Purpose

Describe **how** the system is structured: modules, runtime services, data flow, Kafka boundaries, and who owns writes. Update this file when a phase changes topology.

Product *what* and *when* live in [SPEC.md](SPEC.md). Locked choices live in [ADR/](ADR/).

---

## Current state (Phase 1)

```text
Client → everdeliver-api → PostgreSQL (QUEUED)
                ↓
         Kafka (notification-topic)
                ↓
         everdeliver-worker → PostgreSQL (PROCESSING → SENT|FAILED)
                ↓
         SMTP (Mailpit)
```

| Module | Role |
|---|---|
| `everdeliver-common` | Shared DTOs (Kafka / API request payload) |
| `everdeliver-persistence` | JPA entity, repository, Flyway migrations |
| `everdeliver-api` | REST producer (port 8081); owns schema migrations; inserts `QUEUED` |
| `everdeliver-worker` | Kafka consumer + email send (port 8082); owns status after `QUEUED` |

Infra (Docker Compose): Kafka (KRaft), PostgreSQL, Mailpit, API, Worker.

**Write ownership**

- API: create notification row (`QUEUED`), publish to Kafka (blocks on send future; no outbox yet — [ADR-0001](ADR/0001-phase1-persistence.md)).
- Worker: conditional status transitions `QUEUED → PROCESSING → SENT|FAILED`.
- Schema migrations: API only (`spring.flyway.enabled=false` on worker).

---

## Target state (post phases — stub)

```text
Dashboard / Scheduler / API clients
        ↓
  everdeliver-api  →  Kafka (channel topics or routed topic)
        ↓
  channel workers (email / sms / whatsapp / slack / webhook)
        ↓
  SendGrid | Twilio | Slack | HTTP | Mailpit
        ↓
  PostgreSQL (status, templates, campaigns, integrations)
```

**TBD (confirm in SPEC Phase 2–3 before coding):**

- [ ] Topic-per-channel vs single topic + headers
- [ ] One worker module vs many
- [x] Which service writes notification status to DB → worker (Phase 1)
- [ ] Where campaign scheduler runs (API process vs separate service)
- [ ] Transactional outbox / retry / DLQ (Phase 2)

---

## Design principles

1. API validates and enqueues; workers deliver.
2. Providers (SendGrid, Twilio) are behind channel workers — not called from the React UI.
3. Secrets never appear in Kafka payloads in plaintext if avoidable; prefer server-side lookup by workspace/user id.
4. Failures go through retry/DLQ (SPEC Phase 2).

---

## Diagrams

See SPEC “How Delivery Actually Works” for the product-level flow. Phase 1 sequence is documented in [ADR-0001](ADR/0001-phase1-persistence.md).
