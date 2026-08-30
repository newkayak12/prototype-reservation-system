import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------------------------------------------------------------------------
// S7 — Integrity Under Storm.
//
// 질문: 극한 경합에서 좌석이 두 번 팔리는가.
//
// 이 시나리오가 재는 건 속도가 아니라 정확성이다. 여기서 나오는 값 중 결론을 바꾸는 건
//   successResponses = 클라이언트가 실제로 받은 200/201 수
// 하나뿐이고, 그것도 run.sh가 뒤이어 재는 "DB 유효 점유 수"와 짝을 이뤄야만 의미가 있다.
//
//   DB > 200  → 팔면 안 되는 좌석을 팔았다        (오버부킹)
//   200 > DB  → 성공이라 답해놓고 그 좌석을 잃었다 (유령 성공)
//
// 두 방향은 원인도 심각도도 다르다. 오버부킹은 재고를 넘긴 것이고, 유령 성공은 사용자에게
// 거짓말을 한 것이다. 그래서 aggregate.py는 둘을 절대 하나의 숫자로 합치지 않는다.
//
// 두 모드:
//   PART=extreme  폐쇄형(per-vu-iterations). VU 3000명이 좌석 1석에 동시에 1번씩 = 3000:1.
//                 개방형을 쓰지 않는 이유: 이건 처리량 실험이 아니라 레이스 실험이다.
//                 "전원이 같은 순간에 한 번 쏜다"가 재현하려는 상황 그 자체라, 폐쇄형이
//                 현상에 더 가깝고 해석할 변수도 적다.
//   PART=storm    개방형(constant-arrival-rate). 1500 req/s × 60s, 좌석 100석.
//                 자원이 포화된 상태(스레드 고갈·커넥션 타임아웃·GC 정지)에서 락이 풀리거나
//                 트랜잭션이 잘리는지를 본다. 순간 경합보다 이쪽이 현실적으로 더 위험하다.
//
// 실행: k6 run -e PART=extreme -e VUS=3000 -e OUT=x.json scenario.js
//       k6 run -e PART=storm -e RATE=1500 -e DURATION=60 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const PART = (__ENV.PART || 'extreme').toLowerCase();
const IS_EXTREME = PART === 'extreme';

const VUS = Number(__ENV.VUS || 3000);
const RATE = Number(__ENV.RATE || 1500);
const DURATION = Number(__ENV.DURATION || 60);
const MAX_VUS = Number(__ENV.MAX_VUS || 20000);
const SEATS = Number(__ENV.SEATS || 0); // 기록용 — 실제 시딩은 seed.sh가 한다

// 타임아웃을 길게 잡는다. S2(30s)와 정반대 판단인데, 재는 대상이 다르기 때문이다.
// 짧게 끊으면 서버가 나중에 커밋하는 요청이 클라이언트 쪽에서 status 0으로 남고, 그 행은
// "200 없는 DB 행" = 가짜 오버부킹으로 집계된다. 정합성 판정에서 그건 그냥 오염이다.
// 사용자 체감 지연은 S2/S6가 따로 잰다. 여기서는 모든 요청의 최종 상태를 아는 게 우선이다.
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || '300s';

// 같은 이유로 gracefulStop도 길다. 여기서 잘린 요청은 interrupted_iterations로 빠져
// k6 카운터에는 안 잡히는데 서버는 계속 처리한다 — 정확히 가짜 오버부킹을 만드는 경로다.
const GRACEFUL_STOP = __ENV.GRACEFUL_STOP || '300s';

const cRequests = new Counter('booking_requests');
const cSuccess = new Counter('booking_success');
const cSoldOut = new Counter('booking_sold_out');
const cRejected = new Counter('booking_rejected_4xx');
const cServerError = new Counter('booking_server_error_5xx');
const cTimeout = new Counter('booking_timeout');

const latency = new Trend('booking_latency_ms', true);
const latencySuccess = new Trend('booking_latency_success_ms', true);

const extremeScenario = {
  executor: 'per-vu-iterations',
  vus: VUS,
  iterations: 1,
  maxDuration: '15m',
  gracefulStop: GRACEFUL_STOP,
};

const stormScenario = {
  executor: 'constant-arrival-rate',
  rate: RATE,
  timeUnit: '1s',
  duration: `${DURATION}s`,
  preAllocatedVUs: Math.min(MAX_VUS, Math.max(200, RATE * 2)),
  maxVUs: MAX_VUS,
  gracefulStop: GRACEFUL_STOP,
};

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  // threshold를 걸지 않는다. 5xx가 났다는 건 이 실험에서는 실패가 아니라 관측 대상이고,
  // 판정은 aggregate.py가 DB와 대조해서 내린다.
  scenarios: IS_EXTREME ? { extreme: extremeScenario } : { storm: stormScenario },
};

export function setup() {
  if (tokens.length === 0) {
    throw new Error('token pool이 비어 있다. users.sh 먼저 실행할 것.');
  }
  // 경합점 1개 — 좌석 하나(또는 100석)를 두고 전원이 같은 락 키로 몰리는 게 실험 조건이다.
  return { restaurantId: env.points[0], date: env.date, startTime: env.startTime };
}

