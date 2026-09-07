import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------------------------------------------------------------------------
// S3 — Blast Radius.
//
// 질문: 식당 하나에 트래픽이 몰릴 때, 그 식당과 아무 상관 없는 조회 트래픽까지
//       같이 죽는가.
//
// 이게 B2C 아키텍처 논의의 핵심이다. 처리량 상한 자체는 "그 상품이 늦게 팔린다"로
// 끝나지만, 폭발 반경이 넓으면 "굿즈 하나 때문에 사이트 전체가 죽는다"가 된다.
//
// 메커니즘 가설: before의 예약은 분산락 대기(최대 2분)를 Tomcat 워커 스레드를
// 점유한 채로 한다. 워커는 200개고 큐는 공용이다. 핫슬롯 요청이 워커를 모두
// 물고 있으면, 락과 무관한 GET조차 큐에서 순서를 기다린다(head-of-line blocking).
//   → 배경 GET의 p95가 핫스팟 스파이크 구간에서 치솟으면 가설 지지.
//   → 배경 GET이 평탄하면 반증(격리되어 있다는 뜻).
//
// 두 시나리오가 동시에 돈다:
//   background  K6_BG 식당 GET, 낮고 일정한 도착률. 락을 전혀 안 탄다. 피해자 역할.
//   hotspot     K6_HOT_0001 예약 POST, SPIKE_AT초에 시작해 SPIKE_SEC초간 폭격.
//
// 시간 구간별 p95를 보려고 배경 요청에 bucket 태그를 단다. k6는 태그 서브메트릭을
// threshold에 선언해야만 요약에 내보내므로, 아래에서 버킷마다 threshold를 만든다.
//
// 실행: k6 run -e HOT_RATE=1500 -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const BG_RATE = Number(__ENV.BG_RATE || 50);      // 배경 조회 도착률 (req/s)
const HOT_RATE = Number(__ENV.HOT_RATE || 1500);  // 핫스팟 예약 도착률 (req/s)
const TOTAL_SEC = Number(__ENV.TOTAL_SEC || 120); // 전체 관측 시간
const SPIKE_AT = Number(__ENV.SPIKE_AT || 30);    // 스파이크 시작 시각
const SPIKE_SEC = Number(__ENV.SPIKE_SEC || 60);  // 스파이크 지속
const BUCKET_SEC = Number(__ENV.BUCKET_SEC || 10);

const BG_TIMEOUT = __ENV.BG_TIMEOUT || '10s';   // 조회는 원래 빨라야 한다. 짧게 끊는다.
const HOT_TIMEOUT = __ENV.HOT_TIMEOUT || '30s';

const NBUCKETS = Math.ceil(TOTAL_SEC / BUCKET_SEC);

const bgRequests = new Counter('bg_requests');
const bgOk = new Counter('bg_ok');
const bgTimeout = new Counter('bg_timeout');
const bgError = new Counter('bg_error');
const bgLatency = new Trend('bg_latency_ms', true);

const hotRequests = new Counter('hot_requests');
const hotSuccess = new Counter('hot_success');
const hotTimeout = new Counter('hot_timeout');
const hotError5xx = new Counter('hot_error_5xx');
const hotLatency = new Trend('hot_latency_ms', true);

// 버킷별 서브메트릭을 요약에 등장시키려면 threshold 선언이 필요하다.
// 절대 실패하지 않는 조건을 넣어 "집계만" 시킨다.
const thresholds = {};
for (let b = 0; b < NBUCKETS; b++) {
  thresholds[`bg_latency_ms{bucket:${b}}`] = ['max>=0'];
  thresholds[`bg_requests{bucket:${b}}`] = ['count>=0'];
  thresholds[`bg_ok{bucket:${b}}`] = ['count>=0'];
}

const scenarios = {
  background: {
    executor: 'constant-arrival-rate',
    exec: 'background',
    rate: BG_RATE,
    timeUnit: '1s',
    duration: `${TOTAL_SEC}s`,
    preAllocatedVUs: Math.max(100, BG_RATE * 4),
    // 배경 VU 상한을 넉넉히 준다. 여기가 모자라면 "서버가 느려서 조회가 밀렸다"가
    // 아니라 "발생기가 조회를 못 쐈다"가 되어 결론이 뒤집힌다.
    maxVUs: Math.max(2000, BG_RATE * 60),
    gracefulStop: '30s',
  },
};

