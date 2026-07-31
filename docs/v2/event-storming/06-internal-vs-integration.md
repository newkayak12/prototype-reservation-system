# 내부 도메인 이벤트 vs 통합 이벤트 — timetable · reservation 분류

- **상태**: 초안
- **작성일**: 2026-07-29
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **스코프**: 분류 대상은 [[00-index]] §1 "레퍼런스 심화"에서 페이로드 필드까지 닫힌 두 컨텍스트 — [[02-design-timetable]](timetable) · [[03-design-reservation]](reservation) — 뿐이다. `restaurant`·`schedule`·`user`·`authenticate`는 §1에서 별도로 다룬다.
- **근거 문서**: [[ADR-021-integrated-event-contract-module]](`Proposed`) · [[RFC-024-domain-event-type-and-replay-layering]](`🏷 합의 (2026-07-04) — ADR 비준 대기`) · [[DESIGN-019-event-execution-layering]](`Accepted (2026-07-04) — RFC-024 합의 반영, ADR 비준은 후속`) · [[ADR-010-event-schema-evolution]](`Proposed`) · `docs/v2/modules/02-contract-module.md` · [[04-policies-and-choreography]](컨텍스트 횡단 흐름의 1차 출처)

---

## 0. 이 문서가 하는 일 / 하지 않는 일

이 문서는 [[02-design-timetable]] §3·[[03-design-reservation]] §3·§4가 이미 닫은 카탈로그 이벤트명을, 그 이벤트를 다른 컨텍스트가 실제로 소비하는지 여부로 "내부 도메인 이벤트"/"통합 이벤트" 두 갈래로 나눈다. 소비 여부의 근거는 [[04-policies-and-choreography]] §1·§3(코레오그래피 정책 표·컨텍스트 횡단 그래프)이며, 이 문서가 그 표를 다시 조사하지 않고 그대로 인용한다. Phase 7-1(contract-module) 이관 후보(§5)는 후보로만 제시하고 확정·승인하지 않는다 — [[00-index]] §0의 A/B 갈림길과 마찬가지로 이 문서도 결정 권한을 갖지 않는다.

---

## 1. 분류 미실시 — 나머지 4개 쓰기 컨텍스트

`restaurant`·`schedule`·`user`·`authenticate`는 [[01-big-picture]]가 빅픽처 수준(액터→커맨드→이벤트 후보)까지만 닫았고 페이로드 필드가 없다 — 내부/통합 분류는 어느 이벤트가 실제로 다른 컨텍스트의 상태를 바꾸는 결과까지 이어지는지를 요구하는데, 그 판단에 필요한 상세가 이 세 문서([[01-big-picture]] §3·§4·§5·§6)에는 없다. 근거 없이 분류를 만들어내지 않는다.

| 컨텍스트 | 상태 | 태그 |
|---|---|---|
| restaurant | 분류 미실시 — Phase 7-6 재실시 전제 | `V2 도메인 문서 근거`([[01-big-picture]] §3, 페이로드 미상세) |
| schedule | 분류 미실시 — Phase 7-6 재실시 전제 | `V2 도메인 문서 근거`([[01-big-picture]] §4, 페이로드 미상세) |
| user | 분류 미실시 — Phase 7-6 재실시 전제 | `V2 도메인 문서 근거`([[01-big-picture]] §5, 페이로드 미상세) |
| authenticate | 분류 미실시 — Phase 7-6 재실시 전제 | `V2 도메인 문서 근거`([[01-big-picture]] §6, 페이로드 미상세) |

`docs/v2/00-status-and-plan.md` §6.3 7-6도 이 네 컨텍스트를 순서 3~6(restaurant·schedule·user·authenticate)으로 별도 배치해 뒀다 — 이 문서의 미실시 판단과 그 배치가 모순되지 않는다.

---

## 2. 이미 결정된 것 vs 이 문서가 새로 판정하는 것

### 2.1 ADR-021 규칙 (원문 상태: `Proposed`)

> "공유 계약 모듈은 얇은 통합 이벤트(published language)만 담는다. 내부 도메인 이벤트는 각 컨텍스트가 소유하며 공유 모듈로 묶지 않는다." — [[ADR-021-integrated-event-contract-module]] 결정 본문

