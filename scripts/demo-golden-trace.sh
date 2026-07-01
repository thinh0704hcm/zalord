#!/usr/bin/env bash
# Generate 1 "golden trace" — POST /messages with a known traceparent → wait
# for outbox publish + consumer fanout → print the Jaeger URL.
#
# Usage:
#   scripts/demo-golden-trace.sh             # generate + print URL
#   scripts/demo-golden-trace.sh -o          # also open the URL in default browser

set -euo pipefail
cd "$(dirname "$0")/.."

TRACE_ID=$(openssl rand -hex 16)
SPAN_ID=$(openssl rand -hex 8)

echo "════════════════════════════════════════════════════════"
echo " GOLDEN TRACE DEMO"
echo " trace_id = $TRACE_ID"
echo "════════════════════════════════════════════════════════"
echo

echo "→ Login Alice + Bob..."
ATOK=$(curl -fsS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"0900111001","password":"secret123"}' | jq -r '.data.accessToken')
A_UID=$(curl -fsS http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $ATOK" | jq -r '.userId')

BTOK=$(curl -fsS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"0900111002","password":"secret123"}' | jq -r '.data.accessToken')
B_UID=$(curl -fsS http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $BTOK" | jq -r '.userId')

CONV=$(curl -fsS -X POST http://localhost:8083/api/v1/conversations \
  -H "X-User-Id: $A_UID" -H 'Content-Type: application/json' \
  -d "{\"type\":\"DIRECT\",\"memberUserId\":\"$B_UID\"}" | jq -r '.data.id')
echo "  conv = $CONV"
echo

echo "→ POST /messages with traceparent..."
curl -fsS -X POST http://localhost:8080/api/v1/messages \
  -H "traceparent: 00-${TRACE_ID}-${SPAN_ID}-01" \
  -H "Authorization: Bearer $ATOK" \
  -H 'Content-Type: application/json' \
  -d "{\"conversationId\":\"$CONV\",\"content\":\"defense demo $(date +%s)\"}" \
  | jq -c '{id: .data.id, content: .data.content}'
echo

echo "→ Waiting 12s (outbox poll + collector flush)..."
sleep 12

echo
echo "════════════════════════════════════════════════════════"
SPAN_COUNT=$(curl -s "http://localhost:16686/api/traces/${TRACE_ID}" | jq '.data[0].spans | length')
SVCS=$(curl -s "http://localhost:16686/api/traces/${TRACE_ID}" \
  | jq -r '[.data[0].processes | to_entries[] | .value.serviceName] | unique | join(", ")')
echo " RESULT"
echo " span count : $SPAN_COUNT"
echo " services   : $SVCS"
echo "════════════════════════════════════════════════════════"
echo
URL="http://localhost:16686/trace/${TRACE_ID}"
echo "Open in browser:"
echo "  $URL"
echo
echo "DB proof (trace_context column populated):"
docker exec zalord-message-db psql -U pguser -d message-db -tA \
  -c "SELECT '  ' || id || '  →  ' || trace_context FROM outbox_events
      WHERE trace_context LIKE '00-${TRACE_ID}-%'
      ORDER BY created_at DESC LIMIT 1;"

if [[ "${1:-}" == "-o" ]]; then
  open "$URL" 2>/dev/null || xdg-open "$URL" 2>/dev/null || true
fi
