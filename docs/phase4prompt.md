# Phase 4 handoff prompt

Copy everything inside the fenced block below and paste it to another AI (or a new Cursor chat) to continue EverDeliver development.

Start that chat on branch **`feature/phase3`** (or `develop` / `main` only after Phase 3 is merged). Phase 4 must not be implemented on Phase 2-only code.

---

```text
You are working in the EverDeliver repo (Java 17 / Spring Boot 3.3 / Kafka multi-module Gradle).

## Goal
Implement ONLY SPEC Phase 4 — Dashboard (Delivery Console): web UI for a live notification feed, filters, stats, and manual retry.
Do NOT implement Phase 5+ (login, Integrations settings / DB-stored secrets, templates, audiences, campaigns, observability, CI) or refactor unrelated delivery code.

## Mandatory reading (do this first, in order)
1. AGENTS.md
2. docs/SPEC.md — especially “Phase 4”, Guiding Principles, “Current State (Phase 3 — Complete)”, and locked Product Model A
3. docs/ARCHITECTURE.md
4. docs/DATA_MODEL.md
5. docs/ADR/0001-phase1-persistence.md (locked — do not reopen)
6. docs/ADR/0002-phase2-retry-dlq.md (locked retry/DLQ — do not replace)
7. docs/ADR/0003-phase3-multi-channel.md (locked multi-channel — do not reopen)
8. docs/SECURITY.md (no secrets in git/logs/API responses; dashboard must not call SendGrid/Twilio; CORS is TBD)
9. docs/LOCAL_SETUP.md / README.md (how the stack runs today)
10. .cursor/rules/everdeliver-spec-gates.mdc
11. .cursor/rules/everdeliver-java.mdc

## Mandatory design research (do this before proposing UI or writing frontend)
Fetch/read these and treat them as the design philosophy for Phase 4 — not as extra product scope. Summarize what you took from each before you sketch or code the dashboard:

1. https://impeccable.style/ — anti-slop, operator-console mode (Operate: complete a task). Prefer calm, clinical, high-contrast information design. No purple gradients, glassmorphism, “AI beige”, italic-serif marketing type, status-chip soup, cards-in-cards, or hype copy. Distill: one job per screen.
2. https://ui.shadcn.com/ — accessible, copy-paste primitives (table, badge, button, select, card) you own in-repo. Build a small design system from these, don’t lock into MUI/Ant. Compose a dashboard from primitives, not a theme pack.
3. https://gluestack.io/ — Tailwind-first component patterns (forms, tables, badges, toasts, empty/error states). Use as a pattern reference for spacing, states, and control density — this dashboard is web-only (no React Native / Expo).

UX the console must feel like (still confirm architecture boxes before coding):
- Single-page operator console, not a marketing site or SaaS landing page
- Stats row on top (success rate, latency, failures by channel) — one visual hierarchy, not equal-weight metric soup
- Filter bar: channel, status, time range
- Dense notification table: id, channel, recipient, status, retryCount, lastError, timestamps; Retry only on FAILED/DEAD
- Live updates without a full page reload (polling is fine)
- Empty, loading, and error states that say what to do next
- No login, no settings, no sidebar of fake nav for phases we have not built

## What already works (Phase 1–3 — do not break)
- Modules: everdeliver-api, everdeliver-worker, everdeliver-common, everdeliver-persistence
- Java toolchain is 17 (sourceCompatibility 17). Do not bump to 21 unless I ask.
- Postgres in Docker Compose; Flyway owned by API; worker has spring.flyway.enabled=false
- Table notifications (no Phase 4 schema required unless a decision truly cannot fit existing columns): id, channel, status, recipient, subject, body, provider_message_id, retry_count, last_error, created_at, updated_at, sent_at
- Status: QUEUED → PROCESSING → SENT | FAILED; Kafka retries FAILED → PROCESSING (atomic retry_count++); DLT FAILED → DEAD
- POST /api/v1/notifications — flat multi-channel body; channel defaults to email; {email, subject, message} still works → persist QUEUED → Kafka notification-topic (payload includes id + channel + recipient) → {id, status:QUEUED}
- GET /api/v1/notifications/{id}
- GET /api/v1/notifications?status=&since=&limit=  (NO channel filter yet; no stats endpoint; no retry endpoint)
- Worker: ChannelSender strategies — email (SendGrid if SENDGRID_API_KEY else Mailpit), sms/whatsapp (Twilio), slack Incoming Webhook, generic webhook
- @RetryableTopic on notification-topic; attempts=4; backoff 5s / 30s / 2m; retry topics notification-topic-retry-5000|30000|120000; DLT notification-topic-dlq
- PermanentDeliveryException excluded from retries; HTTP 4xx except 408/429 = permanent; 408/429/5xx/timeouts = retryable
- Provider credentials are worker env only (.env → Compose). Slack/webhook URLs are per-request, not env
- Echo server on localhost:8888 for local Slack/webhook; Mailpit UI localhost:8025; API 8081; worker 8082
- Tests: Testcontainers Postgres + Kafka + WireMock; scripts/smoke.sh
- Known limitation (still deferred): no transactional outbox for API DB↔Kafka dual-write
- Known limitation: no dashboard auth (that is Phase 5). Phase 4 UI is a local/operator console

Key files:
- everdeliver-api/.../NotificationController.java, NotificationService.java, NotificationPersistenceService.java, NotificationResponse.java
- everdeliver-worker/.../NotificationConsumer.java, NotificationStatusService.java, delivery/ChannelSender*.java
- everdeliver-common/.../NotificationRequest.java, Channel.java
- everdeliver-persistence/.../Notification.java, NotificationRepository.java, NotificationStatus.java
- everdeliver-persistence/.../db/migration/V1__create_notifications.sql
- docker-compose.yml (kafka, postgres, mailpit, echo-server, api, worker)

## Hard rule — stop before coding
Phase 4 has unchecked Architecture Decisions (MUST CONFIRM BEFORE IMPLEMENTING).
Your FIRST reply must:
- Summarize Phase 4 goal + acceptance criteria in a short bullet list
- List EVERY architecture decision checkbox from SPEC Phase 4 (all 5)
- After fetching the three design URLs, state 3–5 design takeaways and a one-paragraph UX sketch (layout only — no code)
- Ask me to confirm each architecture item (give your recommendation per item, but wait for my answers)
- Also flag the API gaps below (channel filter, stats, manual retry) as decisions if they are not implied by the 5 boxes — wait for confirmation before inventing endpoints or schema
- Do NOT write application code, add a UI app, Compose services, or new APIs until I confirm

After I confirm:
1. Create an ADR under docs/ADR/ (copy 0000-template.md → 0004-…); update ADR index
2. Update docs/ARCHITECTURE.md and docs/DATA_MODEL.md if topology/schema/API contract changed
3. Check the Phase 4 architecture boxes in docs/SPEC.md
4. Then implement Phase 4 only
5. Match existing project style; keep changes scoped
6. Dashboard talks only to everdeliver-api. Never call SendGrid/Twilio/Slack from the browser. Never log or display full provider secrets (there should be none in the API today)

## Locked product context (do not reopen)
- Model A: EverDeliver login + paste keys is Phase 5. No Gmail OAuth for v1. No login in Phase 4.
- Email: SendGrid when configured; Mailpit local default
- SMS/WhatsApp: Twilio (WhatsApp = sandbox free-form only)
- Slack v1: Incoming Webhook
- Single Kafka topic notification-topic + channel field (ADR-0003)
- Strategy pattern in one worker (ADR-0003)
- Phase 1 persistence (ADR-0001) and Phase 2 retry/DLQ (ADR-0002) stay locked
- Prefer extending existing Gradle modules and Compose patterns. A new UI package/container is allowed if we confirm “separate container”
- Secrets never committed, never logged, never returned by API, never placed in Kafka payloads

## Phase 4 acceptance (from SPEC — “done” means)
- Dashboard runs on a configured port
- Live/updated notification list + stats
- Filter by channel, status, time range
- Retry action works
- README + LOCAL_SETUP say how to open the dashboard and retry a failed/dead notification
- Phase 1–3 API/worker behaviour still works (Mailpit email without keys; existing POST/GET)

## Suggested recommendations (propose these; wait for confirmation)

1. Frontend: React (Vite) vs Next.js vs Thymeleaf?
   Recommend: React + Vite in a new everdeliver-dashboard (npm) app. Next.js SSR/SEO is unused for an operator console. Thymeleaf keeps one language but fights a live feed and is a weaker resume story. ARCHITECTURE already assumes React does not call providers. Do not put the UI inside everdeliver-api source unless I pick “served from API”.

2. Real-time: SSE vs WebSocket vs polling?
   Recommend: short polling (every ~2s) against GET list for v1. Worker already writes status to Postgres; there is no status Kafka topic (ADR-0001 deferred that). Polling reuses the existing API, needs no extra broker, and is enough at v1 volume. SSE is a reasonable upgrade if I want push without WebSocket ops. Do NOT add WebSocket or a new Kafka status topic in this phase unless I insist.

3. Hosting: separate container vs served from API?
   Recommend: separate Compose service (Node build + nginx or vite preview) on port 3000, with Vite dev proxy /api → http://everdeliver-api:8081 (host: http://localhost:8081). Serving static files from Spring couples UI deploys to API rebuilds. CORS: allow the dashboard origin on the API (localhost:3000 / compose service name). No auth in Phase 4 (document that it is an open local console).

4. Charting library?
   Recommend: Recharts (React-native, small). Stats are success rate, latency (sent_at − created_at for SENT), failures by channel — a couple of bars/pies, not a BI tool. No D3. If I want zero extra UI deps, plain HTML/CSS counts + a table is acceptable; still confirm.

5. CSS approach: Tailwind vs component library?
   Recommend: Tailwind + shadcn-style copy-paste primitives (aligned with the design research URLs). Not a heavyweight component library (MUI/Ant/Chakra). Tokens and components live in the dashboard repo so we own the look. After exploring Impeccable/shadcn/gluestack, propose a short visual direction (type, density, color) in the confirmation reply — wait for me to accept it.

Also consider (confirm; these are required to meet acceptance but are not SPEC checkboxes):

- Channel filter: extend GET /api/v1/notifications with optional ?channel= (and keep status, since, limit). Do not invent a new table. Index already has (status, created_at DESC); channel filter can be a query param on the existing table. Confirm before adding a new Flyway index.

- Stats: add GET /api/v1/notifications/stats?since= that aggregates in Postgres (counts by status, counts by channel, success rate, avg latency for SENT). Do not pull the entire table into the browser to compute stats.

- Manual retry (US-4.4): there is NO retry API today. Recommend POST /api/v1/notifications/{id}/retry
  - Allowed only when status is FAILED or DEAD (409 otherwise)
  - API atomically sets status back to QUEUED (clear lastError optional — prefer clear lastError, keep retry_count as history of Kafka attempts)
  - Rebuild NotificationRequest from the row (channel + recipient/email/phone/url + subject + message + same id) and publish to notification-topic
  - Worker claimForProcessing already handles QUEUED → PROCESSING
  - Do not bypass Kafka (API must not deliver)
  - Do not retry SENT / QUEUED / PROCESSING
  - Idempotency: at-least-once still applies (Phase 7); a manual retry may double-send — document it

- No new Flyway unless stats/retry truly need a column (they should not).
- Outbox: still no, unless I explicitly ask.
- Do not implement login, Integrations UI, templates, campaigns, or Gmail.
- Do not change ChannelSender / retry topic names / provider env.

## Done means
All Phase 4 acceptance criteria in docs/SPEC.md are met, ADR-0004 recorded, docs updated (ARCHITECTURE, LOCAL_SETUP, README, SPEC boxes), Compose brings the dashboard up on the agreed port, list+filters+stats update without a full page reload (polling is fine), and a FAILED or DEAD row can be retried from the UI and then reaches SENT or DEAD again via the existing worker path.
```
