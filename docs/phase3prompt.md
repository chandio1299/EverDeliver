# Phase 3 handoff prompt

Copy everything inside the fenced block below and paste it to another AI (or a new Cursor chat) to continue EverDeliver development.

Start that chat on branch **`feature/phase2`** (or `develop` only after Phase 2 is merged). Phase 3 must not be implemented on Phase 1 code.

---

```text
You are working in the EverDeliver repo (Java 21 / Spring Boot 3.3 / Kafka multi-module Gradle).

## Goal
Implement ONLY SPEC Phase 3 — Multi-Channel Delivery (SendGrid Email + Twilio SMS/WhatsApp + Slack + Webhook).
Do NOT implement Phase 4+ (dashboard, auth, campaigns, observability, CI) or refactor unrelated code.

## Mandatory reading (do this first, in order)
1. AGENTS.md
2. docs/SPEC.md — especially “Phase 3”, Guiding Principles, “Current State (Phase 2 — Complete)”, and locked Product Model A
3. docs/ARCHITECTURE.md
4. docs/DATA_MODEL.md
5. docs/ADR/0001-phase1-persistence.md (locked — do not reopen)
6. docs/ADR/0002-phase2-retry-dlq.md (locked retry/DLQ — extend it; do not replace)
7. docs/SECURITY.md (no secrets in git/logs/API responses; no tokens in Kafka payloads)
8. docs/LOCAL_SETUP.md / README.md (how the stack runs today; SendGrid/Twilio sections are stubs)
9. .cursor/rules/everdeliver-spec-gates.mdc
10. .cursor/rules/everdeliver-java.mdc

## What already works (Phase 1–2 — do not break)
- Modules: everdeliver-api, everdeliver-worker, everdeliver-common, everdeliver-persistence
- Postgres in Docker Compose; Flyway owned by API; worker has spring.flyway.enabled=false
- Table notifications: channel (always "email" today), recipient, subject, body, provider_message_id (unused), retry_count, last_error
- Status: QUEUED → PROCESSING → SENT | FAILED; retries FAILED → PROCESSING (atomic retry_count++); DLT FAILED → DEAD
- POST /api/v1/notifications {email, subject, message} → persist QUEUED → Kafka notification-topic (payload includes id) → {id, status:QUEUED}
- GET /api/v1/notifications/{id} and GET /api/v1/notifications?status=&since=&limit= (includes retryCount, lastError)
- Worker: @RetryableTopic on notification-topic; attempts=4; backoff 5s / 30s / 2m (maxDelay=120000 required); retry topics notification-topic-retry-5000|30000|120000; DLT notification-topic-dlq
- PermanentDeliveryException excluded from retries (straight to DLQ); RetryableDeliveryException for transient SMTP
- Email today: JavaMailSender → Mailpit SMTP only. No SendGrid/Twilio/Slack/webhook yet
- Simulate flags: EVERDELIVER_DELIVERY_SIMULATE_FAILURE / SIMULATE_PERMANENT_FAILURE (default false)
- Known limitation (still deferred): no transactional outbox for API DB↔Kafka dual-write
- Phase 2 acceptance path may not have been run in Docker yet; do not rip out retry/DLQ to “simplify” Phase 3

Key files:
- everdeliver-api/.../NotificationService.java, NotificationController.java, NotificationPersistenceService.java
- everdeliver-worker/.../NotificationConsumer.java, NotificationStatusService.java, DeliveryExceptionClassifier.java
- everdeliver-common/.../NotificationRequest.java
- everdeliver-persistence/.../Notification.java, NotificationRepository.java, NotificationStatus.java
- everdeliver-persistence/.../db/migration/V1__create_notifications.sql
- docker-compose.yml (kafka, postgres, mailpit, api, worker)

## Hard rule — stop before coding
Phase 3 has unchecked Architecture Decisions (MUST CONFIRM BEFORE IMPLEMENTING).
Your FIRST reply must:
- Summarize Phase 3 goal + acceptance criteria in a short bullet list
- List EVERY architecture decision checkbox from SPEC Phase 3 (all 9)
- Ask me to confirm each one (give your recommendation per item, but wait for my answers)
- Do NOT write application code, add topics, new Gradle modules, SDKs, or Compose env until I confirm

After I confirm:
1. Create an ADR under docs/ADR/ (copy 0000-template.md → 0003-…); update ADR index
2. Update docs/ARCHITECTURE.md and docs/DATA_MODEL.md if topology/schema/API contract changed
3. Check the Phase 3 architecture boxes in docs/SPEC.md
4. Then implement Phase 3 only
5. Match existing project style; keep changes scoped
6. Reuse Phase 2 retry/DLQ: 4xx/bad-address = PermanentDeliveryException (no retry); 5xx/timeouts = RetryableDeliveryException. Persist provider_message_id when the provider returns one. last_error remains message-only, truncated 1024.

## Locked product context (do not reopen)
- Model A: EverDeliver login + paste keys later (Phase 5). No Gmail OAuth for v1.
- Email: SendGrid when configured; Mailpit local default with zero cloud keys
- SMS/WhatsApp: Twilio
- Slack v1 preference: Incoming Webhook
- Phase 1 persistence (ADR-0001) and Phase 2 retry/DLQ (ADR-0002) stay locked
- Prefer extending existing Gradle modules and Compose patterns
- Secrets never committed, never logged, never returned by API, never placed in Kafka payloads — workers read env (Phase 3) / DB creds (Phase 5)

## Phase 3 acceptance (from SPEC — “done” means)
- All five channels have a working delivery path: email | sms | whatsapp | slack | webhook
- Without SendGrid key → email goes to Mailpit; with key → real email via SendGrid
- Twilio SMS/WhatsApp work with documented sandbox/setup steps
- Provider IDs persisted when available (Twilio SID / SendGrid message id)
- README + LOCAL_SETUP document SendGrid + Twilio + Slack + webhook setup
- Happy-path email without keys still works as Phase 1–2 (Mailpit)
- Failed SendGrid/Twilio calls still retry/DLQ with the Phase 2 semantics

## Suggested recommendations (propose these; wait for confirmation)

1. Routing: single Kafka topic + channel on the payload (and optionally a header) vs topic per channel.
   Recommend: KEEP notification-topic + channel field. Phase 2 retry/DLQ is already wired to that topic. Topic-per-channel is better isolation (US-3.3) but multiplies retry/DLQ topics (5×). At v1 volume, isolate via strategy classes + per-attempt error classification; document that slow Twilio can delay other channels on the same partition. If I insist on strict isolation, then topic-per-channel with the same retry suffix pattern per topic.

2. Worker layout: extra Gradle modules vs strategy pattern in one worker.
   Recommend: strategy/pattern in existing everdeliver-worker (EmailSender, SmsSender, … selected by channel). Do not add five worker apps.

3. API contract: polymorphic JSON vs flat optional fields.
   Recommend: flat body, backward compatible:
   { "channel": "email"|"sms"|"whatsapp"|"slack"|"webhook" (default "email"),
     "email" or "recipient", "subject", "message",
     "phone"?, "slackWebhookUrl"?, "webhookUrl"? }
   Validate required fields per channel (email address, E.164 phone, URL). Prefer existing columns: recipient holds email/phone/URL; subject/body as today. Do not invent extra tables. Only add a Flyway column if a field truly cannot fit (confirm with me first).

4. Email transport: SendGrid Java SDK vs SMTP-to-SendGrid vs WebClient REST.
   Recommend: official SendGrid Java SDK when SENDGRID_API_KEY is set; otherwise keep JavaMailSender → Mailpit. From-address from SENDGRID_FROM_EMAIL. Map SendGrid 4xx → permanent, 5xx/timeout → retryable.

5. Twilio: official Java SDK vs WebClient.
   Recommend: official Twilio Java SDK.

6. Shared Twilio client for SMS + WhatsApp?
   Recommend: yes — one client bean; two sender strategies.

7. WhatsApp v1: sandbox free-form only vs also template SID + variables.
   Recommend: sandbox free-form only. Template SID is Phase 6+ / Meta approval; document the limitation.

8. Slack Incoming Webhook for v1?
   Recommend: yes (already locked). HTTPS POST JSON to the Incoming Webhook URL. No Slack OAuth/bot token in v1.

9. Credentials this phase: env-only first, then Settings UI in Phase 5?
   Recommend: env-only. Compose documents the vars; empty = that channel disabled (except email → Mailpit). Do not build Integrations UI.

Also consider:
- Outbox: still no, unless I explicitly ask.
- Webhook channel: generic HTTPS POST of {id, channel, recipient, subject, message} (or similar); treat 2xx as SENT, 408/429/5xx retryable, other 4xx permanent.
- Do not implement dashboard, login, templates, campaigns, or Gmail.

## Env vars to document (names may be adjusted in the ADR after confirmation)
SENDGRID_API_KEY, SENDGRID_FROM_EMAIL
TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_SMS_FROM, TWILIO_WHATSAPP_FROM
(Slack/webhook URLs come from the request in v1, not env — they are per-notification destinations)

## Done means
All Phase 3 acceptance criteria in docs/SPEC.md are met, ADR-0003 recorded, docs updated, email-without-keys still hits Mailpit, and you show how to verify each channel (including “no SendGrid key → Mailpit” and a documented SendGrid/Twilio path).
```
