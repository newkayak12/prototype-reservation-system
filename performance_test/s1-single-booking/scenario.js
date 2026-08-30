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
// ── 대기열 (after 아키텍처) ───────────────────────────────────────────────
// after는 예약 앞에 대기열이 있고 서버가 이를 강제한다
// (CreateTimeTableOccupancyService.verifyAdmitted). 건너뛰고 booking을 직접 부르면
// 입장 자격 없음으로 즉시 거절되므로, 발사 후 흐름이 세 단계다:
//
//   진입(POST .../queue) → 폴링(GET .../queue/{ticketId}) → 예약(POST .../booking)
//
// 그래서 '마지막 응답'에 대기 시간이 포함된다. **그게 정확한 비교다** — 사용자에게
// 중요한 건 버튼을 누르고 표를 받기까지지 예약 API 하나의 지연이 아니다. 예약 호출
// 자체의 지연만 보려면 booking_ms 를 본다.
//
// before(대기열 없음)에는 SKIP_QUEUE=1 로 이 단계를 건너뛴다. run.sh가 라벨을 보고
// 자동으로 설정하므로, 두 워크트리가 같은 시나리오 파일을 쓴다.
//
// 실행: k6 run -e CROWD=3000 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const CROWD = Number(__ENV.CROWD || 1000);        // 동시 도착 인원
const TIMEOUT_SEC = Number(__ENV.TIMEOUT_SEC || 60);
const SETTLE_SEC = Number(__ENV.SETTLE_SEC || 20); // 예열 종료 → 발사. 앱이 가라앉을 시간

const SKIP_QUEUE = __ENV.SKIP_QUEUE === '1';

// 폴링 간격. **종속 변수가 아니라 설계 파라미터다.**
//
// 승격이 타이머가 아니라 요청 경로(진입/폴링)에서 일어나므로 이 값이 permit 회전 주기를
// 정한다: 회전 = 승격 → 다음 폴링에서 인지(간격/2) → 예약 → 반납.
// 따라서 처리량 상한 = admission-capacity / 회전 이고, 서버의 capacity와 짝으로 움직여야
// 한다. 한쪽만 바꾸면 아키텍처가 아니라 그 불일치를 재게 된다.
//
// 실제 배포에서 이 값은 곧 **CDN TTL**이다. 순번 조회는 엣지에서 캐시될 것이므로,
// 2로 두면 "TTL 2초 CDN을 앞에 세운 상태"를 인프라 없이 재현한 것이 된다.
const POLL_SEC = Number(__ENV.POLL_SEC || 0.5);

// 대기 예산. **런이 끝나지 않는 것을 막는 안전장치일 뿐이다.**
// 정상 상태에서는 포기가 0이어야 한다. 0이 아니면 예산이 짧은 게 아니라 승격이 막혔다는
// 신호이므로, 그 회차는 원인을 찾기 전까지 결과로 읽으면 안 된다.
const QUEUE_BUDGET_SEC = Number(__ENV.QUEUE_BUDGET_SEC || 60);

// 발번 선행 모드. 예열 창에서 미리 줄을 통과해 두고, 발사는 예약만 한다.
//
// admission-capacity를 크게 열어(예: 20000) **예약 경로만** 정면 비교할 때 쓴다.
// 진입을 발사에 섞으면 예약 도착이 진입 응답 지연만큼 흩어져서, before의
// "N명 일제 도착"과 같은 조건이 되지 않는다. 입장 TTL(300s)이 예열 창
// (최대 SPREAD+SETTLE ≈ 120s)보다 길어서 성립한다. capacity가 열려 있으면
// 진입 응답이 곧장 position 0 이라 폴링도 일어나지 않는다.
//
// 평소(capacity 150) 측정에는 켜면 안 된다 — 예열 창 동안 permit이 서서히
// 소진되어 발사 시점의 대기열 상태가 실제와 달라진다.
const QUEUE_PREPASS = __ENV.QUEUE_PREPASS === '1';

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

// ── 대기열 단계 (before에는 존재하지 않는 비용) ────────────────────────────
const qEnterFail = new Counter('q_enter_fail');     // 줄조차 못 섬
const qGiveUp = new Counter('q_give_up');           // 예산 내에 입장 못 함 = 막혔다는 신호
const qAdmitted = new Counter('q_admitted');
const qWait = new Trend('q_wait_ms', true);         // 진입 → 입장 허용

// 폴링 요청 수. **이걸 세지 않으면 after의 실부하가 통계에서 사라진다.**
// 예약만 세면 앱이 실제로 받은 요청의 일부만 잡힌다 — 폴링이 그 몇 배다.
// 실제 배포에서 이 몫은 CDN이 흡수하므로, "엣지로 넘길 수 있는 부하"를 재는 값이기도 하다.
const qPolls = new Counter('q_polls');
const bookingMs = new Trend('booking_ms', true);    // 예약 호출 자체의 지연 (대기 제외)

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