### 2.2 이미 결정된 부분

- **구조 원칙(ADR-021, `Proposed`)**: 통합 이벤트는 얇은 공유 계약 모듈 소관, 내부 도메인 이벤트는 컨텍스트 소유 — 공유 모듈로 묶지 않는다.
- **타입 소유·매핑 계층(RFC-024, `🏷 합의`)**: 애그리거트는 core 타입(내부 도메인 이벤트)만 반환하고, core→contract(통합 이벤트) 매핑·발행은 `command-application`이 수행한다. 리플레이 fold 조립도 application 소관이며 `command-infrastructure`는 타입-불가지 `StoredEvent`(bytes-only)만 다룬다.
- **계층 배정 상세(DESIGN-019, `Accepted (2026-07-04) — RFC-024 합의 반영, ADR 비준은 후속`)**: §3의 계층 책임 표·§7 매트릭스 정합이 위 규칙을 구체 계층(모듈)까지 확정해 뒀다.

즉 "내부 이벤트와 통합 이벤트는 서로 다른 소유자를 갖는다"는 이미 상당 부분 결정돼 있다.

### 2.3 이 카탈로그가 새로 판정하는 부분

RFC-024·DESIGN-019·ADR-021 어디에도 "`SeatHeld`는 통합 이벤트다" 같은 이벤트 이름 단위의 판정은 없다 — 이들은 일반 규칙(내부/통합의 소유·계층 분업)만 확정했다. **어느 구체 이벤트 이름이 그 규칙의 어느 쪽에 속하는지는 이 문서(06)가 처음 판정한다.** 판정 기준은 "다른 컨텍스트가 실제로 그 이벤트를 소비하는가"([[04-policies-and-choreography]] §1·§3의 조사 결과) 하나다 — 소비자가 없으면 통합으로 올리지 않는다(§3 각주 참조).

---

## 3. 분류표 — timetable·reservation 카탈로그 이벤트명 전부 (고유 이벤트명 14개 · 표 행 15행, 미분류 0건)

행수가 이벤트명 수보다 하나 많은 이유: `SeatReleased`가 5a(외부 이벤트 유래)·5b(TTL 유래) 두 인스턴스로 분류가 갈려([[02-design-timetable]] §2의 트리거 종류 분리를 그대로 승계) 이름은 하나이지만 행은 둘이다 — 아래 §4가 이 지점을 다룬다.

판정 기준: [[04-policies-and-choreography]] §1(코레오그래피 정책 표)·§3(컨텍스트 횡단 그래프)에 그 이벤트가 트리거 또는 결과로 등장해 다른 컨텍스트의 정책을 발동시키면 **통합 이벤트**, 그 표·그래프 어디에도 등장하지 않으면(컨텍스트 내부 상태 전이로 끝나면) **내부 도메인 이벤트**다.

