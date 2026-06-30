#!/bin/bash
TRACE_ID=$(openssl rand -hex 16)
SPAN_ID=$(openssl rand -hex 8)
echo "════════════════════════════════════════════════════════════"
echo " STEP 1: Generate trace_id"
echo "════════════════════════════════════════════════════════════"
echo "   trace_id = $TRACE_ID"
echo
echo "════════════════════════════════════════════════════════════"
echo " STEP 2: Send request with traceparent header"
echo "════════════════════════════════════════════════════════════"
ATOK=$(curl -fsS -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"phoneNumber":"0900111001","password":"secret123"}' | jq -r '.data.accessToken')
echo "   → GET http://localhost:8080/api/v1/users/me  (qua Kong → user-service)"
curl -sS -H "traceparent: 00-${TRACE_ID}-${SPAN_ID}-01" \
  -H "Authorization: Bearer $ATOK" \
  http://localhost:8080/api/v1/users/me | jq -c '{userId, displayName}'
echo
echo "════════════════════════════════════════════════════════════"
echo " STEP 3: Wait 5s for OTel collector flush"
echo "════════════════════════════════════════════════════════════"
sleep 5
echo
echo "════════════════════════════════════════════════════════════"
echo " STEP 4: Pull trace from Jaeger"
echo "════════════════════════════════════════════════════════════"
curl -s "http://localhost:16686/api/traces/${TRACE_ID}" | jq -r '
  .data[0] |
  "Trace: " + .traceID,
  "Span count: " + (.spans | length | tostring),
  "Services: " + ([.processes | to_entries[] | .value.serviceName] | unique | join(", ")),
  "",
  "Span tree (sorted by start time):",
  (.spans | sort_by(.startTime)[] | "  [\(.duration/1000 | . * 100 | round / 100)ms]  \(.operationName)   <\(.processID)>")'
echo
echo "════════════════════════════════════════════════════════════"
echo " STEP 5: Open in browser"
echo "════════════════════════════════════════════════════════════"
echo "   http://localhost:16686/trace/${TRACE_ID}"
