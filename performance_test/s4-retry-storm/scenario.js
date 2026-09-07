import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------------------------------------------------------------------------
// S4 — Retry Storm.
//
// 질문: 사용자가 새로고침하면 그 재시도가 부하를 증폭시켜 더 많은 실패를 만드는가.
//
// 독립변수는 도착률이 아니라 "재시도 정책"이다. 도착률(RATE)은 S2에서 구한 knee의
// 150%로 고정한다 — 나선은 용량 초과 상태에서만 발생하고, 여유가 있으면 재시도가
// 그냥 흡수돼서 정책 간 차이가 안 나온다.
//
// 이 시나리오에서 한 번의 이터레이션 = 한 명의 사용자(요청 1건이 아니다).
// 이터레이션 안에서 정책에 따라 재시도를 돌린다. 그래서 constant-arrival-rate의
// rate는 "요청 도착률"이 아니라 "사용자 도착률"이 된다. 이게 핵심 설계다:
// 재시도가 늘어도 사용자 유입은 그대로여야 정책 간 비교가 성립한다.
//
// 핵심 산출물:
//   effectiveThroughputRps  성공한 "고유 사용자" / 초  → 이게 줄면 나선 확정
//   amplification           총 발사 요청 / 고유 사용자 → 재시도가 만든 부하 배수
//   userCompletionMs        첫 시도 시작 → 최종 성공 (사용자 체감 시간)
//   abandoned               3초 안에 응답이 없어 버려진 요청 수
//
// 실행: k6 run -e POLICY=RETRY_FOREVER -e RATE=750 -e DURATION=120 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const RATE = Number(__ENV.RATE || 200);
const DURATION = Number(__ENV.DURATION || 120);
const MAX_VUS = Number(__ENV.MAX_VUS || 20000);

// "사용자 인내 한계"를 클라이언트 타임아웃으로 모델링한다. 서버측 대기 상한(분산락 2분)의
// 1/40이다. 이 괴리가 실험의 전부다 — k6가 커넥션을 끊어도 이미 락 대기에 들어간 워커는
// 그걸 모르고 2분까지 스레드를 놓지 않는다. 버려진 요청이 계속 자원을 먹는다.
const PATIENCE_S = Number(__ENV.PATIENCE_SEC || 3);

// NO_RETRY(1) / RETRY_ONCE(2) / RETRY_FOREVER(RETRY_CAP)
//
// "무한"에 상한을 두는 이유: 진짜 무한 루프면 런이 끝나지 않고, gracefulStop이 진행 중인
// 여정을 중간에 잘라버린다. 그러면 잘린 여정은 성공/실패 어느 쪽으로도 안 세어지고
// 완료 시간 분포에는 빨리 끝난 사람만 남아 결과가 낙관적으로 왜곡된다.
// 넉넉한 유한 상한을 두면 그 왜곡이 "gaveUp(포기한 사용자)"라는 명시적 숫자로 드러난다.
const RETRY_CAP = Number(__ENV.RETRY_CAP || 10);
const POLICY = (__ENV.POLICY || 'NO_RETRY').toUpperCase();
const MAX_ATTEMPTS = POLICY === 'RETRY_ONCE' ? 2 : POLICY === 'RETRY_FOREVER' ? RETRY_CAP : 1;

// 여정이 끝나기 전에 런이 종료되면 안 된다. 정책 C의 최장 여정은 인내한계 × 최대시도다.
const GRACEFUL_S = Number(__ENV.GRACEFUL_SEC || PATIENCE_S * MAX_ATTEMPTS + 30);

const cUsers = new Counter('user_journeys');
const cUserSuccess = new Counter('user_success');
const cUserSoldOut = new Counter('user_sold_out');
const cUserGaveUp = new Counter('user_gave_up');

const cRequests = new Counter('booking_requests');
const cSuccess = new Counter('booking_success');
const cSoldOut = new Counter('booking_sold_out');
const cRejected = new Counter('booking_rejected_4xx');
const cServerError = new Counter('booking_server_error_5xx');
const cAbandoned = new Counter('booking_abandoned');

