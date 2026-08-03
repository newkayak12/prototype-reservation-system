# 재검토 안건 — hotspot · 미결 질문

- **상태**: 초안
- **작성일**: 2026-07-29 (결정 반영 2026-07-30 · 2026-07-31)
- **무엇인가**: [[00-index]] §0이 B(재검토 개방)를 택했으므로, 아래 항목이 재검토 세션의 **안건 목록**이다.
- **H와 Q의 차이**: `H`는 문서·코드 사이에 **이미 벌어진 어긋남**이다(이 카탈로그가 해소하지 않고 사실로 남긴다). `Q`는 카탈로그가 **답을 낼 수 없어 넘기는 질문**이다.

---

## 1. 결정 현황 (2026-07-30)

| 상태 | 항목 |
|---|---|
| 결정됨 | **Q1** 명명 방침 = B · **H1** 타임아웃 소유권 `timetable`→`reservation` 이전으로 dissolve(2026-07-31, 2026-07-30 이름 분리 결정 대체) · **Q3** 슬롯 생성 이벤트 채택(A안, 2026-07-31) · **H3** 슬롯 생성 이벤트 이름 = `SlotProvisioned`(2026-07-31) · **H6** 슬롯 생성 갭 종결(2026-07-31) · **H2** 환불 트리거 = `RefundRequired` 채택(2026-08-01) · **H5** `PaymentRefunded` 노출 유지·소비자 없음(2026-08-01) · **Q4** 재활성화 이벤트 신설 안 함·비활성화=최종 상태(2026-08-01) · **Q6** category=lookup, ES 대상 아님(2026-08-01) · **H4** 매장 정보 변경 = 파트별 세부 이벤트 분할(2026-08-01) |
| 당장 결정 불가 | **Q2 · Q5 · Q7** |

"당장 결정 불가"는 사용자가 2026-07-30에 명시했다. 재촉 대상이 아니다.

**2026-08-01 갱신**: 결정 대기였던 **H2·H5·Q4·Q6**를 결정하고, 보류였던 **H4**의 선행 조건(이벤트 범위)을 확정해 함께 닫았다. 남은 **Q2·Q5·Q7**은 결정 안건이 아니라 실행/비준 단계다 — **Q2**는 ADR `Proposed→Accepted` 비준(사용자 권한), **Q5**는 Phase 7-1 이관 준비, **Q7**은 Phase 7-6 승계. 즉 이벤트 스토밍의 결정 백로그는 비었고, 남은 것은 "ADR 읽고 비준"과 구현 단계뿐이다.

**2026-07-31 갱신**: 사용자가 슬롯 생성 관련 3건(**Q3·H3·H6**)을 "당장 결정 불가"에서 꺼내 결정했다. 상세는 각 항목의 `사용자 판단 (2026-07-31)` 참조. 이 결정의 카탈로그 전파는 완료했다 — 아래 **전파 완료(2026-07-31)** 참조.

### 전파 완료 (2026-07-31)

Q3·H3·H6 결정을 슬롯 클러스터 범위로 카탈로그에 전파했다(H1의 전면 배칭과 달리, 슬롯 3건은 자기완결 클러스터라 떼어내도 표 재작업이 거의 없어 먼저 반영):

- **[[02-design-timetable]] §3**: `SlotProvisioned` 행 — 트리거 `배치(이벤트 발행)`, 근거·원문 상태에 07 Q3·H3 채택 명시, 태그 `V2 도메인 문서 근거`로 갱신.
- **[[05-fold-determinism]] §3.1**: `status`(최초값 `AVAILABLE`) 행 `갭 있음 → 복원 가능`, 전체 판정 `갭 있음(부분적) → 복원 가능`, 구조적 함의 문단을 종결 서술로 재기술.
- **[[06-internal-vs-integration]] §3·§5·§7**: 분류표 `SlotProvisioned` 행 태그 갱신 · §5 `TimeTableCreated` 어긋남 **해소** · §7 미결 목록에서 종결 처리.

