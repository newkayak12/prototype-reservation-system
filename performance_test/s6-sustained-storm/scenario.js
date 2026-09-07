import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------------------------------------------------------------------------
// S6 — Sustained Storm.
//
// 질문 둘.
//   Part A (sustained) 용량 근처 부하를 10분 유지하면 10분째가 1분째와 같은가.
//   Part B (waves)     스파이크를 반복하면 웨이브마다 같은 성능이 나오는가.
//
// S2가 "언제 무너지나"를 쟀다면 여기는 "무너진 다음 어떻게 되나"다. 그래서 한 런의
// 총합 숫자로는 답이 안 나온다 — 총합은 앞부분과 뒷부분을 평균 내서 추세를 지운다.
// 이 시나리오의 산출물은 전부 시간축 위에 있다.
//
// 두 부분 모두 개방형이다. 폐쇄형은 서버가 느려지면 부하도 같이 줄어서, 밀린 상태가
// 다음 웨이브로 이월되는지를 애초에 관측할 수 없다.
//
// 태그를 두 개 쓰고, 둘의 시계가 다르다:
//   bucket  응답이 "도착한" 시각으로 매긴다.
//   wave    요청을 "발사한" 시각으로 매긴다.
// k6는 태그 서브메트릭을 threshold에 선언해야만 요약에 내보내므로(S3과 동일),
// 아래에서 버킷/웨이브마다 절대 실패하지 않는 threshold를 만들어 집계만 시킨다.
//
// 실행: k6 run -e PART=waves -e OUT=x.json scenario.js
// ---------------------------------------------------------------------------

const env = JSON.parse(open('../_lib/env.json'));
const tokens = new SharedArray('tokens', () => JSON.parse(open('../_lib/tokens.json')));

const PART = (__ENV.PART || 'sustained').toLowerCase();
const isWaves = PART === 'waves';

const RATE = Number(__ENV.RATE || 400);            // Part A 도착률 (S2 무릎의 80%)
const SUSTAIN_SEC = Number(__ENV.SUSTAIN_SEC || 600);

const WAVE_RATE = Number(__ENV.WAVE_RATE || 1500); // Part B 스파이크 도착률
const WAVE_SEC = Number(__ENV.WAVE_SEC || 30);
const IDLE_SEC = Number(__ENV.IDLE_SEC || 60);
const WAVES = Number(__ENV.WAVES || 5);

// gracefulStop. 부하를 끈 뒤에도 이만큼은 응답을 계속 받아 적는다.
// 여기서 끊으면 "부하를 껐는데 서버가 아직 응답을 내보내고 있다"는 Part B의 결정적
// 증거가 측정 창 밖으로 잘려 나간다.
const TAIL_SEC = Number(__ENV.TAIL_SEC || 60);

const MAX_VUS = Number(__ENV.MAX_VUS || 20000);
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || '30s';

const PERIOD = WAVE_SEC + IDLE_SEC;
const TOTAL_SEC = isWaves ? WAVES * PERIOD : SUSTAIN_SEC;
const PEAK_RATE = isWaves ? WAVE_RATE : RATE;
const PLANNED_REQUESTS = isWaves ? WAVE_RATE * WAVE_SEC * WAVES : RATE * SUSTAIN_SEC;

// 버킷 폭이 다른 이유: Part A는 "분당 추세"가 질문이라 60초면 충분하고, 60초 버킷이
// 순간 요동을 눌러 추세를 보기 쉽게 만든다. Part B를 60초로 자르면 30초 부하와
// 30초 휴지가 한 버킷에 섞여, 휴지 중 잔여 처리량이 부하와 구분되지 않는다.
const BUCKET_SEC = Number(__ENV.BUCKET_SEC || (isWaves ? 10 : 60));
const NBUCKETS = Math.ceil((TOTAL_SEC + TAIL_SEC) / BUCKET_SEC);

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
  thresholds[`booking_latency_ms{bucket:${b}}`] = ['max>=0'];
  thresholds[`booking_requests{bucket:${b}}`] = ['count>=0'];
  thresholds[`booking_success{bucket:${b}}`] = ['count>=0'];
  thresholds[`booking_timeout{bucket:${b}}`] = ['count>=0'];
}
if (isWaves) {
  for (let w = 1; w <= WAVES; w++) {
    thresholds[`booking_latency_ms{wave:${w}}`] = ['max>=0'];
    thresholds[`booking_requests{wave:${w}}`] = ['count>=0'];
    thresholds[`booking_success{wave:${w}}`] = ['count>=0'];
  }
}