| 카탈로그 명명 | 컨텍스트 | 분류 | 소비 컨텍스트(통합인 경우) | 판단 근거 | 태그 |
|---|---|---|---|---|---|
| `SlotProvisioned` | timetable | 내부 도메인 이벤트 | — | [[04-policies-and-choreography]] 표·그래프 어디에도 등장하지 않음. 태그가 `제안(근거 없음, 사용자 판단 필요)`([[02-design-timetable]] §3)이라는 사실은 이 분류와 별개 축 — 존재 여부가 미결이어도 "존재한다면 내부"라는 분류는 성립한다 | `제안(근거 없음, 사용자 판단 필요)`(이벤트 자체의 존재·이름이 미결) |
| `SeatHeld` | timetable | 통합 이벤트 | `payment`(범위 밖, [[04-policies-and-choreography]] §0.2·P2) | P2: "`SeatHeld`(timetable) → payment가 결제 처리 개시" | `V2 도메인 문서 근거` |
| `SeatConfirmed` | timetable | 내부 도메인 이벤트 | — | P4는 `ReservationConfirmed`가 `ConfirmSeat`를 발동해 `SeatConfirmed`를 만드는 것까지만 다룬다 — `SeatConfirmed` 자체를 트리거로 삼는 후속 정책이 표·그래프에 없다. 다른 컨텍스트가 만든 커맨드의 **결과**라는 사실이 곧 통합 이벤트를 뜻하지 않는다 — 그 결과를 **또 다른 컨텍스트가 소비할 때만** 통합이다 | `V2 도메인 문서 근거` |
| `SeatReleased`(5a, 외부 이벤트 유래 — `ReservationFailed`/`ReservationCancelled`/`ReservationNoShow`) | timetable | 내부 도메인 이벤트 | — | P6·P10·P13 어디에도 이 인과의 `SeatReleased`를 트리거로 삼는 후속 정책이 없다 — §3의 판정 기준(소비자 존재 여부)을 그대로 적용하면 내부다. 다만 카탈로그 명명이 5b(통합 이벤트로 판정됨)와 동일해, "같은 이름이 두 분류에 걸친다"는 별도 문제가 §4에 남는다(분류 판정 자체는 보류가 아니다) | `V2 도메인 문서 근거` |
| `SeatReleased`(5b, TTL 유래) | timetable | 통합 이벤트 | `reservation`(P8) | P8: "`SeatReleased`(timetable, TTL 유래) → `ExpireReservation`(reservation) → `ReservationExpired`" | `V2 도메인 문서 근거` |
| `SlotBlocked` | timetable | 내부 도메인 이벤트 | — | [[04-policies-and-choreography]] 표·그래프에 미등장(매장 점주 수동 커맨드의 컨텍스트 내부 결과) | `V2 도메인 문서 근거` |
| `SlotUnblocked` | timetable | 내부 도메인 이벤트 | — | 위와 동일 | `V2 도메인 문서 근거` |
| `ReservationCreated` | reservation | 통합 이벤트 | `timetable`(P1) | P1: "`ReservationCreated`(reservation) → `HoldSeat`(timetable) → `SeatHeld`" | `V2 도메인 문서 근거` |
| `ReservationConfirmed` | reservation | 통합 이벤트 | `timetable`(P4) | P4 | `V2 도메인 문서 근거` |
| `ReservationFailed` | reservation | 통합 이벤트 | `timetable`(P6) | P6: "`ReservationFailed`(reservation) → `ReleaseSeat`(timetable) → `SeatReleased`" | `V2 도메인 문서 근거` |
| `ReservationExpired` | reservation | 내부 도메인 이벤트 | — | [[04-policies-and-choreography]] 표·그래프에 `ReservationExpired`를 트리거로 삼는 후속 정책이 없음 — `PENDING→EXPIRED` 상태 전이의 종착 결과 | `V2 도메인 문서 근거` |
| `ReservationCancelled`(손님/매장 점주 — 카탈로그 명명 동일) | reservation | 통합 이벤트 | `timetable`(P10) · `payment`(범위 밖, P9) | P10: "→ `ReleaseSeat`(timetable)". P9: 취소 시 payment가 자기 상태 가드로 환불 여부 판단(조건부) | `V2 도메인 문서 근거` |
| `ReservationNoShow` | reservation | 통합 이벤트 | `timetable`(P13) · `payment`(범위 밖, P12) | P13: "→ `ReleaseSeat`(timetable)". P12: "노쇼 시 수수료 부과"(payment 내부 반응, ACL 경계를 넘어 돌아오는 화살표는 없음 — [[04-policies-and-choreography]] §2.3) | `V2 도메인 문서 근거` |
| `VisitConfirmed`(수동/자동 — 카탈로그 명명 동일) | reservation | 내부 도메인 이벤트 | — | [[04-policies-and-choreography]] 표·그래프에 미등장. domain/01 §2가 서술하는 "point 적립 트리거"는 [[04-policies-and-choreography]] §1.1이 이미 "point 도메인 문서 자체가 없어 표에 넣지 않는다"고 판단한 것과 같은 이유로 이 문서도 소비자로 인정하지 않는다 | `V2 도메인 문서 근거` |
| `RefundRequired` | reservation | 통합 이벤트 | `payment`(범위 밖, P14/P15) | P14/P15: "`RefundRequired`(reservation → payment)". 발행 주체는 reservation(범위 안), 소비자는 payment(범위 밖) — [[04-policies-and-choreography]] §2.1이 방향을 정정해 둔 것을 그대로 승계 | `V2 도메인 문서 근거` |

