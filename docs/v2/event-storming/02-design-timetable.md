# timetable 컨텍스트 — 설계 레벨 (Slot 애그리거트)

- **상태**: 초안
- **작성일**: 2026-07-29
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **스코프**: [[00-index]] §1 "레퍼런스 심화" — 페이로드 필드까지 닫는다. Phase 7-1(contract-module)이 이 파일을 직접 소비한다.
- **규약 출처**: 태그 3어휘·8열 스키마 표기는 이 파일에서 [[_conventions]] §2와 다르게 적용한다 — 아래 §0 참조(이 문서 자신의 상태는 위 "초안" 하나뿐, §0의 열 배치 조정은 그와 별개 축). 봉투 필드는 반복하지 않는다([[_conventions]] §2.4, "봉투: [[_conventions]] §2.4 참조").
- **근거 문서**: [[domain/02-timetable]](상태머신 7개 전이) · [[ADR-016-aggregate-concurrency-pessimistic-lock]](`Proposed`) · [[ADR-022-event-identity]](`Proposed`) · [[ADR-008-saga-orchestration-vs-choreography]](`Proposed`) · [[RFC-029-event-carried-payload-uniform]](`🏷 합의 (2026-07-05) — ADR 비준 대기`)

---

## 0. 표 스키마 — 이 파일의 8열 ([[_conventions]] §2.2 대비 변경점)

이 설계 레벨 문서는 커맨드 시그니처까지 닫아야 하므로(Phase 7-1이 클래스로 옮길 수준), [[_conventions]] §2.2가 정의한 8열에 `발생 커맨드` 열을 새로 열어 9열로 확장한다 — 이 확장은 [[_conventions]] §2.5 "규약 확장 로그"에 등록돼 있다(재정의가 아니라 등록된 예외). `원문 상태`는 [[_conventions]] §2.2가 정의한 그대로 독립 열로 유지한다 — ADR/RFC를 인용하는 행은 이 열에 원문 그대로의 상태 문자열(`Proposed`/`Accepted`/`🏷 합의` 등)을 적고, 인용 대상이 도메인 문서·V1 코드뿐이라 ADR/RFC 상태 체계가 적용되지 않는 행은 `—`로 비운다(임의 재해석 금지). 열 순서는 다음과 같이 고정한다.

```
카탈로그 명명 | 기존 명명 / V1 원본 | 트리거 종류 | 발생 커맨드 | 애그리거트 | 페이로드 필드 | 근거(출처) | 원문 상태 | 태그
```

- **`트리거 종류`**: `{커맨드, 외부 이벤트, 시간 경과·스케줄러, 배치}` 중 하나만 쓴다.
- **`발생 커맨드`**: 애그리거트가 실제로 `handle`하는 내부 커맨드명. 없는 전이(예: 배치 생성)는 `—`로 비운다 — 없는 커맨드를 발명하지 않는다.
- **태그 3어휘** ([[_conventions]] §2.1과 동일, 고정 문자열, 변형 금지): `V1 코드에서 확인`(파일 경로 동반 필수) · `V2 도메인 문서 근거` · `제안(근거 없음, 사용자 판단 필요)`.
- 금지 인프라 토큰(목록은 [[_conventions]] §2.3)은 본문에 쓰지 않는다. V1이 이미 그 패턴을 쓴 사실을 인용해야 할 때만 각주에서 원문 그대로 인용한다.

---

## 1. 애그리거트 — Slot (재설계, 슬롯 단위)

[[domain/02-timetable]] §2 "애그리거트 재설계": V1의 `TimeTable`은 행 단위(날짜×시간×테이블 1행 = 1슬롯)였고 행위는 `attachOccupied`/`detachOccupied` 둘뿐이었다(`core-module/src/main/kotlin/com/reservation/timetable/TimeTable.kt`). V2는 이 슬롯 granularity를 애그리거트 경계로 승격해 핫 애그리거트 경합을 슬롯 단위로 격리한다([[DESIGN-006-aggregate-design]] §6.3, domain/02 인용).

