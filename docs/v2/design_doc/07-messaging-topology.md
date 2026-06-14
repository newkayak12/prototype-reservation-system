# V2 Design Doc — 07. Messaging Topology & Delivery Guarantee

- **상위 결정**: [[09.event-ordering-and-delivery-guarantee]]
- **개요**: [[00-design-overview]]
- **인접**: [[03-read-model]] · [[06-consistency-and-sagas]]
- **계승**: [[07.reservation]] (Kafka·Outbox·Zero Payload·PoisonMessage)

> command↔query 의 유일한 접점은 **이벤트(contract)** 다([[03.command-hexagonal-query-layered]]). 그 이벤트가 흐르는 길이 Kafka다. 본 문서는 토픽을 어떻게 자르고, 순서를 어디까지 보장하며, 무엇을 전달 보장하는지를 *메커니즘 수준*에서 확정한다.

> ⚠️ 본 문서는 **목표 아키텍처**다. 현재(V1) `timetable`은 단일 Outbox→Kafka 경로만 운영한다([[07.reservation]]). 토픽 분할·프로젝터 컨슈머 그룹은 [[06.strangler-migration]] 순서대로 도입한다.

## 전제: Outbox 가 발행 경계, Kafka 는 전달 경계

쓰기 트랜잭션과 발행의 원자성은 Outbox 가 책임진다([[02-write-model]] §공통). Kafka 는 그 다음 단계 — **발행된 이벤트를 query 측 프로젝터에게 나르는 전달 채널**이다. 따라서 본 문서의 보장은 "Outbox 가 이벤트를 1건 이상 발행한다"를 출발점으로 한다.

```mermaid
graph LR
    subgraph command [command-module]
        TX[명령 트랜잭션] --> OB[(Outbox)]
    end
    OB -->|AFTER_COMMIT| K[(Kafka<br/>topic per ctx.aggregate-type)]
    subgraph query [query-module]
        K -->|consumer group<br/>per projector| P[projector]
        P --> RM[(read model)]
    end
```

## 1. 토픽 구성 — 컨텍스트 / aggregate-type 단위

토픽은 **`<context>.<aggregate-type>` 단위**로 자른다. 예: `reservation.reservation`, `restaurant.restaurant`, `timetable.timetable`, `schedule.schedule`.

- **왜 컨텍스트/aggregate-type 단위인가**: 토픽이 곧 "스트림의 의미 경계"다. 한 aggregate-type 의 모든 이벤트가 한 토픽에 모이면, 그 토픽을 구독하는 쪽은 "그 애그리거트의 생애"를 순서대로 읽는다. event_store 의 `aggregate_type` 축([[05.event-store-mysql-table]])과 자연 정렬된다.
- **왜 이벤트-타입별 토픽이 아닌가**: `ReservationCreated`·`ReservationCancelled` 를 각각 토픽으로 자르면 한 애그리거트의 사건이 여러 토픽에 흩어져 **순서 보장이 깨진다**(아래 §2). 이벤트 타입은 토픽이 아니라 메시지의 `eventType`(`AbstractEvent`) 으로 구분한다.
- **너무 잘게 자르지 않는다(YAGNI)**: 컨텍스트당 애그리거트가 하나면 토픽도 하나다. 컨슈머가 필요 없는 타입은 무시(filter)하면 되고, 분리 비용(파티션·관리) 대비 이득이 없다.

> 토픽 목록의 확정은 도메인 이벤트 카탈로그(TBD)에 의존한다. 본 문서는 **분할 축(컨텍스트/aggregate-type)** 만 확정한다.

## 2. 파티션 키 = aggregate_id → 애그리거트별 순서 보장

메시지의 **파티션 키는 `aggregate_id`** 로 고정한다.

- Kafka 의 순서 보장은 **파티션 내부**에서만 성립한다. 같은 키는 항상 같은 파티션으로 가므로, **한 애그리거트의 이벤트들은 발행 순서대로 소비**된다(`ReservationCreated` → `ReservationConfirmed` → `ReservationCancelled` 가 뒤집히지 않음).
- 서로 다른 애그리거트 간에는 순서를 보장하지 않는다 — 그리고 **보장할 필요도 없다**. 불변식은 애그리거트 경계 안에서만 성립([[05-aggregate-design]])하므로, 교차 애그리거트 전역 순서는 도메인 요구가 아니다.

