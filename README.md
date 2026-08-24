# EverDeliver 🚀

EverDeliver is a resilient, event-driven notification engine built with Java 17, Spring Boot, and Apache Kafka. The system is designed with a decoupled microservices architecture to ensure high availability and fault tolerance in message delivery.

## 🏗 Architecture Overview

The project is structured as a Gradle Multi-Module project to maintain a clean separation of concerns:

- **everdeliver-api** (Producer): A RESTful entry point that persists notifications as `QUEUED`, publishes them to Kafka, and serves status, stats, and manual retry APIs.
- **everdeliver-worker** (Consumer): Kafka consumer that updates delivery status and sends via SendGrid/Mailpit, Twilio, Slack Incoming Webhook, or generic HTTP.
- **everdeliver-persistence**: Shared JPA entity, repository, and Flyway migrations.
- **everdeliver-common**: Shared DTOs + `Channel` enum (Kafka / API payload).
- **everdeliver-dashboard**: Operator console (React + Vite) that polls the API. Never calls providers.
- **Infrastructure**: Docker Compose — Kafka (KRaft), PostgreSQL, Mailpit, echo-server, API, Worker, Dashboard.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/SPEC.md](docs/SPEC.md).

## 🛠 Tech Stack

- **Language**: Java 17 (Gradle `sourceCompatibility`; Docker images use Temurin 21 JRE)
- **Framework**: Spring Boot 3.3
- **Build Tool**: Gradle (Wrapper)
- **Messaging**: Apache Kafka (KRaft mode - no Zookeeper required)
- **Database**: PostgreSQL 16 + Spring Data JPA + Flyway
- **Infrastructure**: Docker & Docker Compose
- **Providers**: SendGrid (email), Twilio (SMS/WhatsApp), Slack Incoming Webhook, generic HTTP webhook
- **Local email**: Mailpit (used when `SENDGRID_API_KEY` is unset)
- **Base Image**: Eclipse Temurin JDK/JRE 21

## 🚀 Quick Start with Docker

```bash
docker compose up --build
```

This single command will:
- Build `everdeliver-api` (Port 8081), `everdeliver-worker` (Port 8082), and `everdeliver-dashboard` (Port 3000)
- Start Kafka (Port 9092), PostgreSQL (Port 5432), Mailpit (Ports 1025 & 8025), echo-server (Port 8888)

Wait for all services to be healthy (~2-3 minutes on first run). Open the delivery console at http://localhost:3000.

Automated smoke:

```bash
./scripts/smoke.sh
```

## 🚦 Getting Started (Local Development)

### Prerequisites

- Docker Desktop (for Kafka, Postgres, Mailpit)
- Java 17+ (Gradle compiles to 17)

### 1. Start Infrastructure Only

```bash
docker compose up kafka mailpit postgres -d
```

### 2. Build the Project

```bash
./gradlew clean build
```

### 3. Run the Services Locally

**Tab 1 (API):**

```bash
./gradlew :everdeliver-api:bootRun
```

**Tab 2 (Worker):**

```bash
./gradlew :everdeliver-worker:bootRun
```

## 🧪 Testing the Flow (Phase 3)

### Enqueue email (no SendGrid key → Mailpit)

```bash
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "subject": "Hello EverDeliver",
    "message": "This message traveled through Kafka!"
  }'
```

Expect JSON like `{"id":"<uuid>","status":"QUEUED"}`. Open http://localhost:8025 for the delivered email.

### Other channels

```bash
# SMS / WhatsApp need TWILIO_* on the worker; without keys they go DEAD
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"sms","phone":"+15551234567","message":"Hello SMS"}'

# Slack / webhook — Compose echo-server is reachable as http://echo-server/ from the worker
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"webhook","webhookUrl":"http://echo-server/","message":"Callback"}'
```

SendGrid + Twilio setup, retries, and env vars: [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md).

### Check status

```bash
curl -s http://localhost:8081/api/v1/notifications/<id>
curl -s 'http://localhost:8081/api/v1/notifications?status=SENT&limit=10'
curl -s 'http://localhost:8081/api/v1/notifications/stats'
```

Or watch the same row on the delivery console: http://localhost:3000 (filters + live poll + Retry on `FAILED`/`DEAD`). Setup detail: [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md).

### Optional: inspect Postgres

```bash
docker compose exec postgres psql -U everdeliver -c 'select id, channel, status, provider_message_id, retry_count, last_error from notifications;'
```

### Phase 2 — retries & DLQ

Retryable failures go through `notification-topic-retry-5000` (5s), `-retry-30000` (30s), `-retry-120000` (2m), then `notification-topic-dlq` with status `DEAD`. See [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) for `EVERDELIVER_DELIVERY_SIMULATE_FAILURE`.

## 📦 Docker Architecture

### Multi-Stage Builds

Each Java service uses a two-stage Dockerfile (`eclipse-temurin:21-jdk` → JRE). The dashboard uses Node to build the Vite app, then `nginx:alpine` to serve it and proxy `/api/` to the API.

### Service Communication

Inside Docker, services communicate via container names:
- **Kafka**: `kafka:29092`
- **Postgres**: `postgres:5432`
- **Mailpit**: `mailpit:1025`
- **Echo server**: `echo-server:80` (host port 8888)

External access (localhost): 9092 (Kafka), 5432 (Postgres), 8025 (Mailpit UI), 8888 (echo-server)

## 🛣 Roadmap

- [x] Initial Kafka & Spring Boot Integration
- [x] E2E API-to-Worker Flow
- [x] Docker containerization with multi-stage builds
- [x] KRaft mode Kafka (no Zookeeper)
- [x] Message Persistence & Status Tracking (Phase 1)
- [x] Dead Letter Queue (DLQ) & retries for failed deliveries (Phase 2)
- [x] Multi-channel support (email / SMS / WhatsApp / Slack / webhook)
- [x] Delivery console (live feed, stats, manual retry)
- [ ] Kubernetes deployment manifests
