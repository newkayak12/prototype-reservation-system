# 이벤트 스토밍 — 빅픽처 (쓰기 6개 컨텍스트)

- **상태**: 초안
- **작성일**: 2026-07-29
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **규약**: [[_conventions]] §2(태그 3어휘 · 8열 스키마 · 금지 인프라 토큰 · ADR-022 봉투 1회 정의)를 그대로 따른다 — 여기서 재정의하지 않는다. 아래 표는 §2.2 스키마를 빅픽처 스코프에 맞춰 적용한다 — 액터·커맨드·트리거 종류를 명시하고, 페이로드 필드는 [[00-index]] §1 스코프 선언에 따라 이 문서에서 다루지 않는다(레퍼런스 심화 2개 컨텍스트만 별도 파일에서 페이로드까지 닫는다).
- **층위 규율**: 이 문서는 이벤트 후보와 애그리거트 경계까지만 다룬다. 표의 "정책/후속" 열은 각 도메인 문서(`docs/v2/domain/0X-*.md`)가 이미 기록해 둔 서술을 그대로 옮긴 것이며, 통합 이벤트 승격 여부나 소비 컨텍스트를 이 문서가 새로 확정하지 않는다.
- **명명 승계 여부**: 아래 모든 이벤트 행은 "카탈로그 명명"과 "기존 명명(V1 원본/출처)"을 분리해 담는다. 두 열이 같은 값을 갖는 행은 A(기존 명명 승계)를 고르면 그대로 쓰이고, 값이 갈리거나 미결 표시가 붙은 행은 B(재검토 개방)를 고르면 작업 대상이 된다 — 어느 쪽인지는 [[00-index]] §0의 사용자 결정 사항이며, 이 문서는 결정하지 않는다.

---

## 0. ADR-002 분류 — 쓰기 6개 컨텍스트의 저장 방식

[[ADR-002-selective-event-sourcing-scope]](원문 상태: `Proposed`)가 9개 컨텍스트를 저장 방식 기준 셋으로 나눈 것 중, 이 문서가 다루는 쓰기 6개 컨텍스트에 해당하는 부분만 옮긴다.

| 컨텍스트 | 분류 | 저장 방식 | 원문 상태 | 근거 | 태그 |
|---|---|---|---|---|---|
| reservation | ES | append-only 이벤트 스토어 + 리플레이 | `Proposed` | [[ADR-002-selective-event-sourcing-scope]] | `V2 도메인 문서 근거` |
| timetable | ES | append-only 이벤트 스토어 + 리플레이 | `Proposed` | [[ADR-002-selective-event-sourcing-scope]] | `V2 도메인 문서 근거` |
| restaurant | ES | append-only 이벤트 스토어 + 리플레이 | `Proposed` | [[ADR-002-selective-event-sourcing-scope]] | `V2 도메인 문서 근거` |
| schedule | 비-ES | 상태 테이블 + 같은 트랜잭션 Outbox | `Proposed` | [[ADR-002-selective-event-sourcing-scope]] | `V2 도메인 문서 근거` |
| user | 비-ES | 상태 테이블 + 같은 트랜잭션 Outbox | `Proposed` | [[ADR-002-selective-event-sourcing-scope]] | `V2 도메인 문서 근거` |
| authenticate | 비-ES | 상태 테이블 + 같은 트랜잭션 Outbox | `Proposed` | [[ADR-002-selective-event-sourcing-scope]] | `V2 도메인 문서 근거` |

이 분류의 이벤트 이름은 [[00-index]] §0 결정(B — 재검토 개방)에 따라 잠정이다. ADR-002 본문의 "재검토 트리거"(ES로 분류한 컨텍스트에서 이력·동시성 요구가 실제로 없다고 판명되면 비-ES로 강등)도 그 갈림길과 별개로 ADR 본문에 이미 조건으로 적혀 있다.

---

## 1. reservation

### 애그리거트 경계

`Reservation` 단일 애그리거트(요청 단위 `id` 기준) — `booker`·`restaurantInformation`·`schedule`·`occupancy` 4개 VO를 소유한다. V1 상태값은 `shared-module`의 `ReservationStatus` enum이 실제로 `RESERVED`·`CANCELLED` 2개 값을 갖고 있는데(태그: `V1 코드에서 확인`, 출처: `shared-module/src/main/kotlin/com/reservation/enumeration/ReservationStatus.kt`), `docs/v2/domain/01-reservation.md` §1은 "상태: `RESERVED` 하나뿐 — 취소·확정·노쇼 상태 없음"이라 서술한다. 이 드리프트는 여기서 해소하지 않고 재검토 후보 목록(07-hotspots-and-open-questions.md)으로 넘긴다. V2는 이 애그리거트 위에 7개 상태(PENDING/CONFIRMED/FAILED/EXPIRED/CANCELLED/NO_SHOW/VISITED)를 제안한다(태그: `V2 도메인 문서 근거`, 출처: `docs/v2/domain/01-reservation.md` §2).

