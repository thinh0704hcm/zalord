#!/usr/bin/env python3
"""
End-to-end broker benchmark — measures the path that actually exercises Kafka/RabbitMQ:

   Alice POST /messages  →  Postgres outbox  →  OutboxScheduler  →  BROKER  →
   chat-service fan-out  →  WebSocket push  →  Bob receives

This is what users experience. The previous k6 benchmark only timed Alice's POST
(which never touches the broker), so it could not distinguish RabbitMQ from Kafka.

Per scenario it reports:
  * delivery_p50/p95/p99  — end-to-end ms (POST send → Bob WS receive). THE metric.
  * post_p50/p95/p99      — sender-side latency (Postgres + media gRPC)
  * throughput            — delivered msg/s
  * delivery_rate         — % of sent messages that arrived (target 100%)
  * outbox_lag            — outbox.published_at - created_at, queried from DB
                            (isolates broker push latency from Postgres write)

Run via scripts/run-benchmark-rabbit.sh or scripts/run-benchmark-kafka.sh — those
wrappers handle the EVENT_BUS switch + service recreate. Direct invocation:

    EVENT_BUS=rabbitmq OUTBOX_POLL_MS=50 \\
        python3 scripts/benchmark-e2e.py --backend rabbitmq --rate 200 --duration 60
"""

import argparse
import asyncio
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid
from collections import defaultdict
from pathlib import Path

import websockets

KONG = "http://localhost:8080/api/v1"
MSG_DIRECT  = "http://localhost:8083/api/v1/messages"
CONV_DIRECT = "http://localhost:8083/api/v1/conversations"
# Bypass Kong for WS too — chat-service's Identity middleware reads X-User-Id
# directly (same trick we use for /messages). Avoids Kong's flaky WS upgrade.
WS_DIRECT   = "ws://localhost:8084/ws/chat"


# ── HTTP helpers ─────────────────────────────────────────────────────────────
def http(method, url, body=None, headers=None, timeout=10):
    h = {"Content-Type": "application/json"}
    if headers:
        h.update(headers)
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method, headers=h)
    with urllib.request.urlopen(r, timeout=timeout) as resp:
        return resp.status, json.loads(resp.read())


def login(phone):
    return http("POST", f"{KONG}/auth/login",
                {"phoneNumber": phone, "password": "secret123"})[1]["data"]["accessToken"]


def me(token):
    return http("GET", f"{KONG}/users/me", headers={"Authorization": f"Bearer {token}"})[1]["userId"]


# ── benchmark core ───────────────────────────────────────────────────────────
async def ws_listener(user_id, received):
    """Bob's WebSocket. Records {messageId: receive_timestamp} for every
    `message.created` frame. Connects direct to chat-service (bypass Kong)."""
    headers = {"X-User-Id": user_id}
    async with websockets.connect(WS_DIRECT, additional_headers=headers, max_size=2**20) as ws:
        async for raw in ws:
            try:
                frame = json.loads(raw)
                if frame.get("type") != "message.created":
                    continue
                mid = frame["data"]["messageId"]
                received[mid] = time.perf_counter()
            except (json.JSONDecodeError, KeyError):
                pass


def sender_loop(caller_uid, conv_id, rate, duration, post_latencies, sent):
    """Alice's POST loop, runs in a thread. Fires `rate` req/s for `duration` s.
    Records send timestamp keyed by returned messageId."""
    interval = 1.0 / rate
    deadline = time.perf_counter() + duration
    headers = {"X-User-Id": caller_uid, "Content-Type": "application/json"}
    next_send = time.perf_counter()
    i = 0
    while time.perf_counter() < deadline:
        now = time.perf_counter()
        if now < next_send:
            time.sleep(next_send - now)
        body = json.dumps({"conversationId": conv_id, "content": f"bench-{i}"}).encode()
        t0 = time.perf_counter()
        try:
            req = urllib.request.Request(MSG_DIRECT, data=body, method="POST", headers=headers)
            with urllib.request.urlopen(req, timeout=5) as resp:
                doc = json.loads(resp.read())
                mid = doc["data"]["id"]
                sent[mid] = t0
                post_latencies.append(time.perf_counter() - t0)
        except Exception as e:
            post_latencies.append(time.perf_counter() - t0)
            print(f"  POST error: {e}", file=sys.stderr)
        next_send += interval
        i += 1


def query_outbox_lag(backend):
    """Pull outbox.published_at - created_at from message-db. Isolates the
    broker-publish step from sender-side latency."""
    import subprocess
    sql = """
      SELECT EXTRACT(EPOCH FROM (published_at - created_at)) * 1000
      FROM outbox_events
      WHERE published_at IS NOT NULL
        AND created_at > now() - interval '5 minutes'
      ORDER BY created_at DESC LIMIT 5000;
    """
    try:
        out = subprocess.check_output(
            ["docker", "exec", "zalord-message-db", "psql", "-U", "pguser",
             "-d", "message-db", "-tA", "-c", sql],
            stderr=subprocess.DEVNULL, timeout=10,
        ).decode().strip().splitlines()
        lags = [float(x) for x in out if x.strip()]
        return lags
    except Exception as e:
        print(f"  outbox lag query failed: {e}", file=sys.stderr)
        return []


