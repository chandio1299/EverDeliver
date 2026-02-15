# EverDeliver 🚀

EverDeliver is a resilient, event-driven notification engine built with Java 21, Spring Boot, and Apache Kafka. The system is designed with a decoupled microservices architecture to ensure high availability and fault tolerance in message delivery.

## 🏗 Architecture Overview

The project is structured as a Gradle Multi-Module project to maintain a clean separation of concerns:

- **everdeliver-api** (Producer): A RESTful entry point that validates notification requests and asynchronously publishes them to Kafka.

- **everdeliver-worker** (Consumer): A background service that consumes messages from Kafka and executes the delivery logic via SMTP.

- **everdeliver-common**: A shared library containing core Data Transfer Objects (DTOs) and utilities used by both services.

- **Infrastructure**: Orchestrated via Docker, featuring Kafka (KRaft mode) and Mailpit for local SMTP testing.

## 🛠 Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Build Tool**: Gradle (Wrapper)
- **Messaging**: Apache Kafka (KRaft mode - no Zookeeper required)
- **Infrastructure**: Docker & Docker Compose
- **Testing/Mocking**: Mailpit (Mock SMTP Server)
- **Base Image**: Eclipse Temurin JDK/JRE 21

## 🚀 Quick Start with Docker

The easiest way to run the entire stack is using Docker Compose. This approach builds and runs all services in isolated containers:

```bash
docker compose up --build
```

This single command will:
- Build the `everdeliver-api` service (Port 8081)
- Build the `everdeliver-worker` service (Port 8082)
- Start Kafka broker with KRaft mode (Port 9092)
- Start Mailpit for email testing (Ports 1025 & 8025)

Wait for all services to be healthy (~2-3 minutes on first run).

## 🚦 Getting Started (Local Development)

If you prefer running services locally outside Docker:

### Prerequisites

- Docker Desktop (for Kafka & Mailpit)
- Java 21 (managed via SDKMAN! recommended)

### 1. Start Infrastructure Only

Launch just Kafka and Mailpit:

```bash
docker compose up kafka mailpit -d
```

### 2. Build the Project

Use the Gradle wrapper to compile all modules:

```bash
./gradlew clean build
```

### 3. Run the Services Locally

Open two terminal tabs and start the services:

**Tab 1 (API):**

```bash
./gradlew :everdeliver-api:bootRun
```

**Tab 2 (Worker):**

```bash
./gradlew :everdeliver-worker:bootRun
```

## 🧪 Testing the Flow

### Trigger a Notification:

Send a POST request to the API:

```bash
curl -X POST http://localhost:8081/api/v1/notifications \
-H "Content-Type: application/json" \
-d '{
  "email": "user@example.com",
  "subject": "Hello EverDeliver",
  "message": "This message traveled through Kafka!"
}'
```

### Verify via Mailpit:

Open your browser and navigate to http://localhost:8025. You should see the delivered email in the mock inbox.

## 📦 Docker Architecture

### Multi-Stage Builds

Each service uses a two-stage Dockerfile for optimal image size:

1. **Build Stage**: Uses `eclipse-temurin:21-jdk` to compile the Gradle project
2. **Runtime Stage**: Uses `eclipse-temurin:21-jre` to run the packaged JAR

### Service Communication

Inside Docker, services communicate via container names:
- **Kafka**: `kafka:29092` (internal network)
- **Mailpit**: `mailpit:1025` (SMTP server)

External access (localhost) uses ports: 9092 (Kafka), 8025 (Mailpit UI)

## 🛣 Roadmap

- [x] Initial Kafka & Spring Boot Integration
- [x] E2E API-to-Worker Flow
- [x] Docker containerization with multi-stage builds
- [x] KRaft mode Kafka (no Zookeeper)
- [ ] Dead Letter Queue (DLQ) Implementation for Failed Deliveries
- [ ] Multi-channel support (SMS/Push)
- [ ] Message Persistence & Status Tracking
- [ ] Kubernetes deployment manifests
