# Event Sourcing 전환 포인트

> ⚠️ **DEPRECATED (2026-07-28)** — 구 사이클 `20260604-v2-event-sourcing-cqrs` Phase 2 초안.
> 아래의 **낙관적 동시성**(§2·§판단기준)은 [[ADR-016-optimistic-concurrency-control]]에서 **비관 락으로 전환**됐고,
> **미결 상태의 Event Store 옵션·`domain_events` 스키마**(§4)는 [[ADR-005-event-store-mysql-table]]·[[ADR-022-event-identity]]로 확정됐다.
> §6 미결 사항도 전부 확정됨. 현행 진실원: [[index]] · `adr/` · `design_doc/`. 이 문서는 전환 판정의 **역사적 근거**로만 유효하다.

> Cycle: `20260604-v2-event-sourcing-cqrs` (구) | Phase: 2 - 전환 포인트 도출
> 선행 문서: [01-v1-architecture-analysis.md](./01-v1-architecture-analysis.md)

## 1. 전환 전략: 전부가 아니라 선별

모든 Aggregate를 Event Sourcing으로 전환하지 않는다. ES가 실질적 가치를 주는 도메인만 전환하고, 나머지는 전통 방식 유지 또는 Read Model로만 활용한다.

### 판단 기준

| 기준 | 설명 |
|---|---|
| **상태 변경 빈도** | 변경이 잦을수록 이벤트 이력의 가치 ↑ |
| **이력 추적 필요성** | "누가 언제 왜 바꿨는지"가 비즈니스 요구인가 |
| **이벤트 흐름 존재** | 이미 이벤트 기반 흐름이 있으면 전환 비용 ↓ |
| **동시성 이슈** | Optimistic concurrency가 필요한 곳 — ES의 version 기반 충돌 해소가 자연스러움 |
| **Aggregate 복잡도** | 너무 단순하면 ES 오버헤드만 늘어남 |

## 2. 도메인별 전환 판정

### ES 전환 대상 (CONVERT)

#### 2.1 TimeTable + TimetableOccupancy
**전환 근거:**
- 이미 `TimeTableOccupiedDomainEvent` 존재
- Outbox + Kafka 파이프라인 작동 중
- Semaphore + Optimistic Lock으로 동시성 제어 → ES version 기반으로 단순화 가능
- 상태 전이가 명확: `EMPTY → OCCUPIED → UNOCCUPIED`
- 좌석 점유/해제 이력 추적이 비즈니스 가치

**전환 시 이벤트 설계:**
```
TimeTableCreated { restaurantId, date, day, startTime, endTime, tableNumber, tableSize }
TimeTableOccupied { userId, occupiedDatetime }
TimeTableUnoccupied { unoccupiedDatetime }
TimeTableConfirmed { confirmedBy }
TimeTableCancelled { reason }
```

**전환 포인트:**
- `TimeTable.attachOccupied()` → `TimeTableOccupied` 이벤트 발행 + apply
- `TimeTable.detachOccupied()` → `TimeTableUnoccupied` 이벤트 발행 + apply
- `tableStatus` 필드 제거 → 이벤트로부터 재구성
- Semaphore 대신 ES version 기반 optimistic concurrency

#### 2.2 Reservation
**전환 근거:**
- Kafka Consumer에서 생성 — 이미 이벤트 트리거 기반
- 예약 상태 전이가 비즈니스 핵심: `RESERVED → CONFIRMED → COMPLETED / CANCELLED / NO_SHOW`
- 이력 추적 필수 (예약 변경/취소 히스토리)
- 현재 `ReservationStatus` enum으로 단순 관리 → 풍부한 이벤트 이력으로 확장

**전환 시 이벤트 설계:**
```
ReservationCreated { bookerId, restaurantId, timeTableId, occupancyId, schedule }
ReservationConfirmed { confirmedBy, confirmedAt }
ReservationCancelled { cancelledBy, reason, cancelledAt }
ReservationCompleted { completedAt }
ReservationNoShow { markedBy, markedAt }
ReservationModified { field, oldValue, newValue }
```

