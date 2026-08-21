# Phase 3 hardening handoff prompt

Copy everything inside the fenced block below and paste it to another AI (or a new Cursor chat) to fix the flaws found in the Phase 1–3 review. This is remediation work on shipped Phase 3 code — it does NOT open Phase 4.

Start that chat on branch **`feature/phase3`** (or `develop` / `main` only after Phase 3 is merged).

---

```text
You are working in the EverDeliver repo (Java 17 / Spring Boot 3.3 / Kafka multi-module Gradle).

## Goal
Fix the security, correctness, and operability flaws found in a review of Phase 1–3, plus small cleanups. This is remediation of shipped code, NOT a new phase. Do NOT open Phase 4, 5, or 6 scope.

## Mandatory reading (do this first, in order)
1. AGENTS.md
2. docs/SPEC.md — "Current State (Phase 3 — Complete)", Guiding Principles, and locked Product Model A
3. docs/ARCHITECTURE.md
4. docs/DATA_MODEL.md
5. docs/ADR/0001-phase1-persistence.md (locked — do not reopen)
6. docs/ADR/0002-phase2-retry-dlq.md (locked retry/DLQ — but see fix C1 which corrects one consequence note)
7. docs/ADR/0003-phase3-multi-channel.md (locked multi-channel — do not reopen)
8. docs/SECURITY.md
9. docs/LOCAL_SETUP.md / README.md
10. .cursor/rules/everdeliver-spec-gates.mdc
11. .cursor/rules/everdeliver-java.mdc

## Hard rules
- Do NOT implement any Phase 4+ feature: no auth/login, no Integrations settings / DB-stored secrets, no templates/audiences/campaigns, no observability (Grafana/Prometheus), no CI, no transactional outbox, no status Kafka topic, no topic-per-channel.
- Do NOT bump the JDK to 21 (bytecode stays 17; Dockerfiles already run JRE 21 — that is fine and out of scope).
- No secrets in git, logs, API responses, or Kafka payloads.
- Keep changes scoped to Phase 1–3 delivery code and its docs/Compose.

---

## FIXES (implement all)

### S1 — Mask slack/webhook `recipient` in API responses  [SECURITY, P0]
File: everdeliver-api/src/main/java/com/everdeliver/api/NotificationService.java
Problem: `toResponse()` (lines 110–125) returns `recipient` verbatim. For `slack`/`webhook` channels `recipient` IS the webhook URL (Slack bearer secret in the path, possibly signed query for webhooks). Exposed on unauthenticated GET /{id} and list.
Fix:
- Add a masking helper. For `SLACK`/`WEBHOOK` reduce `recipient` to `scheme://host/***`. Email/phone unchanged (not credentials).
- DB keeps the full URL (worker needs it to deliver). Mask only at the response boundary.
- Add tests in everdeliver-api/src/test/java/com/everdeliver/api/NotificationServiceTest.java: slack/webhook masked, email not masked.

### S2 — Stop persisting raw provider error bodies  [SECURITY, P0]
Files: everdeliver-worker/src/main/java/com/everdeliver/worker/delivery/SlackSender.java and WebhookSender.java
Problem: both pass `ex.getResponseBodyAsString()` into `HttpStatusMapper.toDeliveryException`, which flows into `last_error` and back out through `NotificationResponse.lastError`. A hostile webhook can echo arbitrary content into the read API. `Redactor` only strips query strings/auth tokens, not arbitrary bodies.
Fix: pass a short static reason (e.g. "HTTP 404 from webhook") instead of the raw response body, or truncate to a small fixed length before persisting.

### S3 — Document SSRF tradeoff + optional guard  [SECURITY, P2]
Files: everdeliver-api/src/main/java/com/everdeliver/api/NotificationRequestValidator.java, docs/SECURITY.md
Context: `webhook`/`slack` POST to arbitrary user-supplied URLs by design (`requireHttpUrl` only checks http/https + host). Do NOT block private IPs by default (breaks local `http://echo-server/`).
Fix:
- Add a config-gated guard `everdeliver.delivery.block-private-hosts` (default `false`) that resolves host and rejects private/loopback/link-local ranges when enabled.
- Document the tradeoff in docs/SECURITY.md.

### C1 — Reap stuck `PROCESSING` records  [CORRECTNESS, P0]
Files: everdeliver-worker/src/main/java/com/everdeliver/worker/NotificationConsumer.java, everdeliver-persistence/src/main/java/com/everdeliver/persistence/NotificationRepository.java, everdeliver-worker/src/main/java/com/everdeliver/worker/WorkerApplication.java, everdeliver-worker/src/main/resources/application.yml, docs/ADR/0002-phase2-retry-dlq.md, docs/DATA_MODEL.md
Problem: in `consume()` (lines 45–52), if the process dies after `QUEUED→PROCESSING` claim but before `markSent`/`markFailed`, the redelivered record fails the claim and is silently skipped (offset commits), leaving the row stuck in `PROCESSING` forever. No sweeper exists.
Fix:
- Add repository method: `requeueStaleProcessing(cutoff, now)` -> `update Notification set status=QUEUED, updatedAt=:now where status=PROCESSING and updatedAt < :cutoff`.
- Add a `NotificationReaper` component with `@Scheduled(fixedDelayString=...)`.
- Add `@EnableScheduling` to WorkerApplication.
- Config: `everdeliver.delivery.processing-timeout` (default 5m) + reaper interval in application.yml.
- Correct the ADR-0002 consequence note: the claim guard prevents double-send but can leave stuck PROCESSING; add a status note in DATA_MODEL.md.

