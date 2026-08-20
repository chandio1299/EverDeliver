# EverDeliver — Security

## Purpose

Rules for handling credentials, auth, and safe defaults. Expand when Phase 5 (Integrations settings) is designed; confirm choices via SPEC + ADR before implementing crypto or auth.

---

## Non-negotiables

1. **Never commit** SendGrid API keys, Twilio tokens, DB passwords, or JWT secrets.
2. **Never log** full secrets or Authorization headers.
3. **Never return** full secrets from the API after save (mask: `sg••••••••abcd`).
4. Prefer **server-side** provider calls (workers), not browser → SendGrid/Twilio directly.

---

## Product model A (locked)

Users log into **EverDeliver**, then paste provider keys in Settings. We do **not** implement Gmail OAuth for v1. See [SPEC.md](SPEC.md).

---

## Planned areas (stub — decide before coding)

| Topic | Options to confirm |
|---|---|
| Dashboard auth | Session cookies vs JWT vs Spring Security form login |
| API auth | API keys for programmatic clients (Phase 7) |
| Secret storage | Encrypt-at-rest in Postgres vs env-only vs vault |
| Encryption key | Env `ENCRYPTION_KEY` / KMS — TBD |
| Kafka payloads | Store `userId`/`workspaceId` and look up creds; avoid embedding tokens in messages |
| CORS | Dashboard origin allowlist |

---

## Local / git hygiene

- Use `.env` + `.gitignore` (add if missing when implementing)
- Document required vars in [LOCAL_SETUP.md](LOCAL_SETUP.md)
- CI: stub providers (WireMock); do not use real Twilio/SendGrid in PR builds

---

## Threat notes (lightweight)

- Pasted Twilio keys can spend real money — add daily caps / confirm before bulk SMS (SPEC Phase 6).
- Dashboard must be auth-gated before Integrations UI ships.