// ramping-arrival-rate는 단계 사이를 선형 보간한다. 0초짜리 단계를 끼워 넣지 않으면
// 스파이크가 삼각파가 되어 "정각에 한꺼번에 몰린다"는 B2C 판매 개시 상황이 아니게 된다.
//
// 웨이브를 시나리오 5개(constant-arrival-rate + startTime)로 쪼개지 않는 이유는
// preAllocatedVUs가 시나리오마다 따로 잡혀 1500 req/s × 5면 VU를 만 단위로 미리
// 할당하게 되기 때문이다. 단일 시나리오면 VU 풀을 웨이브들이 돌려 쓴다.
function squareWaveStages() {
  const stages = [];
  for (let w = 0; w < WAVES; w++) {
    stages.push({ duration: '0s', target: WAVE_RATE });
    stages.push({ duration: `${WAVE_SEC}s`, target: WAVE_RATE });
    stages.push({ duration: '0s', target: 0 });
    stages.push({ duration: `${IDLE_SEC}s`, target: 0 });
  }
  return stages;
}

const scenario = isWaves
  ? {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      stages: squareWaveStages(),
      preAllocatedVUs: Math.min(MAX_VUS, Math.max(500, WAVE_RATE * 2)),
      maxVUs: MAX_VUS,
      gracefulStop: `${TAIL_SEC}s`,
    }
  : {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${SUSTAIN_SEC}s`,
      preAllocatedVUs: Math.min(MAX_VUS, Math.max(200, RATE * 2)),
      maxVUs: MAX_VUS,
      gracefulStop: `${TAIL_SEC}s`,
    };

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds,
  scenarios: { storm: scenario },
};

export function setup() {
  // 경합점 1개. 이 시나리오가 보려는 건 단일 락 키 위에서 시간이 흐를 때 무슨 일이
  // 생기는가이지, 경합점 수의 효과가 아니다(그건 S5).
  return {
    t0: Date.now(),
    restaurantId: env.points[0],
    date: env.date,
    startTime: env.startTime,
  };
}

const clampBucket = (sec) =>
  String(Math.min(NBUCKETS - 1, Math.max(0, Math.floor(sec / BUCKET_SEC))));

const waveOfSend = (sec) => String(Math.min(WAVES, Math.max(1, Math.floor(sec / PERIOD) + 1)));

export default function (data) {
  // 토큰 재사용. 서버에 사용자당 중복 예약 제한이 없어서 재사용해도 결과가 안 바뀐다.
  const token = tokens[__ITER % tokens.length];

  const sentSec = (Date.now() - data.t0) / 1000;

  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.restaurantId}`,
    JSON.stringify({ date: data.date, startTime: data.startTime }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: token },
      timeout: REQ_TIMEOUT,
      tags: { name: 'booking' },
    },
  );

  // 버킷은 응답이 도착한 시각으로 매긴다. 발사 시각으로 매기면 휴지 구간 버킷은
  // 발사가 0이라 정의상 항상 0건이 되고, "부하를 껐는데도 서버가 응답을 내보내고
  // 있는가"라는 Part B의 결정적 증거를 관측 자체가 불가능해진다.
  const tags = { bucket: clampBucket((Date.now() - data.t0) / 1000) };

  // 반대로 웨이브별 지연은 발사 시각으로 묶는다. 도착 시각으로 묶으면 웨이브 N에서
  // 가장 심하게 밀린 응답이 다음 휴지/웨이브로 넘어가 웨이브 N의 p95가 오히려 좋아진다.
  // 밀릴수록 좋아 보이는 지표로는 "웨이브가 갈수록 나빠지는가"를 물을 수 없다.
  if (isWaves) tags.wave = waveOfSend(sentSec);

  cRequests.add(1, tags);
  if (res.status === 200 || res.status === 201) {
    cSuccess.add(1, tags);
    latencySuccess.add(res.timings.duration);
  } else if (res.status === 409) {
    cSoldOut.add(1, tags);
  } else if (res.status === 0) {
    cTimeout.add(1, tags); // 서버 응답이 아니다 → 지연 분포에서 뺀다
  } else if (res.status >= 500) {
    cServerError.add(1, tags);
  } else {
    // 현재 서버는 ClientException 전체를 400 하나로 매핑한다 — 품절과 그 외가 뭉쳐 있다.
    cRejected.add(1, tags);
  }
  if (res.status !== 0) latency.add(res.timings.duration, tags);
}