### 한계: 파티션 수 변경 = 재해싱

키→파티션 매핑은 기본적으로 `hash(key) % partitionCount` 다. **파티션 수를 늘리면 매핑이 바뀌어**, 같은 `aggregate_id` 가 이전과 다른 파티션으로 갈 수 있다. 그 순간 "예전 파티션의 미소비 이벤트"와 "새 파티션의 신규 이벤트" 사이에 순서 보장이 깨진다.

- **대응 원칙**: 파티션 수는 **초기에 넉넉히 잡고(예: 컨텍스트 처리량 추정의 여유분), 가급적 변경하지 않는다.** 늘려야 한다면 무중단으로 재해싱하지 말고, 컨슈머 드레인(§5) 후 정지 상태에서 늘린다.
- 구체 파티션 수·증설 절차는 운영 사이클에서 결정(TBD). 본 문서는 **"파티션 수는 순서 보장의 계약 일부"** 라는 점을 못 박는다.

## 3. 프로젝터용 컨슈머 그룹과 리밸런싱

각 프로젝터(`query.<ctx>.projection`, [[03-read-model]])는 **자기 전용 컨슈머 그룹**을 가진다.

- 컨슈머 그룹이 다르면 같은 토픽을 **독립적으로** 읽는다. 즉 `reservation.reservation` 토픽을 `reservation` 프로젝터와 (식당명 비정규화가 필요한) 다른 프로젝터가 각자 자기 오프셋으로 소비한다 — pub/sub 팬아웃.
- **리밸런싱 영향**: 그룹 내 컨슈머가 추가/제거되면 파티션이 재배정된다. 재배정 중에는 짧게 소비가 멈추고, 인플라이트 메시지가 재처리될 수 있다 → **멱등 컨슈머(§4)가 전제**다. 리밸런싱을 빈번하게 만들지 않도록 컨슈머 수를 안정적으로 운영한다.

### Competing consumers (그룹 내 병렬)

한 프로젝터를 수평 확장하려면 **같은 컨슈머 그룹에 인스턴스를 늘린다**. 파티션이 인스턴스들에 나뉘어 배정되어 병렬 소비(competing consumers)가 된다.

- **병렬 상한 = 파티션 수.** 인스턴스가 파티션보다 많으면 초과분은 놀고만 있는다. 이것이 §2 에서 "파티션 수를 넉넉히"의 또 다른 이유다.
- 순서 보장은 여전히 파티션 단위 → **같은 `aggregate_id` 는 한 인스턴스가 순서대로** 처리한다. 병렬화해도 애그리거트별 순서는 안전하다.

## 4. 전달 보장 — at-least-once + 멱등 → effectively-once

### 우리가 택하는 보장: at-least-once

- Outbox 의 AFTER_COMMIT 발행과 스케줄러 재시도([[02-write-model]])는 **누락보다 중복을 허용**하는 설계다. 즉 같은 이벤트가 1회 이상 도착할 수 있다.
- Kafka 소비 측도 "처리 후 오프셋 커밋" 순서라, 처리 성공·오프셋 커밋 실패 시 재소비가 일어난다 → 역시 중복 가능.

### 중복을 멱등 컨슈머/inbox 로 흡수 → effectively-once

at-least-once 의 중복은 **컨슈머가 멱등**이면 결과적으로 1회 효과(effectively-once)가 된다.

- **inbox 패턴**: 컨슈머는 처리한 이벤트의 식별자(`aggregate_id` + `sequence_no`, 또는 이벤트 ID)를 inbox(처리완료 기록)에 남기고, **이미 처리한 것은 스킵**한다. read model 갱신과 inbox 기록을 **한 트랜잭션**으로 묶어 원자화한다.
- **자연 멱등 가능 케이스**: Zero Payload([[02-write-model]])라 프로젝터가 "최신 상태를 조회해 덮어쓰기(upsert)"하는 경우, 같은 이벤트를 두 번 처리해도 결과가 같다. 이때는 inbox 없이도 멱등이 성립할 수 있다 — 단, 순서 역전(같은 애그리거트는 §2로 막힘) 가정 아래에서만.