### O1 — Actuator health + Compose healthcheck/restart  [OPERABILITY, P1]
Files: everdeliver-api/build.gradle, everdeliver-worker/build.gradle, everdeliver-api/src/main/resources/application.yml, everdeliver-worker/src/main/resources/application.yml, everdeliver-api/Dockerfile, everdeliver-worker/Dockerfile, docker-compose.yml
Problem: worker build.gradle line 9 says `spring-boot-starter-web` is "For Actuator/Health checks", but `spring-boot-starter-actuator` is absent — no /actuator/health on either service; no Compose healthcheck/restart.
Fix:
- Add `spring-boot-starter-actuator` to both build.gradle.
- Expose `management.endpoints.web.exposure.include: health` in both application.yml.
- Add `curl` to both Dockerfile runtime stages (apt) and add Compose `healthcheck` on /actuator/health + `restart: unless-stopped` for api/worker.

### A1 — `updatedSince` for the live feed  [API, P1]
Files: everdeliver-api/src/main/java/com/everdeliver/api/NotificationService.java, everdeliver-persistence/src/main/java/com/everdeliver/persistence/NotificationRepository.java
Problem: `list()` filters by `created_at` only; the Phase 4 feed needs "recent status changes."
Fix: add `?updatedSince=` (ISO-8601) + repository method `findByUpdatedAtGreaterThanEqualOrderByUpdatedAtDesc`, keep `since` for creation time.

### A2 — `channel` filter + indexes  [API, P1]
Files: everdeliver-api/src/main/java/com/everdeliver/api/NotificationService.java, NotificationController.java, everdeliver-persistence/src/main/java/com/everdeliver/persistence/NotificationRepository.java, everdeliver-persistence/src/main/resources/db/migration/V2__add_notification_indexes.sql (new)
Fix: add `?channel=` to the list endpoint. Use `JpaSpecificationExecutor<Notification>` (or explicit combined methods) to avoid combinatorial derived methods. Add Flyway V2 with `(channel, created_at DESC)` and `(updated_at DESC)` indexes.

### A3 — Bound `subject` and `message` lengths  [API, P1]
File: everdeliver-api/src/main/java/com/everdeliver/api/NotificationRequestValidator.java
Problem: `subject` is unbounded (DB column VARCHAR(1024)) -> overlong subject causes raw SQL error -> HTTP 500. `message` (TEXT) also unbounded.
Fix: subject <= 1024 (400 otherwise); message cap (e.g. 128 KB).

### P2 — Cleanups
- README.md line 3: change "Java 21" to "Java 17".
- Remove dead `Notification.setLastErrorTruncated` in everdeliver-persistence/src/main/java/com/everdeliver/persistence/Notification.java (truncation already lives in NotificationStatusService).
- `channel` Kafka header is write-only (NotificationService.enqueue adds it; consumer never reads it). Remove it and update the header assertion in NotificationServiceTest.java. (Alternative: log it in the consumer — but prefer removing.)
- POST /notifications returns 200 for an async enqueue; switch to 202 Accepted via @ResponseStatus in NotificationController.java. NOTE: this is a client-visible contract change — keep the change minimal and document it.

---

## What already works (do not break)
- Modules: everdeliver-api, everdeliver-worker, everdeliver-common, everdeliver-persistence
- Java toolchain 17 (sourceCompatibility 17); Docker images use Temurin 21 JRE
- Postgres in Docker Compose; Flyway owned by API; worker has spring.flyway.enabled=false
- Table notifications: id, channel, status, recipient, subject, body, provider_message_id, retry_count, last_error, created_at, updated_at, sent_at
- Status: QUEUED → PROCESSING → SENT | FAILED; retries FAILED → PROCESSING (atomic retry_count++); DLT FAILED → DEAD
- POST /api/v1/notifications (flat multi-channel body, channel defaults to email) → persist QUEUED → Kafka notification-topic → {id, status:QUEUED}
- GET /api/v1/notifications/{id}; GET /api/v1/notifications?status=&since=&limit=
- Worker ChannelSender strategies: email (SendGrid if key else Mailpit), sms/whatsapp (Twilio), slack Incoming Webhook, generic webhook
- @RetryableTopic attempts=4, backoff 5s/30s/2m; retry topics notification-topic-retry-5000|30000|120000; DLT notification-topic-dlq
- PermanentDeliveryException excluded from retries; HTTP 4xx except 408/429 = permanent; 408/429/5xx/timeouts = retryable
- Provider credentials worker env only; Slack/webhook URLs per-request
- Tests: Testcontainers Postgres + Kafka + WireMock; scripts/smoke.sh
- Known limitation (still deferred): no transactional outbox; no dashboard auth (Phase 5)

## Verification (done means)
- `./gradlew clean build` green (unit + IT; Testcontainers need Docker running)
- `scripts/smoke.sh` still passes end-to-end (email Mailpit SENT, slack/webhook SENT, sms/whatsapp DEAD)
- GET /{id} for a slack or webhook notification shows a masked recipient (scheme://host/***), never the full URL
- GET list supports status, since, updatedSince, channel, limit filters
- A stuck PROCESSING row older than the timeout is requeued by the reaper and eventually reaches SENT/DEAD
- /actuator/health returns UP on both api (8081) and worker (8082); Compose containers restart on crash
- No secrets in git (verify .env is gitignored and not committed)
```