**전환 포인트:**
- Kafka Consumer → Event Store에 `ReservationCreated` 저장
- `reservationStatus` 필드 제거 → 이벤트 시퀀스로 현재 상태 도출
- 새 상태 전이 (Confirm, Cancel, Complete, NoShow) 추가

#### 2.3 Restaurant
**전환 근거:**
- 가장 복잡한 Aggregate (10+ VO)
- 다수 변경 메서드: `updateDescription`, `updateLocation`, `updateContact`, `manipulate*`
- 변경 이력 추적 가치 (메뉴, 영업시간, 위치 변경 히스토리)
- `CreateScheduleEvent` 발행 — 이벤트 기반 연쇄 이미 존재

**전환 시 이벤트 설계:**
```
RestaurantRegistered { companyId, userId, name, introduce, phone, address, coordinate }
RestaurantDescriptionChanged { name, introduce }
RestaurantLocationChanged { zipCode, address, detail, latitude, longitude }
RestaurantContactChanged { phone }
RestaurantRoutineUpdated { workingDays }
RestaurantPhotoAdded { photoUrl }
RestaurantPhotoRemoved { photoUrl }
RestaurantTagsUpdated { tagIds }
RestaurantNationalitiesUpdated { nationalityIds }
RestaurantCuisinesUpdated { cuisineIds }
RestaurantDeactivated { reason }
```

**전환 포인트:**
- 각 `update*` / `manipulate*` 메서드 → 이벤트 발행 + apply 패턴
- `CreateScheduleEvent` → `RestaurantRegistered` 이벤트의 downstream handler로 통합
- v1의 `snapshot()`은 영속화 DTO이므로 ES Snapshot과 무관 — ES Snapshot(리플레이 최적화)은 별도 설계 필요
- ES Snapshot 저장 주기 결정 (N 이벤트마다)

### ES 전환 대상 (PARTIAL — Schedule)

#### 2.4 Schedule
**전환 근거:**
- Restaurant에 종속, TimeTable 생성의 기반
- Holiday/TimeSpan 추가/삭제 이력이 유용
- 단, 구조가 단순해서 full ES는 오버엔지니어링 가능

**전환 방향:** Restaurant ES의 일부로 통합하거나, 별도 Aggregate로 간소화된 ES 적용

```
ScheduleCreated { restaurantId }
TimeSpanAdded { day, startTime, endTime }
TimeSpanRemoved { timeSpanId }
HolidayAdded { date }
HolidayRemoved { holidayId }
ScheduleActivated {}
ScheduleDeactivated {}
```

### ES 비전환 대상 (KEEP AS-IS)

#### 2.5 Menu
**이유:** 단순 CRUD. 이벤트 이력의 비즈니스 가치 낮음. Read Model로 충분.
**대안:** Restaurant ES에서 `MenuAdded`/`MenuRemoved` 이벤트만 발행, Menu 자체는 전통 방식 유지.

#### 2.6 User / Authenticate
**이유:** 인증은 ES보다 전통 방식이 적합. 비밀번호 변경 이력은 보안 이슈. 접근 기록은 이미 `AccessHistory`로 관리 중.
**대안:** 필요하면 `UserPasswordChanged`, `UserLocked` 등 감사 이벤트만 발행 (Event Store에 저장하지 않고 감사 로그 용도).

#### 2.7 Company / Category
**이유:** 거의 변경 없는 참조 데이터. ES 효과 제로.
**대안:** Read Model로만 유지. CQRS의 Read side에서 직접 쿼리.

## 3. Read/Write 분리 전략 (CQRS)

### Write Side (Command)
```
┌─────────────────────────────────────────┐
│              Command Side               │
│                                         │
│  Command → Aggregate → Domain Event     │
│                    ↓                    │
│              Event Store (MySQL/별도)    │
│                    ↓                    │
│           Event Bus (Kafka)             │
└─────────────────────────────────────────┘
```