const latency = new Trend('booking_latency_ms', true);
const latencySuccess = new Trend('booking_latency_success_ms', true);
const userCompletion = new Trend('user_completion_ms', true);
const userAttempts = new Trend('user_attempts');

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    retry_storm: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION}s`,
      // 이터레이션 1회가 요청 1건이 아니라 여정 전체이므로, 필요한 VU는
      // 사용자 도착률 × 여정 최장 시간이다. 정책 C에서는 이 값이 쉽게 만 단위가 된다.
      // 상한(약 16,000 — 임시 포트 수)에 걸리면 dropped_iterations로 드러나고,
      // 그건 "서버가 무너졌다"가 아니라 "사용자를 발사조차 못 했다"는 뜻이다.
      // 그래서 아래 report에서 userAttainment를 반드시 같이 본다.
      preAllocatedVUs: Math.min(MAX_VUS, Math.max(500, Math.ceil(RATE * PATIENCE_S * MAX_ATTEMPTS))),
      maxVUs: MAX_VUS,
      gracefulStop: `${GRACEFUL_S}s`,
    },
  },
};

export function setup() {
  // 경합점 1개 — S2와 같은 조건에서 정책만 바꿔야 비교가 성립한다.
  return { restaurantId: env.points[0], date: env.date, startTime: env.startTime };
}

export default function (data) {
  // 토큰은 돌려쓴다. 서버에 사용자당 중복 예약 제한이 없어서 재사용해도 결과가 안 변한다.
  // 여기서 "고유 사용자"는 토큰이 아니라 이터레이션 단위로 센다 — 재시도가 같은 사람의
  // 두 번째 시도라는 사실이 토큰이 아니라 여정 구조로 표현돼 있기 때문이다.
  const token = tokens[__ITER % tokens.length];
  const url = `${env.baseUrl}/api/v1/time-table/booking/${data.restaurantId}`;
  const body = JSON.stringify({ date: data.date, startTime: data.startTime });
  const params = {
    headers: { 'Content-Type': 'application/json', Authorization: token },
    timeout: `${PATIENCE_S}s`,
    tags: { name: 'booking' },
  };

  const journeyStart = Date.now();
  let attempts = 0;
  let outcome = 'gaveup';

  while (attempts < MAX_ATTEMPTS) {
    const attemptStart = Date.now();
    attempts++;

    const res = http.post(url, body, params);
    cRequests.add(1);

    if (res.status === 200 || res.status === 201) {
      cSuccess.add(1);
      latencySuccess.add(res.timings.duration);
      outcome = 'success';
    } else if (res.status === 409) {
      cSoldOut.add(1);
      // 품절은 사용자가 실제로 받은 확정 답이다. 새로고침해도 좌석이 생기지 않으므로
      // 재시도 대상에서 뺀다. 이걸 재시도로 치면 이 런은 재시도 증폭이 아니라
      // 재고 소진을 재게 된다. (좌석을 넉넉히 시딩하므로 정상 런에서는 0에 가깝다.)
      outcome = 'soldout';
    } else if (res.status === 0) {
      // 서버 응답이 아니다 → 지연 분포에서 뺀다. 이게 곧 "F5를 누른 순간"이다.
      cAbandoned.add(1);
    } else if (res.status >= 500) {
      cServerError.add(1);
    } else {
      // 현재 서버는 ClientException 전체를 400 하나로 매핑한다 — 품절과 그 외가 뭉쳐 있다.
      cRejected.add(1);
    }
    if (res.status !== 0) latency.add(res.timings.duration);

    if (outcome !== 'gaveup') break;
    if (attempts >= MAX_ATTEMPTS) break;

    // 다음 시도는 인내 한계 주기에 맞춘다. 빠르게 거절당한 요청을 곧바로 다시 쏘면
    // 증폭 계수가 "사용자의 재시도 습관"이 아니라 "서버가 얼마나 빨리 거절하는가"를
    // 재게 된다 — 빨리 거절하는 쪽이 부하를 더 받는 역설이 생긴다.
    const spent = (Date.now() - attemptStart) / 1000;
    if (spent < PATIENCE_S) sleep(PATIENCE_S - spent);
  }

  cUsers.add(1);
  userAttempts.add(attempts);
  if (outcome === 'success') {
    cUserSuccess.add(1);
    // 서버 p95가 아무리 좋아도 5번 재시도했으면 체감은 15초다. 그 괴리를 재는 값.
    userCompletion.add(Date.now() - journeyStart);
  } else if (outcome === 'soldout') {
    cUserSoldOut.add(1);
  } else {
    cUserGaveUp.add(1);
  }
}

const cnt = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values.count || 0 : 0);
const trd = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values : {});

export function handleSummary(data) {
  const users = cnt(data, 'user_journeys');
  const userSuccess = cnt(data, 'user_success');
  const requests = cnt(data, 'booking_requests');
  const dropped = cnt(data, 'dropped_iterations');
  const lat = trd(data, 'booking_latency_ms');
  const latS = trd(data, 'booking_latency_success_ms');
  const comp = trd(data, 'user_completion_ms');
  const att = trd(data, 'user_attempts');

  const elapsed =
    (data.state && data.state.testRunDurationMs ? data.state.testRunDurationMs : DURATION * 1000) / 1000;

  // 처리량의 분모는 elapsed가 아니라 DURATION이다.
  // 사용자 유입은 정확히 DURATION초 동안만 일어나고, 그 뒤 gracefulStop 구간은
  // 남은 여정이 빠지는 배수 시간이다. 재시도가 길수록 이 배수 시간이 길어지므로
  // elapsed로 나누면 재시도 정책이 불리해지도록 분모가 자동으로 커진다 —
  // "재시도하면 처리량이 준다"는 결론이 측정 방식 때문에 저절로 나와버린다.
  const drain = Math.max(0, elapsed - DURATION);

  const abandoned = cnt(data, 'booking_abandoned');
  const err5xx = cnt(data, 'booking_server_error_5xx');

  const report = {
    policy: POLICY,
    maxAttempts: MAX_ATTEMPTS,
    patienceSeconds: PATIENCE_S,
    targetUserRps: RATE,
    durationSeconds: DURATION,
    elapsedSeconds: elapsed,
    drainSeconds: drain,
    valid: users > 0,

    // --- 사용자 단위 (이 시나리오의 결론이 사는 층) --------------------------
    users: {
      launched: users,
      dropped: dropped,
      offered: users + dropped,
      success: userSuccess,
      soldOut: cnt(data, 'user_sold_out'),
      gaveUp: cnt(data, 'user_gave_up'),
    },
    userArrivalRps: users / DURATION,
    // 목표 사용자 도착률 대비 실제 발사 비율. 이게 무너지면 유효 처리량 하락의 원인이
    // 서버가 아니라 발생기일 수 있다 — 나선이라고 부르기 전에 반드시 확인할 값.
    userAttainment: RATE > 0 ? users / DURATION / RATE : 0,
    effectiveThroughputRps: userSuccess / DURATION,
    userSuccessRate: users > 0 ? userSuccess / users : 0,
    userCompletionMs: {
      p50: comp['p(50)'] || 0,
      p95: comp['p(95)'] || 0,
      p99: comp['p(99)'] || 0,
      max: comp.max || 0,
    },
    attemptsPerUser: { avg: att.avg || 0, p95: att['p(95)'] || 0, max: att.max || 0 },

    // --- 요청 단위 (부하의 크기. 성과가 아니다) ------------------------------
    requests,
    requestRps: requests / DURATION,
    // 재시도가 만든 부하 배수. 1.0이면 재시도가 없었다는 뜻.
    amplification: users > 0 ? requests / users : 0,
    outcome: {
      success: cnt(data, 'booking_success'),
      soldOut: cnt(data, 'booking_sold_out'),
      rejected4xx: cnt(data, 'booking_rejected_4xx'),
      serverError5xx: err5xx,
      abandoned,
    },
    abandonRate: requests > 0 ? abandoned / requests : 0,
    failureRate: requests > 0 ? (abandoned + err5xx) / requests : 0,
    latencyMs: {
      avg: lat.avg || 0,
      p50: lat['p(50)'] || 0,
      p90: lat['p(90)'] || 0,
      p95: lat['p(95)'] || 0,
      p99: lat['p(99)'] || 0,
      max: lat.max || 0,
    },
    latencySuccessMs: { p50: latS['p(50)'] || 0, p95: latS['p(95)'] || 0 },
  };

  const out = {
    stdout:
      `\n  정책 ${POLICY} (최대 ${MAX_ATTEMPTS}회 / 인내 ${PATIENCE_S}s) | 목표 ${RATE} user/s\n` +
      `  유효 처리량 ${report.effectiveThroughputRps.toFixed(1)} user/s ` +
      `(사용자 성공률 ${(report.userSuccessRate * 100).toFixed(0)}%, ` +
      `유저 도달률 ${(report.userAttainment * 100).toFixed(0)}%)\n` +
      `  증폭 ×${report.amplification.toFixed(2)} | 총 ${report.requestRps.toFixed(0)} req/s | ` +
      `abandon ${abandoned} | 5xx ${err5xx} | dropped ${dropped}\n` +
      `  서버 p95 ${report.latencyMs.p95.toFixed(0)}ms vs 사용자 체감 p95 ` +
      `${(report.userCompletionMs.p95 / 1000).toFixed(1)}s\n`,
  };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
