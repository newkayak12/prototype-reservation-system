import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// ---------------------------------------------------------------------------
// S8 — Inventory Scale.
//
// 잔여 재고 N을 바꿔가며 "예약 요청 하나의 임계구간"이 어떻게 변하는지 잰다.
//
// before의 findBookableTimeTable에는 LIMIT이 없어서, 좌석 하나를 잡으려고 남은 좌석
// 전부를 엔티티로 로딩한다. 그리고 그게 분산락 임계구간 안에서 일어난다.
//   → 임계구간 T(N) ≈ 고정비용 + 행당비용 × N
//   → 직렬 처리 상한 = 1/T(N) 은 N에 반비례
//
// 추정량: T(N) = 매진시간 / 판매좌석수
//   VU가 N보다 크면 N석이 전부 팔리고, 그 매진까지 걸린 시간을 판매 수로 나누면
//   성공 경로 한 건의 직렬 처리 시간이 나온다. (락이 완전 직렬이므로 성립)
//
// 폐쇄형(per-vu-iterations)을 쓴다: 이 실험은 "무너지는가"가 아니라 "임계구간이 얼마인가"를
// 재는 것이라 단순한 모형이 더 깨끗하다.
//
// 실행: k6 run -e VUS=1000 -e SEATS=1000 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const VUS = Number(__ENV.VUS || 1000);
const SEATS = Number(__ENV.SEATS || 0); // 기록용 (시딩은 seed.sh가 함)
// 서버측 대기 상한이 k6 기본(60s)보다 길다: 분산락 2분, 세마포어 5분.
// 60s로 끊으면 실제 지연이 아니라 타임아웃 절단선을 재게 된다.
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || '300s';

const cRequests = new Counter('booking_requests');
const cSuccess = new Counter('booking_success');
const cSoldOut = new Counter('booking_sold_out');
const cRejected = new Counter('booking_rejected_4xx');
const cServerError = new Counter('booking_server_error_5xx');
const cTimeout = new Counter('booking_timeout');

// 버스트 시작(t0) 기준 경과.
//   timeToSuccess.max = 마지막 좌석이 팔린 시각 = 매진 시간
//   timeToResolve.max = 마지막 요청이 응답을 받은 시각 = 전체 해소 시간
const timeToSuccess = new Trend('time_to_success_ms', true);
const timeToResolve = new Trend('time_to_resolve_ms', true);
const latency = new Trend('booking_latency_ms', true);
const latencySuccess = new Trend('booking_latency_success_ms', true);

export const options = {
  discardResponseBodies: false,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '15m',
      gracefulStop: '0s',
    },
  },
  thresholds: { booking_server_error_5xx: ['count==0'] },
};

export function setup() {
  if (tokens.length < VUS) {
    throw new Error(`token pool(${tokens.length}) < VUS(${VUS}). POOL_SIZE=${VUS} 로 users.sh 실행 필요.`);
  }
  // 경합점 1개 — 직렬 상한을 직접 재는 게 목적이므로 락 키가 하나여야 한다.
  return {
    restaurantId: env.points[0],
    date: env.date,
    startTime: env.startTime,
    seatsPerPoint: env.seatsPerPoint,
  };
}

export default function (data) {
  const token = tokens[(__VU - 1) % tokens.length];
  const t0 = exec.scenario.startTime;

  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.restaurantId}`,
    JSON.stringify({ date: data.date, startTime: data.startTime }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: token },
      timeout: REQ_TIMEOUT,
      tags: { name: 'booking' },
    },
  );

  const elapsed = Date.now() - t0;
  cRequests.add(1);
  timeToResolve.add(elapsed);

  if (res.status === 200 || res.status === 201) {
    cSuccess.add(1);
    timeToSuccess.add(elapsed);
    latencySuccess.add(res.timings.duration);
  } else if (res.status === 409) {
    cSoldOut.add(1);
  } else if (res.status === 0) {
    // 타임아웃/커넥션 실패. 서버 응답이 아니므로 지연 분포에서 제외한다.
    cTimeout.add(1);
  } else if (res.status >= 500) {
    cServerError.add(1);
  } else {
    // 현재 서버는 ClientException 전체를 400 하나로 매핑한다 - 품절과 그 외가 뭉쳐 있다.
    cRejected.add(1);
  }

  if (res.status !== 0) latency.add(res.timings.duration);
}

const cnt = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values.count || 0 : 0);
const trd = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values : {});

export function handleSummary(data) {
  const requests = cnt(data, 'booking_requests');
  const success = cnt(data, 'booking_success');
  const selloutMs = trd(data, 'time_to_success_ms').max || 0;
  const resolveMs = trd(data, 'time_to_resolve_ms').max || 0;
  const lat = trd(data, 'booking_latency_ms');
  const latS = trd(data, 'booking_latency_success_ms');

  // 핵심 산출물: 성공 경로 한 건의 직렬 처리 시간.
  //
  // 추정량은 "전원이 성공 경로를 탔을 때"만 유효하다. 좌석 N이 VU보다 적으면 VU가 다 뜨기도
  // 전에 매진돼서, 매진시간/판매수가 임계구간이 아니라 VU 기동 속도를 재게 된다.
  // (실측: N=10/VU200에서 31ms, N=1000/VU200에서 22ms — 재고가 적은 쪽이 더 느리게 나옴)
  // 따라서 N >= VUS 조건에서만 재고 민감도를 논한다.
  const estimatorValid = success === requests && requests > 0;
  const criticalSectionMs = success > 0 ? resolveMs / success : 0;
  const serialCeiling = criticalSectionMs > 0 ? 1000 / criticalSectionMs : 0;

  const report = {
    seats: SEATS || data.setup_data?.seatsPerPoint || 0,
    vus: VUS,
    // 요청 0건은 "성공 0건"이 아니라 측정 실패(setup 예외 등)다. 집계에서 빼야 한다.
    valid: requests > 0,
    // 임계구간 추정량이 유효한 런인지 (전원 성공 = 매진으로 인한 조기 종료 없음)
    estimatorValid,
    requests,
    outcome: {
      success,
      soldOut: cnt(data, 'booking_sold_out'),
      rejected4xx: cnt(data, 'booking_rejected_4xx'),
      serverError5xx: cnt(data, 'booking_server_error_5xx'),
      timeout: cnt(data, 'booking_timeout'),
    },
    selloutSeconds: selloutMs / 1000,
    resolveSeconds: resolveMs / 1000,
    criticalSectionMs,
    serialCeilingRps: serialCeiling,
    throughputRps: resolveMs > 0 ? requests / (resolveMs / 1000) : 0,
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
      `\n  좌석 ${report.seats} | VU ${VUS} | 성공 ${success} | ` +
      `매진 ${report.selloutSeconds.toFixed(3)}s | ` +
      `임계구간 ${criticalSectionMs.toFixed(2)}ms${estimatorValid ? '' : ' (무효: 매진)'} ` +
      `(직렬상한 ${serialCeiling.toFixed(0)} req/s) | ` +
      `p95 ${report.latencyMs.p95.toFixed(0)}ms | timeout ${report.outcome.timeout} | ` +
      `5xx ${report.outcome.serverError5xx}\n`,
  };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
