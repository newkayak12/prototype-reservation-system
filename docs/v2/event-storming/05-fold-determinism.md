# fold 복원 검증 — ES 3개 컨텍스트의 상태 전이 vs 카탈로그 이벤트 대조

- **상태**: 초안
- **작성일**: 2026-07-29
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **역할 고지**: 이 문서는 결론을 미리 정하지 않고 domain/01·domain/02·V1 코드를 대조한 조사 결과를 그대로 적는다. "복원 가능"/"갭 있음" 판정은 조사 결과가 결정하며, 사전에 정해 둔 결론을 확인하는 절차가 아니다.
- **규약**: [[_conventions]] §2(태그 3어휘)를 따른다. `확정`·`합의` 어휘는 판정값으로 쓰지 않는다 — 판정 3어휘는 §0.2에 고정한다.
- **근거 문서**: [[ADR-002-selective-event-sourcing-scope]](`Proposed`) · [[domain/02-timetable]] · [[domain/01-reservation]] · [[domain/03-restaurant]] · [[02-design-timetable]] · [[03-design-reservation]] · [[01-big-picture]]

---

## 0. 스코프

### 0.1 ADR-002 기준 ES 3개 컨텍스트

[[ADR-002-selective-event-sourcing-scope]]의 원문 상태는 **`Proposed`**다(예외 없이 26 ADR 전부 동일 — [[_conventions]] §1). 이 ADR이 "진짜 ES"(append-only 이벤트 스토어 + 리플레이로 상태 도출)로 분류한 컨텍스트는 `reservation`·`timetable`·`restaurant` 3개다. 이 문서는 이 3개만 다룬다(`schedule`·`user`·`authenticate`는 ADR-002가 "비-ES"로 분류했으므로 fold 복원 검증 대상이 아니다).

### 0.2 판정 어휘 (고정, 사전 지정 금지)

- **대응 이벤트 있음** — domain 문서의 전이에 카탈로그(01~03 파일)가 이미 닫은 이벤트가 있다.
- **대응 이벤트 없음** — 카탈로그에도 이벤트가 없다.
- **도메인 밖 구현(배치·스케줄러)** — 전이가 이벤트가 아니라 배치 작업 또는 스케줄러가 만든 부수효과다.

세 값 외의 표현(예: "확정", "합의")은 판정 셀에 쓰지 않는다.

---

## 1. timetable — domain/02 7개 전이 대조

domain/02 §2 상태머신 mermaid 화살표를 그대로 7개 센다(02-design-timetable.md §2의 8행 분리는 트리거 종류 축 재기술이며, 이 절은 domain/02 자신의 원 전이 수와 정확히 맞춘다).

| # | domain/02 전이 | 판정 | 대응 카탈로그 이벤트 | 근거 | 태그 |
|---|---|---|---|---|---|
| 1 | `[*] → AVAILABLE`: 슬롯 생성 (schedule에서) | **도메인 밖 구현(배치)** | 없음 — 아래 §1.1 참조 | [[02-design-timetable]] §2.1 조사 체인 + 이 문서가 직접 재확인(§1.1) | `V1 코드에서 확인` — `batch-module/src/main/kotlin/com/reservation/batch/timetable/job/TimeTableJobConfig.kt` · `batch-module/src/main/kotlin/com/reservation/batch/timetable/step/processor/TimeTableItemProcessor.kt` |
| 2 | `AVAILABLE → HELD`: HoldSeat | 대응 이벤트 있음 | `SeatHeld` | [[02-design-timetable]] §3 | `V2 도메인 문서 근거` |
| 3 | `AVAILABLE → BLOCKED`: BlockSlot | 대응 이벤트 있음 | `SlotBlocked` | [[02-design-timetable]] §3 | `V2 도메인 문서 근거` |
| 4 | `HELD → CONFIRMED`: ConfirmSeat | 대응 이벤트 있음 | `SeatConfirmed` | [[02-design-timetable]] §3 | `V2 도메인 문서 근거` |
| 5 | `HELD → AVAILABLE`: ReleaseSeat / ExpireSeat (TTL) | 대응 이벤트 있음 | `SeatReleased`(두 원인 모두 동일 이름 — [[02-design-timetable]] §4가 이 지점을 페이로드 축 미결로 남김) | [[02-design-timetable]] §3·§4 | `V2 도메인 문서 근거` |
| 6 | `CONFIRMED → AVAILABLE`: ReleaseSeat (취소/노쇼) | 대응 이벤트 있음 | `SeatReleased` | [[02-design-timetable]] §3 | `V2 도메인 문서 근거` |
| 7 | `BLOCKED → AVAILABLE`: UnblockSlot | 대응 이벤트 있음 | `SlotUnblocked` | [[02-design-timetable]] §3 | `V2 도메인 문서 근거` |

