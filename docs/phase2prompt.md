# Phase 2 handoff prompt

Copy everything inside the fenced block below and paste it to another AI (or a new Cursor chat) to continue EverDeliver development.

---

```text
You are working in the EverDeliver repo (Java 21 / Spring Boot 3.3 / Kafka multi-module Gradle).

## Goal
Implement ONLY SPEC Phase 2 — Dead Letter Queue & Retries.
Do NOT implement Phase 3+ (multi-channel, dashboard, auth, campaigns) or refactor unrelated code.

## Mandatory reading (do this first, in order)
1. AGENTS.md
2. docs/SPEC.md — especially “Phase 2”, Guiding Principles, and “Current State (Phase 1 — Complete)”
3. docs/ARCHITECTURE.md
4. docs/DATA_MODEL.md
5. docs/ADR/0001-phase1-persistence.md (locked Phase 1 decisions — do not reopen)
6. docs/SECURITY.md (no new secrets in Phase 2)
7. docs/LOCAL_SETUP.md / README.md (how the stack runs today)
8. .cursor/rules/everdeliver-spec-gates.mdc
9. .cursor/rules/everdeliver-java.mdc

## What already works (Phase 1 — do not break)
- Modules: everdeliver-api, everdeliver-worker, everdeliver-common, everdeliver-persistence
- Postgres in Docker Compose; Flyway owned by API; worker has spring.flyway.enabled=false
- Table notifications already has retry_count and last_error columns (nullable/defaulted; unused until Phase 2)
- Status enum includes DEAD (reserved; unused until Phase 2)
- POST /api/v1/notifications → persist QUEUED → Kafka notification-topic (payload includes id) → return {id, status:QUEUED}
- Worker: conditional QUEUED→PROCESSING → SMTP (Mailpit) → SENT or FAILED; failures are swallowed so offsets commit
- GET /api/v1/notifications/{id} and GET /api/v1/notifications?status=&since=&limit=
- Known Phase 1 limitation (explicitly deferred): no transactional outbox for API DB↔Kafka dual-write

Key files:
- everdeliver-api/.../NotificationService.java, NotificationController.java
- everdeliver-worker/.../NotificationConsumer.java, NotificationStatusService.java
- everdeliver-persistence/.../Notification.java, NotificationRepository.java, NotificationStatus.java
- everdeliver-persistence/.../db/migration/V1__create_notifications.sql
- docker-compose.yml (kafka, postgres, mailpit, api, worker)

## Hard rule — stop before coding
Phase 2 has unchecked Architecture Decisions (MUST CONFIRM BEFORE IMPLEMENTING).
Your FIRST reply must:
- Summarize Phase 2 goal + acceptance criteria in 3–5 bullets
- List every architecture decision checkbox from SPEC Phase 2
- Ask me to confirm each one (give your recommendation per item, but wait for my answers)
- Do NOT write application code, add topics, or change Docker Compose until I confirm

After I confirm:
1. Create/update an ADR under docs/ADR/ (copy 0000-template.md → e.g. 0002-…); update ADR index
2. Update docs/ARCHITECTURE.md and docs/DATA_MODEL.md if topology/schema/status semantics changed
3. Check the Phase 2 architecture boxes in docs/SPEC.md
4. Then implement Phase 2 only
5. Match existing project style; keep changes scoped

## Locked product context (do not reopen)
- Model A: real email via SendGrid later; Mailpit local default today
- SMS/WhatsApp via Twilio later — Phase 2 is retry/DLQ only (still email/Mailpit path)
- Phase 1 persistence decisions in ADR-0001 stay locked
- Prefer extending existing Gradle modules and Compose patterns

## Phase 2 acceptance (from SPEC — “done” means)
- A simulated provider failure triggers retries
- After max retries, message lands in DLQ topic and DB status = DEAD
- Notification record shows retryCount and lastError

## Suggested recommendations (propose these; wait for confirmation)
1. Retry strategy: Kafka-native retry topics (e.g. notification-topic-retry-N) with delays, OR app-level scheduled re-publish — recommend Kafka retry topics + DLQ topic if it fits the stack; otherwise justify scheduled retries
2. Max retries / backoff: e.g. 3 retries with exponential backoff (5s / 30s / 2m) — propose concrete numbers
3. DLQ naming: e.g. notification-topic-dlq
4. Error storage: message only (already truncated to 1024); no full stack traces in DB
5. 4xx vs 5xx: for Phase 2 (Mailpit/SMTP only) treat connection/timeouts as retryable and clearly permanent failures (e.g. invalid address if detectable) as non-retryable → FAILED or DEAD without burning retries; document how this maps when SendGrid/Twilio arrive in Phase 3

Also consider whether Phase 2 should introduce a minimal transactional outbox for the API dual-write gap called out in ADR-0001 — recommend yes/no with rationale; do not expand into Phase 3+.

## Done means
All Phase 2 acceptance criteria in docs/SPEC.md are met, ADR recorded, docs updated, and you show how to verify (simulate failure → retries → DLQ + status DEAD + retryCount/lastError visible via GET).
```
