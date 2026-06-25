// k6 load test — POST /api/v1/messages directly to message-service (bypass Kong rate-limit).
//
// Drives sustained RPS against the message pipeline so we can compare RabbitMQ vs Kafka
// under realistic steady-state load (not a burst). Inputs come from env vars set by the
// wrapper script (scripts/run-loadtest.sh):
//   UID       — caller user id (X-User-Id header, Kong would normally inject this)
//   CONV      — conversation id (DIRECT conv between Alice & Bob)
//   BACKEND   — "rabbitmq" or "kafka" — only used as a label/tag in output
//   PROFILE   — "smoke" | "sustained" | "ramp"  (default: sustained)
//
// Run via: scripts/run-loadtest.sh   (handles bootstrap + backend switch)
// Direct:  k6 run -e UID=... -e CONV=... -e BACKEND=rabbitmq scripts/loadtest.js

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const UID     = __ENV.UID     || (() => { throw 'UID env required'; })();
const CONV    = __ENV.CONV    || (() => { throw 'CONV env required'; })();
const BACKEND = __ENV.BACKEND || 'unknown';
const PROFILE = __ENV.PROFILE || 'sustained';
const TARGET  = __ENV.TARGET  || 'http://localhost:8083/api/v1/messages';

const profiles = {
  // 30s @ 50 RPS — quick sanity check
  smoke: {
    executor: 'constant-arrival-rate',
    rate: 50, timeUnit: '1s', duration: '30s',
    preAllocatedVUs: 20, maxVUs: 50,
  },
  // 5 min @ 500 RPS — steady state, this is the headline number
  sustained: {
    executor: 'constant-arrival-rate',
    rate: 500, timeUnit: '1s', duration: '5m',
    preAllocatedVUs: 100, maxVUs: 500,
  },
  // ramp 0 -> 2000 RPS over 7 min — find the breaking point
  ramp: {
    executor: 'ramping-arrival-rate',
    startRate: 50, timeUnit: '1s',
    preAllocatedVUs: 100, maxVUs: 1000,
    stages: [
      { duration: '1m', target: 200  },
      { duration: '2m', target: 500  },
      { duration: '2m', target: 1000 },
      { duration: '2m', target: 2000 },
    ],
  },
};

export const options = {
  scenarios: { load: profiles[PROFILE] },
  thresholds: {
    http_req_failed:   ['rate<0.01'],            // <1% errors
    http_req_duration: ['p(95)<200', 'p(99)<500'],
  },
  tags: { backend: BACKEND, profile: PROFILE },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

const sendLatency = new Trend('send_latency_ms', true);
const sendErrors  = new Counter('send_errors');

export default function () {
  const payload = JSON.stringify({
    conversationId: CONV,
    content: `k6-${BACKEND}-${__VU}-${__ITER}`,
  });
  const params = {
    headers: { 'Content-Type': 'application/json', 'X-User-Id': UID },
    tags:    { backend: BACKEND },
  };
  const res = http.post(TARGET, payload, params);
  sendLatency.add(res.timings.duration);
  const ok = check(res, { '2xx': r => r.status >= 200 && r.status < 300 });
  if (!ok) sendErrors.add(1);
}

export function handleSummary(data) {
  // Per-run JSON written next to the script (k6 cwd = scripts/ — wrapper mounts it).
  const out = `loadtest-${BACKEND}-${PROFILE}.json`;
  return { [out]: JSON.stringify(data, null, 2), stdout: textSummary(data) };
}

function textSummary(data) {
  const m  = data.metrics;
  const d  = m.http_req_duration.values;
  const f  = m.http_req_failed.values;
  const it = m.iterations.values;
  return `
═══════════════════════════════════════════════════════════
 ${BACKEND.toUpperCase()} / ${PROFILE}
───────────────────────────────────────────────────────────
 requests       : ${it.count}
 throughput     : ${it.rate.toFixed(1)} req/s
 error rate     : ${(f.rate * 100).toFixed(2)}%
 latency p50    : ${d.med.toFixed(1)} ms
 latency p95    : ${d['p(95)'].toFixed(1)} ms
 latency p99    : ${d['p(99)'].toFixed(1)} ms
 latency max    : ${d.max.toFixed(1)} ms
═══════════════════════════════════════════════════════════
`;
}
