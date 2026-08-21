# ADR-0002: Phase 2 Retry Topics & Dead Letter Queue

- **Status:** Accepted
- **Date:** 2026-08-21
- **Phase:** SPEC Phase 2
- **Deciders:** Product owner (confirmed recommendations)

## Context

Phase 1 marks delivery failures as `FAILED` and swallows the exception so the Kafka offset commits. There is no retry and no DLQ. SPEC Phase 2 requires exponential backoff retries, a DLQ for exhausted/permanent failures, status `DEAD`, and `retryCount` / `lastError` on the notification record.

The API dual-write gap (Postgres insert then Kafka publish, no outbox) was deferred from ADR-0001 into Phase 2. Phase 2 is consumer-side delivery reliability; outbox is a separate producer-side concern.

## Decision

1. **Retry strategy:** Kafka-native non-blocking retries via Spring Kafka `@RetryableTopic`. Not application-level scheduled re-publish.
2. **Attempts / backoff:** `attempts=4` (1 initial + 3 retries). Exponential delays **5s → 30s → 2m** via `@Backoff(delay=5000, multiplier=6.0, maxDelay=120000)`. `maxDelay` is required; Spring Retry's `ExponentialBackOffPolicy` otherwise caps at 30s.
3. **Topics:** keep `notification-topic`. Retry topics use Spring's delay-suffix default: `notification-topic-retry-5000`, `notification-topic-retry-30000`, `notification-topic-retry-120000`. DLQ: `notification-topic-dlq` (`dltTopicSuffix="-dlq"`).
4. **Error storage:** message only, truncated to 1024 characters (existing column). No stack traces in the database.
5. **Retryable vs permanent (Phase 2, SMTP/Mailpit):**
   - Retryable: connection failures, timeouts, and other transient SMTP errors → retry topics.
   - Permanent: clearly invalid addresses (`AddressException` / `SendFailedException` when detectable) → DLT immediately (`exclude = PermanentDeliveryException`). `retry_count` is not burned.
   - Unknown errors default to retryable.
6. **Phase 3 mapping (SendGrid / Twilio HTTP):** 4xx (bad request / bad address) = non-retryable; 5xx and timeouts = retryable. Documented now; implemented when those providers land.
7. **Status / retry_count:**
   - First attempt: `QUEUED → PROCESSING` (no increment).
   - Each retry claim: `FAILED → PROCESSING` and `retry_count = retry_count + 1` in the same UPDATE.
   - Any delivery failure: `PROCESSING → FAILED` + `last_error`, then rethrow so `@RetryableTopic` routes the record.
   - `DEAD` is written only by `@DltHandler` (`FAILED → DEAD`).
8. **Transactional outbox:** still deferred. Phase 2 does not close the API DB↔Kafka dual-write gap.

## Alternatives considered

- **Scheduled DB re-publish** — precise timestamps from Postgres, fewer Kafka topics. Rejected: extra scheduler/race surface, weaker Kafka DLQ story, poorer fit for the current stack.
- **Index-suffixed retry topics (`-retry-0|1|2`)** — requires `SUFFIX_WITH_INDEX_VALUE`. Rejected: delay-suffixed names are the Spring default and self-document the backoff.
- **Rename `notification-topic`** — deferred to Phase 3 when channel routing is decided.
- **Outbox in Phase 2** — correct long-term producer pattern; does not help retry/DLQ acceptance criteria. Deferred again.

## Consequences

- Worker must produce to retry/DLT topics (Kafka producer config on the worker) and must **rethrow** classified exceptions (Phase 1 swallow is removed).
- Retry topics relax cross-message ordering; notifications are independent (keyed by id).
- At-least-once delivery can double-send if SMTP succeeded but the status write was lost. True send idempotency is Phase 7.
- SMTP 4xx/5xx classification is heuristic (JavaMail exceptions, not HTTP status). Tighten in Phase 3.
- Stuck `QUEUED` rows after a failed Kafka publish remain a known API limitation (no outbox).
- The claim guard (`QUEUED`/`FAILED` → `PROCESSING` only) prevents double-send on Kafka redelivery, but a crash after claim and before `markSent`/`markFailed` can leave a row stuck in `PROCESSING` (redelivery skips the non-claimable record). A scheduled reaper requeues stale `PROCESSING` rows to `QUEUED` and republishes them.
- Acceptance testing uses `everdeliver.delivery.simulate-failure` / `simulate-permanent-failure` (env-overridable; off by default). Full retry exhaustion takes ~2.5 minutes at production delays.

## References

- [SPEC.md](../SPEC.md) — Phase 2
- [ARCHITECTURE.md](../ARCHITECTURE.md)
- [DATA_MODEL.md](../DATA_MODEL.md)
- [ADR-0001](0001-phase1-persistence.md)
