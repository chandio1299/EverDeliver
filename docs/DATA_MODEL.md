# EverDeliver — Data Model

## Purpose

Define persistent entities, statuses, and relationships **before** implementing Phase 1 (notifications) and Phase 6 (templates/campaigns). Nothing here is final until confirmed and recorded in an ADR.

---

## Phase 0 (today)

No database. Messages exist only in Kafka and SMTP/Mailpit.

---

## Planned entities (stub)

### Notification

Delivery attempt / message record.

| Field (proposed) | Notes |
|---|---|
| `id` | UUID |
| `channel` | email \| sms \| whatsapp \| slack \| webhook |
| `status` | QUEUED \| PROCESSING \| SENT \| FAILED \| DEAD |
| `recipient` | email or E.164 phone or URL |
| `subject` / `body` | content snapshot |
| `providerMessageId` | Twilio SID / SendGrid id |
| `retryCount` | int |
| `lastError` | short text |
| `campaignId` | optional FK |
| `createdAt` / `updatedAt` / `sentAt` | timestamps |

**TBD:** confirm schema in SPEC Phase 1.

### User / Workspace

Dashboard login (Phase 5). Single-user vs multi-user TBD.

### IntegrationCredential

SendGrid / Twilio keys (Phase 5). See [SECURITY.md](SECURITY.md) — never store plaintext without a decision.

### Template

Named message body + channel + placeholders (Phase 6).

### Audience / AudienceMember

Recipient lists (Phase 6).

### Campaign / CampaignSchedule

Template + audience + timing + daily cap (Phase 6).

---

## Status enum (proposed)

```text
QUEUED → PROCESSING → SENT
              ↘
                FAILED → (retry) → SENT
                          ↘ (exhausted) → DEAD
```

Confirm retry/DLQ semantics in SPEC Phase 2.

---

## Ownership

**TBD:** which service writes status updates (API vs worker vs status events). Must be decided in Phase 1 before coding.
