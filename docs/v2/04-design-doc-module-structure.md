# Design Doc: V2 모듈 구조

> Cycle: `20260604-v2-event-sourcing-cqrs` | Phase: 4 — Design Docs
> 전제: V1 마이그레이션 없음. 0부터 새로 설계.

---

## 1. 결정 요약

V1 헥사고날 구조를 기반으로 **event-store-module**, **projection-module** 2개를 추가한다.
총 모듈: 8개 (batch-module 제외).

---

## 2. 모듈 구조

```
reservation/
├── shared-module          # Enum, 공통 유틸리티
├── core-module            # Domain Entities, DomainEvent, EventSourcingAggregate
├── event-store-module     # ★ EventStore Port + StoredEvent 모델
├── application-module     # Command Handlers, Query Handlers, Use Case Ports
├── projection-module      # ★ Projector, Read Model 정의
├── infrastructure-module  # Flyway 마이그레이션
├── adapter-module         # REST, JPA(write/read), Kafka, Security, Redis
└── test-module            # FixtureMonkey, 공통 픽스처
```

---

## 3. 의존성 방향

```
shared-module
      ↑
core-module
      ↑
event-store-module
      ↑              ↑
application-module   projection-module
      ↑                    ↑
           adapter-module
                ↑
        infrastructure-module
```

**규칙**:
- `core-module` → 외부 의존 없음 (shared-module 제외)
- `event-store-module` → core-module만 의존 (DomainEvent 타입 참조)
- `application-module` → core-module + event-store-module
- `projection-module` → core-module + event-store-module
- `adapter-module` → 위 모두를 조립

---

## 4. 모듈별 책임

### 4.1 core-module

```
core/
├── {domain}/
│   ├── {Aggregate}.kt               # EventSourcingAggregate 상속
│   ├── event/
│   │   ├── {Domain}Created.kt       # DomainEvent 구현체
│   │   └── {Domain}Changed.kt
│   ├── service/
│   └── vo/
└── support/
    ├── DomainEvent.kt               # sealed interface
    └── EventSourcingAggregate.kt    # apply() 추상 메서드
```

**핵심**: DomainEvent는 도메인 개념이다. 인프라가 아님.

```kotlin
// DomainEvent — core-module
sealed interface DomainEvent {
    val aggregateId: String
    val occurredAt: Instant
}

// EventSourcingAggregate — core-module
abstract class EventSourcingAggregate {
    val domainEvents: MutableList<DomainEvent> = mutableListOf()
    var version: Long = 0L

    protected abstract fun apply(event: DomainEvent)

    protected fun raise(event: DomainEvent) {
        apply(event)
        domainEvents.add(event)
    }
}
```

### 4.2 event-store-module

```
event-store/
├── port/
│   └── EventStore.kt                # Output Port
├── model/
│   ├── StoredEvent.kt
│   ├── EventStream.kt
│   └── AggregateId.kt
└── exception/
    └── OptimisticConcurrencyException.kt
```

**핵심**: EventStore는 Port(인터페이스)만 존재. 구현체는 adapter-module.

```kotlin
// EventStore Port — event-store-module
interface EventStore {
    fun append(aggregateId: AggregateId, events: List<DomainEvent>, expectedVersion: Long)
    fun load(aggregateId: AggregateId): EventStream
    fun loadAfter(aggregateId: AggregateId, afterVersion: Long): EventStream
}

// StoredEvent — event-store-module
data class StoredEvent(
    val sequenceId: Long,
    val aggregateId: String,
    val aggregateType: String,
    val eventType: String,
    val payload: String,       // JSON
    val version: Long,
    val occurredAt: Instant,
)
```

**이 모듈을 분리하는 이유**: EventStore는 일반 Repository와 다르다는 사실을 모듈 경계로 강조. append-only, version 기반 낙관적 동시성이 일반 CRUD와 다름.

### 4.3 application-module

```
application/
└── {domain}/
    ├── port/
    │   ├── input/
    │   │   ├── command/             # Command DTO + UseCase 인터페이스
    │   │   └── query/               # Query DTO + UseCase 인터페이스
    │   └── output/                  # Load Port (Write side)
    └── usecase/
        ├── command/                 # Command Handler 구현
        └── query/                   # Query Handler 구현
```

**핵심**: Command Handler는 EventStore에서 Aggregate 복원 → Command 처리 → 이벤트 저장.
Query Handler는 Read Model(projection-module)에서 직접 조회. Aggregate 복원 불필요.

```kotlin
// Command Handler 패턴 — application-module
@UseCase
class OccupyTimeTableService(
    private val eventStore: EventStore,
) : OccupyTimeTableUseCase {
    @Transactional
    override fun execute(command: OccupyTimeTableCommand) {
        val stream = eventStore.load(AggregateId.of(command.timeTableId))
        val timeTable = TimeTable.reconstitute(stream)  // 이벤트로 복원

        timeTable.occupy(command.userId)                // 도메인 행위

        eventStore.append(
            AggregateId.of(command.timeTableId),
            timeTable.domainEvents,
            timeTable.version - timeTable.domainEvents.size,  // expectedVersion
        )
    }
}

// Query Handler 패턴 — application-module
@UseCase
class FindAvailableTimeTablesService(
    private val timeTableAvailabilityReadPort: TimeTableAvailabilityReadPort,
) : FindAvailableTimeTablesUseCase {
    override fun execute(query: FindAvailableTimeTablesQuery) =
        timeTableAvailabilityReadPort.findAvailable(query.restaurantId, query.date)
}
```

