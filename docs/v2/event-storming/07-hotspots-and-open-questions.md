# 재검토 안건 — hotspot · 미결 질문

- **상태**: 초안
- **작성일**: 2026-07-29 (결정 반영 2026-07-30)
- **무엇인가**: [[00-index]] §0이 B(재검토 개방)를 택했으므로, 아래 항목이 재검토 세션의 **안건 목록**이다.
- **H와 Q의 차이**: `H`는 문서·코드 사이에 **이미 벌어진 어긋남**이다(이 카탈로그가 해소하지 않고 사실로 남긴다). `Q`는 카탈로그가 **답을 낼 수 없어 넘기는 질문**이다.

---

## 1. 결정 현황 (2026-07-30)

| 상태 | 항목 |
|---|---|
| 결정됨 | **Q1** 명명 방침 = B · **H1** `SeatReleased` 이름 분리 |
| 보류 — 선행 조건 필요 | **H4** 이벤트 범위를 먼저 정해야 답이 나온다 |
| 당장 결정 불가 | **H2 · H3 · H5 · H6 · Q2 · Q3 · Q4 · Q5 · Q6 · Q7** |

"당장 결정 불가"는 사용자가 2026-07-30에 명시했다. 재촉 대상이 아니다.

---

## 2. 결정된 것

### H1 · 좌석 해제 이벤트를 원인별로 쪼갠다

좌석이 풀리는 경우가 둘이다. 예약이 실패·취소·노쇼로 풀리는 것과, 임시 점유 시간(TTL)이 지나 풀리는 것. 지금까지 둘 다 `SeatReleased` 하나로 발행했고, 페이로드에 원인 필드가 없어 구분이 불가능했다.

**결정**: 이름을 나눈다. 예약 유래는 `SeatReleased`, TTL 유래는 `SeatExpired`. 원인 필드(`cause`)는 넣지 않는다 — 이름이 원인을 나른다.

부수 효과로 분류 충돌도 사라진다. 예약 유래는 timetable 내부 이벤트, TTL 유래는 reservation이 구독하는 통합 이벤트인데, 지금까지 한 이름이 두 분류에 걸쳐 있었다.

근거: [[02-design-timetable]] §4 · [[06-internal-vs-integration]] §3·§4

### 전파 대기

이 결정은 아직 카탈로그 표에 들어가지 않았다.

`SeatReleased`는 **7개 파일 40개 지점**에 있고, 그중 TTL 유래만 `SeatExpired`가 되어야 한다. 예약 유래(`AVAILABLE` 복귀 5a)와 확정 후 취소·노쇼(6번 전이)는 `SeatReleased`로 남는다. 지점마다 판단이 갈리므로 일괄 치환이 안 된다.

B를 택했으니 다른 이름도 재검토 대상이다. **전파는 §1의 "당장 결정 불가" 항목이 닫힌 뒤 한 번에 한다.** 건별로 하면 같은 표를 반복해 고친다.

전파 대상: [[01-big-picture]] · [[02-design-timetable]] · [[03-design-reservation]] · [[04-policies-and-choreography]] · [[05-fold-determinism]] · [[06-internal-vs-integration]] · 이 문서.

---

## 3. 이름을 정해야 하는 것

### H2 · 환불 요청 이벤트

예약이 만료된 뒤 결제가 늦게 성공하면 확정을 거부하고 환불해야 한다. `docs/v2/domain/01-reservation.md` §2 불변식#11은 "이 환불 트리거를 나타낼 이벤트가 표에 없다"고 비워 뒀다. 그런데 [[ADR-008-saga-orchestration-vs-choreography]](`Proposed`)는 이미 `RefundRequired`라는 이름으로 결정 본문에 서술해 놨다.

**정할 것**: 그 이름을 채택할지, `domain/01-reservation.md`의 이벤트 표를 채울지.

근거: [[03-design-reservation]] §4

### H3 · 슬롯 생성 이벤트

`docs/v2/modules/02-contract-module.md` §4의 패키지 구조 예시 주석에 `TimeTableCreated`가 있다. 그런데 `docs/v2/domain/02-timetable.md`와 V1 코드 어디에도 그 이름의 근거가 없다. 카탈로그가 내놓은 후보는 `SlotProvisioned` 하나이고, 그것도 근거 없는 제안이다.

**정할 것**: Phase 7-1이 만들 클래스 이름.

근거: [[00-index]] §3 · [[01-big-picture]] §2 · [[06-internal-vs-integration]] §5

### H4 · 매장 정보 변경 이벤트의 범위 — 보류

카탈로그는 `RestaurantInfoUpdated`, `docs/v2/modules/02-contract-module.md` §4 예시는 `RestaurantRenamed`다. 같은 개념인지 이름만으로는 알 수 없다.

**사용자 판단 (2026-07-30)**: `RestaurantInfoUpdated`의 범위를 어디까지로 두느냐에 따라 개명이 그 안에 포함될 수도, 별개 이벤트일 수도 있다. **범위를 먼저 정해야 답이 나온다.** 범위 확정은 보류.

근거: [[01-big-picture]] §3

### Q3 · 슬롯 생성 이벤트를 만들 것인가

H3은 "이름을 뭐로 할지"이고, 이것은 "그 이벤트가 존재해야 하는지"다. `SlotProvisioned`는 사건 자체가 어느 문서에도 없어 카탈로그가 제안으로만 올렸다.

**정할 것**: 채택 여부. 채택하면 H6이 닫힌다. 미채택이면 슬롯의 최초 상태는 계속 배치 산출물로 남는다.

