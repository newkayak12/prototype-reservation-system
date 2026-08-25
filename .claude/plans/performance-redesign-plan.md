# 예약 부하테스트 & 아키텍처 개선 (포트폴리오)

## Context

현재 좌석 예약(점유) 쓰기 경로는 `CreateTimeTableOccupancyService.execute()` (application-module)에
`@RateLimiter` + `@DistributedLock(FAIR_LOCK)` + `@Transactional` 이 겹겹이 걸려 있고, 그 안에서
Redisson **RSemaphore**로 좌석 수만큼 permit을 발급한 뒤 JPA로 `TimeTableOccupancyEntity`를 저장한다.
즉 같은 (restaurantId+date+startTime) 슬롯에 대한 모든 요청이 **하나의 분산락을 순차적으로 통과**해야
하며, 그 안에 DB 왕복까지 포함되어 있어 동시성이 몰리면 락 대기열이 그대로 커넥션/스레드 점유로 번진다.
이게 바로 mnet 투표 사례가 지적하는 "여기가 병목입니다" 지점과 동일한 패턴이다.

목표: Redis 대기열 → Redis 원자적 처리/중복 방지 → Kafka 파티셔닝 순서보장 → DB row lock 최종 방어선,
4단계 아이디어를 이 코드베이스에 이식하고, k6로 **개선 전/후**를 동일 시나리오·동일 VU 램프로 10회씩
측정해 포트폴리오용 비교 자료를 만든다. 사용자가 확정한 대로 ① 대기열은 "예약 페이지 진입 전" 세마포어
역할 + 순번 폴링 방식으로 만든다.