태그 결정: `SlotProvisioned`·`TimeTableCreated` 행의 태그를 `V2 도메인 문서 근거`([[07-hotspots-and-open-questions]] 결정 링크 동반)로 확정했다 — 07 결정이 이 이벤트의 v2 문서 근거이며, 태그 3어휘([[_conventions]] §2.1)는 증거 출처 축이고 결정 사실의 진실 원천은 이 문서(07)다. `modules/02-contract-module.md` §4 예시 주석의 `TimeTableCreated → SlotProvisioned` 정정은 이벤트 스토밍 범위 밖이라 별도 전파로 남긴다.

---

## 2. 결정된 것

### H1 · 좌석 해제 이벤트 — 타임아웃 소유권 이전으로 dissolve

좌석이 풀리는 경우가 둘이다. 예약이 실패·취소·노쇼로 풀리는 것과, 결제 대기 시간(TTL)이 지나 풀리는 것. 지금까지 둘 다 `SeatReleased` 하나로 발행했고, 앞은 timetable 내부 이벤트·뒤는 reservation이 구독하는 통합 이벤트라 한 이름이 두 분류에 걸쳐 있었다.

**결정 (2026-07-31 — 2026-07-30 "이름 분리(`SeatReleased`/`SeatExpired`)" 결정을 대체)**: 이름을 나누지 않는다. 대신 **타임아웃 소유권을 `timetable`에서 `reservation`으로 옮긴다**([[ADR-008-saga-orchestration-vs-choreography]] 결정 블록 개정 · [[DESIGN-007-consistency-and-sagas]] §4.4 시퀀스 개정). `reservation`이 결제 대기 만료를 자기 스케줄러로 판정해 `ExpireReservation`으로 `PENDING → EXPIRED` 후 `ReservationExpired`를 발행하고, `timetable`은 이를 구독해(취소·실패·노쇼와 동일 경로) `ReleaseSeat → SeatReleased`한다.

**결과**: timetable의 `ExpireSeat` 커맨드와 TTL 유래 해제(5b)가 사라진다. `SeatReleased`는 이제 **항상 timetable 내부 단일 이벤트**(cross-context 소비자 없음)이고 `SeatExpired`도 `cause` 필드도 필요 없다. "한 이름이 두 분류에 걸침" 문제가 이름 분리가 아니라 **경로 통일로 소멸**한다.

**왜 분리 대신 소유권 이전인가**: 취소·결제실패·노쇼 3개 해제 경로가 이미 reservation-선행(reservation이 자기 상태를 먼저 전이하고 timetable이 뒤따라 해제)인데 타임아웃만 timetable-선행이라 "좌석은 풀렸는데 예약은 아직 `PENDING`"인 창이 생기고, 그 창에서 지연 `PaymentConfirmed`가 아직 `PENDING`인 만료 예약을 확정시킬 수 있었다(가드는 `EXPIRED` 이후에만 작동). 타임아웃을 reservation 소유로 두면 이 비대칭과 레이스 창까지 함께 사라진다.

근거: [[ADR-008-saga-orchestration-vs-choreography]] 결정 블록 · [[DESIGN-007-consistency-and-sagas]] §4.4 · [[02-design-timetable]] §2·§4 · [[06-internal-vs-integration]] §3·§4

### 전파 대기

이 결정은 SSOT 둘([[ADR-008-saga-orchestration-vs-choreography]]·[[DESIGN-007-consistency-and-sagas]])에는 반영됐고, 이벤트 스토밍 카탈로그에는 아직이다. 반영 범위:

