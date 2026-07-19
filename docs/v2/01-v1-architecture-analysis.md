# V1 Architecture Analysis

> Cycle: `20260604-v2-event-sourcing-cqrs` | Phase: 1 — V1 구조 분석
> 기준: 실제 코드 직접 독해 (core-module 216파일, adapter/application-module 전수 확인)

---

## 1. 모듈 구조

```
reservation/
├── shared-module      # Enum, 공통 유틸리티 (UuidGenerator, PasswordEncoderUtility)
├── core-module        # Domain Layer — 외부 의존 없음 (216 Kotlin 파일)
├── application-module # Use Case + Port 정의
├── infrastructure-module # Flyway 마이그레이션 (V1_0 ~ V1_18)
├── adapter-module     # REST Controller, JPA, Security, Kafka, Redis
├── test-module        # FixtureMonkey 기반 테스트 픽스처
└── batch-module       # 배치 처리 (분석 대상 외)
```

### 의존성 방향

```
shared-module ← core-module ← application-module ← adapter-module
                                                  ← infrastructure-module
```

- `core-module`: shared-module만 의존 — 순수 도메인
- `application-module`: core-module 의존
- `adapter-module`: application-module + infrastructure-module 의존

---

## 2. 도메인 맵 (Bounded Contexts)

### 2.1 User Context

| 구성 | 실제 코드 |
|---|---|
| **Aggregate Root** | `User`, `RestaurantOwner`, `Admin` |
| **공통 인터페이스** | `ServiceUser` — User/RestaurantOwner 추상화. `UserResignable`, `PasswordChangeable` 별도 |
| **Value Objects** | `LoginId`, `Password`, `PersonalAttributes`, `UserAttribute`, `LockState` |
| **특수 객체** | `ResignedUser` — 탈퇴 처리 후 암호화 상태 보존용. `EncryptedAttributes` |
| **도메인 행위** | `changePassword()`, `changePersonalAttributes()`, `changeUserNickname()`, `resign()` |
| **이력** | application layer: `user/history/access`, `user/history/change` — 별도 포트로 분리 |

**핵심 관찰**: `User`, `RestaurantOwner`는 구조가 동일하나 `ServiceUser`로 추상화. `Admin`은 별도 인터페이스 구현 (`UserResignable`, `PasswordChangeable`). ID는 `String?` — 영속화 시 할당.

---

### 2.2 Authenticate Context

| 구성 | 실제 코드 |
|---|---|
| **Aggregate Root** | `Authenticate` |
| **Value Objects** | `AccessDetails`, `AccessHistory` |
| **Domain Service** | `AuthenticateSignInDomainService` |
| **Policy** | `SignInPolicy` → `NormalSignInPolicy` (실패 횟수 제한, 잠금 시간 정책) |
| **핵심 메서드** | `canISignIn(rawPassword, signInPolicy)` |

**`Authenticate.canISignIn()` 흐름**:
```
1. isPasswordSame() → 실패 시 lockState.addFailureCount()
2. isLockdownTimeOver() → 잠금 해제 여부 확인
3. writeAccessHistory() → 접근 이력 기록
4. hasExceededFailCount() → 초과 시 lockState.deactivate()
5. 성공 시 lockState.activate()
```

**상태 필드**: `passwordCheckSuccess`, `lockCheckSuccess`, `isSuccess` (= 둘 다 true)

---

### 2.3 Company Context

| 구성 | 실제 코드 |
|---|---|
| **Aggregate Root** | `Company` |
| **Value Objects** | `Brand`, `Business`, `CompanyAddress`, `CompanyContact`, `Representative` |
| **특이사항** | Command 없음. 조회 전용. Restaurant에서 `companyId`로 참조 |

---

### 2.4 Restaurant Context

