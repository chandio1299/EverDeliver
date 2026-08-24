# EverDeliver — Architecture

## Purpose

Describe **how** the system is structured: modules, runtime services, data flow, Kafka boundaries, and who owns writes. Update this file when a phase changes topology.

Product *what* and *when* live in [SPEC.md](SPEC.md). Locked choices live in [ADR/](ADR/).

---

## Current state (Phase 4)

```text
Dashboard (:3000) ─┐
                   ├→ everdeliver-api → PostgreSQL (QUEUED, channel, recipient)
API clients ───────┘         ↓
                      Kafka (notification-topic; payload includes channel)
                             ↓
                      everdeliver-worker → ChannelSender by channel
                             ↓
                      email: SendGrid API (if key) | Mailpit SMTP (default)
                      sms / whatsapp: Twilio Messages API
                      slack: Incoming Webhook POST
                      webhook: generic HTTPS/HTTP POST
                             ↓
                      SENT (provider_message_id) | FAILED → retry-5000 → retry-30000 → retry-120000
                                                           ↓ exhausted / permanent
                                                 Kafka (notification-topic-dlq) → DEAD
```

| Module | Role |
|---|---|
| `everdeliver-common` | Shared DTOs + `Channel` enum (Kafka / API request payload) |
| `everdeliver-persistence` | JPA entity, repository, Flyway migrations |
| `everdeliver-api` | REST producer (port 8081); owns schema migrations; validates per channel; inserts `QUEUED`; stats; manual retry republish |
| `everdeliver-worker` | Kafka consumer + channel senders (port 8082); owns status after `QUEUED` (retries / `DEAD`) |
| `everdeliver-dashboard` | Operator console (port 3000); polls API; never calls providers |

Infra (Docker Compose): Kafka (KRaft), PostgreSQL, Mailpit, echo-server (local Slack/webhook target), API, Worker, Dashboard.

**Write ownership**

- API: create notification row (`QUEUED`), publish to Kafka (blocks on send future; no outbox yet — [ADR-0001](ADR/0001-phase1-persistence.md)). Manual retry: `FAILED`/`DEAD` → `QUEUED` (clears `last_error`, keeps `retry_count`), then republish to `notification-topic` ([ADR-0004](ADR/0004-phase4-dashboard.md)).
- Worker: conditional status transitions `QUEUED → PROCESSING → SENT|FAILED`; retries `FAILED → PROCESSING` (increment `retry_count`); DLT `FAILED → DEAD` ([ADR-0002](ADR/0002-phase2-retry-dlq.md)). Persists `provider_message_id` on `SENT` ([ADR-0003](ADR/0003-phase3-multi-channel.md)).
- Schema migrations: API only (`spring.flyway.enabled=false` on worker).
- Provider credentials: worker env only. Never in Kafka payloads. Dashboard talks only to the API.

**Locked Phase 3 topology** ([ADR-0003](ADR/0003-phase3-multi-channel.md))

- Single topic `notification-topic` (not topic-per-channel)
- One worker module with `ChannelSender` strategies
- Retry/DLQ topic names unchanged from Phase 2

---

## Target state (post phases — stub)

```text
Dashboard / Scheduler / API clients
        ↓
  everdeliver-api  →  Kafka (notification-topic)
        ↓
  everdeliver-worker (channel strategies)
        ↓
  SendGrid | Twilio | Slack | HTTP | Mailpit
        ↓
  PostgreSQL (status, templates, campaigns, integrations)
```

**TBD (confirm in later SPEC phases before coding):**

- [x] Topic-per-channel vs single topic + headers → single topic ([ADR-0003](ADR/0003-phase3-multi-channel.md))
- [x] One worker module vs many → one module, strategies ([ADR-0003](ADR/0003-phase3-multi-channel.md))
- [x] Which service writes notification status to DB → worker (Phase 1)
- [ ] Where campaign scheduler runs (API process vs separate service)
- [x] Retry / DLQ (Phase 2) — Kafka retry topics + `notification-topic-dlq`; outbox still deferred

---

## Design principles

1. API validates and enqueues; workers deliver.
2. Providers (SendGrid, Twilio) are behind channel workers — not called from the React UI.
3. Secrets never appear in Kafka payloads in plaintext if avoidable; prefer server-side lookup by workspace/user id.
4. Failures go through retry/DLQ (SPEC Phase 2).

---

## Diagrams

See SPEC “How Delivery Actually Works” for the product-level flow. Phase 1 sequence is documented in [ADR-0001](ADR/0001-phase1-persistence.md). Retry/DLQ is [ADR-0002](ADR/0002-phase2-retry-dlq.md). Multi-channel is [ADR-0003](ADR/0003-phase3-multi-channel.md). Delivery console is [ADR-0004](ADR/0004-phase4-dashboard.md).
