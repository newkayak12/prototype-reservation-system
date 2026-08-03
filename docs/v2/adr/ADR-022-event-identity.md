# ADR-022: 이벤트 정체성은 `event_id`(UUIDv7) 단일 키로 두고 전역 순번은 두지 않는다

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-021-event-identity-and-global-ordering]] · **설계**: [[DESIGN-009-event-store-lifecycle]]
- **연관 ADR**: [[ADR-002-selective-event-sourcing-scope]]

---

## 맥락과 문제 (Context and Problem Statement)

V2 이벤트 스토어 스키마는 `(aggregate_type, aggregate_id, sequence_no, event_type, event_version, payload, occurred_at)`로 시작했다. 여기엔 이벤트 하나를 가리키는 안정된 식별자가 없다.

ES 컨텍스트는 `(aggregate_id, sequence_no)`로 중복을 가려낼 수 있다. 문제는 그 키가 없는 이벤트다. `schedule`·`user`·`authenticate` 같은 비-ES와 `menu`·`category`·`company` 같은 lookup은 상태 테이블 + Outbox라 스트림도 시퀀스도 없다. 이들이 발행한 이벤트는 정체성이 없어, 컨슈머가 중복 전달을 가려낼 키도, `causationId`가 가리킬 대상도 만들 수 없다.

두 번째 긴장은 재구축이다. `sequence_no`는 애그리거트별이라, 프로젝션 재구축이 스토어 전체를 빠짐없이·재개 가능하게 훑을 기준이 없다.

이 둘은 한 덩어리로 보였지만 결정할 건 둘이었다. 정체성을 무엇으로 두는가, 그리고 재구축 열거를 위해 전역 순번을 따로 두어야 하는가.

**전 컨텍스트 공통의 이벤트 정체성을 무엇으로 두고, 재구축 열거를 어떻게 지원하는가?**

## 결정 동인 (Decision Drivers)

- ES·비-ES·lookup을 가리지 않는 **공통 dedup 키와 `causation` 앵커**가 필요하다.
- 재구축에 필요한 건 교차-애그리거트 전순서가 아니라, 빠짐없이·재개 가능하게 훑는 **열거**다.
- 프로젝터 정확성은 이미 per-aggregate 순서 + 멱등 + 버전 가드로 선다 — 전역 순서·전역 락은 사지 않는다.
- 컬럼과 메커니즘을 늘리기 전에, 하나로 두 역할을 겸할 수 있는지 먼저 따진다.

## 검토한 선택지 (Considered Options)

- **A. `event_id`(UUIDv7) 단일 키** — 정체성과 재구축 열거 커서를 하나로 겸한다.
- **B. `event_id` + 전용 `global_seq`** — 정체성은 `event_id`, 열거 커서는 `BIGINT AUTO_INCREMENT`로 분리한다.
- **C. `occurred_at` + tiebreak 승격** — 별도 식별자 없이 시각 컬럼을 순서 기준으로 쓴다.

## 결정 (Decision Outcome)

**채택: A — `event_id`(UUIDv7) 단일 키.**

UUIDv7은 시간정렬 단조라 그 자체가 재구축 열거 커서를 겸한다. 그래서 전용 `global_seq` 컬럼(B)은 잉여가 되고, 시계에 의존하며 교차 tiebreak이 무의미한 C는 애초에 정체성이 되지 못한다.

결정의 구조는 다음과 같다.

- **`event_id`는 전 컨텍스트 공통 1급 정체성이다.** ES·비-ES·lookup을 가리지 않으며, 비-ES Outbox 이벤트도 보유한다.
- **채번 시점은 append/Outbox 기록 트랜잭션 안이다.** 값은 전역 유일하다.
- **하나의 키가 세 가지로 쓰인다** — inbox/dedup 키, `causationId`/`correlationId` 앵커, Kafka 봉투의 `messageId`.
- **재구축 열거 커서를 겸한다** — `WHERE event_id > :last ORDER BY event_id` keyset 스캔으로 스토어를 훑고 중단점에서 재개한다.
- **파티션 키는 `aggregate_id` 불변이다.** `event_id`는 정체성이지 라우팅 키가 아니다.