| 구성 | 실제 코드 |
|---|---|
| **Aggregate Root** | `Restaurant` |
| **Value Objects** | `RestaurantDescription`, `RestaurantContact`, `RestaurantAddress` (`+RestaurantCoordinate`), `RestaurantRoutine` (`+RestaurantWorkingDay`), `RestaurantPhotoBook` (`+RestaurantPhoto`), `RestaurantTags`, `RestaurantNationalities`, `RestaurantCuisines` |
| **Domain Services** | `CreateRestaurantDomainService`, `ChangeRestaurantDomainService` |
| **Update Helpers** | `UpdatePhoto`, `UpdateTag`, `UpdateRoutine`, `UpdateNationalities`, `UpdateCuisines` (service/update 패키지) |
| **Validate Helpers** | `ValidateName`, `ValidatePhone`, `ValidateAddress`, `ValidateZipCode`, `ValidateCoordinate`, `ValidateIntroduce` (service/validate 패키지) |
| **Domain Event** | `CreateScheduleEvent(restaurantId)` — Restaurant 생성 시 발행 |

**실제 `Restaurant` 변경 메서드**:
```kotlin
fun updateDescription(newDescription: RestaurantDescription)    // setter 패턴
fun updateLocation(newLocation: RestaurantAddress)              // setter 패턴
fun updateContact(newContract: RestaurantContact)               // setter 패턴
fun manipulateRoutine(block: (RestaurantRoutine) -> Unit)       // 람다로 VO 직접 조작
fun manipulatePhoto(block: (RestaurantPhotoBook) -> Unit)
fun manipulateTags(block: (RestaurantTags) -> Unit)
fun manipulateNationalities(block: (RestaurantNationalities) -> Unit)
fun manipulateCuisines(block: (RestaurantCuisines) -> Unit)
```

**`CreateRestaurantDomainService.create()`**: 검증 → `Restaurant` 생성 → `snapshot()` 반환
**`ChangeRestaurantDomainService.change()`**: 검증 → 모든 update 메서드 호출 → `snapshot()` 반환

---

### 2.5 Category Context

| 구성 | 실제 코드 |
|---|---|
| **Entities** | `Cuisine`, `Nationality`, `Tag` |
| **Value Objects** | `CategoryDetail` |
| **특이사항** | 조회 전용. Restaurant에서 ID로 참조. `CategoryType` enum: `NATIONALITY`, `CUISINE`, `TAG` |

---

### 2.6 Menu Context

| 구성 | 실제 코드 |
|---|---|
| **Aggregate Root** | `Menu` |
| **Value Objects** | `MenuDescription`, `MenuPrice`, `MenuAttributes`, `MenuPhotoBook` (`+MenuPhoto`) |
| **Domain Services** | `CreateMenuDomainService`, `ChangeMenuDomainService` |

**실제 `Menu` 변경 메서드**:
```kotlin
fun changeInformation(information: MenuDescription)   // setter 패턴
fun changeAttributes(attributes: MenuAttributes)      // setter 패턴
fun changePrice(price: MenuPrice)                     // setter 패턴
fun manipulatePhoto(block: (MenuPhotoBook) -> Unit)   // 람다로 VO 조작
```

---

### 2.7 Schedule Context

| 구성 | 실제 코드 |
|---|---|
| **Aggregate Root** | `Schedule` |
| **Entities** | `TimeSpan` (요일+시간대), `Holiday` (날짜), `Table` (테이블번호+크기) |
| **Domain Services** | `CreateScheduleDomainService`, `CreateTimeSpanDomainService`, `CreateHolidayDomainService` |
| **Status** | `ScheduleActiveStatus`: `ACTIVE`, `INACTIVE` |

**`Schedule` 메서드**:
```kotlin
fun addHoliday(holiday: Holiday)    // 중복 체크 후 추가
fun addTimeSpan(timeSpan: TimeSpan) // 중복 체크 후 추가
```

**`ScheduleActiveStatus.rebalance()`**: tablesConfigured + workingHoursConfigured + holidaysConfigured 모두 true일 때만 ACTIVE. 상태가 설정 완료도로 결정됨.

**Application 레이어 특이사항**:
- `ScheduleMutator` — `LoadScheduleResult` (영속화 결과) → `Schedule` (도메인 객체) 변환
- `ScheduleInquiryMutator` — `ScheduleSnapshot` → `ScheduleInquiry` (영속화 입력) 변환
- `schedule/port/mutator/` 패키지가 별도 존재 — 독특한 설계

