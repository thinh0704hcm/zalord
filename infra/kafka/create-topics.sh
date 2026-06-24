#!/usr/bin/env bash
# Idempotent topic creation. Run as a one-shot init container after Kafka
# is healthy. Each --create call uses --if-not-exists so re-runs are safe.
#
# Convention: one topic per event type (mirrors the RabbitMQ routing keys).
# This keeps the wire schema directly comparable between the two backends.

set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"

create() {
    local topic="$1"
    local partitions="${2:-1}"
    /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$BOOTSTRAP" \
        --create \
        --if-not-exists \
        --topic "$topic" \
        --partitions "$partitions" \
        --replication-factor 1
    echo "[kafka-init] ✓ $topic ($partitions partitions)"
}

echo "[kafka-init] waiting for broker at $BOOTSTRAP ..."
until /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "$BOOTSTRAP" >/dev/null 2>&1; do
    sleep 2
done
echo "[kafka-init] broker up, creating topics ..."

# Topics mirror RabbitMQ routing keys so the EventBus interface can swap
# backends without changing event names.
create "user.created"              1
create "message.created"           3
create "message.read"              1
create "group.created"             1
create "group.member.added"        1
create "group.member.removed"      1
create "group.updated"             1

echo "[kafka-init] all topics ready"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list
