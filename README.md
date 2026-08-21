# EverDeliver 🚀

EverDeliver is a resilient, event-driven notification engine built with Java 21, Spring Boot, and Apache Kafka. The system is designed with a decoupled microservices architecture to ensure high availability and fault tolerance in message delivery.

## 🏗 Architecture Overview

The project is structured as a Gradle Multi-Module project to maintain a clean separation of concerns:

- **everdeliver-api** (Producer): A RESTful entry point that persists notifications as `QUEUED`, publishes them to Kafka, and serves status APIs.
- **everdeliver-worker** (Consumer): A background service that consumes messages from Kafka, updates delivery status in Postgres, and sends email via SMTP.
- **everdeliver-persistence**: Shared JPA entity, repository, and Flyway migrations.
- **everdeliver-common**: Shared DTOs used by API and worker (Kafka payload).
- **Infrastructure**: Docker Compose — Kafka (KRaft), PostgreSQL, Mailpit, API, Worker.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/SPEC.md](docs/SPEC.md).

## 🛠 Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Build Tool**: Gradle (Wrapper)
- **Messaging**: Apache Kafka (KRaft mode - no Zookeeper required)
- **Database**: PostgreSQL 16 + Spring Data JPA + Flyway
- **Infrastructure**: Docker & Docker Compose
- **Testing/Mocking**: Mailpit (Mock SMTP Server)
- **Base Image**: Eclipse Temurin JDK/JRE 21

## 🚀 Quick Start with Docker

```bash
docker compose up --build
```

This single command will:
- Build `everdeliver-api` (Port 8081) and `everdeliver-worker` (Port 8082)
- Start Kafka (Port 9092), PostgreSQL (Port 5432), Mailpit (Ports 1025 & 8025)

Wait for all services to be healthy (~2-3 minutes on first run).

## 🚦 Getting Started (Local Development)

### Prerequisites

- Docker Desktop (for Kafka, Postgres & Mailpit)
- Java 21 (managed via SDKMAN! recommended)

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

## 🧪 Testing the Flow (Phase 1–2)

### Enqueue a notification

```bash
curl -s -X POST http://localhost:8081/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "subject": "Hello EverDeliver",
    "message": "This message traveled through Kafka!"
  }'
```

Expect JSON like `{"id":"<uuid>","status":"QUEUED"}`.

### Check status

```bash
curl -s http://localhost:8081/api/v1/notifications/<id>
curl -s 'http://localhost:8081/api/v1/notifications?status=SENT&limit=10'
```

### Verify via Mailpit

Open http://localhost:8025 — you should see the delivered email.

### Optional: inspect Postgres

```bash
docker compose exec postgres psql -U everdeliver -c 'select id, status, retry_count, last_error from notifications;'
```

### Phase 2 — retries & DLQ

Retryable SMTP failures go through `notification-topic-retry-5000` (5s), `-retry-30000` (30s), `-retry-120000` (2m), then `notification-topic-dlq` with status `DEAD`. See [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) for `EVERDELIVER_DELIVERY_SIMULATE_FAILURE`.

## 📦 Docker Architecture

### Multi-Stage Builds

Each service uses a two-stage Dockerfile:

1. **Build Stage**: `eclipse-temurin:21-jdk` compiles the Gradle project
2. **Runtime Stage**: `eclipse-temurin:21-jre` runs the packaged JAR

### Service Communication

Inside Docker, services communicate via container names:
- **Kafka**: `kafka:29092`
- **Postgres**: `postgres:5432`
- **Mailpit**: `mailpit:1025`

External access (localhost): 9092 (Kafka), 5432 (Postgres), 8025 (Mailpit UI)

## 🛣 Roadmap

- [x] Initial Kafka & Spring Boot Integration
- [x] E2E API-to-Worker Flow
- [x] Docker containerization with multi-stage builds
- [x] KRaft mode Kafka (no Zookeeper)
- [x] Message Persistence & Status Tracking (Phase 1)
- [x] Dead Letter Queue (DLQ) & retries for failed deliveries (Phase 2)
- [ ] Multi-channel support (SMS/Push)
- [ ] Kubernetes deployment manifests