---

### 2.8 TimeTable Context

| 구성 | 실제 코드 |
|---|---|
| **Aggregate Root** | `TimeTable` |
| **Entity** | `TimetableOccupancy` |
| **Domain Services** | `CreateTimeTableOccupancyDomainService`, `CreateTimeTableOccupiedDomainEventService` |
| **Domain Event** | `TimeTableOccupiedDomainEvent(timeTableId, timeTableOccupancyId)` |

**`TimeTable` 상태 필드**:
- `tableStatus: TableStatus` — `EMPTY`, `OCCUPIED`
- `timeTableConfirmStatus: TimeTableConfirmStatus` — `NOT_CONFIRMED`, `CONFIRMED`
- `timetableOccupancy: TimetableOccupancy?` — null이면 빈 자리

**주의**: `TableStatus.isOccupied() = this == EMPTY` — **버그성 명명**: 메서드 이름과 반환값이 반대. 실제로는 "isEmpty()" 동작.

**`TimetableOccupancy` 상태**:
- `occupiedStatus: OccupyStatus` — `OCCUPIED`, `UNOCCUPIED`
- 생성 시 `OCCUPIED`, `unoccupied()` 호출 시 `UNOCCUPIED` + `unoccupiedDatetime` 기록

**`TimeTable` 핵심 메서드**:
```kotlin
fun attachOccupied(userId: String) {
    if (isOccupied()) return           // idempotent
    tableStatus = OCCUPIED
    timetableOccupancy = TimetableOccupancy(timeTableId = id!!, userId = userId)
}

fun detachOccupied() {
    if (!isOccupied()) return          // idempotent
    tableStatus = EMPTY
    timetableOccupancy?.unoccupied()
}
```

**`CreateTimeTableOccupancyService` 동시성 제어 (3중)**:
```
@RateLimiter (SpEL key: restaurantId:date:startTime)
  → @DistributedLock (FairLock, SpEL key 동일)
    → Semaphore (Redis, key: SEMAPHORE:restaurantId:date:startTime)
      → @Transactional
```
순서: RateLimiter(최외곽) → DistributedLock → Transactional → Semaphore(내부)

---

### 2.9 Reservation Context

| 구성 | 실제 코드 |
|---|---|
| **Aggregate Root** | `Reservation` |
| **Value Objects** | `ReservationBooker(userId)`, `ReservationRestaurantInformation(restaurantId, tableNumber, tableSize)`, `ReservationSchedule(timeTableId, date, day, startTime, endTime)`, `ReservationOccupancy(timeTableOccupancyId, occupiedDatetime)` |
| **Domain Service** | `CreateReservationDomainService` |
| **Status** | `ReservationStatus`: **`RESERVED`, `CANCELLED` 단 2개** |

**중요 관찰**: `Reservation`은 `toSnapshot()`만 존재. 상태 변경 메서드(confirm, complete, noShow)가 **전혀 없음**. `ReservationStatus.CANCELLED`도 사용처가 없음 — 현재 취소 기능 미구현.

---

## 3. 이벤트 흐름

### 3.1 동기 이벤트 (Spring `@EventListener`)

```
CreateRestaurantService.execute()
  → applicationEventPublisher.publishEvent(CreateScheduleEvent(restaurantId))
  → ScheduleEventListener.handleCreateScheduleEvent()  [같은 트랜잭션]
  → CreateScheduleUseCase.execute(CreateScheduleCommand(restaurantId))
  → Schedule 생성 + 영속화
```

- 동기, 같은 스레드, 같은 트랜잭션 — Restaurant 생성 실패 시 Schedule도 롤백

### 3.2 비동기 이벤트 (Outbox + Kafka)

