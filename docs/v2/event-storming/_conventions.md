# 이벤트 스토밍 카탈로그 — 작성 규약 · 대조 범위

- **상태**: 초안
- **성격**: 유지보수용. 카탈로그 내용을 읽으려면 [[00-index]]부터 본다.
- **범위**: `docs/v2/event-storming/` 전체가 지키는 표기 규칙과, 카탈로그가 실제로 대조한 소스 목록.

---

## 1. 상태 표기 — 두 축을 섞지 않는다

`docs/v2/event-storming/` 전체의 **자기 상태는 `초안`** 하나다.

인용한 소스의 상태는 **그 문서 원문 그대로** 옮긴다. 카탈로그가 무언가를 확정·합의된 것처럼 다시 쓰지 않는다.

"이 파일이 초안이다"와 "이 파일이 인용한 ADR-002가 Proposed다"는 다른 축이다.

### 1.1 인용 소스의 원문 상태

| 문서 | 원문 상태 |
|---|---|
| [[ADR-002-selective-event-sourcing-scope]] | `Proposed` |
| [[ADR-008-saga-orchestration-vs-choreography]] | `Proposed` |
| [[ADR-010-event-schema-evolution]] | `Proposed` |
| [[ADR-021-integrated-event-contract-module]] | `Proposed` |
| [[ADR-022-event-identity]] | `Proposed` |
| [[RFC-024-domain-event-type-and-replay-layering]] | `🏷 합의 (2026-07-04) — ADR 비준 대기` |
| [[RFC-029-event-carried-payload-uniform]] | `🏷 합의 (2026-07-05) — ADR 비준 대기` |
| [[RFC-031-architecture-fitness-functions-archunit]] | `🌱 초안` |

인용할 때 이 표의 상태를 함께 적는다. 요약·재해석하지 않는다.

01~06이 DESIGN-007·DESIGN-009·RFC-025·RFC-030 등 추가 소스를 인용할 때도 같다 — 원문 상태를 그 자리에 직접 적는다.

### 1.2 V1 모듈 대조 범위

`settings.gradle.kts` 실측 — V1 7개, V2 신설 7개. 카탈로그가 각 V1 모듈에서 확인한 것:

| 모듈 | 확인한 것 | 어디서 |
|---|---|---|
| `core-module` | 애그리거트 본체(`Reservation.kt`·`TimeTable.kt`·`TimetableOccupancy.kt`), Policy·Validate | 01·02·03 전역 |
| `shared-module` | `enumeration/`의 `ReservationStatus.kt`·`TableStatus.kt` | 01 §1, 02 §1 |
| `application-module` | UseCase 36개·Command 15개 전수. V1 커맨드 시그니처(`CreateReservationCommand` 11필드 등). `ConfirmReservation`·`FailReservation`·`ExpireReservation`은 V1에 승계 대상 없음(0건 매치) | 03 §3 |
| `adapter-module` | `event/schedule/ScheduleEventListener.kt` | 02 §2.1, 05 §1.1 |
| `infrastructure-module` | `event/abstractEvent/AbstractEvent.kt` — V1 자체 봉투, V2 계약 모듈과 동명이물 | §2.4 |
| `batch-module` | `TimeTableItemProcessor.kt`·`TimeTableBatchController.kt` — 슬롯 생성 배치 | 02 §2.1, 05 §1.1 |
| `test-module` | 픽스처 2건. 도메인 이벤트 근거 없음 | — |

V2 신설 모듈과의 관계:

| V2 모듈 | 관계 |
|---|---|
| `contract-module` | Phase 7-1의 직접 소비자 — 06 §6 이관 후보가 이 모듈의 입력 |
| `command-core` | 애그리거트 구현체 위치. 02·03의 불변식이 옮겨질 대상 (배치 자체는 카탈로그가 결정하지 않음) |
| `command-application` | core→contract 매핑·발행 (RFC-024) — 06 §2.2 |
| `command-infrastructure` | 타입-불가지 `StoredEvent` 영속화 (RFC-024) — 06 §2.2 |
| `command-adapter` · `query-module` | 범위 밖 |
| `auth-server-module` | authenticate의 V2 배치처일 수 있으나 모듈 배치는 카탈로그 범위 밖 |

---

## 2. 표기 규약 — 여기만 인용, 재정의 금지

### 2.1 태그 3어휘 (고정 문자열, 변형 금지)

- `V1 코드에서 확인` — **파일 경로 동반 필수.** 경로 없이 이 태그만 붙인 행은 무효다.
- `V2 도메인 문서 근거`
- `제안(근거 없음, 사용자 판단 필요)`

### 2.2 표 컬럼 스키마 (8열 고정)

