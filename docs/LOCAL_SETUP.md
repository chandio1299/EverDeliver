# EverDeliver — Local Setup

## Purpose

How to run the stack locally and (later) plug in **real** SendGrid + Twilio credentials. Expand as Phase 3/5 land.

---

## Prerequisites

- Docker Desktop
- Java 21 (for non-Docker `./gradlew bootRun`)
- Optional: SendGrid account, Twilio account (trial is enough for testing)

---

## Phase 1 — run everything (Postgres + Mailpit)

```bash
docker compose up --build
```

| Service | URL / port |
|---|---|
| API | http://localhost:8081 |
| Worker | http://localhost:8082 |
| Mailpit UI | http://localhost:8025 |
| Kafka | localhost:9092 |
| PostgreSQL | localhost:5432 |

**Postgres (local Compose defaults — not for production):**

| Env | Value |
|---|---|
| `POSTGRES_DB` | `everdeliver` |
| `POSTGRES_USER` | `everdeliver` |
| `POSTGRES_PASSWORD` | `everdeliver` |
| JDBC (in Compose) | `jdbc:postgresql://postgres:5432/everdeliver` |
| JDBC (host / bootRun) | `jdbc:postgresql://localhost:5432/everdeliver` |

Smoke test:

```bash
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","subject":"Hi","message":"Through Kafka"}'
# expect {"id":"<uuid>","status":"QUEUED"}

curl -s http://localhost:8081/api/v1/notifications/<id>
curl -s 'http://localhost:8081/api/v1/notifications?status=SENT&limit=10'
```

Check Mailpit for the message. Optional DB check:

```bash
docker compose exec postgres psql -U everdeliver -c 'select id, status from notifications;'
```

Infra only (then bootRun API + worker):

```bash
docker compose up kafka mailpit postgres -d
./gradlew :everdeliver-api:bootRun
./gradlew :everdeliver-worker:bootRun
```

### Known Phase 1 limitation (dual-write)

The API writes `QUEUED` to Postgres, then publishes to Kafka (blocks on the send future). There is **no transactional outbox** yet. If Kafka publish fails after the insert, you may see a stuck `QUEUED` row and an HTTP 500. Outbox / retry / DLQ land in Phase 2 ([ADR-0001](ADR/0001-phase1-persistence.md)).

---

## Real email (SendGrid) — stub

**When configured (Phase 3+):** set env vars (names TBD / confirm in ADR):

```text
SENDGRID_API_KEY=...
SENDGRID_FROM_EMAIL=noreply@your-verified-domain.com
```

Without these keys, email should keep using Mailpit.

Steps to flesh out later:

1. Create SendGrid API key
2. Verify sender / domain
3. Pass env into Docker Compose worker/API as decided

---

## Real SMS / WhatsApp (Twilio) — stub

```text
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_SMS_FROM=+1...
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
```

1. Twilio console → get SID/token
2. Buy or use trial SMS number
3. WhatsApp: join Twilio sandbox for dev
4. Never commit these values

Dashboard Integrations UI (Phase 5) will paste the same fields into encrypted storage — env remains the bootstrap path.

---

## Secrets

See [SECURITY.md](SECURITY.md). Use `.env` (gitignored) or Compose secrets; never commit keys.
