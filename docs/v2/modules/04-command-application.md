# 04 · command-application (유스케이스 · 포트) [신규]

> 허브: [[00-module-index]] | 근거: [[DESIGN-002]] §4.2 · [[DESIGN-019]] (실행 계층 분업) · [[DESIGN-003]] §4

## 1. 책임

유스케이스와 포트. **core 이벤트 타입을 아는 유일한 계층**([[DESIGN-019]] §3 핵심 불변식).

- `port/in` — Command 유스케이스 인터페이스 (command DTO)
- `port/out` — 저장 포트 (`EventStorePort`, `StateStorePort`, `OutboxPort`)
- `service` — 유스케이스 구현: core 애그리거트 조립·실행 + **직렬화/역직렬화** + **core→contract 매핑**

## 2. 의존성

| 항목 | 값 |
|------|-----|
| **허용 의존** | `command-core`, `contract-module`, `shared-module` |
| **금지** | `command-adapter` · `command-infrastructure` · `query` |
| **구현 시점** | **Phase 7-3** |

> application은 core(내부 이벤트)와 contract(통합 이벤트)를 **둘 다** 쥔다 — 그래서 매핑·직렬화의 자연한 자리다. infra는 `StoredEvent`(바이트)만 알고, core 타입은 절대 모른다.

## 3. 사용 라이브러리

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| `command-core` / `contract-module` / `shared-module` | (project) | 도메인·계약 |
| `spring-context` (Boot BOM) | `3.4.5` | `@Service` 유스케이스 빈 |
| `spring-tx` | `6.2.1` | `@Transactional` — append+outbox 단일 트랜잭션 경계([[DESIGN-019]] §4) |
| `spring-retry` | (Boot BOM) | 락-wait 타임아웃 시 bounded 재시도(409 신호 — [[ADR-016]], 낙관적 append 재시도 아님) |
| `jackson-module-kotlin` | (Boot BOM) | `DomainEvent ↔ StoredEvent.payload(JSON)` 직렬화, `eventType` 복원 레지스트리 |
| (테스트) `kotest-runner-junit5`·`-assertions`·`-framework` | `5.9.0` | 유스케이스 행위 명세 — `BehaviorSpec`([[ADR-014]], 레이어 전략상 application도 Kotest로 통일) |
| (테스트) `mockk` / `springmockk` | `1.13.10` / `4.0.2` | 포트 목킹 |

> **주의**: application은 Spring(`@Service`/`@Transactional`)을 쓰지만 **JPA는 안 쓴다** — 저장은 port/out 인터페이스로 위임하고, 구현은 adapter/infrastructure가 한다.

## 4. 구조

```
command-module/command-application
└── com.reservation.command.application
    ├── reservation/
    │   ├── port/in/
    │   │   ├── CreateReservationUseCase.kt
    │   │   └── CancelReservationUseCase.kt
    │   ├── port/out/
    │   │   ├── EventStorePort.kt       # StoredEvent I/O (infra 구현)
    │   │   ├── OutboxPort.kt           # 통합 이벤트 insert (infra 구현)
    │   │   ├── StateStorePort.kt       # 비-ES 상태 저장(infra 구현)
    │   │   └── AggregateLockPort.kt    # 비관 락 L1(Redisson)+L1'(DB 폴백) — infra 구현, [[ADR-016]]
    │   └── service/
    │       ├── CreateReservationService.kt
    │       └── mapper/                 # core DomainEvent → contract IntegrationEvent
    ├── timetable/ …
    └── support/
        ├── EventSerializer.kt          # DomainEvent ↔ StoredEvent (eventType 레지스트리)
        └── AggregateRehydrator.kt      # load→역직렬화→fold(apply)
```

## 5. 핵심 설계 — 쓰기 경로 (단일 트랜잭션 + 비관 락) ([[DESIGN-019]] §4 · [[ADR-016]])

