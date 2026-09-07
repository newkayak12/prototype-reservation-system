import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// ---------------------------------------------------------------------------
// 인기 좌석 티켓팅 버스트 시나리오 (재설계 후 아키텍처).
//
// 한 번의 k6 run = 하나의 VU 레벨에 대한 단일 버스트다:
//   좌석 30석이 갓 리셋된 상태에서 N명이 "동시에 1번씩" 예약을 시도하고,
//   전원의 응답이 끝날 때까지의 시간/성공수/레이턴시를 잰다.
//
// 이전 설계(ramping-vus로 100→2000까지 한 런에서 계단식 램프)는 VU 100 구간
// 초반에 30석이 소진돼 나머지 200초가 전부 "이미 매진된 좌석에 대한 거절 응답"
// 측정이었다. VU 레벨별로 런을 쪼개고 매 런 앞에서 좌석을 재시딩해야 각 레벨이
// 동일 조건(=빈 좌석 30석)에서 비교된다.
//
// ## before 시나리오와 다른 점 — 대기열
//
// 재설계 후에는 예약 앞에 대기열이 있고, 서버가 이를 **강제**한다
// (`CreateTimeTableOccupancyService.verifyAdmitted`). 대기열을 건너뛰고 booking을
// 직접 부르면 입장 자격 없음으로 즉시 거절되므로, 이 시나리오는 진입 → 폴링 →
// 예약 세 단계를 밟는다. 그게 이 아키텍처에서 사용자가 실제로 겪는 경로다.
//
// 그래서 t0 기준 시간(매진/해소)에는 대기 시간이 포함된다. before와 비교할 때
// **그게 정확한 비교다** — 사용자 입장에서 "표를 사기까지 걸린 시간"이니까.
// 예약 API 호출 자체의 지연만 보려면 booking_latency_ms를 본다.
//
// 실행: VUS=600 OUT=results/x.json k6 run scenarios/booking.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../lib/env.json'));

const users = new SharedArray('users', function () {
  return JSON.parse(open('../lib/users.json'));
});

const VUS = Number(__ENV.VUS || 100);
// 서버측 대기 상한이 k6 기본 타임아웃(60s)보다 길 수 있으므로 넉넉히 잡고,
// 타임아웃은 별도 버킷으로 집계한다.
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || '180s';
const ALLOW_TOKEN_REUSE = __ENV.ALLOW_TOKEN_REUSE === '1';

// 폴링 간격. **이 시나리오에서 가장 중요한 손잡이다.**
//
// 예전에는 입장 워커(500ms 주기)의 박자를 따라가는 종속 변수였다. 지금은 반대다 - 승격이
// 타이머가 아니라 요청 경로(진입/폴링)에서 일어나므로, 이 값이 곧 permit 회전 주기를 정한다:
//
//   회전 = 승격 -> 사용자가 다음 폴링에서 알아챔(간격/2) -> 예약(약 40ms) -> 반납
//   처리량 상한 = admission-capacity / 회전
//
// 그래서 이 값은 서버의 admission-capacity와 짝으로 움직여야 한다. 한쪽만 바꾸면
// 아키텍처가 아니라 그 불일치를 측정하게 된다.
//
// 실제 배포에서는 이 간격이 곧 **CDN TTL**이다. 순번 조회는 엣지에서 캐시될 것이므로,
// 여기서 2로 두면 "TTL 2초짜리 CDN을 앞에 세운 상태"를 인프라 없이 재현한 것이 된다
// (사용자마다 URL이 다른 현재 API 기준. 캐시 엔트리가 사용자별로 생기므로
//  "각자 TTL마다 한 번 호출"과 origin 부하가 동일하다).
const POLL_INTERVAL_SECONDS = Number(__ENV.POLL_INTERVAL_SECONDS || 0.5);

// 한 VU가 입장을 기다리는 데 쓸 예산. **런이 끝나지 않는 것을 막는 안전장치일 뿐이다.**
//
// 한때는 이 값이 결과를 지배했다. permit이 성공 경로에서만 반납되던 시절에는 품절로 거절된
// 사용자가 자리를 쥔 채 끝나 대기열이 정원 언저리에서 멈췄고, 남은 VU들이 예산을 다 쓰고
// 나가면서 **해소 시간이 이 상수에 고정**됐다. 그 상태의 처리율을 before와 나란히 놓은 것이
// 잘못된 비교였다.
//
// 종착 거절(품절/중복)도 자리를 반납하도록 고친 뒤로는 모든 VU 레벨에서 대기포기가 0이다.
// 그러니 이 값이 결과에 나타나면 그건 "예산이 짧았다"가 아니라 **어딘가 막혔다는 신호**다.
// queue_timeout이 0이 아닌 회차는 원인을 찾기 전까지 집계에 넣으면 안 된다.
const QUEUE_WAIT_BUDGET_MS = Number(__ENV.QUEUE_WAIT_BUDGET_MS || 30000);

