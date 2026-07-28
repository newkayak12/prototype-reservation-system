# ADR-023: 이벤트 실행 레이어링 — 타입 소유는 core, 번역·apply 조립은 application, infra는 bytes-only

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-024-domain-event-type-and-replay-layering]] · **설계**: [[DESIGN-019-event-execution-layering]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 이벤트 소싱이 없다. 예약을 확정하면 `reservation` 테이블의 상태 컬럼을 직접 `UPDATE`했다. "이벤트 객체가 어느 모듈 소유냐", "저장된 이벤트로 상태를 어떻게 재조립하냐" 같은 질문 자체가 성립하지 않았다 — 상태가 곧 진실원이었다.

V2에서는 흐름이 달라진다. 애그리거트가 이벤트를 만들고(`Reservation.handle(ConfirmReservation) → ReservationConfirmed`), 그 이벤트가 진실원으로 event_store에 append되며, 상태가 필요하면 `events.fold(초기){ s, e -> s.apply(e) }`로 되감아 재조립한다. 그런데 밖으로 나가는 것은 이 내부 이벤트가 아니라 얇은 통합 이벤트(published language)다.

이 흐름을 모듈 의존성 매트릭스([[DESIGN-002-module-structure]] §4.4)와 겹쳐 놓으면 두 자리가 비어 있었다. ① 애그리거트가 반환하는 `ReservationConfirmed`는 어느 모듈 타입인가, 그리고 내부→통합 번역은 누가 하는가. ② `apply`는 애그리거트(core) 소유인데, ES 엔진은 `command-infrastructure`라 core를 부를 수 없다 — 그 fold를 누가 돌리는가.

**매트릭스(core·infra 제약)를 바꾸지 않고, 이벤트의 저장·재생·발행 실행을 어느 계층이 조립할 것인가?**

## 결정 동인 (Decision Drivers)

- [[DESIGN-002-module-structure]] §4.4 매트릭스를 변경 없이 그대로 만족해야 한다.
- 내부 도메인 이벤트 ≠ 통합 이벤트(contract) 분리([[RFC-023-event-schema-contract-management]])를 유지해야 한다.
- event_store append와 통합 이벤트 발행(outbox) 사이의 dual-write(원자성 붕괴)를 막아야 한다.
- 리플레이 `apply` fold의 조립 책임을 계층 하나에 명확히 둬야 한다.

## 검토한 선택지 (Considered Options)

- **A. core 타입 반환 + application이 매핑·조립, infra는 bytes-only** — 애그리거트는 core 타입만 반환하고, `command-application`이 core→contract 매핑·발행과 리플레이 fold 조립을 모두 맡으며, `command-infrastructure`는 직렬화 레코드(`StoredEvent`) I/O만 한다.
- **B. contract 타입 직접 반환** — 애그리거트가 통합 이벤트를 바로 반환.
- **C. contract를 shared처럼 취급(매트릭스 완화)** — `core → contract` 의존을 허용해 B를 합법화.
- **D. event_store에 contract(통합) 이벤트 저장** — infra가 contract 타입만 알아도 재생 가능하게 함.

## 결정 (Decision Outcome)

**채택: A.** 매트릭스를 바꾸지 않고 이미 허용된 의존(`command-application → command-core`, `command-application → contract`) 위에 실행 책임을 배정한다.

- **애그리거트는 core 타입(내부 도메인 이벤트)을 반환한다.** `command-core`가 자기 자신의 이벤트 타입을 쥔다 — contract 타입은 core에서 절대 소유하지 않는다.
- **core→contract 매핑·발행 주체는 `command-application`(UseCase)이다.** 매트릭스가 이미 application에 core·contract 양쪽 import를 허용하므로, 이 결정은 새 권한 부여가 아니라 이미 있는 권한에 대한 책임 배정이다.
- **리플레이 `apply` fold의 조립 주체도 `command-application`이다.** UseCase가 `StoredEvent`를 core `DomainEvent`로 역직렬화하고 `events.fold(base){ s,e -> s.apply(e) }`를 돌린다.
- **event_store 저장 형태는 직렬화 `StoredEvent`다** — `aggregateId`, `sequenceNo`, `eventType`(타입 태그), `payload`(직렬화 JSON), `occurredAt`으로 구성되며, `command-infrastructure`는 이 레코드의 I/O만 수행한다.
- **event_store append와 contract outbox insert는 동일 트랜잭션·동일 datasource다.** 커밋 후 relay가 outbox를 비동기로 Kafka에 발행한다.
- **핵심 불변식**: `command-infrastructure`는 core 이벤트 타입을 절대 쥐지 않는다. event_store 경로에서 오가는 것은 타입-불가지의 `StoredEvent`뿐이며, core 이벤트 타입을 아는 유일한 계층은 `command-application`이다. [[DESIGN-002-module-structure]] §4.4의 "infra는 contract 이벤트 타입만 안다"는 서술은 발행 경로(relay → Kafka) 한정으로 읽는다.

상세 시퀀스·`EventStorePort`/`StoredEvent` 스키마는 [[DESIGN-019-event-execution-layering]] 참조.

### 결과 (Consequences)

- 좋은 점: 매트릭스 무변경으로 내부/통합 이벤트 분리가 유지된다. append(직렬화)와 리플레이(역직렬화+fold)가 대칭으로 닫힌다. append+outbox insert가 동일 트랜잭션이라 원자적 발행이 성립해 dual-write를 막는다.
- 나쁜 점 / 트레이드오프: 매핑·직렬화·역직렬화·fold 조립 책임이 모두 `command-application`(UseCase)에 집중돼 유스케이스 코드가 무거워진다. event_store와 outbox가 같은 datasource라는 전제가 성립해야만 동일 트랜잭션 원자성이 실제로 서며, 이 전제가 깨지면(예: 별도 datasource로 분리) 트랜잭션 경계를 재검토해야 한다. outbox→Kafka relay의 순서 보존은 이 결정 범위 밖이다.

### 확인 (Confirmation)

- `EventStorePort`(`load`/`append`) 시그니처가 `StoredEvent`만 노출하고 core/contract 도메인 타입을 노출하지 않는지 확인한다 — 이것이 불변식의 강제 지점이다.
- `command-infrastructure`가 `command-core`를 import하지 않는지 모듈 의존성 검사(아키텍처 테스트)로 확인한다.
- event_store append와 outbox insert가 동일 트랜잭션 안에서 호출되는지 코드 리뷰 체크로 확인한다.

## 선택지 상세 (Pros and Cons of the Options)

### B. contract 타입 직접 반환
- 장점: 번역 계층이 없어 단순하다.
- 단점: `command-core → contract`는 매트릭스가 금지한다. core의 build.gradle에 contract 의존이 없어 물리적으로 불가하다.
- 기각 사유: 매트릭스 위반.

### C. contract를 shared처럼 취급(매트릭스 완화)
- 장점: B를 합법화해 번역 계층을 없앤다.
- 단점: 내부 이벤트 ≠ 통합 이벤트 구분이 붕괴한다. 가장 안정적이어야 할 core가 가장 자주 바뀌는 contract에 의존해 의존 안정성이 역전된다. contract가 단일 모듈이면 모든 컨텍스트의 core가 서로의 통합 이벤트를 보게 되어 컨텍스트 격리가 무너진다. event_store가 published 계약 버저닝의 인질이 된다.
- 기각 사유: 이미 Accepted된 매트릭스와 [[RFC-023-event-schema-contract-management]]를 뒤집어야 한다.

### D. event_store에 contract(통합) 이벤트 저장
- 장점: infra가 contract 타입만 알아도 재생이 가능하다.
- 단점: published 계약을 진실원으로 삼게 되어 수년치 히스토리 리플레이가 발행 계약 버전에 묶인다.
- 기각 사유: 선택지 C에서 기각한 결합을 event_store 경로로 뒷문 삽입하는 것과 같다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: outbox→Kafka relay의 순서 계약(별도 결정 범위), 리플레이 fold의 base 상태를 스냅샷에서 시작하는 최적화([[DESIGN-009-event-store-lifecycle]] 소관). (event_store와 outbox가 동일 datasource라는 전제·트리아지 C-1/C06은 [[ADR-027-event-store-outbox-atomicity]]로 확정.)
- 관련: [[RFC-024-domain-event-type-and-replay-layering]] · [[DESIGN-019-event-execution-layering]] · [[DESIGN-002-module-structure]] · [[DESIGN-009-event-store-lifecycle]] · [[RFC-023-event-schema-contract-management]]
