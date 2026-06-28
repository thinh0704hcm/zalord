#!/usr/bin/env bash
# End-to-end benchmark — KAFKA backend only.
# Twin script: scripts/run-benchmark-rabbit.sh
#
# Steps:
#   1. Recreate message/chat/notification with EVENT_BUS=kafka + OUTBOX_POLL_MS=50
#   2. Wait /health green
#   3. Run scripts/benchmark-e2e.py — appends 1 row to scripts/benchmark-results.json
#   4. Restore OUTBOX_POLL_MS=3000 (production default)
#
# Usage:
#   scripts/run-benchmark-kafka.sh                       # default rate=200, duration=60
#   RATE=500 DURATION=120 scripts/run-benchmark-kafka.sh
#   RESET=1 scripts/run-benchmark-kafka.sh               # wipe results file before run
#
# Run both backends back-to-back:
#   RESET=1 scripts/run-benchmark-rabbit.sh && scripts/run-benchmark-kafka.sh

set -euo pipefail
cd "$(dirname "$0")/.."

BACKEND=kafka
RATE="${RATE:-200}"
DURATION="${DURATION:-60}"
OUTBOX_POLL="${OUTBOX_POLL_MS:-50}"
OUT="${OUT:-scripts/benchmark-results.json}"

command -v python3 >/dev/null || { echo "✗ python3 required"; exit 1; }
python3 -c "import websockets" 2>/dev/null \
  || { echo "✗ pip3 install websockets"; exit 1; }

if [[ "${RESET:-0}" == "1" || ! -f "$OUT" ]]; then
  echo "[]" > "$OUT"
fi

wait_healthy() {
  local url=$1 name=$2 deadline=$(( $(date +%s) + 90 ))
  while (( $(date +%s) < deadline )); do
    curl -fsS --max-time 2 "$url" >/dev/null 2>&1 && return 0
    sleep 2
  done
  echo "✗ $name never came healthy ($url)"; return 1
}

echo "══════════════════════════════════════════════════════"
echo " KAFKA benchmark — rate=${RATE}/s  duration=${DURATION}s"
echo " (outbox poll = ${OUTBOX_POLL}ms during benchmark)"
echo "══════════════════════════════════════════════════════"

echo "⟳ recreating message/chat/notification with EVENT_BUS=$BACKEND..."
EVENT_BUS="$BACKEND" OUTBOX_POLL_MS="$OUTBOX_POLL" \
  docker compose up -d --force-recreate --no-deps \
  message-service chat-service notification-service >/dev/null

wait_healthy http://localhost:8083/actuator/health message-service
wait_healthy http://localhost:8084/health          chat-service
wait_healthy http://localhost:8087/health          notification-service
echo "✓ services healthy"
sleep 3

python3 scripts/benchmark-e2e.py \
  --backend "$BACKEND" --rate "$RATE" --duration "$DURATION" --out "$OUT"

echo
echo "Restoring OUTBOX_POLL_MS=3000 (production default)..."
docker compose up -d --force-recreate --no-deps \
  message-service chat-service notification-service >/dev/null
echo "✓ results appended to $OUT"
