# 예약 부하테스트 & 아키텍처 개선 (포트폴리오)

## Context

현재 좌석 예약(점유) 쓰기 경로는 `CreateTimeTableOccupancyService.execute()` (application-module)에
`@RateLimiter` + `@DistributedLock(FAIR_LOCK)` + `@Transactional` 이 겹겹이 걸려 있고, 그 안에서
Redisson **RSemaphore**로 좌석 수만큼 permit을 발급한 뒤 JPA로 `TimeTableOccupancyEntity`를 저장한다.
즉 같은 (restaurantId+date+startTime) 슬롯에 대한 모든 요청이 **하나의 분산락을 순차적으로 통과**해야
하며, 그 안에 DB 왕복까지 포함되어 있어 동시성이 몰리면 락 대기열이 그대로 커넥션/스레드 점유로 번진다.
이게 바로 mnet 투표 사례가 지적하는 "여기가 병목입니다" 지점과 동일한 패턴이다.

**Phase 0 베이스라인 측정 중 발견한 락 이중화 이슈 (수정하지 않고 그대로 둠 — 재설계로 자연 해소)**:
`DistributedLockAspect.executeDistributedLockAction()`은 Redis `FAIR_LOCK` 획득 시도 중
`RedisException`이 나면(Redisson 자체 커넥션 풀 고갈 등) `executeNamedLock()`으로 폴백해 MySQL
Named Lock(`GET_LOCK`)으로 같은 메서드를 재실행한다. 문제는 이 두 락이 서로 다른 백엔드라
**상호 배제되지 않는다** — 어떤 요청은 Redis 락으로, 동시에 다른 요청은 DB 락으로 각자 "락을 잡았다"고
믿은 채 `CreateTimeTableOccupancyService.execute()` 안쪽(특히 `acquireSemaphore()`가 읽는 "현재 예약
가능한 좌석 수" 스냅샷)에 동시 진입할 수 있다. 실제로 베이스라인 10회 중 8회차에서 좌석 30개 중
10개만 점유되고 나머지 20개가 끝까지 비어버리는 현상이 재현됐다(이중예약이 아니라 과소예약 —
`docs/perf/baseline-report.md` 8회차 각주 참고). 부트런 로그에 해당 구간과 겹치는 시각대에
"Unable to connect to Redis" 경고가 다수 확인되어 이 메커니즘으로 설명이 된다. Phase 0 스코프에서는
고치지 않는다 — 애초에 이 세마포어+분산락 조합 자체를 Phase 1~4에서 Redis 원자적 Lua 연산 +
Kafka 순서 보장 + DB row lock으로 통째로 교체하므로, 재설계가 이 문제를 구조적으로 없앤다.

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
- **발견 후 수정한 버그**: `IsReservationExists` 출력 포트(`application-module`)를 구현하는 어댑터가
  `infrastructure-module`에 아예 없었다 — 테스트는 MockK로 우회하므로 지금까지 드러나지 않았지만, 실제
  `bootRun`으로 전체 스프링 컨텍스트를 띄우면 `timeTableOccupancyKafkaListener` 빈 생성 시점에 즉시
  실패해 앱이 아예 기동조차 되지 않는 상태였다. Phase 0 k6 부하테스트를 실행하려면 앱이 떠 있어야 하므로
  `infrastructure-module/.../reservation/repository/jpa/ReservationJpaRepository.kt`에
  `existsReservation(timeTableId, timeTableOccupancyId)` JPQL 쿼리를 추가하고,
  `IsReservationExistsAdapter`(기존 `CreateReservationAdapter`와 동일 패턴)를 새로 만들어 채웠다. 이건
  이번 재설계 스코프와 무관한, 이미 존재하던 결함을 고친 것.
- **발견 후 수정한 버그 (2)**: `SecurityConfig`의 `JwtWhitelist`(`security.jwt.allowed.path`)에
  `/api/v1/user/sign-up`, `/api/v1/user/sign-in` 등이 등록되어 있어 `JwtFilter`는 이 경로들에서 스킵되지만,
  `authorizeHttpRequests`의 `USER_PATHS` 규칙이 `/api/v1/user/**` 전체에 `hasRole(ROLE_USER)`를 걸어버려
  익명 요청이 전부 403으로 막혔다 — 화이트리스트의 존재 의도(비로그인 상태에서 회원가입/로그인 허용)가
  실제로는 관철되지 않던 상태. `filterChain()`의 `authorizeHttpRequests`에 `jwtWhitelistPaths()`(=
  `jwtPath.path`) permitAll 규칙을 `USER_PATHS` 규칙보다 앞에 추가해 화이트리스트가 실제로 동작하게 했다.
  이것도 재설계 스코프와 무관, k6가 실제 회원가입/로그인 API를 타려면 필요했던 사전 수정. (부가 발견:
  화이트리스트 항목 중 `/api/**/internal/**`는 `**`가 두 번 나와 Spring MVC `PathPattern` 파서가 파싱을
  거부한다 — 기존엔 `JwtFilter.shouldNotFilter()`에서 단순 문자열 `contains` 비교로만 쓰여서 문제가 안
  됐지만, `requestMatchers()`에 그대로 넘기면 500이 난다. `jwtWhitelistPaths()`에서 `**`가 2번 이상 나오는
  패턴은 걸러내는 방식으로 회피했다 — 그 패턴 자체의 의도를 바꾸지 않기 위해.)
- **발견 후 수정한 버그 (4)**: `UserEntity.userStatus`(`infrastructure-module/.../user/entity/UserEntity.kt`)
  필드에 `@Enumerated(EnumType.STRING)`이 빠져 있었다 — 같은 클래스의 `role` 필드는 제대로 붙어 있는데
  `userStatus`만 누락. JPA 기본값은 ORDINAL이라 회원가입 INSERT 시 `user_status` 컬럼(MySQL
  `ENUM('ACTIVATED','DEACTIVATED')`)에 문자열 대신 서수 정수(0)를 보내서
  `Data truncated for column 'user_status'` SQL 에러로 회원가입 자체가 500으로 실패했다. `role` 필드와
  동일하게 `@Enumerated(value = EnumType.STRING)`을 추가해 해결. 이것도 재설계 스코프와 무관한 사전 수정.
- **발견 후 수정한 버그 (5)**: `AuthenticateUserRepository.queryToDatabase()`(로그인 조회 쿼리)의
  QueryDSL `Projections.constructor(Result::class.java, ...)` 호출이 인자 6개만 넘기는데, `Result` data
  class는 필드 7개(`id, loginId, password, failCount, userStatus, lockedDatetime, role`)라서
  `role`에 대응하는 생성자를 못 찾아 `ExpressionException: No constructor found`로 로그인이 항상 500으로
  터졌다. `userEntity.role`을 프로젝션 인자에 추가해 해결. 이것도 재설계 스코프와 무관한 사전 수정.
- **발견 후 수정한 버그 (6)**: `GeneralUserSignInController`/`SellerUserSignInController`/
  `RefreshGeneralUserController`가 리프레시 토큰을 `Cookie`에 담을 때 `JWTProvider.tokenize()`가 반환하는
  `"Bearer <jwt>"` 형태(공백 포함)를 그대로 넣어서 `IllegalArgumentException: An invalid character [32]
  was present in the Cookie value`로 로그인 자체가 500으로 죽었다. 세 곳 모두 쿠키에 넣기 직전에
  `.removePrefix("Bearer ")`를 적용해 해결 (액세스 토큰은 Authorization 헤더 값이라 "Bearer " 접두어가
  맞지만, 쿠키 값에는 애초에 그 접두어가 들어가면 안 됨). `RefreshGeneralUserController`가 쿠키에서 다시
  읽어 `JWTProvider`에 넘기는 경로는 `removeBearer()`가 접두어 유무와 무관하게 안전하게 동작해서 영향
  없음.
- **발견 후 수정한 버그 (7, 가장 심각)**: `UserEntity.updateAuthenticateResult()`가
  `this.lockedDatetime = lockedDateTime ?: LocalDateTime.now()`로 되어 있어서, 로그인 **성공** 시에도
  (도메인 로직 `LockState.activate()`는 `lockedDateTime=null`을 반환 — "잠금 없음"을 의미) null을 그냥
  두지 않고 항상 현재시각으로 덮어써버렸다. 그 결과 `isLockdownTimeOver()`가 다음 로그인 때
  `lockedDatetime + 30분 > now`로 계산돼 "아직 잠금 시간 안 지남"으로 판정 — **모든 사용자가 로그인에 한
  번 성공하면 그 다음부터 30분간 재로그인이 전부 실패하는 상태**였다. k6가 매 실행마다 300명을 다시
  로그인시키는 구조라 이 버그를 못 고쳤으면 2회차부터 토큰 발급이 전부 막혔을 것 — 발견 즉시 가장 심각한
  버그. `this.lockedDatetime = lockedDateTime`로 단순화해 해결 (null이면 null 그대로 저장).
- **발견 후 수정한 버그 (8)**: `CreateTimeTableOccupancyController`/`CreateRestaurantController`/
  `ChangeRestaurantController` 세 곳 모두 `header: HttpHeaders` 파라미터에 `@RequestHeader` 애노테이션이
  빠져 있었다. Spring MVC는 애노테이션 없는 `HttpHeaders` 파라미터를 실제 요청 헤더로 채워주는 리졸버가
  없어서 빈 `HttpHeaders()`로 폴백되고, 그 결과 `header.getFirst(AUTHORIZATION)`이 항상 null이 되어
  (Spring Security의 `JwtFilter`는 인증에 성공했는데도) 컨트롤러 안에서
  `ExtractIdentifierFromHeaderUseCase`가 `UnauthorizedException`을 던져 예약/매장 생성·수정이 전부
  401로 막혔다. 세 곳 모두 `@RequestHeader header: HttpHeaders`로 수정.
- **발견 후 수정한 버그 (9)**: `TimeTableJpaRepository.findBookableTimeTable`의 JPQL이 개별 컬럼 10개를
  `SELECT`하면서 반환 타입은 `List<TimeTableEntity>`였다 — `TimeTableEntity`의 실제 생성자는 8개 파라미터
  (`identifier`, `timeTableConfirmStatus`는 생성자에 없음)라 이 프로젝션은 애초에 성립할 수 없는 구조라
  `ConverterNotFoundException`으로 예약 조회 자체가 500이었다. `SELECT timetable FROM TimeTableEntity
  timetable ...`로 엔티티 자체를 선택하도록 단순화해 해결.
- **발견 후 수정한 버그 (10, 가장 치명적)**: `CreateTimeTableOccupancyService.acquireSemaphore()`에서
  `SemaphoreSettings`(세마포어 총 용량)와 `SemaphoreInquiry`(이번 요청이 요구하는 permit 수)의 값이
  서로 뒤바뀌어 있었다 — 세마포어 총 용량을 상수 `SEMAPHORE_ACQUIRE_SIZE`(1)로 설정하고, 정작 이번 요청은
  좌석 수(`size`, 예: 30)만큼의 permit을 한 번에 요구하는 구조였다. 총 용량 1인 풀에서 30개를 요구하면
  **영원히 충족 불가능** — 모든 예약 요청이 `tryAcquire`의 최대 대기시간(5분)만큼 그냥 멈춰있다가 실패하는
  상태였다(실제로 curl이 120초 타임아웃에 걸려서 발견함). 상수 이름(`SEMAPHORE_DURATION`,
  `SEMAPHORE_MAXIMUM_WAIT_TIME`)이 원래 의도를 보여줘서, `SemaphoreSettings(size, SEMAPHORE_DURATION)` +
  `SemaphoreInquiry(SEMAPHORE_ACQUIRE_SIZE, SEMAPHORE_MAXIMUM_WAIT_TIME)`로 값을 맞바꿔 해결.
- **발견 후 수정한 버그 (11)**: `OutBox`(`infrastructure-module/.../outbox/entity/OutBox.kt`)의 `@Table`에
  다른 모든 엔티티(`RestaurantEntity`, `TimeTableEntity`, `UserEntity` 등)와 달리 `catalog =
  "prototype_reservation"`이 빠져 있었다. 커넥션 풀의 특정 커넥션이 "현재 스키마"를 잃어버린 상태가 되면
  (부트런 로그에 `Unable to restore connection to having no default schema` 경고가 실제로 찍혔음 —
  Flyway가 자기 커넥션의 스키마를 원복하는 과정에서 발생한 것으로 보임) 스키마 미지정 쿼리는 `No database
  selected`로 실패한다 — outbox INSERT(예약 성공 후 도메인 이벤트 발행 단계)가 여기 해당돼 예약 자체는
  성공해도 이후 처리가 500으로 죽었다. 다른 엔티티와 동일하게 `catalog = "prototype_reservation"`을
  추가해 해결.
- **발견 후 수정한 버그 (12)**: 4개 프로필(`local`/`temporary`/`stage`/`production`) 전부 Kafka producer
  설정이 `delivery.timeout.ms: 30000`, `request.timeout.ms: 30000`, `linger.ms: 5`였는데, Kafka 클라이언트
  라이브러리는 `delivery.timeout.ms >= linger.ms + request.timeout.ms`를 강제한다 (30000 < 30005) —
  `ConfigException`으로 KafkaProducer 생성 자체가 실패해서, 예약 저장은 성공해도 그 직후
  outbox→Kafka 발행(`TimeTableOccupiedDomainEventListener.publishKafkaEvent`, `@TransactionalEventListener`
  AFTER_COMMIT)이 전부 조용히 실패하고 있었다 — HTTP 응답은 201로 성공해서 눈치채기 어려운 종류의 버그.
  4개 yaml 파일 모두 `delivery.timeout.ms`를 35000으로 올려 해결.
- **발견 후 수정한 버그 (13)**: `KafkaConfig.createProducerConfig()`(`adapter-module/.../kafka/config/
  KafkaConfig.kt`)가 `kafkaProperties.producer.bootstrapServers`만 읽어서 `ProducerConfig
  .BOOTSTRAP_SERVERS_CONFIG`를 채우는데, 4개 프로필 yaml 전부 `spring.kafka.bootstrap-servers`(최상위)만
  설정하고 `spring.kafka.producer.bootstrap-servers`(producer 하위)는 별도로 설정하지 않았다. Spring Boot의
  오토컨피그(`KafkaProperties.buildProducerProperties()`)는 producer 하위 값이 비어 있으면 최상위 값으로
  폴백하지만, 이 프로젝트는 `ProducerFactory`/`KafkaTemplate` 빈을 수동으로 직접 구성하고 있어서 그 폴백
  로직을 타지 않는다 — 결과적으로 `bootstrap.servers`가 빈 채로 `KafkaProducer`가 생성되어
  `ConfigException: No resolvable bootstrap urls given in bootstrap.servers`가 첫 `send()` 호출 시점(=
  `TimeTableOccupiedDomainEventListener.publishKafkaEvent`, outbox AFTER_COMMIT 단계)에 터졌다. 버그
  (12)와 마찬가지로 예약 저장/HTTP 응답(201)은 정상이라 겉으로는 티가 안 나는 종류. `producerConfig
  .bootstrapServers ?: kafkaProperties.bootstrapServers`로 최상위 값 폴백을 직접 추가해 해결. (consumer
  쪽은 4개 yaml 전부 `spring.kafka.consumer.bootstrap-servers`를 명시적으로 갖고 있어서 이 문제가 없었음
  — 그래서 컨슈머는 처음부터 정상 기동했던 것.)
  → 이 수정 이후 outbox 상태가 `PUBLISHED`(발행 대기)에서 `PROCESSED`(발행 성공)로 정상 전이됨을 확인.
  다만 `reservation` 테이블 카운트는 여전히 0인데, 이는 새 버그가 아니라 위 Context 섹션에 이미 기록된
  기존 결함(프로듀서는 `createdEvent.eventType.name = "TIME_TABLE_OCCUPIED"` 토픽으로 발행하는데 컨슈머
  `TimeTableOccupancyKafkaListener`는 하드코딩된 `"time-table-occupancy"` 토픽을 구독 — 실제 Kafka
  토픽 목록에 두 이름이 별도로 존재함을 직접 확인) 때문이며, 이 토픽명 통일은 계획대로 Phase 3에서
  다룬다. Phase 0의 정합성 검증 대상은 `timetable_occupancy`(좌석 점유)이지 `reservation`(하류 감사
  레코드)이 아니므로, `reservation_count=0`은 Phase 0 기준 정상/기지 한계로 취급하고 베이스라인 측정을
  진행한다.
  → 여기까지 총 13개의 사전 결함을 고쳐야 실제 회원가입→로그인→예약(비동기 outbox 발행 포함) 흐름을
  k6로 반복 측정할 수 있는 상태가 됨. 전부 기존 테스트가 실제 시큐리티 설정/전체 스프링 컨텍스트를 타지
  않고 mock/slice로 우회해서 지금까지 발견되지 않고 있던 결함들. (Enum 매핑 누락은 이 두 건이 전부인지
  별도 서브에이전트로 전수조사 완료 — 나머지 엔티티는 전부 정상.)
- **발견 후 수정한 버그 (3)**: `SecurityConfig.filterChain()`에 `.csrf { it.disable() }` 호출이 아예
  없었다 — Stateless JWT REST API인데 CSRF 보호가 기본값(ON)으로 걸려 있어서 POST/PUT 등 모든 상태 변경
  요청이 CSRF 토큰 없이는 403으로 막힌다. 기존 컨트롤러 테스트들은 전부
  `adapter-module/src/test/kotlin/.../TestSecurity.kt`(테스트 전용 시큐리티 빈, `.csrf { it.disable() }`
  포함)로 실제 `SecurityConfig`를 대체해서 돌기 때문에 지금까지 드러난 적이 없었다 — 즉 이 앱은 지금까지
  한 번도 실제 시큐리티 설정으로 end-to-end 기동+호출된 적이 없었다는 뜻. `filterChain()`에도 동일하게
  `.csrf { it.disable() }`를 추가해 해결. 이것도 재설계 스코프와 무관한 사전 수정.
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
- **인증은 우회하지 않는다**: k6 `setup()` 단계에서 실제 `POST /api/v1/user/sign-up`
  (`GeneralUserUrl.USER_SIGN_UP`)으로 부하테스트 전용 사용자 M명을 실제로 회원가입시키고, 실제
  `PUT /api/v1/user/sign-in`으로 로그인해 진짜 JWT(`accessToken`, 이미 `"Bearer "` 접두어 포함)를 받는다.
  DB에 비밀번호 해시를 직접 꽂아넣는 방식(사전 계산한 BCrypt 해시)은 Spring Security의 BCrypt 버전
  호환성 리스크가 있어 배제 — 반드시 실제 서비스 코드 경로(회원가입→로그인)를 그대로 통과시켜 토큰을
  받는다. 이 토큰들은 setup 단계에서 한 번만 발급해 VU 전체가 재사용 (매 반복마다 로그인하지 않음 —
  이건 "인증 우회"가 아니라 "부하테스트 대상은 로그인이 아니라 예약 엔드포인트"라는 표준 k6 관례).
  레스토랑(`restaurant`)·타임테이블(`timetable`) 시드는 raw SQL로 직접 삽입 (FK 제약 없음, 승인 상태
  게이트가 조회 경로를 막지 않음을 확인함 — `findBookableTimeTable` 쿼리는 `table_status='EMPTY'`,
  `time_table_confirm_status='NOT_CONFIRMED'`, 미점유 조건만 봄). 단, `restaurantId`는 URL 경로 정규식
  `[0-9a-fA-F\-]{36}`(표준 UUID 형식)을 만족해야 하므로 MySQL `UUID()`로 생성.
- `perf/k6/lib/auth.js` — setup 단계 회원가입+로그인 헬퍼.
- `perf/k6/lib/seed.sql` — 레스토랑 1개 + 좌석 수가 제한된 timetable N개(예: 30개, 각기 다른
  `table_number`로 동일 슬롯에 30석)를 시드. 좌석 수를 의도적으로 작게 잡아 "티켓팅"처럼 대부분 요청이
  경합하도록 구성.
- `perf/k6/scenarios/booking.js` — 대상: `POST /api/v1/time-table/booking/{restaurantId}`
  (`TimeTableOccupyUrl.BOOKING`), body `{date, startTime}`. 시나리오는 env 변수로 라벨만 바꿔가며
  baseline/redesigned 양쪽에 재사용. 각 반복은 성공/실패(품절/중복/락타임아웃) 여부와 상태코드를 구분해
  커스텀 메트릭으로 기록.
  - **ramping-vus 스테이지 설계 (포화점/최대 처리량 탐색)**: "한 번에 받을 수 있는 양"을 알아내는 게
    목적이므로 단순 상승이 아니라 100 → 300 → 600 → 1000 → 1500 → 2000 식으로 계단마다 일정 시간
    유지하며 에러율·p95가 임계치를 넘는 지점을 찾는다. 각 스테이지는 k6 태그로 구분해 요약에서 "몇 VU
    부터 무너지기 시작했는지"를 바로 읽을 수 있게 한다.
  - **"프로세스 완료 시각" (정착 시간) 측정**: 현재 구조는 `execute()` 안에서 DB 저장까지 동기로
    끝나므로 HTTP 응답 완료 = 예약 확정이지만, 개선 후(Phase 1~4)는 Kafka 비동기 파이프라인을 거쳐
    실제 DB 커밋까지 지연이 생긴다. 그래서 Phase 0/5 공통으로 "체감 응답속도"(p50/p95/p99, HTTP 레벨)와
    "정착 시간"(마지막 요청 전송 시각부터, `timetable_occupancy`/`reservation` 테이블의 카운트가 더 이상
    변하지 않게 될 때까지의 wall-clock)을 **둘 다** 따로 리포트한다. 정착 시간 측정은 `run.sh`가 k6
    종료 직후 DB를 짧은 간격으로 폴링하는 별도 쉘 루프로 처리 (`perf/k6/lib/wait-settle.sh`).
- `perf/k6/run.sh SCENARIO_NAME` — 동일 설정으로 10회 반복 실행, 매 회
  `--summary-export=perf/k6/results/<scenario>-<n>.json` 저장하고 직후 `wait-settle.sh`로 정착 시간을
  같은 파일명 접미사로 기록. (`perf/k6/results/`는 gitignore.)
- 측정 후 DB에서 실제 성공 예약 수 vs 좌석 수(오버부킹 여부)를 확인하는 검증 쿼리/스크립트도 같이 둔다 —
  포트폴리오에서 "빨라졌는데 정합성도 지켰다"를 증명하는 핵심 근거.
- 산출물: `docs/perf/baseline-report.md` — 10회 실행의 p50/p95/p99, 에러율, 스테이지별 처리량(포화점),
  정착 시간, 오버부킹 여부 요약.

이 Phase는 애플리케이션 코드를 건드리지 않는다 (k6 스크립트 + 시드 데이터 + 로컬 docker-compose 실행
뿐). `docker-compose up -d`로 기존 redis/kafka(3-broker)/mysql을 그대로 사용, `./gradlew
:adapter-module:bootRun`으로 앱을 별도 기동해 k6가 실제 HTTP로 때린다.

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