```
카탈로그 명명 | 기존 명명(V1 원본/출처) | 트리거 종류 | 애그리거트 | 페이로드 필드 | 근거 | 원문 상태 | 태그
```

- "기존 명명"은 원문 그대로 보존한다. `CreateScheduleEvent`를 `ScheduleCreationRequested` 등으로 고치지 않는다.
- "재검토 필요 여부" 열은 없다. 재검토 후보는 [[07-hotspots-and-open-questions]]로 분리한다.

### 2.3 금지 인프라 토큰

```
Kafka | 토픽 | 파티션 | event_store | Outbox | 아웃박스 | 테이블 분리 | 스냅샷 | Debezium | CDC
```

카탈로그 본문에서 쓰지 않는다. 소스를 원문 그대로 인용하는 각주만 예외다.

근거: `docs/v2/analysis/09-event-delivery-and-offsets.md` — "Kafka 미채택은 여기서 정하는 게 아니라 RFC/ADR로 확정할 사안". 도메인 이벤트 명명·트리거·불변식은 전송·저장 계층 결정에 선행하므로, 인프라 어휘가 새어들지 않게 막는다.

### 2.4 이벤트 봉투 — 여기서 한 번만 열거

출처: `docs/v2/modules/02-contract-module.md` §5.1(본문 명시 "개념 — 실제 시그니처는 구현 사이클 확정") + [[ADR-022-event-identity]] + DESIGN-003 §4.4. 태그: `V2 도메인 문서 근거`.

```
eventId: UUID          // UUIDv7 — 전역 dedup/causation 앵커 (ADR-022)
aggregateType: String
aggregateId: String
sequenceNo: Long
eventType: String      // 타입 태그(FQCN 아님) — 업캐스팅 대상. 배선은 ADR-010(Proposed)
eventVersion: Int
occurredAt: Instant
correlationId: String  // 추적 봉투 — 발행 경로에서 충전
causationId: String?
traceparent: String?
```

01~07은 "페이로드 필드" 열에 이 10개를 다시 나열하지 않는다. 각 이벤트 고유의 도메인 페이로드만 적고, 봉투가 필요하면 이 절을 참조한다.

**동명이물 주의**: V1 `infrastructure-module`에도 같은 이름의 `AbstractEvent`가 있다(`infrastructure-module/src/main/kotlin/com/reservation/event/abstractEvent/AbstractEvent.kt`, sealed interface, `eventType: OutboxEventType`, `eventVersion: Double`, `key(): String`). 모양이 전혀 다르다. 카탈로그가 이 절의 봉투를 인용할 때는 `AbstractEvent(contract, 제안)`로 V2 쪽임을 명시한다.

### 2.5 규약 확장 로그

"재정의 금지"는 하위 파일이 §2를 **몰래** 바꾸는 것을 막는 규칙이다. 스코프상 불가피한 확장은 아래에 등록하면 승인된 것으로 본다. 미등록 변형은 금지.

| 파일 | 대상 | 확장 내용 | 사유 |
|---|---|---|---|
| [[02-design-timetable]] · [[03-design-reservation]] | §2.2 8열 | `발생 커맨드` 열 추가 → 9열 | Phase 7-1이 클래스로 옮길 커맨드 시그니처까지 닫아야 한다. 태그 3어휘·금지 토큰·`원문 상태` 열 요건은 유지 |
| [[04-policies-and-choreography]] | §2.1 태그 | 4번째 태그 `V2 설계/ADR 근거(도메인 문서 없음)` 추가 | 이 파일은 ADR-008·DESIGN-007·ADR-015를 1차 근거로 삼고 `docs/v2/domain/*.md`를 쓰지 않는다(payment 도메인 문서 자체가 없음). 3어휘 중 `V2 도메인 문서 근거`를 쓰면 없는 문서를 있는 것처럼 인용하게 된다 |
| [[00-index]] §0 · 본 문서 §2.5 · [[07-hotspots-and-open-questions]] | `원문 상태` 열 | 열 대신 식별자 뒤에 `(Proposed)` 병기 | 한 셀이 ADR-002·008·021을 함께 인용한다. 열 하나에 상태 3개를 담을 수 없다. 행마다 ADR 1건인 표(예: [[01-big-picture]] §7)는 독립 열을 쓴다 |

---

## 3. 파일명 이력

처음 계획은 01~06을 컨텍스트별 파일(`01-reservation.md` 등), 07을 `07-retrospective-candidates.md`로 두는 것이었다. 실제로는 01이 6개 컨텍스트를 묶은 빅픽처가 되고, 02~06은 관점별(설계 심화·정책·fold·분류)로 나뉘었다. 본문의 "→ 07" 표기는 전부 [[07-hotspots-and-open-questions]]를 가리킨다.