```
CreateTimeTableOccupancyService.execute()
  ↓ [내부]
  saveOccupancy() → TimeTable.attachOccupied() → TimeTableOccupiedDomainEvent 생성
  saveToOutBoxAndPublish() → applicationEventPublisher.publishEvent(domainEvent)

  ↓ [@TransactionalEventListener(BEFORE_COMMIT)]
  TimeTableOccupiedDomainEventListener.handleCreateTimeTableOccupancyEvent()
  → TimeTableOccupiedEvent 생성 (eventId, occurredAt 포함)
  → OutBox 저장 (outboxRepository.save())
  → TimeTableOccupiedOutboxEvent publish

  ↓ [@TransactionalEventListener(AFTER_COMMIT), REQUIRES_NEW 트랜잭션]
  TimeTableOccupiedDomainEventListener.publishKafkaEvent()
  → kafkaTemplate.send(ProducerRecord) — 헤더: x-retry-count=0, x-original-topic
  → 성공: outbox.succeeded() / 실패: outbox.failed()

  ↓ [Kafka Consumer: ParallelStreamProcessor, EOS mode, KEY ordering]
  TimeTableOccupancyKafkaListener.onEventHandler()
  → isReservationExistsUseCase.execute() — 멱등 체크
  → httpInterface.findTimeTableOccupancyInternally() — HTTP Interface로 TimeTable 조회
  → createReservationUseCase.execute(CreateReservationCommand)
```

**Retry 전략**:
```
time-table-occupancy (origin)
  → time-table-occupancy-RETRY-1 (1초 대기 후)
  → time-table-occupancy-RETRY-2 (2초 대기 후)
  → time-table-occupancy-dlt    (헤더: x-error-reason, x-failed-timestamp)
```
- retry count 3회 초과 시 DLT 전송
- backoff: `1000ms * 2.0^retryCount`

**Kafka Consumer 구현**:
- Confluent `ParallelStreamProcessor.createEosStreamProcessor()` — EOS(Exactly Once Semantics) 모드
- `processingOrder: KEY` — 같은 key(timeTableId_timeTableOccupancyId)는 순서 보장
- `@PostConstruct`에서 subscribe + poll 등록, `@PreDestroy`에서 `closeDrainFirst(30s)`

---

## 4. 인프라 계층 패턴

### 4.1 Redis (Redisson)

| 용도 | 구현 | 위치 |
|---|---|---|
| **Distributed Lock** | `GeneralLock`, `FairLock`, `NamedLock` | `redis/redisson/lock/` |
| **Rate Limiter** | Redis 기반 + InMemory fallback (`RateLimiterTemplateState`) | `redis/redisson/ratelimit/` |
| **Semaphore** | TimeTable 동시 점유 제어 | `redis/redisson/timetable/semaphore/` |

**Rate Limiter Fallback**: `AcquireRateLimitRedisAdapter.status()` → `ACTIVATED`이면 Redis, `DEACTIVATED`이면 `AcquireRateLimitInMemoryAdapter` 사용. Redis 장애 시 자동 강등.

### 4.2 AOP Cross-cutting Concerns

| 어노테이션 | 동작 | Order |
|---|---|---|
| `@RateLimiter(key=SpEL, type, rate, ...)` | Redis/InMemory Rate Limit | `LOWEST_PRECEDENCE` |
| `@DistributedLock(key=SpEL, lockType, waitTime, ...)` | Redisson Lock 획득 후 proceed | 명시 없음 |
| `@FeatureFlag` | 기능 플래그 체크 | 명시 없음 |

SpEL 파싱: `SpelParser` 유틸리티 — method args를 변수로 등록해 key expression 평가.

### 4.3 Security

- JWT: Access Token + Refresh Token, `JWTVersion.V1/V2`
- XSS: `CrossSiteScriptFilter` + `RequestWrapper`
- Role: `USER`, `RESTAURANT_OWNER` (`MANAGER`로 매핑), `ROOT` (Admin)
- `SecurityRole.fromRole()` / `SecurityRole.toRole()` — Spring Security ↔ Domain Role 변환

### 4.4 DB 스키마 (Flyway)