### Read Side (Query)
```
┌─────────────────────────────────────────┐
│              Query Side                 │
│                                         │
│  Event Consumer → Projection Builder    │
│                       ↓                │
│              Read DB (MySQL/Redis)      │
│                       ↓                │
│              Query Handler → Response   │
└─────────────────────────────────────────┘
```

### Projection 전략

| Read Model | Source Events | Storage | 용도 |
|---|---|---|---|
| `RestaurantListView` | RestaurantRegistered, *Changed | MySQL (Read DB) | 레스토랑 목록/검색 |
| `RestaurantDetailView` | All Restaurant events | MySQL (Read DB) | 상세 조회 |
| `TimeTableAvailabilityView` | TimeTableCreated, Occupied, Unoccupied | Redis (실시간) | 잔여 좌석 실시간 조회 |
| `ReservationHistoryView` | All Reservation events | MySQL (Read DB) | 예약 이력 |
| `RestaurantScheduleView` | ScheduleCreated, TimeSpan/Holiday Added/Removed | MySQL (Read DB) | 영업 스케줄 조회 |

## 4. Event Store 설계 방향

### 옵션 비교

| 옵션 | 장점 | 단점 |
|---|---|---|
| **MySQL Event Table** | 기존 인프라 재활용, 트랜잭션 보장 | 고성능 쓰기에 한계, 스냅샷 관리 수동 |
| **Kafka as Event Store** | 이미 Kafka 있음, 자연스러운 pub/sub | 조회 어려움, 컴팩션 관리 |
| **별도 Event Store (Axon/EventStoreDB)** | 전용 기능 (projection, subscription) | 인프라 추가, 학습 비용 |

**제안:** MySQL Event Table + Kafka (이벤트 전파) 조합
- Event Store는 MySQL에 append-only로 저장 (기존 인프라)
- Kafka는 이벤트 전파 용도 (Read Model 업데이트, 외부 시스템 연동)
- 추후 EventStoreDB 등으로 마이그레이션 가능한 추상화 유지

### Event Table 스키마 (초안)
```sql
CREATE TABLE domain_events (
    sequence_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id  VARCHAR(36) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type    VARCHAR(200) NOT NULL,
    event_version INT NOT NULL DEFAULT 1,
    payload       JSON NOT NULL,
    metadata      JSON,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_aggregate (aggregate_type, aggregate_id, sequence_id),
    INDEX idx_event_type (event_type, created_at)
);

CREATE TABLE aggregate_snapshots (
    aggregate_id   VARCHAR(36) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    version        INT NOT NULL,
    payload        JSON NOT NULL,
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (aggregate_type, aggregate_id)
);
```

## 5. 전환 로드맵

> 상세 로드맵은 [00-roadmap.md](./00-roadmap.md) 참조.
> 이 섹션은 전환 포인트 관점의 기술적 순서만 요약한다.

```
1. 도메인 확장 + 이벤트 스토밍 (전환 대상 도메인의 이벤트 전수 식별)
2. Design Docs (Event Store, Aggregate Base, CQRS Read Model, 모듈 구조)
3. ADR (Design Doc 확정 후)
4. 구현 (TimeTable 파일럿 → Reservation → Restaurant → Schedule)
5. Kafka 심화 + 성능 검증 + 인프라
```

## 6. 미결 사항 (사용자 리뷰 필요)

- [ ] 도메인별 전환/비전환 판정 동의 여부
- [ ] Event Store를 MySQL Event Table로 할지, 다른 옵션 검토 필요한지
- [ ] Schedule을 Restaurant에 통합할지 별도 Aggregate로 유지할지
- [ ] Menu를 ES 비전환으로 확정할지, 학습 목적으로 포함할지
- [ ] User/Auth 감사 이벤트 필요 범위