// HOT_RATE=0 은 대조군: 핫스팟 없이 배경 조회만 돌려 평상시 기준선을 잡는다.
// 이 런의 p95가 다른 런의 "평상 구간" p95와 맞아야, 폭발 반경 배수를 믿을 수 있다.
if (HOT_RATE > 0) {
  scenarios.hotspot = {
    executor: 'constant-arrival-rate',
    exec: 'hotspot',
    rate: HOT_RATE,
    timeUnit: '1s',
    startTime: `${SPIKE_AT}s`,
    duration: `${SPIKE_SEC}s`,
    preAllocatedVUs: Math.max(500, HOT_RATE * 2),
    maxVUs: Number(__ENV.HOT_MAX_VUS || 20000),
    gracefulStop: '30s',
  };
}

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds,
  scenarios,
};

// 테스트 시작 기준 경과 초 → 버킷 번호.
// 기준 시각은 setup()에서 한 번 찍은 t0다. 시나리오별 startTime을 쓰면 background와
// hotspot이 서로 다른 원점을 갖게 되어 두 시계열을 겹쳐 볼 수 없다.
function bucketOf(t0) {
  const sec = (Date.now() - t0) / 1000;
  return String(Math.min(NBUCKETS - 1, Math.max(0, Math.floor(sec / BUCKET_SEC))));
}

export function setup() {
  return {
    t0: Date.now(),
    hotId: env.points[0],
    bgId: env.background.restaurantId,
    date: env.date,
    startTime: env.startTime,
  };
}

export function background(data) {
  const bucket = bucketOf(data.t0);
  // 조회 API도 인증이 필요하다. 토큰 없이 부르면 JwtFilter가 NPE를 내며 500이 되고,
  // 그러면 "핫스팟 때문에 조회가 실패했다"가 아니라 "인증이 없어서 실패했다"를 재게 된다.
  const token = tokens[__ITER % tokens.length];

  // 락도 트랜잭션 쓰기도 없는 순수 조회. 핫스팟과 공유하는 건 Tomcat 워커 풀과 DB 커넥션뿐.
  const res = http.get(
    `${env.baseUrl}/api/v1/time-table?restaurantId=${data.bgId}&date=${data.date}&tableStatus=EMPTY`,
    {
      headers: { Authorization: token },
      timeout: BG_TIMEOUT,
      tags: { name: 'bg_find', bucket },
    },
  );

  bgRequests.add(1, { bucket });
  if (res.status === 200) {
    bgOk.add(1, { bucket });
    bgLatency.add(res.timings.duration, { bucket });
  } else if (res.status === 0) {
    bgTimeout.add(1, { bucket });
  } else {
    bgError.add(1, { bucket });
    bgLatency.add(res.timings.duration, { bucket });
  }
}