- **[[02-design-timetable]]**: §2 상태머신 5b(`ExpireSeat`/TTL) 행 제거·5a `ReleaseSeat` 트리거에 `ReservationExpired` 추가 · §3 카탈로그 `SeatReleased`(5b) 행 제거·단일 내부로 · §4 재기술 · §5 불변식#3(HELD TTL) 재기술 · §2.1 조사 정합.
- **[[03-design-reservation]]**: §2 전이#4 `EXPIRED` 트리거를 timetable 이벤트 → **reservation 스케줄러**로.
- **[[04-policies-and-choreography]]**: P7(TTL)·P8을 reservation-선행 단일 경로로.
- **[[06-internal-vs-integration]]**: §3 `SeatReleased`(5b, 통합) 제거·단일 내부로 · §4·§6 재기술.
- **[[01-big-picture]] · [[05-fold-determinism]]**: `SeatReleased`/`ExpireSeat`/TTL 언급 정합.

이 중 **구조(경로·소유권) 변경은 이 결정으로 확정**이며, 순수 명명(다른 이벤트들)은 §1의 "당장 결정 불가" 항목이 닫힌 뒤 함께 정리한다. 배치 규칙은 §1 "전파 대기(2026-07-31)"와 동일.

---

## 3. 이름을 정해야 하는 것

### H2 · 환불 요청 이벤트

예약이 만료된 뒤 결제가 늦게 성공하면 확정을 거부하고 환불해야 한다. `docs/v2/domain/01-reservation.md` §2 불변식#11은 "이 환불 트리거를 나타낼 이벤트가 표에 없다"고 비워 뒀다. 그런데 [[ADR-008-saga-orchestration-vs-choreography]](`Proposed`)는 이미 `RefundRequired`라는 이름으로 결정 본문에 서술해 놨다.

**정할 것**: 그 이름을 채택할지, `domain/01-reservation.md`의 이벤트 표를 채울지.

**사용자 판단 (2026-08-01)**: **`RefundRequired` 채택.** `reservation`이 paid-after-expiry 가드(`handle(ConfirmReservation)`)에서 확정을 거부하고 발행 → `payment`가 환불 처리([[ADR-008-saga-orchestration-vs-choreography]] §44·§62). H1 타임아웃 소유권 이전과 동일 메커니즘이라 정합. `domain/01-reservation.md` §2 #11 미결 노트는 **해소 처리 완료**(정확한 페이로드는 reservation 구현 모델링에서 확정). ADR-015가 노출하는 `PaymentRefunded`(결과 이벤트)와는 별개 — 이쪽은 트리거다.

근거: [[03-design-reservation]] §4 · [[ADR-008-saga-orchestration-vs-choreography]] §44·§62

### H3 · 슬롯 생성 이벤트

`docs/v2/modules/02-contract-module.md` §4의 패키지 구조 예시 주석에 `TimeTableCreated`가 있다. 그런데 `docs/v2/domain/02-timetable.md`와 V1 코드 어디에도 그 이름의 근거가 없다. 카탈로그가 내놓은 후보는 `SlotProvisioned` 하나이고, 그것도 근거 없는 제안이다.

**정할 것**: Phase 7-1이 만들 클래스 이름.

**사용자 판단 (2026-07-31)**: `SlotProvisioned`로 확정. `docs/v2/modules/02-contract-module.md` §4 예시 주석의 `TimeTableCreated`는 폐기한다. 전파(catalog·modules 예시 주석 정정)는 §1 "전파 대기(2026-07-31)"에 따라 일괄 처리한다.

근거: [[00-index]] §3 · [[01-big-picture]] §2 · [[06-internal-vs-integration]] §5

### H4 · 매장 정보 변경 이벤트의 범위

카탈로그는 `RestaurantInfoUpdated`, `docs/v2/modules/02-contract-module.md` §4 예시는 `RestaurantRenamed`다. 같은 개념인지 이름만으로는 알 수 없다.

**사용자 판단 (2026-07-30)**: `RestaurantInfoUpdated`의 범위를 어디까지로 두느냐에 따라 개명이 그 안에 포함될 수도, 별개 이벤트일 수도 있다. **범위를 먼저 정해야 답이 나온다.** 범위 확정은 보류.

