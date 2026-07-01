#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${KUBE_NAMESPACE:-zalord-staging}"
KUBE_CONTEXT="${KUBE_CONTEXT:-}"
KUBECTL=(kubectl)
[[ -n "$KUBE_CONTEXT" ]] && KUBECTL+=(--context "$KUBE_CONTEXT")

TOPIC="${TOPIC:-zalord-event-stream-bench}"
PARTITIONS="${PARTITIONS:-24}"
MESSAGES="${MESSAGES:-1000000}"
SIZE="${SIZE:-512}"
CONSUMER_GROUPS="${CONSUMER_GROUPS:-5}"
RABBIT_IMAGE="${RABBIT_IMAGE:-pivotalrabbitmq/perf-test:latest}"
OUT="${OUT:-scripts/event-stream-benchmark-results.json}"

now_ms() { date +%s%3N; }
json_escape() { python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'; }

kafka_topic_reset() {
  "${KUBECTL[@]}" -n "$NAMESPACE" exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic "$TOPIC" >/dev/null 2>&1 || true
  sleep 3
  "${KUBECTL[@]}" -n "$NAMESPACE" exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic "$TOPIC" --partitions "$PARTITIONS" --replication-factor 1 >/dev/null
}

kafka_produce() {
  "${KUBECTL[@]}" -n "$NAMESPACE" exec kafka-0 -- /opt/kafka/bin/kafka-producer-perf-test.sh \
    --topic "$TOPIC" \
    --num-records "$MESSAGES" \
    --record-size "$SIZE" \
    --throughput -1 \
    --producer-props bootstrap.servers=localhost:9092 acks=1 batch.size=65536 linger.ms=5 compression.type=lz4
}

kafka_consume_group() {
  local group="$1"
  "${KUBECTL[@]}" -n "$NAMESPACE" exec kafka-0 -- /opt/kafka/bin/kafka-consumer-perf-test.sh \
    --bootstrap-server localhost:9092 \
    --topic "$TOPIC" \
    --group "$group" \
    --messages "$MESSAGES" \
    --timeout 120000
}

rabbit_secret() {
  local key="$1"
  "${KUBECTL[@]}" -n "$NAMESPACE" get secret shared-secret -o "jsonpath={.data.$key}" | base64 -d
}

rabbit_prepare() {
  local user pass
  user="$(rabbit_secret RABBITMQ_USER)"
  pass="$(rabbit_secret RABBITMQ_PASSWORD)"
  "${KUBECTL[@]}" -n "$NAMESPACE" exec rabbitmq-0 -- rabbitmqadmin -u "$user" -p "$pass" delete exchange name=zalord.event.bench >/dev/null 2>&1 || true
  for i in $(seq 1 "$CONSUMER_GROUPS"); do
    "${KUBECTL[@]}" -n "$NAMESPACE" exec rabbitmq-0 -- rabbitmqadmin -u "$user" -p "$pass" delete queue name="zalord.event.bench.$i" >/dev/null 2>&1 || true
  done
}

rabbit_run_perf() {
  local user pass uri pod
  user="$(rabbit_secret RABBITMQ_USER)"
  pass="$(rabbit_secret RABBITMQ_PASSWORD)"
  uri="amqp://$user:$pass@rabbitmq:5672/%2f"
  pod="rabbit-event-bench-$(date +%s)"
  "${KUBECTL[@]}" -n "$NAMESPACE" run "$pod" --restart=Never --image="$RABBIT_IMAGE" --command -- \
    java -jar /perf_test/perf-test.jar \
    --uri "$uri" \
    --exchange zalord.event.bench \
    --type fanout \
    --queue-pattern 'zalord.event.bench.%d' \
    --queue-pattern-from 1 \
    --queue-pattern-to "$CONSUMER_GROUPS" \
    --producers 1 \
    --consumers "$CONSUMER_GROUPS" \
    --size "$SIZE" \
    --pmessages "$MESSAGES" \
    --auto-delete false >/dev/null
  "${KUBECTL[@]}" -n "$NAMESPACE" wait --for=condition=Ready "pod/$pod" --timeout=180s >/dev/null
  "${KUBECTL[@]}" -n "$NAMESPACE" logs -f "$pod"
  "${KUBECTL[@]}" -n "$NAMESPACE" delete pod "$pod" --ignore-not-found >/dev/null
}

sample_usage() {
  for pod in kafka-0 rabbitmq-0; do
    "${KUBECTL[@]}" -n "$NAMESPACE" top pod "$pod" 2>/dev/null || true
  done
  "${KUBECTL[@]}" top node 2>/dev/null || true
}

usage_json() {
  for pod in kafka-0 rabbitmq-0; do
    "${KUBECTL[@]}" -n "$NAMESPACE" top pod "$pod" --no-headers 2>/dev/null || true
  done | python3 -c '
import json, re, sys
pods = {}
for line in sys.stdin:
    name, cpu, mem, *_ = line.split()
    cpu_m = int(cpu[:-1]) if cpu.endswith("m") else int(float(cpu) * 1000)
    n = float(re.match(r"[0-9.]+", mem).group())
    mem_mi = n * (1024 if "Gi" in mem else 1 / 1024 if "Ki" in mem else 1)
    pods[name] = {"cpu_millicores": cpu_m, "memory_mib": round(mem_mi, 1)}
print(json.dumps(pods))'
}

usage_avg_file_json() {
  python3 - "$1" <<'PY'
import json, sys
rows=[]
with open(sys.argv[1]) as f:
    rows=[json.loads(line) for line in f if line.strip()]
out={}
for pod in {p for row in rows for p in row}:
    vals=[row[pod] for row in rows if pod in row]
    out[pod]={
      "avg_cpu_millicores": round(sum(v["cpu_millicores"] for v in vals)/len(vals), 1),
      "avg_memory_mib": round(sum(v["memory_mib"] for v in vals)/len(vals), 1),
      "samples": len(vals)
    }
print(json.dumps(out))
PY
}

usage_avg_json() {
  local file
  file="$(mktemp)"
  for _ in $(seq 1 "${1:-5}"); do usage_json >> "$file"; sleep 1; done
  usage_avg_file_json "$file"
  rm -f "$file"
}

start_usage_sampler() {
  local file="$1"
  : > "$file"
  while true; do usage_json >> "$file"; sleep 1; done &
  USAGE_SAMPLER_PID="$!"
}

stop_usage_sampler() {
  local pid="$1" file="$2"
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  usage_avg_file_json "$file"
}

mkdir -p "$(dirname "$OUT")"
echo "[]" > "$OUT"

baseline_usage="$(usage_avg_json 5)"

kafka_topic_reset
kafka_start="$(now_ms)"
kafka_usage_file="$(mktemp)"
start_usage_sampler "$kafka_usage_file"
kafka_usage_pid="$USAGE_SAMPLER_PID"
kafka_produce_out="$(kafka_produce 2>&1)"
kafka_produce_end="$(now_ms)"
kafka_consume_start="$(now_ms)"
kafka_consume_out=""
for i in $(seq 1 "$CONSUMER_GROUPS"); do
  kafka_consume_out+="$(kafka_consume_group "event-bench-$i" 2>&1)"$'\n'
done
kafka_end="$(now_ms)"
kafka_usage="$(stop_usage_sampler "$kafka_usage_pid" "$kafka_usage_file")"
rm -f "$kafka_usage_file"
usage_after_kafka="$(sample_usage | json_escape)"

rabbit_prepare
rabbit_start="$(now_ms)"
rabbit_usage_file="$(mktemp)"
start_usage_sampler "$rabbit_usage_file"
rabbit_usage_pid="$USAGE_SAMPLER_PID"
rabbit_out="$(rabbit_run_perf 2>&1)"
if grep -Eq 'Main thread caught exception|PRECONDITION_FAILED|Parsing failed' <<<"$rabbit_out"; then
  stop_usage_sampler "$rabbit_usage_pid" "$rabbit_usage_file" >/dev/null
  rm -f "$rabbit_usage_file"
  printf '%s\n' "$rabbit_out" >&2
  exit 1
fi
rabbit_end="$(now_ms)"
rabbit_usage="$(stop_usage_sampler "$rabbit_usage_pid" "$rabbit_usage_file")"
rm -f "$rabbit_usage_file"
usage_after_rabbit="$(sample_usage | json_escape)"

jq --argjson messages "$MESSAGES" \
   --argjson size "$SIZE" \
   --argjson groups "$CONSUMER_GROUPS" \
   --argjson partitions "$PARTITIONS" \
   --argjson kafka_produce_ms "$((kafka_produce_end - kafka_start))" \
   --argjson kafka_total_ms "$((kafka_end - kafka_start))" \
   --argjson rabbit_total_ms "$((rabbit_end - rabbit_start))" \
   --arg kafka_producer_raw "$kafka_produce_out" \
   --arg kafka_consumer_raw "$kafka_consume_out" \
   --arg rabbit_raw "$rabbit_out" \
   --argjson usage_after_kafka "$usage_after_kafka" \
   --argjson usage_after_rabbit "$usage_after_rabbit" \
   --argjson baseline_usage "$baseline_usage" \
   --argjson kafka_usage "$kafka_usage" \
   --argjson rabbit_usage "$rabbit_usage" \
   '. + [
     {
       backend: "kafka",
       scenario: "event-streaming-fanout-replay",
       messages: $messages,
       message_size_bytes: $size,
       partitions: $partitions,
       consumer_groups: $groups,
       publish_ms: $kafka_produce_ms,
       total_replay_ms: $kafka_total_ms,
       publish_msg_per_sec: (($messages * 1000 / $kafka_produce_ms) | floor),
       effective_group_deliveries: ($messages * $groups),
       effective_delivery_msg_per_sec: (($messages * $groups * 1000 / $kafka_total_ms) | floor),
       baseline_resource_usage: $baseline_usage,
       avg_resource_usage: $kafka_usage,
       producer_raw: $kafka_producer_raw,
       consumer_raw: $kafka_consumer_raw,
       resource_snapshot: $usage_after_kafka
     },
     {
       backend: "rabbitmq",
       scenario: "event-streaming-fanout-replay",
       messages: $messages,
       message_size_bytes: $size,
       consumer_groups: $groups,
       total_ms: $rabbit_total_ms,
       effective_group_deliveries: ($messages * $groups),
       effective_delivery_msg_per_sec: (($messages * $groups * 1000 / $rabbit_total_ms) | floor),
       baseline_resource_usage: $baseline_usage,
       avg_resource_usage: $rabbit_usage,
       raw: $rabbit_raw,
       resource_snapshot: $usage_after_rabbit
     }
   ]' "$OUT" > "$OUT.tmp"
mv "$OUT.tmp" "$OUT"
printf '%s\n' "$OUT"