```mermaid
sequenceDiagram
    participant K as Kafka
    participant C as projector (consumer)
    participant IB as inbox
    participant RM as read model
    K->>C: event(aggregate_id, sequence_no)
    C->>IB: 이미 처리? (aggregate_id, sequence_no)
    alt 처리됨
        IB-->>C: yes → skip
    else 신규
        C->>RM: read model 갱신
        C->>IB: 처리완료 기록 (같은 트랜잭션)
    end
    C->>K: offset commit
```

### 진짜 exactly-once 는 채택하지 않는다

Kafka 의 트랜잭셔널 producer/consumer(EOS)로 이론상 exactly-once 를 만들 수 있으나 — **우리는 채택하지 않는다.**

- **이유**: (1) Outbox→Kafka 단계가 이미 트랜잭션 밖(AFTER_COMMIT)이라 producer 측 EOS 만으로 끝나지 않고, (2) 소비 측 read model 은 외부 시스템(MySQL)이라 Kafka 트랜잭션 경계 밖이다. 진짜 EOS 는 전 구간을 Kafka 트랜잭션으로 묶어야 해 **비용·복잡도가 학습 목표 대비 과하다**(YAGNI).
- **결론**: at-least-once + 멱등으로 **effectively-once**를 만든다. "정확히 한 번 전달"이 아니라 "정확히 한 번의 효과"가 목표다.

## 5. 보장 성립 조건 — 그레이스풀 셧다운과 인플라이트 드레인

위 보장은 **종료가 깔끔할 때만** 성립한다. 막무가내 종료(SIGKILL·OOM)는 인플라이트 메시지를 어중간한 상태로 남긴다.

- **command 측**: 셧다운 시 진행 중 트랜잭션을 마치고, Outbox 발행 스케줄러를 멈춘다. 미발행분은 다음 기동 때 재발행(at-least-once 보존).
- **query 측(프로젝터)**: 셧다운 시 **현재 폴링한 배치를 끝까지 처리하고 오프셋을 커밋한 뒤** 종료한다(인플라이트 드레인). 드레인 없이 죽으면 미커밋 오프셋이 재소비되는데, 이는 멱등(§4)으로 흡수되므로 **안전하되 비효율** — 그래서 드레인으로 재처리량을 줄인다.
- **리밸런싱 직전**도 동일 — 파티션을 내놓기 전에 진행분을 커밋(또는 멱등에 위임)한다.

> 한 줄: **멱등은 안전망, 드레인은 효율.** 둘 다 있어야 effectively-once 가 비용 없이 성립한다.

## 6. 백프레셔와 consumer lag

- **consumer lag** = 토픽의 최신 오프셋과 컨슈머 커밋 오프셋의 차 = read model 의 **최종 일관성 지연**([[03-read-model]] §일관성)의 직접 지표다. lag 모니터링이 곧 "읽기 신선도" 모니터링이다.
- **백프레셔**: 프로젝터가 못 따라가면 lag 가 쌓인다. 대응은 (1) competing consumers 로 병렬 확장(§3, 상한=파티션 수), (2) 프로젝션 갱신 자체를 가볍게(Zero Payload upsert), (3) 그래도 모자라면 파티션 증설(§2 한계 감수, 정지 후).
- Kafka 는 디스크에 보존하므로 컨슈머가 느려도 producer 를 막지 않는다(브로커가 버퍼). 즉 백프레셔는 "command 가 느려지는" 문제가 아니라 "읽기가 늦어지는" 문제다 — CQRS 의 의도된 비대칭과 정합.
- 구체 lag 임계치·알람·동기 프로젝션 예외는 운영/구현 사이클에서 정의(TBD, [[03-read-model]]와 동일하게 화면 요구 의존).

## 관련 문서
- [[00-design-overview]] · [[03-read-model]] · [[06-consistency-and-sagas]] · [[02-write-model]]
- ADR: [[09.event-ordering-and-delivery-guarantee]] · [[05.event-store-mysql-table]] · [[03.command-hexagonal-query-layered]]
- 계승: [[07.reservation]]