### 4.4 projection-module

```
projection/
├── Projector.kt                      # 인터페이스
├── ProjectionRegistry.kt
└── {domain}/
    ├── {Domain}Projector.kt          # 이벤트 → Read Model 변환 로직
    ├── model/
    │   └── {Domain}ReadModel.kt      # Read Model 정의 (JPA 없음)
    └── port/
        └── {Domain}ReadRepository.kt # Output Port (구현은 adapter-module)
```

**핵심**: Read Model은 Write Model(Aggregate)의 복사본이 아니다. 이벤트로부터 파생된다.
Projector는 순수 함수에 가깝다 — 이벤트를 받아 Read Model 상태를 변환.

```kotlin
// Projector 인터페이스 — projection-module
interface Projector<E : DomainEvent> {
    fun handle(event: E)
}

// TimeTable Projector 예시 — projection-module
@Component
class TimeTableAvailabilityProjector(
    private val readRepository: TimeTableAvailabilityReadRepository,
) : Projector<TimeTableDomainEvent> {

    fun handle(event: TimeTableCreated) {
        readRepository.save(TimeTableAvailabilityView(
            timeTableId = event.aggregateId,
            restaurantId = event.restaurantId,
            available = true,
        ))
    }

    fun handle(event: TimeTableOccupied) {
        readRepository.updateAvailability(event.aggregateId, available = false)
    }

    fun handle(event: TimeTableUnoccupied) {
        readRepository.updateAvailability(event.aggregateId, available = true)
    }
}
```

**이 모듈을 분리하는 이유**: Projection 로직이 adapter-module에 있으면 "그냥 JPA Repository 업데이트"처럼 보인다. 별도 모듈로 분리해야 "Read Model은 이벤트로부터 독립적으로 구축된다"는 개념이 물리적으로 보인다.

### 4.5 adapter-module

```
adapter/
├── persistence/
│   ├── write/                        # Event Store JPA 구현
│   │   ├── EventStoreJpaAdapter.kt   # EventStore Port 구현
│   │   ├── StoredEventJpaEntity.kt
│   │   └── StoredEventJpaRepository.kt
│   └── read/                         # Read Model JPA 구현
│       ├── {Domain}ReadJpaAdapter.kt # ReadRepository Port 구현
│       ├── {Domain}ReadJpaEntity.kt
│       └── {Domain}ReadJpaRepository.kt
├── messaging/
│   ├── publisher/                    # EventStore → Kafka 발행 (Outbox 패턴)
│   └── consumer/                     # Kafka → Projector 호출
└── api/
    ├── command/                      # Command REST Controllers
    └── query/                        # Query REST Controllers
```

**핵심**: Write DB(events 테이블)와 Read DB(view 테이블)가 같은 MySQL이지만 **논리적으로 분리**. 추후 물리적 분리(Read DB → Redis/별도 MySQL)로 확장 가능.

---

## 5. 데이터 흐름

### Write Path (Command)

```
REST POST /timetables/{id}/occupy
  ↓
CommandController (adapter)
  ↓
OccupyTimeTableUseCase (application)
  ↓
EventStore.load() → Aggregate 복원 (event-store-module Port → adapter 구현)
  ↓
TimeTable.occupy() → TimeTableOccupied 이벤트 생성 (core-module)
  ↓
EventStore.append() → domain_events 테이블에 저장
  ↓
(AFTER_COMMIT) KafkaEventPublisher → Kafka 발행
```

### Read Path (Query)

```
REST GET /timetables/available?restaurantId=&date=
  ↓
QueryController (adapter)
  ↓
FindAvailableTimeTablesUseCase (application)
  ↓
TimeTableAvailabilityReadRepository (projection-module Port → adapter 구현)
  ↓
timetable_availability_view 테이블 직접 조회
```

### Projection Path (이벤트 → Read Model 갱신)

```
Kafka Consumer (adapter)
  ↓
TimeTableAvailabilityProjector.handle(event) (projection-module)
  ↓
TimeTableAvailabilityReadRepository.save() (adapter 구현)
  ↓
timetable_availability_view 테이블 갱신
```

---

## 6. 모듈별 테스트 전략

| 모듈 | 테스트 범위 | 도구 |
|---|---|---|
| core-module | Aggregate 상태 전이, apply 로직 | Kotest, 순수 단위 테스트 |
| event-store-module | 포트 계약 검증 | 인터페이스 레벨만 |
| application-module | Command/Query Handler | JUnit + MockK (EventStore mock) |
| projection-module | Projector 변환 로직 | Kotest, 순수 단위 테스트 |
| adapter-module | EventStore JPA, Read JPA | Testcontainers (MySQL) |

---

## 7. 미결 사항

- [ ] Kafka Consumer가 Projector를 직접 호출 vs application-module을 거쳐 호출
- [ ] Read DB를 MySQL 단일 인스턴스로 시작할지, 별도 스키마로 분리할지
- [ ] Aggregate Snapshot 저장 주기 (N 이벤트마다? 타임 기반?)
- [ ] projection-module이 Kafka 의존성을 가져야 하는지 (현재: adapter만 Kafka 의존)