### 액터 → 커맨드 → 이벤트 후보

| 액터 | 커맨드 | 애그리거트 | 트리거 종류 | 카탈로그 명명(과거형) | 기존 명명(V1 원본/출처) | 태그 | 출처 |
|---|---|---|---|---|---|---|---|
| 손님 | `CreateReservation` | Reservation | 사용자 커맨드 | `ReservationCreated` | V1 이벤트 없음 — `CreateReservationDomainService`가 검증+생성만 수행, event 패키지 자체가 없음 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/reservation/`(하위에 `event/` 없음) |
| — (이벤트 `PaymentConfirmed`) | `ConfirmReservation` | Reservation | 이벤트 구독(사가) | `ReservationConfirmed` | V1 대응 없음 — payment 컨텍스트 자체가 V1에 없는 순수 신설 | `V2 도메인 문서 근거` | `docs/v2/domain/01-reservation.md` §2 |
| — (이벤트 `PaymentFailed`) | `FailReservation` | Reservation | 이벤트 구독(사가) | `ReservationFailed` | 위와 동일(신설) | `V2 도메인 문서 근거` | `docs/v2/domain/01-reservation.md` §2 |
| 스케줄러 (결제 대기 만료) | `ExpireReservation` | Reservation | 스케줄러 | `ReservationExpired` | 위와 동일(신설) — 타임아웃 소유권 이전([[ADR-008-saga-orchestration-vs-choreography]] 개정)으로 reservation 자체 스케줄러가 만료를 판정 | `V2 도메인 문서 근거` | `docs/v2/domain/01-reservation.md` §2 · [[ADR-008-saga-orchestration-vs-choreography]] |
| 손님 | `CancelReservation` | Reservation | 사용자 커맨드 | `ReservationCancelled` | V1 이벤트 없음 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/reservation/`(하위에 `event/` 없음) |
| 매장 점주 | `CancelReservation` | Reservation | 사용자 커맨드 | `ReservationCancelled` | V1 이벤트 없음 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/reservation/`(하위에 `event/` 없음) |
| 스케줄러 | `JudgeNoShow` | Reservation | 스케줄러 | `ReservationNoShow` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/01-reservation.md` §2 |
| 매장 점주 | `ConfirmVisit` | Reservation | 사용자 커맨드 | `VisitConfirmed` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/01-reservation.md` §2 |
| 타이머(7일) | `AutoConfirmVisit` | Reservation | 스케줄러 | `VisitConfirmed` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/01-reservation.md` §2 |

> **미결(→ 07)**: 방문 확정 불변식 #11("EXPIRED 상태에서 `PaymentConfirmed` 수신 시 확정 거부 + 환불 트리거")을 나타낼 도메인 이벤트가 이 표에 없다 — `docs/v2/domain/01-reservation.md` §2 자체가 이 지점을 미결로 남겨 두고 있다. `ConfirmReservation`/`FailReservation`/`ExpireReservation` 3개 커맨드는 V1에 승계할 기존 명명 자체가 없어([[00-index]] §3) 재검토 대상이 아니다.

---

## 2. timetable

### 애그리거트 경계

V1 `TimeTable`은 행 단위(날짜×시간×테이블 1행 = 1슬롯) 애그리거트였다. V2는 애그리거트를 **Slot**(슬롯 단위)으로 재정의한다 — 핫 애그리거트 경합을 슬롯 단위로 격리하기 위함이다(태그: `V2 도메인 문서 근거`, 출처: `docs/v2/domain/02-timetable.md` §2, 원 근거는 DESIGN-006 §6.3으로 도메인 문서가 인용).

### 액터 → 커맨드 → 이벤트 후보

