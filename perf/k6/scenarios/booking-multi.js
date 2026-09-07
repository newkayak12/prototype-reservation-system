import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// ---------------------------------------------------------------------------
// 다중 경합 지점 버스트 시나리오.
//
// 단일 슬롯 시나리오(booking.js)와 다른 점은 하나다: VU가 **서로 다른 슬롯에 분산**된다.
//
// ## 왜 이 축이 필요한가
//
// 단일 슬롯에서는 before가 모든 축에서 이겼다(docs/perf/single-slot-burst.md). 그럴 수밖에
// 없는 조건이었다 - 슬롯이 하나면 분산락 키도 하나라 **동시에 열린 트랜잭션이 항상 1개**다.
// before에게 가장 유리하고, after의 원자 연산/파티션 병렬성은 발휘될 여지가 없다.
//
// 경합 지점이 N개로 늘면 before는 동시 트랜잭션도 N개가 되어 커넥션 풀을 잠식한다. 락을 쥔
// 스레드가 커넥션을 못 얻으면 락 보유 시간이 늘고, 그게 뒤에 줄 선 사람의 대기로 번지는
// 되먹임에 걸린다. after는 요청 경로에 DB 트랜잭션이 없으므로 이 되먹임이 없어야 한다.
//
// 그 가설을 확인하는 것이 이 시나리오의 목적이다. VU는 고정하고 경합 지점 수만 올린다.
//
// 실행: VUS=3000 POINTS=50 OUT=results/x.json k6 run scenarios/booking-multi.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../lib/env.json'));
const users = new SharedArray('users', () => JSON.parse(open('../lib/users.json')));

const VUS = Number(__ENV.VUS || 100);
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || '180s';
const ALLOW_TOKEN_REUSE = __ENV.ALLOW_TOKEN_REUSE === '1';
// 폴링 간격은 종속 변수가 아니라 설계 파라미터다. 승격이 요청 경로에서 일어나므로 이 값이
// permit 회전 주기를 정하고, 서버의 admission-capacity와 짝으로 움직여야 한다.
// 자세한 근거는 booking.js의 같은 상수에 적어 두었다.
const POLL_INTERVAL_SECONDS = Number(__ENV.POLL_INTERVAL_SECONDS || 0.5);
// 런이 끝나지 않는 것을 막는 안전장치. 정상 상태에서는 queue_timeout이 0이어야 하고,
// 0이 아니면 예산이 짧은 게 아니라 어딘가 막혔다는 신호다.
const QUEUE_WAIT_BUDGET_MS = Number(__ENV.QUEUE_WAIT_BUDGET_MS || 60000);

// 대기열 단계를 건너뛴다 (before 아키텍처에는 대기열이 없다).
const SKIP_QUEUE = __ENV.SKIP_QUEUE === '1';

const cRequests = new Counter('booking_requests');
const cSuccess = new Counter('booking_success');
const cSoldOut = new Counter('booking_sold_out_409');
const cSemaphore = new Counter('booking_semaphore_blocked_423');
const cRateLimited = new Counter('booking_rate_limited_429');
const cRejected = new Counter('booking_rejected_400');
const cUnauthorized = new Counter('booking_unauthorized_401');
const cServerError = new Counter('booking_server_error_5xx');
const cTimeout = new Counter('booking_timeout');

const cQueueEnterFailed = new Counter('queue_enter_failed');
const cQueueTimeout = new Counter('queue_timeout');
const cQueueAdmitted = new Counter('queue_admitted');
const queueWait = new Trend('queue_wait_ms', true);

const timeToSuccess = new Trend('time_to_success_ms', true);
const timeToResolve = new Trend('time_to_resolve_ms', true);
const latency = new Trend('booking_latency_ms', true);

export const options = {
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '10m',
      gracefulStop: '0s',
    },
  },
  thresholds: {
    booking_server_error_5xx: ['count==0'],
  },
};

