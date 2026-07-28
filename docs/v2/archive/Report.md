# V2 전환 현실 진단 리포트

> ⚠️ **DEPRECATED (2026-07-28)** — 구 사이클 초기 진단 리포트.
> 본문의 **`reservation_event` 스키마**(§3)는 [[ADR-005-event-store-mysql-table]]로, **낙관적 동시성·`expectedVersion`**(§Phase 1)은 [[ADR-016-optimistic-concurrency-control]] 비관 락으로, **§5 옛 ADR 6개 추천 목록**은 실제 ADR-001~027로 대체됐다.
> 진단(빈약 도메인·도메인 우선 확장)의 **문제 인식**은 여전히 유효하나, 해법·스키마·ADR 번호는 확정본을 따른다. 현행 진실원: [[index]].
>
> 대상 레포: [prototype-reservation-system](https://github.com/newkayak12/prototype-reservation-system)

---

## 목차

1. [현재 상태 요약](#1-현재-상태-요약)
2. [솔직한 문제점](#2-솔직한-문제점)
3. [현실적 V2 로드맵](#3-현실적-v2-로드맵)
4. [안 하는 것 정리](#4-안-하는-것-정리)
5. [ADR 추천](#5-adr-추천)
6. [착수 순서](#6-착수-순서)

---

## 1. 현재 상태 요약

| 항목 | 현황 |
|---|---|
| **Reservation 라이프사이클** | `RESERVED` → `CANCELLED` (2단계뿐) |
| **Command** | `CreateReservation` 1개 |
| **Query** | `IsReservationExists` 1개 |
| **이벤트** | Kafka로 TimeTable → Reservation 방향만 (수신만, 발행 없음) |
| **아키텍처** | 헥사고날 + 포트/어댑터 잘 되어 있음 |
| **Snapshot** | 이미 도메인에 `ReservationSnapshot` 패턴 존재 |

### 현재 Reservation 생성 흐름

```
Kafka Event (time-table-occupancy)
    |
TimeTableOccupancyKafkaListener.onEventHandler()
    |- Check if reservation already exists (IsReservationExistsUseCase)
    |- Fetch TimeTable/Occupancy details via HTTP interface
    +- Create CreateReservationCommand
        |
CreateReservationService.execute()
    |- Call CreateReservationDomainService.createReservation()
    |   |- Validate: userId, restaurantId, timeTableId, timeTableOccupancyId
    |   +- Build Reservation aggregate from VOs -> Convert to ReservationSnapshot
    |- Convert snapshot to CreateReservationInquiry
    +- Call CreateReservationAdapter.command()
        |- Convert inquiry to ReservationEntity
        +- Save via ReservationJpaRepository
```

### 현재 모듈별 파일 배치

```
core-module/reservation/
├── Reservation.kt                          # Aggregate Root
├── service/CreateReservationDomainService.kt
├── vo/                                     # ReservationBooker, Schedule, Occupancy 등
├── policy/                                 # 검증 정책들
├── snapshot/ReservationSnapshot.kt         # 불변 스냅샷
└── exceptions/                             # 도메인 예외

application-module/reservation/
├── port/input/  (CreateReservationUseCase, IsReservationExistsUseCase)
├── port/output/ (CreateReservation, IsReservationExists)
└── usecase/     (CreateReservationService, IsReservationExistsService)

infrastructure-module/reservation/
├── entity/ReservationEntity.kt             # JPA Entity + @Embeddable VOs
└── repository/                             # JPA Repository + Adapter

adapter-module/kafka/
└── TimeTableOccupancyKafkaListener.kt      # Kafka Consumer
```

### 현재 DB 스키마

```sql
CREATE TABLE prototype_reservation.reservation (
    id                             VARCHAR(128) PRIMARY KEY,
    user_id                        VARCHAR(128),
    restaurant_id                  VARCHAR(128),
    timetable_id                   VARCHAR(128),
    reservation_date               DATE,
    reservation_day                ENUM ('MONDAY','TUESDAY',...,'SUNDAY'),
    reservation_time               TIME,
    reservation_seat_size          INT,
    timetable_occupancy_id         VARCHAR(128),
    reservation_occupied_datetime  DATETIME,
    reservation_status             ENUM ('RESERVED', 'CANCELLED'),
    reservation_cancelled_datetime DATETIME,
    INDEX index_user_id_reservation_date_reservation_status
        (user_id, reservation_date, reservation_status)
) ENGINE = innodb DEFAULT CHARACTER SET 'utf8mb4';
```

---

## 2. 솔직한 문제점

### 2.1 라이프사이클이 너무 빈약하다

ES가 빛나려면 Aggregate의 상태 전이가 풍부해야 한다. 현재는:

```
(없음) -> RESERVED -> CANCELLED
```

이걸로는 replay할 이벤트가 2~3개뿐이라 ES의 가치가 드러나지 않는다.

### 2.2 Command가 1개뿐이다

`CreateReservation`만 있고 `CancelReservation`, `ConfirmReservation`, `ModifyReservation` 등이 없다. ES에서 Aggregate가 Command를 받고 불변식 검증 -> Event 생성하는 흐름을 보여주려면 최소 3~4개 Command가 필요하다.

### 2.3 Read 요구가 거의 없다

`IsReservationExists` 하나뿐이라 CQRS의 Read Model 분리 가치가 안 보인다.

### 2.4 결론

**ES 인프라를 먼저 짜는 게 아니라, 도메인을 먼저 풍부하게 만들어야 한다.**

---

## 3. 현실적 V2 로드맵

### Phase 0 -- 도메인 확장 (ES 도입 전에 먼저)

ES를 넣기 전에 Reservation 도메인 자체를 풍부하게 만든다.

#### 라이프사이클 확장

```
REQUESTED -> CONFIRMED -> SEATED -> COMPLETED
                |
                +-> CANCELLED
                +-> NO_SHOW
```

#### Command 추가

| Command | 불변식 | Event |
|---|---|---|
| `RequestReservation` | 슬롯 가용 확인 | `ReservationRequested` |
| `ConfirmReservation` | REQUESTED 상태여야 함 | `ReservationConfirmed` |
| `CancelReservation` | COMPLETED가 아니어야 함 | `ReservationCancelled` |
| `SeatGuest` | CONFIRMED 상태여야 함 | `GuestSeated` |
| `CompleteReservation` | SEATED 상태여야 함 | `ReservationCompleted` |
| `MarkNoShow` | CONFIRMED + 시간 초과 | `NoShowMarked` |

#### Query 추가

| Query | 용도 | Read Model |
|---|---|---|
| 사용자 예약 목록 | 마이페이지 | `user_reservation_summary` |
| 매장 일별 예약 현황 | 점주 대시보드 | `restaurant_daily_schedule` |
| 예약 상세 + 이력 | 분쟁 대응 | `reservation_detail_with_history` |

이렇게 되면 ES/CQRS 도입의 정당성이 생긴다.

---

### Phase 1 -- Event Store + Aggregate 전환

#### 이벤트 테이블

```sql
CREATE TABLE reservation_event (
    sequence       BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id   VARCHAR(128) NOT NULL,
    version        BIGINT NOT NULL,
    event_type     VARCHAR(128) NOT NULL,
    payload        JSON NOT NULL,
    occurred_at    DATETIME(6) NOT NULL,
    UNIQUE KEY uk_aggregate_version (aggregate_id, version)
) ENGINE=InnoDB;
```

#### Aggregate 전환

- `Reservation.kt` -> 순수 POJO (JPA 의존 제거)
- `apply(event)` + Command handler 메서드 추가
- `ReservationEntity.kt`는 그대로 두되 **Projection 결과물**로 역할 전환

#### EventStore 구현

- `infrastructure-module`에 `JdbcReservationEventStore` (JdbcTemplate 기반)
- JPA 쓰지 않음 -- `0.Prepare.md`에서 정리한 대로 dirty check가 무의미

#### Aggregate 코드 골격

```kotlin
class ReservationAggregate private constructor() {
    lateinit var id: String
    var status: ReservationStatus = ReservationStatus.REQUESTED
    var version: Long = 0
    // ... 기타 상태 필드

    // Command: 불변식 검증 -> 이벤트 생성
    fun confirm(): List<ReservationEvent> {
        require(status == ReservationStatus.REQUESTED) { "REQUESTED 상태에서만 확정 가능" }
        return listOf(ReservationConfirmed(id, Instant.now()))
    }

    fun cancel(reason: String): List<ReservationEvent> {
        require(status != ReservationStatus.COMPLETED) { "완료된 예약은 취소 불가" }
        return listOf(ReservationCancelled(id, reason, Instant.now()))
    }

    // Event apply: 상태 변경만 (검증 없음)
    fun apply(event: ReservationEvent) {
        when (event) {
            is ReservationRequested  -> { id = event.aggregateId; status = REQUESTED }
            is ReservationConfirmed  -> { status = CONFIRMED }
            is ReservationCancelled  -> { status = CANCELLED }
            is GuestSeated           -> { status = SEATED }
            is ReservationCompleted  -> { status = COMPLETED }
            is NoShowMarked          -> { status = NO_SHOW }
        }
        version++
    }

    companion object {
        fun replay(events: List<ReservationEvent>): ReservationAggregate =
            ReservationAggregate().also { agg -> events.forEach { agg.apply(it) } }
    }
}
```

#### 모듈 배치

```
core-module/reservation/
├── ReservationAggregate.kt      # POJO, apply() + command handlers
├── command/                     # RequestReservation, Cancel, Confirm...
├── event/                       # ReservationRequested, Confirmed...
└── vo/                          # 기존 VO 재활용

infrastructure-module/reservation/
├── eventstore/
|   └── JdbcReservationEventStore.kt   # append, loadEvents
└── projection/
    └── ReservationProjector.kt        # event -> 기존 reservation 테이블 UPDATE

application-module/reservation/
└── ReservationCommandHandler.kt       # loadEvents -> replay -> handle -> append
```

#### 핵심 원칙

**기존 `reservation` 테이블은 삭제하지 않는다.** Projection 결과로 계속 쓰면 기존 API/쿼리가 깨지지 않는다.

#### Command Handler 흐름

```
1. Command 도착 (예: ConfirmReservationCommand)
2. eventStore.loadEvents(aggregateId)
3. ReservationAggregate.replay(events)  -- 메모리에 POJO 생성
4. aggregate.confirm()                  -- 불변식 검증 + 새 이벤트 반환
5. eventStore.append(newEvents, expectedVersion)  -- 낙관적 동시성
6. projector.project(newEvents)         -- Read Model 갱신
```

---

### Phase 2 -- Read Model 분화 + CQRS 분리

- `QueryService`와 `CommandService` 물리 분리
- Read Model 다중화 (사용자용 summary, 점주용 daily view)
- Projection은 이벤트 구독으로 비동기 갱신
- 결과적 일관성 수용

#### Read Model 테이블 예시

```sql
-- 사용자 예약 요약 (마이페이지)
CREATE TABLE user_reservation_summary (
    reservation_id   VARCHAR(128) PRIMARY KEY,
    user_id          VARCHAR(128) NOT NULL,
    restaurant_name  VARCHAR(256),
    reservation_date DATE,
    reservation_time TIME,
    seat_size        INT,
    status           VARCHAR(32),
    last_updated_at  DATETIME(6),
    INDEX idx_user_date (user_id, reservation_date)
);

-- 매장 일별 예약 현황 (점주 대시보드)
CREATE TABLE restaurant_daily_schedule (
    restaurant_id    VARCHAR(128) NOT NULL,
    reservation_date DATE NOT NULL,
    time_slot        TIME NOT NULL,
    reservation_id   VARCHAR(128),
    user_name        VARCHAR(128),
    seat_size        INT,
    status           VARCHAR(32),
    PRIMARY KEY (restaurant_id, reservation_date, time_slot)
);
```

#### Projector 코드 골격

```kotlin
class ReservationSummaryProjector {
    fun on(event: ReservationEvent) = when (event) {
        is ReservationRequested -> insertSummary(event)
        is ReservationConfirmed -> updateStatus(event.aggregateId, "CONFIRMED")
        is ReservationCancelled -> updateStatus(event.aggregateId, "CANCELLED")
        is GuestSeated          -> updateStatus(event.aggregateId, "SEATED")
        is ReservationCompleted -> updateStatus(event.aggregateId, "COMPLETED")
        is NoShowMarked         -> updateStatus(event.aggregateId, "NO_SHOW")
    }
}
```

---

### Phase 3 -- Snapshot + 고도화 (선택)

- 이벤트 100개 이상 쌓이는 Aggregate가 생기면 그때 도입
- POC 수준에서는 불필요할 가능성 높음

#### Snapshot 테이블 (필요 시)

```sql
CREATE TABLE reservation_snapshot (
    aggregate_id            VARCHAR(128) NOT NULL,
    version                 BIGINT NOT NULL,
    payload                 JSON NOT NULL,
    snapshot_schema_version INT NOT NULL,
    created_at              DATETIME(6) NOT NULL,
    PRIMARY KEY (aggregate_id, version)
);
```

#### Snapshot 로드 흐름

```
1. snapshot = snapshotStore.loadLatest(aggregateId)    -- version=100
2. events   = eventStore.loadAfter(aggregateId, v=100) -- 101, 102, 103
3. aggregate = ReservationAggregate.fromSnapshot(snapshot)
   events.forEach { aggregate.apply(it) }
4. Command 처리...
```

---

## 4. 안 하는 것 정리

| 안 하는 것 | 이유 |
|---|---|
| 전 도메인 ES화 | Store, Holiday, User는 CRUD가 정답 |
| Saga (Phase 1) | Reservation <-> Schedule 연동은 현재 Kafka로 충분 |
| EventStoreDB 도입 | MySQL + UNIQUE 제약으로 충분, 운영 복잡도 불필요 |
| Snapshot (Phase 1) | 이벤트 수가 적어서 replay 비용 미미 |
| Axon Framework | 학습 비용 대비 POC에서 직접 구현이 이해도 높음 |
| 폴리글랏 Read Store | Elasticsearch/Redis 등은 Phase 2 이후 필요 시 |

---

## 5. ADR 추천

> V2의 진짜 가치는 코드보다 **ADR**에 있다.

| 번호 | ADR 주제 | 핵심 논점 |
|---|---|---|
| 1 | 왜 Reservation만 ES인가 | 도메인별 적합도 분석, "안 쓰는 판단" |
| 2 | Phase 0에서 도메인을 먼저 확장한 이유 | ES 전에 도메인 풍부화가 선행되어야 함 |
| 3 | JdbcTemplate vs JPA | ES에서 JPA의 dirty check가 맞지 않는 이유 |
| 4 | 기존 reservation 테이블을 Projection으로 재활용 | 점진 전환 전략, 기존 API 호환 |
| 5 | expectedVersion 동시성 전략 | 동시 예약 충돌 처리 |
| 6 | Saga vs 현재 Kafka 구조 유지 | Phase 1에서 Saga를 도입하지 않은 이유 |

---

## 6. 착수 순서

### 추천: Phase 0부터 시작

ES 인프라 코드를 먼저 짜는 게 아니라 **도메인 먼저, 인프라 나중.**

```
Phase 0: 도메인 확장
  1. ReservationStatus 확장 (REQUESTED, CONFIRMED, SEATED, COMPLETED, CANCELLED, NO_SHOW)
  2. Command/Event 클래스 설계
  3. ReservationAggregate에 상태 전이 로직 + 불변식 구현
  4. Given/When/Then 테스트로 검증
       |
Phase 1: Event Store 도입
  5. reservation_event 테이블 생성 (Flyway)
  6. JdbcReservationEventStore 구현
  7. ReservationCommandHandler 구현 (loadEvents -> replay -> handle -> append)
  8. 기존 reservation 테이블을 Projection으로 전환
  9. expectedVersion 동시성 테스트
       |
Phase 2: CQRS 분리
  10. Read Model 테이블 설계 + 생성
  11. Projector 구현 (이벤트 구독 -> Read Model 갱신)
  12. QueryService / CommandService 물리 분리
  13. 결과적 일관성 테스트
       |
Phase 3: 고도화 (선택)
  14. Snapshot 도입 (필요 시)
  15. 이벤트 버저닝 / Upcasting
  16. Projection 재구축 메커니즘
```

### Given/When/Then 테스트 패턴 (Phase 0 핵심)

```kotlin
// Given: 과거 이벤트들
val events = listOf(
    ReservationRequested(aggregateId, userId, restaurantId, ...),
    ReservationConfirmed(aggregateId, confirmedAt)
)

// When: Command 처리
val aggregate = ReservationAggregate.replay(events)
val newEvents = aggregate.cancel("고객 요청")

// Then: 기대하는 이벤트
assertThat(newEvents).containsExactly(
    ReservationCancelled(aggregateId, "고객 요청", any())
)
assertThat(aggregate.status).isEqualTo(CANCELLED)
```

이 테스트가 통과하면 EventStore 없이도 도메인 로직이 검증된 것이다. 인프라는 그 다음.

---

## 부록: 도메인별 ES 적합도 (재확인)

| 도메인 | 적합도 | 이유 | V2 전략 |
|---|---|---|---|
| **Reservation** | **높음** | 라이프사이클 풍부, 분쟁 대응, "왜 취소" 핵심 | ES + CQRS |
| **Schedule/Slot** | 중간 | 동시성, 시간축 본질 | Phase 3 확장 후보 |
| **Table** | 낮음 | 점유/해제가 있지만 단순 | JPA 유지 |
| **Store** | 낮음 | 마스터성 데이터 | JPA 유지 |
| **Holiday** | 매우 낮음 | 단순 설정 | JPA 유지 |
| **User** | 매우 낮음 | 마스터 | JPA 유지 |

> 면접 변별력은 "ES를 안다"가 아니라 **"안 쓸 곳을 안 쓴다는 판단"** 에서 나온다.