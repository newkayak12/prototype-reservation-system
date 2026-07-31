# reservation 컨텍스트 — 설계 레벨 (Reservation 애그리거트)

- **상태**: 초안
- **작성일**: 2026-07-29
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **스코프**: [[00-index]] §1 "레퍼런스 심화" — 페이로드 필드까지 닫는다. Phase 7-1(contract-module)이 이 파일을 직접 소비한다.
- **표 스키마**: [[02-design-timetable]] §0과 동일 — 9열 `카탈로그 명명 | 기존 명명 / V1 원본 | 트리거 종류 | 발생 커맨드 | 애그리거트 | 페이로드 필드 | 근거(출처) | 원문 상태 | 태그`, 태그 3어휘 고정, 봉투는 [[_conventions]] §2.4 참조(반복 안 함), 금지 인프라 토큰([[_conventions]] §2.3) 본문 미사용. `발생 커맨드` 열 추가는 [[_conventions]] §2.5 "규약 확장 로그"에 등록돼 있다(재정의가 아니라 등록된 예외) — `원문 상태`는 [[_conventions]] §2.2가 정의한 그대로 독립 열로 유지하며(ADR/RFC 미해당 행은 `—`), 접거나 생략하지 않는다.
- **근거 문서**: [[domain/01-reservation]](상태머신 9개 전이) · [[ADR-002-selective-event-sourcing-scope]](`Proposed`) · [[ADR-008-saga-orchestration-vs-choreography]](`Proposed`) · [[ADR-016-aggregate-concurrency-pessimistic-lock]](`Proposed`) · [[ADR-022-event-identity]](`Proposed`) · [[RFC-029-event-carried-payload-uniform]](`🏷 합의 (2026-07-05) — ADR 비준 대기`)

---

## 1. 애그리거트 — Reservation

V1은 `toSnapshot()` 외 행위가 없는 빈약 애그리거트, 상태는 `RESERVED` 하나뿐(`core-module/src/main/kotlin/com/reservation/reservation/Reservation.kt`). V2는 7개 상태·9개 전이를 가진 상태 머신으로 재설계한다(domain/01 §2).

**V1 VO 구성** (근거: 아래 파일, 태그 `V1 코드에서 확인`):
```
booker: ReservationBooker(userId)                                    — reservation/vo/ReservationBooker.kt
restaurantInformation: ReservationRestaurantInformation               — reservation/vo/ReservationRestaurantInformation.kt
  (restaurantId, tableNumber, tableSize)
schedule: ReservationSchedule                                        — reservation/vo/ReservationSchedule.kt
  (timeTableId, date, day, startTime, endTime)
occupancy: ReservationOccupancy                                       — reservation/vo/ReservationOccupancy.kt
  (timeTableOccupancyId, occupiedDatetime)
```
V2에서 `occupancy` VO의 `timeTableOccupancyId`/`occupiedDatetime`은 timetable의 `TimetableOccupancy` 엔티티가 Slot 애그리거트로 흡수된 것과 같은 이유로 폐기되고, `slotId`(=V1 `timeTableId`) 참조 하나로 대체된다([[02-design-timetable]] §1). ID 포맷은 V1 UUIDv4 → V2 UUIDv7(domain/01 §3).

---

## 2. 상태 전이 — 9개, 빠짐없이 (domain/01 §2 상태머신 대응)

domain/01의 mermaid 화살표 중 실제 전이 9개(터미널 `--> [*]` 5개는 상태 도달 표기일 뿐 전이가 아니므로 산입하지 않음 — UML `stateDiagram-v2`의 통상 표기).