export function setup() {
  if (users.length < VUS && !ALLOW_TOKEN_REUSE) {
    throw new Error(
      `user pool(${users.length}) < VUS(${VUS}). POOL_SIZE=${VUS} 로 시딩하거나 ` +
        `ALLOW_TOKEN_REUSE=1 로 강제 실행.`,
    );
  }
  if (!env.slots || env.slots.length === 0) {
    throw new Error('env.json에 slots가 없다. seed-multi.sh로 시딩했는지 확인.');
  }

  const needed = Math.min(VUS, users.length);
  const tokens = [];
  const batchSize = 50;
  for (let i = 0; i < needed; i += batchSize) {
    const chunk = users.slice(i, Math.min(i + batchSize, needed));
    const responses = http.batch(
      chunk.map((u) => ({
        method: 'PUT',
        url: `${env.baseUrl}/api/v1/user/sign-in`,
        body: JSON.stringify({ loginId: u.loginId, password: u.password }),
        params: { headers: { 'Content-Type': 'application/json' } },
      })),
    );
    for (const res of responses) {
      if (res.status === 200) tokens.push(JSON.parse(res.body).accessToken);
    }
  }

  if (tokens.length < needed) {
    throw new Error(`sign-in ${tokens.length}/${needed} 성공. 시딩 확인 필요.`);
  }

  return { tokens, slots: env.slots, seatCount: env.seatCount };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];

  // VU를 슬롯에 라운드로빈으로 배정한다. 무작위로 뽑으면 회차마다 슬롯별 인원이 흔들려
  // "경합 지점 수"라는 축이 흐려진다 - 균등 분배여야 지점당 부하가 VUS/POINTS로 고정된다.
  const slot = data.slots[(__VU - 1) % data.slots.length];

  const headers = { 'Content-Type': 'application/json', Authorization: token };
  const slotBody = JSON.stringify({ date: slot.date, startTime: slot.startTime });
  const bookingUrl = `${env.baseUrl}/api/v1/time-table/booking/${slot.restaurantId}`;
  const t0 = exec.scenario.startTime;

  cRequests.add(1);

  if (!SKIP_QUEUE) {
    const queueUrl = `${bookingUrl}/queue`;
    const enterRes = http.post(queueUrl, slotBody, {
      headers,
      timeout: REQ_TIMEOUT,
      tags: { name: 'queue_enter' },
    });

    if (enterRes.status !== 201) {
      cQueueEnterFailed.add(1);
      if (enterRes.status >= 500) cServerError.add(1);
      else if (enterRes.status === 0) cTimeout.add(1);
      timeToResolve.add(Date.now() - t0);
      return;
    }

    const entered = JSON.parse(enterRes.body);
    const waitStart = Date.now();
    let admitted = entered.position === 0;

    while (!admitted && Date.now() - waitStart < QUEUE_WAIT_BUDGET_MS) {
      sleep(POLL_INTERVAL_SECONDS);
      const pollRes = http.get(
        `${queueUrl}/${entered.ticketId}?date=${slot.date}&startTime=${slot.startTime}`,
        { headers, timeout: REQ_TIMEOUT, tags: { name: 'queue_poll' } },
      );
      if (pollRes.status === 200 && JSON.parse(pollRes.body).status === 'ADMITTED') {
        admitted = true;
      }
    }

    if (!admitted) {
      cQueueTimeout.add(1);
      timeToResolve.add(Date.now() - t0);
      return;
    }

    queueWait.add(Date.now() - waitStart);
    cQueueAdmitted.add(1);
  }

  const res = http.post(bookingUrl, slotBody, {
    headers,
    timeout: REQ_TIMEOUT,
    tags: { name: 'booking' },
  });

  const elapsed = Date.now() - t0;
  timeToResolve.add(elapsed);

  if (res.status === 201 || res.status === 200) {
    cSuccess.add(1);
    timeToSuccess.add(elapsed);
    latency.add(res.timings.duration);
  } else if (res.status === 409) {
    cSoldOut.add(1);
    latency.add(res.timings.duration);
  } else if (res.status === 423) {
    cSemaphore.add(1);
    latency.add(res.timings.duration);
  } else if (res.status === 429) {
    cRateLimited.add(1);
    latency.add(res.timings.duration);
  } else if (res.status === 400) {
    cRejected.add(1);
    latency.add(res.timings.duration);
  } else if (res.status === 401 || res.status === 403) {
    cUnauthorized.add(1);
  } else if (res.status === 0) {
    cTimeout.add(1);
  } else if (res.status >= 500) {
    cServerError.add(1);
    latency.add(res.timings.duration);
  }

  check(res, { 'no 5xx': (r) => r.status < 500 || r.status === 0 });
}