def pctl(xs, p):
    if not xs:
        return 0.0
    xs = sorted(xs)
    k = max(0, min(len(xs) - 1, int(round(p * (len(xs) - 1)))))
    return xs[k]


async def run_scenario(backend, rate, duration, caller_uid, bob_uid, conv_id):
    print(f"  ▶ backend={backend} rate={rate}/s duration={duration}s")

    sent = {}            # {messageId: send_ts}
    received = {}        # {messageId: recv_ts}
    post_latencies = []  # seconds

    # Bob's WS task — runs for the whole scenario plus grace period
    listener = asyncio.create_task(ws_listener(bob_uid, received))
    await asyncio.sleep(0.5)  # let WS subscribe before sender starts

    # Alice sender in a thread (urllib is blocking)
    loop = asyncio.get_running_loop()
    sender = loop.run_in_executor(
        None, sender_loop, caller_uid, conv_id, rate, duration, post_latencies, sent,
    )
    await sender

    # Grace period — wait for in-flight messages to finish their broker → WS trip
    print(f"    sender done, sent={len(sent)}, waiting 5s for delivery to drain...")
    await asyncio.sleep(5)
    listener.cancel()
    try:
        await listener
    except (asyncio.CancelledError, websockets.exceptions.ConnectionClosed):
        pass

    # Compute deltas
    deltas_ms = []
    delivered = 0
    for mid, t_send in sent.items():
        if mid in received:
            deltas_ms.append((received[mid] - t_send) * 1000)
            delivered += 1

    outbox_lag_ms = query_outbox_lag(backend)
    post_ms = [x * 1000 for x in post_latencies]

    result = {
        "backend":       backend,
        "rate":          rate,
        "duration_s":    duration,
        "sent":          len(sent),
        "delivered":     delivered,
        "delivery_rate": round(100 * delivered / max(1, len(sent)), 2),
        "throughput":    round(delivered / duration, 1),
        "delivery_p50":  round(pctl(deltas_ms, 0.50), 1),
        "delivery_p95":  round(pctl(deltas_ms, 0.95), 1),
        "delivery_p99":  round(pctl(deltas_ms, 0.99), 1),
        "delivery_max":  round(max(deltas_ms) if deltas_ms else 0, 1),
        "post_p50":      round(pctl(post_ms, 0.50), 1),
        "post_p95":      round(pctl(post_ms, 0.95), 1),
        "post_p99":      round(pctl(post_ms, 0.99), 1),
        "outbox_lag_p50": round(pctl(outbox_lag_ms, 0.50), 1),
        "outbox_lag_p95": round(pctl(outbox_lag_ms, 0.95), 1),
        "outbox_lag_p99": round(pctl(outbox_lag_ms, 0.99), 1),
        "outbox_samples": len(outbox_lag_ms),
    }
    print(f"    sent={result['sent']} delivered={result['delivered']} "
          f"({result['delivery_rate']}%) throughput={result['throughput']}/s")
    print(f"    end-to-end  p50={result['delivery_p50']}ms  "
          f"p95={result['delivery_p95']}ms  p99={result['delivery_p99']}ms")
    print(f"    outbox lag  p50={result['outbox_lag_p50']}ms  "
          f"p95={result['outbox_lag_p95']}ms  p99={result['outbox_lag_p99']}ms  "
          f"(n={result['outbox_samples']})")
    print(f"    POST        p50={result['post_p50']}ms  "
          f"p95={result['post_p95']}ms  p99={result['post_p99']}ms")
    return result


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--backend", required=True, choices=["rabbitmq", "kafka"],
                    help="label for the result; switching is handled by the wrapper")
    ap.add_argument("--rate", type=int, default=200, help="msg/s sent by Alice")
    ap.add_argument("--duration", type=int, default=60, help="seconds to send for")
    ap.add_argument("--out", default="scripts/benchmark-results.json",
                    help="append result here")
    args = ap.parse_args()

    print("Bootstrapping (login Alice+Bob + DIRECT conv)...")
    atok = login("0900111001")
    btok = login("0900111002")
    a_uid = me(atok)
    b_uid = me(btok)
    conv = http("POST", CONV_DIRECT,
                {"type": "DIRECT", "memberUserId": b_uid},
                {"X-User-Id": a_uid})[1]["data"]["id"]
    print(f"  caller(Alice)={a_uid}\n  receiver(Bob)={b_uid}\n  conv={conv}\n")

    result = await run_scenario(args.backend, args.rate, args.duration, a_uid, b_uid, conv)

    # Append to results file
    out = Path(args.out)
    history = []
    if out.exists():
        try:
            history = json.loads(out.read_text())
        except json.JSONDecodeError:
            history = []
    history.append(result)
    out.write_text(json.dumps(history, indent=2))
    print(f"\n  saved → {out}")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nAborted by user.", file=sys.stderr)
        sys.exit(1)
