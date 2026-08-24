# ADR-0004: Phase 4 Delivery Console

- **Status:** Accepted
- **Date:** 2026-08-21
- **Phase:** SPEC Phase 4
- **Deciders:** Product owner (confirmed recommendations, including Operate-mode visual direction)

## Context

Phase 1–3 persist notifications, retry/DLQ through Kafka, and deliver on five channels. There is no operator UI. Phase 4 needs a local delivery console: live feed, filters, aggregate stats, and manual retry. Login and Integrations settings remain Phase 5. ADR-0001, ADR-0002, and ADR-0003 stay locked. `GET /api/v1/notifications` already supports `?channel=` (no schema change).

## Decision

1. **Frontend:** React + Vite + TypeScript in a new `everdeliver-dashboard` npm app. Not a Gradle module. Not Next.js. Not Thymeleaf. Not served from `everdeliver-api` source.
2. **Real-time:** short polling (~2s) of the list and stats APIs. No SSE, WebSocket, or status Kafka topic.
3. **Hosting:** separate Compose service on port **3000**. Production image is a Node build served by nginx. nginx proxies `/api/` to `http://everdeliver-api:8081/api/`. Vite dev server proxies `/api` to `http://localhost:8081`.
4. **CORS:** API allowlists dashboard origins (`everdeliver.api.cors-allowed-origins`, default `http://localhost:3000`) so `npm run dev` can call the API directly. Compose same-origin proxy is the production path. No dashboard auth in Phase 4 (open local console).
5. **Charting:** Recharts, used only for compact supporting visuals (failures by channel). The notification table is the primary surface.
6. **CSS:** Tailwind + locally owned shadcn-style primitives (button, badge, select, table, alert). No MUI/Ant/Chakra.
7. **Visual direction:** Impeccable Operate mode, refined with [Taste Skill](https://www.tasteskill.dev/) redesign rules and [awesome-design-md](https://github.com/VoltAgent/awesome-design-md) refs (Linear / PostHog / HashiCorp). Tokens live in `everdeliver-dashboard/DESIGN.md`. Single-page console, no sidebar, no equal-weight metric cards, no gradients/glass/decorative animation. Success rate is the primary statistic; latency and failures are supporting. Status always uses a text label plus color.
8. **Stats API:** `GET /api/v1/notifications/stats?since=` aggregates in PostgreSQL (`byStatus`, `byChannel`, `successRate`, `avgLatencyMs` for SENT, `failuresByChannel` for FAILED+DEAD). Optional `since` filters `created_at`.
9. **Manual retry:** `POST /api/v1/notifications/{id}/retry`. Allowed only when status is `FAILED` or `DEAD` (409 otherwise; 404 if missing). API atomically sets `QUEUED`, clears `last_error`, keeps `retry_count`, rebuilds the Kafka payload from the row, and publishes to `notification-topic`. Worker `claimForProcessing` handles `QUEUED → PROCESSING`. API never delivers. A manual retry may double-send (at-least-once; Phase 7).
10. **Schema:** no new Flyway. Existing columns and indexes are sufficient.
11. **Secrets:** dashboard talks only to `everdeliver-api`. Browser never calls SendGrid/Twilio/Slack. Slack/webhook recipients stay masked on GET responses.

## Alternatives considered

- **Next.js** — unused SSR/SEO for an operator console. Rejected.
- **Thymeleaf in the API** — weaker live-feed story; couples UI deploys to API rebuilds. Rejected.
- **SSE / WebSocket** — extra broker/ops surface; worker already writes status to Postgres. Deferred.
- **Serve static files from Spring** — couples UI deploys to API rebuilds. Rejected for Compose; Vite proxy remains for local frontend work.
- **D3 or no charts** — Recharts is enough for a compact bar; a large BI chart would steal space from the queue.
- **Heavyweight component library** — fights Operate-mode density and ownership of tokens. Rejected.
- **New `channel` index / extra columns** — existing `(channel, created_at DESC)` and status columns suffice.

## Consequences

- Compose gains `everdeliver-dashboard` on localhost:3000.
- API gains CORS config, a stats endpoint, and a retry endpoint. Phase 1–3 POST/GET and worker delivery are unchanged.
- Manual retry reuses the known dual-write limitation (DB then Kafka; no outbox).
- Dashboard is an unauthenticated local operator console until Phase 5.

## References

- [SPEC.md](../SPEC.md) — Phase 4
- [ARCHITECTURE.md](../ARCHITECTURE.md)
- [DATA_MODEL.md](../DATA_MODEL.md)
- [SECURITY.md](../SECURITY.md)
- [ADR-0001](0001-phase1-persistence.md)
- [ADR-0002](0002-phase2-retry-dlq.md)
- [ADR-0003](0003-phase3-multi-channel.md)