**집계**: 고유 이벤트명 14개(표 행수는 15행, `SeatReleased` 5a/5b 분리 계산) 전부가 내부/통합 중 하나로 분류됐다 — 미분류(판정 자체를 시도하지 않은 행)는 0건이다. 다만 `SeatReleased`는 5a(내부)·5b(통합)로 **같은 이름이 서로 다른 두 분류에 걸치는** 유일한 사례다 — 이 이름 공유 자체가 만드는 문제는 방치하지 않고 §4에서 별도로 다룬다.

---

## 4. `SeatReleased` — 이름 하나가 분류 두 개에 걸치는 문제 (기존 hotspot의 새 층위)

[[02-design-timetable]] §4는 `SeatReleased`가 원인이 다른 두 트리거(5a 외부 이벤트/5b TTL)를 동일 이름으로 공유해 페이로드에 `cause` 필드가 없다는 것을 미결로 남겼다. §3의 분류 판정 자체는 각 인스턴스에 개별 적용했으므로 보류가 아니다 — 5a=내부, 5b=통합, 둘 다 확정 분류다. 이 문서가 새로 드러낸 것은 그다음 층위다: **하나의 카탈로그 이름(`SeatReleased`)이 분류가 다른 두 인스턴스를 동시에 가리킨다.** Phase 7-1이 이 이름 그대로 계약 모듈에 통합 이벤트 클래스를 만들면(§6 이관 후보), 그 클래스가 실제로는 5a(내부 전용)까지 함께 노출하는 것인지, 5b(TTL 유래)만 좁혀 노출하는 것인지 이름만으로는 구분되지 않는다.

이 문서는 이 문제를 해소하지 않는다(§0 선언대로 결정 권한 밖). 07(hotspots)로 넘긴다 — 원인 미구분(02가 남긴 페이로드 미결)과 이름 공유로 인한 이관 경계 모호성(이 문서가 새로 발견한 미결)이 같은 근본 원인(하나의 이름이 둘을 가리킴)에서 나온다는 사실도 함께 넘긴다.

---

## 5. `docs/v2/modules/02-contract-module.md` 대조 — 어긋남 행

`modules/02-contract-module.md` §4 구조 예시는 `timetable/`·`reservation/` 패키지 아래 이벤트 클래스 이름을 주석으로 나열한다. 이 문서의 분류 대상(timetable·reservation)과 이름을 대조한다 — 일치하는 이름은 표에 넣지 않고, 어긋나는 이름만 행으로 남긴다(양쪽 다 수정하지 않는다).

| modules/02 §4 예시 이름 | 이 카탈로그의 대응 | 어긋남 | 태그 |
|---|---|---|---|
| `TimeTableCreated`(timetable 패키지 주석) | 대응 없음 — [[02-design-timetable]] §3의 슬롯 생성 이벤트는 `SlotProvisioned`(태그 `제안`)이며, domain/02·V1 어디에도 `TimeTableCreated`라는 이름의 근거가 없다([[02-design-timetable]] §2.1 조사) | **예** — [[00-index]] §3 "문서 간 표류" 항목("`TimeTableCreated`(modules/02) vs domain/02 미명명")과 동일 지점을 이 문서가 재확인 | `제안(근거 없음, 사용자 판단 필요)`(어느 이름이 맞는지 근거 없음) |

`SeatHeld`·`SeatReleased`(timetable), `ReservationCreated`·`ReservationConfirmed`·`ReservationCancelled`(reservation)는 modules/02 예시와 이 카탈로그의 이름이 문자열 그대로 일치한다 — 어긋남 없음.

---

## 6. Phase 7-1 contract-module 이관 후보 (후보만 — 확정·승인 표기 없음)

§3에서 통합 이벤트로 판정된 이벤트를 이관 후보로 제시한다. `SeatReleased`는 5b(TTL 유래)만 통합으로 판정됐고 5a(외부 이벤트 유래)는 내부로 남았으므로, 이관 후보에는 5b만 올린다 — 다만 §4가 지적한 이름 공유 문제(5a·5b가 같은 클래스명을 씀) 때문에 5b만 좁혀 이관하는 것이 실제로 가능한지(같은 이름의 내부 이벤트와 어떻게 구분할지)는 이관 시점에 먼저 정리돼야 한다.