**`event_id`는 순서 정확성을 나르지 않는다.**

`event_id`가 나르는 것은 진행/열거이지 교차-애그리거트 적용 순서가 아니다. 프로젝터 정확성은 per-aggregate 순서(파티션 키) + 멱등 upsert + per-aggregate 버전 가드(`sequence_no`)로 선다. 교차-애그리거트 전순서는 정확성 요구가 아니다. 여러 애그리거트의 상대 시점에 의존하는 파생 사실은 생산 시점에 이벤트 페이로드로 박아 넣고, 진짜 교차 불변식 강제는 사가가 푼다.

확정 스키마는 [[DESIGN-009-event-store-lifecycle]]에 있다. `event_store`의 PK는 `event_id`(`BINARY(16)`, UUIDv7)이고, `(aggregate_id, sequence_no)`에 UNIQUE 제약을 둔다. `global_seq` 컬럼은 없다.

### 결과 (Consequences)

**좋은 점**

- 비-ES·lookup 이벤트까지 단일 dedup 키를 얻어, 멱등이 컨텍스트 종류에 묶이지 않는다.
- `causationId`·추적 메타가 실제로 가리킬 앵커가 생겨 인과 사슬이 성립한다.
- 재구축이 결정적·재개 가능한 열거를 얻으면서도, 전용 순번 컬럼도 전역 락도 사지 않는다.
- UUIDv7이라 삽입 지역성이 양호하고, 재구축 keyset이 PK 스캔과 일치한다.

**트레이드오프**

- UUIDv7의 시간정렬은 근사 단조다. 같은 밀리초 내 삽입 순서는 엄밀하지 않다. 이는 열거 커서로는 충분하지만 순서 정확성 보증이 아니라는 점을 전제로 감수한다.
- 정체성과 커서를 한 컬럼이 겸하므로 책임이 결합한다. 재구축 완전성은 커서 단독이 아니라 (백필 `≤ HWM`) ∪ (라이브 tail) + 멱등 가드로 봉합한다("구독 먼저").
- **재검토 트리거**: 이벤트 스토어를 수평 샤딩하면 store-global 단조가 깨진다. 현재 토폴로지가 샤딩을 명시 기각했으므로 범위 밖이나, 샤딩이 도입되면 이 결정을 다시 연다.

### 확인 (Confirmation)

- `event_store` PK가 `event_id`이고 `global_seq` 컬럼이 없는지 마이그레이션 스키마로 확인한다.
- 모든 도메인 이벤트가 발행 경로에서 `event_id`를 채우는지 아키텍처 테스트로 검증한다.
- 재구축 열거가 `event_id` keyset로만 페이지네이션·재개하는지 코드 리뷰 체크로 확인한다.

## 선택지 상세 (Pros and Cons of the Options)

### B. `event_id` + 전용 `global_seq`

- 장점: 삽입 커서가 엄밀 단조라 열거 순서가 명확하다.
- 단점: UUIDv7을 정체성으로 확정하면 그 시간정렬 단조가 열거 커서를 이미 겸한다. 별도 `BIGINT AUTO_INCREMENT`는 잉여다.
- 기각 사유: RFC-021 닫힘으로 불채택. keyset 커서가 UUIDv7 PK 스캔으로 대체되어 전용 컬럼이 불필요하다.

### C. `occurred_at` + tiebreak 승격

- 장점: 새 식별자 컬럼이 없다.
- 단점: 시계에 의존하고, 교차 애그리거트 tiebreak이 무의미하다. 정체성 역할도 못 한다.
- 기각 사유: 안정된 전역 유일 식별자가 되지 못해 dedup·`causation` 앵커로 쓸 수 없다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: Outbox 경로의 `event_id` 전파 배선(기록 채번 → 봉투 → inbox), 비-ES inbox 적용 범위 판정, 재구축 페이지네이션·재개 체크포인트 구현.
- 관련: [[RFC-021-event-identity-and-global-ordering]] · [[DESIGN-009-event-store-lifecycle]] · [[DESIGN-003-write-model]] · [[ADR-002-selective-event-sourcing-scope]] · [[RFC-011-projection-rebuild-catchup]]