**참고 사례 정정**: 처음엔 mnet 투표 사례(집계 시스템)를 참고했는데, 사용자가 제시한 두 번째 사례
(한정 굿즈 구매 — 재고 1000개, 동시접속 30만, 1인 1개, 가결제 후 5분 내 미결제 시 자동 취소, 초과판매
없음)가 좌석 예약 도메인과 훨씬 더 가깝다 (좌석=한정 재고, 1인 1슬롯=1인 1개 구매, "잠깐 자리를 잡아두고
확정 못 하면 풀리는" 흐름이 실제 예약 UX와 자연스럽게 대응됨). 이 문서의 Phase 1/4는 이 두 번째 사례의
설계를 따른다:
- 대기열 순번의 권위(authority)는 **Kafka Offset**이다 — Redis는 그 순서를 빠르게 조회하기 위한 캐시일
  뿐이며, Redis가 죽으면 Kafka를 다시 읽어 순번을 복구한다 (별도 DB 폴백 테이블을 만드는 대신 Kafka
  자체가 진실의 원천).
- 재고 방어는 이중 체크: (1) 대기열 통과 직후 Redis 원자적 연산(세마포어/카운터+중복키)으로 1차 방어,
  Fail-Fast. (2) Kafka 컨슈머의 DB row-lock 단계에서 UQ(유니크 제약, NULL 허용 트릭)로 2차 방어.
- "가결제 → 5분 내 확정 없으면 자동 취소 + 재고/세마포어 전부 복원"에 대응하는 예약 도메인 개념
  (예: "임시 홀드 상태로 좌석을 잡아두고, N분 내 확정하지 않으면 자동 해제") 도입 여부는 아래 확인 필요.

기존 코드 재사용 포인트 (탐색 완료):
- 분산락/세마포어: `AcquireTimeTableSemaphore`/`ReleaseSemaphore` 포트 + Redisson 어댑터
  (`adapter-module/.../redis/redisson/timetable/semaphore/...`) — 그대로 재사용 가능.
- Redis 장애 시 DB로 폴백하는 패턴은 `DistributedLockAspect`가 이미 구현 (`RedisException` →
  `NamedLockCoordinator` + `SERIALIZABLE` 트랜잭션) — 새 대기열의 DB 폴백 설계에 그대로 참고.
- Kafka: Confluent **Parallel Consumer**가 이미 `ProcessingOrder.KEY`로 파티션이 아니라 **키 단위
  순서 보장**을 제공 중 (`KafkaConfig.kt`). 새 토픽도 이 컨슈머 패턴을 그대로 재사용.
- Kafka→DB 쓰기는 이미 비동기 컨슈머(`TimeTableOccupancyKafkaListener`)가 `CreateReservationUseCase`를
  호출해 `reservation` 테이블에 감사용 레코드를 남기는 구조가 있음 — 이 하류 흐름은 건드리지 않는다.
- 발견한 기존 버그(참고용, 이번 스코프에서 같이 고침): outbox 프로듀서는 토픽명을
  `OutboxEventType.name = "TIME_TABLE_OCCUPIED"`로 보내는데, 컨슈머는 `TOPIC = "time-table-occupancy"`로
  구독 — 실제로는 안 맞는 상태. 새 파이프라인 만들 때 이름 규칙을 통일한다.
- DB에는 현재 `timetable.version`(낙관적 락) 외에 비관적 락/유니크 제약이 전혀 없다 — 이중예약을 막는
  DB 차원 안전장치가 없는 게 실제 갭이며, ④ 단계가 이걸 메운다.
- 테스트 패턴: `adapter-module/src/test/kotlin/.../TimeTableOccupiedDomainEventListenerTest.kt`가
  MySQL+Redis+Kafka Testcontainers 통합 테스트 템플릿, `AcquireRateLimiterRedisAdapterTest.kt`가
  Redis 단독 Testcontainers 템플릿 — 새 컴포넌트 테스트에 그대로 따른다.

## 브랜치/워크트리 전략

- **Phase 0 (베이스라인)**: 현재 브랜치 `chore/performance-test`
  (`/Users/sanghyeonkim/Downloads/port/prototype-reservation-system-perf`)에서 그대로 실행. 애플리케이션
  코드는 건드리지 않으므로 별도 브랜치 불필요.
- **Phase 1~4 (재설계)**: 기존에 이미 만들어져 있는 워크트리
  `/Users/sanghyeonkim/orca/workspaces/prototype-reservation-system/chore-performance-test-after`
  (브랜치 `newkayak12/chore-performance-test-after`)에서 진행 — "개선 전" 코드가 현재 브랜치에 그대로
  남아있어야 Phase 5에서 두 버전을 각각 checkout해 정확히 비교할 수 있음.
- 이 계획 문서는 양쪽 워크트리의 `.claude/plans/performance-redesign-plan.md`에 동일하게 커밋해 어느
  쪽에서 작업하든 참조할 수 있게 한다.

## 실행 순서 (사용자 확정: 베이스라인 측정 → 개선 → 재측정)

이번 작업은 범위가 크므로 **Phase 0(k6 베이스라인)만 이번 세션에서 바로 실행**하고, Phase 1~4(실제
아키텍처 교체)는 Phase 0 결과를 같이 보고 나서 `chore-performance-test-after` 워크트리에서 이어서
진행한다. Phase 5(재측정+리포트)는 Phase 1~4 완료 후 진행.

### Phase 0 — k6 베이스라인 측정 도구 + 현재 구조 10회 측정 (지금 실행)

새 최상위 디렉터리 `perf/k6/` 신설 (그레이들 모듈과 무관, 신규):
- `perf/k6/lib/auth.js` — `PUT /api/v1/{...}/sign-in` 호출해 VU별 JWT 캐시 (setup 단계에서 1회 로그인
  후 재사용, 기존 `GeneralUserSignInController`/`GeneralUserUrl.USER_SIGN_IN` 사용).
- `perf/k6/lib/seed.js` (또는 `seed.sql`, docker-entrypoint-initdb.d 옆에 별도 파일로) — 부하테스트 전용
  레스토랑 1개 + 좌석 수가 제한된 timetable N개(예: 30개) + 사용자 M명(예: VU 최대치만큼)을 시드. 좌석
  수를 의도적으로 작게 잡아 "티켓팅"처럼 대부분 요청이 경합하도록 구성 — mnet 사례와 동일한 부하 패턴.
- `perf/k6/scenarios/booking.js` — 대상: `POST /api/v1/time-table/booking/{restaurantId}`
  (`TimeTableOccupyUrl.BOOKING`), body `{date, startTime}`. 시나리오는 env 변수로 라벨만 바꿔가며
  baseline/redesigned 양쪽에 재사용. ramping-vus 스테이지: 100 → (200/500/1000 등 점증) — "VU 100~
  쭉쭉"에 맞춰 여러 단계로 계단식 램프. 각 반복은 성공/실패(품절/중복/락타임아웃) 여부와 상태코드를
  구분해 커스텀 메트릭으로 기록.
- `perf/k6/run.sh SCENARIO_NAME` — 동일 설정으로 10회 반복 실행, 매 회
  `--summary-export=perf/k6/results/<scenario>-<n>.json` 저장. (`perf/k6/results/`는 gitignore.)
- 측정 후 DB에서 실제 성공 예약 수 vs 좌석 수(오버부킹 여부)를 확인하는 검증 쿼리/스크립트도 같이 둔다 —
  포트폴리오에서 "빨라졌는데 정합성도 지켰다"를 증명하는 핵심 근거.
- 산출물: `docs/perf/baseline-report.md` — 10회 실행의 p50/p95/p99, 에러율, 처리량, 오버부킹 여부 요약.

이 Phase는 애플리케이션 코드를 건드리지 않는다 (k6 스크립트 + 시드 데이터 + 로컬 docker-compose 실행
뿐). `docker-compose up -d`로 기존 redis/kafka(3-broker)/mysql을 그대로 사용.

### Phase 1 — 대기열: Kafka Offset이 권위, Redis는 캐시 (① 세마포어 역할)

새 바운디드 컨텍스트 (예: `com.reservation.queue`, core/application/infrastructure/adapter 4개 모듈에
기존 패키지 구조 그대로 미러링):
- `POST /api/v1/time-table/booking/{restaurantId}/queue` — 대기열 진입. 요청을 새 토픽
  `QUEUE_ENTRY_REQUESTED`(파티션 키 = `restaurantId:date:startTime`)에 즉시 발행하고, 클라이언트에는
  `ticketId`(=사용자 고유값 기반)만 우선 반환.
- 이 토픽을 구독하는 전용 컨슈머("RedisNode" 역할)가 Kafka가 부여한 순서대로 하나씩 처리하며 Redis에
  `WAITING_QUEUE:{key}`(ZSET, score=consumer가 처리한 순서/offset 기반 시퀀스)를 채운다 — 순번의 진실은
  Kafka offset이고, Redis는 이걸 빠르게 조회하기 위한 투영(projection)일 뿐이다.
- `GET /api/v1/time-table/booking/{restaurantId}/queue/{ticketId}` — Redis ZRANK로 순번/상태 폴링
  (`WAITING`/`ADMITTED`/`EXPIRED`). 입장 허용(ADMITTED)은 기존 `AcquireTimeTableSemaphore`/
  `ReleaseSemaphore` 포트를 "동시 입장 허용치" 세마포어로 재사용(좌석 세마포어와는 별도 키).
- **Redis 장애 시 폴백**: 별도 DB 대기열 테이블을 만드는 대신, 해당 파티션의 컨슈머 그룹 offset을
  처음부터(또는 마지막 커밋 지점부터) 재생(replay)해서 Redis 상태를 재구성한다 — Kafka가 이미 순서의
  원장이므로 DB는 필요 없음. 컨슈머 재시작 시 이 재생 로직이 자동으로 타야 한다.
- 조회 부하 분산: 순번 조회 응답에 짧은 TTL(1~2초) 캐싱을 둬서 폴링 폭주가 Redis/DB로 그대로 전달되지
  않게 한다 (CDN 캐싱까지는 이번 스코프에서 생략, 애플리케이션 레벨 TTL 캐시로 대체).
- 테스트: `AcquireRateLimiterRedisAdapterTest.kt` 스타일 Redis Testcontainers 통합 테스트 + Kafka
  Testcontainers로 컨슈머 재시작 시 offset 재생 검증.

### Phase 2 — Redis 원자적 처리 (② 중복/재고 원자 처리)

`CreateTimeTableOccupancyService.execute()`에서 `@DistributedLock(FAIR_LOCK)` 제거 (현재 병목의 핵심).
대신 Lua 스크립트(Redisson `RScript` 또는 `RedisTemplate` + `DefaultRedisScript`, 이 레포 최초 도입)로
한 번의 라운드트립에 원자적으로:
1. `DEDUP:{restaurantId}:{date}:{startTime}:{userId}` — SETNX+TTL, 이미 있으면 중복 거부.
2. `SEATS:{restaurantId}:{date}:{startTime}` — 좌석 카운터 DECR (없으면 `loadBookableTimeTables` 결과
   개수로 최초 시딩), 0 이하면 품절 거부.
`@RateLimiter`는 그대로 유지(외곽 방어용, 이번 병목과 무관). `@Transactional` + JPA 저장은 이 단계에서
제거하고 Phase 3의 Kafka 발행으로 대체 — 성공 시 즉시 "접수됨" 응답, DB 쓰기는 비동기로 넘어감.
테스트: 기존 Redis Testcontainers 패턴으로 동시성(N개 스레드 동시 요청) 원자성 검증 필수.

### Phase 3 — Kafka 파티셔닝 순서 보장 (③)

새 토픽(예: `TIMETABLE_OCCUPANCY_REQUESTED`, 기존 프로듀서/컨슈머 토픽명 불일치 버그를 반복하지 않도록
이름 통일) 신설, 파티션 키 = `"$restaurantId:$timeTableSlot"`. Phase 2의 Lua 성공 직후
`KafkaTemplate.send()`로 직접 발행 (커밋 전 outbox 단계는 생략 — Redis Lua 성공이 이미 원자적 커밋
지점이므로). 기존 `KafkaConfig`의 Confluent Parallel Consumer(`ProcessingOrder.KEY`) 설정을 그대로
재사용해 같은 슬롯의 이벤트가 파티션과 무관하게 키 단위로 순서 보장되도록 컨슈머를 구성.

### Phase 4 — DB row lock 최종 방어선 (④) + 임시 홀드 만료 스케줄러

새 파라렐 컨슈머 리스너(기존 `TimeTableOccupancyKafkaListener`의 subscribe/retry/DLT 구조를 그대로
따름)가 Phase 3 토픽을 구독. `@Transactional` 안에서 `TimeTableEntity`를
`@Lock(LockModeType.PESSIMISTIC_WRITE)`로 조회(신규 리포지토리 메서드, `SELECT ... FOR UPDATE`)해 최종
가용성 재검증 후 `TimeTableOccupancyEntity`를 **임시 홀드 상태(PENDING)** 로 저장 + `tableStatus` 갱신.

- **UQ(null 활용) 중복 방지**: 신규 Flyway 마이그레이션으로 `timetable_occupancy`에
  `released_at DATETIME NULL` 컬럼을 추가하고 `UNIQUE(timetable_id, released_at)`을 건다. MySQL은
  UNIQUE 인덱스에서 NULL을 서로 다른 값으로 취급하므로, "활성 occupancy"는 `released_at IS NULL` 상태
  1건만 허용되고, 취소/만료된 occupancy는 `released_at`에 실제 시각을 채워 유니크 제약을 우회하며
  이력으로 남는다 — 이미지에 나온 "UQ(null 활용)" 트릭 그대로.
- **5분 홀드 만료 스케줄러**: `batch-module`에 새 스케줄 작업(Spring `@Scheduled` 또는 기존 배치 스텝
  패턴)을 추가해, `released_at IS NULL AND occupied_status = 'PENDING' AND occupied_datetime < now-5m`
  인 row를 주기적으로 스캔 → 각 row를 `PESSIMISTIC_WRITE`로 잠그고 `released_at` 채움(취소) +
  `tableStatus`를 다시 `EMPTY`로 복원 + Redis 좌석 카운터/세마포어도 함께 복원(`INCR`로 되돌림) — 이미지의
  "결제 이벤트가 안쌓이면 스케쥴링 작업으로 취소 처리 + 모두 복원"에 대응.
- 처리 결과(성공/실패/만료)는 Phase 1 폴링 엔드포인트가 읽을 수 있도록 `RESULT:{ticketId}` 같은 짧은
  TTL 키에 기록.
- **확정 액션 (사용자 확인, 무료 "가결제")**: 이 시스템엔 실제 결제가 없으므로 "가결제"는 결제 게이트웨이
  연동이 아니라 **무료 확정 액션**으로 구현한다. 새 엔드포인트
  `POST /api/v1/time-table/booking/{restaurantId}/queue/{ticketId}/confirm` — PENDING 상태의 occupancy를
  `PESSIMISTIC_WRITE`로 잠그고 `occupied_status`를 `CONFIRMED`로 전환 (결제 처리 없음, 단순 상태 전이).
  클라이언트는 대기열 통과 후 좌석이 PENDING으로 잡히면 5분 안에 이 confirm을 호출해야 하고, 안 하면
  아래 스케줄러가 자동 취소한다. 참고 이미지의 "가결제 → 5분 내 미결제 시 자동 취소" 흐름을 그대로
  재현하되 결제 자체만 없는 버전 — 좌석 홀드를 실제 사용자가 체감하는 예약 흐름으로 만들어 포트폴리오
  스토리(한정 재고 레이싱 + 자동 해제)를 완성한다.
- 하류의 기존 `TimeTableOccupiedDomainEvent → outbox → TimeTableOccupancyKafkaListener →
  CreateReservationUseCase` 흐름은 confirm으로 CONFIRMED 전환된 시점에 트리거되도록 옮긴다 (PENDING
  생성 시점이 아니라 confirm 시점에 도메인 이벤트 발행 — 그래야 만료된 PENDING이 하류에 잘못 전파되지
  않음).

### Phase 5 — 재측정 + 비교 리포트

Phase 0과 동일한 `perf/k6/run.sh`로 개선된 구조를 10회 측정. `docs/perf/baseline-vs-redesign.md`에
p50/p95/p99·처리량·에러율·오버부킹 여부를 표/차트로 비교, 새 아키텍처 mermaid 다이어그램 포함 —
포트폴리오 최종 산출물.

## 지금 실행할 것 (Phase 0)

1. `perf/k6/` 디렉터리 신설: `lib/auth.js`, `lib/seed.js`(or seed SQL), `scenarios/booking.js`, `run.sh`.
2. 시드 데이터: 레스토랑 1개, 좌석수 제한된 timetable(예: 30개), 사용자 다수 — 기존 회원가입/로그인/
   레스토랑·타임테이블 생성 API를 재사용해 setup 단계에서 구성 (필요시 SQL 직접 삽입도 검토).
3. k6 시나리오: `POST /api/v1/time-table/booking/{restaurantId}`, ramping-vus 100→상승, 성공/품절/중복/
   에러 커스텀 메트릭 분리.
4. `docker-compose up -d`로 로컬 redis/kafka/mysql 기동 확인 후 10회 반복 실행, 결과 JSON 저장.
5. DB 검증 쿼리로 오버부킹 여부 확인.
6. `docs/perf/baseline-report.md` 작성.

## 검증 방법
- Phase 0: `k6 run` 실행 로그의 http_req_duration p95/p99, checks 통과율 확인. 실행 후
  `SELECT COUNT(*) FROM timetable_occupancy WHERE ...`로 성공 건수가 시드한 좌석 수를 넘지 않는지 확인.
- Phase 1~4: 각 Phase마다 `./gradlew :{module}:test`로 신규 테스트 통과 확인, `./gradlew detekt
  spotlessCheck`로 품질 게이트 통과 확인 (CLAUDE.md: maxIssues 0).
- Phase 5: Phase 0과 동일한 스크립트로 재측정해 수치 직접 비교.