export default function (data) {
  // 폐쇄형에서는 모든 VU의 __ITER가 0이라 __ITER로 인덱싱하면 전원이 같은 토큰 하나를 쓴다.
  // 서버에 사용자당 중복 예약 제한은 없지만, 토큰 한 개에 3000요청을 몰면 검증 캐시 같은
  // 부수 경로가 결과에 섞일 수 있어 VU 단위로 흩어 놓는다.
  const token = tokens[(IS_EXTREME ? __VU - 1 : __ITER) % tokens.length];

  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.restaurantId}`,
    JSON.stringify({ date: data.date, startTime: data.startTime }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: token },
      timeout: REQ_TIMEOUT,
      tags: { name: 'booking' },
    },
  );

  cRequests.add(1);
  if (res.status === 200 || res.status === 201) {
    cSuccess.add(1);
    latencySuccess.add(res.timings.duration);
  } else if (res.status === 409) {
    cSoldOut.add(1);
  } else if (res.status === 0) {
    cTimeout.add(1); // 서버 응답이 아니다 → 지연 분포에서 빼고, 판정에서는 불확정으로 센다
  } else if (res.status >= 500) {
    cServerError.add(1);
  } else {
    // 현재 서버는 ClientException 전체를 400 하나로 매핑한다 — 품절과 그 외가 뭉쳐 있다.
    cRejected.add(1);
  }
  if (res.status !== 0) latency.add(res.timings.duration);
}

const cnt = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values.count || 0 : 0);
const trd = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values : {});

export function handleSummary(data) {
  const requests = cnt(data, 'booking_requests');
  const success = cnt(data, 'booking_success');
  const timeout = cnt(data, 'booking_timeout');
  const dropped = cnt(data, 'dropped_iterations');
  const interrupted = cnt(data, 'interrupted_iterations');
  const lat = trd(data, 'booking_latency_ms');
  const latS = trd(data, 'booking_latency_success_ms');

  const elapsed =
    (data.state && data.state.testRunDurationMs
      ? data.state.testRunDurationMs
      : DURATION * 1000) / 1000;

  const report = {
    part: PART,
    seats: SEATS,
    vus: IS_EXTREME ? VUS : 0,
    targetRps: IS_EXTREME ? 0 : RATE,
    durationSeconds: IS_EXTREME ? 0 : DURATION,
    elapsedSeconds: elapsed,
    // 요청 0건은 "성공 0건"이 아니라 측정 실패(setup 예외 등)다. 집계에서 빼야 한다.
    valid: requests > 0,
    requests,
    attempted: requests + dropped,
    droppedIterations: dropped,
    // k6가 중간에 끊어버린 요청. 서버는 계속 처리했을 수 있으므로 DB 대조에서 불확정이다.
    interruptedIterations: interrupted,
    // 이 실험의 대조군 A. 대조군 B(DB 유효 점유 수)는 integrity.sh가 따로 낸다.
    successResponses: success,
    // 응답을 못 받은 요청 = 이 요청이 DB에 반영됐는지 클라이언트가 알 수 없는 요청.
    // 0이 아니면 successResponses vs DB 불일치를 결함으로 단정할 수 없다.
    ambiguousRequests: timeout + interrupted,
    outcome: {
      success,
      soldOut: cnt(data, 'booking_sold_out'),
      rejected4xx: cnt(data, 'booking_rejected_4xx'),
      serverError5xx: cnt(data, 'booking_server_error_5xx'),
      timeout,
    },
    achievedRps: elapsed > 0 ? requests / elapsed : 0,
    latencyMs: {
      avg: lat.avg || 0,
      p50: lat['p(50)'] || 0,
      p95: lat['p(95)'] || 0,
      p99: lat['p(99)'] || 0,
      max: lat.max || 0,
    },
    latencySuccessMs: { p50: latS['p(50)'] || 0, p95: latS['p(95)'] || 0 },
  };

  const head = IS_EXTREME
    ? `  extreme | 좌석 ${SEATS} | VU ${VUS}`
    : `  storm | 좌석 ${SEATS} | ${RATE} req/s × ${DURATION}s (달성 ${report.achievedRps.toFixed(0)})`;

  const out = {
    stdout:
      `\n${head} | 요청 ${requests} | **성공 응답 ${success}**\n` +
      `  품절 ${report.outcome.soldOut} | 4xx ${report.outcome.rejected4xx} | ` +
      `5xx ${report.outcome.serverError5xx} | timeout ${timeout} | 중단 ${interrupted} | ` +
      `dropped ${dropped}\n` +
      `  p95 ${report.latencyMs.p95.toFixed(0)}ms | p99 ${report.latencyMs.p99.toFixed(0)}ms\n` +
      `  (DB 대조는 integrity.sh 결과와 aggregate.py가 한다)\n`,
  };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
