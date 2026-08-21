# EverDeliver — Security

## Purpose

Rules for handling credentials, auth, and safe defaults. Expand when Phase 5 (Integrations settings) is designed; confirm choices via SPEC + ADR before implementing crypto or auth.

---

## Non-negotiables

1. **Never commit** SendGrid API keys, Twilio tokens, DB passwords, or JWT secrets.
2. **Never log** full secrets or Authorization headers.
3. **Never return** full secrets from the API after save (mask: `sg••••••••abcd`).
4. Prefer **server-side** provider calls (workers), not browser → SendGrid/Twilio directly.
5. **Slack/webhook `recipient`** is a URL that may embed secrets. API GET responses mask it to `scheme://host/***`. The DB keeps the full URL for delivery.
6. **Provider error bodies** are not persisted into `last_error` for Slack/webhook — only a short static reason (hostile targets can echo arbitrary content).

---

## Product model A (locked)

Users log into **EverDeliver**, then paste provider keys in Settings. We do **not** implement Gmail OAuth for v1. See [SPEC.md](SPEC.md).

---

## Slack / webhook SSRF tradeoff

By design, `slack` and `webhook` notifications POST to **caller-supplied** http(s) URLs (`requireHttpUrl` checks scheme + host only). That enables local Compose targets such as `http://echo-server/`.

Optional guard: set `everdeliver.delivery.block-private-hosts=true` on the API. When enabled, hosts that resolve to loopback, link-local, site-local (RFC1918), multicast, or unspecified addresses are rejected with HTTP 400. **Default is `false`** so local/dev keeps working. Enabling it in shared or production deployments reduces SSRF risk against internal networks; it will break Docker-internal hostnames that resolve to private IPs.

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
