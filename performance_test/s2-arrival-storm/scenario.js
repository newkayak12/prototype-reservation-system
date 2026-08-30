import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------------------------------------------------------------------------
// S2 — Arrival Storm.
//
// 질문: 도착률을 올릴수록 처리량은 어디까지 따라오고, 어디서부터 무너지는가.
//
// 폐쇄형(VU N명이 각자 1회)이 아니라 개방형(constant-arrival-rate)을 쓴다.
// 폐쇄형은 서버가 느려지면 부하도 같이 줄어들어서(= 부하가 서버에 종속) 붕괴를
// 관측할 수 없다. 실제 B2C 트래픽은 서버 사정을 봐주지 않는다.
//
// 한 번의 실행 = 사다리 한 칸(고정 도착률 R로 DURATION초). 칸마다 run.sh가
// 재시딩하므로 각 칸은 서로 독립이다. 램프 하나로 이어붙이지 않는 이유는,
// 앞 칸에서 소진된 재고와 쌓인 대기열이 뒤 칸으로 새기 때문이다.
//
// ── 처리량을 하나의 숫자로 말하면 거짓말이 된다 ──────────────────────────────
//
// 재고가 남아 있는 동안과 매진된 뒤는 완전히 다른 코드 경로다.
//   매진 전: 락을 잡고 잔여 좌석을 전부 읽고 쓴다 (비싸다)
//   매진 후: 락은 잡지만 읽을 게 없어 즉시 실패한다 (싸다)
//
// 실측(좌석 100, 목표 200 req/s): 달성 200 req/s인데 goodput은 10 req/s였다.
// 응답률만 보면 "목표를 100% 소화"로 보이지만, 실제로 좌석을 받은 사람은
// 초당 10명뿐이고 나머지는 전부 "품절" 응답을 빠르게 받은 것이다.
//
// 그래서 두 처리량을 분리해서 낸다.
//   할당 처리량 = 매진 전 구간의 성공/초  → 이 시스템이 실제로 좌석을 나눠주는 속도
//   거절 처리량 = 매진 후 구간의 요청/초  → 재고가 없을 때 부하를 털어내는 속도
//
// 국면을 나누려면 시각이 필요하므로 1초 버킷 태그를 단다. k6는 태그 서브메트릭을
// threshold에 선언해야만 요약에 내보내므로 아래에서 버킷마다 threshold를 만든다.
//
// 실행: k6 run -e RATE=800 -e DURATION=30 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const RATE = Number(__ENV.RATE || 200);
const DURATION = Number(__ENV.DURATION || 30);
const MAX_VUS = Number(__ENV.MAX_VUS || 20000);

// 사용자 관점의 타임아웃. 서버측 대기 상한(분산락 2분)보다 훨씬 짧게 잡는다.
// 2분을 기다려주는 B2C 클라이언트는 없고, 게이트웨이도 그전에 끊는다.
// 여기서 timeout으로 잡히는 건 "사용자가 포기한 요청"이라는 뜻이다.
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || '30s';

// gracefulStop 동안 잔여 요청이 빠지므로 관측 창은 DURATION보다 길어야 한다.
const NBUCKETS = Math.min(300, DURATION + 90);

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
  // 절대 실패하지 않는 조건. threshold를 거는 목적은 판정이 아니라 서브메트릭 집계다.
  thresholds[`booking_requests{sec:${b}}`] = ['count>=0'];
  thresholds[`booking_success{sec:${b}}`] = ['count>=0'];
  thresholds[`booking_latency_ms{sec:${b}}`] = ['max>=0'];
}

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds,
  scenarios: {
    storm: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION}s`,
      // 도착률 × 지연 만큼의 VU가 필요하다. 지연이 커지면 VU가 폭증하므로 상한을 둔다.
      // 상한에 걸리면 dropped_iterations로 드러나고, 그게 곧 "서버가 발생기를 삼켰다"는 신호다.
      preAllocatedVUs: Math.min(MAX_VUS, Math.max(200, RATE * 2)),
      maxVUs: MAX_VUS,
      gracefulStop: '60s',
    },
  },
};

export function setup() {
  // 경합점 1개 — 이 시나리오가 재려는 건 "단일 락 키의 처리량 상한"이다.
  // 경합점을 늘렸을 때의 변화는 S5(hotspot-skew)가 따로 잰다.
  return { t0: Date.now(), restaurantId: env.points[0], date: env.date, startTime: env.startTime };
}

export default function (data) {
  // 토큰은 돌려쓴다. 서버에 사용자당 중복 예약 제한이 없어서(CreateTimeTableOccupancyService)
  // 재사용해도 결과가 달라지지 않는다. 토큰 풀을 VU 수만큼 만들 필요가 없다.
  const token = tokens[__ITER % tokens.length];

  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.restaurantId}`,
    JSON.stringify({ date: data.date, startTime: data.startTime }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: token },
      timeout: REQ_TIMEOUT,
      tags: { name: 'booking' },
    },
  );

  // 응답을 받은 시각으로 버킷을 정한다. 발사 시각이 아니라 완료 시각이어야
  // "이 순간 시스템이 초당 몇 건을 내보내고 있었나"가 된다.
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
    cTimeout.add(1); // 서버 응답이 아니다 → 지연 분포에서 뺀다
  } else if (res.status >= 500) {
    cServerError.add(1);
  } else {
    // 현재 서버는 ClientException 전체를 400 하나로 매핑한다 — 품절과 그 외가 뭉쳐 있다.
    cRejected.add(1);
  }
  if (res.status !== 0) latency.add(res.timings.duration, { sec });
}