// 성공/실패 사유별 분리 집계.
// 주의: 현재 서버는 RestControllerExceptionHandler에서 ClientException 전체를 400 하나로
// 매핑한다 - 품절(AllTheSeatsAreAlreadyOccupied)과 세마포어 획득 실패
// (AllTheThingsAreAlreadyOccupied)가 HTTP 레벨에서 구분되지 않는다. 409/423/429 버킷은
// 예외 핸들러를 세분화하면 그대로 채워지도록 미리 열어 뒀다.
// Trend의 summary values에는 count가 없다(avg/min/med/max/p(N)뿐). 총 요청 수는 Counter로 따로 센다.
const cRequests = new Counter('booking_requests');
const cSuccess = new Counter('booking_success');
const cSoldOut = new Counter('booking_sold_out_409');
const cSemaphore = new Counter('booking_semaphore_blocked_423');
const cRateLimited = new Counter('booking_rate_limited_429');
const cRejected = new Counter('booking_rejected_400');
const cUnauthorized = new Counter('booking_unauthorized_401');
const cServerError = new Counter('booking_server_error_5xx');
const cTimeout = new Counter('booking_timeout');

// 대기열 단계 (before에는 존재하지 않는 새 비용).
const cQueueEnterFailed = new Counter('queue_enter_failed');
const cQueueTimeout = new Counter('queue_timeout');
const cQueueAdmitted = new Counter('queue_admitted');
const queueWait = new Trend('queue_wait_ms', true);

// 버스트 시작(t0) 기준 경과 시간.
//   timeToSuccess의 max  = 마지막 좌석이 팔린 시각 = "매진 시간"
//   timeToResolve의 max  = 마지막 요청이 응답을 받은 시각 = "전체 해소 시간"
const timeToSuccess = new Trend('time_to_success_ms', true);
const timeToResolve = new Trend('time_to_resolve_ms', true);
// 타임아웃(status 0)은 실제 서버 응답이 아니므로 레이턴시 분포에서 제외한다.
const bookingLatency = new Trend('booking_latency_ms', true);

export const options = {
  setupTimeout: '600s',
  discardResponseBodies: false,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
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
    // 5xx는 0이어야 한다. 나머지(품절/충돌/대기열 초과)는 시나리오상 정상 결과다.
    booking_server_error_5xx: ['count==0'],
  },
};

export function setup() {
  if (users.length < VUS && !ALLOW_TOKEN_REUSE) {
    throw new Error(
      `user pool(${users.length}) < VUS(${VUS}). 티켓팅 시뮬레이션은 VU 1개당 서로 다른 유저여야 ` +
        `한다 - POOL_SIZE=${VUS} ./seed.sh 로 유저를 늘리거나 ALLOW_TOKEN_REUSE=1 로 강제 실행.`,
    );
  }

  // VU가 쓸 만큼만 로그인한다 (VUS < poolSize인 레벨에서 불필요한 로그인 비용 제거).
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
      if (res.status === 200) {
        tokens.push(JSON.parse(res.body).accessToken);
      }
    }
  }

  if (tokens.length < needed) {
    throw new Error(
      `sign-in ${tokens.length}/${needed} 성공. seed.sh가 정상 실행됐는지 확인 필요.`,
    );
  }

  return {
    tokens,
    restaurantId: env.restaurantId,
    date: env.date,
    startTime: env.startTime,
    seatCount: env.seatCount,
  };
}

