# EverDeliver — Local Setup

## Purpose

How to run the stack locally and plug in **real** SendGrid + Twilio credentials. Slack/webhook URLs are per-notification, not env.

---

## Prerequisites

- Docker Desktop
- Java 17+ (for non-Docker `./gradlew bootRun`; Gradle `sourceCompatibility` is 17)
- Optional: SendGrid account, Twilio account (trial is enough for testing)

---

## Run everything

```bash
docker compose up --build
```

| Service | URL / port |
|---|---|
| API | http://localhost:8081 |
| Worker | http://localhost:8082 |
| Mailpit UI | http://localhost:8025 |
| Echo server (local Slack/webhook target) | http://localhost:8888 |
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

Automated smoke (email → Mailpit SENT, Slack/webhook → echo-server SENT, unconfigured SMS/WhatsApp → DEAD):

```bash
chmod +x scripts/smoke.sh
./scripts/smoke.sh
```

Manual email smoke:

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
docker compose exec postgres psql -U everdeliver -c 'select id, channel, status, provider_message_id, retry_count, last_error from notifications;'
```

Infra only (then bootRun API + worker):

```bash
docker compose up kafka mailpit postgres -d
./gradlew :everdeliver-api:bootRun
./gradlew :everdeliver-worker:bootRun
```

### Known limitation (API dual-write)

The API writes `QUEUED` to Postgres, then publishes to Kafka (blocks on the send future). There is **no transactional outbox**. If Kafka publish fails after the insert, you may see a stuck `QUEUED` row and an HTTP 500. Outbox remains deferred ([ADR-0001](ADR/0001-phase1-persistence.md), [ADR-0002](ADR/0002-phase2-retry-dlq.md)).

---

## Phase 2 — retries & DLQ

Worker delivery failures are classified and routed by Spring Kafka `@RetryableTopic`:

| Topic | Role |
|---|---|
| `notification-topic` | First attempt |
| `notification-topic-retry-5000` | Retry after 5s |
| `notification-topic-retry-30000` | Retry after 30s |
| `notification-topic-retry-120000` | Retry after 2m |
| `notification-topic-dlq` | Exhausted retries or permanent failure → DB status `DEAD` |

Topics are created by the worker at startup (`KafkaAdmin` / `@RetryableTopic` auto-create). No Compose topic init is required.

### Simulate a provider failure (acceptance)

Rebuild/restart the worker with:

```bash
EVERDELIVER_DELIVERY_SIMULATE_FAILURE=true
```

In Compose, set that env under `everdeliver-worker` (the keys already exist, default `false`), then:

```bash
docker compose up --build -d everdeliver-worker

curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"email":"fail@example.com","subject":"Retry","message":"Should go DEAD"}'

# poll until DEAD (~2.5 minutes at production backoff)
curl -s http://localhost:8081/api/v1/notifications/<id>
# expect status=DEAD, retryCount=3, lastError set
```

Permanent-failure path (skips retries, `retryCount` stays 0):

```bash
EVERDELIVER_DELIVERY_SIMULATE_PERMANENT_FAILURE=true
```

Leave both flags **false** (default) for normal Mailpit delivery.

| Env | Default | Meaning |
|---|---|---|
| `EVERDELIVER_DELIVERY_SIMULATE_FAILURE` | `false` | Throw a retryable failure after claim |
| `EVERDELIVER_DELIVERY_SIMULATE_PERMANENT_FAILURE` | `false` | Throw a permanent failure (straight to DLQ) |

---

## Phase 3 — multi-channel (SendGrid / Twilio / Slack / webhook)

`POST /api/v1/notifications` accepts a flat body. `channel` defaults to `email`. Existing `{email, subject, message}` requests still work.

| Channel | Required fields | Destination stored in `recipient` |
|---|---|---|
| `email` | `email`, `message` | email address |
| `sms` / `whatsapp` | `phone` (E.164), `message` | phone |
| `slack` | `slackWebhookUrl`, `message` | Incoming Webhook URL |
| `webhook` | `webhookUrl`, `message` | callback URL |

Provider credentials are **worker env only**. Copy `.env.example` → `.env` (gitignored). Compose interpolates `${SENDGRID_API_KEY:-}` from the host / `.env`.

### Email — Mailpit (default) vs SendGrid

Without `SENDGRID_API_KEY`, email still goes to Mailpit (http://localhost:8025).

With a key:

1. Create a SendGrid API key with Mail Send permission.
2. Verify a sender (Single Sender or domain).
3. Set:

```text
SENDGRID_API_KEY=SG....
SENDGRID_FROM_EMAIL=noreply@your-verified-domain.com
```

4. Recreate the worker: `docker compose up --build -d everdeliver-worker`

```bash
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"email","email":"you@example.com","subject":"SendGrid","message":"Real inbox"}'
# GET the id — expect SENT and providerMessageId set
```

Failed SendGrid 4xx → `DEAD` immediately. 5xx/timeouts follow Phase 2 retry/DLQ.

### SMS / WhatsApp — Twilio

```text
TWILIO_ACCOUNT_SID=ACxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxx
TWILIO_SMS_FROM=+1xxxxxxxxxx
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
```

1. Twilio console → Account SID + Auth Token.
2. SMS: use a Twilio number (trial: verify the destination phone).
3. WhatsApp: [join the Twilio sandbox](https://www.twilio.com/docs/whatsapp/sandbox) (send the join code to the sandbox number). v1 is **free-form sandbox only** — no template SID.
4. Never commit these values. Empty Twilio env → SMS/WhatsApp fail permanently (`DEAD`, `retryCount=0`).

```bash
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"sms","phone":"+15551234567","message":"Hello SMS"}'

curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"whatsapp","phone":"+15551234567","message":"Hello WhatsApp"}'
```

### Slack Incoming Webhook

Create an Incoming Webhook in Slack and POST the URL on the notification (not env):

```bash
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"slack","slackWebhookUrl":"https://hooks.slack.com/services/...","message":"Hello Slack"}'
```

Local Compose smoke uses `http://echo-server/` (worker-internal DNS). From the host, the echo server is http://localhost:8888.

### Generic webhook

Worker POSTs `{id, channel, recipient, subject, message}`. 2xx = SENT; 408/429/5xx retry; other 4xx permanent.

```bash
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"webhook","webhookUrl":"http://echo-server/","subject":"Hi","message":"Callback"}'
```

Dashboard Integrations UI (Phase 5) will paste SendGrid/Twilio fields into encrypted storage — env remains the bootstrap path.

---

## Secrets

See [SECURITY.md](SECURITY.md). Use `.env` (gitignored) or Compose secrets; never commit keys.