**소계**: 7개 전이 중 6개는 대응 이벤트가 있고(5·6은 이름이 같은 이벤트를 공유), 1개(슬롯 생성)는 이벤트가 아니라 배치다.

### 1.1 슬롯 생성 전이 — 직접 재확인 (이 문서가 코드를 다시 읽은 결과)

acceptance 스펙이 지정한 3개 경로를 이 문서가 직접 열어 확인했다(02-design-timetable.md §2.1의 조사를 재현·검증한 것이며 그 결론에 기대어 재인용만 한 것이 아니다):

- `core-module/src/main/kotlin/com/reservation/restaurant/event/CreateScheduleEvent.kt` — 필드가 `restaurantId: String` 하나뿐인 순수 데이터 클래스. Spring `@EventListener`가 소비할 애플리케이션 이벤트.
- `adapter-module/src/main/kotlin/com/reservation/event/schedule/ScheduleEventListener.kt` — `@EventListener fun handleCreateScheduleEvent(event: CreateScheduleEvent)`가 `CreateScheduleCommand(restaurantId)`를 만들어 `createScheduleUseCase.execute(command)`를 호출한다. 이 체인이 만드는 것은 **schedule 컨텍스트의 주간 스케줄**이지 `TimeTable`(슬롯) 행이 아니다 — `TimeTableEntity` 생성 코드는 이 리스너 안 어디에도 없다.
- `batch-module/src/main/kotlin/com/reservation/batch/timetable/step/processor/TimeTableItemProcessor.kt` — `ItemProcessor<ScheduleWithData, List<TimeTableEntity>>`. `process()`가 대상 월의 날짜×시간×테이블 조합을 전개해 `TimeTableEntity(restaurantId, date, day, startTime, endTime, tableNumber, tableSize, tableStatus = TableStatus.EMPTY)` 목록을 반환한다. **실제 슬롯 행을 만드는 지점은 여기다.**
- `batch-module/src/main/kotlin/com/reservation/rest/TimeTableBatchController.kt` — `@PostMapping(...FIRE) fun fireTimeTableBatch()`가 `jobLauncher.run(timeTableJob, jobParameter)`로 배치를 기동한다. **REST 엔드포인트로 외부에서 호출되는 구조** — 코드 안에서 자동으로 도는 스케줄러가 아니다.
- 저장소 전체(`grep -rl "@Scheduled"`, `grep -rl "CronTrigger"` — `batch-module`·`adapter-module` 포함 전체 리포에서) **0건**. 이 문서가 직접 재실행해 확인했다.

**판정 근거**: 슬롯 생성은 이벤트가 아니라 `TimeTableItemProcessor`가 만드는 배치 산출물이고, 그 배치는 스케줄러가 아니라 REST로 기동된다. 따라서 판정은 "도메인 밖 구현(배치)"이며 "스케줄러"는 아니다 — 이 판정값 안의 두 하위 개념(배치/스케줄러) 중 실제로 해당하는 것은 배치뿐임을 명시한다.

### 1.2 이 전이가 fold 복원에 갖는 함의 → §3.1로 이월

이 전이가 이벤트가 아니라는 사실은 timetable을 ES(리플레이로 상태 도출)로 분류한 ADR-002와 만나면 구조적 함의를 갖는다 — 이는 판정표(§1)의 범위를 넘으므로 §3.1에서 다룬다.

---

## 2. reservation — domain/01 9개 전이 대조

domain/01 §2 상태머신의 실전이 9개(터미널 `--> [*]` 5개 제외, [[03-design-reservation]] §2와 동일 산정 기준).

| # | domain/01 전이 | 판정 | 대응 카탈로그 이벤트 | 근거 | 태그 |
|---|---|---|---|---|---|
| 1 | `[*] → PENDING`: CreateReservation | 대응 이벤트 있음 | `ReservationCreated` | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| 2 | `PENDING → CONFIRMED`: PaymentConfirmed | 대응 이벤트 있음 | `ReservationConfirmed` | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| 3 | `PENDING → FAILED`: PaymentFailed | 대응 이벤트 있음 | `ReservationFailed` | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| 4 | `PENDING → EXPIRED`: SeatReleased (TTL 만료) | 대응 이벤트 있음 | `ReservationExpired` | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| 5 | `PENDING → CANCELLED`: CancelReservation | 대응 이벤트 있음 | `ReservationCancelled`(손님) | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| 6 | `CONFIRMED → CANCELLED`: CancelReservation (방문 3일 전까지) | 대응 이벤트 있음 | `ReservationCancelled`(손님/점주) | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| 7 | `CONFIRMED → NO_SHOW`: JudgeNoShow | 대응 이벤트 있음 | `ReservationNoShow` | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| 8 | `CONFIRMED → VISITED`: ConfirmVisit | 대응 이벤트 있음 | `VisitConfirmed`(수동) | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| 9 | `CONFIRMED → VISITED`: AutoConfirmVisit (7일) | 대응 이벤트 있음 | `VisitConfirmed`(자동) | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |

**소계**: 9개 전이 전부 대응 이벤트가 있다 — "대응 이벤트 없음"·"도메인 밖 구현" 판정은 reservation에 하나도 없다. `CreateReservation`은 timetable의 슬롯 생성과 달리 V1에도 명시 도메인 서비스(`CreateReservationDomainService`)가 있고 V2 카탈로그가 `ReservationCreated`를 실제 사용자 커맨드 결과로 닫아, 최초 생성부터 이벤트 기반이다(§3.2 대조).

---

## 3. 상태 필드 → 이벤트 매핑, fold 복원 판정

### 3.1 timetable (Slot 애그리거트)

| 상태 필드 | 값을 바꾸는 이벤트 | 복원 판정 | 근거 | 태그 |
|---|---|---|---|---|
| `status`(최초값 `AVAILABLE`) | 없음 — §1.1의 배치 INSERT | **갭 있음** | §1.1 직접 재확인 | `V1 코드에서 확인` — `batch-module/src/main/kotlin/com/reservation/batch/timetable/step/processor/TimeTableItemProcessor.kt` · `batch-module/src/main/kotlin/com/reservation/batch/timetable/job/TimeTableJobConfig.kt` |
| `status`(이후 전이: `HELD`/`BLOCKED`/`CONFIRMED`/`AVAILABLE` 복귀) | `SeatHeld`/`SlotBlocked`/`SeatConfirmed`/`SeatReleased`/`SlotUnblocked` | 복원 가능 | §1 표 | `V2 도메인 문서 근거`([[02-design-timetable]] §3) |
| 현재 점유 참조(`reservationId`, 있으면) | `SeatHeld`(설정) → `SeatReleased`(해제 함의) | 복원 가능 | [[02-design-timetable]] §3 페이로드 | `V2 도메인 문서 근거` |
| `heldAt`/`holdExpiresAt` | `SeatHeld` | 복원 가능 | [[02-design-timetable]] §3 페이로드 | `V2 도메인 문서 근거` |
| `blockedBy`/`blockedAt` | `SlotBlocked` → `SlotUnblocked`(해제 함의) | 복원 가능 | [[02-design-timetable]] §3 페이로드 | `V2 도메인 문서 근거` |

**전체 판정 — 갭 있음(부분적).** `HELD` 이후의 모든 전이는 이벤트로부터 완전히 재구성된다. 그러나 애그리거트의 **최초 상태**(`status = AVAILABLE`)는 어떤 이벤트에서도 나오지 않는다 — `TimeTableItemProcessor`가 만든 배치 산출물(JPA 엔티티 행)이 최초 값의 유일한 출처다.

이것이 fold 복원에 갖는 구조적 함의: [[ADR-002-selective-event-sourcing-scope]](`Proposed`)는 `timetable`을 "append-only 이벤트 스토어 + 리플레이로 상태 도출"하는 진짜 ES 컨텍스트로 분류한다. 순수 ES 전제라면 애그리거트의 전체 생애 — 생성을 포함해 — 가 이벤트 스트림의 리플레이만으로 재구성돼야 한다. 그런데 슬롯의 최초 존재 자체가 이벤트가 아니라 배치 INSERT로 생기므로, 이 최초 상태를 이벤트 스토어의 append-only 스트림만으로 재구성할 방법이 현재 문서·코드 어디에도 없다. 이 문서는 이 함의가 실제 결함인지, 아니면 "생성은 배치가 하고 그 이후만 ES로 다룬다"는 의도된 설계인지 판단하지 않는다 — 이 자체가 조사 결과이고, 판단은 사용자 몫이다(→ 07 후보).

### 3.2 reservation (Reservation 애그리거트)

| 상태 필드 | 값을 바꾸는 이벤트 | 복원 판정 | 근거 | 태그 |
|---|---|---|---|---|
| `status`(최초값 `PENDING`) | `ReservationCreated` | 복원 가능 | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |
| `status`(이후 전이 전부) | `ReservationConfirmed`/`ReservationFailed`/`ReservationExpired`/`ReservationCancelled`/`ReservationNoShow`/`VisitConfirmed` | 복원 가능 | §2 표 | `V2 도메인 문서 근거` |
| `booker`/`restaurantInformation`/`schedule`(생성 시 고정, 이후 불변) | `ReservationCreated` | 복원 가능 | [[03-design-reservation]] §1·§3 | `V2 도메인 문서 근거` |
| `cancelledBy`/`reason`/`cancelledAt` | `ReservationCancelled` | 복원 가능 | [[03-design-reservation]] §3 | `V2 도메인 문서 근거` |

