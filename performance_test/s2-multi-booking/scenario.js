import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------------------------------------------------------------------------
// S2 — 다건 오픈런 (한 사람이 여러 슬롯을 잡는다)
//
// S1과 부하 모형은 같다(유한 인구, 시각 배리어, 일제 발사). 다른 건 하나뿐이다:
// **한 사람이 슬롯 3개를 한꺼번에 잡으려 한다.**
//
// 이게 왜 별도 시나리오인가 — 처리량을 다시 재려는 게 아니다.
// 단건에서는 원리적으로 나타날 수 없는 실패 모드를 보려는 것이다.
//
//   1. 부분 성공. 3건 중 2건만 되면 시스템은 뭘 하는가?
//      "18시 19시는 됐고 20시는 실패" 는 사용자에게 예약이 된 것도 안 된 것도 아니다.
//      현재 코드에는 이걸 되돌리는 경로가 없다 — execute()가 슬롯 단위라
//      여러 슬롯에 걸친 트랜잭션 경계가 애초에 존재하지 않는다.
//      → 부분 성공자 수가 이 시나리오의 핵심 관측값이다.
//
//   2. 락 키가 3개로 갈린다. 단건은 슬롯 하나에 완전 직렬이지만, 다건은 같은 사람이
//      서로 다른 락 3개를 동시에 요구한다. 획득 순서가 고정되어 있지 않으면
//      교착이 가능한 구조인지 드러난다.
//
// ── 왜 순차가 아니라 동시(batch)인가 ──────────────────────────────────────
// 순차로 쏘면 2번째 요청은 1번째 응답을 받은 뒤에야 나간다. S1에서 그 응답이
// 최대 27초 걸렸으므로, 슬롯 2·3은 일제 도착이 아니라 27초에 걸쳐 흩어진
// 완전히 다른 부하가 된다. 그러면 "동시에 몰렸을 때"를 재는 게 아니게 된다.
//
// http.batch 로 3건을 한 번에 내보내면 세 슬롯 모두 일제 도착이 유지되고,
// 같은 사용자가 세 락을 동시에 요구하는 상황도 그대로 재현된다.
//
// 실행: k6 run -e CROWD=3000 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const CROWD = Number(__ENV.CROWD || 1000);
const TIMEOUT_SEC = Number(__ENV.TIMEOUT_SEC || 60);
const SETTLE_SEC = Number(__ENV.SETTLE_SEC || 20);
const SPREAD_SEC = Number(__ENV.SPREAD_SEC || Math.max(30, Math.ceil(CROWD / 100)));
const SKEW_MAX_MS = Number(__ENV.SKEW_MAX_MS || 1000);

const SPREAD_MS = SPREAD_SEC * 1000;
const SETTLE_MS = SETTLE_SEC * 1000;

const fireSkew = new Trend('fire_skew_ms', true);
const lateVus = new Counter('late_vus');
const warmupFail = new Counter('warmup_fail');
const warmupOk = new Counter('warmup_ok');

// ★ 이 시나리오의 핵심. 한 사람이 몇 건을 얻었는가.
const gotAll = new Counter('got_all');          // 3/3 — 온전한 예약
const gotPartial = new Counter('got_partial');  // 1~2건 — 반쪽 예약
const gotNone = new Counter('got_none');        // 0건 — 깨끗한 실패
const partialSeats = new Counter('partial_seats'); // 부분 성공자가 붙잡은 좌석 총합

// 요청 단위 결과 (슬롯 3개 × 인원)
const reqSuccess = new Counter('req_success');
const reqSoldOut = new Counter('req_soldout');
const reqRejected = new Counter('req_rejected');
const req5xx = new Counter('req_5xx');
const reqTimeout = new Counter('req_timeout');
const reqDropped = new Counter('req_dropped');
const reqRefused = new Counter('req_refused');

const answered = new Trend('answered_ms', true);   // 3건이 전부 끝난 시각
const seatWon = new Trend('seat_won_ms', true);

