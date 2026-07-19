# Event Sourcing 학습 정리

> Reservation 시스템 V2 전환 POC를 위한 Event Sourcing / CQRS / DDD 개념 정리
>
> 대상 레포: [prototype-reservation-system](https://github.com/newkayak12/prototype-reservation-system)

---

## 목차

1. [도메인 모델은 어디에 존재하는가](#1-도메인-모델은-어디에-존재하는가)
2. [이벤트와 트랜잭션 경계](#2-이벤트와-트랜잭션-경계)
3. [Event Sourcing과 DDD의 관계](#3-event-sourcing과-ddd의-관계)
4. [Aggregate 단위와 Replay](#4-aggregate-단위와-replay)
5. [Snapshot 전략](#5-snapshot-전략)
6. [JPA와 Event Sourcing](#6-jpa와-event-sourcing)
7. [Read Model과 Projection](#7-read-model과-projection)
8. [DB는 도구일 뿐](#8-db는-도구일-뿐)
9. [POC 전환 전략](#9-poc-전환-전략)
10. [CQRS와 Event Sourcing은 별개](#10-cqrs와-event-sourcing은-별개)

---

## 1. 도메인 모델은 어디에 존재하는가

**도메인 모델(Aggregate)은 Write 쪽에만 존재한다.** Read 쪽은 도메인 모델이 아니라 "쿼리용 투영(Projection/Read Model)"이다.

### Write side (Command)
- Aggregate = 도메인 모델 본체
- 상태는 이벤트 스트림을 replay해서 메모리에 복원
- Snapshot은 replay 비용 최적화 수단일 뿐, 도메인 모델 그 자체는 아님
- 비즈니스 규칙/불변식(invariant) 검증은 여기서만 수행

### Read side (Query)
- Projection / Materialized View
- 이벤트를 구독해서 JOIN·집계된 평탄한 테이블로 떨굼
- DTO에 가깝고, 도메인 로직 없음
- 여러 Read 모델을 용도별로 자유롭게 만들 수 있음 (UI용, 통계용, 검색용 등)

### Snapshot의 위치
- Snapshot은 **Aggregate의 직렬화된 상태** + **마지막 적용 이벤트 버전**
- Write 쪽에 존재하지만, 도메인 모델 *대신*이 아니라 *재구성 가속용 캐시*로 봐야 함
- 복원 절차: `Snapshot 로드 → 이후 이벤트만 replay → Aggregate 완성 → Command 처리`

### 흔한 오해 정리
- "Read 모델이 도메인 모델이다" → ❌ Read 모델은 쿼리 최적화 결과물
- "Snapshot이 도메인 모델이다" → ❌ Snapshot은 Aggregate의 한 시점 스냅샷
- "Write에는 Event만 있다" → ❌ Event는 변경 사실, Aggregate는 그 사실들을 적용한 현재 상태

| 구분 | Write Side | Read Side |
|---|---|---|
| 핵심 | Event Store + Aggregate | Projection |
| 도메인 모델 | O (Aggregate) | X (DTO/View) |
| 비즈니스 규칙 | O | X |
| Snapshot | 최적화용으로 존재 | 해당 없음 |

---

## 2. 이벤트와 트랜잭션 경계

### 두 가지 패턴 구분

**1. 순수 이벤트 소싱 (Pure Event Sourcing)**
- Event Store에 **이벤트만** 저장 (단일 INSERT)
- 도메인 모델의 "현재 상태"는 별도 테이블에 저장하지 않음
- 다음 Command 처리 시 이벤트 replay(+Snapshot)로 복원
- 트랜잭션 범위: **이벤트 append 하나**

**2. 상태 저장 + 이벤트 발행 (State-stored + Event)**
- 도메인 테이블(현재 상태) UPDATE + 이벤트 테이블 INSERT
- 흔히 Transactional Outbox와 함께 쓰임
- 트랜잭션 범위: **상태 변경 + 이벤트 적재가 한 트랜잭션**
- 엄밀히는 이벤트 소싱이 아니라 "이벤트 기반 아키텍처"

### "이벤트 + 도메인 모델 수정까지 한 트랜잭션인가?"

- **순수 이벤트 소싱이라면**: 도메인 모델을 따로 "수정"하지 않는다. 이벤트 append만 트랜잭션. 상태는 다음 로드 시 replay로 만들어짐.
- **상태 저장 + 이벤트 방식이라면**: 상태 UPDATE + 이벤트 INSERT를 **반드시 한 트랜잭션**으로 묶어야 한다. 그래야 "상태는 바뀌었는데 이벤트는 누락" 또는 그 반대 상황을 막을 수 있음 (Outbox 패턴의 핵심 동기).

### Snapshot의 트랜잭션 분리

순수 이벤트 소싱에서도 Snapshot을 저장하긴 하지만:
- Snapshot 저장은 **Command 트랜잭션과 분리**되는 게 일반적
- 보통 N개 이벤트마다 비동기로 갱신 (예: 100개 이벤트마다)
- Snapshot이 늦거나 실패해도 정확성은 깨지지 않음 (이벤트가 진실의 원천)

| 항목 | 순수 ES | 상태+이벤트 |
|---|---|---|
| 진실의 원천 | Event Store | 상태 테이블 |
| 트랜잭션 | 이벤트 append만 | 상태 UPDATE + 이벤트 INSERT |
| 도메인 모델 영속화 | 안 함 (replay) | 함 |
| Snapshot | 최적화용, 비동기 가능 | 해당 없음 |
| 이벤트의 역할 | 상태 그 자체 | 변경 알림 |

---

## 3. Event Sourcing과 DDD의 관계

**이벤트 소싱은 DDD를 흐리지 않는다. 오히려 DDD를 가장 충실하게 구현하는 방식 중 하나다.**

### DDD 개념 ↔ Event Sourcing 매핑

| DDD 개념 | 이벤트 소싱에서의 구현 |
|---|---|
| Aggregate | 이벤트를 적용해 상태가 만들어지는 일관성 경계 |
| Aggregate Root | Command를 받고 이벤트를 생성하는 진입점 |
| Domain Event | **1급 시민**. 그냥 알림이 아니라 상태 그 자체 |
| Invariant | Command 처리 시점에 Aggregate가 검증 |
| Ubiquitous Language | 이벤트 이름이 곧 도메인 언어 (`OrderPlaced`, `PaymentCaptured`) |
| Bounded Context | Context별로 독립된 Event Store/스트림 |

### "도메인 모델이 희미해진다"는 오해의 출처

- "도메인 모델을 DB에 저장 안 하니까 모델이 없는 거 아닌가?"
  → 저장 형태가 바뀐 것뿐, 모델은 메모리에서 더 풍부하게 살아 있음
- "이벤트만 INSERT하면 행위가 빈약해 보임"
  → 실제로는 Command → Aggregate.handle() → Event 생성 → apply() 흐름에서 비즈니스 로직이 Aggregate에 응축됨
- "Read 모델이 따로 있으니 도메인이 분산된 느낌"
  → Read 모델은 DDD에서도 원래 도메인 모델이 아님 (CQRS 관점에서 깔끔히 분리될 뿐)

### 오히려 강화되는 지점
- **불변식이 명시적**: Command 거부 / Event 생성이 명확히 갈림
- **이력이 도메인 자산**: "왜 이 상태가 되었는가"가 코드가 아니라 데이터로 남음
- **부수효과와 의도 분리**: Command(의도) vs Event(사실)가 분리됨
- **언어 일치도 상승**: 기획자가 말하는 "주문 취소됨"이 `OrderCancelled` 이벤트로 그대로 존재

### 진짜 희미해지는 건 CRUD 관성

- "테이블 한 줄이 곧 엔티티"
- "UPDATE 한 번으로 끝"
- "이력은 audit log로 따로"

이런 관성이 무너진다. DDD가 사라진 게 아니라 CRUD가 사라진 것.

---

## 4. Aggregate 단위와 Replay

### 이벤트는 Aggregate Root 단위

**이벤트는 Aggregate Root 단위로 발생한다.** Sub-entity(Aggregate 내부 엔티티)는 별도로 이벤트를 내지 않는다.

- 이벤트 소싱의 단위 = **Aggregate (= 일관성 경계 = 트랜잭션 경계)**
- Root가 Command를 받고, 내부 엔티티 변경도 Root가 이벤트로 표현
- 다른 Aggregate는 **별도 스트림**, 별도 이벤트, 별도 트랜잭션

```
Order (Aggregate Root)
├── OrderLine (sub-entity, Order 내부)
└── ShippingInfo (sub-entity, Order 내부)

Payment (다른 Aggregate Root, 별도 스트림)
```

- `OrderLine`이 추가돼도 → `OrderLineAdded` 이벤트는 **Order 스트림**에 쌓임
- `Payment`는 **완전히 다른 스트림** (`payment-{id}`)
- Order와 Payment 간 일관성은 **Saga/Process Manager**로 처리 (한 트랜잭션 X)

### Event는 delta만 담는다

- ❌ `OrderUpdated { 전체 상태 }`
- ✅ `OrderLineAdded { productId, qty, price }`
- ✅ `OrderCancelled { reason, cancelledAt }`

이유:
- 이벤트는 **불변(immutable) 사실**. 작아야 의미가 명확
- 도메인 언어와 1:1 (`주문 취소됨` = `OrderCancelled`)
- 작아야 저장·전송·버저닝 비용이 낮음
- 전체 상태를 담으면 그건 사실상 스냅샷

### Replay로 POJO 생성

```
Event Store (DB)
  ├── OrderCreated      { orderId, customerId }
  ├── OrderLineAdded    { productId, qty }
  ├── OrderLineAdded    { productId, qty }
  └── OrderConfirmed    { confirmedAt }
            │
            ▼  replay (순서대로 apply)
     Order POJO (메모리)
       - id, customer, lines[], status=CONFIRMED
            │
            ▼  Command 처리
       새 Event 생성 → append
```

### Aggregate 코드 골격 (Kotlin)

```kotlin
class Order private constructor() {
    lateinit var id: OrderId
    val lines = mutableListOf<OrderLine>()
    var status: OrderStatus = OrderStatus.DRAFT
    var version: Long = 0

    // Command: 의도 검증 → 이벤트 생성
    fun addLine(cmd: AddLineCommand): List<DomainEvent> {
        require(status == OrderStatus.DRAFT) { "확정된 주문은 수정 불가" }
        return listOf(OrderLineAdded(cmd.productId, cmd.qty))
    }

    // Event apply: 상태 변경만 (검증 없음)
    fun apply(event: DomainEvent) {
        when (event) {
            is OrderCreated   -> { id = event.orderId }
            is OrderLineAdded -> { lines.add(OrderLine(event.productId, event.qty)) }
            is OrderConfirmed -> { status = OrderStatus.CONFIRMED }
        }
        version++
    }

    companion object {
        fun replay(events: List<DomainEvent>): Order =
            Order().apply { events.forEach { apply(it) } }
    }
}
```

핵심 분리:
- **Command 핸들러**: 불변식 검증, 이벤트 생성 (새 사실 만들기)
- **Event apply**: 검증 없이 상태 반영만 (과거 사실 재현)

### 저장 위치 요약

| 위치 | 무엇이 있나 |
|---|---|
| **DB (Event Store)** | 이벤트들의 append-only 로그 |
| **DB (Snapshot Store, 선택)** | N개 이벤트마다의 Aggregate 직렬화본 |
| **메모리** | Command 처리 시점에 replay된 Aggregate POJO |
| **DB (Read Model)** | Projection이 만들어둔 쿼리 최적화 테이블 |

### 수명주기

1. Command 도착
2. `loadEvents(aggregateId)` (+ snapshot이 있으면 스냅샷부터 + 이후 이벤트만)
3. `Order.replay(events)` → 메모리에 POJO 생성
4. `order.handle(command)` → 새 이벤트들 반환
5. `eventStore.append(events, expectedVersion)` (낙관적 동시성 체크)
6. POJO는 **버려도 됨** (다음 Command 때 다시 replay)

### 동시성: expectedVersion

```
append(events, expectedVersion = 4)
  → 현재 스트림 version이 4면 OK, 5면 ConcurrencyException
```

이게 Aggregate 단위 낙관적 락이고, Aggregate가 "일관성 경계"인 이유의 실체.

---

## 5. Snapshot 전략

### Snapshot 저장 위치

| 옵션 | 설명 | 특징 |
|---|---|---|
| **별도 테이블 (같은 DB)** | `event_store` + `snapshot_store` 분리 | 가장 흔함. 트랜잭션 분리 쉬움 |
| **Event Store 내부 스트림** | EventStoreDB처럼 스냅샷도 이벤트로 취급 | 도구가 지원할 때만 |
| **외부 캐시 (Redis 등)** | 핫 Aggregate만 캐시 | 보조 계층, 진실의 원천은 아님 |

실무에서는 **"별도 테이블 + Redis 캐시"** 조합이 가장 흔함.

### Snapshot 테이블 스키마 예시

```sql
CREATE TABLE aggregate_snapshot (
    aggregate_id    VARCHAR(64) NOT NULL,
    aggregate_type  VARCHAR(64) NOT NULL,
    version         BIGINT      NOT NULL,  -- 이 시점까지 적용된 이벤트 버전
    payload         JSON        NOT NULL,  -- 직렬화된 Aggregate 상태
    snapshot_schema_version INT NOT NULL,  -- 스냅샷 포맷 버전 (마이그레이션용)
    created_at      TIMESTAMP   NOT NULL,
    PRIMARY KEY (aggregate_id, version)
);
```

핵심:
- `version`이 "몇 번째 이벤트까지 반영했는가"의 기준
- 로드 시 `SELECT ... ORDER BY version DESC LIMIT 1`
- payload는 JSON / Protobuf / Avro 등

### 로드 흐름

```
1. snapshot = snapshotStore.loadLatest(aggregateId)   -- version=100
2. events   = eventStore.loadAfter(aggregateId, v=100) -- 101, 102, 103
3. aggregate = Order.fromSnapshot(snapshot)
   events.forEach { aggregate.apply(it) }
4. command 처리...
```

즉 **"Snapshot으로 점프 → 이후 이벤트만 replay"**.

### 언제 찍나

| 전략 | 기준 | 특징 |
|---|---|---|
| **이벤트 카운트** | N개마다 (예: 100개) | 가장 단순, 가장 많이 씀 |
| **시간 기반** | 마지막 스냅샷 후 N분/시간 | 활성도 낮은 Aggregate에 유리 |
| **이벤트 종류 기반** | 특정 milestone 이벤트 시 (`OrderCompleted` 등) | 도메인적으로 의미 있는 지점 |

기준점은 보통 "replay 비용이 SLA를 위협하기 직전"으로.

### Snapshot은 Command 트랜잭션과 분리

```
Command 트랜잭션:
  - eventStore.append(events)   ← 이게 진실
  - 끝

비동기 (별도 워커 or 후처리):
  - if (currentVersion - lastSnapshotVersion >= 100) {
      snapshot = aggregate.toSnapshot()
      snapshotStore.save(snapshot)
    }
```

### 주의할 함정

1. **스냅샷 포맷 변경 시 마이그레이션 부담** → `snapshot_schema_version` + Upcaster, 또는 "포맷 깨지면 그냥 무시하고 전체 replay" 정책
2. **너무 자주 찍지 말 것** → 이벤트마다 스냅샷 = 그냥 상태 저장 방식과 다를 게 없음
3. **오래된 스냅샷 정리** → 최신 1~2개만 남기고 삭제
4. **스냅샷 ≠ Read Model** → Write용 복원 대상 vs 쿼리용 평탄화 뷰

---

## 6. JPA와 Event Sourcing

### JPA와 ES는 본질적으로 충돌

| 항목 | JPA의 전제 | 이벤트 소싱의 전제 |
|---|---|---|
| 진실의 원천 | **현재 상태 테이블** | **이벤트 로그** |
| 변경 추적 | Dirty Checking (전/후 비교) | 명시적 Event 생성 |
| 쓰기 패턴 | UPDATE (in-place) | INSERT only (append) |
| 식별 | `@Id` + 1차 캐시 | aggregate_id + version |
| 동시성 | `@Version` (행 단위 낙관적 락) | expectedVersion (스트림 단위) |
| 로딩 | Lazy fetch, JOIN | 이벤트 스트림 replay |
| 라이프사이클 | persist/merge/remove | append-only, 삭제 없음 |

### Dirty Check가 어불성설인 이유

- 이벤트 소싱에서 "변경"은 자동 감지 대상이 아니라 **도메인이 명시적으로 선언하는 사실**
- `order.cancel()`은 필드를 수정하는 게 아니라 **`OrderCancelled` 이벤트를 생성**하는 행위
- "이전 상태 vs 현재 상태 비교"가 의미 없음
- UPDATE가 없으니 dirty check가 트리거할 SQL 자체가 없음

### "JPA가 DDD를 차용했다"의 진실

JPA가 가져온 건 **DDD의 일부 어휘**일 뿐.

**JPA가 차용한 DDD 어휘**
- Entity, Value Object (`@Entity`, `@Embeddable`)
- Repository 패턴 (`JpaRepository`)
- Aggregate 경계 (Cascade, fetch 설정으로 흉내)
- Domain Event (`@DomainEvents`, `AbstractAggregateRoot`)

**JPA가 못 따라가는 DDD**
- Aggregate가 일관성 경계라는 본질
- 불변식이 도메인 메서드 안에서 강제되어야 한다는 점
- Event가 1급 시민이라는 점
- CQRS 분리

JPA는 **"상태 저장 기반 DDD"의 도구**.

### JPA로 ES "흉내"

```kotlin
@Entity
@Table(name = "event_store")
class EventEntity(
    @Id @GeneratedValue val seq: Long? = null,
    val aggregateId: String,
    val aggregateType: String,
    val version: Long,
    val eventType: String,
    @Lob val payload: String,        // JSON 직렬화
    val occurredAt: Instant,
) {
    // setter 없음, 변경 메서드 없음 — 그냥 INSERT용
}
```

- ✅ INSERT만 함 → dirty check 발동 안 함
- ✅ Aggregate 자체는 JPA `@Entity`로 매핑하지 **않음** — 일반 POJO
- ❌ JPA의 영속성 컨텍스트는 오히려 방해

권장: **JdbcTemplate / jOOQ / R2DBC**가 더 깔끔.

### `@DomainEvents`는 ES가 아니다

```kotlin
class Order : AbstractAggregateRoot<Order>() {
    fun cancel() {
        this.status = CANCELED
        registerEvent(OrderCanceled(this.id))
    }
}
```

이건 **상태 저장 + 이벤트 발행** 패턴이지 이벤트 소싱이 아님.
- Order 테이블 UPDATE (dirty check 동작)
- save() 시점에 등록된 이벤트를 ApplicationEventPublisher로 발행
- 이벤트는 **알림용**이지 **상태 그 자체가 아님**

### 왜 JPA가 표준이 됐는가

대부분의 비즈니스 도메인은 이벤트 소싱이 필요할 만큼 변경 이력이 핵심 가치가 아니기 때문.

```
풀 이벤트 소싱           ← 핵심 도메인, 변경 이력이 가치 (소수)
  ↑
State-stored + Outbox    ← 통합 필요한 도메인 (중간 다수)
  ↑
JPA + Domain Events      ← 일반 비즈니스 도메인 (대다수)
  ↑
JPA만 (CRUD)             ← 마스터/설정 영역 (대다수)
```

JPA가 표준인 이유와 ES가 안 퍼지는 이유는 같다: **대부분의 도메인이 상태 중심이기 때문.**

---

## 7. Read Model과 Projection

### 사용자가 보는 것 ≠ 시스템이 저장하는 것

**CRUD 사고**
```
사용자가 본다 → orders 테이블
사용자가 바꾼다 → orders 테이블 UPDATE
```

**Event Sourcing**
```
사용자가 본다     → Read Model (조회 전용 평탄한 테이블)
사용자가 의도한다 → Command → Aggregate replay → 이벤트 append
```

### 시간 순서 (qty: 2 → 3 변경 시나리오)

**CRUD**
```
1. SELECT * FROM orders WHERE id=1     → 화면 표시 (qty=2)
2. 사용자가 3으로 수정
3. UPDATE orders SET qty=3 WHERE id=1
```

**Event Sourcing**
```
[조회]
1. SELECT * FROM order_read_model WHERE id=1  → 화면 표시 (qty=2)

[명령]
2. ChangeQuantityCommand(orderId=1, newQty=3)
3. Command Handler:
   a. eventStore.loadEvents(orderId=1)  → [OrderCreated, LineAdded(qty=2)]
   b. order = Order.replay(events)
   c. order.changeQuantity(3) → [QuantityChanged(from=2, to=3)]
   d. eventStore.append(events, expectedVersion=2)

[전파, 비동기]
4. Projector가 QuantityChanged 수신
   → UPDATE order_read_model SET qty=3 WHERE id=1

5. 다음 조회 시 qty=3
```

### 사용자 입장은 똑같다

**사용자는 어차피 "현재 상태"를 보고 "변경점"을 누른다.** UX를 이벤트 소싱답게 바꿀 필요 없음.

### Projection은 Aggregate replay와 같은 원리

| 구분 | Aggregate Replay (Write) | Projection Replay (Read) |
|---|---|---|
| 입력 | 한 Aggregate의 이벤트 스트림 | 여러 Aggregate의 이벤트들 (관심 있는 것만) |
| 출력 | 메모리상 Aggregate POJO | DB 테이블 (Read Model) |
| 결과물 | 일관성/불변식 가진 도메인 객체 | 쿼리 최적화된 평탄한 뷰 |
| 검증 | 도메인 불변식 보존 | 그냥 반영만 |
| 수명 | Command 후 버려도 됨 | 영속, 계속 갱신 |

### Projector 코드 골격

```kotlin
class OrderSummaryProjector {
    fun on(event: DomainEvent) = when (event) {
        is OrderCreated   -> insertRow(event.orderId, status = "DRAFT")
        is OrderLineAdded -> incrementTotal(event.orderId, event.qty * event.price)
        is OrderConfirmed -> updateStatus(event.orderId, "CONFIRMED")
        else -> { /* 관심 없음 */ }
    }
}
```

### Read Model은 Domain Model + α인가?

**반은 맞고 반은 위험.**

**형태상 닮을 수 있다**
- 같은 도메인이라 필드 겹침
- JOIN 결과(`customerName`, `productName`)를 +α로 얹는 형태

**본질적으로 다르다**

| 항목 | Aggregate | Read Model |
|---|---|---|
| 불변식 검증 | O | X |
| 행위(메서드) | O | X (DTO) |
| 일관성 경계 | 자기 자신 = 트랜잭션 단위 | 없음, 결과적 일관성 |
| 변경 방식 | Command → Event 생성 | Event 수신 → UPDATE |
| 개수 | Aggregate당 1개 본질 | 용도별 N개 |
| 책임 | "어떻게 바뀌어야 하는가" | "어떻게 보여줘야 하는가" |

**위험한 이유**

Read Model을 보고 Command를 판단하면 CQRS가 무너진다.
- Read Model은 항상 살짝 늦을 수 있음 (결과적 일관성)
- Race Condition 발생
- 결정의 근거는 **반드시 Aggregate replay 결과**여야 함

> **Read Model은 보여주기 위함, Aggregate는 결정하기 위함.**

### Event Sourcing에서 CDC가 불필요한 이유

| 방식 | 출처 | ES와의 관계 |
|---|---|---|
| **CDC (Debezium 등)** | DB의 binlog/WAL 파싱 | 상태 저장 방식에서 "변경 알림"을 사후 추출 |
| **ES의 Projection** | Event Store에 이미 명시적으로 존재하는 도메인 이벤트 구독 | 이벤트가 **원본**이라 추출 불필요 |

ES에서는 **CDC가 필요 없다.** 이벤트가 이미 도메인 1급 시민으로 저장돼 있음.

```
Event Store → (subscribe) → Projector → Read Model DB
```

구독 방식:
- **Polling** (마지막 처리한 position 기억하고 주기적으로 읽기)
- **Push** (Event Store가 구독자에게 푸시, EventStoreDB·Kafka)
- **Catch-up Subscription** (처음엔 과거 이벤트 다 읽고, 따라잡으면 실시간)

---

## 8. DB는 도구일 뿐

### 패러다임 전환

**CRUD 사고**
- DB = 진실의 원천 = 도메인 그 자체
- 테이블 스키마가 곧 도메인 모델
- DB 중심 사고

**Event Sourcing**
- 이벤트(=사실의 흐름) = 진실의 원천
- DB는 그 사실을 담아두는 저장소일 뿐
- MySQL, Postgres, EventStoreDB, Kafka — **갈아끼울 수 있는 도구**

> "Database is a detail." — Robert C. Martin

도메인이 DB에 종속되지 않고, DB가 도메인을 담는 어댑터가 됨. 헥사고날의 본질.

### Event Store 저장소 후보

이벤트 저장 요구사항:
- append-only, 대량 INSERT
- aggregate_id + version 단위 조회
- 글로벌 순서 보장
- expectedVersion 기반 낙관적 동시성

| 후보 | 특징 |
|---|---|
| **EventStoreDB** | ES 전용, subscription·snapshot·projection 내장 |
| **Kafka** | append-only, 대량처리. 임의 시점 replay 불편 |
| **DynamoDB / Cosmos DB** | 파티션 키 = aggregate_id, sort key = version 잘 맞음 |
| **PostgreSQL/MySQL** | 가장 흔한 실무 선택. UNIQUE + 시퀀스로 충분 |

실무는 **운영 익숙함 > 이론적 최적**. MySQL/Postgres로 시작 → 한계 오면 옮김.

### Read Model은 폴리글랏이 자연스러움

| 용도 | 추천 저장소 |
|---|---|
| 정형 쿼리, 트랜잭션성 조회 | RDB (PostgreSQL/MySQL) |
| 전문 검색 | Elasticsearch / OpenSearch |
| 캐시성 단건 조회 | Redis |
| 시계열/대시보드 집계 | ClickHouse, Druid, TimescaleDB |
| 그래프 탐색 | Neo4j |

### 섞는 게 중요하다

**전부 이벤트 소싱으로 통일하는 시도는 거의 항상 과하다.**

| 도메인 예시 | 적합한 방식 | 이유 |
|---|---|---|
| 주문 라이프사이클 | Event Sourcing | "왜 취소됐는지" 추적 가치 큼 |
| 결제 거래 | Event Sourcing | 금융 감사, 정산, 분쟁 대응 |
| 재고 이동 | Event Sourcing | 입고/출고/이동 이력이 도메인 |
| 상품 마스터 | CRUD | 현재 정보만 중요 |
| 사용자 프로필 | CRUD | 최신 값이면 됨 |
| 시스템 설정 | CRUD | 변경 이력이 도메인 가치 X |

### 점진 도입 경로

| 단계 | 패턴 | 특징 |
|---|---|---|
| 1 | **CRUD + Audit Log** | UPDATE + 변경 로그 별도 |
| 2 | **CRUD + Transactional Outbox** | 상태 UPDATE + 이벤트 INSERT 한 트랜잭션 |
| 3 | **State-stored + Domain Events** | 도메인 이벤트가 1급 시민 |
| 4 | **Pure Event Sourcing (선택 도메인)** | 핵심 Aggregate만 |
| 5 | **Event Sourcing + CQRS** | Read Model 분리, Projection 본격 |

대부분 팀은 **2~3단계로 충분**.

### "여기에 이벤트 소싱이 필요한가?" 체크리스트

- "왜 이렇게 됐는지" 질문이 비즈니스적으로 자주 나오는가
- 감사/규제/분쟁 대응이 도메인 핵심 가치인가
- 시간 순 재현이 가치가 있는가
- 이벤트 자체가 도메인 언어와 일치하는가
- 같은 데이터를 다양한 관점으로 보여줘야 하는가

3개 이상 "그렇다" → ES 고려. 1개 이하 → CRUD가 정답.

> 이벤트 소싱은 **무기**이지 **종교**가 아니다.

---

## 9. POC 전환 전략

### 대상 시스템: prototype-reservation-system

캐치테이블 류 음식점 예약 시스템. Kotlin + Spring Boot + MySQL + Redis, 헥사고날 멀티 모듈.

### 도메인별 ES 적합도

| 도메인 | 적합도 | 이유 |
|---|---|---|
| **Reservation (예약)** | ★★★★★ | 라이프사이클 풍부, 분쟁 대응, "왜 취소" 핵심 |
| **Schedule/Slot** | ★★★★ | 동시성·시간축 본질 |
| **Table (테이블 운영)** | ★★★ | 점유/해제 흐름이 이벤트적 |
| **Store (매장)** | ★★ | 마스터성, 영업시간 변경 이력은 가치 |
| **Holiday (휴일)** | ★ | 단순 설정성 |
| **User (사용자)** | ★ | 마스터 |

**핵심 후보: Reservation 단일. 확장 후보: Schedule.** 나머지는 JPA 유지.

### 3단계 점진 전환

**V2 Phase 1 — Outbox + Domain Event 강화**
- `reservation_outbox` 테이블 추가
- 예약 상태 변경 시 상태 UPDATE + Outbox INSERT를 한 트랜잭션
- Outbox → Kafka로 발행
- JPA 그대로 유지, 도메인 코드 변경 최소
- 얻는 것: 이벤트 1급 시민화, 외부 통합 안정성, 감사 추적

**V2 Phase 2 — Reservation을 Pure ES로**
- `reservation_event` 테이블 신설 (append-only)
- `ReservationAggregate`를 JPA `@Entity`에서 분리, 순수 Kotlin POJO로
- Command Handler 도입, `loadEvents → replay → handle → append`
- expectedVersion 기반 낙관적 동시성
- Snapshot은 처음엔 안 만들어도 됨
- Read Model 1개 먼저: 기존 `reservation` 테이블을 Projection 결과로 재활용

**V2 Phase 3 — Read Model 분화 + Schedule 합류 (선택)**
- Read Model 다중화 (사용자용 / 점주용 / 통계용)
- 필요시 Elasticsearch나 Redis 기반 Read Model
- Schedule/Slot Aggregate를 ES화
- Reservation ↔ Schedule 일관성은 Saga

### 모듈 배치 제안

```
core-module/
  └── reservation/
       ├── ReservationAggregate (POJO, JPA 의존 없음)
       ├── command/  (ReserveCommand, CancelCommand, ...)
       └── event/    (ReservationRequested, ReservationConfirmed, ...)

application-module/
  └── reservation/
       └── ReservationCommandHandler (loadEvents → replay → append)

infrastructure-module/
  └── reservation/
       ├── eventstore/    (JdbcTemplate 기반 EventStore 구현)
       ├── snapshot/      (선택)
       └── projection/    (Read Model 빌더)

adapter-module/
  └── reservation/
       ├── inbound/  (REST/Web)
       └── outbound/ (Outbox → Kafka)
```

원칙:
- **core**: JPA/Spring 의존성 0, 순수 도메인
- **infrastructure**: EventStore 구현은 JdbcTemplate 권장
- Projection은 별도 Adapter 또는 Infrastructure에서 이벤트 구독

### 함께 챙길 주제

- **이벤트 버저닝 / Upcasting** — 스키마 변경 시 옛 이벤트 읽는 법
- **Projection 재구축** — Read Model 깨지면 처음부터 빌드
- **expectedVersion 동시성 실험** — 동시 예약 충돌 처리
- **Saga로 Reservation ↔ Schedule 일관성**
- **Idempotency Key + Outbox 멱등성**
- **Given(Events) / When(Command) / Then(Events) 테스트 패턴**

### 포트폴리오 관점

V2의 진짜 가치는 코드보다 **ADR**에 있다.

권장 ADR:
- 왜 전 도메인이 아니라 Reservation만 ES인가
- EventStore를 JPA가 아닌 JdbcTemplate으로 구현한 이유
- Snapshot을 Phase 1에 도입하지 않은 이유
- Saga vs 2PC, 보상 트랜잭션 설계

> 면접 변별력은 "ES를 안다"가 아니라 **"안 쓸 곳을 안 쓴다는 판단"** 에서 나온다.

---

## 10. CQRS와 Event Sourcing은 별개

### 흔한 오해

- **CQRS** = Command(쓰기)와 Query(읽기) 모델 분리
- **Event Sourcing** = 상태가 아닌 이벤트를 진실의 원천으로 저장

**이 둘은 독립 패턴.** 4가지 조합 모두 가능.

| 조합 | 설명 | 흔한가 |
|---|---|---|
| CRUD + 단일 모델 | 평범한 Spring + JPA | 압도적 다수 |
| CRUD + CQRS | 같은 DB지만 Read/Write 모델 분리 | 가끔 |
| Event Sourcing + 단일 모델 | 이론상 가능하나 어색 | 거의 없음 |
| Event Sourcing + CQRS | 본 대화의 조합 | ES 시 사실상 필수 |

**CQRS는 ES 없이도 의미 있고, ES는 CQRS 없이는 어색하다.**

### CQRS만 단독으로 쓰는 케이스

**잘 맞는 상황**
- 읽기/쓰기 부하 패턴이 극단적으로 다름
- 읽기 모델 형태가 도메인 모델과 멀리 떨어져 있음
- 읽기 전용 노드 별도 스케일 아웃
- 읽기 SLA가 빡빡함 (검색, 자동완성, 랭킹)

**안 맞는 상황 (대부분)**
- 일반 비즈니스 도메인
- 읽기/쓰기 부하 비슷
- 도메인 모델 그대로 보여줘도 충분

### CRUD에 CQRS만 얹는다면?

**일반적인 도메인에서는 과하다.**
- Write UPDATE 데이터를 Read가 봐야 함 → 동기화 메커니즘 필요
- 트리거 / CDC / Outbox + 메시징 중 하나
- ES의 가치(이력, 재현, 감사)는 없는데 복잡도만 떠안음
- Read Replica로 푸는 게 거의 항상 더 쌈

**CRUD에서는 Read/Write를 "물리 모듈 분리"까지 갈 이유가 약하다.** 코드 레이어 분리(Command 객체 ≠ Query 응답 DTO) 정도면 충분.

### 분리의 단계

| 단계 | 분리 정도 | 적합한 상황 |
|---|---|---|
| 0 | Service 하나, 같은 DTO | 단순 CRUD, 초기 |
| 1 | Command DTO ≠ Query DTO, 같은 Service | 일반 Spring 권장 |
| 2 | CommandService / QueryService 클래스 분리 | 복잡한 도메인 |
| 3 | Read 전용 패키지/모듈, 같은 DB·Aggregate 재사용 | CQRS 입문 |
| 4 | Read 전용 DB/스키마, 별도 Projection | 부하 분리, 검색 |
| 5 | **Read/Write 물리 모듈 + 별도 저장소 + 이벤트 동기화** | ES 도입 시 |

**1~2단계는 거의 모든 프로젝트 권장.** 3단계부터는 도메인 요구가 있을 때.

### POC 의도 정리

| POC 의도 | 추천 구조 |
|---|---|
| ES 전 사이클을 다 만져본다 | **Read/Write 물리 분리 + ES + CQRS** |
| CQRS만 경험 (ES 없이) | Read 패키지 분리, 같은 모듈, 같은 DB |
| ES만 경험 (CQRS 없이) | 어색해서 비추천 |
| 기존 시스템 진화 경로 | Phase 1 Outbox부터 (모듈 분리 X) |

### 결론

> CQRS는 Event Sourcing 없이도 가능하지만, **Read/Write 물리 분리까지 가는 건 Event Sourcing이 동반될 때 비로소 비용 대비 가치가 맞다.** 일반 CRUD에서는 코드 레이어 분리 정도가 정답.

ES를 한다 → 이벤트가 진실 → Read는 Projection → Read/Write 책임이 본질적으로 다름 → 모듈 분리가 자연스러움.

분리해두면 나중에 Read만 폴리글랏화할 때 비용이 거의 0.

---

## 부록: 핵심 원칙 요약

1. **도메인 모델은 Write에만**, Read는 Projection
2. **이벤트는 delta**, 전체 상태 X
3. **이벤트는 Aggregate Root 단위**, Sub-entity 자체 이벤트 X
4. **트랜잭션 경계 = Aggregate**, 다른 Aggregate는 Saga
5. **expectedVersion으로 동시성**
6. **Snapshot은 진실 아니다**, 가속용 캐시
7. **JPA와 ES는 본질적으로 충돌**, ES는 JdbcTemplate이 깔끔
8. **CDC는 ES와 무관**, ES는 자체 이벤트 구독
9. **Read Model로 결정하지 말 것**, 결정은 Aggregate replay로
10. **CQRS와 ES는 별개**, 둘 다 도입 비용 vs 가치 판단 필요
11. **DB는 도구**, 진실의 원천은 도메인 사실의 흐름
12. **전부 ES화는 거의 항상 과함**, 도메인별 혼합이 정답
13. **"안 쓰는 판단"이 시니어의 변별력**