export function hotspot(data) {
  const token = tokens[__ITER % tokens.length];

  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.hotId}`,
    JSON.stringify({ date: data.date, startTime: data.startTime }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: token },
      timeout: HOT_TIMEOUT,
      tags: { name: 'hot_booking' },
    },
  );

  hotRequests.add(1);
  if (res.status === 200 || res.status === 201) hotSuccess.add(1);
  else if (res.status === 0) hotTimeout.add(1);
  else if (res.status >= 500) hotError5xx.add(1);
  if (res.status !== 0) hotLatency.add(res.timings.duration);
}

const sub = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values : {});
const cnt = (d, n) => sub(d, n).count || 0;

export function handleSummary(data) {
  // 시간축: 버킷마다 배경 조회의 p95/성공률. 스파이크 구간(SPIKE_AT ~ SPIKE_AT+SPIKE_SEC)과
  // 그 바깥을 비교하는 게 이 시나리오의 전부다.
  // 국면은 셋이다. 스파이크가 끝났다고 바로 평상으로 돌아가지 않기 때문이다.
  //   pre   스파이크 이전 = 진짜 기준선
  //   spike 스파이크 구간
  //   post  스파이크 종료 후 = 회복 구간
  // "스파이크가 아닌 구간"을 통째로 기준선으로 쓰면, 아직 회복 못 한 post가 섞여
  // 기준선이 부풀고 증폭 배수가 1에 가깝게 나온다(= 피해를 못 본 것처럼 보인다).
  const spikeEnd = SPIKE_AT + SPIKE_SEC;
  const phaseOf = (from) => (from < SPIKE_AT ? 'pre' : from < spikeEnd ? 'spike' : 'post');

  const series = [];
  for (let b = 0; b < NBUCKETS; b++) {
    const lat = sub(data, `bg_latency_ms{bucket:${b}}`);
    const req = cnt(data, `bg_requests{bucket:${b}}`);
    const ok = cnt(data, `bg_ok{bucket:${b}}`);
    const from = b * BUCKET_SEC;
    series.push({
      bucket: b,
      fromSec: from,
      toSec: from + BUCKET_SEC,
      phase: phaseOf(from),
      inSpike: from >= SPIKE_AT && from < spikeEnd,
      requests: req,
      ok,
      okRate: req > 0 ? ok / req : null,
      // 성공 표본이 없으면 지연은 "빠른" 게 아니라 "모름"이다. 0으로 두면 평균이 좋아진다.
      hasLatency: ok > 0,
      p50: ok > 0 ? lat['p(50)'] || 0 : null,
      p95: ok > 0 ? lat['p(95)'] || 0 : null,
      p99: ok > 0 ? lat['p(99)'] || 0 : null,
      max: ok > 0 ? lat.max || 0 : null,
    });
  }

  const of = (ph) => series.filter((s) => s.phase === ph && s.requests > 0);
  const avg = (a, f) => (a.length ? a.reduce((x, s) => x + f(s), 0) / a.length : 0);
  // 지연 평균은 표본이 있는 버킷만으로 낸다.
  const avgLat = (a) => {
    const w = a.filter((s) => s.hasLatency);
    return w.length ? avg(w, (s) => s.p95) : null;
  };

  const pre = of('pre');
  const spike = of('spike');
  const post = of('post');

  const bgP95Base = avgLat(pre);
  const bgP95Spike = avgLat(spike);
  const bgP95Post = avgLat(post);

  const bgReq = cnt(data, 'bg_requests');
  const bgOkC = cnt(data, 'bg_ok');
  const hotReq = cnt(data, 'hot_requests');
  const hotLat = sub(data, 'hot_latency_ms');

  const report = {
    bgRate: BG_RATE,
    hotRate: HOT_RATE,
    totalSec: TOTAL_SEC,
    spikeAt: SPIKE_AT,
    spikeSec: SPIKE_SEC,
    bucketSec: BUCKET_SEC,
    // 대조군(HOT_RATE=0)은 핫스팟 요청이 0건인 게 정상이다.
    valid: bgReq > 0 && (HOT_RATE === 0 || hotReq > 0),
    control: HOT_RATE === 0,

    background: {
      requests: bgReq,
      ok: bgOkC,
      okRate: bgReq > 0 ? bgOkC / bgReq : 0,
      timeout: cnt(data, 'bg_timeout'),
      error: cnt(data, 'bg_error'),
      dropped: cnt(data, 'dropped_iterations'), // 시나리오 합산 (해석 주의)
      p95Baseline: bgP95Base,
      p95Spike: bgP95Spike,
      p95Post: bgP95Post,
      // 이 배수 하나가 결론이다. 1에 가까우면 격리, 크면 폭발 반경이 넓다.
      p95Amplification: bgP95Base && bgP95Spike ? bgP95Spike / bgP95Base : null,
      // 스파이크가 끝난 뒤에도 기준선으로 돌아오지 않으면, 피해는 스파이크보다 오래 간다.
      p95Recovery: bgP95Base && bgP95Post ? bgP95Post / bgP95Base : null,
      okRateBaseline: avg(pre, (s) => s.okRate || 0),
      okRateSpike: avg(spike, (s) => s.okRate || 0),
      okRatePost: avg(post, (s) => s.okRate || 0),
      // 성공률이 이 배수보다 중요할 수 있다. 지연 배수는 응답을 받은 요청만 세지만,
      // 성공률은 아예 응답을 못 받은 요청까지 포함한다.
      okDropped: avg(pre, (s) => s.okRate || 0) - avg(spike, (s) => s.okRate || 0),
    },
    hotspot: {
      requests: hotReq,
      success: cnt(data, 'hot_success'),
      timeout: cnt(data, 'hot_timeout'),
      error5xx: cnt(data, 'hot_error_5xx'),
      p95: hotLat['p(95)'] || 0,
      p99: hotLat['p(99)'] || 0,
    },
    series,
  };

  const ms = (v) => (v === null ? '표본없음' : `${v.toFixed(0)}ms`);
  const x = (v) => (v === null ? '—' : `${v.toFixed(1)}배`);
  const pct = (v) => `${(v * 100).toFixed(1)}%`;
  const out = {
    stdout:
      `\n  배경 GET p95: 평상 ${ms(bgP95Base)} → 스파이크 ${ms(bgP95Spike)} ` +
      `(${x(report.background.p95Amplification)}) → 회복 ${ms(bgP95Post)} ` +
      `(${x(report.background.p95Recovery)})\n` +
      `  배경 성공률: 평상 ${pct(report.background.okRateBaseline)} → ` +
      `스파이크 ${pct(report.background.okRateSpike)} → 회복 ${pct(report.background.okRatePost)}\n` +
      `  핫스팟: ${hotReq}건, 성공 ${report.hotspot.success}, ` +
      `timeout ${report.hotspot.timeout}, 5xx ${report.hotspot.error5xx}\n`,
  };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
