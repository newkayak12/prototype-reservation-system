# V2 Design Doc — 02. Write Model

- **상위 결정**: [[02.selective-event-sourcing-scope]] · [[05.event-store-mysql-table]]
- **개요**: [[00-design-overview]]
- **계승**: [[07.reservation]] (Outbox·Zero Payload·PoisonMessage)

> command 측 쓰기 모델은 컨텍스트 분류에 따라 두 갈래다. **단, 대외 이벤트 발행 경로는 동일**하다.

## A. ES 컨텍스트 — `reservation` · `timetable` · `restaurant`

진실의 원천 = **append-only 이벤트 스트림**. 현재 상태는 이벤트 리플레이로 재구성한다.

### 애그리거트 책임 (빈약 도메인 탈피)

```kotlin
// 개념 예시 — 실제 시그니처는 구현 사이클에서 확정
class Reservation private constructor(/* state */) {
    fun handle(command: CancelReservation): List<DomainEvent> {
        // 불변식 검증은 애그리거트 안에서 (방문 3일 전까지만 등)
        require(canCancel(command.now)) { "취소 가능 기한 초과" }
        return listOf(ReservationCancelled(id, command.reason, command.now))
    }
    fun apply(event: ReservationCancelled): Reservation { /* 불변 전이 */ }
}
```

