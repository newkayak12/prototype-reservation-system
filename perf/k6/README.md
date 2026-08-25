# k6 부하테스트 도구 (Phase 0 베이스라인 / Phase 5 재측정 공용)

`docs/perf/baseline-report.md`의 원본 측정 도구. 개선 전(`chore/performance-test`)과 개선 후
(`chore-performance-test-after`) 양쪽에서 **동일한 스크립트**로 동일 시나리오를 돌려야 두 결과가
비교 가능하므로, 이 디렉터리는 두 워크트리 모두에 동일하게 존재한다.

## 설계 원칙

- **인증 우회 없음**: k6가 mock 토큰을 만들어 쓰지 않는다. `seed.sh`가 실제
  `POST /api/v1/user/sign-up`으로 사용자를 만들고, `booking.js`의 `setup()`이 실제
  `PUT /api/v1/user/sign-in`으로 토큰을 발급받는다. JWT 만료(5분)보다 짧게 램프를 설계한 것도 이 때문 —
  런 도중 토큰이 만료돼 401이 "실패"로 잘못 집계되는 걸 막는다.
- **경합이 실제로 생기는 시드**: 좌석(=timetable row)을 30개로 제한하고 유저 풀은 300명 — "티켓팅"처럼
  대부분의 요청이 품절/충돌을 맞는 분포를 의도적으로 만든다. 좌석 수를 넉넉하게 주면 병목이 안 보인다.
- **정착 시간(settle time) 측정**: k6가 응답을 받은 시점 ≠ 시스템이 실제로 처리를 끝낸 시점. 동기 구조
  (Phase 0)에서는 둘이 거의 같지만, Redis 대기열 + Kafka 비동기 파이프라인(Phase 1~4)이 들어가면
  HTTP 응답은 빨리 와도 최종 DB 반영은 뒤늦게 끝난다 — 이 차이를 `wait-settle.sh`로 별도 측정한다.
- **매회 독립적인 정합성 검증**: 10번 반복 각각 재시딩하고, 매회 `verify-overbooking.sql`로 좌석 수를
  넘는 이중예약이 없었는지 확인한다 — "빨라졌다"만으론 부족하고 "정합성도 지켰다"를 같이 증명해야
  포트폴리오로서 의미가 있다.

## 구성 요소

| 파일 | 역할 |
|---|---|
| `seed.sh` | 매 회 실행 전 호출. `K6_PERF_RESTAURANT` 이름의 레스토랑 1개 + 좌석 30개(raw SQL, 멱등 DELETE→INSERT)를 시딩하고, `env.json`(baseUrl/restaurantId/date/…)과 실제 회원가입으로 만든 `users.json`(300명, 고정 비밀번호)을 씀. |
| `scenarios/booking.js` | k6 본 시나리오. `setup()`에서 `users.json`을 배치 로그인해 토큰을 받고, VU마다 `POST /api/v1/time-table/booking/{restaurantId}`를 호출. `ramping-vus`로 100→300→600→1000→1500→2000까지 계단식으로 올려 포화점을 찾는다. 성공/품절/충돌/기타에러를 커스텀 메트릭으로 분리 집계. |
| `run.sh` | `seed.sh` → `k6 run` → `wait-settle.sh` → `verify-overbooking.sql`을 10회(`RUNS` env로 조절) 반복하고 결과를 `results/`에 저장. `./run.sh <scenario-label>` (예: `baseline`, `redesigned`)로 호출. |
| `lib/wait-settle.sh` | k6 종료 직후 실행. `timetable_occupancy`의 OCCUPIED 건수를 0.5초 간격으로 폴링해서 4회 연속 변화가 없으면(최대 60초) 멈추고 `{"settleSeconds": ..., "finalOccupiedCount": ...}`를 출력. |
| `verify-overbooking.sql` | 시드한 좌석 수 대비 실제 OCCUPIED 수(오버부킹 여부)와, 같은 timetable에 OCCUPIED가 2건 이상 붙은 직접적 이중예약 사례를 찾는 두 개의 SELECT. |
| `lib/env.json`, `lib/users.json` | `seed.sh`가 매회 갱신하는 산출물 (gitignore). |
| `results/` | 각 run의 k6 summary JSON/log, settle 결과, 오버부킹 검증 결과 (gitignore). |

## 사용법

```bash
# 사전 조건: docker-compose로 MySQL/Redis/Kafka 기동, 앱은
# SPRING_PROFILES_ACTIVE=temporary SPRING_DOCKER_COMPOSE_ENABLED=false \
# SPRING_JPA_HIBERNATE_DDL_AUTO=none SERVER_PORT=8081 로 bootRun 중이어야 함.

RUNS=10 ./perf/k6/run.sh baseline      # Phase 0 (개선 전)
RUNS=10 ./perf/k6/run.sh redesigned    # Phase 5 (개선 후, chore-performance-test-after 워크트리에서)
```

결과는 `perf/k6/results/<label>-<n>.json`(k6 summary), `<label>-<n>.log`, `<label>-<n>-settle.json`,
`<label>-<n>-overbooking.txt`로 쌓인다. `docs/perf/baseline-report.md` / `baseline-vs-redesign.md`는
이 산출물들을 집계해서 사람이 읽을 수 있게 정리한 문서다.

## 측정 항목과 Phase 0에서 확인된 한계

- **HTTP 레벨**: p50/p95/p99, 상태코드별 카운트(성공/품절/충돌/5xx) — k6 자체 출력.
- **트래픽 포화점**: `booking.js`의 계단식 VU 램프 구간별로 에러율/충돌률이 급격히 꺾이는 지점.
- **정착 시간**: `wait-settle.sh` 출력. Phase 0(동기 구조)에서는 거의 0초에 수렴할 것으로 예상 — 이 값이
  유의미해지는 건 Phase 1~4 이후 (Redis 대기열 + Kafka 비동기 발행이 들어가면서부터).
- **오버부킹 여부**: `verify-overbooking.sql`.
- **알려진 스코프 제외**: `reservation` 테이블(하류 감사 레코드) 카운트는 Phase 0에서 항상 0으로 나온다
  — outbox 프로듀서가 발행하는 토픽명(`TIME_TABLE_OCCUPIED`)과 컨슈머가 구독하는 하드코딩된 토픽명
  (`time-table-occupancy`)이 애초에 다르기 때문 (기존 결함, `.claude/plans/performance-redesign-plan.md`
  참고). Phase 0의 정합성 검증 대상은 `timetable_occupancy`(좌석 점유)이며, 이 토픽명 통일은 Phase 3
  스코프다.
