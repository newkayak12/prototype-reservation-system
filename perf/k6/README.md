# k6 부하테스트 도구 (인기 좌석 티켓팅 버스트)

아키텍처 변경 **전(`before`) / 후(`after`)** 를 같은 스크립트로 재서 비교하기 위한 측정 도구.
결과는 `performance_test/<label>/` 아래에 쌓인다.

## 무엇을 재는가

좌석 30석짜리 인기 시간대 하나를 놓고, **VU 레벨마다 좌석을 리셋한 뒤** 그 수만큼의 유저가
**동시에 1번씩** 예약을 시도하는 버스트를 발사한다. VU를 100 → 2000까지 올려가며
같은 측정을 반복하고, 각 레벨에서 TPS / 처리율 / 처리 속도를 기록한다.

```
for VU in 100 300 600 1000 1500 2000:
  for r in 1..10:
    좌석 30석 리셋  →  VU명이 동시 발사  →  전원 응답 완료  →  정착 대기  →  오버부킹 검증
```

### 왜 VU 레벨마다 좌석을 리셋해야 하는가

이전 설계는 좌석을 런 시작 시 **한 번만** 시딩하고 `ramping-vus`로 100→2000을 한 런에서
계단식으로 올렸다. 그러면 VU 100 구간 초반 1초 안에 30석이 전부 소진되고, **남은 200초 동안의
수만~수십만 건은 전부 `loadBookableTimeTables()`가 빈 리스트를 반환하는 조기 리턴 경로**가 된다.

즉 개선 전/후를 비교해도 비교되는 건 "예약이 몰릴 때의 성공 처리량"이 아니라 "이미 매진된
좌석에 대한 거절 응답 속도"였다. VU 600부터 성공이 0인 것도 시스템이 무너져서가 아니라
설계상 그럴 수밖에 없는 값이라 포화점이라고 부를 수 없었다.

레벨마다 좌석을 30석으로 되돌려야 각 레벨이 **동일 조건(빈 좌석 30석)** 에서 비교된다.

## 설계 원칙

- **인증 우회 없음**: k6가 mock 토큰을 만들지 않는다. `seed.sh`가 실제 `POST /api/v1/user/sign-up`으로
  유저를 만들고, `booking.js`의 `setup()`이 실제 `PUT /api/v1/user/sign-in`으로 토큰을 발급받는다.
- **VU 1개 = 유저 1명**: 토큰을 돌려쓰지 않는다. 티켓팅에서 경쟁하는 주체는 커넥션이 아니라 사람이다.
  유저 풀이 최대 VU 레벨보다 작으면 `setup()`이 실행을 거부한다 (`ALLOW_TOKEN_REUSE=1`로 강제 가능).
- **타임아웃을 성공으로 세지 않는다**: 서버측 대기 상한이 k6 기본 타임아웃(60s)보다 길다
  (`@DistributedLock` waitTime 2분, 세마포어 대기 5분). 60초로 끊으면 실제 레이턴시가 아니라
  **타임아웃 절단선**을 재게 되므로 요청 타임아웃을 180초로 늘리고, 타임아웃(status 0)은
  레이턴시 분포에서 빼고 별도 버킷으로 센다.
- **매회 독립적인 정합성 검증**: 매 버스트마다 재시딩하고 `verify-overbooking.sql`로 좌석 수를
  넘는 이중예약이 없었는지 확인한다. "빨라졌다"만으론 부족하고 "정합성도 지켰다"를 같이 증명해야 한다.
- **레스토랑 UUID를 매번 새로 뽑는다**: Redis 세마포어/분산락 키가 `restaurantId` 기반이라 같은 ID를
  재사용하면 이전 버스트의 세마포어 permit(TTL 10분)이 남아 다음 측정을 오염시킨다.
- **정착 시간(settle time) 분리 측정**: k6가 응답을 받은 시점 ≠ 시스템이 처리를 끝낸 시점. 동기
  구조에서는 둘이 거의 같지만, 대기열 + 비동기 파이프라인이 들어가면 HTTP 응답은 빨라도 최종 DB
  반영은 뒤늦게 끝난다 — 이 차이를 `wait-settle.sh`로 따로 잰다.

## 지표 정의

| 지표 | 정의 | 계산 |
|---|---|---|
| **매진 시간** | 버스트 발사부터 마지막 좌석이 팔릴 때까지 | `max(time_to_success_ms)` |
| **TPS** | 실제 예약 성공 처리율 | 성공 수 / 매진 시간 |
| **해소 시간** | 마지막 요청이 성공/거절 응답을 받을 때까지 | `max(time_to_resolve_ms)` |
| **처리율** | 시스템이 소화한 req/s | 전체 요청 / 해소 시간 |
| **처리 속도** | 응답 레이턴시 분포 | p50/p90/p95/p99 (타임아웃 제외) |
| **정착 시간** | HTTP 응답 이후 DB 점유가 안정될 때까지 | `wait-settle.sh` |
| **정합성** | OCCUPIED ≤ 시드 좌석 수 && 한 timetable에 OCCUPIED 1건 이하 | `verify-overbooking.sql` |

## 구성 요소

