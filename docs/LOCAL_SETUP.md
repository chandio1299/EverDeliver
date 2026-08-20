# EverDeliver — Local Setup

## Purpose

How to run the stack locally and (later) plug in **real** SendGrid + Twilio credentials. Expand as Phase 3/5 land.

---

## Prerequisites

- Docker Desktop
- Java 21 (for non-Docker `./gradlew bootRun`)
- Optional: SendGrid account, Twilio account (trial is enough for testing)

---

## Phase 0 — run everything (Mailpit only)

```bash
docker compose up --build
```

| Service | URL / port |
|---|---|
| API | http://localhost:8081 |
| Worker | http://localhost:8082 |
| Mailpit UI | http://localhost:8025 |
| Kafka | localhost:9092 |

Smoke test:

```bash
curl -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","subject":"Hi","message":"Through Kafka"}'
```

Check Mailpit for the message.

Infra only:

```bash
docker compose up kafka mailpit -d
./gradlew :everdeliver-api:bootRun
./gradlew :everdeliver-worker:bootRun
```

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
