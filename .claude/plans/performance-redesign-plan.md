# 예약 부하테스트 & 아키텍처 개선 (포트폴리오)

## Context

현재 좌석 예약(점유) 쓰기 경로는 `CreateTimeTableOccupancyService.execute()` (application-module)에
`@RateLimiter` + `@DistributedLock(FAIR_LOCK)` + `@Transactional` 이 겹겹이 걸려 있고, 그 안에서
Redisson **RSemaphore**로 좌석 수만큼 permit을 발급한 뒤 JPA로 `TimeTableOccupancyEntity`를 저장한다.
즉 같은 (restaurantId+date+startTime) 슬롯에 대한 모든 요청이 **하나의 분산락을 순차적으로 통과**해야
하며, 그 안에 DB 왕복까지 포함되어 있어 동시성이 몰리면 락 대기열이 그대로 커넥션/스레드 점유로 번진다.
이게 바로 mnet 투표 사례가 지적하는 "여기가 병목입니다" 지점과 동일한 패턴이다.

목표: 첨부 이미지의 4단계 아이디어(① Redis 대기열 + fallback DB, ② Redis 원자적 처리/중복 방지,
③ Kafka 파티셔닝 순서보장, ④ DB row lock 최종 방어선)를 이 코드베이스에 이식하고, k6로 **개선 전/후**를
동일 시나리오·동일 VU 램프로 10회씩 측정해 포트폴리오용 비교 자료를 만든다. 사용자가 확정한 대로
① 대기열은 "예약 페이지 진입 전" 세마포어 역할 + 순번 폴링 방식으로 만든다.

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

### Phase 1 — Redis 대기열 (① 세마포어 역할 + fallback DB)

새 바운디드 컨텍스트 (예: `com.reservation.queue`, core/application/infrastructure/adapter 4개 모듈에
기존 패키지 구조 그대로 미러링):
- `POST /api/v1/time-table/booking/{restaurantId}/queue` — 대기열 진입, `{ticketId, position}` 반환.
- `GET /api/v1/time-table/booking/{restaurantId}/queue/{ticketId}` — 순번/상태 폴링
  (`WAITING`/`ADMITTED`/`EXPIRED`).
- Redis 자료구조: ZSET `WAITING_QUEUE:{restaurantId}:{date}:{startTime}` (member=ticketId, score=Redis
  `INCR` 시퀀스 — 시계 스큐 방지). 입장 허용은 기존 `AcquireTimeTableSemaphore`/`ReleaseSemaphore`
  포트를 새 "동시 입장 허용치" 세마포어로 재사용 (좌석 세마포어와는 별도 키, 시스템 동시처리 capacity
  기준). 허용된 ticket은 TTL 달린 `ADMITTED:{key}` SET으로 이동.
- Redis 장애 시 폴백: `DistributedLockAspect`의 `RedisException → NamedLockCoordinator` 패턴을 그대로
  본떠서, DB 테이블(`waiting_queue`, 신규 Flyway 마이그레이션, auto-increment id로 순번 대체)로 전환.
- 테스트: `AcquireRateLimiterRedisAdapterTest.kt` 스타일 Redis Testcontainers 통합 테스트 + 폴백 경로용
  DistributedLockAspectTest 스타일 mock 테스트.

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

### Phase 4 — DB row lock 최종 방어선 (④)

새 파라렐 컨슈머 리스너(기존 `TimeTableOccupancyKafkaListener`의 subscribe/retry/DLT 구조를 그대로
따름)가 Phase 3 토픽을 구독. `@Transactional` 안에서 `TimeTableEntity`를
`@Lock(LockModeType.PESSIMISTIC_WRITE)`로 조회(신규 리포지토리 메서드, `SELECT ... FOR UPDATE`)해 최종
가용성 재검증 후 `TimeTableOccupancyEntity` 저장 + `tableStatus` 갱신. 신규 Flyway 마이그레이션으로
`timetable_occupancy`에 유니크 제약(같은 timetable_id에 대해 OCCUPIED 상태 1건만 허용)을 추가해 DB
레벨에서 이중예약을 원천 차단 — 현재 존재하지 않는 안전장치. 처리 결과(성공/실패)는 Phase 1 폴링
엔드포인트가 읽을 수 있도록 `RESULT:{ticketId}` 같은 짧은 TTL 키에 기록. 성공 시 하류의 기존
`TimeTableOccupiedDomainEvent → outbox → TimeTableOccupancyKafkaListener → CreateReservationUseCase`
흐름은 그대로 재사용 (변경 없음).

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