| 액터 | 커맨드 | 애그리거트 | 트리거 종류 | 카탈로그 명명(과거형) | 기존 명명(V1 원본/출처) | 태그 | 출처 |
|---|---|---|---|---|---|---|---|
| — (슬롯 생성, schedule 유래) | *(미명명)* | Slot | 미상 | *(미명명)* | V1 대응 없음 — 슬롯 자체가 행 생성으로 이미 존재, "생성 이벤트"라는 개념이 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/02-timetable.md` §2 상태 다이어그램(`[*] --> AVAILABLE`) |
| — (이벤트 `ReservationCreated`) | `HoldSeat` | Slot | 이벤트 구독(사가) | `SeatHeld` | V1: `TimeTableOccupiedDomainEvent(timeTableId, timeTableOccupancyId)` — hold/confirm 구분 없이 점유 하나로 뭉쳐 있음 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/timetable/event/TimeTableOccupiedDomainEvent.kt` |
| — (이벤트 `ReservationConfirmed`) | `ConfirmSeat` | Slot | 이벤트 구독(사가) | `SeatConfirmed` | 위 행과 동일한 V1 이벤트 — V1엔 임시/확정 구분이 없다 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/timetable/event/TimeTableOccupiedDomainEvent.kt` |
| — (이벤트 `ReservationFailed`/`ReservationCancelled`/`ReservationNoShow`/`ReservationExpired`) | `ReleaseSeat` | Slot | 이벤트 구독(사가) | `SeatReleased` | V1 이벤트 없음 — `detachOccupied()`만 존재, 해제 이벤트 자체가 없음. 타임아웃 포함 모든 해제가 reservation 이벤트 구독으로 수렴(timetable 자체 `ExpireSeat`·TTL 소멸, [[ADR-008-saga-orchestration-vs-choreography]] 개정) | `V2 도메인 문서 근거` | `docs/v2/domain/02-timetable.md` §2 · [[ADR-008-saga-orchestration-vs-choreography]] |
| 매장 점주 | `BlockSlot` | Slot | 사용자 커맨드 | `SlotBlocked` | V1 이벤트 없음 — 수동 차단 개념 자체가 V1에 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/02-timetable.md` §2 |
| 매장 점주 | `UnblockSlot` | Slot | 사용자 커맨드 | `SlotUnblocked` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/02-timetable.md` §2 |

> **미결(→ 07)**: 슬롯 생성(`[*] → AVAILABLE`) 트리거명 자체가 `docs/v2/domain/02-timetable.md`에 없다([[00-index]] §3 "상태 전이 트리거 미명명"). `docs/v2/modules/02-contract-module.md` §4 구조 예시엔 `TimeTableCreated`가 통합 이벤트 패키지 주석으로 등장하는데, 도메인 문서와 이름이 일치하는지 대조가 안 돼 있다 — 문서 간 표류 후보. ~~`ReleaseSeat`(보상)와 `ExpireSeat`(TTL)가 동일한 `SeatReleased`로 합쳐지는 점~~은 타임아웃 소유권 이전([[ADR-008-saga-orchestration-vs-choreography]] 개정 · [[07-hotspots-and-open-questions]] H1)으로 **해소**됐다 — timetable 자체 `ExpireSeat`이 사라지고 `SeatReleased`가 단일 내부 이벤트로 수렴한다.

---

## 3. restaurant

### 애그리거트 경계

`Restaurant` 단일 애그리거트 — `userId`(소유자), `companyId`(company 컨텍스트 참조) 보유. `docs/v2/domain/03-restaurant.md` §1.

### 액터 → 커맨드 → 이벤트 후보

| 액터 | 커맨드 | 애그리거트 | 트리거 종류 | 카탈로그 명명(과거형) | 기존 명명(V1 원본/출처) | 태그 | 출처 |
|---|---|---|---|---|---|---|---|
| 매장 점주 | `RegisterRestaurant` | Restaurant | 사용자 커맨드 | `RestaurantRegistered` | V1: 매장 "등록" 자체의 이벤트는 없다 — `CreateScheduleEvent(restaurantId)`가 명령형 이름으로 존재하나, 이는 등록 결과로 schedule 생성을 트리거하는 별개 이벤트다 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/restaurant/event/CreateScheduleEvent.kt` |
| 매장 점주 | `UpdateRestaurantInfo` | Restaurant | 사용자 커맨드 | `RestaurantInfoUpdated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/03-restaurant.md` §2 |
| 매장 점주 | `UpdateRestaurantLocation` | Restaurant | 사용자 커맨드 | `RestaurantLocationUpdated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/03-restaurant.md` §2 |
| 매장 점주 | `UpdateRestaurantRoutine` | Restaurant | 사용자 커맨드 | `RestaurantRoutineUpdated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/03-restaurant.md` §2 |
| 매장 점주 | `UpdateRestaurantPhotos` | Restaurant | 사용자 커맨드 | `RestaurantPhotosUpdated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/03-restaurant.md` §2 |
| 매장 점주 | `UpdateRestaurantCategories` | Restaurant | 사용자 커맨드 | `RestaurantCategoriesUpdated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/03-restaurant.md` §2 |
| 관리자 | `DeactivateRestaurant` | Restaurant | 관리자 커맨드 | `RestaurantDeactivated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/03-restaurant.md` §2 |

