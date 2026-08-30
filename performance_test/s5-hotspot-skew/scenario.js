import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------------------------------------------------------------------------
// S5 — Hotspot Skew.
//
// 질문: 총 트래픽을 고정한 채 경합점(= 분산락 키) 수만 바꾸면 처리량이 어떻게 변하는가.
//
// 이게 B2B와 B2C를 가르는 축이다.
//   경합점 100개  = B2B. 예약이 여러 지점에 골고루 퍼진다. 락은 서로 독립.
//   경합점 1개    = B2C. 굿즈 하나, 콘서트 회차 하나에 전원이 몰린다.
//
// before의 락은 restaurantId 단위라 경합점이 늘면 병렬로 처리될 수 있다.
// 따라서 처리량은 경합점 수에 비례해 올라가다가, 어느 지점에서 다른 자원
// (워커 200, DB 커넥션 10)에 막혀 평평해진다. 그 평평해지는 지점이
// "이 아키텍처가 감당 가능한 분산도의 하한"이다.
//
// 가설: 처리량 ≈ min(K × 단일키상한, 공유자원상한)
//   → K=1에서 심하게 낮고 K가 커질수록 개선. 개선이 멈추는 K가 있으면 그게 천장.
//   → K를 늘려도 처리량이 그대로면 병목이 락이 아니라는 뜻이므로 가설 반증.
//
// 총 도착률은 K와 무관하게 고정한다. 그래야 "부하는 같은데 분산도만 다르다"가 된다.
// 각 요청은 K개 경합점 중 하나를 균등하게 고른다.
//
// 실행: k6 run -e RATE=1500 -e POINTS=5 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const RATE = Number(__ENV.RATE || 1500);
const DURATION = Number(__ENV.DURATION || 30);
const MAX_VUS = Number(__ENV.MAX_VUS || 20000);
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || '30s';

// S2와 같은 이유로 1초 버킷을 단다: 매진 전(할당)과 매진 후(거절)는 다른 코드 경로라
// 하나의 goodput으로 뭉치면 배수 구간이 섞여 할당 속도가 실제보다 낮게 나온다.
// K 레벨마다 매진 시점이 다르므로, 뭉쳐 놓으면 K의 효과와 매진 시점의 효과가 구분되지 않는다.
const NBUCKETS = Math.min(300, Number(__ENV.DURATION || 30) + 90);

const cRequests = new Counter('booking_requests');
const cSuccess = new Counter('booking_success');
const cSoldOut = new Counter('booking_sold_out');
const cRejected = new Counter('booking_rejected_4xx');
const cServerError = new Counter('booking_server_error_5xx');
const cTimeout = new Counter('booking_timeout');

const latency = new Trend('booking_latency_ms', true);
const latencySuccess = new Trend('booking_latency_success_ms', true);

const thresholds = {};
for (let b = 0; b < NBUCKETS; b++) {
  // 판정이 아니라 서브메트릭 집계가 목적이므로 절대 실패하지 않는 조건을 건다.
  thresholds[`booking_requests{sec:${b}}`] = ['count>=0'];
  thresholds[`booking_success{sec:${b}}`] = ['count>=0'];
}

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds,
  scenarios: {
    skew: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION}s`,
      preAllocatedVUs: Math.min(MAX_VUS, Math.max(500, RATE * 2)),
      maxVUs: MAX_VUS,
      gracefulStop: '60s',
    },
  },
};

export function setup() {
  if (!env.points || env.points.length === 0) throw new Error('env.json에 경합점이 없다');
  return { t0: Date.now(), points: env.points, date: env.date, startTime: env.startTime };
}

export default function (data) {
  // 균등 분배. 여기서 편향을 주면 "경합점이 K개"라는 전제가 깨진다.
  // 실제 트래픽은 지프 분포에 가깝지만, 그 효과까지 섞으면 K의 순효과를 못 본다.
  const rid = data.points[__ITER % data.points.length];
  const token = tokens[__ITER % tokens.length];

  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${rid}`,
    JSON.stringify({ date: data.date, startTime: data.startTime }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: token },
      timeout: REQ_TIMEOUT,
      tags: { name: 'booking' },
    },
  );

  const sec = String(
    Math.min(NBUCKETS - 1, Math.max(0, Math.floor((Date.now() - data.t0) / 1000))),
  );

  cRequests.add(1, { sec });
  if (res.status === 200 || res.status === 201) {
    cSuccess.add(1, { sec });
    latencySuccess.add(res.timings.duration);
  } else if (res.status === 409) {
    cSoldOut.add(1);
  } else if (res.status === 0) {
    cTimeout.add(1);
  } else if (res.status >= 500) {
    cServerError.add(1);
  } else {
    cRejected.add(1);
  }
  if (res.status !== 0) latency.add(res.timings.duration);
}