// 대기열 진입 → 입장 허용까지 폴링. 통과했으면 true.
//
// position 0 은 "이미 입장 허용됨"을 뜻한다(EnterWaitingQueue.ADMITTED_POSITION).
// 대기열이 비어 있고 정원이 남아 있으면 진입 응답이 그대로 0으로 오므로 폴링을 한 번도
// 하지 않는다 — 한산할 때 대기열이 순수 지연이 되지 않게 한 서버 쪽 처리
// (EnterWaitingQueueService.admitNow)가 여기서 그대로 드러난다.
function passQueue(data, headers, slotBody) {
  const queueUrl = `${env.baseUrl}/api/v1/time-table/booking/${data.hotId}/queue`;

  const enter = http.post(queueUrl, slotBody, {
    headers,
    timeout: `${TIMEOUT_SEC}s`,
    tags: { name: 'queue_enter' },
  });

  if (enter.status !== 201) {
    qEnterFail.add(1);
    // 줄조차 못 선 것도 사용자에게는 실패다. 발사 실패와 같은 잣대로 분류해 둔다.
    const kind = classify(enter);
    if (kind === 'timeout') outTimeout.add(1);
    else if (kind === 'dropped') outDropped.add(1);
    else if (kind === 'refused') outRefused.add(1);
    else if (kind === 'error5xx') out5xx.add(1);
    else outOther.add(1);
    return false;
  }

  const entered = JSON.parse(enter.body);
  const ticketId = entered.ticketId;
  let admitted = entered.position === 0;

  const waitStart = Date.now();
  const deadline = waitStart + QUEUE_BUDGET_SEC * 1000;
  while (!admitted && Date.now() < deadline) {
    sleep(POLL_SEC);

    const poll = http.get(
      `${queueUrl}/${ticketId}?date=${data.date}&startTime=${data.startTime}`,
      { headers, timeout: `${TIMEOUT_SEC}s`, tags: { name: 'queue_poll' } },
    );
    qPolls.add(1);

    if (poll.status === 200 && JSON.parse(poll.body).status === 'ADMITTED') admitted = true;
  }

  if (!admitted) {
    qGiveUp.add(1);
    return false;
  }

  qWait.add(Date.now() - waitStart);
  qAdmitted.add(1);
  return true;
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

  const slotBody = JSON.stringify({ date: data.date, startTime: data.startTime });
  const headers = { Authorization: token, 'Content-Type': 'application/json' };

  // 발번 선행: 예열 창에서 줄까지 통과해 둔다. 실패는 passQueue 안에서 사유별로
  // 집계되므로, 여기서는 발사에 끼지 않고 빠지기만 한다.
  if (!SKIP_QUEUE && QUEUE_PREPASS && !passQueue(data, headers, slotBody)) return;

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

  // 대기열을 통과하지 못하면 예약 단계에 가지 못한다. 거절도 실패도 아닌 "아직 줄에 있음"
  // 이므로 별도 버킷으로 빠지고 아래 분류에 섞이지 않는다.
  // (발번 선행 모드에서는 이미 예열 창에서 통과했으므로 건너뛴다)
  if (!SKIP_QUEUE && !QUEUE_PREPASS && !passQueue(data, headers, slotBody)) return;

  const bookAt = Date.now();
  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.hotId}`,
    slotBody,
    { headers, timeout: `${TIMEOUT_SEC}s`, tags: { name: 'booking' } },
  );
  if (res.status !== 0) bookingMs.add(Date.now() - bookAt);

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

  const polls = cnt(data, 'q_polls');
  const gaveUp = cnt(data, 'q_give_up');
  // 예열 GET은 오픈 전 트래픽이라 뺀다. 발사 이후 앱이 받은 요청만 센다:
  // 진입(전원) + 폴링 + 예약(입장한 사람만).
  const originReqs = SKIP_QUEUE ? CROWD : CROWD + polls + cnt(data, 'q_admitted');

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
      // 예약 호출 자체의 지연. 위 waitMs는 대기를 포함하므로, 둘을 같이 봐야
      // "느린 게 줄 때문인지 처리 때문인지"가 갈린다.
      bookingMs: {
        p50: tv(data, 'booking_ms', 'med'),
        p95: tv(data, 'booking_ms', 'p(95)'),
        max: tv(data, 'booking_ms', 'max'),
      },
    },

    // before에는 없는 단계. skipped=true면 before를 잰 것이다.
    queue: {
      skipped: SKIP_QUEUE,
      prepass: QUEUE_PREPASS, // true면 발번을 예열 창에서 끝냈다 — 발사는 예약뿐
      admitted: cnt(data, 'q_admitted'),
      enterFailed: cnt(data, 'q_enter_fail'),
      gaveUp: gaveUp,          // 0이어야 정상. 0이 아니면 승격이 막힌 것이다
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

  const pct = (n) => `${((n / CROWD) * 100).toFixed(1)}%`;
  const queueLine = SKIP_QUEUE
    ? ''
    : `  대기열 : 입장 ${report.queue.admitted} / 진입실패 ${report.queue.enterFailed} / ` +
      `포기 ${gaveUp}${gaveUp > 0 ? ' ★막힘' : ''}, 대기 p95 ${report.queue.waitMs.p95}ms\n` +
      `           폴링 ${polls}건 → 앱이 받은 요청 ${originReqs}건 ` +
      `(폴링 비중 ${((polls / originReqs) * 100).toFixed(0)}%)\n`;
  const line =
    `\n[S1 단건 오픈런] 동시 ${CROWD}명 / 좌석 ${env.seatsPerSlot}석` +
    `${SKIP_QUEUE ? ' / 대기열 없음' : ''}\n` +
    `  유효성 : 발사 스큐 max ${skewMax}ms (한계 ${SKEW_MAX_MS}ms), ` +
    `늦은 VU ${late} → ${report.validity.usable ? '사용 가능' : '★ 폐기 대상'}\n` +
    `  오픈 전: 페이지 못 연 사람 ${wFail} (${pct(wFail)})\n` +
    queueLine +
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