| 마이그레이션 | 내용 |
|---|---|
| V1_0 | 초기화 |
| V1_1 ~ V1_3 | User context (password_change_flag, user_change_history 등 ALTER) |
| V1_4 ~ V1_9 | Category context (Cuisine, Nationality, Tag — enum 타입 점진적 확장) |
| V1_10 | Company context |
| V1_11 | Restaurant context |
| V1_12 | Menu context |
| V1_13 | Schedule context (schedule, time_span, holiday, table) |
| V1_14 | FeatureFlag |
| V1_15 | TimeTable + TimetableOccupancy |
| V1_16 | Outbox |
| V1_17 | `ALTER TABLE timetable ADD COLUMN version BIGINT` — Optimistic Lock |
| V1_18 | Reservation |

---

## 5. Application Layer 패턴

### 5.1 Port 구조

```
{domain}/
├── port/
│   ├── input/
│   │   ├── {Action}UseCase.kt          # Inbound Port 인터페이스
│   │   ├── command/request/            # Write 요청 DTO
│   │   └── query/request, response/    # Read 요청/응답 DTO
│   └── output/
│       ├── Load{Entity}.kt             # 도메인 객체로 로드 (영속화 → 도메인)
│       ├── Find{Entity}.kt             # 결과 DTO로 조회 (쿼리 전용)
│       ├── Create{Entity}.kt           # 생성
│       └── Change{Entity}.kt           # 변경
└── usecase/
    └── {Action}Service.kt              # UseCase 구현체
```

**Load vs Find**:
- `Load*`: 도메인 행위를 위해 도메인 객체(`TimeTable`, `Schedule` 등) 반환
- `Find*`: 조회 전용, 결과 DTO 반환. 도메인 객체 불필요

### 5.2 `@UseCase` 어노테이션

`@UseCase`: application-module의 `config/annotations/`에 정의. Spring `@Component` 메타 어노테이션. UseCase 구현체임을 명시.

### 5.3 Command/Query 분리 현황

- **Command 있음**: Restaurant (Create/Change), Menu (Create/Change), Schedule (Create + Add TimeSpan/Holiday), TimeTable (CreateOccupancy), Reservation (Create), User (Create/Change/Resign)
- **Query 있음**: 모든 도메인에 Find UseCase 존재
- **같은 DB**: Read/Write 모두 동일 MySQL — 물리적 Read/Write 분리 없음
- **`schedule/port/mutator/`**: `ScheduleMutator` (영속 → 도메인), `ScheduleInquiryMutator` (도메인 → 영속 입력) — Schedule에만 있는 독특한 레이어

---

## 6. 아키텍처 패턴 적용 현황

| 패턴 | 적용 여부 | 세부 |
|---|---|---|
| Hexagonal Architecture | O | Port/Adapter 명확 분리 |
| DDD | O | Rich Domain Model, Domain Service, Policy, VO, 도메인 이벤트 |
| CQRS | 부분 | UseCase 레벨 Command/Query 분리, 동일 DB |
| Event Sourcing | X | 상태 직접 저장. `snapshot()`은 영속화 DTO |
| Outbox Pattern | O | TimeTable 점유 이벤트만 (1개 토픽) |
| Domain Events | 제한적 | 2개만: `CreateScheduleEvent` (동기), `TimeTableOccupiedDomainEvent` (비동기) |
| Saga | X | — |
| Optimistic Lock | 부분 | TimeTable에만 (V1_17) |

---

## 7. ES 전환 관점 핵심 관찰

### 7.1 v1 `snapshot()` / `toSnapshot()`은 ES Snapshot이 아니다

v1의 `snapshot()` / `toSnapshot()`은 **도메인 객체 → 영속화 DTO 변환** 메서드다.

```
도메인 레이어의 snapshot() 역할:
  Restaurant.snapshot()        → RestaurantSnapshot (JPA Entity에 전달할 flat DTO)
  TimeTable.toSnapshot()       → TimeTableSnapshot
  Schedule.snapshot()          → ScheduleSnapshot
  Reservation.toSnapshot()     → ReservationSnapshot
```

