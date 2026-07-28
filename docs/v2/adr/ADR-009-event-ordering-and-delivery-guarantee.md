# ADR-009: 이벤트 순서·전달 보장 — 단일 순차 relay + LWW 가드, DLQ는 재구축으로 복구

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-003-messaging-delivery]] · [[RFC-025-ordering-relay-dlq-reconciliation]] · **설계**: [[DESIGN-008-messaging-topology]] · [[DESIGN-020-ordering-and-failure-handling]]
- **연관 ADR**: [[ADR-005-event-store-mysql-table]] · [[ADR-022-event-identity]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-008-saga-orchestration-vs-choreography]] · [[ADR-018-event-store-recovery-semantics]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 한 앱 안에서 호출 순서대로 트랜잭션을 탔다. 확정 다음 취소가 나면 순서가 뒤집힐 자리가 없었다. V2는 command가 만든 사실(이벤트)을 Kafka를 거쳐 query 측 프로젝터·사가에게 비동기로 전달한다([[DESIGN-008-messaging-topology]]). 파티션 키를 `aggregate_id`로 고정해 애그리거트별 순서를 보장하기로 했으나([[RFC-021-event-identity-and-global-ordering]]), 그 계약은 발행과 소비 양쪽에서 조용히 깨질 수 있다.

시나리오: 애그리거트가 `e1=ReservationConfirmed`(seq 5), `e2=ReservationCancelled`(seq 6)를 outbox에 순서대로 쌓는다.

1. **발행 측** — outbox relay를 여러 인스턴스로 경쟁 소비시키면, relay-1이 e1을 relay-2가 e2를 집어 e2가 Kafka에 먼저 도착할 수 있다.
2. **소비 측** — 컨슈머가 e1 처리에 실패해 DLQ로 보내고 e2를 먼저 처리한 뒤, 사람이 e1을 나중에 손으로 되쏘면 이미 지나간 순서를 건너뛴 재주입이 된다.

두 자리 모두 파티션 내 순서 보장이라는 전제를 무너뜨린다. 정상 흐름에서는 Kafka(단일 파티션·순차 소비)가 이미 순서를 지키므로, 닫아야 할 자리는 이 둘뿐이다.

**relay의 병렬성과 컨슈머의 실패·DLQ 처리가 애그리거트별 순서 계약을 다시 깨지 않게 하려면 무엇을 확정해야 하는가.**

## 결정 동인 (Decision Drivers)

- 애그리거트별 순서 보장은 프로젝션 정확성과 순서 결정적 소비(사가) 정확성의 전제다 — 발행·소비 전 구간에서 지켜야 한다.
- 무트래픽 학습 규모에 맞는 최소 machinery — 전역 순서, 진짜 EOS, 새 코디네이터 도입을 피한다.
- 실패 처리(DLQ)가 순서 계약을 소비 단계에서 다시 깨서는 안 된다.
- 이미 확정된 자산 위에서 짓는다 — event_store가 진실 원천이라는 전제([[DESIGN-008-messaging-topology]])와 프로젝션 재구축 경로([[RFC-011-projection-rebuild-catchup]])를 복구 메커니즘으로 재사용한다.
- V1의 검증된 자산(Kafka·Outbox·PoisonMessage)을 계승하되, 그 위에서 발견된 순서 역전 지점만 국소적으로 보강한다.

## 검토한 선택지 (Considered Options)

**발행 측 — relay 단일성**
- **A. `SELECT … FOR UPDATE SKIP LOCKED` 경쟁 소비 (원안)** — 여러 relay 인스턴스가 잠기지 않은 outbox 행만 집어가며 병렬 발행한다.
- **B. leader election(외부 코디네이터)** — 주키퍼/etcd류 코디네이터로 리더 하나만 뽑는다.
- **C. 단일 순차 relay(ShedLock 리더)** — 분산 락으로 뽑힌 리더 인스턴스 하나만 outbox를 `sequence_no` ASC로 순차 폴링·발행한다.

**소비 측 — 실패·DLQ 처리**
- **D. DLQ 수동 재생 (원안)** — 실패 이벤트를 사람이 판단해 라이브 스트림으로 다시 밀어 넣는다.
- **E. LWW seq 가드 + 꼬리 격리 + DLQ=알림/감사, 복구=event_store 재구축** — 컨슈머 종류별로 재정렬을 자가치유하거나 격리하고, DLQ는 되쏘지 않는다.

## 결정 (Decision Outcome)

**채택: C(단일 순차 relay) + E(컨슈머 종류별 실패 처리, DLQ는 재구축으로 복구).** relay 경쟁(A)과 DLQ 수동 재생(D)은 둘 다 처리량·즉응성을 위해 순서 직렬화를 포기하는 선택이라, 애그리거트별 순서 계약을 각자의 자리에서 스스로 깬다. 새 코디네이터(B)는 그 대가로 운영 짐만 늘린다.

순서·전달 보장은 다음처럼 구간별로 선다. 파티션 키=`aggregate_id`·토픽=컨텍스트/aggregate-type 단위·at-least-once+inbox 멱등([[DESIGN-008-messaging-topology]])은 그대로 유지되는 전제이고, 이번 결정이 바꾸는 자리는 **발행 relay의 단일성**과 **DLQ의 위치**다.

| 구간 | 보장 | 메커니즘 |
|---|---|---|
| **발행**(command→Kafka) | 애그리거트별 순차 발행 | **단일 순차 relay(ShedLock 리더)**가 outbox를 `sequence_no` ASC로 순차 발행. Kafka 프로듀서 idempotent. 경쟁 소비(A)는 폐기 |
| **전달**(Kafka) | 파티션 내 순서, at-least-once | 파티션 키=`aggregate_id`, 토픽=컨텍스트/aggregate-type 단위, 파티션 수 고정 지향(증설=신규 토픽 마이그레이션) |
| **소비-정상** | effectively-once | 처리 후 수동 커밋 + inbox(`event_id`) 멱등, 프로젝터별 독립 컨슈머 그룹으로 fan-out, 그룹 내 리밸런싱은 cooperative-sticky |
| **소비-실패(프로젝션·state-snapshot)** | 재정렬 자가치유 | **LWW seq 가드** — 들어온 aggregate seq ≤ 이미 적용한 seq면 무시, 크면 적용 |
| **소비-실패(프로젝션·commutative)** | 순서 무관 | `event_id` dedup(현행 inbox)만으로 충분 |
| **소비-실패(순서 결정적: 사가·부수효과·비가환)** | 앞 순서 미적용 원천 차단 | 바운드 재시도(transient) → 실패 지속 시 해당 aggregate의 꼬리를 seq 순서로 park(무관 aggregate는 계속 처리) → 원인 해소 후 seq 순서로 드레인 |
| **복구** | 순서 보장된 회복 | DLQ는 라이브 스트림에 되쏘지 않는다. 격리된 이벤트는 event_store에 그대로 남아 유실이 아니며, DLQ의 역할은 알림·감사 로그로 좁아진다. 회복은 사람이 개별 이벤트를 재주입하는 것이 아니라, event_store를 seq 순서로 재적용해 프로젝션을 원본과 다시 맞추는 **재구축**이다([[RFC-011-projection-rebuild-catchup]]) |

부속 규칙:

- **relay는 outbox를 순차 읽는다.** `sequence_no` ASC로 발행하고 발행 완료를 표시한다. 처리량이 실증적으로 문제가 되면 `aggregate_id` 해시로 나눠 각 relay가 자기 파티션 순서만 보장하는 파티션드 relay, 또는 CDC로 졸업하는 경로가 있으나 지금은 채택하지 않는다.
- **inbox를 확장한다.** 현재의 `event_id` dedup(전 컨텍스트 공통 정체성, [[ADR-022-event-identity]])에 **aggregate별 last-applied `sequence_no`**를 더한다. LWW 가드와 꼬리 격리 판정의 공통 토대다.
- **LWW 가드가 흡수하는 것과 흡수하지 못하는 것을 가른다.** state-snapshot 프로젝션은 최신 seq만 남으면 정확하므로 재정렬을 가드가 자가치유한다. 사가·부수효과처럼 외부에 이미 발생한 효과(예: 결제 호출)는 되돌릴 수 없어 LWW로 못 덮는다 — 그래서 별도로 꼬리 격리를 둔다.
- **leader election과의 관계.** ShedLock 리더 방식도 형태상 leader election의 일종이다. RFC-003/[[DESIGN-008-messaging-topology]]가 기각한 leader election은 주키퍼·etcd류 **외부 코디네이터**를 새로 들이는 무거운 방식이었고, 그 기각은 유지된다. ShedLock은 새 코디네이터 프로세스 없이 기존 분산 락 인프라로 리더를 뽑는 경량 방식이라 별개다. [[DESIGN-010-deployment-runtime]]도 relay 워크로드를 "leader 단일성 필요"·`replicas: 1(+standby)`로 배치하고 구체 구현(leader election vs SKIP LOCKED)은 구현 사이클로 열어 뒀을 뿐, 특정 코디네이터를 강제하지 않는다 — 이번 결정은 그 배치 전제 안에서 순차성의 구체 방식을 ShedLock으로 확정하는 것이지, 배치 결정을 뒤집는 것이 아니다.

### 결과 (Consequences)

- 좋은 점: relay가 순차적이라 애그리거트별 발행 순서가 항상 보존된다 — LWW 가드가 다뤄야 할 재정렬 폭이 relay 경쟁이 아니라 리밸런싱·재시도 같은 국소 지점으로 줄어든다.
- 좋은 점: DLQ가 라이브 스트림 재주입을 하지 않으므로, 실패 이벤트의 뒤늦은 재생이 순서를 깨는 경로 자체가 사라진다.
- 좋은 점: 새 코디네이터 없이 기존 분산 락 인프라로 relay 단일성을 얻는다.
- 좋은 점: 프로젝션 대다수(state-snapshot)는 LWW 가드로 거의 공짜로 재정렬을 자가치유하고, 되돌릴 수 없는 순서 결정적 소수(사가·부수효과)만 꼬리 격리를 문다.
- 트레이드오프: 단일 순차 relay는 병렬 발행을 포기한다. 무트래픽 학습 규모에선 수용하나, **재검토 트리거**: relay 처리량이 실증적으로 병목이 되면 파티션드 relay 또는 CDC로 이전한다.
- 트레이드오프: 꼬리 격리는 별도 park 저장소·드레인 로직을 요구해 구현 표면이 늘어난다(구체 스키마는 구현 사이클).
- 트레이드오프: DLQ가 복구 경로에서 빠지면서 실패 원인 수정 후 재구축이 유일한 회복 통로가 된다 — 프로젝션 규모가 커지면 재구축 비용도 함께 커진다([[RFC-011-projection-rebuild-catchup]] 전략에 의존).
- 트레이드오프: ShedLock 리더가 죽으면 재선출까지 발행이 멈춘다(단일성의 가용성 공백). 재선출·standby 전환 시간은 구현/운영 사이클에서 다룬다.

### 확인 (Confirmation)

- relay가 항상 단일 인스턴스(ShedLock 리더)에서만 활성인지, outbox를 `sequence_no` ASC로 순차 발행하는지 통합 테스트로 검증한다.
- 같은 aggregate에 대해 순서가 뒤바뀐 이벤트를 주입했을 때 LWW 가드가 오래된 이벤트를 drop하고 최신만 적용하는지 테스트한다.
- DLQ로 격리된 이벤트가 라이브 토픽으로 재주입되는 코드 경로(수동 재생 API·스크립트)가 존재하지 않는지 코드 리뷰로 확인한다.
- 순서 결정적 컨슈머(사가)가 실패 시 해당 aggregate의 후속 이벤트를 park하고 무관 aggregate는 계속 처리하는지 통합 테스트로 재현한다.
- 프로젝션 재구축이 event_store를 seq 순서로 재적용하는지는 [[RFC-011-projection-rebuild-catchup]] 재구축 경로의 확인 항목과 공유한다.

## 선택지 상세 (Pros and Cons of the Options)

### A. `SKIP LOCKED` 경쟁 소비 (원안, 폐기)

- 장점: relay 여러 인스턴스가 병렬로 처리량을 낼 수 있다. 별도 코디네이터가 필요 없다.
- 단점: 여러 relay가 서로 다른 outbox 행을 경쟁적으로 집어가면, 같은 aggregate의 두 이벤트를 서로 다른 relay가 서로 다른 시점에 발행해 도착 순서가 뒤집힐 수 있다.
- 기각 사유: 처리량을 위해 직렬화를 포기하는 선택이라, aggregate별 순서 계약을 발행 단계에서 스스로 깬다.

### B. leader election(외부 코디네이터) (기각, 유지)

- 장점: 진짜 단일 처리자를 강하게 보장한다.
- 단점: 주키퍼·etcd류 코디네이터와 리더 교체 로직이라는 운영 짐을 새로 진다.
- 기각 사유: ShedLock으로 같은 목적(단일 리더)을 새 코디네이터 없이 달성할 수 있다.

### D. DLQ 수동 재생 (원안, 폐기)

- 장점: 실패 이벤트를 사람이 판단해 빠르게 개별 재처리할 수 있다.
- 단점: 재생 시점이 이미 처리된 후속 이벤트보다 늦어, 앞 순서를 건너뛴 재주입이 된다. inbox는 "봤는가"만 보고 "건너뛰었는가"는 못 봐 이 순서 역전을 흡수하지 못한다.
- 기각 사유: event_store 재구축이 순서 보장된 회복 경로를 이미 제공하므로, 순서를 깨는 수동 되쏘기를 별도로 둘 이유가 없다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 꼬리 격리 park 저장소의 구체 스키마, relay 처리량이 실제로 병목이 될 때의 파티션드 relay/CDC 전환 임계, ShedLock 리더 장애·재선출 소요 시간. (outbox↔event_store 동일 datasource 전제는 [[ADR-027-event-store-outbox-atomicity]]로 확정 — 불변식 I-OUTBOX-1 + CDC 졸업 경로.)
- 관련: [[RFC-003-messaging-delivery]] · [[RFC-025-ordering-relay-dlq-reconciliation]] · [[DESIGN-008-messaging-topology]] · [[DESIGN-020-ordering-and-failure-handling]] · [[RFC-021-event-identity-and-global-ordering]] · [[RFC-011-projection-rebuild-catchup]] · [[DESIGN-010-deployment-runtime]] · [[ADR-005-event-store-mysql-table]] · [[ADR-022-event-identity]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-008-saga-orchestration-vs-choreography]] · [[ADR-018-event-store-recovery-semantics]]
- 계승: `09.event-ordering-and-delivery-guarantee.md`(v2 초기 스케치) — 파티션 키=`aggregate_id`·effectively-once 골격은 유지하되, relay 단일성(SKIP LOCKED→단일 순차 relay)과 DLQ 처리(수동 재생→재구축)는 [[RFC-025-ordering-relay-dlq-reconciliation]] 합의로 이 ADR이 대체한다.
