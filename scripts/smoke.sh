#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

API="${API_URL:-http://localhost:8081}"

echo "==> Starting Compose stack"
docker compose up --build -d

wait_for_api() {
  echo "==> Waiting for API"
  for i in $(seq 1 60); do
    if curl -sf "$API/api/v1/notifications?limit=1" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "API did not become ready" >&2
  docker compose logs --tail=80 everdeliver-api everdeliver-worker
  return 1
}

post() {
  local body="$1"
  curl -sf -X POST "$API/api/v1/notifications" \
    -H "Content-Type: application/json" \
    -d "$body"
}

poll_status() {
  local id="$1"
  local expected="$2"
  local seconds="${3:-45}"
  for i in $(seq 1 "$seconds"); do
    local json
    json="$(curl -sf "$API/api/v1/notifications/$id")"
    local status
    status="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['status'])" "$json")"
    if [[ "$status" == "$expected" ]]; then
      echo "$json"
      return 0
    fi
    sleep 1
  done
  echo "Notification $id did not reach $expected" >&2
  curl -s "$API/api/v1/notifications/$id" >&2 || true
  return 1
}

extract_id() {
  python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "$1"
}

wait_for_api

echo "==> Email (Mailpit, no SendGrid key)"
EMAIL_RESP="$(post '{"email":"smoke@example.com","subject":"Smoke","message":"Hello Mailpit"}')"
EMAIL_ID="$(extract_id "$EMAIL_RESP")"
poll_status "$EMAIL_ID" "SENT" 30 >/dev/null
echo "    email $EMAIL_ID SENT"

echo "==> Slack via echo-server"
SLACK_RESP="$(post '{"channel":"slack","slackWebhookUrl":"http://echo-server/","message":"Slack smoke"}')"
SLACK_ID="$(extract_id "$SLACK_RESP")"
poll_status "$SLACK_ID" "SENT" 30 >/dev/null
echo "    slack $SLACK_ID SENT"

echo "==> Webhook via echo-server"
HOOK_RESP="$(post '{"channel":"webhook","webhookUrl":"http://echo-server/","subject":"Hook","message":"Webhook smoke"}')"
HOOK_ID="$(extract_id "$HOOK_RESP")"
poll_status "$HOOK_ID" "SENT" 30 >/dev/null
echo "    webhook $HOOK_ID SENT"

echo "==> SMS without Twilio (expects DEAD, permanent)"
SMS_RESP="$(post '{"channel":"sms","phone":"+15551234567","message":"SMS smoke"}')"
SMS_ID="$(extract_id "$SMS_RESP")"
poll_status "$SMS_ID" "DEAD" 30 >/dev/null
echo "    sms $SMS_ID DEAD (Twilio not configured)"

echo "==> WhatsApp without Twilio (expects DEAD, permanent)"
WA_RESP="$(post '{"channel":"whatsapp","phone":"+15551234567","message":"WA smoke"}')"
WA_ID="$(extract_id "$WA_RESP")"
poll_status "$WA_ID" "DEAD" 30 >/dev/null
echo "    whatsapp $WA_ID DEAD (Twilio not configured)"

echo
echo "Smoke passed. Mailpit UI: http://localhost:8025"
echo "If SENDGRID_API_KEY / TWILIO_* are set in the environment, re-run after compose recreate to exercise live providers."