ES의 Snapshot과 완전히 다른 개념:
- v1: Domain → Persistence 방향, 현재 상태 → flat DTO
- ES Snapshot: Event Store → Aggregate 복원 시 N번째 이벤트까지 상태 캐시

**전환 시**: 기존 `*Snapshot` 클래스는 ES 전환 대상이 아니다. Read Model Projection의 DTO로 재활용하거나 폐기 후 재설계.

### 7.2 상태 전이 구멍

**`ReservationStatus`**: `RESERVED`, `CANCELLED` 단 2개.
- `CONFIRMED`, `COMPLETED`, `NO_SHOW` 없음
- 취소 로직도 미구현 (Reservation에 상태 변경 메서드 없음)
- ES 전환 시 이 상태들을 이벤트로 설계해야 함

**`TimeTableConfirmStatus`**: `NOT_CONFIRMED`, `CONFIRMED` 존재하나 Confirm 비즈니스 로직 미구현

### 7.3 `TableStatus.isOccupied()` 버그

```kotlin
enum class TableStatus {
    EMPTY, OCCUPIED;
    fun isOccupied(): Boolean = this == EMPTY  // 버그: EMPTY일 때 true 반환
}
```
실제 사용처에서 `isOccupied()` 대신 `timetableOccupancy != null`로 판단하는 이유.

### 7.4 동시성 제어 복잡도

`CreateTimeTableOccupancyService`에 3중 동시성 제어 (RateLimiter → DistributedLock → Semaphore):
- ES version 기반 Optimistic Concurrency로 단순화 가능
- Semaphore는 "tableSize만큼의 동시 점유" 허용 — ES 전환 시 별도 설계 필요

### 7.5 ES 전환 시 활용 가능한 요소

- **이벤트 흐름**: `TimeTableOccupiedDomainEvent` + Outbox + Kafka 파이프라인 재활용
- **Policy 패턴**: 검증 로직이 도메인에 응집 — Command 검증에 재사용 가능
- **ID 기반 참조**: Aggregate 간 ID 참조 — ES에서 필수인 느슨한 결합
- **Port/Adapter 구조**: Event Store 구현체를 Output Port로 주입 가능

### 7.6 전환 시 도전 과제

- **상태 setter 패턴 전면 교체**: 모든 `update*()`, `change*()`, `manipulate*()` → Command + apply 패턴
- **동기 `CreateScheduleEvent` → 비동기 전환**: UX 영향 (Restaurant 생성 후 Schedule이 eventually 생성)
- **Kafka Consumer → HTTP Interface 결합**: Consumer가 직접 HTTP 호출 — ES 전환 시 Event 내 필요 데이터 포함으로 HTTP 의존 제거 가능
- **`Reservation` 기능 완성**: 상태 전이 (Confirm, Complete, Cancel, NoShow) 신규 설계 필요
- **테이블 정원(tableSize) 동시성**: Semaphore로 구현된 "N좌석 동시 점유" 로직 — ES 전환 시 Aggregate 설계에 반영

---

## 8. 도메인 참조 관계도

```
User / RestaurantOwner
    │ userId                    │ userId
    ▼                           ▼
Company ←─companyId── Restaurant ──restaurantId──→ Schedule
                          │                        (TimeSpan, Holiday, Table)
                          │ restaurantId                    │
                          ▼                                 │ (배치/이벤트로 생성)
                        Menu                                ▼
                                                       TimeTable ──→ TimetableOccupancy
                                                                          │
                                                                  Outbox+Kafka 이벤트
                                                                          ↓
                                                                    Reservation
```

---

## 9. Phase 2 진입을 위한 전제 확인

| 항목 | 상태 |
|---|---|
| 모든 Aggregate 실제 코드 확인 | 완료 |
| 이벤트 흐름 (Outbox+Kafka) 실제 구현 확인 | 완료 |
| 동시성 제어 메커니즘 확인 | 완료 |
| Flyway 마이그레이션 순서 확인 | 완료 |
| v1 snapshot ≠ ES snapshot 명확화 | 완료 |
| 미구현 상태 전이 파악 (`Reservation` 등) | 완료 |
