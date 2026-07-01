#!/usr/bin/env bash
# Generate sustained message traffic to trigger chat/message/notification HPAs.
# Defaults target staging via kubectl port-forward.

set -euo pipefail
cd "$(dirname "$0")/.."

KUBE_NAMESPACE="${KUBE_NAMESPACE:-zalord-staging}"
KONG_NAMESPACE="${KONG_NAMESPACE:-kong}"
KUBE_CONTEXT="${KUBE_CONTEXT:-}"
OVERLAY="${OVERLAY:-staging}"
RATE="${RATE:-800}"
DURATION="${DURATION:-240}"
BACKEND="${BACKEND:-rabbitmq}"
OUT="${OUT:-scripts/hpa-trigger-results.json}"
APPLY_HPA="${APPLY_HPA:-1}"
KUBECTL=(kubectl)
[[ -n "$KUBE_CONTEXT" ]] && KUBECTL+=(--context "$KUBE_CONTEXT")
PF_PIDS=()
HPA_LOG="${HPA_LOG:-/tmp/zalord-hpa-watch.log}"

cleanup() {
  for pid in "${PF_PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT

need() { command -v "$1" >/dev/null || { echo "✗ $1 required" >&2; exit 1; }; }
need kubectl
need curl
need python3
python3 -c 'import websockets' 2>/dev/null || { echo "✗ pip3 install websockets" >&2; exit 1; }

wait_healthy() {
  local url="$1" name="$2" deadline=$(( $(date +%s) + 180 ))
  while (( $(date +%s) < deadline )); do
    curl -fsS --max-time 2 "$url" >/dev/null 2>&1 && return 0
    sleep 2
  done
  echo "✗ $name never became healthy: $url" >&2
  exit 1
}

if [[ "$APPLY_HPA" == "1" && -f "deploy/k8s/overlays/$OVERLAY/messaging-hpa.yaml" ]]; then
  "${KUBECTL[@]}" apply -f "deploy/k8s/overlays/$OVERLAY/messaging-hpa.yaml"
fi

"${KUBECTL[@]}" -n "$KONG_NAMESPACE" port-forward svc/kong-kong-proxy 18080:80 --address 127.0.0.1 >/tmp/zalord-hpa-kong.log 2>&1 & PF_PIDS+=("$!")
"${KUBECTL[@]}" -n "$KUBE_NAMESPACE" port-forward svc/message-service 18083:8083 --address 127.0.0.1 >/tmp/zalord-hpa-message.log 2>&1 & PF_PIDS+=("$!")
"${KUBECTL[@]}" -n "$KUBE_NAMESPACE" port-forward svc/chat-service 18084:8084 --address 127.0.0.1 >/tmp/zalord-hpa-chat.log 2>&1 & PF_PIDS+=("$!")
sleep 5

export KUBE_NAMESPACE
export BENCH_KONG=http://127.0.0.1:18080/api/v1
export BENCH_MSG_DIRECT=http://127.0.0.1:18083/api/v1/messages
export BENCH_CONV_DIRECT=http://127.0.0.1:18083/api/v1/conversations
export BENCH_WS_DIRECT=ws://127.0.0.1:18084/ws/chat
export BENCH_MSG_HEALTH=http://127.0.0.1:18083/actuator/health
export BENCH_CHAT_HEALTH=http://127.0.0.1:18084/health
export BENCH_SENDER_WORKERS="${BENCH_SENDER_WORKERS:-256}"

wait_healthy "$BENCH_MSG_HEALTH" message-service
wait_healthy "$BENCH_CHAT_HEALTH" chat-service

echo "[]" > "$OUT"
echo "▶ HPA load: rate=${RATE}/s duration=${DURATION}s backend-label=${BACKEND} namespace=${KUBE_NAMESPACE}"
echo "▶ HPA watch log: $HPA_LOG"
("${KUBECTL[@]}" -n "$KUBE_NAMESPACE" get hpa,pod -w > "$HPA_LOG" 2>&1) & PF_PIDS+=("$!")

python3 scripts/benchmark-e2e.py --backend "$BACKEND" --rate "$RATE" --duration "$DURATION" --out "$OUT"

echo
"${KUBECTL[@]}" -n "$KUBE_NAMESPACE" get hpa chat-service-hpa message-service-hpa notification-service-hpa
"${KUBECTL[@]}" -n "$KUBE_NAMESPACE" get deploy chat-service message-service notification-service
printf 'saved → %s\nwatch → %s\n' "$OUT" "$HPA_LOG"
