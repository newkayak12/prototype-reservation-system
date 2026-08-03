# ADR-009: 이벤트 순서·전달 보장 — 단일 순차 relay + Kafka offset 순서, DLQ는 재구축으로 복구

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-003-messaging-delivery]] · [[RFC-025-ordering-relay-dlq-reconciliation]] · **설계**: [[DESIGN-008-messaging-topology]] · [[DESIGN-020-ordering-and-failure-handling]]
- **연관 ADR**: [[ADR-005-event-store-mysql-table]] · [[ADR-022-event-identity]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-008-saga-orchestration-vs-choreography]] · [[ADR-018-event-store-recovery-semantics]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 한 앱 안에서 호출 순서대로 트랜잭션을 탔다. 확정 다음 취소가 나면 순서가 뒤집힐 자리가 없었다. V2는 command가 만든 사실(이벤트)을 Kafka를 거쳐 query 측 프로젝터·사가에게 비동기로 전달한다([[DESIGN-008-messaging-topology]]). 파티션 키를 `aggregate_id`로 고정해 애그리거트별 순서를 보장하기로 했으나([[RFC-021-event-identity-and-global-ordering]]), 그 계약은 발행과 소비 양쪽에서 조용히 깨질 수 있다.

시나리오: 애그리거트가 `e1=ReservationConfirmed`(seq 5), `e2=ReservationCancelled`(seq 6)를 outbox에 순서대로 쌓는다.

1. **발행 측** — outbox relay를 여러 인스턴스로 **경쟁 소비**(각 relay가 서로 다른 행을 집는 방식)시키면, relay-1이 e1을 relay-2가 e2를 집어 e2가 Kafka에 먼저 도착할 수 있다.
2. **소비 측** — 컨슈머가 e1 처리에 실패해 DLQ로 보내고 e2를 먼저 처리한 뒤, 사람이 e1을 나중에 손으로 되쏘면 이미 지나간 순서를 건너뛴 재주입이 된다.

두 자리 모두 파티션 내 순서 보장이라는 전제를 무너뜨린다. 정상 흐름에서는 Kafka(파티션 내 순차 소비)가 이미 순서를 지키므로, 닫아야 할 자리는 이 둘뿐이다.

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
- **C. 단일 순차 relay(Quartz 클러스터 리더)** — 클러스터 스케줄러가 뽑은 단일 인스턴스가 outbox를 삽입 순서로 통짜 드레인·발행한다.

**소비 측 — 실패·DLQ 처리**
- **D. DLQ 수동 재생 (원안)** — 실패 이벤트를 사람이 판단해 라이브 스트림으로 다시 밀어 넣는다.
- **E. offset 순서 소비 + 꼬리 격리 + DLQ=알림/감사, 복구=event_store 재구축** — 순서는 파티션 offset 순서 소비로 보존하고, 순서 결정적 소비자는 실패 시 꼬리를 격리하며, DLQ는 라이브로 되쏘지 않는다.

## 결정 (Decision Outcome)

**채택: C(단일 순차 relay) + E(offset 순서 소비, DLQ는 재구축으로 복구).** relay 경쟁(A)과 DLQ 수동 재생(D)은 둘 다 처리량·즉응성을 위해 순서 직렬화를 포기하는 선택이라, 애그리거트별 순서 계약을 각자의 자리에서 스스로 깬다. 새 코디네이터(B)는 그 대가로 운영 짐만 늘린다.

순서·전달 보장은 다음처럼 구간별로 선다. 파티션 키=`aggregate_id`·토픽=컨텍스트/aggregate-type 단위·at-least-once+inbox 멱등([[DESIGN-008-messaging-topology]])은 그대로 유지되는 전제이고, 이번 결정이 바꾸는 자리는 **발행 relay의 단일성(→ Quartz 클러스터)**, **소비 순서 보존의 메커니즘(→ Kafka offset 순서)**, **DLQ의 위치**다.

| 구간 | 보장 | 메커니즘 |
|---|---|---|
| **발행**(command→Kafka) | 애그리거트별 순차 발행 | **단일 순차 relay(Quartz 클러스터, `@DisallowConcurrentExecution`)** 가 outbox를 **삽입 순서(PK `id` ASC)** 로 통짜 드레인. Kafka 프로듀서 idempotent. 경쟁 드레인(A)은 폐기 |
| **전달**(Kafka) | 파티션 내 순서, at-least-once | 파티션 키=`aggregate_id`, 토픽=컨텍스트/aggregate-type 단위, 파티션 수 고정 지향(증설=신규 토픽 마이그레이션) |
| **소비-정상** | effectively-once | 처리 후 수동 커밋 + inbox(`event_id`) 멱등, 프로젝터별 독립 컨슈머 그룹으로 fan-out, 그룹 내 리밸런싱은 cooperative-sticky |
| **소비-실패(프로젝션·state-snapshot, ES·비-ES 공통)** | offset 순서로 보존 | **파티션당 단일 스레드 offset 순서 apply + `event_id` dedup.** 각 producer가 삽입 순서로 통짜 드레인하므로 relay 겹침(좀비 중첩)이 나도 각 이벤트의 **최초 등장 offset이 seq 순서**다 — 겹침은 중복만 만들고(역전 아님) dedup이 흡수한다. ES·비-ES를 가리지 않고 동일하게 선다 |
| **소비-실패(프로젝션·commutative)** | 순서 무관 | `event_id` dedup(현행 inbox)만으로 충분 |
| **소비-실패(순서 결정적: 사가·부수효과·비가환)** | 앞 순서 미적용 원천 차단 | 바운드 재시도(transient) → 실패 지속 시 해당 aggregate의 꼬리를 **offset(도착) 순서**로 park(무관 aggregate는 계속 처리) → 원인 해소 후 offset 순서로 드레인 |
| **복구** | 순서 보장된 회복 | DLQ는 라이브 스트림에 되쏘지 않는다 — DLQ의 역할은 알림·감사 로그로 좁아진다. 회복은 사람이 개별 이벤트를 재주입하는 것이 아니라 **재구축**이다. 재구축 원천은 컨텍스트 종류에 따라 갈린다: **ES 컨텍스트**는 event_store를 seq 순서로 재적용하고(격리된 이벤트가 event_store에 그대로 남아 유실이 아니다), **비-ES·lookup 컨텍스트**는 event_store가 없어 원본 상태 테이블에서 read model을 재빌드한다([[RFC-011-projection-rebuild-catchup]] 결정 1) |

부속 규칙:

- **relay는 outbox를 삽입 순서로 통짜 드레인한다(경쟁 드레인 금지).** 정렬 키는 outbox PK `id`(전역 단조)이고, `sequence_no`가 아니다. `sequence_no`는 애그리거트별 순번이라 혼합 outbox의 전역 정렬 키가 될 수 없고, 비-ES·lookup 컨텍스트의 outbox 행에는 애초에 존재하지 않는다([[ADR-022-event-identity]]). 같은 애그리거트의 두 이벤트는 삽입 순서가 곧 `sequence_no` 순서라, 삽입 순서 드레인이 애그리거트별 순서를 그대로 보존한다([[DESIGN-020-ordering-and-failure-handling]] §2 · [[RFC-032-non-es-state-copy-reordering]] 결정 2). 발행 완료는 행에 표시한다. **각 relay 인스턴스가 서로 다른 행을 나눠 집는 경쟁 드레인(A안)은 영구 폐기한다** — 그것이 애그리거트별 순서를 역전시키는 유일한 발행-측 발생원이기 때문이다(아래 §불변식). 처리량이 실증적으로 문제가 되면 `aggregate_id` 해시로 나눠 각 relay가 자기 파티션 순서만 보장하는 파티션드 relay, 또는 CDC로 졸업하는 경로가 있으나 지금은 채택하지 않는다.
- **inbox는 `event_id` dedup만 유지한다.** 정체성은 전 컨텍스트 공통인 `event_id`([[ADR-022-event-identity]]) 하나이고, aggregate별 `last-applied sequence_no` 같은 순서 토큰을 inbox에 두지 않는다. 순서 보존은 소비 측 비교 토큰이 아니라 **파티션 offset 순서 + 단일 순차 relay**에 기댄다. (구안이 LWW 가드·꼬리 격리 판정용으로 두려던 `last-applied sequence_no`는 이번 결정으로 제거된다 — LWW가 사라지고, 꼬리 park도 offset 순서로 판정한다.)
- **왜 offset 순서만으로 충분한가(구안 LWW 가드 폐기).** 순서를 실제로 깨는 발행-측 발생원은 **경쟁 드레인 하나뿐**이다. 단일 순차 relay가 삽입 순서로 통짜 드레인하면, 리더 교체 창에서 구·신 리더가 잠깐 겹쳐 이중 발행하더라도 **두 인스턴스 모두 삽입 순서로** 낸다 — 그래서 각 이벤트의 최초 등장 offset은 항상 seq 순서이고, 뒤늦은 재등장은 `event_id` dedup이 버린다(중복이지 역전이 아니다). 소비 측도 파티션당 단일 스레드 offset 순서 apply를 지키면 재정렬 발생원이 없다. 결국 구안의 **LWW seq 가드(`seq ≤ 적용됨 → 무시`)는 일어날 수 없는 역전을 막는 잉여**였고, 오히려 파트별 세부 이벤트처럼 **한 행의 일부 필드만 나르는 delta 이벤트**([[ADR-008-saga-orchestration-vs-choreography]] 계열의 매장 정보 세분화)에서는 낮은 seq를 drop해 **다른 필드 갱신을 유실**시켰다. offset 순서 소비는 drop이 없어 delta도 안전하다.
- **불변식 두 개(암묵 → 명문화).** 위 정합성은 아래 두 규칙 위에서만 선다. 구안에서는 LWW 뒤에 암묵적으로 숨어 있었으므로 계약으로 못박는다.
  - **I-RELAY-ORDER**: relay는 항상 **삽입 순서 통짜 드레인**한다. 여러 인스턴스가 서로 다른 행을 나눠 집는 경쟁/파티션드 드레인을 금지한다.
  - **I-CONSUME-ORDER**: 컨슈머는 **파티션당 단일 스레드로 offset 순서 apply**하고 `event_id`로 dedup한다. 레코드를 스레드풀로 넘기는 멀티스레드 async apply를 금지한다.
- **producer 펜싱을 이번 사이클에 넣지 않는다.** Quartz 리더 교체 창의 좀비 이중 발행은 위 논리로 **중복만** 만들고 dedup이 흡수하므로 순서 정확성에 무관하다. 펜싱(transactional producer + epoch)은 중복 *양*을 줄일 뿐이라 무트래픽 규모에서 과투자다. 트래픽이 정당화하면 파티션드 relay/CDC 졸업과 함께 재검토한다(→ 재검토 트리거).
- **leader election과의 관계.** Quartz 클러스터 방식도 형태상 leader election의 일종이다. RFC-003/[[DESIGN-008-messaging-topology]]가 기각한 leader election은 주키퍼·etcd류 **외부 코디네이터**를 새로 들이는 무거운 방식이었고, 그 기각은 유지된다. Quartz는 [[ADR-008-saga-orchestration-vs-choreography]]의 예약 타임아웃 스케줄러로 **어차피 도입하는** 인프라이고, JDBC JobStore 클러스터 모드(`isClustered=true`)가 `QRTZ_LOCKS` 행 락으로 트리거를 단일 노드에서만 발화시키며 `@DisallowConcurrentExecution`이 같은 job의 클러스터 전역 동시 실행을 막는다 — 새 코디네이터 프로세스 없이 relay 단일성을 얻는 경량 방식이라 별개다. [[DESIGN-010-deployment-runtime]]도 relay 워크로드를 "leader 단일성 필요"로 배치하되 구체 구현을 구현 사이클로 열어 뒀을 뿐, 특정 코디네이터를 강제하지 않는다 — 이번 결정은 그 배치 전제 안에서 순차성의 구체 방식을 Quartz 클러스터로 확정하고, 배치 형태를 `replicas: 1(+standby)`에서 **N 대칭 배치 + Quartz 중재**로 바꾼다(standby 수동 관리 소거).

### 결과 (Consequences)

- 좋은 점: relay가 순차적이고 삽입 순서 통짜 드레인이라, 애그리거트별 발행 순서가 항상 보존된다 — 소비 측이 다뤄야 할 재정렬 발생원 자체가 사라지고(경쟁 드레인 폐기 + 단일 스레드 apply), 순서 보존이 별도 비교 토큰 없이 offset 순서에만 기댄다.
- 좋은 점: DLQ가 라이브 스트림 재주입을 하지 않으므로, 실패 이벤트의 뒤늦은 재생이 순서를 깨는 경로 자체가 사라진다.
- 좋은 점: 새 코디네이터 없이, 어차피 도입하는 Quartz 클러스터로 relay 단일성을 얻는다.
- 좋은 점: LWW seq 가드를 폐기하면서 **파트별 세부(delta) 이벤트의 필드 유실 위험이 사라진다** — offset 순서 소비는 drop 없이 모든 이벤트를 순서대로 apply한다. ES·비-ES state-snapshot 프로젝션이 같은 메커니즘(offset 순서 + `event_id` dedup)으로 서서 컨텍스트별 분기가 준다.
- 좋은 점: 꼬리 park를 seq가 아니라 offset(도착) 순서로 판정하므로, `sequence_no`가 없는 비-ES 이벤트(예: `payment`)를 구독하는 순서 결정적 소비자(`reservation` 사가)에도 동일하게 적용된다 — 구안이 "비-ES엔 꼬리 격리 등가물이 없다"고 남긴 미결이 offset 기반으로 흡수된다.
- 트레이드오프: 단일 순차 relay는 병렬 발행을 포기한다. 무트래픽 학습 규모에선 수용하나, **재검토 트리거**: relay 처리량이 실증적으로 병목이 되면 파티션드 relay 또는 CDC로 이전하고, 그때 producer 펜싱을 함께 검토한다.
- 트레이드오프: 꼬리 격리는 별도 park 저장소·드레인 로직을 요구해 구현 표면이 늘어난다(구체 스키마는 구현 사이클).
- 트레이드오프: DLQ가 복구 경로에서 빠지면서 실패 원인 수정 후 재구축이 유일한 회복 통로가 된다 — 프로젝션 규모가 커지면 재구축 비용도 함께 커진다([[RFC-011-projection-rebuild-catchup]] 전략에 의존).
- 트레이드오프: Quartz 리더가 죽으면 다음 fire에서 다른 노드가 재발화할 때까지 발행이 멈춘다(단일성의 가용성 공백). 재발화 지연은 트리거 주기·`clusterCheckinInterval`에 묶이며 구현/운영 사이클에서 수치로 다룬다.
- 트레이드오프: 비-ES·lookup 컨텍스트의 재구축은 event_store가 아니라 원본 테이블 재빌드라 **현재 상태만** 회복하고 개별 이벤트를 되살리지 못한다 — state-snapshot 프로젝션에는 동등하지만, 과거 이벤트 열 자체가 필요한 소비자에는 등가가 아니다(순서 보존·꼬리 park는 offset 기반으로 동일하게 서므로 이 항은 *복구* 등가성에 한정된다).

### 확인 (Confirmation)

- relay가 항상 단일 인스턴스(Quartz 클러스터 리더)에서만 활성인지, outbox를 삽입 순서(PK `id` ASC)로 통짜 드레인·발행하는지 통합 테스트로 검증한다. 정렬 키가 `sequence_no`가 아님을 함께 확인한다 — 비-ES 행에는 그 값이 없다. 경쟁 드레인(서로 다른 행 분할) 코드 경로가 존재하지 않는지 코드 리뷰로 확인한다(I-RELAY-ORDER).
- 같은 aggregate에 대해 relay 이중 발행(리더 교체 창)을 재현했을 때, 컨슈머가 파티션 offset 순서로 apply하고 `event_id` dedup으로 중복을 버려 **최종 apply 순서가 seq 순서와 일치**하는지 테스트한다 — LWW 가드 없이 offset 순서만으로 서는지가 검증 대상이다.
- 컨슈머가 파티션당 단일 스레드로만 apply하는지(멀티스레드 async apply 경로 부재) 코드 리뷰로 확인한다(I-CONSUME-ORDER).
- DLQ로 격리된 이벤트가 라이브 토픽으로 재주입되는 코드 경로(수동 재생 API·스크립트)가 존재하지 않는지 코드 리뷰로 확인한다.
- 순서 결정적 컨슈머(사가)가 실패 시 해당 aggregate의 후속 이벤트를 **offset 순서로 park**하고 무관 aggregate는 계속 처리하는지 통합 테스트로 재현한다 — park·드레인 판정이 `sequence_no`가 아니라 도착 offset 순서를 전제하므로 ES·비-ES 이벤트에 공통 적용된다([[DESIGN-020-ordering-and-failure-handling]] §5).
- 프로젝션 재구축 확인은 컨텍스트에 따라 갈린다: **ES 컨텍스트**는 프로젝션 재구축이 event_store를 seq 순서로 재적용하는지, **비-ES·lookup 컨텍스트**는 event_store가 없어 원본 상태 테이블에서 read model을 재빌드하는지를 검증한다 — [[RFC-011-projection-rebuild-catchup]] 재구축 경로의 확인 항목과 공유한다.

## 선택지 상세 (Pros and Cons of the Options)

### A. `SKIP LOCKED` 경쟁 소비 (원안, 폐기)

- 장점: relay 여러 인스턴스가 병렬로 처리량을 낼 수 있다. 별도 코디네이터가 필요 없다.
- 단점: 여러 relay가 서로 다른 outbox 행을 경쟁적으로 집어가면, 같은 aggregate의 두 이벤트를 서로 다른 relay가 서로 다른 시점에 발행해 도착 순서가 뒤집힐 수 있다. **이것이 애그리거트별 순서를 역전시키는 유일한 발행-측 발생원이다** — 단일 리더의 이중 발행(좀비 중첩)이 삽입 순서를 유지해 중복만 만드는 것과 대비된다.
- 기각 사유: 처리량을 위해 직렬화를 포기하는 선택이라, aggregate별 순서 계약을 발행 단계에서 스스로 깬다. 이 발생원을 닫는 것이 offset 순서 소비가 성립하는 전제(I-RELAY-ORDER)다.

### B. leader election(외부 코디네이터) (기각, 유지)

- 장점: 진짜 단일 처리자를 강하게 보장한다.
- 단점: 주키퍼·etcd류 코디네이터와 리더 교체 로직이라는 운영 짐을 새로 진다.
- 기각 사유: Quartz 클러스터로 같은 목적(단일 리더)을, 어차피 도입하는 스케줄러 인프라 위에서 새 코디네이터 없이 달성할 수 있다.

### D. DLQ 수동 재생 (원안, 폐기)

- 장점: 실패 이벤트를 사람이 판단해 빠르게 개별 재처리할 수 있다.
- 단점: 재생 시점이 이미 처리된 후속 이벤트보다 늦어, 앞 순서를 건너뛴 재주입이 된다. inbox는 "봤는가"만 보고 "건너뛰었는가"는 못 봐 이 순서 역전을 흡수하지 못한다.
- 기각 사유: event_store 재구축이 순서 보장된 회복 경로를 이미 제공하므로, 순서를 깨는 수동 되쏘기를 별도로 둘 이유가 없다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 꼬리 격리 park 저장소의 구체 스키마, relay 처리량이 실제로 병목이 될 때의 파티션드 relay/CDC 전환 임계와 producer 펜싱 도입 여부, Quartz 리더 장애·재발화 소요 시간. (outbox↔event_store 동일 datasource 전제는 [[ADR-027-event-store-outbox-atomicity]]로 확정 — 불변식 I-OUTBOX-1 + CDC 졸업 경로.) 비-ES 컨텍스트(`payment`, [[ADR-015-payment-acl-boundary]])의 이벤트를 순서 결정적 소비자(`reservation` 사가, [[ADR-008-saga-orchestration-vs-choreography]])가 구독하는 흐름은 이번 결정으로 offset 순서 보존 + offset 기반 꼬리 park를 함께 얻는다 — 다만 event_store가 없어 *복구* 시 개별 과거 이벤트 재생이 안 되는 한계는 [[DESIGN-003-write-model]]의 payment ES 승격 미결과 정합하는 잔여 항으로 남는다.
- 관련: [[RFC-003-messaging-delivery]] · [[RFC-025-ordering-relay-dlq-reconciliation]] · [[RFC-032-non-es-state-copy-reordering]] · [[DESIGN-008-messaging-topology]] · [[DESIGN-020-ordering-and-failure-handling]] · [[RFC-021-event-identity-and-global-ordering]] · [[RFC-011-projection-rebuild-catchup]] · [[DESIGN-010-deployment-runtime]] · [[ADR-005-event-store-mysql-table]] · [[ADR-022-event-identity]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-008-saga-orchestration-vs-choreography]] · [[ADR-018-event-store-recovery-semantics]]
- 계승: `09.event-ordering-and-delivery-guarantee.md`(v2 초기 스케치) — 파티션 키=`aggregate_id`·effectively-once 골격은 유지하되, relay 단일성(SKIP LOCKED→단일 순차 relay)과 DLQ 처리(수동 재생→재구축)는 [[RFC-025-ordering-relay-dlq-reconciliation]] 합의로 이 ADR이 대체한다.
- 정정 (2026-07-30): relay 발행 정렬 키를 `sequence_no` ASC → 삽입 순서(outbox PK `id` ASC)로 고친다. `sequence_no`는 애그리거트별 순번이라 혼합 outbox의 전역 정렬 키가 못 되고 비-ES 행에는 존재하지 않는다 — [[DESIGN-020-ordering-and-failure-handling]] §2·[[RFC-032-non-es-state-copy-reordering]] 결정 2가 이미 확정한 값을 이 ADR이 옛 값으로 들고 있었다.
- 정정 (2026-08-03): **LWW seq 가드를 전면 폐기하고 순서 보존을 Kafka offset 순서로 대체한다.** ① 순서를 깨는 발행-측 발생원은 경쟁 드레인 하나뿐이고, 단일 순차 relay의 삽입 순서 통짜 드레인 + 소비 측 offset 순서 apply + `event_id` dedup이면 리더 교체 창의 이중 발행조차 중복만 만들어(역전 아님) LWW가 잉여였다. ② 파트별 세부(delta) 이벤트에서 LWW의 낮은-seq drop은 다른 필드 갱신을 유실시키는 결함이었다. 동반 변경: relay 단일성 기전 ShedLock → **Quartz 클러스터**(어차피 도입하는 예약 타임아웃 스케줄러 재사용), inbox의 aggregate별 `last-applied sequence_no` 제거(dedup은 `event_id`만), 꼬리 park 판정을 seq → **offset(도착) 순서**로 변경(비-ES 소비자에도 적용), producer 펜싱 미도입(좀비 중첩=중복, dedup 흡수), 불변식 **I-RELAY-ORDER**·**I-CONSUME-ORDER** 명문화, 배치 형태 `replicas:1(+standby)` → Quartz N 대칭. 연쇄: [[DESIGN-020-ordering-and-failure-handling]]·[[DESIGN-010-deployment-runtime]]·[[RFC-025-ordering-relay-dlq-reconciliation]]·[[RFC-032-non-es-state-copy-reordering]]·[[RFC-021-event-identity-and-global-ordering]]·[[ADR-022-event-identity]]·데이터 스키마.