```kotlin
@Service
class CancelReservationService(
    private val eventStore: EventStorePort,
    private val outbox: OutboxPort,
    private val serializer: EventSerializer,
    private val mapper: ReservationEventMapper,
    private val lock: AggregateLockPort,      // L1 Redisson, Redis 불가 시 L1' DB FOR UPDATE 폴백 — [[ADR-016]]
) : CancelReservationUseCase {

    @Transactional  // append + outbox insert = 하나의 트랜잭션·동일 datasource
    override fun cancel(cmd: CancelReservation) {
        lock.withLock(cmd.reservationId) {                             // L1/L1' — 라이터를 큐로 세움
            val stored = eventStore.load(cmd.reservationId)             // List<StoredEvent>
            val events = stored.map(serializer::deserialize)           // List<DomainEvent> (타입 앎)
            val reservation = events.fold(Reservation.empty()) { s, e -> s.apply(e) }  // 리플레이

            val newEvents = reservation.handle(cmd)                    // core 불변식 검증 — reload 후 상태가 실제로 바뀌었으면 422
            eventStore.append(newEvents.map(serializer::serialize))    // L0 UNIQUE 백스톱 — 잔여 충돌 시 AggregateConflictException(409)
            outbox.insert(newEvents.map(mapper::toIntegration))        // 통합 이벤트(같은 txn)
        }
    }
}
```

### 포트 시그니처 ([[DESIGN-019]] §6 · [[ADR-016]])

```kotlin
data class StoredEvent(
    val aggregateId: String, val sequenceNo: Long,
    val eventType: String, val payload: String, val occurredAt: Instant,
)
interface EventStorePort {
    fun load(aggregateId: String, fromSeq: Long = 0): List<StoredEvent>
    fun append(events: List<StoredEvent>)   // outbox insert와 동일 트랜잭션. 잔여 UNIQUE 충돌은 AggregateConflictException으로 번역해 던짐(raw JPA 예외 금지)
}
interface AggregateLockPort {
    fun <T> withLock(aggregateId: String, block: () -> T): T   // 락 범위=단일 aggregateId만, 전역 락 금지. lock-wait 타임아웃 시 LockTimeoutException(409 신호)
}
class AggregateConflictException(aggregateId: String) : RuntimeException()  // L0 잔여 충돌 — 409, bounded 재시도 대상([[ADR-016]] 충돌 처리 규칙)
```

**충돌 처리 판별축**([[ADR-016]]): "재판단 결과가 뒤집혔는가"가 아니라 **도메인 상태가 실제로 커밋되어 바뀌었는가**다. lock-wait 타임아웃·잔여 UNIQUE 위반(락 유실)은 순수 락 경합으로 **409**(재시도로 풀림). reload 후 도메인 상태가 실제로 변경되어 `handle()`이 거절하면 **422**.

## 6. 할 일

- [ ] `EventStorePort` / `OutboxPort` / `StateStorePort` / `AggregateLockPort` 인터페이스
- [ ] `EventSerializer` — `eventType` 복원 레지스트리(명시 등록 — [[DESIGN-009]] §4.2)
- [ ] `AggregateRehydrator` — load→역직렬화→fold
- [ ] `AggregateConflictException`(409)·도메인 거절(422) 예외 매핑
- [ ] 레퍼런스: `OccupyTimeTableUseCase`, `CreateReservationUseCase`, `CancelReservationUseCase`
- [ ] core DomainEvent → contract 통합 이벤트 매핑 계층
- [ ] 비-ES: `StateStorePort` + 상태 저장 유스케이스
- [ ] 단위 테스트 (Kotest `BehaviorSpec` + MockK — [[ADR-014]])

## 7. 미결

