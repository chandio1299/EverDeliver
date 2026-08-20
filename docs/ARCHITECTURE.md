# EverDeliver — Architecture

## Purpose

Describe **how** the system is structured: modules, runtime services, data flow, Kafka boundaries, and who owns writes. Update this file when a phase changes topology.

Product *what* and *when* live in [SPEC.md](SPEC.md). Locked choices live in [ADR/](ADR/).

---

## Current state (Phase 0)

```text
Client → everdeliver-api → Kafka (notification-topic) → everdeliver-worker → SMTP (Mailpit)
```

| Module | Role |
|---|---|
| `everdeliver-common` | Shared DTOs |
| `everdeliver-api` | REST producer (port 8081) |
| `everdeliver-worker` | Kafka consumer + email send (port 8082) |

Infra (Docker Compose): Kafka (KRaft), Mailpit, API, Worker.

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

**TBD (confirm in SPEC Phase 1–3 before coding):**

- [ ] Topic-per-channel vs single topic + headers
- [ ] One worker module vs many
- [ ] Which service writes notification status to DB
- [ ] Where campaign scheduler runs (API process vs separate service)

---

## Design principles

1. API validates and enqueues; workers deliver.
2. Providers (SendGrid, Twilio) are behind channel workers — not called from the React UI.
3. Secrets never appear in Kafka payloads in plaintext if avoidable; prefer server-side lookup by workspace/user id.
4. Failures go through retry/DLQ (SPEC Phase 2).

---

## Diagrams

Replace/expand when Phase 1+ decisions are locked. See SPEC “How Delivery Actually Works” for the product-level flow.
