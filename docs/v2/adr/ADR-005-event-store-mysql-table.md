# ADR-005: 이벤트 스토어를 MySQL append-only 테이블로 직접 구현한다

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-001-v2-cqrs-and-event-sourcing]] · **설계**: [[DESIGN-009-event-store-lifecycle]]
- **연관 ADR**: [[ADR-002-selective-event-sourcing-scope]]

---

## 맥락과 문제 (Context and Problem Statement)

ADR-002에서 `reservation`·`timetable`·`restaurant` 셋을 진짜 ES로 정했다. 이들의 쓰기 저장소, 즉 이벤트 스토어를 **무엇으로 구현하는가**가 다음 질문이다.

이벤트 스토어에 필요한 것은 셋이다. append-only 이벤트 저장과 애그리거트별 스트림, 같은 스트림의 이중 append를 막는 정확성 백스톱, 그리고 리플레이로 상태를 재구성하고 스냅샷으로 단축하는 경로다.

프로젝트는 이미 MySQL·Flyway·JPA를 운영 중이고, V1부터 Outbox 패턴을 써 왔다. 새 저장소를 고르면 이 자산과 이중화된다.

**이벤트 스토어를 전용 제품으로 도입할 것인가, 기존 MySQL 위에 직접 구현할 것인가?**

## 결정 동인 (Decision Drivers)

- 현 규모·트래픽에 지불할 운영 복잡도의 상한.
- 기존 인프라(MySQL·Flyway·Outbox) 재사용 대 신규 인프라 도입.
- 이벤트 저장과 통합 이벤트 발행의 원자성 — 한 트랜잭션에 묶을 수 있는가.
- 비용보다 학습·효용. "직접 만들어 보는" 가치도 근거에 든다.

## 검토한 선택지 (Considered Options)

- **A. 전용 이벤트 스토어 제품** — EventStoreDB·Axon 등. 스트림·구독·스냅샷을 기성으로 제공.
- **B. MySQL append-only 이벤트 테이블 직접 구현** — 기존 인프라 위에 이벤트 테이블을 직접 얹는다.

## 결정 (Decision Outcome)

**채택: B — MySQL append-only 이벤트 테이블 직접 구현.**

전용 제품은 현 규모·트래픽에 운영 복잡도가 과하고, 기존 MySQL/Outbox 자산과 이중화된다. B는 신규 인프라 없이 요구 셋을 채우고, 이벤트 저장을 Outbox 발행과 한 트랜잭션에 묶는다.

핵심 구조는 다음과 같다. 상세 메커니즘은 [[DESIGN-009-event-store-lifecycle]]로 위임한다.

- **저장 모델**: `event_store` 테이블에 `(aggregate_type, aggregate_id, sequence_no, event_type, event_version, payload, occurred_at)`를 append-only로 쌓는다.
- **동시성 백스톱**: `(aggregate_id, sequence_no)`에 UNIQUE 제약을 둬 같은 스트림의 이중 append를 최종 거절한다(정확성 = safety). 동시 쓰기를 직렬화하는 동시성 제어 전략(비관 락 등)은 [[ADR-016-aggregate-concurrency-pessimistic-lock]]에서 다룬다.
- **스냅샷 최적화**: V1의 기존 `*Snapshot` 패턴을 ES 스냅샷 최적화로 재활용한다.
- **Outbox 연계**: 이벤트 저장과 통합 이벤트 발행을 한 트랜잭션으로 묶어 V1의 Outbox 운영을 계승한다.

### 결과 (Consequences)

**좋은 점**

- 신규 인프라가 없다. MySQL·Flyway·Outbox를 그대로 쓴다.
- 이벤트 저장·상태·Outbox가 한 트랜잭션에 들어가 원자성이 선다.
- 기존 도구로 조회·디버깅이 되므로 운영이 단순하다.
- ES의 스트림·스냅샷·동시성 제어를 직접 구현하며 학습·효용을 얻는다.

**트레이드오프**

- 스트림·스냅샷·구독·동시성 제어를 직접 구현한다 — 제품이 대신해 주던 코드 책임을 진다.
- 초대규모에서는 전용 제품 대비 성능·기능 한계가 있다.
- **재검토 트리거**: 트래픽 증가로 MySQL 이벤트 테이블이 성능 한계에 닿으면, 전용 제품 또는 CDC 기반 확장으로의 전환 기준을 그때 정한다.

### 확인 (Confirmation)

- 이벤트 스토어 스키마·인덱스가 Flyway 마이그레이션으로 버전 관리되는지 코드 리뷰로 확인.
- `(aggregate_id, sequence_no)` UNIQUE 제약과 append-only 규칙이 아키텍처 테스트로 강제되는지 확인.
- 이벤트 저장과 Outbox 기록이 동일 트랜잭션 경계에 묶이는지 테스트로 검증(불변식 I-OUTBOX-1 · [[ADR-027-event-store-outbox-atomicity]]).

## 선택지 상세 (Pros and Cons of the Options)

### A. 전용 이벤트 스토어 제품

- 장점: 스트림·구독·스냅샷 등 ES 기능을 기성으로 제공한다.
- 단점: 신규 인프라·운영·학습 비용이 든다. 기존 MySQL/Outbox 자산과 이중화된다.
- 기각 사유: 현 규모·트래픽에 운영 복잡도가 과하다. 얻는 기능이 아직 필요한 규모에 이르지 않았다.

### B. MySQL append-only 이벤트 테이블 직접 구현

- 장점: 기존 인프라 재사용, Outbox와 한 트랜잭션, 운영·디버깅 단순.
- 단점: 스트림·스냅샷·동시성 제어를 직접 구현. 초대규모 성능 한계 가능.

## 추가 정보 (More Information)

- **미결정 (→ 별도 ADR로 위임)**: 스냅샷 스키마 진화는 [[ADR-010-event-schema-evolution]], 복구 의미론은 [[ADR-018-event-store-recovery-semantics]], 이벤트 정체성·순서는 [[ADR-022-event-identity]]에서 다룬다. 이 ADR은 "제품이냐 직접 MySQL이냐"라는 구현 수단 결정 하나에 한정한다.
- **미결정 (→ 구현 사이클)**: 스냅샷 주기 N, 페이로드 직렬화 정책, 이벤트 테이블 파티셔닝·아카이빙 수치.
- 관련: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-004-event-store-schema-evolution]] · [[DESIGN-009-event-store-lifecycle]] · [[DESIGN-003-write-model]] · [[ADR-002-selective-event-sourcing-scope]]
