# EverDeliver agent guide

This repo is an event-driven messaging platform (Java / Spring Boot / Kafka). Product and phase gates live in docs — follow them.

## Before writing code

1. Read [docs/SPEC.md](docs/SPEC.md) for the phase you are implementing.
2. Present every **Architecture Decisions (MUST CONFIRM BEFORE IMPLEMENTING)** checkbox to the user.
3. Do **not** start implementation until the user confirms those decisions.
4. After confirmation, add/update an ADR under [docs/ADR/](docs/ADR/) and update [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) / [docs/DATA_MODEL.md](docs/DATA_MODEL.md) if needed.
5. Do not skip to a later phase without explicit user instruction.

## Locked product choices

- **Model A:** EverDeliver login + paste SendGrid/Twilio keys (real email/SMS). No Gmail OAuth for v1.
- **Email:** SendGrid when configured; Mailpit for local default.
- **SMS/WhatsApp:** Twilio.
- **Slack v1 preference:** Incoming Webhook.

## Docs map

| Doc | Use |
|---|---|
| [docs/SPEC.md](docs/SPEC.md) | Phases, user stories, decision gates |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, flows, ownership |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | Entities/statuses |
| [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) | Run locally + provider env |
| [docs/SECURITY.md](docs/SECURITY.md) | Secrets and auth boundaries |
| [docs/ADR/](docs/ADR/) | Locked technical decisions |
| [README.md](README.md) | Current runbook |
| [docs/phase4prompt.md](docs/phase4prompt.md) | Handoff prompt to start Phase 4 |

## Implementation hygiene

- Prefer extending existing Gradle modules (`everdeliver-api`, `everdeliver-worker`, `everdeliver-common`, `everdeliver-persistence`) and Docker Compose patterns. The Phase 4 UI lives in `everdeliver-dashboard` (npm, not Gradle).
- No secrets in git or logs.
- Keep changes scoped to the active phase.