| 파일 | 역할 |
|---|---|
| `seed.sh` | 매 버스트 전 호출. 좌석 30개를 DELETE→INSERT로 리셋하고 새 `restaurantId`로 `env.json`을 쓴다. 유저 풀은 `users.json`에 이미 `POOL_SIZE`명 이상 있으면 회원가입을 건너뛴다(`FORCE_USERS=1`로 강제). |
| `scenarios/booking.js` | 버스트 시나리오 1개 = VU 레벨 1개. `per-vu-iterations`로 VU당 1회씩 동시 발사하고, `handleSummary()`가 위 지표를 계산해 `OUT` 경로에 JSON으로 쓴다. |
| `run.sh` | VU 레벨 × 반복 회차 전체를 도는 스윕. 매 회 `seed.sh` → `k6 run` → `wait-settle.sh` → 오버부킹 검증을 수행하고 마지막에 `aggregate.py`로 집계한다. |
| `lib/aggregate.py` | 회차별 JSON을 VU 레벨로 묶어 **중앙값** 표를 만들고 `summary.md` / `summary.json`을 쓴다. 첫 회차는 JIT 워밍업·커넥션 풀 초기화가 섞여 튀므로 단일 회차가 아니라 중앙값을 쓴다. |
| `lib/wait-settle.sh` | k6 종료 직후 `timetable_occupancy`의 OCCUPIED 건수를 0.5초 간격으로 폴링해 4회 연속 변화가 없으면 멈추고 `{"settleSeconds":…, "finalOccupiedCount":…}`를 출력. |
| `verify-overbooking.sql` | 시드 좌석 수 대비 OCCUPIED 수(오버부킹)와, 같은 timetable에 OCCUPIED가 2건 이상 붙은 직접적 이중예약 사례를 찾는 두 개의 SELECT. |
| `lib/env.json`, `lib/users.json` | `seed.sh` 산출물 (gitignore). |

## 사용법

```bash
# 사전 조건: docker-compose로 MySQL/Redis/Kafka 기동, 앱은
# SPRING_PROFILES_ACTIVE=temporary SPRING_DOCKER_COMPOSE_ENABLED=false \
# SPRING_JPA_HIBERNATE_DDL_AUTO=none SERVER_PORT=8081 로 bootRun 중이어야 함.

./perf/k6/run.sh before     # 아키텍처 변경 전
./perf/k6/run.sh after      # 변경 후

# 빠른 확인 (레벨/반복 축소)
REPEATS=3 VU_LEVELS="100 600" ./perf/k6/run.sh before

# 단일 버스트만 수동 실행
./perf/k6/seed.sh
k6 run -e VUS=600 -e OUT=/tmp/x.json perf/k6/scenarios/booking.js
```

> `k6`는 시스템 환경변수를 `__ENV`로 넘기지 않는다. 반드시 `-e KEY=VALUE`로 전달할 것 —
> `VUS=600 k6 run …`으로 쓰면 스크립트가 조용히 기본값 100으로 돈다.

첫 실행은 유저 2000명 회원가입 때문에 시간이 걸리고, 이후 실행은 `users.json`을 재사용한다.

## 결과물

```
performance_test/<label>/
├── summary.md        ← VU 레벨별 집계표 (커밋 대상)
├── summary.json      ← 같은 내용의 기계 판독용 (커밋 대상)
└── raw/              ← 회차별 원본 (gitignore)
    ├── vu0100-r01.json           k6 handleSummary 산출물
    ├── vu0100-r01.log            k6 콘솔 출력
    ├── vu0100-r01-seed.log       시딩 로그
    ├── vu0100-r01-settle.json    정착 시간
    ├── vu0100-r01-integrity.txt  시드좌석 / OCCUPIED / 중복점유 timetable 수
    └── vu0100-r01-overbooking.txt  verify-overbooking.sql 원본 출력
```

## 알려진 한계

- **실패 사유가 HTTP 레벨에서 구분되지 않는다.** `RestControllerExceptionHandler`가
  `ClientException` 전체를 400 하나로 매핑해서, 품절(`AllTheSeatsAreAlreadyOccupied`)과 세마포어
  획득 실패(`AllTheThingsAreAlreadyOccupied`)가 같은 400으로 나온다. `booking.js`는 409/423/429
  버킷을 미리 열어 뒀으므로, 예외 핸들러를 세분화하면 집계표의 해당 칸이 그대로 채워진다.
- **부하 발생기와 SUT가 같은 머신에 있다.** VU 2000 구간에서 k6가 애플리케이션·MySQL·Redis와
  CPU를 다투므로, 그 구간의 포화는 서버가 아니라 측정 환경의 한계일 수 있다. 절대값보다
  **before/after 상대 비교**에 무게를 둘 것 (양쪽을 같은 환경에서 재는 한 비교는 유효하다).
- **`reservation` 테이블(하류 감사 레코드)은 항상 0**이다. outbox 프로듀서가 발행하는 토픽명
  (`TIME_TABLE_OCCUPIED`)과 컨슈머가 구독하는 하드코딩된 토픽명(`time-table-occupancy`)이 달라서다
  (기존 결함). 정합성 검증 대상은 `timetable_occupancy`(좌석 점유)다.
- **레이트리미터는 제거된 상태**다. `CreateTimeTableOccupancyService`에 걸려 있던
  `@RateLimiter`(1000 req/s, 대기 3초)가 병목 측정을 가리고 그 거절마저 400으로 섞여 들어와서,
  측정 대상에서 뺐다. before/after 양쪽 모두 레이트리미터 없는 상태를 비교한다.