- **M-2**: core→contract 매핑 위치 — (a) application (b) adapter. **본 설계는 (a) application 채택**([[DESIGN-019]]).
- ~~동시성: 상시 Redisson 락 vs 락프리 낙관 append+재시도~~ — **확정**: 비관 락(Redisson L1 + DB 폴백 L1') + UNIQUE 백스톱(L0), 409/422 판별축 포함 → [[RFC-014-aggregate-concurrency-control]](합의 2026-06-29) · [[ADR-016]]

## 8. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: core 이벤트 타입을 아는 유일한 계층인 application이 rehydrate·매핑·직렬화·append+outbox 단일 트랜잭션을 모두 소유한다.
**Steel-man**: 타입 소유를 한 계층에 모으면 `infra ↛ core` 불변식이 물리적으로 강제되고 append↔replay가 한 자리에서 대칭으로 닫힌다 — 경계 누수 리스크를 구조로 제거한 정직한 배치다.

**숨은 가정**
1. event_store와 outbox가 **동일 datasource**라 `@Transactional` 하나로 원자성이 성립한다 ([[DESIGN-019]] §9가 C06으로 아직 *미확인*이라 명시한 전제).
2. core→contract 매퍼와 직렬화는 부작용 없는 순수 변환이라 트랜잭션 경계 안에 넣어도 트랜잭션이 길어지지 않는다.
3. 리플레이(load→역직렬화→fold) 비용은 무시할 만하다 — 스냅샷 없이도 크리티컬 패스에 넣을 수 있다(스냅샷 최적화는 [[DESIGN-009]]로 연기).

**반론**

1. `[structural]` · **severity: high — 해소됨(2026-07-19 동기화)** — 이 반론은 §5 코드가 락도 버전 체크도 없이 `load→fold→handle→append`만 수행할 때 성립했다. §5를 `AggregateLockPort`(L1 Redisson + L1' DB 폴백)로 감싸고, `EventStorePort.append`의 잔여 UNIQUE 충돌을 `AggregateConflictException`(409)으로 타입 있게 번역하도록 포트 계약에 명시했다 — "infra는 bytes만, 타입 신호 없음"이라는 경계가 raw 예외로 뚫리는 문제가 해소됐다.

2. `[assumption]` · **severity: high — 해소됨(2026-07-19 동기화)** — 이 반론은 §3 라이브러리 표가 낙관 append 방향(`spring-retry`="낙관적 append 충돌 재시도")을 시사해 [[RFC-014-aggregate-concurrency-control]](합의 2026-06-29)·[[ADR-016]]의 비관 락 채택과 모순됐을 때 성립했다. §3·§5·§7을 ADR-016대로 "비관 락(Redisson L1 + DB 폴백 L1') + UNIQUE 백스톱(L0)"으로 통일했다 — 코드·라이브러리 표·근거 ADR 세 곳이 이제 같은 방향을 가리킨다.

3. `[execution]` · **severity: medium** — 하나의 `@Service`가 rehydrate·fold·직렬화·매핑·트랜잭션을 전부 짊어진다. 취소 한 건마다 전체 이벤트 스트림을 load→역직렬화→fold 하므로 지연이 이벤트 수에 선형이고(스냅샷 부재), 단위 테스트는 포트 5개(락 포함) 목킹 × 직렬화 레지스트리 × 매퍼 × 리플레이 상태 조합으로 폭발한다. "매핑·직렬화의 자연한 자리"라는 정당화는 *타입을 쥔다 = 실행 책임을 전부 진다*를 등치했지만, 타입 소유(불변식)와 오케스트레이션 비대(SRP)는 분리 가능한 문제다. **선례**: no clear precedent — speculative concern(리플레이 비용은 실측 전).

**핵심 취약점**: 동시성 방향(구 반론 1·2)은 ADR-016 동기화로 해소됐다. **남은 핵심 취약점은 반론 3** — 하나의 `@Service`가 타입 소유(core→contract 매핑·직렬화, 정당한 책임)와 오케스트레이션 비대(rehydrate·락·트랜잭션, SRP 위반 소지)를 함께 짊어져, 리플레이 비용·테스트 조합 폭발이 실측 없이 방치돼 있다는 점이다.

**가역성**: 대체로 reversible (구현 전 문서). `EventStorePort.append`/`AggregateLockPort` 시그니처는 infra 구현·테스트가 붙는 순간 one-way door에 근접한다 — 지금 확정해 둔 것이 이후 마이그레이션 비용을 줄인다.
