import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------------------------------------------------------------------------
// S1 — 단건 오픈런 (일제 도착)
//
// 부하테스트가 아니라 트래픽 테스트다. "몇 req/s에서 무너지나"를 묻지 않는다.
// 예약 도메인은 오픈 시각이 공표되어 있어서 전원이 그 시각에 온다 — 일제 도착이
// 예외가 아니라 평상시 모양이다. 그 이벤트를 재현하고 사용자가 뭘 겪었는지 잰다.
//
// 부하 모형: 유한 인구 N명, 1인 1요청, 전원 동시 발사.
//   개방형(constant-arrival-rate)은 여기 쓸 수 없다. rate=8000 timeUnit=1s 는
//   8,000건을 1초에 균등 배분한다(125µs 간격) — 일제 도착이 아니라 촘촘한 줄서기다.
//   반대로 VU가 루프를 돌면 부하가 자기제한된다(서버가 느려지면 다음 발사도 느려짐).
//   iterations=1 이면 둘 다 피한다. 부하가 오직 인구수로 결정된다.
//
// ── CountDownLatch 흉내 ────────────────────────────────────────────────────
// k6에는 VU 간 공유 가변 상태가 없다(SharedArray는 읽기 전용). 배리어로 쓸 수 있는
// 건 시각뿐이다. setup()의 반환값은 전 VU에 배포되고 전부 같은 프로세스라 시계
// 스큐가 0이므로, 공통 벽시계 시각이 countDown() 역할을 한다.
//
// ★ 커넥션 예열이 없으면 latch가 아니다.
//   배리어 직후 TCP 핸드셰이크 N개가 동시에 몰리면, 재는 게 예약 API가 아니라
//   커널 accept 큐가 된다. 그래서 배리어 전에 락 없는 GET을 한 번 보내 keep-alive
//   소켓을 열어둔다. 발사 때는 이미 열린 소켓에 바이트만 나간다.
//
//   그리고 이건 트릭이 아니라 더 정확한 모사다. 실제 티켓팅에서 사람들은 오픈 전에
//   이미 페이지를 열어두고 시계를 본다. 커넥션은 정각 이전에 맺어져 있다.
//   그래서 실패가 두 종류로 갈린다:
//     예열 실패 = "페이지가 안 열려요"   (tomcat max-connections 8192 벽)
//     발사 실패 = "눌렀는데 답이 없어요" (워커 200개 + 분산락 직렬화)
//
//   예열 자체가 두 번째 스톰이 되면 안 되므로 VU 번호로 흩뿌린다.
//
// ── 유효성 ────────────────────────────────────────────────────────────────
// 발사가 번지면 그 회차는 결과가 아니라 폐기 대상이다. 일제 도착을 재현하지 못한
// 데이터로 "동시 접속 N명에서 이랬다"고 말할 수 없다. fire_skew_ms 와 late_vus 를
// threshold로 걸어 k6가 스스로 실패를 표시하게 한다(exit 99). run.sh는 결과를
// 남기되 그 회차에 표시를 남긴다.
//
// 실행: k6 run -e CROWD=3000 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const CROWD = Number(__ENV.CROWD || 1000);        // 동시 도착 인원
const TIMEOUT_SEC = Number(__ENV.TIMEOUT_SEC || 60);
const SETTLE_SEC = Number(__ENV.SETTLE_SEC || 20); // 예열 종료 → 발사. 앱이 가라앉을 시간

// 예열을 인원에 비례해 흩뿌린다. 10,000명이면 100초에 걸쳐 100 req/s.
// 고정값으로 두면 큰 군중에서 예열이 그 자체로 스톰이 된다.
const SPREAD_SEC = Number(__ENV.SPREAD_SEC || Math.max(30, Math.ceil(CROWD / 100)));