| 카탈로그 명명 | 소비 컨텍스트 | 이관 후보 | 근거 | 태그 |
|---|---|---|---|---|
| `SeatHeld` | payment(범위 밖) | 후보 | §3 | `V2 도메인 문서 근거` |
| `SeatReleased`(5b, TTL 유래만 — 5a는 내부로 남아 이관 후보 아님) | reservation | 후보 — 단 §4의 이름 공유 문제가 먼저 정리돼야 실제로 5a와 분리 이관 가능한지 판단 가능 | §3·§4 | `V2 도메인 문서 근거` |
| `ReservationCreated` | timetable | 후보 | §3 | `V2 도메인 문서 근거` |
| `ReservationConfirmed` | timetable | 후보 | §3 | `V2 도메인 문서 근거` |
| `ReservationFailed` | timetable | 후보 | §3 | `V2 도메인 문서 근거` |
| `ReservationCancelled` | timetable, payment(범위 밖) | 후보 | §3 | `V2 도메인 문서 근거` |
| `ReservationNoShow` | timetable, payment(범위 밖) | 후보 | §3 | `V2 도메인 문서 근거` |
| `RefundRequired` | payment(범위 밖) | 후보 | §3 | `V2 도메인 문서 근거` |

이 표의 "이관 후보"는 이관을 확정·승인하는 표기가 아니다 — Phase 7-1이 실제로 이 목록을 참고해 판단할 후보 집합일 뿐이다.

### 6.1 이관 시 페이로드 씬닝 지점 (additive-only·스키마 진화 인용)

[[ADR-021-integrated-event-contract-module]](`Proposed`)의 additive-only 규율("필드는 추가만 하고 ... 삭제·rename은 금지한다")은 통합 이벤트로 발행되는 순간부터 그 페이로드가 **되돌릴 수 없는 공개 계약**이 됨을 뜻한다. 반면 내부 도메인 이벤트는 [[ADR-010-event-schema-evolution]](`Proposed`)의 명시 등록 업캐스터로 재해석 가능해 진화 여지가 더 크다 — 내부 이벤트가 리치한 필드를 들고 있어도 나중에 업캐스팅으로 흡수할 수 있지만, 통합 이벤트는 한 번 나가면 그 필드를 영구히 진다. 이 비대칭이 "통합 이벤트 페이로드는 내부보다 얇아야 한다"는 근거다.

이 규율이 적용될 구체 지점(결정하지 않음, 위치만 표시):

- `ReservationCreated`([[03-design-reservation]] §3 내부 페이로드 11개 필드: `reservationId, userId, restaurantId, tableNumber, tableSize, slotId, date, day, startTime, endTime, requestedAt`) — 소비 컨텍스트 `timetable`(`HoldSeat`)이 실제로 쓰는 필드가 이 11개 전부인지, 감사성 필드(`requestedAt`)·중복 파생 필드(`day`, `date`에서 유도 가능)까지 통합 계약에 실어야 하는지는 이 문서가 판단하지 않는다.
- `ReservationCancelled`([[03-design-reservation]] §3: `reservationId, cancelledBy, reason, cancelledAt` — 매장 점주 변형은 `reason` 포함) — `payment`(범위 밖)가 환불 여부만 판단한다면(P9: "자기 상태 가드") `reason`(취소 사유 텍스트)까지 필요한지는 확인되지 않는다.

---

## 7. 이 문서가 남기는 미결 (→ 07)

- `SeatReleased` 5a(내부)·5b(통합)가 이름을 공유해 생기는 이관 경계 모호성(§4) — 분류 자체는 확정됐으나 이름 충돌 처리 방식은 미결.
- §6.1의 페이로드 씬닝 지점 — 위치만 표시했고 실제 필드 축소 여부는 미판단.
- §5의 `TimeTableCreated` 어긋남 — 어느 쪽(modules/02 예시 vs 이 카탈로그의 `SlotProvisioned`/미명명)도 이 문서가 수정하지 않는다.