function count(data, name) {
  const m = data.metrics[name];
  return m && m.values ? m.values.count || 0 : 0;
}

function trend(data, name) {
  const m = data.metrics[name];
  return m && m.values ? m.values : {};
}

export function handleSummary(data) {
  const requests = count(data, 'booking_requests');
  const success = count(data, 'booking_success');
  const selloutMs = trend(data, 'time_to_success_ms').max || 0;
  const resolveMs = trend(data, 'time_to_resolve_ms').max || 0;
  const lat = trend(data, 'booking_latency_ms');
  const wait = trend(data, 'queue_wait_ms');

  const points = env.contentionPoints || 1;
  const totalSeats = env.totalSeats || env.seatCount;

  const report = {
    vus: VUS,
    // 이 시나리오의 축. 지점당 부하 = VUS / points.
    contentionPoints: points,
    seatCount: env.seatCount,
    totalSeats,
    vusPerPoint: VUS / points,
    requests,
    valid: requests > 0,
    skipQueue: SKIP_QUEUE,
    outcome: {
      success,
      soldOut409: count(data, 'booking_sold_out_409'),
      semaphoreBlocked423: count(data, 'booking_semaphore_blocked_423'),
      rateLimited429: count(data, 'booking_rate_limited_429'),
      rejected400: count(data, 'booking_rejected_400'),
      unauthorized401: count(data, 'booking_unauthorized_401'),
      serverError5xx: count(data, 'booking_server_error_5xx'),
      timeout: count(data, 'booking_timeout'),
      queueTimeout: count(data, 'queue_timeout'),
      queueEnterFailed: count(data, 'queue_enter_failed'),
    },
    queue: {
      admitted: count(data, 'queue_admitted'),
      waitMs: { p50: wait['p(50)'] || 0, p95: wait['p(95)'] || 0, max: wait.max || 0 },
      budgetMs: QUEUE_WAIT_BUDGET_MS,
    },
    selloutSeconds: selloutMs / 1000,
    tps: selloutMs > 0 ? success / (selloutMs / 1000) : 0,
    resolveSeconds: resolveMs / 1000,
    throughput: resolveMs > 0 ? requests / (resolveMs / 1000) : 0,
    latencyMs: {
      p50: lat['p(50)'] || 0,
      p95: lat['p(95)'] || 0,
      p99: lat['p(99)'] || 0,
      max: lat.max || 0,
      avg: lat.avg || 0,
    },
  };

  const out = {
    stdout:
      `\n  VU ${VUS} | 경합지점 ${points} (지점당 ${(VUS / points).toFixed(0)}명) | ` +
      `성공 ${success}/${totalSeats}석 | 매진 ${report.selloutSeconds.toFixed(2)}s | ` +
      `해소 ${report.resolveSeconds.toFixed(2)}s (${report.throughput.toFixed(1)} req/s) | ` +
      `p95 ${report.latencyMs.p95.toFixed(0)}ms | 대기포기 ${report.outcome.queueTimeout} | ` +
      `5xx ${report.outcome.serverError5xx}\n`,
  };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