export const options = {
  scenarios: {
    openrun: {
      executor: 'per-vu-iterations',
      vus: CROWD,
      iterations: 1,
      maxDuration: `${SPREAD_SEC + SETTLE_SEC + TIMEOUT_SEC + 180}s`,
      gracefulStop: '10s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    fire_skew_ms: [`max<${SKEW_MAX_MS}`],
    late_vus: ['count<1'],
  },
  noConnectionReuse: false,
  noVUConnectionReuse: false,
};

export function setup() {
  if (!env.slots || env.slots.length < 3) {
    throw new Error(`슬롯이 ${(env.slots || []).length}개다. SLOTS=3 으로 재시딩할 것`);
  }
  return {
    fireAt: Date.now() + SPREAD_MS + SETTLE_MS,
    hotId: env.points[0],
    bgId: env.background.restaurantId,
    date: env.date,
    slots: env.slots.slice(0, 3).map((s) => s.startTime),
  };
}

// S1과 동일한 분류. 'dial'을 가장 먼저 본다 — 문자열에 timeout이 들어있어서
// 응답 타임아웃과 합쳐지기 쉬운데, 합치면 max-connections 벽이 사라진다.
function classify(res) {
  if (res.status === 0) {
    const e = String(res.error || '').toLowerCase();
    if (e.includes('dial')) return 'refused';
    if (e.includes('deadline') || e.includes('timeout')) return 'timeout';
    if (e.includes('refused')) return 'refused';
    if (e.includes('eof') || e.includes('reset') || e.includes('broken pipe')) return 'dropped';
    return 'dropped';
  }
  if (res.status === 200 || res.status === 201) return 'success';
  if (res.status === 400 || res.status === 409) return 'soldout';
  if (res.status === 423 || res.status === 429) return 'rejected';
  if (res.status >= 500) return 'error5xx';
  return 'soldout';
}

export default function (data) {
  const token = tokens[(__VU - 1) % tokens.length];

  // ── 1. 예열 (S1과 동일) ────────────────────────────────────────────────
  const frac = CROWD > 1 ? (__VU - 1) / (CROWD - 1) : 0;
  const warmupAt = data.fireAt - SETTLE_MS - SPREAD_MS + frac * SPREAD_MS;
  const toWarmup = warmupAt - Date.now();
  if (toWarmup > 0) sleep(toWarmup / 1000);

  const warm = http.get(
    `${env.baseUrl}/api/v1/time-table?restaurantId=${data.bgId}&date=${data.date}&tableStatus=EMPTY`,
    { headers: { Authorization: token }, timeout: '30s', tags: { name: 'warmup' } },
  );
  if (warm.status === 200) warmupOk.add(1);
  else warmupFail.add(1);

  // ── 2. 배리어 ─────────────────────────────────────────────────────────
  const toFire = data.fireAt - Date.now();
  if (toFire > 0) sleep(toFire / 1000);
  else lateVus.add(1);

  // ── 3. 슬롯 3개 동시 발사 ──────────────────────────────────────────────
  fireSkew.add(Date.now() - data.fireAt);

  const common = {
    headers: { Authorization: token, 'Content-Type': 'application/json' },
    timeout: `${TIMEOUT_SEC}s`,
    tags: { name: 'booking' },
  };
  const requests = data.slots.map((startTime) => [
    'POST',
    `${env.baseUrl}/api/v1/time-table/booking/${data.hotId}`,
    JSON.stringify({ date: data.date, startTime }),
    common,
  ]);

  const responses = http.batch(requests);
  const elapsed = Date.now() - data.fireAt;

  let won = 0;
  for (const res of responses) {
    const kind = classify(res);
    if (kind === 'success') { reqSuccess.add(1); won++; }
    else if (kind === 'soldout') reqSoldOut.add(1);
    else if (kind === 'rejected') reqRejected.add(1);
    else if (kind === 'error5xx') req5xx.add(1);
    else if (kind === 'timeout') reqTimeout.add(1);
    else if (kind === 'dropped') reqDropped.add(1);
    else if (kind === 'refused') reqRefused.add(1);
  }

  answered.add(elapsed);
  if (won > 0) seatWon.add(elapsed);

  // 사람 단위 판정. 이게 이 시나리오가 존재하는 이유다.
  if (won === data.slots.length) gotAll.add(1);
  else if (won > 0) { gotPartial.add(1); partialSeats.add(won); }
  else gotNone.add(1);
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
  const all = cnt(data, 'got_all');
  const partial = cnt(data, 'got_partial');
  const none = cnt(data, 'got_none');

  const report = {
    crowd: CROWD,
    slots: 3,
    seatsPerSlot: env.seatsPerSlot,

    validity: {
      fireSkewMaxMs: skewMax,
      fireSkewP95Ms: tv(data, 'fire_skew_ms', 'p(95)'),
      fireSkewAvgMs: tv(data, 'fire_skew_ms', 'avg'),
      lateVus: late,
      skewLimitMs: SKEW_MAX_MS,
      usable: skewMax < SKEW_MAX_MS && late === 0,
    },

    preOpen: { connected: cnt(data, 'warmup_ok'), failed: cnt(data, 'warmup_fail') },

    // ★ 사람 단위 — 3건을 원했는데 몇 건을 받았나
    perPerson: {
      gotAll: all,
      gotPartial: partial,
      gotNone: none,
      partialSeatsHeld: cnt(data, 'partial_seats'),
    },

    // 요청 단위 — 슬롯 3개 × 인원
    perRequest: {
      success: cnt(data, 'req_success'),
      soldOut: cnt(data, 'req_soldout'),
      rejected: cnt(data, 'req_rejected'),
      error5xx: cnt(data, 'req_5xx'),
      noAnswer: cnt(data, 'req_timeout'),
      dropped: cnt(data, 'req_dropped'),
      unreachable: cnt(data, 'req_refused'),
    },

    timing: {
      lastAnswerSec: tv(data, 'answered_ms', 'max') / 1000,
      waitMs: {
        p50: tv(data, 'answered_ms', 'med'),
        p95: tv(data, 'answered_ms', 'p(95)'),
        p99: tv(data, 'answered_ms', 'p(99)'),
        max: tv(data, 'answered_ms', 'max'),
      },
    },
  };

  const line =
    `\n[S2 다건 오픈런] 동시 ${CROWD}명 × 3슬롯 / 슬롯당 ${env.seatsPerSlot}석\n` +
    `  유효성 : 발사 스큐 max ${skewMax}ms, 늦은 VU ${late} → ` +
    `${report.validity.usable ? '사용 가능' : '★ 폐기 대상'}\n` +
    `  사람   : 3건 전부 ${all} / ★부분성공 ${partial} (좌석 ${report.perPerson.partialSeatsHeld}개 점유) / ` +
    `전부실패 ${none}\n` +
    `  요청   : 성공 ${report.perRequest.success} / 매진 ${report.perRequest.soldOut} / ` +
    `무응답 ${report.perRequest.noAnswer} / 튕김 ${report.perRequest.dropped} / ` +
    `접속불가 ${report.perRequest.unreachable}\n` +
    `  시간   : 마지막 응답 ${report.timing.lastAnswerSec}s, p95 ${report.timing.waitMs.p95}ms\n`;

  const out = { stdout: line };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