**상태**: `AVAILABLE | HELD | CONFIRMED | BLOCKED` (V1은 `TableStatus{EMPTY, OCCUPIED}` 2개뿐 — `shared-module/src/main/kotlin/com/reservation/enumeration/TableStatus.kt`(주의: `core-module`이 아니라 `shared-module` 소속), domain/02 §1 "네이밍 트랩" 참고: `isOccupied()`가 `EMPTY`일 때 `true`를 반환하는 반전 이름).

**V1 대응 필드** (근거: `core-module/src/main/kotlin/com/reservation/timetable/TimeTable.kt`·`core-module/src/main/kotlin/com/reservation/timetable/TimetableOccupancy.kt`, 태그 `V1 코드에서 확인`):
```
restaurantId: String
date: LocalDate / day: DayOfWeek
startTime / endTime: LocalTime
tableNumber: Int / tableSize: Int
```
V1 점유 부속 엔티티 `TimetableOccupancy(timeTableId, userId, occupiedStatus, occupiedDatetime, unoccupiedDatetime)`(`core-module/src/main/kotlin/com/reservation/timetable/TimetableOccupancy.kt`) — V2에서는 별도 엔티티가 아니라 Slot 애그리거트 자신의 상태로 흡수된다(domain/02 §3).

---

## 2. 상태 전이 — 7개, 빠짐없이 (domain/02 §2 상태머신 대응)

domain/02의 mermaid 화살표 7개를 그대로 센다. 5번째 화살표(`HELD --> AVAILABLE: ReleaseSeat / ExpireSeat (TTL)`)는 트리거 종류가 서로 달라(외부 이벤트 vs 시간 경과·스케줄러) 카탈로그 표에서 2행으로 분리한다 — 전이 자체는 domain/02의 7개 중 1개이므로 "빠짐없이 덮음"의 산술은 화살표 기준 7, 카탈로그 행 기준 8이다(분리 사유는 §4에서 재론).

| # (domain/02 화살표 순번) | 전이 | 트리거 종류 | 원문 상태 | 태그 |
|---|---|---|---|---|
| 1 | `[*] → AVAILABLE`: 슬롯 생성 | 배치 (§3.1 근거) | `—`(V1 코드, ADR/RFC 상태 체계 해당 없음) | `V1 코드에서 확인` — `batch-module/src/main/kotlin/com/reservation/batch/timetable/job/TimeTableJobConfig.kt` · `batch-module/src/main/kotlin/com/reservation/batch/timetable/step/processor/TimeTableItemProcessor.kt`(조사 체인 §2.1) |
| 2 | `AVAILABLE → HELD`: HoldSeat | 외부 이벤트 (reservation: `ReservationCreated`) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/02 §2) |
| 3 | `AVAILABLE → BLOCKED`: BlockSlot | 커맨드 (매장 점주) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/02 §2) |
| 4 | `HELD → CONFIRMED`: ConfirmSeat | 외부 이벤트 (reservation: `ReservationConfirmed`) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/02 §2) |
| 5a | `HELD → AVAILABLE`: ReleaseSeat | 외부 이벤트 (reservation: `ReservationFailed`/`ReservationCancelled`/`ReservationNoShow`) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/02 §2) |
| 5b | `HELD → AVAILABLE`: ExpireSeat (TTL) | 시간 경과·스케줄러 ([[ADR-008-saga-orchestration-vs-choreography]]: "스케줄러가 주기적으로 깨어 TTL 지난 `SeatHeld`를 찾아 `SeatReleased` 보상을 발행") | `Proposed`([[ADR-008-saga-orchestration-vs-choreography]]) | `V2 도메인 문서 근거`(ADR-008) |
| 6 | `CONFIRMED → AVAILABLE`: ReleaseSeat (취소/노쇼) | 외부 이벤트 (reservation: `ReservationCancelled`/`ReservationNoShow`) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/02 §2) |
| 7 | `BLOCKED → AVAILABLE`: UnblockSlot | 커맨드 (매장 점주) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/02 §2) |

### 2.1 슬롯 생성([*]→AVAILABLE) 트리거 종류 판정 — 조사 근거

