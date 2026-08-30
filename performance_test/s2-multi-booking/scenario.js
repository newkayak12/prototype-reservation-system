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
// ── 대기열 (after 아키텍처) ───────────────────────────────────────────────
// after는 예약 앞에 대기열이 있고 서버가 강제한다. 대기열 키는 **슬롯 단위**라
// (WaitingQueueSlot = restaurantId + date + startTime) 3슬롯을 잡으려면 줄을 3개 서야 한다.
// 이 자체가 S2가 드러내려는 것과 맞물린다 — 다건 예약에 걸친 경계가 없다는 사실이
// 대기열에서도 똑같이 반복된다.
//
// 그래서 흐름이 이렇게 된다:
//   진입 3건 동시(batch) → 입장한 슬롯은 **그 자리에서 즉시** 예약 →
//   남은 줄만 폴링, 입장할 때마다 또 즉시 예약
//
// ★ "셋을 다 받고 나서 예약"이 아니다. 1차 측정(2026-08-30)을 그걸로 날렸다.
//   permit은 "지금 예약 중인 사람 수"라는 계약이고 예약을 마쳐야 반납된다
//   (ReleaseAdmissionRedisAdapter). 입장해 놓고 다른 줄을 기다리면 permit이
//   lease(300s)까지 묶여 회전이 멈춘다 — capacity 150 × 3슬롯이 전부
//   "k6에서 sleep 중인 사람"으로 채워졌고, 1,000명부터 뒷줄 전원이 60초 예산을
//   소진했다(전부 포기 399~4,930명). 시스템이 아니라 부하 생성기가 만든 병목이다.
//
//   모아 쏘기로 지키려던 "세 락 동시 요구"도 대기열 뒤에서는 애초에 성립하지 않는다.
//   세 줄의 입장 시각이 서로 다르므로, 입장을 모아서 쏘는 건 동시 도착의 복원이
//   아니라 인위적 재동기화다. before(SKIP_QUEUE=1)는 대기열이 없으니 지금도
//   3건을 batch로 동시에 쏜다 — 그쪽에서는 관측 대상이 실재한다.
//
// 예산 안에 일부만 입장하면 **입장한 것만 예약한다.** 전부 포기시키면 부분 성공이
// 과소 집계되고, 안 된 슬롯까지 쏘면 입장 자격 없음 거절이 매진 거절에 섞인다.
// 대신 못 들어간 슬롯 수를 따로 세서, 요청 수가 조용히 줄어드는 일이 없게 한다.
//
// before(대기열 없음)에는 SKIP_QUEUE=1 로 이 단계를 건너뛴다. run.sh가 라벨을 보고
// 자동으로 설정하므로, 두 워크트리가 같은 시나리오 파일을 쓴다.
//
// 실행: k6 run -e CROWD=3000 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const CROWD = Number(__ENV.CROWD || 1000);
const TIMEOUT_SEC = Number(__ENV.TIMEOUT_SEC || 60);
const SETTLE_SEC = Number(__ENV.SETTLE_SEC || 20);

const SKIP_QUEUE = __ENV.SKIP_QUEUE === '1';
// 폴링 간격 = 실제 배포의 CDN TTL이자 permit 회전 주기. 서버 admission-capacity와
// 짝으로 움직인다. 자세한 근거는 S1 scenario.js 주석 참조.
const POLL_SEC = Number(__ENV.POLL_SEC || 0.5);
// 런이 끝나지 않는 것을 막는 안전장치. 정상이면 포기 0이어야 한다.
const QUEUE_BUDGET_SEC = Number(__ENV.QUEUE_BUDGET_SEC || 60);
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

// ── 대기열 단계 (before에는 존재하지 않는 비용) ────────────────────────────
const qEnterFail = new Counter('q_enter_fail');       // 줄에 서지도 못한 슬롯 수
const qAdmitted = new Counter('q_admitted');          // 입장한 슬롯 수
const qNotAdmitted = new Counter('q_not_admitted');   // 예산 내 입장 실패한 슬롯 수
const qGiveUpAll = new Counter('q_give_up_all');      // 3슬롯 전부 못 들어간 사람
const qPartialAdmit = new Counter('q_partial_admit'); // 일부만 들어간 사람 = 승격이 막혔다는 신호
const qWait = new Trend('q_wait_ms', true);           // 진입 → 필요한 줄을 다 통과
// 폴링 요청 수. 이걸 세지 않으면 after의 실부하가 통계에서 사라진다.
const qPolls = new Counter('q_polls');
const bookingMs = new Trend('booking_ms', true);      // 예약 batch 자체의 지연 (대기 제외)

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

