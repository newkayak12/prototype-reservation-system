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
| `spring-retry` | (Boot BOM) | 낙관적 append 충돌 시 bounded 재시도(선택 — [[DESIGN-003]] 자기리뷰 동시성) |
| `jackson-module-kotlin` | (Boot BOM) | `DomainEvent ↔ StoredEvent.payload(JSON)` 직렬화, `eventType` 복원 레지스트리 |
| (테스트) `junit-jupiter` | `5.10.2` | 유스케이스 단위 테스트(레이어 전략상 application=JUnit) |
| (테스트) `mockk` / `springmockk` | `1.13.10` / `4.0.2` | 포트 목킹 |
| (테스트) `assertj-core` | `3.24.2` | 검증 |

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
    │   │   └── StateStorePort.kt       # 비-ES 상태 저장(infra 구현)
    │   └── service/
    │       ├── CreateReservationService.kt
    │       └── mapper/                 # core DomainEvent → contract IntegrationEvent
    ├── timetable/ …
    └── support/
        ├── EventSerializer.kt          # DomainEvent ↔ StoredEvent (eventType 레지스트리)
        └── AggregateRehydrator.kt      # load→역직렬화→fold(apply)
```

## 5. 핵심 설계 — 쓰기 경로 (단일 트랜잭션) ([[DESIGN-019]] §4)

```kotlin
@Service
class CancelReservationService(
    private val eventStore: EventStorePort,
    private val outbox: OutboxPort,
    private val serializer: EventSerializer,
    private val mapper: ReservationEventMapper,
) : CancelReservationUseCase {

    @Transactional  // append + outbox insert = 하나의 트랜잭션·동일 datasource
    override fun cancel(cmd: CancelReservation) {
        val stored = eventStore.load(cmd.reservationId)          // List<StoredEvent>
        val events = stored.map(serializer::deserialize)          // List<DomainEvent> (타입 앎)
        val reservation = events.fold(Reservation.empty()) { s, e -> s.apply(e) }  // 리플레이

        val newEvents = reservation.handle(cmd)                   // core 불변식 검증
        eventStore.append(newEvents.map(serializer::serialize))   // 진실원(StoredEvent)
        outbox.insert(newEvents.map(mapper::toIntegration))       // 통합 이벤트(같은 txn)
    }
}
```

### 포트 시그니처 ([[DESIGN-019]] §6)

```kotlin
data class StoredEvent(
    val aggregateId: String, val sequenceNo: Long,
    val eventType: String, val payload: String, val occurredAt: Instant,
)
interface EventStorePort {
    fun load(aggregateId: String, fromSeq: Long = 0): List<StoredEvent>
    fun append(events: List<StoredEvent>)   // outbox insert와 동일 트랜잭션
}
```

## 6. 할 일

- [ ] `EventStorePort` / `OutboxPort` / `StateStorePort` 인터페이스
- [ ] `EventSerializer` — `eventType` 복원 레지스트리(명시 등록 — [[DESIGN-009]] §4.2)
- [ ] `AggregateRehydrator` — load→역직렬화→fold
- [ ] 레퍼런스: `OccupyTimeTableUseCase`, `CreateReservationUseCase`, `CancelReservationUseCase`
- [ ] core DomainEvent → contract 통합 이벤트 매핑 계층
- [ ] 비-ES: `StateStorePort` + 상태 저장 유스케이스
- [ ] 단위 테스트 (JUnit + MockK)

## 7. 미결

- **M-2**: core→contract 매핑 위치 — (a) application (b) adapter. **본 설계는 (a) application 채택**([[DESIGN-019]]).
- **동시성**: 상시 Redisson 락 vs 락프리 낙관 append+재시도 — 도메인별 혼용 방향([[DESIGN-003]] 자기리뷰). 실패 처리(재시도 상한·백오프·409 매핑)를 여기 명시.

## 8. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: core 이벤트 타입을 아는 유일한 계층인 application이 rehydrate·매핑·직렬화·append+outbox 단일 트랜잭션을 모두 소유한다.
**Steel-man**: 타입 소유를 한 계층에 모으면 `infra ↛ core` 불변식이 물리적으로 강제되고 append↔replay가 한 자리에서 대칭으로 닫힌다 — 경계 누수 리스크를 구조로 제거한 정직한 배치다.

**숨은 가정**
1. event_store와 outbox가 **동일 datasource**라 `@Transactional` 하나로 원자성이 성립한다 ([[DESIGN-019]] §9가 C06으로 아직 *미확인*이라 명시한 전제).
2. core→contract 매퍼와 직렬화는 부작용 없는 순수 변환이라 트랜잭션 경계 안에 넣어도 트랜잭션이 길어지지 않는다.
3. 리플레이(load→역직렬화→fold) 비용은 무시할 만하다 — 스냅샷 없이도 크리티컬 패스에 넣을 수 있다(스냅샷 최적화는 [[DESIGN-009]]로 연기).

**반론**

1. `[structural]` · **severity: high** — 포트 시그니처가 동시성 가드를 표현하지 못한다. `EventStorePort.append(events: List<StoredEvent>)`에는 `expectedVersion`/`expectedSeq`가 없다. 그런데 §5 코드는 락도 버전 체크도 전혀 없이 `load→fold→handle→append`만 한다 — 두 요청이 같은 aggregate를 동시에 취소하면 lost update 또는 seq 중복이다. 충돌은 결국 UNIQUE(aggregate_id, sequence_no) 위반이 infra에서 raw `DataIntegrityViolation`으로 튀어 올라오는 형태가 되는데, 이는 이 문서가 "infra는 bytes만, 타입 신호 없음"이라 선언한 바로 그 경계를 타입 없는 예외로 관통한다. **선례**: [[DESIGN-003]] line 187–188은 UNIQUE가 safety 백스톱, Redisson(L1)이 정상 경로임을 명시한다 — 이 모듈 문서의 쓰기 경로 샘플은 그 둘을 모두 뺐다.

2. `[assumption]` · **severity: high** — 이 문서의 동시성 방향이 근거 ADR과 정면 모순이다. §3 라이브러리 표는 `spring-retry`를 "낙관적 append 충돌 시 bounded 재시도"로 올려 **낙관 append** 방향을 시사한다. 그러나 [[DESIGN-003]] line 133은 "낙관적 락만 사용"을 [[16.optimistic-concurrency-control]] 개정에서 **비관 락(Redisson)+UNIQUE로 변경**했다고 확정한다("낙관적 락은 충돌 시 재시도 부담이 예약 컨텍스트에서 과함"). 이 문서는 이미 기각된 방향의 라이브러리를 선반영하면서, §5 코드에는 낙관·비관 어느 가드도 넣지 않아 세 곳(코드·라이브러리 표·근거 ADR)이 서로 다른 상태를 가리킨다. **선례**: [[DESIGN-003]] line 133 · line 190(도메인별 혼용은 "방향"일 뿐 미확정).

3. `[execution]` · **severity: medium** — 하나의 `@Service`가 rehydrate·fold·직렬화·매핑·트랜잭션을 전부 짊어진다. 취소 한 건마다 전체 이벤트 스트림을 load→역직렬화→fold 하므로 지연이 이벤트 수에 선형이고(스냅샷 부재), 단위 테스트는 포트 4개 목킹 × 직렬화 레지스트리 × 매퍼 × 리플레이 상태 조합으로 폭발한다. "매핑·직렬화의 자연한 자리"라는 정당화는 *타입을 쥔다 = 실행 책임을 전부 진다*를 등치했지만, 타입 소유(불변식)와 오케스트레이션 비대(SRP)는 분리 가능한 문제다. **선례**: no clear precedent — speculative concern(리플레이 비용은 실측 전).

**핵심 취약점**: 미결로 미룬 동시성 결정(§7 M-2/동시성)이 이미 포트 계약에 각인돼 버렸다. `append(events)`에 버전이 없다는 것은 "가드 없음"을 기본값으로 굳힌 것이고, 나중에 낙관 CAS나 비관 락으로 확정하면 포트 시그니처 변경 → infra 구현·테스트 동반 변경이 강제된다. 즉 "나중에 정한다"가 실제로는 특정 방향을 선택해 버린 상태다.

**가역성**: 대체로 reversible (구현 전 문서). 단 `EventStorePort.append` 시그니처는 infra 구현·테스트가 붙는 순간 one-way door에 근접한다 — `expectedVersion`을 지금 넣지 않으면 이후 추가는 마이그레이션이 된다.