// 발사 스큐 허용치. 이걸 넘으면 "동시"가 아니었다는 뜻이라 회차를 버린다.
// 잠정값 — 첫 probe 런의 실측 분포를 보고 조정해야 한다. 근거 없이 고정하면
// 통과시키려고 맞춘 숫자가 된다.
const SKEW_MAX_MS = Number(__ENV.SKEW_MAX_MS || 1000);

const SPREAD_MS = SPREAD_SEC * 1000;
const SETTLE_MS = SETTLE_SEC * 1000;

// ── 지표: 용량이 아니라 "그날 그 사람들이 겪은 것" ──────────────────────────
const fireSkew = new Trend('fire_skew_ms', true);   // 동시였는지 자체의 증거
const lateVus = new Counter('late_vus');            // 배리어를 놓친 VU (예열 시간 부족)

const warmupFail = new Counter('warmup_fail');      // "페이지가 안 열려요"
const warmupOk = new Counter('warmup_ok');

const seatWon = new Trend('seat_won_ms', true);     // max = 매진까지 걸린 시간
const answered = new Trend('answered_ms', true);    // max = 마지막 사람이 답 받은 시각

const outSuccess = new Counter('out_success');      // 좌석 획득
const outSoldOut = new Counter('out_soldout');      // 정상 거절 (매진/중복)
const outRejected = new Counter('out_rejected');    // 락 타임아웃 / 스로틀
const out5xx = new Counter('out_5xx');
const outTimeout = new Counter('out_timeout');      // "눌렀는데 답이 없어요" (기다리다 만료)
const outDropped = new Counter('out_dropped');      // "누르자마자 튕겼어요" (서버가 커넥션을 끊음)
const outRefused = new Counter('out_refused');      // 서버에 닿지도 못함 (connection refused)
const outOther = new Counter('out_other');