// 예약 batch를 쏘고 요청 단위 분류를 집계한다. 획득한 좌석 수를 돌려준다.
function fireBookings(data, headers, slots) {
  const bookAt = Date.now();
  const responses = http.batch(
    slots.map((startTime) => [
      'POST',
      `${env.baseUrl}/api/v1/time-table/booking/${data.hotId}`,
      JSON.stringify({ date: data.date, startTime }),
      { headers, timeout: `${TIMEOUT_SEC}s`, tags: { name: 'booking' } },
    ]),
  );
  bookingMs.add(Date.now() - bookAt);

  let won = 0;
  for (const res of responses) {
    const kind = classify(res);
    if (kind === 'success') { reqSuccess.add(1); won++; }
    else if (kind === 'soldout') reqSoldOut.add(1);
    else if (kind === 'rejected') reqRejected.add(1);
    else if (kind === 'error5xx') req5xx.add(1);
    else if (kind === 'timeout') reqTimeout.add(1);
    else if (kind === 'dropped') reqDropped.add(1);
    else reqRefused.add(1);
  }
  return won;
}

// 슬롯 3개의 대기열을 통과시키되, **입장한 슬롯은 그 자리에서 즉시 예약한다.**
//
// permit은 "지금 예약 중"의 표시라 예약을 마쳐야 반납된다. 다른 줄의 입장을
// 기다리며 쥐고 있으면 회전이 멈춘다(파일 머리 주석 참조). 예약을 미루는 순간
// 부하 생성기가 시스템을 굶기는 가짜 병목이 된다.
//
// 진입과 폴링은 여전히 batch다 — "같은 사람이 세 줄에 동시에 선다"는 유지한다.
function queueAndBook(data, headers) {
  const queueUrl = `${env.baseUrl}/api/v1/time-table/booking/${data.hotId}/queue`;

  const enters = http.batch(
    data.slots.map((startTime) => [
      'POST',
      queueUrl,
      JSON.stringify({ date: data.date, startTime }),
      { headers, timeout: `${TIMEOUT_SEC}s`, tags: { name: 'queue_enter' } },
    ]),
  );

  let toBook = [];    // 입장했지만 아직 예약을 안 쏜 슬롯의 startTime
  const pending = []; // { startTime, ticketId }

  enters.forEach((res, i) => {
    if (res.status !== 201) {
      qEnterFail.add(1);
      // 줄에 서지도 못한 것도 요청 단위 실패다. 예약 응답과 같은 잣대로 분류해야
      // 커넥션 벽이 대기열 단계에서 나타났을 때 표에서 사라지지 않는다.
      const kind = classify(res);
      if (kind === 'timeout') reqTimeout.add(1);
      else if (kind === 'dropped') reqDropped.add(1);
      else if (kind === 'refused') reqRefused.add(1);
      else if (kind === 'error5xx') req5xx.add(1);
      else reqRejected.add(1);
      return;
    }
    const body = JSON.parse(res.body);
    // position 0 = 이미 입장 허용 (EnterWaitingQueue.ADMITTED_POSITION).
    // 한산하면 진입 응답이 그대로 0으로 와서 폴링을 한 번도 하지 않는다.
    if (body.position === 0) toBook.push(data.slots[i]);
    else pending.push({ startTime: data.slots[i], ticketId: body.ticketId });
  });

  const waitStart = Date.now();
  const deadline = waitStart + QUEUE_BUDGET_SEC * 1000;
  let won = 0;
  let booked = 0;

  for (;;) {
    // 입장분부터 소화한다. 폴링보다 먼저 와야 permit이 최단 시간에 반납된다.
    if (toBook.length > 0) {
      for (let i = 0; i < toBook.length; i++) {
        qAdmitted.add(1);
        qWait.add(Date.now() - waitStart); // 슬롯 단위: 진입 → 그 줄 통과까지
      }
      won += fireBookings(data, headers, toBook);
      booked += toBook.length;
      toBook = [];
    }
    if (pending.length === 0 || Date.now() >= deadline) break;

    sleep(POLL_SEC);
    const polls = http.batch(
      pending.map((p) => [
        'GET',
        `${queueUrl}/${p.ticketId}?date=${data.date}&startTime=${p.startTime}`,
        null,
        { headers, timeout: `${TIMEOUT_SEC}s`, tags: { name: 'queue_poll' } },
      ]),
    );
    qPolls.add(polls.length);

    // 뒤에서부터 지운다 — 앞에서 splice하면 인덱스가 밀려 다음 항목을 건너뛴다.
    for (let i = polls.length - 1; i >= 0; i--) {
      const res = polls[i];
      if (res.status === 200 && JSON.parse(res.body).status === 'ADMITTED') {
        toBook.push(pending[i].startTime);
        pending.splice(i, 1);
      }
    }
  }

  qNotAdmitted.add(pending.length);
  if (pending.length > 0) {
    if (booked === 0) qGiveUpAll.add(1);
    else qPartialAdmit.add(1);
  }
  return { won, booked };
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

  const headers = { Authorization: token, 'Content-Type': 'application/json' };

  let won;
  let booked;
  if (SKIP_QUEUE) {
    // 대기열이 없으면(before) 3건을 batch로 동시에 쏜다 — 같은 사람이 세 락을
    // 동시에 요구하는 상황의 재현. 이쪽은 관측 대상이 실재한다.
    won = fireBookings(data, headers, data.slots);
    booked = data.slots.length;
  } else {
    // 대기열이 있으면 입장한 슬롯부터 즉시 예약한다. 이유는 queueAndBook 주석 참조.
    const r = queueAndBook(data, headers);
    won = r.won;
    booked = r.booked;
  }

  const elapsed = Date.now() - data.fireAt;
  answered.add(elapsed);
  if (won > 0) seatWon.add(elapsed);

  if (booked === 0) {
    gotNone.add(1);
    return;
  }

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

  const polls = cnt(data, 'q_polls');
  // 예열 GET은 오픈 전 트래픽이라 뺀다. 발사 이후 앱이 받은 요청만 센다:
  // 진입(전원 × 3슬롯) + 폴링 + 예약(입장한 슬롯만).
  const originReqs = SKIP_QUEUE
    ? CROWD * 3
    : CROWD * 3 + polls + cnt(data, 'q_admitted');

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
      // 예약 batch 자체의 지연. 위 waitMs는 대기를 포함하므로, 둘을 같이 봐야
      // 느림의 출처가 줄인지 처리인지 갈린다.
      bookingMs: {
        p50: tv(data, 'booking_ms', 'med'),
        p95: tv(data, 'booking_ms', 'p(95)'),
        max: tv(data, 'booking_ms', 'max'),
      },
    },

    // before에는 없는 단계. skipped=true면 before를 잰 것이다.
    queue: {
      skipped: SKIP_QUEUE,
      admittedSlots: cnt(data, 'q_admitted'),
      notAdmittedSlots: cnt(data, 'q_not_admitted'),
      enterFailedSlots: cnt(data, 'q_enter_fail'),
      gaveUpAll: cnt(data, 'q_give_up_all'),       // 0이어야 정상
      partialAdmit: cnt(data, 'q_partial_admit'),  // 0이어야 정상
      waitMs: {
        p50: tv(data, 'q_wait_ms', 'med'),
        p95: tv(data, 'q_wait_ms', 'p(95)'),
        max: tv(data, 'q_wait_ms', 'max'),
      },
      pollSec: POLL_SEC,
      polls: polls,
      originRequests: originReqs,
    },
  };

  const q = report.queue;
  const blocked = q.gaveUpAll + q.partialAdmit;
  const queueLine = SKIP_QUEUE
    ? ''
    : `  대기열 : 입장 ${q.admittedSlots}슬롯 / 진입실패 ${q.enterFailedSlots}슬롯 / ` +
      `미입장 ${q.notAdmittedSlots}슬롯` +
      `${blocked > 0 ? ` ★막힘(전부포기 ${q.gaveUpAll}명, 일부만 ${q.partialAdmit}명)` : ''}\n` +
      `           대기 p95 ${q.waitMs.p95}ms, 폴링 ${polls}건 → 앱이 받은 요청 ${originReqs}건 ` +
      `(폴링 비중 ${((polls / originReqs) * 100).toFixed(0)}%)\n`;
  const line =
    `\n[S2 다건 오픈런] 동시 ${CROWD}명 × 3슬롯 / 슬롯당 ${env.seatsPerSlot}석` +
    `${SKIP_QUEUE ? ' / 대기열 없음' : ''}\n` +
    `  유효성 : 발사 스큐 max ${skewMax}ms, 늦은 VU ${late} → ` +
    `${report.validity.usable ? '사용 가능' : '★ 폐기 대상'}\n` +
    queueLine +
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
