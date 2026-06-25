#!/usr/bin/env bash
# Orchestrates k6 load test across RabbitMQ + Kafka.
#
# Steps:
#   1. Login Alice + Bob via Kong, get caller user-id + open DIRECT conv
#   2. For each backend in [rabbitmq, kafka]:
#        a. docker compose recreate message/chat/notification with EVENT_BUS=<backend>
#        b. wait until all 3 are /health green
#        c. run k6 with PROFILE (default: sustained)
#   3. Per-backend JSON dropped at scripts/loadtest-<backend>-<profile>.json
#
# Usage:
#   scripts/run-loadtest.sh                 # default profile=sustained, both backends
#   PROFILE=smoke scripts/run-loadtest.sh   # 30s quick check
#   PROFILE=ramp  scripts/run-loadtest.sh   # 7-min ramp to find breakpoint
#   BACKENDS=kafka scripts/run-loadtest.sh  # only one backend
#
# Requires: docker compose stack already up, Alice (0900111001) + Bob (0900111002)
# registered with password "secret123". k6 runs as a docker container on the
# compose network (grafana/k6:latest — pulled automatically).

set -euo pipefail

KONG="${KONG:-http://localhost:8080/api/v1}"
PROFILE="${PROFILE:-sustained}"
BACKENDS="${BACKENDS:-rabbitmq kafka}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:latest}"
COMPOSE_NET="${COMPOSE_NET:-zalord_zalord-net}"

cd "$(dirname "$0")/.."

command -v docker >/dev/null || { echo "✗ docker not installed"; exit 1; }
command -v jq     >/dev/null || { echo "✗ jq not installed — brew install jq"; exit 1; }

echo "── bootstrap ────────────────────────────────────────"
# Retry helper — Kong sometimes returns 502 during cold-start or 429 on rate-limit
# burst. We retry with backoff so a transient failure doesn't waste a 12-min run.
retry_curl() {
  local i body
  for i in 1 2 3 4 5; do
    if body=$(curl -fsS "$@" 2>/dev/null); then
      echo "$body"; return 0
    fi
    sleep $((i * 3))
  done
  echo "✗ curl failed after 5 retries: $*" >&2
  return 1
}
login() {
  retry_curl -X POST "$KONG/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"phoneNumber\":\"$1\",\"password\":\"secret123\"}" \
    | jq -r '.data.accessToken'
}
ATOK=$(login 0900111001) || exit 1
BTOK=$(login 0900111002) || exit 1
[ -n "$ATOK" ] && [ -n "$BTOK" ] || { echo "✗ login returned empty token"; exit 1; }

UID_A=$(retry_curl "$KONG/users/me" -H "Authorization: Bearer $ATOK" | jq -r '.userId')
UID_B=$(retry_curl "$KONG/users/me" -H "Authorization: Bearer $BTOK" | jq -r '.userId')

# Hit message-service directly for conversation setup — Kong → message-service
# is flaky under our config (intermittent 502). The load test itself also bypasses
# Kong (TARGET in loadtest.js), so we stay consistent.
CONV=$(retry_curl -X POST "http://localhost:8083/api/v1/conversations" \
  -H "X-User-Id: $UID_A" \
  -H 'Content-Type: application/json' \
  -d "{\"type\":\"DIRECT\",\"memberUserId\":\"$UID_B\"}" \
  | jq -r '.data.id')

echo "  caller=$UID_A"
echo "  conv=$CONV"
echo "  profile=$PROFILE   backends=$BACKENDS"
echo

wait_healthy() {
  local url=$1 name=$2 deadline=$(( $(date +%s) + 90 ))
  while (( $(date +%s) < deadline )); do
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  echo "✗ $name never came healthy ($url)"; return 1
}

for BACKEND in $BACKENDS; do
  echo "── backend: $BACKEND ────────────────────────────────"
  echo "  ⟳ recreating message/chat/notification with EVENT_BUS=$BACKEND..."
  EVENT_BUS="$BACKEND" docker compose up -d \
    --force-recreate --no-deps \
    message-service chat-service notification-service \
    >/dev/null

  wait_healthy http://localhost:8083/actuator/health message-service
  wait_healthy http://localhost:8084/health          chat-service
  wait_healthy http://localhost:8087/health          notification-service
  echo "  ✓ services healthy"
  sleep 3  # let consumers settle

  echo "  ▶ running k6 (profile=$PROFILE)"
  docker run --rm -i \
    --network "$COMPOSE_NET" \
    --user "$(id -u):$(id -g)" \
    -v "$(pwd)/scripts:/scripts" \
    -w /scripts \
    -e "UID=$UID_A" \
    -e "CONV=$CONV" \
    -e "BACKEND=$BACKEND" \
    -e "PROFILE=$PROFILE" \
    -e "TARGET=http://message-service:8083/api/v1/messages" \
    "$K6_IMAGE" run /scripts/loadtest.js
  echo
done

echo "── done ─────────────────────────────────────────────"
echo "Per-backend JSON: scripts/loadtest-*-${PROFILE}.json"
echo "Grafana:          http://localhost:3000/d/zalord-overview"