domain/02는 이 화살표에 트리거명이 없다("슬롯 생성 (schedule에서)"만 표기, [[00-index]] §3 "상태 전이 트리거 미명명" 3건 중 하나). 카탈로그 관점에서 실제 V1 경로를 추적한 결과:

1. `core-module/src/main/kotlin/com/reservation/restaurant/event/CreateScheduleEvent.kt` — `restaurantId`만 든 Spring 애플리케이션 이벤트. 매장 등록 시점에 발행(발행 지점은 restaurant 컨텍스트 소관, 이 문서 스코프 밖).
2. `adapter-module/src/main/kotlin/com/reservation/event/schedule/ScheduleEventListener.kt` — `@EventListener`로 `CreateScheduleEvent`를 받아 `CreateScheduleCommand(restaurantId)`를 만들어 `CreateScheduleUseCase`를 실행한다. 이 체인은 **schedule 컨텍스트의 주간 스케줄(영업시간·테이블 구성)을 만드는 것**이지, `TimeTable`(슬롯) 행을 만드는 것이 아니다.
3. `batch-module/src/main/kotlin/com/reservation/batch/timetable/job/TimeTableJobConfig.kt` + `TimeTableItemProcessor.kt` + `TimeTableCompositeItemReader.kt` — Spring Batch Job. Reader가 `schedule` 컨텍스트의 `ScheduleEntity`·`HolidayEntity`·`TableEntity`·`TimeSpanEntity`를 조회하고, Processor가 대상 월(`YearMonth`)의 날짜×시간×테이블 조합을 전개해 `TimeTableEntity(tableStatus = EMPTY)` 목록을 만든다. **실제 슬롯 행을 만드는 지점은 이 배치뿐이다.**
4. `batch-module/src/main/kotlin/com/reservation/rest/TimeTableBatchController.kt` — `POST /api/v1/batch/time-table/fire`가 `JobLauncher.run(timeTableJob, ...)`으로 이 배치를 기동한다. 저장소 전체(`batch-module/src`, `adapter-module/src`)에 `@Scheduled`/`CronTrigger` 사용처가 없다 — 이 배치는 코드상 자동 스케줄러가 아니라 **외부에서 호출되는 배치 실행**이다.

**판정**: 슬롯 생성은 (2)의 이벤트 체인이 만든 것이 아니라 (3)+(4)의 배치가 만든다. 트리거 종류 = **배치**. 내부 커맨드가 없으므로 `발생 커맨드` = `—`. (2)의 `CreateScheduleEvent`/`ScheduleEventListener`는 이 배치의 **선행 조건**(주간 스케줄 존재)을 만들 뿐 슬롯 생성 자체의 트리거가 아니다 — 혼동하지 않도록 두 체인을 표 각주에서 분리해 인용한다.

---

## 3. 이벤트 카탈로그 (8열)

> 봉투(정체성·추적 메타 10개 필드)는 [[_conventions]] §2.4가 한 번만 정의한다 — 이 문서는 그 정의를 다시 나열하지 않고 참조만 한다. 아래 페이로드 필드는 각 이벤트 고유의 도메인 데이터만 담는다.
> 페이로드 정책 근거: [[RFC-029-event-carried-payload-uniform]](`🏷 합의 (2026-07-05) — ADR 비준 대기`) — "이벤트는 그 시점 사실(값 또는 불변 참조)을 담는다. 소비 측은 가변 최신 상태를 조회해 재생 이벤트를 채우지 않는다."