const sub = (d, n) => (d.metrics[n] && d.metrics[n].values ? d.metrics[n].values : {});
const cnt = (d, n) => sub(d, n).count || 0;

// 최소제곱 기울기. 첫 버킷과 마지막 버킷만 비교하면 한쪽 버킷의 우연한 튐이 결론을
// 뒤집는다. 전 구간 기울기를 같이 봐야 "단조 증가"를 주장할 수 있다.
function slope(ys) {
  const n = ys.length;
  if (n < 2) return 0;
  let sx = 0, sy = 0, sxx = 0, sxy = 0;
  for (let i = 0; i < n; i++) { sx += i; sy += ys[i]; sxx += i * i; sxy += i * ys[i]; }
  const den = n * sxx - sx * sx;
  return den === 0 ? 0 : (n * sxy - sx * sy) / den;
}

function phaseOf(fromSec) {
  if (!isWaves) return fromSec < SUSTAIN_SEC ? { phase: 'load', wave: null } : { phase: 'tail', wave: null };
  const w = Math.floor(fromSec / PERIOD);
  if (w >= WAVES) return { phase: 'tail', wave: null };
  return { phase: fromSec - w * PERIOD < WAVE_SEC ? 'load' : 'idle', wave: w + 1 };
}

export function handleSummary(data) {
  const series = [];
  for (let b = 0; b < NBUCKETS; b++) {
    const lat = sub(data, `booking_latency_ms{bucket:${b}}`);
    const from = b * BUCKET_SEC;
    const req = cnt(data, `booking_requests{bucket:${b}}`);
    const ok = cnt(data, `booking_success{bucket:${b}}`);
    const ph = phaseOf(from);
    series.push({
      bucket: b,
      fromSec: from,
      toSec: from + BUCKET_SEC,
      phase: ph.phase,
      wave: ph.wave,
      requests: req,
      success: ok,
      timeout: cnt(data, `booking_timeout{bucket:${b}}`),
      // 완료 기준 처리량 — 이 버킷 안에 응답이 도착한 건수다.
      completedRps: req / BUCKET_SEC,
      successRps: ok / BUCKET_SEC,
      p50: lat['p(50)'] || 0,
      p95: lat['p(95)'] || 0,
      p99: lat['p(99)'] || 0,
      max: lat.max || 0,
    });
  }

  const requests = cnt(data, 'booking_requests');
  const success = cnt(data, 'booking_success');
  const dropped = cnt(data, 'dropped_iterations');
  const timeout = cnt(data, 'booking_timeout');
  const err5xx = cnt(data, 'booking_server_error_5xx');
  const lat = sub(data, 'booking_latency_ms');
  const latS = sub(data, 'booking_latency_success_ms');
  const elapsed =
    (data.state && data.state.testRunDurationMs ? data.state.testRunDurationMs : TOTAL_SEC * 1000) / 1000;

  const report = {
    part: PART,
    valid: requests > 0,
    targetRps: isWaves ? WAVE_RATE : RATE,
    totalSec: TOTAL_SEC,
    bucketSec: BUCKET_SEC,
    waveSec: isWaves ? WAVE_SEC : null,
    idleSec: isWaves ? IDLE_SEC : null,
    waveCount: isWaves ? WAVES : null,
    elapsedSeconds: elapsed,
    requests,
    attempted: requests + dropped,
    droppedIterations: dropped,
    // Part B에서 elapsed 기준 rps는 휴지 시간까지 분모에 넣으므로 "서버가 낼 수 있는
    // 처리량"이 아니다. 부하 구간만 본 값과 계획 대비 도달률을 같이 싣는다.
    achievedRps: requests / elapsed,
    loadWindowRps: isWaves ? requests / (WAVE_SEC * WAVES) : requests / SUSTAIN_SEC,
    goodputRps: success / elapsed,
    plannedRequests: PLANNED_REQUESTS,
    attainment: PLANNED_REQUESTS > 0 ? requests / PLANNED_REQUESTS : 0,
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

  let head;
  if (isWaves) {
    const waves = [];
    for (let w = 1; w <= WAVES; w++) {
      const wl = sub(data, `booking_latency_ms{wave:${w}}`);
      const wreq = cnt(data, `booking_requests{wave:${w}}`);
      const wok = cnt(data, `booking_success{wave:${w}}`);
      // 이 웨이브 뒤 휴지 구간에 "도착한" 응답. 발사는 이미 끝났으므로 여기 잡히는
      // 건 전부 밀려 있던 요청이다. 직전 웨이브에서 넘어온 것도 섞이지만, 그것 역시
      // 잔여이므로 구분할 필요가 없다.
      const idle = series.filter((s) => s.wave === w && s.phase === 'idle');
      const idleReq = idle.reduce((a, s) => a + s.requests, 0);
      const lastIdle = idle.length ? idle[idle.length - 1] : null;
      waves.push({
        wave: w,
        requests: wreq,
        success: wok,
        // 발사한 요청 중 응답을 받은 비율이 아니라, 계획 대비 완료 비율이다.
        completionRate: WAVE_RATE * WAVE_SEC > 0 ? wreq / (WAVE_RATE * WAVE_SEC) : 0,
        p50: wl['p(50)'] || 0,
        p95: wl['p(95)'] || 0,
        p99: wl['p(99)'] || 0,
        max: wl.max || 0,
        idleResidualRequests: idleReq,
        idleResidualRps: IDLE_SEC > 0 ? idleReq / IDLE_SEC : 0,
        // 다음 웨이브 직전 버킷에도 응답이 나오고 있으면, 다음 웨이브는 빈 상태가
        // 아니라 밀린 상태에서 시작한다는 직접 증거다.
        carryIntoNextWave: lastIdle ? lastIdle.requests : 0,
      });
    }
    const first = waves[0];
    const last = waves[waves.length - 1];
    report.waves = waves;
    report.waveDegradation = {
      p95First: first.p95,
      p95Last: last.p95,
      p95Ratio: first.p95 > 0 ? last.p95 / first.p95 : 0,
      p95SlopePerWave: slope(waves.map((x) => x.p95)),
      completionFirst: first.completionRate,
      completionLast: last.completionRate,
      idleResidualTotal: waves.reduce((a, x) => a + x.idleResidualRequests, 0),
      carryMax: Math.max(...waves.map((x) => x.carryIntoNextWave)),
    };
    head =
      `\n  웨이브 p95: 1번 ${first.p95.toFixed(0)}ms → ${WAVES}번 ${last.p95.toFixed(0)}ms ` +
      `(${report.waveDegradation.p95Ratio.toFixed(2)}배)\n` +
      `  휴지 구간 잔여 완료: ${report.waveDegradation.idleResidualTotal}건 ` +
      `(다음 웨이브 직전 최대 ${report.waveDegradation.carryMax}건)\n`;
  } else {
    const load = series.filter((s) => s.phase === 'load' && s.requests > 0);
    const first = load[0] || null;
    const last = load.length ? load[load.length - 1] : null;
    report.drift = {
      buckets: load.length,
      p99First: first ? first.p99 : 0,
      p99Last: last ? last.p99 : 0,
      p99Ratio: first && first.p99 > 0 ? last.p99 / first.p99 : 0,
      p99SlopePerBucket: slope(load.map((s) => s.p99)),
      rpsFirst: first ? first.completedRps : 0,
      rpsLast: last ? last.completedRps : 0,
      rpsRatio: first && first.completedRps > 0 ? last.completedRps / first.completedRps : 0,
      rpsSlopePerBucket: slope(load.map((s) => s.completedRps)),
    };
    head =
      `\n  p99: 첫 버킷 ${report.drift.p99First.toFixed(0)}ms → 마지막 버킷 ` +
      `${report.drift.p99Last.toFixed(0)}ms (${report.drift.p99Ratio.toFixed(2)}배, ` +
      `기울기 ${report.drift.p99SlopePerBucket.toFixed(1)}ms/버킷)\n` +
      `  처리량: ${report.drift.rpsFirst.toFixed(0)} → ${report.drift.rpsLast.toFixed(0)} req/s\n`;
  }

  const out = {
    stdout:
      head +
      `  전체 ${requests}건 | 달성 ${report.loadWindowRps.toFixed(0)} req/s (부하 구간 기준) | ` +
      `도달률 ${(report.attainment * 100).toFixed(0)}%\n` +
      `  p95 ${report.latencyMs.p95.toFixed(0)}ms | p99 ${report.latencyMs.p99.toFixed(0)}ms | ` +
      `timeout ${timeout} | 5xx ${err5xx} | dropped ${dropped}\n`,
  };
  if (__ENV.OUT) out[__ENV.OUT] = JSON.stringify(report, null, 2);
  return out;
}