export const options = {
  scenarios: {
    openrun: {
      executor: 'per-vu-iterations',
      vus: CROWD,
      iterations: 1,              // 1인 1요청. 자기제한 없음
      // 예열 + 안정화 + 타임아웃 + 여유. 여기 걸리면 이상 상황이므로 넉넉히 준다.
      maxDuration: `${SPREAD_SEC + SETTLE_SEC + TIMEOUT_SEC + 120}s`,
      gracefulStop: '10s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    // 실패해도 런은 계속된다. 판정만 남긴다.
    fire_skew_ms: [`max<${SKEW_MAX_MS}`],
    late_vus: ['count<1'],
  },
  // 예열로 연 커넥션을 발사까지 유지해야 latch가 성립한다.
  noConnectionReuse: false,
  noVUConnectionReuse: false,
  discardResponseBodies: false,
};

export function setup() {
  const slot = env.slots[0];
  return {
    fireAt: Date.now() + SPREAD_MS + SETTLE_MS,
    hotId: env.points[0],
    bgId: env.background.restaurantId,
    date: env.date,
    startTime: slot.startTime,
  };
}

// status/error를 사용자 경험 단위로 분류한다.
// k6의 숫자 error_code는 버전 간 매핑이 바뀔 수 있어 문자열로 판정한다.
function classify(res) {
  if (res.status === 0) {
    const e = String(res.error || '').toLowerCase();

    // ★ 'dial' 이 붙은 실패는 TCP 연결 수립 단계에서 죽은 것이다.
    //   "기다렸는데 답이 없다"가 아니라 "줄에 서지도 못했다"이고, 사용자에게는
    //   누르자마자 나는 에러로 보인다. 문자열에 'timeout'이 들어있다는 이유로
    //   응답 타임아웃과 합치면, max-connections 벽이 "서버가 느리다"로 둔갑한다.
    //   (1차·2차 측정에서 연속으로 이 함정에 빠졌다)
    if (e.includes('dial')) return 'refused';

    if (e.includes('deadline') || e.includes('timeout')) return 'timeout';
    if (e.includes('refused')) return 'refused';
    // EOF / reset = 서버가 응답 없이 커넥션을 끊었다. 이건 '기다렸는데 답이 없음'과
    // 완전히 다른 사건이다 — tomcat이 max-connections를 넘겨 끊어버린 거라
    // 사용자에게는 "누르자마자 튕김"으로 보인다. 섞으면 8192 벽이 통계에서 사라진다.
    // (실제로 1차 측정에서 이 둘을 합쳐 놓아 벽을 놓쳤다)
    if (e.includes('eof') || e.includes('reset') || e.includes('broken pipe')) return 'dropped';
    return 'neterr';
  }
  if (res.status === 200 || res.status === 201) return 'success';
  if (res.status === 400 || res.status === 409) return 'soldout';
  if (res.status === 423 || res.status === 429) return 'rejected';
  if (res.status >= 500) return 'error5xx';
  return 'other';
}

export default function (data) {
  const token = tokens[(__VU - 1) % tokens.length];

  // ── 1. 예열: 배리어 전에 커넥션을 연다 ──────────────────────────────────
  // 창(window)의 끝을 fireAt - SETTLE 로 고정하고 그 앞으로 SPREAD 만큼 편다.
  // VU 시작 시각이 아니라 절대 시각을 기준으로 잡아야, VU 초기화가 느려도
  // 예열 창이 발사 시각을 침범하지 않는다.
  const frac = CROWD > 1 ? (__VU - 1) / (CROWD - 1) : 0;
  const warmupAt = data.fireAt - SETTLE_MS - SPREAD_MS + frac * SPREAD_MS;

  const toWarmup = warmupAt - Date.now();
  if (toWarmup > 0) sleep(toWarmup / 1000);

  // 락도 쓰기도 타지 않는 순수 조회. 핫슬롯이 아닌 배경 식당을 읽는다 —
  // 핫슬롯을 미리 읽으면 버퍼풀이 데워져서 회차마다 조건이 달라진다.
  const warm = http.get(
    `${env.baseUrl}/api/v1/time-table?restaurantId=${data.bgId}&date=${data.date}&tableStatus=EMPTY`,
    { headers: { Authorization: token }, timeout: '30s', tags: { name: 'warmup' } },
  );
  if (warm.status === 200) warmupOk.add(1);
  else warmupFail.add(1);

  // ── 2. 배리어 = await() ────────────────────────────────────────────────
  const toFire = data.fireAt - Date.now();
  if (toFire > 0) {
    sleep(toFire / 1000);
  } else {
    // 이 VU는 이미 발사 시각을 넘겨서 도착했다. 일제 발사에 끼지 못했다는 뜻이라
    // 회차 전체의 유효성을 의심해야 한다. SPREAD/SETTLE을 늘려야 한다.
    lateVus.add(1);
  }

  // ── 3. 일제 발사 ──────────────────────────────────────────────────────
  const t = Date.now();
  fireSkew.add(t - data.fireAt);

  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.hotId}`,
    JSON.stringify({ date: data.date, startTime: data.startTime }),
    {
      headers: { Authorization: token, 'Content-Type': 'application/json' },
      timeout: `${TIMEOUT_SEC}s`,
      tags: { name: 'booking' },
    },
  );

  const elapsed = Date.now() - data.fireAt;
  const kind = classify(res);

  // 답을 받은 사람만 '해소'에 넣는다. 타임아웃은 답을 못 받은 것이므로 제외하고
  // 따로 센다. 섞으면 "마지막 사람이 60초에 답을 받았다"처럼 읽혀 버린다.
  if (kind === 'success') {
    outSuccess.add(1);
    seatWon.add(elapsed);
    answered.add(elapsed);
  } else if (kind === 'soldout') {
    outSoldOut.add(1);
    answered.add(elapsed);
  } else if (kind === 'rejected') {
    outRejected.add(1);
    answered.add(elapsed);
  } else if (kind === 'error5xx') {
    out5xx.add(1);
    answered.add(elapsed);
  } else if (kind === 'timeout') {
    outTimeout.add(1);
  } else if (kind === 'dropped') {
    outDropped.add(1);
  } else if (kind === 'refused') {
    outRefused.add(1);
  } else {
    outOther.add(1);
    answered.add(elapsed);
  }
}

// ---------------------------------------------------------------------------
const cnt = (d, k) => (d.metrics[k] ? d.metrics[k].values.count : 0);
const tv = (d, k, f) => {
  const m = d.metrics[k];
  if (!m || m.values[f] === undefined) return 0;
  return Math.round(m.values[f] * 10) / 10;
};

export function handleSummary(data) {
  const skewMax = tv(data, 'fire_skew_ms', 'max');
  const late = cnt(data, 'late_vus');

  const success = cnt(data, 'out_success');
  const timeout = cnt(data, 'out_timeout');
  const dropped = cnt(data, 'out_dropped');
  const refused = cnt(data, 'out_refused');
  const wFail = cnt(data, 'warmup_fail');

  const report = {
    crowd: CROWD,
    seats: env.seatsPerSlot,

    // 이 회차를 결과로 쓸 수 있는가. 아니면 나머지 숫자는 읽으면 안 된다.
    validity: {
      fireSkewMaxMs: skewMax,
      fireSkewP95Ms: tv(data, 'fire_skew_ms', 'p(95)'),
      fireSkewAvgMs: tv(data, 'fire_skew_ms', 'avg'),
      lateVus: late,
      skewLimitMs: SKEW_MAX_MS,
      usable: skewMax < SKEW_MAX_MS && late === 0,
    },

    // 오픈 전: 페이지를 열 수 있었는가 (tomcat max-connections 벽)
    preOpen: {
      connected: cnt(data, 'warmup_ok'),
      failed: wFail,
    },

    // 오픈 후: 버튼을 눌렀을 때 무슨 일이 있었는가
    outcome: {
      seatWon: success,
      soldOut: cnt(data, 'out_soldout'),
      rejected: cnt(data, 'out_rejected'),
      error5xx: cnt(data, 'out_5xx'),
      noAnswer: timeout,
      dropped: dropped,
      unreachable: refused,
      other: cnt(data, 'out_other'),
    },

    timing: {
      soldOutSec: tv(data, 'seat_won_ms', 'max') / 1000,   // 마지막 좌석이 팔린 시각
      lastAnswerSec: tv(data, 'answered_ms', 'max') / 1000, // 마지막 사람이 답 받은 시각
      waitMs: {
        p50: tv(data, 'answered_ms', 'med'),
        p95: tv(data, 'answered_ms', 'p(95)'),
        p99: tv(data, 'answered_ms', 'p(99)'),
        max: tv(data, 'answered_ms', 'max'),
      },
    },
  };

  const pct = (n) => `${((n / CROWD) * 100).toFixed(1)}%`;
  const line =
    `\n[S1 단건 오픈런] 동시 ${CROWD}명 / 좌석 ${env.seatsPerSlot}석\n` +
    `  유효성 : 발사 스큐 max ${skewMax}ms (한계 ${SKEW_MAX_MS}ms), ` +
    `늦은 VU ${late} → ${report.validity.usable ? '사용 가능' : '★ 폐기 대상'}\n` +
    `  오픈 전: 페이지 못 연 사람 ${wFail} (${pct(wFail)})\n` +
    `  오픈 후: 좌석 ${success} / 매진거절 ${report.outcome.soldOut} / ` +
    `락거절 ${report.outcome.rejected} / 5xx ${report.outcome.error5xx}\n` +
    `           무응답 ${timeout} (${pct(timeout)}) / 튕김 ${dropped} (${pct(dropped)}) / ` +
    `접속불가 ${refused}\n` +
    `  시간   : 매진 ${report.timing.soldOutSec}s, 마지막 응답 ${report.timing.lastAnswerSec}s, ` +
    `대기 p95 ${report.timing.waitMs.p95}ms\n`;

  const out = { stdout: line };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
