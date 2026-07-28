# ADR-027: event_store·outbox 원자성 — 동일 datasource 트랜잭셔널 아웃박스, CDC로 졸업

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-025-ordering-relay-dlq-reconciliation]] · **설계**: [[DESIGN-003-write-model]] · [[DESIGN-019-event-execution-layering]]
- **연관 ADR**: [[ADR-005-event-store-mysql-table]] · [[ADR-023-event-execution-layering]] · [[ADR-009-event-ordering-and-delivery-guarantee]]

---

## 맥락과 문제 (Context and Problem Statement)

쓰기 경로는 애그리거트를 커밋할 때 두 가지를 함께 남긴다 — event_store에 도메인 이벤트를 append하고, contract 통합 이벤트를 outbox에 insert한다. 커밋 후 relay가 outbox를 폴링해 Kafka로 비동기 발행한다([[ADR-009-event-ordering-and-delivery-guarantee]] · [[RFC-025-ordering-relay-dlq-reconciliation]]).

이 구조의 원자성은 **event_store append와 outbox insert가 같은 트랜잭션·같은 DB 커넥션에 있을 때만** 성립한다. ES 엔진이 독립 트랜잭션으로 append하면 "이벤트는 저장됐는데 outbox 기록은 실패 = 발행 유실"이라는 dual-write가 열린다([[DESIGN-003-write-model]] §4.2 자기리뷰가 이 구멍을 지목).

여러 문서가 산문으로 "같은 트랜잭션"을 반복 명시했으나([[ADR-005-event-store-mysql-table]] · [[ADR-023-event-execution-layering]] L41 · [[DESIGN-003-write-model]] §4.2), 이 전제를 **명명된 불변식**으로 한 줄 못박고 그 대가(event_store를 outbox와 다른 저장소로 분리 불가)를 명시적으로 수용·강제한 결정 문서는 없었다. [[analysis/06-design-weakness-triage]](C-1)·[[modules/06-command-infrastructure]] §7 반론2가 이를 **"뒤집기 비싼 one-way door를 미결 상태로 구현에 태우는 것"**으로 남겨 뒀다.

**event_store·outbox 원자성 전제를 강제되는 불변식으로 확정하고, 그로 인한 저장소 결합의 탈출 경로를 명시한다.**

## 결정 동인 (Decision Drivers)

- **정확성 불변식**: 2PC 없이 event append와 통합 이벤트 발행이 원자적이어야 한다(dual-write 차단).
- **단순성 우선**: 실트래픽 없는 학습 규모에서 분산 트랜잭션·별도 이벤트 저장 제품은 존재하지 않는 비용을 최적화한다.
- **one-way door 방어**: 되돌리기 비싼 결합을 채택하려면, 그 문을 여는 대가와 **나가는 문(졸업 경로)**을 결정 시점에 함께 못박아야 한다.

## 검토한 선택지 (Considered Options)

- **A. 동일 datasource 트랜잭셔널 아웃박스** — event_store append + outbox insert를 같은 트랜잭션·같은 커넥션에 묶는다. 둘은 같은 MySQL 인스턴스에 산다.
- **B. 별도 저장소 + 2PC / 분산 트랜잭션** — event_store와 outbox를 물리 분리하고 XA 등으로 원자성 확보.
- **C. 처음부터 CDC(binlog 테일링)** — outbox 테이블을 두지 않고 event_store의 binlog를 Debezium으로 테일링해 Kafka로 흘린다.

## 결정 (Decision Outcome)

**채택: A — 지금은 동일 datasource 트랜잭셔널 아웃박스, 병목이 실증되면 C(CDC)로 졸업한다.**

- **불변식 (I-OUTBOX-1)**: `event_store` append와 contract `outbox` insert는 **동일 트랜잭션·동일 datasource·동일 커넥션**에서 수행한다. 커밋 후 relay가 outbox를 비동기로 Kafka에 발행한다([[ADR-009-event-ordering-and-delivery-guarantee]]). 이 트랜잭션 경계를 벗어난 append/insert는 금지한다.
- **결합의 수용**: 위 불변식은 event_store를 outbox와 **다른 저장소/제품으로 분리하지 못한다**는 제약과 직결된다. 이는 [[ADR-005-event-store-mysql-table]](MySQL 직접 구현) 선택의 또 다른 근거이며, 여기서 그 대가를 명시적으로 수용한다. 성장 시 1차 스케일 축은 저장소 분리가 아니라 파티셔닝/샤딩이다.
- **졸업 경로 (탈출 문)**: event_store I/O가 성장해 outbox 폴링(핫 경로)과 경합하면, **CDC(Debezium이 event_store binlog를 테일링 → Kafka)**로 졸업한다. CDC로 가면 outbox-in-same-tx 자체가 불필요해져(변경 포착을 event_store의 자기 binlog가 대신함) 동일 datasource 결합이 해소된다. 즉 A의 결합은 **CDC로 되돌릴 수 있는 문**이지, 영구 봉인이 아니다. 이 경로는 이미 [[ADR-005-event-store-mysql-table]]·[[ADR-009-event-ordering-and-delivery-guarantee]]·[[analysis/08-k6-load-test-strategy]] Item B에 재검토 트리거로 흩어져 있던 것을 여기서 이 ADR의 공식 exit ramp로 지정한다.
- **졸업 트리거(측정 기반, 수치는 구현 후)**: [[analysis/08-k6-load-test-strategy]] Item B에서 부하 상승 시 relay/프로젝션 lag이 **발산**하거나, event_store append와 outbox 폴링의 I/O 경합이 관측되면 CDC 졸업을 개시한다. 트리거 수치 자체는 무트래픽 단계에서 정하지 않는다(learning 우선).

