# 이벤트 스토밍 재실시 — 색인

- **상태**: 초안
- **작성일**: 2026-07-29
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **왜 이 문서가 있나**: [[00-status-and-plan]] §4가 "이벤트 스토밍 재실시 → 도메인 이벤트 카탈로그 확정"을 **TBD 선행 작업**으로 올려두었다. 이 카탈로그가 그 산출물이고, Phase 7-1(contract-module)의 직접 입력이다.
- **위임 근거**: [[DESIGN-006-aggregate-design]] §3 — "컨텍스트별 구체 애그리거트·이벤트 목록: 이벤트 스토밍 재실시 후 확정한다. 기존 보드는 참고용으로만 사용한다."
- **표기 규약**: [[_conventions]] (유지보수용)

---

## 0. 결정 — B (재검토 개방)

**사용자 결정, 2026-07-30: B.**

이 카탈로그의 이벤트 명명은 **잠정**이다. [[07-hotspots-and-open-questions]]가 열거한 hotspot 6건(H1~H6)과 미결 질문 7건(Q1~Q7)이 재검토 세션의 **작업 목록**이며, 그 세션이 명명을 정한다. 기존 문서(`domain/01~06` · `modules/02-contract-module` · ADR-002 · ADR-008)에 쓰인 이름을 그대로 승계하지 않는다.

### 0.1 갈림길이 있었던 이유

`docs/v2/domain/01~06.md` · `docs/v2/modules/02-contract-module.md` · ADR-002 · ADR-008이 **이미 구체 이벤트 이름을 써 놓았다** (`ReservationCreated`, `SeatHeld`, `RestaurantRegistered` 등).

그런데 이 카탈로그의 위임 근거인 DESIGN-006은 "스토밍 재실시 후 확정, 기존 보드는 참고용"이라 못박는다. 스토밍 산출물을 소비해야 할 문서가 스토밍보다 먼저 이름을 정한 **역순 구조**였다. 선택지는 기존 명명 승계(A)와 재검토 개방(B)이었고, B가 선택됐다.

### 0.2 아직 열려 있는 하위 결정 — Phase 7-1 착수 타이밍

B는 명명을 재검토에 맡긴다. Phase 7-1(contract-module)을 언제 시작할지는 **별개 결정이며 아직 정해지지 않았다**.

| | 대기 | 잠정 착수 |
|---|---|---|
| **방식** | 재검토 세션 완료까지 7-1 보류 | 이 카탈로그의 잠정 이름으로 착수하고, 재검토 결과에 맞춰 리네임 |
| **비용** | 7-1이 재검토 일정에 묶인다 | 리네임 작업 + 그 사이 작성된 코드·테스트의 이름 변경 |

### 0.3 파급 대상

ADR-002(`Proposed`) · ADR-008(`Proposed`) · ADR-021(`Proposed`) · [[13-phase7-checklist]] 7-1. B를 골랐으므로 이 네 곳의 이벤트 이름은 재검토 결과가 나올 때까지 잠정으로 취급한다.

재검토 결과를 기존 ADR 본문에 직접 반영할 수 있다. ADR 26건이 전부 `Proposed`이고, `.claude/conventions/boundaries.md`의 불변 규칙은 `Accepted` ADR에만 걸리기 때문이다 —

> "ADR files under `docs/v2/adr/**` once their status is `Accepted` — immutable per MADR convention; a reversal is a new superseding ADR, not an edit."

즉 리네임을 위해 "새 ADR로 supersede"하는 절차를 밟을 필요가 없다.

카탈로그 파일 전량 재작업도 없다. 모든 이벤트 행이 `기존 명명` 열과 `카탈로그 명명` 열을 이미 분리해 담았으므로, 재검토는 후자만 갱신한다.

`00-status-and-plan.md` §4와 `13-phase7-checklist.md`의 실제 갱신은 **별도 작업**이다. 이 디렉터리의 어느 파일도 그 갱신을 대신하지 않는다.

