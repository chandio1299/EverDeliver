# ADR-0003: Phase 3 Multi-Channel Delivery

- **Status:** Accepted
- **Date:** 2026-08-21
- **Phase:** SPEC Phase 3
- **Deciders:** Product owner (confirmed recommendations)

## Context

Phase 1–2 deliver email only (JavaMailSender → Mailpit) on a single Kafka topic with retry/DLQ. Phase 3 requires five working channels (email, SMS, WhatsApp, Slack, webhook), SendGrid when configured, Twilio for SMS/WhatsApp, provider IDs on `SENT`, and env-only credentials. ADR-0001 (persistence) and ADR-0002 (retry/DLQ) stay locked.

## Decision

1. **Routing:** keep `notification-topic`. Channel is a field on the Kafka payload (and a `channel` header for logs). Isolation is via strategy classes, not topic-per-channel. A slow Twilio call can delay other channels on the same partition; acceptable at v1 volume.
2. **Worker layout:** strategy pattern in existing `everdeliver-worker` (`ChannelSender` per channel + registry). No extra Gradle modules or worker processes.
3. **API contract:** flat JSON, backward compatible. `channel` defaults to `email`. Ingress aliases: `email`, `phone`, `slackWebhookUrl`, `webhookUrl`. Canonical destination is persisted in `recipient` and travels on Kafka as `recipient` (email channel also sets `email` for in-flight Phase 2 messages).
The Twilio Java SDK has no `baseUrl` on `TwilioRestClient.Builder`. HTTP is sent through Spring `RestClient` (same timeouts as Slack/webhook) via a small `HttpClient` adapter so WireMock and custom hosts work. Message create/parse still uses the official SDK.
6. **Slack:** Incoming Webhook. HTTPS POST `{"text": "..."}` to the per-notification URL. No bot token / OAuth.
7. **Webhook:** generic HTTP POST of `{id, channel, recipient, subject, message}`. 2xx = SENT; 408/429/5xx = retryable; other 4xx = permanent.
8. **Credentials:** env-only on the worker. Empty Twilio/SendGrid = that path disabled (email falls back to Mailpit; SMS/WhatsApp throw `PermanentDeliveryException`). Settings UI is Phase 5. Slack/webhook URLs come from the request, not env.
9. **Schema:** no new Flyway. Reuse `channel`, `recipient`, `provider_message_id`. `markSent` now persists provider id (truncated 255).
10. **Retry/DLQ:** ADR-0002 unchanged (4 attempts, 5s/30s/2m, `notification-topic-dlq`). Backoff is externalized via `@Backoff(*Expression)` so tests can shorten delays; production defaults stay identical. HTTP mapping: 4xx except 408/429 = permanent; 408/429/5xx/timeouts = retryable. Missing provider config = permanent (retry cannot fix it).
11. **Secrets:** never in git, logs, API responses, or Kafka payloads. `last_error` and logs run through a redactor (query strings, `Authorization`/`Bearer`, api-key patterns).
12. **Tests:** Testcontainers PostgreSQL + Kafka + WireMock. No H2. No live SendGrid/Twilio. Compose smoke script for the full stack.
13. **Toolchain:** Gradle `sourceCompatibility` remains **Java 17** (this repo). Docs that said Java 21 were wrong; do not bump the JDK in this phase.

## Alternatives considered

- **Topic-per-channel** — better isolation (US-3.3) but multiplies retry/DLQ topics 5×. Rejected at v1 volume.
- **Five worker apps / extra Gradle modules** — ops cost; rejected.
- **Polymorphic JSON API** — more precise types; worse backward compatibility. Rejected.
- **SMTP-to-SendGrid** — works but weaker error classification and no message-id header. Rejected as primary.
- **WebClient/RestClient-only Twilio** — reinvent the Messages API. Rejected.
- **WhatsApp template SID in v1** — needs Meta approval. Deferred.
- **Slack bot token / OAuth** — already locked Incoming Webhook for v1.
- **DB-stored credentials now** — Phase 5 Integrations UI.

## Consequences

- Worker Compose env documents `SENDGRID_*` and `TWILIO_*`; API does not receive those secrets.
- `recipient` VARCHAR(512) holds email, E.164, or webhook URL. Overlong destinations are rejected at the API (400).
- Local/dev webhook URLs may use `http://` (Compose echo server); Slack production URLs are `https://`.
- At-least-once delivery can still double-send (Phase 7 idempotency).
- Outbox still deferred (stuck `QUEUED` on Kafka publish failure).

## References

- [SPEC.md](../SPEC.md) — Phase 3
- [ARCHITECTURE.md](../ARCHITECTURE.md)
- [DATA_MODEL.md](../DATA_MODEL.md)
- [ADR-0001](0001-phase1-persistence.md)
- [ADR-0002](0002-phase2-retry-dlq.md)