**사용자 판단 (2026-08-01, 선행 조건 해소)**: 매장 정보 변경은 **파트별 세부 이벤트로 분할한다** — [[01-big-picture]] §3 후보표대로 `RestaurantInfoUpdated`(소개·연락처) / `RestaurantLocationUpdated`(주소·좌표) / `RestaurantRoutineUpdated`(영업시간) / `RestaurantPhotosUpdated`(사진) / `RestaurantCategoriesUpdated`(태그·국적·요리). `RestaurantInfoUpdated`는 catch-all 우산이 아니라 소개·연락처 파트 전용이다. modules/02 예시의 `RestaurantRenamed`는 **별도 우산 이벤트를 신설하는 게 아니라** 이 파트 분할 체계 안에서 다룬다 — 상호(개명)가 어느 파트 필드에 속하는지의 정밀 매핑은 restaurant 페이로드가 닫히는 **Phase 7-6**에서 확정한다(Q7). 범위 축은 여기서 "파트별 분할"로 확정됐으므로 보류를 해제한다.

근거: [[01-big-picture]] §3

### Q3 · 슬롯 생성 이벤트를 만들 것인가

H3은 "이름을 뭐로 할지"이고, 이것은 "그 이벤트가 존재해야 하는지"다. `SlotProvisioned`는 사건 자체가 어느 문서에도 없어 카탈로그가 제안으로만 올렸다.

**정할 것**: 채택 여부. 채택하면 H6이 닫힌다. 미채택이면 슬롯의 최초 상태는 계속 배치 산출물로 남는다.

**사용자 판단 (2026-07-31)**: **채택한다(A안).** 배치가 슬롯을 DB에 직접 INSERT하는 대신 `SlotProvisioned` 이벤트를 발행하고, 슬롯의 최초 존재를 이 이벤트에서 도출한다. 이로써 H6이 닫힌다. 이벤트 볼륨(레스토랑×날짜×시간×테이블 조합만큼의 생성 이벤트)은 스냅샷/아카이빙([[DESIGN-009-event-store-lifecycle]])으로 관리하는 별개 이슈로 남긴다. v2 전면 리빌드 전제이므로 기존 배치 코드 재사용 제약은 없다.

근거: [[02-design-timetable]] §3 · [[05-fold-determinism]] §4

### Q4 · 매장 재활성화 이벤트를 만들 것인가

`docs/v2/domain/03-restaurant.md` §2가 "비활성화가 최종 상태인지"를 스스로 미결로 남겼다. 최종 상태가 아니라면 재활성화 이벤트가 필요하다.

**정할 것**: `RestaurantReactivated` 신설 여부. restaurant는 Phase 7-6 순서 3이다.

**사용자 판단 (2026-08-01)**: **신설하지 않는다.** 비활성화를 **최종 상태**로 확정한다 — 범위를 늘리지 않는다. `docs/v2/domain/03-restaurant.md` §2가 남긴 "비활성화가 최종 상태인지" 미결은 "최종 상태"로 닫힌다. 재활성화 요구가 실제로 관측되면 그때 별도 안건으로 연다.

근거: [[01-big-picture]] §3 · [[05-fold-determinism]] §4

---

## 4. 설계 전제가 어긋난 것

### H6 · 슬롯은 이벤트로 태어나지 않는다

[[ADR-002-selective-event-sourcing-scope]](`Proposed`)는 timetable을 "이벤트를 재생해 상태를 도출하는" ES 컨텍스트로 분류했다.

그런데 슬롯이 처음 생기는 경로는 배치가 DB에 행을 INSERT하는 것뿐이다(`batch-module/src/main/kotlin/com/reservation/batch/timetable/step/processor/TimeTableItemProcessor.kt`). 최초 상태 `AVAILABLE`을 만드는 이벤트가 없으므로, 이벤트 재생만으로는 슬롯의 존재 자체를 되살릴 수 없다. `HELD` 이후의 모든 전이는 이벤트로 완전히 복원된다.