> 번호 025는 ADR 26건에 없다. `00-status-and-plan.md`:24가 025번을 "조건부 보류"로 적어 둔 대로 파일이 만들어지지 않았고, `ADR-INDEX.md`:80이 그 조건을 "읽기 신선도 예외가 실제로 필요해질 때만 작성"으로 규정한다.

---

## 1. 스코프

| 수준 | 대상 | 어디까지 |
|---|---|---|
| 빅픽처 | 쓰기 6개 — `reservation` · `timetable` · `restaurant` · `schedule` · `user` · `authenticate` | 액터 → 커맨드 → 트리거 → 이벤트 |
| 레퍼런스 심화 | `timetable` · `reservation` 2개 | 페이로드 필드까지 |

레퍼런스 2개만 심화하는 이유는 [[13-phase7-checklist]] 7-1이 "레퍼런스 컨텍스트(`timetable`·`reservation`) 통합 이벤트 클래스 정의"를 명시하기 때문이다. Phase 7-1이 실제로 소비할 범위만 닫았다.

나머지 4개 컨텍스트의 페이로드 상세화는 이 카탈로그 범위 밖이다. 필요해지면 Phase 7-2 이후 별도 작업이며, 이 문서가 그 시점을 정하지 않는다.

---

## 2. 파일 지도

| 파일 | 내용 |
|---|---|
| [[01-big-picture]] | 쓰기 6개 컨텍스트 — 애그리거트 경계, 액터→커맨드→이벤트 |
| [[02-design-timetable]] | timetable — 상태 전이 7개, 이벤트, 페이로드 |
| [[03-design-reservation]] | reservation — 상태 전이 9개, 이벤트, 페이로드 |
| [[04-policies-and-choreography]] | 컨텍스트 횡단 사가 정책, 문서 간 어긋남 |
| [[05-fold-determinism]] | 상태 전이 vs 이벤트 대조 — fold 복원 갭 |
| [[06-internal-vs-integration]] | 내부/통합 분류, Phase 7-1 이관 후보 |
| [[07-hotspots-and-open-questions]] | hotspot · 미결 질문 총괄 — B를 고를 경우의 작업 목록 |
| [[_conventions]] | 표기 규약, 대조 범위 (유지보수용) |

---

## 3. 재검토 후보 — 범주 요약

컨텍스트별 개별 항목과 근거는 [[07-hotspots-and-open-questions]]에 전량 열거한다. 여기서는 범주만.

| 범주 | 건수 | 예 |
|---|---|---|
| 상태 전이 트리거 미명명 | 3 | timetable `[*]→AVAILABLE` 슬롯 생성 트리거명 없음 · schedule `GenerateMonthlySlots` 전달 방식 미확정 |
| 문서 간 표류 — 같은 개념, 다른 이름 | 4 | `TimeTableCreated`(modules/02) vs domain/02 미명명 · `RestaurantRenamed` vs `RestaurantInfoUpdated` · `RefundRequired`(DESIGN-007만) vs domain/01 미결 |
| V1→V2 승계 여부 미결 | 6 | `ReservationStatus{RESERVED,CANCELLED}` vs 문서 서술 · 생산자/소비자 이벤트 DTO 이원화 · `AuthenticateGeneralUserUseCase` vs `SignIn` 어휘 차이 |
| archive 표류 | 2 | `TimeTableCreated`류가 modules/02에 재등장 · `ScheduleDeactivated`가 domain/04 미결과 이름만 같음 |

`ConfirmReservation` · `FailReservation` · `ExpireReservation` 3개는 재검토 대상이 **아니다**. V1에 승계할 기존 명명 자체가 없는 순수 신설 개념이라(UseCase 36개 전수 확인, 0건 매치) A/B와 무관하다.

---

## 4. archive 보드

`docs/v2/archive/02-event-sourcing-transition-points.md`는 헤더가 `⚠️ DEPRECATED (2026-07-28)`이고 "현행 진실원은 index·adr/·design_doc/"라 명시한다. 이 카탈로그는 근거로 인용하지 않는다.

다만 그 보드에 남아 있던 이벤트 이름이 카탈로그 명명과 겹치거나 갈라지는 지점은 [[07-hotspots-and-open-questions]]에서 대조한다.