| 카탈로그 명명 | 기존 명명 / V1 원본 | 트리거 종류 | 발생 커맨드 | 애그리거트 | 페이로드 필드 | 근거(출처) | 원문 상태 | 태그 |
|---|---|---|---|---|---|---|---|---|
| `SlotProvisioned` | domain/02·V1 모두 미명명(§2.1 조사 결과) | 배치 | — | Slot | `slotId, restaurantId, date, day, startTime, endTime, tableNumber, tableSize` | `TimeTableItemProcessor.kt`(필드 출처)·§2.1 조사 체인 | `—`(V1 코드, ADR/RFC 아님) | `제안(근거 없음, 사용자 판단 필요)` — 사건 자체가 어느 문서에도 이름이 없어 이름·존재 여부 모두 사용자 판단 필요 |
| `SeatHeld` | `HoldSeat` ← `ReservationCreated`(domain/02 §2) | 외부 이벤트 (경계: reservation) | `HoldSeat` | Slot | `slotId, reservationId, userId, restaurantId, date, startTime, endTime, tableNumber, heldAt, holdExpiresAt` | userId·occupiedDatetime 대응: `core-module/src/main/kotlin/com/reservation/timetable/TimetableOccupancy.kt`(V1). holdExpiresAt(TTL)은 V1에 없음 — domain/02 §2 불변식#2 | `—`(V1 코드+도메인 문서, ADR/RFC 아님) | `V1 코드에서 확인`(`core-module/src/main/kotlin/com/reservation/timetable/TimetableOccupancy.kt`) + `V2 도메인 문서 근거`(TTL 필드, domain/02) 혼재 — 혼재 사유를 셀에 병기 |
| `SeatConfirmed` | `ConfirmSeat` ← `ReservationConfirmed`(domain/02 §2) | 외부 이벤트 (경계: reservation) | `ConfirmSeat` | Slot | `slotId, reservationId, confirmedAt` | domain/02 §2 액터→커맨드→이벤트 표 | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `SeatReleased` (5a) | `ReleaseSeat` ← `ReservationFailed`/`ReservationCancelled`/`ReservationNoShow`(domain/02 §2) | 외부 이벤트 (경계: reservation) | `ReleaseSeat` | Slot | `slotId, reservationId, releasedAt` — `cause` 필드 부재는 §4 참조(07-hotspots 후보, 본 카탈로그가 추가 여부를 정하지 않음) | domain/02 §2·§ "미결" 원문 | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `SeatReleased` (5b, TTL) | `ExpireSeat`(TTL 만료, domain/02 §2) | 시간 경과·스케줄러 | `ExpireSeat` | Slot | `slotId, releasedAt` — `reservationId`는 TTL 경로에선 Slot이 보유한 현재 hold의 참조를 그대로 실음(불변 참조, RFC-029 §논점2). `cause` 필드 부재는 §4 참조 | domain/02 §2·불변식#2 · [[ADR-008-saga-orchestration-vs-choreography]](스케줄러 폴링→`SeatReleased` 보상 발행 서술) | `Proposed`([[ADR-008-saga-orchestration-vs-choreography]]) | `V2 도메인 문서 근거` |
| `SlotBlocked` | `BlockSlot`(domain/02 §2, 매장 점주) | 커맨드 | `BlockSlot` | Slot | `slotId, blockedBy, blockedAt` — `reason` 필드는 도메인 문서·V1 모두 근거 없음(제안 시 별도 표기 필요) | domain/02 §2 — V1 대응 없음(순수 신설, 수동 차단 자체가 V1엔 없음) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `SlotUnblocked` | `UnblockSlot`(domain/02 §2, 매장 점주) | 커맨드 | `UnblockSlot` | Slot | `slotId, unblockedBy, unblockedAt` | domain/02 §2 — V1 대응 없음 | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |

---

## 4. `ReleaseSeat` vs `ExpireSeat` — 원인 미구분을 카탈로그 관점으로 재기술 (07-hotspots 후보)

domain/02 §2는 이 지점을 "미결"로 남겨 뒀다: "`ReleaseSeat`(보상: Failed/Cancelled/NoShow가 원인)와 `ExpireSeat`(TTL 만료가 원인) 모두 동일한 `SeatReleased` 이벤트를 발행한다 ... 두 원인이 구분되지 않는다."

**이 문서는 그 미결을 그대로 승계하지 않는다.** 카탈로그 관점에서 재기술하면:

