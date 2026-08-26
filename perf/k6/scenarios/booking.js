import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

const env = JSON.parse(open('../lib/env.json'));

// ramping-vus 스테이지의 target VU 목록. 요청 시점의 실제 활성 VU 수를 이 중 가장 가까운 값으로 스냅해
// vus 태그로 남긴다 - 나중에 lib/analyze-vu-stages.py가 이 태그로 그룹핑해 VU 단계별 처리량/레이턴시를 뽑는다.
const STAGE_VU_TARGETS = [100, 300, 600, 1000, 1500, 2000];

function nearestStageTarget(activeVus) {
  return STAGE_VU_TARGETS.reduce((closest, target) =>
    Math.abs(target - activeVus) < Math.abs(closest - activeVus) ? target : closest,
  );
}

const bookingSuccess = new Counter('booking_success');
const bookingSoldOut = new Counter('booking_sold_out');
const bookingConflict = new Counter('booking_conflict');
const bookingUnexpectedError = new Counter('booking_unexpected_error');
const loginDuration = new Trend('login_duration', true);

const users = new SharedArray('users', function () {
  return JSON.parse(open('../lib/users.json'));
});

// Staged ramp to find the saturation point ("한 번에 받을 수 있는 양"), kept short
// so JWTs issued once in setup() (5-minute expiry, see V1_1 user table / JWTProperties) stay valid
// for the whole run.
export const options = {
  setupTimeout: '180s',
  scenarios: {
    booking_ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 100 },
        { duration: '20s', target: 100 },
        { duration: '15s', target: 300 },
        { duration: '20s', target: 300 },
        { duration: '15s', target: 600 },
        { duration: '20s', target: 600 },
        { duration: '15s', target: 1000 },
        { duration: '20s', target: 1000 },
        { duration: '15s', target: 1500 },
        { duration: '20s', target: 1500 },
        { duration: '15s', target: 2000 },
        { duration: '20s', target: 2000 },
        { duration: '10s', target: 0 },
      ],
    },
  },
};

export function setup() {
  const start = Date.now();
  const tokens = [];
  const batchSize = 50;
  for (let i = 0; i < users.length; i += batchSize) {
    const chunk = users.slice(i, i + batchSize);
    const requests = chunk.map((u) => ({
      method: 'PUT',
      url: `${env.baseUrl}/api/v1/user/sign-in`,
      body: JSON.stringify({ loginId: u.loginId, password: u.password }),
      params: { headers: { 'Content-Type': 'application/json' } },
    }));
    const responses = http.batch(requests);
    for (const res of responses) {
      if (res.status === 200) {
        const body = JSON.parse(res.body);
        tokens.push(body.accessToken);
      }
    }
  }
  loginDuration.add(Date.now() - start);

  if (tokens.length === 0) {
    throw new Error('setup() failed to obtain any access tokens - check perf/k6/seed.sh ran successfully');
  }

  return { tokens, restaurantId: env.restaurantId, date: env.date, startTime: env.startTime };
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];
  const payload = JSON.stringify({ date: data.date, startTime: data.startTime });
  const vuStage = nearestStageTarget(exec.instance.vusActive);
  const res = http.post(
    `${env.baseUrl}/api/v1/time-table/booking/${data.restaurantId}`,
    payload,
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: token,
      },
      tags: { name: 'booking', vu_stage: String(vuStage) },
    },
  );

  if (res.status === 201 || res.status === 200) {
    bookingSuccess.add(1);
  } else if (res.status === 409 || res.status === 400) {
    bookingSoldOut.add(1);
  } else if (res.status === 423 || res.status === 429) {
    bookingConflict.add(1);
  } else {
    bookingUnexpectedError.add(1);
  }

  check(res, {
    'status is not 5xx': (r) => r.status < 500,
  });
}