export default function (data) {
  // VU 1개 = 유저 1명 (토큰 공유 금지). 티켓팅에서 경쟁 주체는 커넥션이 아니라 사람이다.
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const headers = { 'Content-Type': 'application/json', Authorization: token };
  const slotBody = JSON.stringify({ date: data.date, startTime: data.startTime });
  const queueUrl = `${env.baseUrl}/api/v1/time-table/booking/${data.restaurantId}/queue`;
  const t0 = exec.scenario.startTime;

  cRequests.add(1);

  // --- 1) 대기열 진입 -------------------------------------------------------
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
  const ticketId = entered.ticketId;

  // --- 2) 입장 허용까지 폴링 -------------------------------------------------
  // position 0은 "이미 입장 허용됨"을 뜻한다(EnterWaitingQueue.ADMITTED_POSITION).
  // 대기 중인 티켓은 rank + 1 이라 항상 1 이상이다.
  const waitStart = Date.now();
  let admitted = entered.position === 0;

  while (!admitted && Date.now() - waitStart < QUEUE_WAIT_BUDGET_MS) {
    sleep(POLL_INTERVAL_SECONDS);

    const pollRes = http.get(
      `${queueUrl}/${ticketId}?date=${data.date}&startTime=${data.startTime}`,
      { headers, timeout: REQ_TIMEOUT, tags: { name: 'queue_poll' } },
    );

    if (pollRes.status === 200 && JSON.parse(pollRes.body).status === 'ADMITTED') {
      admitted = true;
    }
  }

  if (!admitted) {
    // 입장 정원에 막혀 예약 단계까지 가지 못했다. 거절도 실패도 아닌 "아직 줄 서 있음"이다.
    cQueueTimeout.add(1);
    timeToResolve.add(Date.now() - t0);
    return;
  }

  queueWait.add(Date.now() - waitStart);
  cQueueAdmitted.add(1);

  // --- 3) 예약 -------------------------------------------------------------
  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.restaurantId}`,
    slotBody,
    { headers, timeout: REQ_TIMEOUT, tags: { name: 'booking' } },
  );

  const elapsed = Date.now() - t0;
  timeToResolve.add(elapsed);

  if (res.status === 201 || res.status === 200) {
    cSuccess.add(1);
    timeToSuccess.add(elapsed);
  } else if (res.status === 409) {
    cSoldOut.add(1);
  } else if (res.status === 423) {
    cSemaphore.add(1);
  } else if (res.status === 429) {
    cRateLimited.add(1);
  } else if (res.status === 400) {
    cRejected.add(1);
  } else if (res.status === 401 || res.status === 403) {
    cUnauthorized.add(1);
  } else if (res.status >= 500) {
    cServerError.add(1);
  } else if (res.status === 0) {
    // k6가 타임아웃/커넥션 실패로 끊은 경우. 이전 설계는 이걸 status<500 체크로 "통과"시켰다.
    cTimeout.add(1);
  }

  if (res.status !== 0) {
    bookingLatency.add(res.timings.duration);
  }

  check(res, {
    'got a response (not timeout)': (r) => r.status !== 0,
    'not 5xx': (r) => r.status === 0 || r.status < 500,
  });
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
  const success = count(data, 'booking_success');
  const requests = count(data, 'booking_requests');
  const selloutMs = trend(data, 'time_to_success_ms').max || 0;
  const resolveMs = trend(data, 'time_to_resolve_ms').max || 0;
  const lat = trend(data, 'booking_latency_ms');
  const wait = trend(data, 'queue_wait_ms');

  const report = {
    vus: VUS,
    seatCount: env.seatCount,
    // setup()이 던지거나 VU가 한 번도 못 돈 경우 요청이 0건이다. 이런 런은 "성공 0건"이 아니라
    // "측정 실패"이므로 집계에서 빼야 한다 - aggregate.py가 이 플래그를 보고 건너뛴다.
    valid: requests > 0,
    requests,
    outcome: {
      success,
      soldOut409: count(data, 'booking_sold_out_409'),
      semaphoreBlocked423: count(data, 'booking_semaphore_blocked_423'),
      rateLimited429: count(data, 'booking_rate_limited_429'),
      rejected400: count(data, 'booking_rejected_400'),
      unauthorized401: count(data, 'booking_unauthorized_401'),
      serverError5xx: count(data, 'booking_server_error_5xx'),
      timeout: count(data, 'booking_timeout'),
      // 재설계 후에만 존재하는 버킷. before 산출물에는 이 키가 없다.
      queueTimeout: count(data, 'queue_timeout'),
      queueEnterFailed: count(data, 'queue_enter_failed'),
    },
    queue: {
      admitted: count(data, 'queue_admitted'),
      waitMs: {
        p50: wait['p(50)'] || 0,
        p95: wait['p(95)'] || 0,
        max: wait.max || 0,
      },
      budgetMs: QUEUE_WAIT_BUDGET_MS,
    },
    // 매진 시간: 버스트 발사부터 마지막 좌석이 팔리기까지. TPS = 팔린 좌석 / 매진 시간.
    selloutSeconds: selloutMs / 1000,
    tps: selloutMs > 0 ? success / (selloutMs / 1000) : 0,
    // 전체 해소 시간: 마지막 요청(성공/거절/대기 포기)이 종착점에 닿기까지.
    // 대기 예산에 걸린 VU가 있으면 이 값은 예산(QUEUE_WAIT_BUDGET_MS)에 붙는다 -
    // 시스템의 한계가 아니라 시나리오 상수이므로 그렇게 읽어야 한다.
    resolveSeconds: resolveMs / 1000,
    throughput: resolveMs > 0 ? requests / (resolveMs / 1000) : 0,
    latencyMs: {
      avg: lat.avg || 0,
      p50: lat['p(50)'] || 0,
      p90: lat['p(90)'] || 0,
      p95: lat['p(95)'] || 0,
      p99: lat['p(99)'] || 0,
      max: lat.max || 0,
    },
  };

  const out = {
    stdout:
      `\n  VU ${report.vus} | 성공 ${success}/${env.seatCount}석 | ` +
      `매진 ${report.selloutSeconds.toFixed(2)}s (TPS ${report.tps.toFixed(1)}) | ` +
      `해소 ${report.resolveSeconds.toFixed(2)}s (${report.throughput.toFixed(1)} req/s) | ` +
      `p95 ${report.latencyMs.p95.toFixed(0)}ms | 대기포기 ${report.outcome.queueTimeout} | ` +
      `timeout ${report.outcome.timeout} | 5xx ${report.outcome.serverError5xx}\n`,
  };
  if (__ENV.OUT) {
    out[__ENV.OUT] = JSON.stringify(report, null, 2);
  }
  return out;
}