- **트리거 종류 축에서는 이미 구분된다.** §2의 5a(외부 이벤트)·5b(시간 경과·스케줄러)는 이 카탈로그에서 서로 다른 행이다 — "구분 안 됨"은 더 이상 사실이 아니다.
- **남는 미결은 페이로드 축이다.** 두 행 모두 카탈로그 명명이 `SeatReleased`로 동일하고, 어느 쪽도 `cause`(원인) 필드를 페이로드에 갖지 않는다(§3 표). RFC-029(`🏷 합의`)의 event-carried 원칙을 엄격히 적용하면 "그 시점 사실"에는 원인도 포함될 수 있어 보이지만, domain/02·V1 어느 쪽도 `cause` 필드를 근거로 갖지 않으므로 이 문서가 임의로 필드를 추가하지 않는다(없는 필드를 발명하지 않는다).
- **결정됨 (사용자, 2026-07-30): 분리한다.** 5a(외부 이벤트 유래)는 `SeatReleased`, 5b(TTL 유래)는 `SeatExpired`로 카탈로그 이벤트를 나눈다. 검토 대상이던 대안 — 하나의 이름을 유지하고 `cause` 필드로 원인을 구분하는 안 — 은 채택하지 않았다. 이름이 원인을 나르므로 `cause` 필드는 필요하지 않다.
- **아직 반영되지 않았다.** 이 결정은 `SeatReleased`가 등장하는 카탈로그 40개 지점(7개 파일)에 전파돼야 한다. [[00-index]] §0 결정(B)에 따라 다른 이름들도 재검토 대상이므로, 전파는 재검토 안건이 모두 닫힌 뒤 한 번에 수행한다 — 건별로 전파하면 같은 표를 반복해 고치게 된다. 그때까지 §2·§3 표의 `SeatReleased`는 5a·5b를 아직 함께 가리킨다.

---

## 5. 컨텍스트 불변식

| # | 불변식 | 태그 | 원문 상태 | 출처 |
|---|---|---|---|---|
| 1 | `AVAILABLE` 상태에서만 `HoldSeat` 점유 가능(이중 점유 방지) | `V1 코드에서 확인` | `—`(V1 코드+도메인 문서, ADR/RFC 아님) | V1 선례: `core-module/src/main/kotlin/com/reservation/timetable/policy/validation/TimeTableStatusIsNotVacantPolicy.kt` — `tableStatus.isOccupied()` 가드. V2 상태 가드는 domain/02 §2 불변식#1 |
| 2 | 단일 슬롯(`aggregate_id`)에 대한 동시 `HoldSeat` 경합은 락으로 직렬화하고, 정확성 최종 심판은 `(aggregate_id, sequence_no)` UNIQUE — 락이 안전성을 대체하지 않는다 | `V2 도메인 문서 근거` | `Proposed`([[ADR-016-aggregate-concurrency-pessimistic-lock]]) | [[ADR-016-aggregate-concurrency-pessimistic-lock]] L0/L1 층 구조 |
| 3 | `HELD`는 TTL을 가진다 — TTL 내 결제 미도착 시 `ExpireSeat`가 자동 만료시킨다(스케줄러 폴링, 즉시 삭제 아닌 append) | `V2 도메인 문서 근거` | `Proposed`([[ADR-008-saga-orchestration-vs-choreography]]) | domain/02 §2 불변식#2 · [[ADR-008-saga-orchestration-vs-choreography]]("타임아웃 = timetable TTL 자치") |
| 4 | 해제/만료된 슬롯(`AVAILABLE`)에 뒤늦은 `ConfirmSeat`는 거부한다 | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/02 §2 불변식#3 |
| 5 | `BLOCKED` 슬롯은 `HoldSeat` 대상이 될 수 없다 | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/02 §2 불변식#4 |
| 6 | 이벤트 정체성은 슬롯별 append 순서(`sequence_no`)로 보장되고 전역 순번에 의존하지 않는다 — 재구축 열거는 `event_id`(UUIDv7) keyset로 별도 처리 | `V2 도메인 문서 근거` | `Proposed`([[ADR-022-event-identity]]) | [[ADR-022-event-identity]] |

---

## 6. V1→V2 요약 (재확인, domain/02 §3과 동일 — 재정의 아님)

domain/02 §3 "V1→V2 변경 요약" 표를 그대로 참조한다. 이 파일은 그 표를 다시 쓰지 않고, §1~§5의 설계 레벨 상세만 얹는다.