const trd = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values : {});
const cnt = (d, n) => trd(d, n).count || 0;

export function handleSummary(data) {
  const requests = cnt(data, 'booking_requests');
  const success = cnt(data, 'booking_success');
  const dropped = cnt(data, 'dropped_iterations');
  const lat = trd(data, 'booking_latency_ms');
  const latS = trd(data, 'booking_latency_success_ms');
  const elapsed =
    (data.state && data.state.testRunDurationMs ? data.state.testRunDurationMs : DURATION * 1000) / 1000;

  const timeout = cnt(data, 'booking_timeout');
  const err5xx = cnt(data, 'booking_server_error_5xx');
  const points = (data.setup_data && data.setup_data.points ? data.setup_data.points.length : 0);

  const series = [];
  for (let b = 0; b < NBUCKETS; b++) {
    series.push({
      sec: b,
      requests: cnt(data, `booking_requests{sec:${b}}`),
      success: cnt(data, `booking_success{sec:${b}}`),
    });
  }
  while (series.length && series[series.length - 1].requests === 0) series.pop();

  // 매진 시점 = 마지막으로 성공이 나온 초. 그 뒤는 재고가 없어 빠른 실패 경로다.
  let lastSuccessSec = -1;
  for (const s of series) if (s.success > 0) lastSuccessSec = s.sec;
  const soldOut = lastSuccessSec >= 0 && lastSuccessSec < series.length - 1;
  const pre = series.filter((s) => s.sec <= lastSuccessSec);
  const preSec = pre.length;
  const allocationRps = preSec > 0 ? pre.reduce((x, s) => x + s.success, 0) / preSec : 0;

  const report = {
    points,
    targetRps: RATE,
    durationSeconds: DURATION,
    elapsedSeconds: elapsed,
    valid: requests > 0,
    requests,
    droppedIterations: dropped,
    // 발생기가 VU를 못 구해 드롭한 만큼은 서버에 도달조차 못 했다. 이걸 빼고 보면
    // 실제 제공 부하가 목표보다 낮은데도 "서버가 못 버텼다"로 오독하게 된다.
    offeredRps: (requests + dropped) / elapsed,
    generatorLimited: dropped > requests * 0.1,
    achievedRps: requests / elapsed,
    goodputRps: success / elapsed,
    attainment: RATE > 0 ? requests / elapsed / RATE : 0,

    // 매진 전 구간의 성공/초. K 레벨마다 매진 시점이 다르므로, 전체 경과로 나눈
    // goodputRps를 K끼리 비교하면 "매진 후 배수 구간이 긴 쪽"이 불리해진다.
    // K를 비교할 때 봐야 하는 건 이 값이다.
    soldOut,
    selloutSeconds: lastSuccessSec >= 0 ? lastSuccessSec + 1 : null,
    allocationRps,
    // 경합점당 할당 처리량. K에 비례해 총량이 늘면 락이 병목,
    // 점당 값이 K에 반비례해 떨어지면 공유자원이 병목이다.
    allocationPerPoint: points > 0 ? allocationRps / points : 0,
    goodputPerPoint: points > 0 ? success / elapsed / points : 0,
    series,
    outcome: {
      success,
      soldOut: cnt(data, 'booking_sold_out'),
      rejected4xx: cnt(data, 'booking_rejected_4xx'),
      serverError5xx: err5xx,
      timeout,
    },
    failureRate: requests > 0 ? (timeout + err5xx) / requests : 0,
    latencyMs: {
      avg: lat.avg || 0,
      p50: lat['p(50)'] || 0,
      p95: lat['p(95)'] || 0,
      p99: lat['p(99)'] || 0,
      max: lat.max || 0,
    },
    latencySuccessMs: { p50: latS['p(50)'] || 0, p95: latS['p(95)'] || 0 },
  };

  const out = {
    stdout:
      `\n  경합점 ${points} | 목표 ${RATE} req/s | 제공 ${report.offeredRps.toFixed(0)} | ` +
      `달성 ${report.achievedRps.toFixed(0)} req/s\n` +
      `  할당 ${allocationRps.toFixed(0)} req/s (점당 ${report.allocationPerPoint.toFixed(1)})` +
      (soldOut ? ` | ${report.selloutSeconds}s 만에 매진` : ' | 매진 안 됨') + '\n' +
      `  p95 ${report.latencyMs.p95.toFixed(0)}ms | timeout ${timeout} | 5xx ${err5xx} | ` +
      `dropped ${dropped}${report.generatorLimited ? ' (발생기 한계 — 제공 부하가 목표 미달)' : ''}\n`,
  };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