근거: [[02-design-timetable]] §3 · [[05-fold-determinism]] §4

### Q4 · 매장 재활성화 이벤트를 만들 것인가

`docs/v2/domain/03-restaurant.md` §2가 "비활성화가 최종 상태인지"를 스스로 미결로 남겼다. 최종 상태가 아니라면 재활성화 이벤트가 필요하다.

**정할 것**: `RestaurantReactivated` 신설 여부. restaurant는 Phase 7-6 순서 3이다.

근거: [[01-big-picture]] §3 · [[05-fold-determinism]] §4

---

## 4. 설계 전제가 어긋난 것

### H6 · 슬롯은 이벤트로 태어나지 않는다

[[ADR-002-selective-event-sourcing-scope]](`Proposed`)는 timetable을 "이벤트를 재생해 상태를 도출하는" ES 컨텍스트로 분류했다.

그런데 슬롯이 처음 생기는 경로는 배치가 DB에 행을 INSERT하는 것뿐이다(`batch-module/src/main/kotlin/com/reservation/batch/timetable/step/processor/TimeTableItemProcessor.kt`). 최초 상태 `AVAILABLE`을 만드는 이벤트가 없으므로, 이벤트 재생만으로는 슬롯의 존재 자체를 되살릴 수 없다. `HELD` 이후의 모든 전이는 이벤트로 완전히 복원된다.

**정할 것**: "생성은 배치가 하고 그 이후만 ES로 다룬다"가 의도된 설계인지, 아니면 고쳐야 할 결함인지.

근거: [[05-fold-determinism]] §3.1

---

## 5. 받는 쪽이 불분명한 것

### H5 · 환불 완료 이벤트의 소비자

[[ADR-015-payment-acl-boundary]](`Proposed`)는 payment가 외부에 노출하는 이벤트 3개 중 하나로 `PaymentRefunded`를 명시한다. 그런데 [[DESIGN-007-consistency-and-sagas]](`Accepted`) §4.4의 시퀀스 6개 어디에도 이 이벤트가 다른 컨텍스트로 향하는 화살표가 없다 — payment 자기 내부 주석으로만 등장한다.

**정할 것**: 받는 컨텍스트가 실제로 있는지. 없으면 노출 목록에서 빼야 하는지.

근거: [[04-policies-and-choreography]] §2.2

---

## 6. V1에서 넘어온 미결 묶음

### Q7 · 빅픽처만 훑은 4개 컨텍스트의 승계 미결

`schedule` · `restaurant` · `user` · `authenticate`는 페이로드까지 닫지 않았고, 분류도 Phase 7-6 재실시 전제로 미실시다. 그 과정에서 확인만 하고 넘긴 것들:

| 항목 | 무엇이 미결인가 |
|---|---|
| `GenerateMonthlySlots` | 이벤트로 전달하는지, 직접 호출인지 |
| `DeactivateSchedule` ↔ `RestaurantDeactivated` | schedule이 restaurant 비활성화를 구독하는지 |
| `User.password` / `Authenticate.password` | 두 곳에 나뉜 비밀번호를 어떻게 동기화하는지 |
| 계정 잠금 타이머 | 잠금 중 재시도가 타이머를 연장하는 V1 동작을 V2에서 유지할지 |
| `ReservationStatus` | V1 코드에는 `RESERVED`·`CANCELLED` 둘인데 `docs/v2/domain/01-reservation.md` §1은 "RESERVED 하나뿐"이라 적었다 |

근거: [[01-big-picture]] §1·§4·§5·§6

---

## 7. 나머지

### Q2 · ADR 승인 대기

`docs/v2/adr/ADR-0*.md` 26건이 전부 `Proposed`다. `docs/v2/00-status-and-plan.md` §4가 "ADR `Proposed → Accepted` 전환 — 사용자 권한 (미착수)"를 열린 항목으로 두고 있다. 문서 결함이 아니라 권한이 아직 행사되지 않은 상태다.

### Q5 · Phase 7-1 이관 준비

[[06-internal-vs-integration]]이 남긴 것 중 (a) `SeatReleased` 이름 충돌은 H1으로 닫혔다. 남은 것:

- 통합 이벤트 페이로드에서 어느 필드를 덜어낼지 — `ReservationCreated` · `ReservationCancelled` ([[06-internal-vs-integration]] §6.1)
- 이관 후보 목록([[06-internal-vs-integration]] §6) 자체를 언제 확정할지

### Q6 · `category` 도메인 문서가 없다

`docs/v2/domain/`에 `08`번이 비어 있다 — `07-menu.md`와 `09-company.md`만 있고 `category` 문서가 없다. `docs/v2/00-status-and-plan.md` §6.3 Phase 7-6 순서 7(`menu`·`category`·`company` read-only 마이그레이션)의 입력이 빠진 셈이다.

**정할 것**: 문서를 쓸지, 우선순위를 어디에 둘지.

---

## 8. 이 문서가 하지 않는 것

`docs/v2/00-status-and-plan.md` §4와 `docs/v2/modules/13-phase7-checklist.md` 7-1 체크박스는 이 카탈로그 완성을 계기로 갱신될 수 있다. **이 문서는 그 갱신을 하지 않는다.** §1의 미결이 닫힌 뒤의 별도 작업이다.

이 문서를 포함한 `docs/v2/event-storming/` 전체의 자기 상태는 `초안` 하나다. 위에서 인용한 출처 상태(`Proposed`·`Accepted`)는 원문 그대로 적었다 — [[_conventions]] §1의 두 축 분리 원칙.