| # | 전이 | 트리거 종류 | 원문 상태 | 태그 |
|---|---|---|---|---|
| 1 | `[*] → PENDING`: CreateReservation | 커맨드 (손님) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/01 §2) |
| 2 | `PENDING → CONFIRMED`: PaymentConfirmed | 외부 이벤트 (경계: payment) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/01 §2) |
| 3 | `PENDING → FAILED`: PaymentFailed | 외부 이벤트 (경계: payment) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/01 §2) |
| 4 | `PENDING → EXPIRED`: SeatReleased (TTL 만료) | 외부 이벤트 (경계: timetable — TTL 자체는 timetable의 스케줄러 자치, [[ADR-008-saga-orchestration-vs-choreography]]; reservation이 보는 건 timetable이 발행한 이벤트) | `Proposed`([[ADR-008-saga-orchestration-vs-choreography]]) | `V2 도메인 문서 근거`(domain/01 §2 · ADR-008) |
| 5 | `PENDING → CANCELLED`: CancelReservation | 커맨드 (손님) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/01 §2) |
| 6 | `CONFIRMED → CANCELLED`: CancelReservation (방문 3일 전까지) | 커맨드 (손님) — 매장 점주는 방문일시 전까지(불변식#8, 완화된 조건) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/01 §2) |
| 7 | `CONFIRMED → NO_SHOW`: JudgeNoShow (예약 시각 경과 후) | 시간 경과·스케줄러 | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/01 §2) |
| 8 | `CONFIRMED → VISITED`: ConfirmVisit | 커맨드 (매장 점주) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/01 §2) |
| 9 | `CONFIRMED → VISITED`: AutoConfirmVisit (7일 미확정 시) | 시간 경과·스케줄러 (타이머 7일) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거`(domain/01 §2) |

---

## 3. 이벤트 카탈로그 (8열)

> 봉투는 [[_conventions]] §2.4 참조. 페이로드 정책: [[RFC-029-event-carried-payload-uniform]](`🏷 합의 (2026-07-05) — ADR 비준 대기`).
> [[00-index]] §3: "`ConfirmReservation`/`FailReservation`/`ExpireReservation` 3개 V2 커맨드는 재검토 대상이 아니다 — V1 승계 명명 자체가 없는 순수 신설 개념" — 아래 표의 `발생 커맨드` 열 중 이 3개는 그 확인을 그대로 인용한다.

| 카탈로그 명명 | 기존 명명 / V1 원본 | 트리거 종류 | 발생 커맨드 | 애그리거트 | 페이로드 필드 | 근거(출처) | 원문 상태 | 태그 |
|---|---|---|---|---|---|---|---|---|
| `ReservationCreated` | `CreateReservation`(domain/01 §2) | 커맨드 | `CreateReservation` | Reservation | `reservationId, userId, restaurantId, tableNumber, tableSize, slotId, date, day, startTime, endTime, requestedAt` | `booker/restaurantInformation/schedule` VO 필드 대응 — `core-module/src/main/kotlin/com/reservation/reservation/vo/` 아래 `ReservationBooker.kt`·`ReservationRestaurantInformation.kt`·`ReservationSchedule.kt`. `timeTableOccupancyId`/`occupiedDatetime`(V1 `ReservationOccupancy.kt`)는 `slotId` 참조로 대체(§1) | `—`(V1 코드, ADR/RFC 아님) | `V1 코드에서 확인` — `core-module/src/main/kotlin/com/reservation/reservation/vo/ReservationBooker.kt` 외 위 2개 VO |
| `ReservationConfirmed` | `ConfirmReservation` ← `PaymentConfirmed`(domain/01 §2) | 외부 이벤트 (경계: payment) | `ConfirmReservation` | Reservation | `reservationId, confirmedAt` | domain/01 §2 액터→커맨드→이벤트 표 — V1 대응 없음(순수 신설, [[00-index]] §3 확인 인용) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `ReservationFailed` | `FailReservation` ← `PaymentFailed`(domain/01 §2) | 외부 이벤트 (경계: payment) | `FailReservation` | Reservation | `reservationId, failedAt` — 결제 실패 사유 상세(`failureReason` 등)는 domain/01·V1 어디에도 근거 없어 미포함(발명 금지) | domain/01 §2 — V1 대응 없음(순수 신설) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `ReservationExpired` | `ExpireReservation` ← `SeatReleased`(domain/01 §2) | 외부 이벤트 (경계: timetable) | `ExpireReservation` | Reservation | `reservationId, expiredAt` | domain/01 §2 — V1 대응 없음(순수 신설) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `ReservationCancelled` (손님) | `CancelReservation`(domain/01 §2, 손님) | 커맨드 | `CancelReservation` | Reservation | `reservationId, cancelledBy(GUEST), cancelledAt` | domain/01 §2 불변식#7("방문 3일 전까지") — V1 대응 없음(V1은 취소 상태 자체가 없음, domain/01 §1 한계) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `ReservationCancelled` (매장 점주) | `CancelReservation`(domain/01 §2, 매장 점주) | 커맨드 | `CancelReservation` | Reservation | `reservationId, cancelledBy(OWNER), reason, cancelledAt` — `reason`은 30자 이상 200자 미만 필수(불변식#9) | domain/01 §2 불변식#8·#9 — V1 대응 없음 | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `ReservationNoShow` | `JudgeNoShow`(domain/01 §2) | 시간 경과·스케줄러 | `JudgeNoShow` | Reservation | `reservationId, judgedAt` | domain/01 §2 불변식#14 — V1 대응 없음(V1은 노쇼 상태 없음) | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `VisitConfirmed` (수동) | `ConfirmVisit`(domain/01 §2, 매장 점주) | 커맨드 | `ConfirmVisit` | Reservation | `reservationId, confirmedBy(OWNER), confirmedAt` | domain/01 §2 불변식#12("예약 시각 이후에만") — V1 대응 없음 | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |
| `VisitConfirmed` (자동) | `AutoConfirmVisit`(domain/01 §2, 타이머 7일) | 시간 경과·스케줄러 | `AutoConfirmVisit` | Reservation | `reservationId, confirmedBy(SYSTEM), confirmedAt` | domain/01 §2 불변식#13 — V1 대응 없음 | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |

---

## 4. 상태 전이가 아닌 방출 이벤트 — `RefundRequired` (domain/01 불변식#11 미결 재조명)

domain/01 §2 불변식#11은 다음을 "미결"로 남겼다: "EXPIRED 상태에서 `PaymentConfirmed` → 확정 거부 + 환불 트리거 ... #11의 '환불 트리거'를 나타내는 도메인 이벤트가 액터→커맨드→이벤트 표에 없다."

이 문서가 조사한 결과, 이 이벤트는 **다른 문서에 이미 이름이 있다.** [[ADR-008-saga-orchestration-vs-choreography]](`Proposed`)는 다음과 같이 서술한다: "`reservation`이 EXPIRED 상태에서 `PaymentConfirmed`를 받으면 확정을 거부하고 `RefundRequired`를 발행해 `payment`가 환불을 처리한다." [[00-index]] §3도 이를 문서 간 표류 사례로 이미 요약해 뒀다("`RefundRequired`(DESIGN-007만) vs domain/01 미결 표기") — 이 문서는 그 표류가 DESIGN-007뿐 아니라 ADR-008에도 있음을 추가로 확인한다.

**이 표가 §3 전이 표에 들어가지 않는 이유**: `RefundRequired`는 상태 전이를 일으키지 않는다(EXPIRED 유지, 확정을 "거부"할 뿐). §2의 9개 전이 산술을 흔들지 않도록 별도 절로 분리한다.

| 카탈로그 명명 | 기존 명명 / V1 원본 | 트리거 종류 | 발생 커맨드 | 애그리거트 | 페이로드 필드 | 근거(출처) | 원문 상태 | 태그 |
|---|---|---|---|---|---|---|---|---|
| `RefundRequired` | ADR-008 원문 그대로("RefundRequired") — domain/01에는 이름 없음(불변식#11 "미결") | 외부 이벤트 (경계: payment, `PaymentConfirmed` 수신이 계기) | `ConfirmReservation`(EXPIRED 상태 가드에서 거부 분기) | Reservation | `reservationId, rejectedAt` — 환불 금액 등 payment 도메인 상세는 이 문서 스코프 밖(payment ACL 경계) | [[ADR-008-saga-orchestration-vs-choreography]] 결정 본문 인용 · domain/01 §2 불변식#11 | `Proposed`([[ADR-008-saga-orchestration-vs-choreography]]) | `V2 도메인 문서 근거` |

**07-hotspots 후보로 표시**: domain/01(도메인 카탈로그의 1차 소스)과 ADR-008(사가 조율 결정)이 서로 다른 문서에서 같은 사건을 다르게 다룬다 — domain/01은 "미결"로, ADR-008은 이름까지 확정해 서술한다. 이 문서는 `RefundRequired`를 카탈로그에 채택하되(§4 표), domain/01 자체의 액터→커맨드→이벤트 표를 갱신하는 것은 이 파일의 권한 밖이므로 그 갱신 여부는 `07-hotspots-and-open-questions.md` 대상으로 남긴다.

---

## 5. 컨텍스트 불변식

| # | 불변식 | 태그 | 원문 상태 | 출처 |
|---|---|---|---|---|
| 1 | 예약 날짜는 미래여야 한다(과거 날짜 불가) | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#1 — V1에 없던 규칙 |
| 2 | `startTime < endTime`(시간 범위 정합성) | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#2 |
| 3 | `date`의 요일 = `day`(DayOfWeek 정합성) | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#3 |
| 4 | `tableNumber ≥ 1`, `tableSize ≥ 1` | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#4·#5 |
| 5 | 참조 ID(`userId`·`restaurantId`·`slotId`) 비어있지 않음 + UUID v7 포맷 | `V1 코드에서 확인` | `—`(V1 코드, ADR/RFC 아님) | V1 Policy 8개(Empty+Format) 승계, v4→v7 변경 — 예: `core-module/src/main/kotlin/com/reservation/reservation/policy/validations/ReservationUserIdPolicy.kt`(디렉터리 `core-module/src/main/kotlin/com/reservation/reservation/policy/validations/` 안 8개 전부 동일 패턴), `core-module/src/main/kotlin/com/reservation/reservation/service/validate/ValidateUserId.kt`(디렉터리 `core-module/src/main/kotlin/com/reservation/reservation/service/validate/` 안 4개 전부 동일 패턴) |
| 6 | 손님 취소는 방문 3일 전까지만 가능 | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#7 |
| 7 | 매장 점주 취소는 방문일시 전까지 가능(손님보다 완화) | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#8 |
| 8 | 매장 점주 취소 시 사유 필수(30자 이상 200자 미만) | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#9 |
| 9 | 취소 권한은 예약자 본인 또는 해당 매장 점주만 | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#10 |
| 10 | EXPIRED 상태에서 지연 `PaymentConfirmed` 수신 시 확정 거부 + `RefundRequired` 발행(§4) | `V2 도메인 문서 근거` | `Proposed`([[ADR-008-saga-orchestration-vs-choreography]]) | domain/01 §2 불변식#11 · [[ADR-008-saga-orchestration-vs-choreography]] |
| 11 | 방문 확정은 예약 시각 이후에만 가능 | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#12 |
| 12 | 7일 미확정 시 자동 확정(`AutoConfirmVisit`) | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#13 |
| 13 | 노쇼 판정은 예약 시각 경과 후에만 | `V2 도메인 문서 근거` | `—`(도메인 문서, ADR/RFC 아님) | domain/01 §2 불변식#14 |
| 14 | 상태 전이는 유효한 이전 상태에서만(상태 가드) — 단일 애그리거트 내 즉시 일관성, 동시 요청 직렬화는 락이 아닌 상태 가드로 방어(paid-after-expiry 레이스, ADR-008) | `V2 도메인 문서 근거` | `Proposed`([[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-008-saga-orchestration-vs-choreography]]) | domain/01 §2 불변식#15 · [[ADR-016-aggregate-concurrency-pessimistic-lock]]("락 범위=단일 aggregate_id") · [[ADR-008-saga-orchestration-vs-choreography]] |
| 15 | 동일 사용자·동일 시간대 중복 예약 불가 — 애그리거트 단독 판단 불가(교차 애그리거트/읽기 모델 조회 필요), 즉시 일관성 범위를 넘으므로 락 granularity 결정 자체가 이 지점의 위임 대상 | `V2 도메인 문서 근거` | `Proposed`([[ADR-016-aggregate-concurrency-pessimistic-lock]]) | domain/01 §2 불변식#16 · [[ADR-016-aggregate-concurrency-pessimistic-lock]]("granularity 위임: ... 이벤트 스토밍에 위임") |
| 16 | 이벤트 정체성은 `event_id`(UUIDv7) 단일 키, 전역 순번 없음 — 재구축 열거는 이 키의 keyset 스캔으로 처리 | `V2 도메인 문서 근거` | `Proposed`([[ADR-022-event-identity]]) | [[ADR-022-event-identity]] |

---

## 6. V1→V2 요약 (재확인, domain/01 §3과 동일 — 재정의 아님)

domain/01 §3 "V1→V2 변경 요약" 표를 그대로 참조한다. 이 파일은 그 표를 다시 쓰지 않고, §1~§5의 설계 레벨 상세만 얹는다.