> **미결(→ 07)**: `RestaurantInfoUpdated`가 `docs/v2/modules/02-contract-module.md` §4 구조 예시의 `RestaurantRenamed`와 같은 개념을 가리키는지 이름만으로는 확인되지 않는다([[00-index]] §3 "문서 간 표류"). 재활성화(`ReactivateRestaurant`) 커맨드가 없다 — `docs/v2/domain/03-restaurant.md` §2가 자체적으로 "비활성화가 최종 상태인지" 미결로 남겨 둠.

---

## 4. schedule

### 애그리거트 경계

`Schedule` 단일 애그리거트 — `restaurantId`가 PK이자 restaurant와 1:1. `timeSpans`·`holidays`·`tables` 3개 컬렉션을 소유한다. `docs/v2/domain/04-schedule.md` §1.

### 액터 → 커맨드 → 이벤트 후보

| 액터 | 커맨드 | 애그리거트 | 트리거 종류 | 카탈로그 명명(과거형) | 기존 명명(V1 원본/출처) | 태그 | 출처 |
|---|---|---|---|---|---|---|---|
| — (이벤트 `RestaurantRegistered`) | `InitSchedule` | Schedule | 이벤트 구독(사가) | `ScheduleInitialized` | V1: schedule 쪽엔 이벤트가 없고, restaurant의 `CreateScheduleEvent`를 구독해 Schedule을 생성하는 리스너가 곧 이 트리거다 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/restaurant/event/CreateScheduleEvent.kt`(소비 측 schedule 패키지엔 `event/` 없음) |
| 매장 점주 | `SetTimeSpans` | Schedule | 사용자 커맨드 | `TimeSpansUpdated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/04-schedule.md` §2 |
| 매장 점주 | `SetHolidays` | Schedule | 사용자 커맨드 | `HolidaysUpdated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/04-schedule.md` §2 |
| 매장 점주 | `SetTables` | Schedule | 사용자 커맨드 | `TablesUpdated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/04-schedule.md` §2 |
| 매장 점주 | `ActivateSchedule` | Schedule | 사용자 커맨드 | `ScheduleActivated` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/04-schedule.md` §2 |
| 시스템(월말) | `GenerateMonthlySlots` | — (결과는 timetable Slot 소관) | 시스템 배치 | *(이벤트 후보 없음 — 전달 방식 미정)* | V1 대응 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/04-schedule.md` §2 |

> **미결(→ 07)**: `GenerateMonthlySlots`가 timetable에 슬롯 생성을 알리는 경로가 이벤트인지 직접 커맨드 호출인지 `docs/v2/domain/04-schedule.md` §2가 자체적으로 미결로 남김 — 2번 항목(timetable 슬롯 생성 트리거 미명명)과 같은 지점이다. `DeactivateSchedule` 경로가 없다 — restaurant의 `RestaurantDeactivated`를 schedule이 구독해야 하는지도 같은 문서가 미결로 남김.

---

## 5. user

### 애그리거트 경계

`User` 계층 — `ServiceUser` 인터페이스 아래 `User`(일반)·`RestaurantOwner`(매장 점주)·`Admin` 3개 구현체. `docs/v2/domain/05-user.md` §1.

### 액터 → 커맨드 → 이벤트 후보

| 액터 | 커맨드 | 애그리거트 | 트리거 종류 | 카탈로그 명명(과거형) | 기존 명명(V1 원본/출처) | 태그 | 출처 |
|---|---|---|---|---|---|---|---|
| 일반 사용자 | `RegisterUser` | User | 사용자 커맨드 | `UserRegistered` | V1 이벤트 없음 — `com.reservation.user` 하위에 `event/` 패키지 없음 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/user/` |
| 관리자 | `RegisterSeller` | User | 관리자 커맨드 | `SellerRegistered` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/05-user.md` §2 |
| 사용자 | `ChangePassword` | User | 사용자 커맨드 | `PasswordChanged` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/05-user.md` §2 |
| 사용자 | `ChangeNickname` | User | 사용자 커맨드 | `NicknameChanged` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/05-user.md` §2 |
| 사용자 | `ChangePersonalInfo` | User | 사용자 커맨드 | `PersonalInfoChanged` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/05-user.md` §2 |
| 사용자 | `ResignUser` | User | 사용자 커맨드 | `UserResigned` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/05-user.md` §2 |

> **미결(→ 07)**: `User.password`와 6.authenticate의 `Authenticate.password`가 별도 애그리거트에 중복 보관된다 — `PasswordChanged`/`TemporaryPasswordIssued`가 서로를 구독해 동기화하는지, password를 한쪽으로 일원화할지 `docs/v2/domain/05-user.md` §2가 자체적으로 미결로 남김([[00-index]] §3 "V1-V2 승계 여부 미결"). `RegisterSeller`(V1 `CreateSellerUserDomainService`)가 일반 가입 검증 규칙을 전혀 타지 않는 점도 같은 문서가 재확인 필요로 남겼다.

---

## 6. authenticate

### 애그리거트 경계

`Authenticate` 단일 애그리거트 — `lockState`(실패 카운트·잠금 시각)와 `accessLog`(접근 이력)를 소유. `docs/v2/domain/06-authenticate.md` §1.

### 액터 → 커맨드 → 이벤트 후보

| 액터 | 커맨드 | 애그리거트 | 트리거 종류 | 카탈로그 명명(과거형) | 기존 명명(V1 원본/출처) | 태그 | 출처 |
|---|---|---|---|---|---|---|---|
| 사용자 | `SignIn` | Authenticate | 사용자 커맨드 | `SignInSucceeded` | V1 이벤트 없음 — `canISignIn`이 상태 변이+이력 기록을 한 메서드 안에서 동시 수행, 이벤트로 분리돼 있지 않음 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/authenticate/`(하위에 `event/` 없음) |
| 사용자 | `SignIn` | Authenticate | 사용자 커맨드 | `SignInFailed` | V1 이벤트 없음 — 위 행과 동일한 `canISignIn` 내부 처리 | `V1 코드에서 확인` | `core-module/src/main/kotlin/com/reservation/authenticate/`(하위에 `event/` 없음) |
| — (이벤트 `SignInFailed` 5회 누적) | `LockAccount` | Authenticate | 이벤트 구독(사가) | `AccountLocked` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/06-authenticate.md` §2 |
| 스케줄러 | `UnlockAccount`(30분 경과) | Authenticate | 스케줄러 | `AccountUnlocked` | 위와 동일 | `V2 도메인 문서 근거` | `docs/v2/domain/06-authenticate.md` §2 |
| 사용자 | `SignOut` | — | 사용자 커맨드 | `SignedOut` | V1 대응 없음 — 세션/토큰 무효화 개념 자체가 V1에 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/06-authenticate.md` §2 |
| 사용자 | `FindLoginId` | — (조회) | 조회(이벤트 없음) | *(해당 없음)* | V1 대응 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/06-authenticate.md` §2 |
| 사용자 | `ResetPassword` | Authenticate | 사용자 커맨드 | `TemporaryPasswordIssued` | V1 이벤트 없음 | `V2 도메인 문서 근거` | `docs/v2/domain/06-authenticate.md` §2 |

> **미결(→ 07)**: 잠금 중 재시도가 잠금 타이머를 계속 연장시키는 V1 동작(`docs/v2/domain/06-authenticate.md` §2 "코드 대조 메모")을 V2가 유지할지, "잠금 중 시도 자체 차단"으로 바꿀지 같은 문서가 미결로 남김. `Authenticate.password`/`User.password` 이원화는 5.user 항목과 동일 지점.

---

## 7. 읽기 전용 예정 (참고) — menu · company

빅픽처 대상은 쓰기 6개 컨텍스트뿐이지만, restaurant가 참조하는 두 lookup 컨텍스트 중 도메인 문서가 있는 것만 경계를 적는다. [[ADR-002-selective-event-sourcing-scope]](원문 상태: `Proposed`)는 `menu`·`category`·`company`를 "현행/lookup — 저장 방식 현행 유지"로 분류한다.

| 컨텍스트 | 애그리거트 경계 | 근거 | 원문 상태 | 태그 |
|---|---|---|---|---|
| menu | `Menu` 단일 애그리거트, `restaurantId` 참조. V1에 이벤트 없음. 다른 컨텍스트가 구독해야 할 때 Outbox 이벤트를 추가하는 안이 문서에 있다([[ADR-002-selective-event-sourcing-scope]] 분류 참조) | `docs/v2/domain/07-menu.md` §1, §3 | `Proposed`(ADR-002) | `V2 도메인 문서 근거` |
| company | `Company` 단일 애그리거트. `changeBrand()` 외 행위 없음, 이벤트 없음. restaurant가 `companyId`로 참조 | `docs/v2/domain/09-company.md` §1, §2 | `—`(도메인 문서, ADR/RFC 아님) | `V2 도메인 문서 근거` |

`category`는 근거가 될 도메인 문서(`docs/v2/domain/`)가 없다 — 이 표에 넣지 않고 재검토 후보 목록(07-hotspots-and-open-questions.md)으로 넘긴다.