const sub = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values : {});
const cnt = (d, n) => sub(d, n).count || 0;

export function handleSummary(data) {
  const requests = cnt(data, 'booking_requests');
  const success = cnt(data, 'booking_success');
  const dropped = cnt(data, 'dropped_iterations');
  const lat = sub(data, 'booking_latency_ms');
  const latS = sub(data, 'booking_latency_success_ms');
  const timeout = cnt(data, 'booking_timeout');
  const err5xx = cnt(data, 'booking_server_error_5xx');

  const elapsed =
    (data.state && data.state.testRunDurationMs ? data.state.testRunDurationMs : DURATION * 1000) / 1000;

  // 초 단위 시계열. 빈 꼬리 버킷은 버린다.
  const series = [];
  for (let b = 0; b < NBUCKETS; b++) {
    const req = cnt(data, `booking_requests{sec:${b}}`);
    const ok = cnt(data, `booking_success{sec:${b}}`);
    const l = sub(data, `booking_latency_ms{sec:${b}}`);
    series.push({ sec: b, requests: req, success: ok, p50: l['p(50)'] || 0, p95: l['p(95)'] || 0 });
  }
  while (series.length && series[series.length - 1].requests === 0) series.pop();

  // 매진 시점 = 마지막으로 성공이 나온 초. 그 뒤로는 재고가 없다.
  // 성공이 0건이면 매진 개념이 성립하지 않으므로 국면을 나누지 않는다.
  let lastSuccessSec = -1;
  for (const s of series) if (s.success > 0) lastSuccessSec = s.sec;
  const soldOut = lastSuccessSec >= 0 && lastSuccessSec < series.length - 1;

  const pre = series.filter((s) => s.sec <= lastSuccessSec);
  const post = series.filter((s) => s.sec > lastSuccessSec);
  const sum = (a, f) => a.reduce((x, s) => x + f(s), 0);

  const preSec = pre.length;
  const postSec = post.length;

  const report = {
    targetRps: RATE,
    durationSeconds: DURATION,
    elapsedSeconds: elapsed,
    valid: requests > 0,
    requests,
    // 발사 시도 = 완료 + 드롭. 드롭이 많으면 서버가 아니라 발생기가 한계라는 뜻이므로
    // "도달률"을 반드시 같이 봐야 한다.
    attempted: requests + dropped,
    droppedIterations: dropped,
    offeredRps: (requests + dropped) / elapsed,
    achievedRps: requests / elapsed,
    attainment: RATE > 0 ? requests / elapsed / RATE : 0,

    // ── 국면 분리 ──
    soldOut,
    selloutSeconds: lastSuccessSec >= 0 ? lastSuccessSec + 1 : null,
    // 할당 처리량: 재고가 있는 동안 실제로 좌석을 나눠준 속도. 이게 진짜 처리량이다.
    allocationRps: preSec > 0 ? sum(pre, (s) => s.success) / preSec : 0,
    // 매진 전 구간의 총 요청 처리 속도 (성공 + 그 구간의 실패)
    preSelloutRps: preSec > 0 ? sum(pre, (s) => s.requests) / preSec : 0,
    // 거절 처리량: 재고가 바닥난 뒤 부하를 털어내는 속도. 높다고 좋은 게 아니다.
    rejectionRps: postSec > 0 ? sum(post, (s) => s.requests) / postSec : null,
    preSelloutP95: preSec > 0 ? Math.max(...pre.map((s) => s.p95)) : 0,
    postSelloutP95: postSec > 0 ? Math.max(...post.map((s) => s.p95)) : null,

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
      p90: lat['p(90)'] || 0,
      p95: lat['p(95)'] || 0,
      p99: lat['p(99)'] || 0,
      max: lat.max || 0,
    },
    latencySuccessMs: { p50: latS['p(50)'] || 0, p95: latS['p(95)'] || 0 },
    series,
  };

  const alloc = report.allocationRps;
  const rej = report.rejectionRps;
  const out = {
    stdout:
      `\n  목표 ${RATE} req/s | 달성 ${report.achievedRps.toFixed(0)} req/s ` +
      `(도달률 ${(report.attainment * 100).toFixed(0)}%)\n` +
      `  할당 ${alloc.toFixed(0)} req/s` +
      (soldOut ? ` (${report.selloutSeconds}s 만에 매진) | 거절 ${rej.toFixed(0)} req/s` : ' (매진 안 됨)') +
      `\n  p95 ${report.latencyMs.p95.toFixed(0)}ms | p99 ${report.latencyMs.p99.toFixed(0)}ms | ` +
      `timeout ${timeout} | 5xx ${err5xx} | dropped ${dropped}\n`,
  };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