- `handle(command) → List<DomainEvent>`: 불변식 검증 후 발생할 이벤트를 반환.
- `apply(event) → newState`: 이벤트를 상태에 적용(불변 복사). 리플레이·신규 발생에 공용.
- V1의 도메인 서비스 검증 로직([[02-domain-limitations]] #1)은 애그리거트로 이전한다.

### 이벤트 스토어 (MySQL 직접 구현 — [[05.event-store-mysql-table]])

```
event_store(
  event_id     BINARY(16),                      -- UUIDv7. 전역 유일 정체성 + 재구축 keyset 커서: inbox/dedup·causation 앵커·Kafka messageId
  aggregate_type, aggregate_id, sequence_no,    -- (aggregate_id, sequence_no) UNIQUE
  event_type, event_version, payload(JSON),
  occurred_at,
  -- 봉투 추적메타: correlation_id, causation_id, traceparent (§공통)
  PRIMARY KEY (event_id), UNIQUE (aggregate_id, sequence_no)   -- UUIDv7 PK: 삽입 지역성 양호, 재구축 keyset = PK 스캔
)
```

- **정체성·열거**: `event_id`(UUIDv7, 전 컨텍스트 공통 dedup/causation 앵커 + 재구축 *keyset* 열거 커서 겸용 — 순서 정확성 아님)는 [[22.event-identity-and-global-ordering]]에서 확정. 비-ES Outbox 이벤트도 `event_id` 보유. (전용 `global_seq`는 [[RFC-021-event-identity-and-global-ordering]] 닫힘으로 불채택 — UUIDv7이 커서를 겸한다.)
- **동시성 = 비관 락 + UNIQUE 백스톱**([[16.optimistic-concurrency-control]] 2026-06-17 개정): 한 자리에 대한 결정을 한 줄로 세운다.
  ```
  lock(aggregate_id)            # L1: Redisson 분산 락 (Redis 불가 시 L1′: DB lock-row FOR UPDATE 폴백)
    load(replay) → version N
    handle(cmd) → events
    append (aggregate_id, N+1)  # L0: (aggregate_id, sequence_no) UNIQUE = 정확성 백스톱(불변)
  release(lock)
  ```
  - **L0 UNIQUE는 절대 제거하지 않는다** — 분산 락은 liveness지 safety가 아니다([[19.caching-redis-role]] 단일 인스턴스·`allkeys-lru`·페일오버로 락 유실 가능). 락이 풀려도 UNIQUE가 이중 점유를 최종 거절. (V1의 부재 #6 해소)
  - 락 범위 = **단일 `aggregate_id`만**(전역 락 금지). 교차 애그리거트는 사가([[06-consistency-and-sagas]]). ES 쓰기 경로 한정 — 비-ES는 DB 행 락 그대로.
- **스냅샷 최적화**: 기존 `*Snapshot` 패턴을 재활용해 N개 이벤트마다 스냅샷 저장 → 리플레이 단축. 스냅샷 낀 로드의 expected `sequence_no` = 스냅샷 버전 + 이후 이벤트 수([[08-event-store-lifecycle]]).
- **전용 제품 미도입**: 현 규모에서 EventStoreDB/Axon은 과함. 근거 [[05.event-store-mysql-table]].

## B. 비-ES 컨텍스트 — 상태 + Outbox (`schedule` · `user` · `authenticate`)

진실의 원천 = **현재 상태 테이블**(V1 방식 유지). 변경 시 통합 이벤트를 Outbox로 발행한다.

1. 커맨드 → 애그리거트가 상태 변경(행위 중심으로 개선) → 상태 테이블 저장.
2. **같은 트랜잭션**에서 Outbox에 통합 이벤트 기록 (timetable `BEFORE_COMMIT` 패턴).
3. 커밋 후 Kafka 발행.

> **도메인/JPA 분리 유지** ([[07.command-domain-jpa-separation]]): 1번의 애그리거트는 순수 도메인(`domain`)이고, 상태 테이블은 별도 JPA 엔티티(`adapter/out`)로 매핑한다. 애그리거트에 `@Entity` 를 붙이지 않는다(도메인 JPA 오염 금지). V1 방식 그대로.

> ES냐 비-ES냐의 차이는 1번(이벤트 스토어 vs 상태 테이블)뿐이다. 2~3번(Outbox 발행)은 동일하다.

## C. 현행/lookup — `menu` · `category` · `company`

저빈도·lookup 성격. 상태 테이블만 유지하며, **다른 컨텍스트가 구독해야 할 때만** Outbox 이벤트를 추가한다. 그 외 변경 없음.

## 공통: 대외 이벤트 발행 경로 (Outbox → Kafka)

ES·비-ES 무관하게 동일하다. [[07.reservation]]에서 검증된 패턴을 일반화한다.

```mermaid
sequenceDiagram
    participant TX as 명령 트랜잭션
    participant OB as Outbox 테이블
    participant K as Kafka
    TX->>OB: 이벤트 기록 (BEFORE_COMMIT, 같은 트랜잭션)
    Note over TX,OB: 커밋 — 상태(or 이벤트스토어) + Outbox 원자적
    OB->>K: AFTER_COMMIT (REQUIRES_NEW) 발행
    K-->>OB: 성공 → succeeded() / 실패 → failed() (스케줄러 재처리)
```

- **Zero Payload** 원칙 계승([[07.reservation]]): 메시지는 식별자 중심, 컨슈머가 최신 상태/이벤트 조회. 스키마 진화·DLQ 재처리 안전.
- **eventVersion** 보유(`AbstractEvent`)로 이벤트 진화 대응. 호환성 규칙·읽기 시 업캐스팅 전략은 [[10.event-schema-evolution]].
- **재처리**: 스케줄러 기반 미발행 Outbox 재시도, Consumer 실패는 PoisonMessage 별도 관리([[07.reservation]] 계승).
- **추적 메타 공통 충전**: `AbstractEvent`의 추적 메타(`correlationId`·`causationId`·`traceparent` — [[10-observability]], [[RFC-008-observability]])는 바로 이 공통 발행 경로에서 채운다. `correlationId`는 사슬 루트를 묶어 무변경 전파하고(필수), `causationId`는 **직전 원인 메시지의 `event_id`**(원인이 커맨드면 `commandId`)를 가리키며([[22.event-identity-and-global-ordering]]), `traceparent`는 W3C Trace Context로 OTel 추적을 봉투에 직렬화한다. 발행 경로가 ES·비-ES 무관하게 동일하므로, 추적 메타도 발행자가 일일이 신경 쓰지 않고 이 한 경로에서 일관 충전된다 — 채움 시점은 **Outbox 기록(트랜잭션 내)**이라 발행 단계에서 뒤늦게 채우는 유실을 피한다.

## 도메인 이벤트 카탈로그 (TBD)

컨텍스트별 구체 이벤트 목록·페이로드·버전 정책은 **이벤트 스토밍 재실시 후 확정**한다(기존 보드는 참고용). 본 문서는 *메커니즘*만 확정한다.

## 관련 문서
- [[00-design-overview]] · [[01-module-structure]] · [[03-read-model]] · [[04-migration]]
- ADR: [[02.selective-event-sourcing-scope]] · [[05.event-store-mysql-table]]
- 계승: [[07.reservation]]