**정할 것**: "생성은 배치가 하고 그 이후만 ES로 다룬다"가 의도된 설계인지, 아니면 고쳐야 할 결함인지.

**사용자 판단 (2026-07-31)**: **고쳐야 할 결함으로 본다.** Q3 채택으로 슬롯 생성을 `SlotProvisioned` 이벤트로 발행한다. 생성 이벤트를 재생하면 최초 상태 `AVAILABLE`까지 이벤트 스트림만으로 복원되어, [[ADR-002-selective-event-sourcing-scope]](`Proposed`)가 timetable에 부여한 "이벤트 재생으로 상태 도출" 전제와 정합한다. 배치는 사라지는 게 아니라 INSERT 대신 이벤트를 발행하는 발행자로 바뀐다. **H6 종결.**

근거: [[05-fold-determinism]] §3.1

---

## 5. 받는 쪽이 불분명한 것

### H5 · 환불 완료 이벤트의 소비자

[[ADR-015-payment-acl-boundary]](`Proposed`)는 payment가 외부에 노출하는 이벤트 3개 중 하나로 `PaymentRefunded`를 명시한다. 그런데 [[DESIGN-007-consistency-and-sagas]](`Accepted`) §4.4의 시퀀스 6개 어디에도 이 이벤트가 다른 컨텍스트로 향하는 화살표가 없다 — payment 자기 내부 주석으로만 등장한다.

**정할 것**: 받는 컨텍스트가 실제로 있는지. 없으면 노출 목록에서 빼야 하는지.

**사용자 판단 (2026-08-01)**: **노출 목록에 유지한다 — 지금 소비자는 없다.** [[ADR-015-payment-acl-boundary]] §46이 payment 노출 이벤트를 `PaymentConfirmed`/`PaymentFailed`/`PaymentRefunded` 3개로 **동결**했으므로 표면에서 빼지 않는다. 현재 이 이벤트를 구독하는 컨텍스트가 없다는 사실([[DESIGN-007-consistency-and-sagas]] §4.4에 도착 화살표 없음)은 결함이 아니라 **"만들어두되 아직 쓰지 않음"** 상태로 명시한다. 소비자가 생기면 그때 policy·시퀀스에 화살표를 잇는다. ADR-015 수정 불필요.

근거: [[04-policies-and-choreography]] §2.2 · [[ADR-015-payment-acl-boundary]] §46

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

**사용자 판단 (2026-08-01)**: category는 [[ADR-002-selective-event-sourcing-scope]] 분류상 **현행/lookup(현행 유지)** — 비-ES조차 아니고 되짚을 이력이 없어 ES·이벤트·도메인 모델링 대상이 아니다. 따라서 **event-storming 안건에서는 종결**한다. 빠진 `domain/08-category.md`는 Phase 7-6 순서 7(`menu`·`category`·`company` read-only 마이그레이션)의 입력일 뿐이므로, 필요하면 그 시점에 **최소 범위(read 스키마 수준)**로 쓴다 — 지금 우선순위는 두지 않는다.

---

## 8. 이 문서가 하지 않는 것

`docs/v2/00-status-and-plan.md` §4와 `docs/v2/modules/13-phase7-checklist.md` 7-1 체크박스는 이 카탈로그 완성을 계기로 갱신될 수 있다. **이 문서는 그 갱신을 하지 않는다.** §1의 미결이 닫힌 뒤의 별도 작업이다.

이 문서를 포함한 `docs/v2/event-storming/` 전체의 자기 상태는 `초안` 하나다. 위에서 인용한 출처 상태(`Proposed`·`Accepted`)는 원문 그대로 적었다 — [[_conventions]] §1의 두 축 분리 원칙.