**전체 판정 — 복원 가능.** timetable과 달리 `CreateReservation` 커맨드 자체가 사용자 커맨드로 처리되고 `ReservationCreated`가 카탈로그에 닫혀 있어, 최초 상태를 포함한 애그리거트 전 생애가 이벤트로부터 재구성된다. 이 문서가 조사한 범위 안에서 reservation 쪽에 timetable과 같은 종류의 생성-단계 갭은 발견되지 않았다.

### 3.3 restaurant — 후보 집합만, 완전 종결 아님

restaurant는 [[00-index]] §1의 스코프 선언대로 빅픽처 깊이에서만 다뤄졌다 — [[01-big-picture]] §3의 액터→커맨드→이벤트 후보 표가 이 컨텍스트의 유일한 근거다. 페이로드 필드가 닫히지 않았으므로 아래 표는 **후보**이며, "복원 가능"/"갭 있음"의 최종 판정을 내리지 않는다.

| 상태 필드(후보) | 값을 바꾸는 이벤트(후보) | 판정 | 근거 | 태그 |
|---|---|---|---|---|
| `status`(`ACTIVE`/`DEACTIVATED`) | `RestaurantRegistered`(최초)/`RestaurantDeactivated` | 판정 보류(후보만) | [[01-big-picture]] §3 | `V2 도메인 문서 근거` |
| `introduce`/`contact` | `RestaurantInfoUpdated` | 판정 보류(후보만) | [[01-big-picture]] §3 | `V2 도메인 문서 근거` |
| `address`/`coordinate` | `RestaurantLocationUpdated` | 판정 보류(후보만) | [[01-big-picture]] §3 | `V2 도메인 문서 근거` |
| `routine` | `RestaurantRoutineUpdated` | 판정 보류(후보만) | [[01-big-picture]] §3 | `V2 도메인 문서 근거` |
| `photos` | `RestaurantPhotosUpdated` | 판정 보류(후보만) | [[01-big-picture]] §3 | `V2 도메인 문서 근거` |
| `tags`/`nationalities`/`cuisines` | `RestaurantCategoriesUpdated` | 판정 보류(후보만) | [[01-big-picture]] §3 | `V2 도메인 문서 근거` |

domain/03 §2 자체가 재활성화(`ReactivateRestaurant`) 경로 부재를 미결로 남겨 뒀다는 사실도 이 표가 완결이 아님을 뒷받침한다. **완전 종결은 Phase 7-6 재실시로 남긴다** — 이 문서는 restaurant를 timetable/reservation과 같은 무게의 "닫힌" 컨텍스트로 쓰지 않는다.

---

## 4. 보완 이벤트 제안 — `제안(근거 없음, 사용자 판단 필요)`

- **timetable 생성 갭(§3.1)의 보완 후보**: [[02-design-timetable]] §3이 이미 `SlotProvisioned`를 `제안(근거 없음, 사용자 판단 필요)`으로 올려 뒀다("사건 자체가 어느 문서에도 이름이 없어 이름·존재 여부 모두 사용자 판단 필요"). 이 문서는 그 제안을 다시 발명하지 않고 인용만 한다 — 채택하면 §3.1의 갭이 닫히고, 채택하지 않으면 timetable의 최초 상태는 배치 산출물로 남는다. 이 문서는 채택 여부를 판단하지 않는다.
- **restaurant 재활성화 경로**: domain/03 §2가 미결로 남긴 지점을 이 문서가 다시 확인했다 — `ReactivateRestaurant`/`RestaurantReactivated` 같은 보완 이벤트가 필요한지는 domain/03도, [[01-big-picture]]도 답하지 않는다. `제안(근거 없음, 사용자 판단 필요)`으로만 존재를 표시하고, 이름·필드는 이 문서가 임의로 만들지 않는다.

이 외에 reservation 쪽에는 이 문서가 조사한 범위에서 fold 복원을 막는 갭이 발견되지 않았으므로 보완 제안이 없다.

---

## 5. 인프라 — 설계하지 않음, 인용만

이벤트 저장·복구의 세부 절차는 이 문서의 범위가 아니며 [[ADR-005-event-store-mysql-table]]·[[DESIGN-009-event-store-lifecycle]]·[[ADR-018-event-store-recovery-semantics]]로 넘긴다.

- 이벤트 스토어 저장 모델(`event_store` 테이블, `(aggregate_id, sequence_no)` UNIQUE): [[ADR-005-event-store-mysql-table]](`Proposed`).
- 스냅샷 생성·무효화·재생성, 스냅샷 주기 N: [[DESIGN-009-event-store-lifecycle]](`Accepted`) §4.2·§6.3.
- 복구 의미론(진실 원천 단일 보호·보상 이벤트 정정·복구 순서): [[ADR-018-event-store-recovery-semantics]](`Proposed`).