상세 시퀀스는 [[DESIGN-003-write-model]] §4.2·§4.4, 실행 레이어링은 [[ADR-023-event-execution-layering]] 참조.

### 결과 (Consequences)

- 좋은 점: 2PC 없이 append+발행 원자성이 성립해 dual-write가 닫힌다. 학습 규모에서 인프라가 단순하다(MySQL 하나). one-way door 우려가 명명된 졸업 경로(CDC)로 해소돼, 결합이 "탈출 가능한 결합"이 된다.
- 나쁜 점 / 트레이드오프: **CDC 졸업 전까지 event_store와 outbox는 같은 MySQL 인스턴스에 묶인다** — append-only event_store가 커져도 저장소 분리로는 못 빠져나가고, 파티셔닝/샤딩 또는 CDC 졸업만이 스케일 경로다. CDC 졸업 자체도 공짜가 아니다 — Debezium/Connect 인프라 도입, binlog에서 발행 순서·계약 매핑 재유도, 그리고 [[ADR-023-event-execution-layering]]의 "outbox=contract 타입 발행 지점" 전제를 CDC 파이프라인이 대체하도록 재설계해야 한다. **재검토 트리거**: 위 졸업 트리거.

### 확인 (Confirmation)

- event_store append와 outbox insert가 **같은 트랜잭션 안에서** 호출되는지 통합 테스트(Testcontainers)로 검증한다 — 커밋 롤백 시 event_store와 outbox가 함께 롤백되는지 확인.
- `EventStorePort`/outbox 저장 어댑터가 각자 새 트랜잭션(`REQUIRES_NEW`)·별도 datasource를 열지 않는지 아키텍처 테스트(Konsist) 또는 코드 리뷰 체크로 강제한다([[ADR-023-event-execution-layering]] §확인과 연동).
- Flyway상 event_store·outbox 테이블이 동일 스키마(command datasource)에 생성되는지 확인한다([[modules/06-command-infrastructure]]).

## 선택지 상세 (Pros and Cons of the Options)

### B. 별도 저장소 + 2PC / 분산 트랜잭션
- 장점: event_store와 outbox를 물리 분리해도 원자성 유지.
- 단점: XA/분산 트랜잭션 운영 복잡도·성능 비용이 크고, MySQL·Kafka 조합에서 실용적 2PC 경로가 빈약하다. 실트래픽 없는 학습 규모에 과설계.
- 기각 사유: 존재하지 않는 분리 요구를 위해 큰 복잡도를 지금 진다.

### C. 처음부터 CDC(binlog 테일링)
- 장점: outbox 테이블 불필요, event_store 자기 binlog가 진실원이라 dual-write 자체가 없음. 저장소 결합 문제도 없음.
- 단점: Debezium/Connect 인프라를 처음부터 운영해야 하고, 발행 순서·contract 매핑을 binlog에서 재유도하는 파이프라인이 초기부터 필요하다. 트랜잭셔널 아웃박스보다 학습 곡선·초기 구축 비용이 크다.
- 기각 사유(지금은): 무트래픽 단계에서 CDC 인프라 비용이 이르다. 단 **미래 졸업 목적지로 채택** — A의 exit ramp가 곧 C다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 졸업 트리거의 구체 수치(k6 Item B lag 발산 rate), snapshot 주기와의 상호작용, CDC 졸업 시 발행 순서 계약 재유도 상세.
- **닫는 항목**: [[modules/12-implementation-plan]] §3 C-1, [[analysis/06-design-weakness-triage]] C-1/C06, [[ADR-009-event-ordering-and-delivery-guarantee]] L106 미결정, [[ADR-023-event-execution-layering]] 미결정(동일 datasource 확인), [[ADR-005-event-store-mysql-table]] L64 확인 항목.
- 관련: [[RFC-025-ordering-relay-dlq-reconciliation]] · [[DESIGN-003-write-model]] · [[modules/06-command-infrastructure]] · [[analysis/08-k6-load-test-strategy]]
