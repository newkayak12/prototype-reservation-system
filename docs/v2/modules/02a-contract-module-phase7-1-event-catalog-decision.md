# 02a · contract-module Phase 7-1 — timetable 통합 이벤트 카탈로그 + 직렬화 전략 (결정)

> 허브: [[00-module-index]] | 대상 모듈: [[02-contract-module]] §5·§6 · [[13-phase7-checklist]] §7-1
> 1차 근거: [[06-internal-vs-integration]] §3·§4·§6 · [[07-hotspots-and-open-questions]] H3 · [[02-design-timetable]] §3
> 성격: **결정 산출물, 코드 없음.** contract-module에 실제 클래스를 만드는 것은 이 문서가 아니라 후속 구현 서브골이다 — 이 문서는 그 구현이 따라야 할 결정을 확정한다.

---

## 0. 요약

- timetable 컨텍스트에서 contract-module로 올릴 통합 이벤트는 **`SeatHeld` 단 하나**다.
- 요청서/계획서가 전제한 `TimeTableCreated`는 **존재하지 않는다** — 이미 폐기된 이름이고, 그 자리를 대신하는 `SlotProvisioned`조차 **내부 이벤트**라 애초에 contract-module 대상이 아니다. 이 어긋남을 아래 §2에서 명시적으로 기록한다.
- `SeatHeld` 페이로드는 카탈로그 10개 필드 중 **3개만 채택**(`reservationId`, `userId`, `restaurantId`)하고 나머지 7개(`slotId`, `date`, `startTime`, `endTime`, `tableNumber`, `heldAt`, `holdExpiresAt`)는 제외한다. 근거는 §4.
- 직렬화: JSON + `eventType` 문자열 태그, `jackson-module-kotlin`, 어노테이션 기반 다형성 미사용, 복원 레지스트리는 `command-application` 소유. 근거는 §6.
- `LongParameterList`: V1 선례(`@Suppress("LongParameterList")` 클래스 레벨)를 따르고 `detekt.yaml`은 건드리지 않는다. 근거는 §7.

---

## 1. 분류 1차 근거 — [[06-internal-vs-integration]] §3

[[06-internal-vs-integration]] §3 분류표에서 `timetable` 컨텍스트에 속한 6개 이벤트 중 분류가 **통합 이벤트**인 것은 다음 한 행뿐이다.

| 카탈로그 명명 | 분류 | 소비 컨텍스트 |
|---|---|---|
| `SeatHeld` | 통합 이벤트 | `payment`(범위 밖) — P2: "`SeatHeld`(timetable) → payment가 결제 처리 개시" |

나머지 5개(`SlotProvisioned`, `SeatConfirmed`, `SeatReleased`, `SlotBlocked`, `SlotUnblocked`)는 모두 **내부 도메인 이벤트**로 판정돼 있다 — [[04-policies-and-choreography]] §1·§3의 코레오치오그래피 표·컨텍스트 횡단 그래프 어디에도 트리거·결과로 등장하지 않는다는 것이 공통 근거다.

특히 `SeatReleased`는 과거(타임아웃 소유권 이전 전) 일부가 통합 이벤트로 분류될 뻔했던 이력이 있으나, [[06-internal-vs-integration]] §4·§6이 이를 다음과 같이 **명시적으로 종결**했다:

> "Phase 7-1은 `SeatReleased`를 내부 이벤트로만 다루면 되고 계약 모듈로 올릴 필요가 없다." — [[06-internal-vs-integration]] §4

> [[06-internal-vs-integration]] §6의 이관 후보표에도 `SeatReleased`는 없다 — 타임아웃 소유권 이전(H1) 이후 단일 내부 이벤트로 수렴했기 때문이다.

**결론**: timetable → contract-module 이관 대상은 `SeatHeld` 1개, 그 이상도 이하도 아니다.

---

## 2. 어긋남 기록 — `TimeTableCreated`는 존재하지 않는다

요청서·계획서는 "timetable 통합 이벤트"에 `TimeTableCreated`가 포함된다는 전제를 깔고 있었다(이 전제의 출처는 [[02-contract-module]] §4 구조 예시 주석 `timetable/ # TimeTableCreated, SeatHeld, SeatReleased ...`). 이 전제는 **두 겹으로 틀렸다**.

1. **이름이 틀렸다.** [[07-hotspots-and-open-questions]] H3(2026-07-31, 사용자 판단)이 슬롯 생성 이벤트의 이름을 `SlotProvisioned`로 확정하고 `TimeTableCreated`를 **폐기**했다.
   > "`SlotProvisioned`로 확정. `docs/v2/modules/02-contract-module.md` §4 예시 주석의 `TimeTableCreated`는 폐기한다." — [[07-hotspots-and-open-questions]] H3, 2026-07-31
2. **폐기된 이름을 올바른 이름(`SlotProvisioned`)으로 바꿔 넣어도 여전히 틀렸다.** `SlotProvisioned`는 [[06-internal-vs-integration]] §3에서 **내부 도메인 이벤트**로 분류돼 있다 — 슬롯 생성은 배치 발행이고 [[04-policies-and-choreography]] 표·그래프에 트리거/결과로 등장하지 않는다. 내부 이벤트는 ADR-021 규칙("공유 계약 모듈은 얇은 통합 이벤트만 담는다")상 애초에 contract-module에 올라갈 수 없다.

**따라서 결정**: `SlotProvisioned`를 포함한 timetable의 어떤 슬롯 생성 이벤트도 contract-module `timetable/` 패키지에 들어가지 않는다. 이 컨텍스트의 contract-module 산출물은 `SeatHeld` 하나뿐이다.

**파생 발견 (구현 아님, 기록만)**: [[02-contract-module]] §4 구조 예시의 주석은 이제 이중으로 부정확하다 — `TimeTableCreated`는 폐기된 이름이고, 그 자리에 `SlotProvisioned`를 넣어도 내부 이벤트이므로 예시 자체가 성립하지 않는다. 같은 예시 줄의 `SeatReleased`도 §1의 근거로 내부 이벤트라 예시에서 빠져야 한다. 이 문서는 이 부정확함을 **보고만** 하고 고치지 않는다 — [[02-contract-module]] §4/§5/§6 본문 갱신은 목표 (f) 단계(별도 서브골)의 몫이다.

---

## 3. event-carried × 얇은 통합 이벤트 — 화해 규칙

RFC-029(event-carried, 내부 이벤트 페이로드 정책)와 ADR-021·[[06-internal-vs-integration]] §6.1(얇은 통합 이벤트, published language)은 서로 다른 축의 규칙이라 조문 자체는 충돌하지 않지만, 통합 이벤트 페이로드를 설계할 때 실제로 적용할 단일 화해 규칙을 아래처럼 명문화한다.

> **화해 규칙**: 발생 시점 값을 싣되(소비 측이 최신 상태를 조회하지 않음), 소비자가 계약상 실제로 쓰는 필드만 싣는다(애그리거트 전체 덤프 금지).

즉 "무엇을 담는가"(RFC-029, event-carried — 조회가 아니라 그 시점 값)와 "얼마나 담는가"(ADR-021, 얇게 — 소비자가 실제로 쓰는 것만)는 서로 다른 질문이고, 이 문서의 §4는 후자를 판정한다.

---

## 4. `SeatHeld` 페이로드 필드별 채택/제외

기준 카탈로그: [[02-design-timetable]] §3 `SeatHeld` 행 — `slotId, reservationId, userId, restaurantId, date, startTime, endTime, tableNumber, heldAt, holdExpiresAt` (10개).
전제: `AbstractEvent` 봉투가 이미 `eventId, aggregateType, aggregateId, sequenceNo, eventType, eventVersion, occurredAt, correlationId, causationId, traceparent`(10개)를 갖고, `SeatHeld`의 애그리거트는 Slot이므로 `aggregateId = slotId`다([[02-design-timetable]] §1 "애그리거트 — Slot").

| 필드 | 판정 | 한 줄 사유 |
|---|---|---|
| `slotId` | **제외** | 봉투 `aggregateId`가 이미 Slot 애그리거트의 식별자를 담는다 — 페이로드에 다시 넣으면 봉투/페이로드 중복이다 |
| `reservationId` | **채택** | payment가 `PaymentConfirmed`/`PaymentFailed`로 되돌아갈 대상(reservation)을 알아야 한다(P3/P5) — 봉투 어디에도 없는 필수 상관관계 키 |
| `userId` | **채택** | payment가 PG에 결제를 요청하려면(ADR-015) 결제 대상(누구에게 청구하는지)을 알아야 한다 — 봉투에 없음, event-carried로 그 시점 사용자 식별자를 실어야 함 |
| `restaurantId` | **채택** | payment가 정산 대상 가맹점을 식별해야 한다("결제 처리 개시"가 어느 매장 앞으로 정산되는지) — 봉투에 없음 |
| `date` | **제외** | payment 소비 근거 없음 — ADR-015는 필드 수준 상세가 없고, payment 도메인 문서 자체가 없다([[04-policies-and-choreography]] P2 각주 "payment 도메인 문서 없음"). 근거 없이 실으면 애그리거트 부분 덤프가 된다 |
| `startTime` | **제외** | 위와 동일 사유 |
| `endTime` | **제외** | 위와 동일 사유 |
| `tableNumber` | **제외** | 위와 동일 사유 |
| `heldAt` | **제외** | 봉투 `occurredAt`이 "이 사실이 발생한 시점"을 이미 담는다 — `SeatHeld`에서 그 시점은 곧 hold가 성사된 시점이라 `occurredAt`과 값이 항상 같다. 봉투/페이로드 중복 |
| `holdExpiresAt` | **제외** | §5에서 별도 판정(타임아웃 소유권 이전 이후 데이터 출처 자체가 없음) |

**채택 결과 (3개)**: `reservationId`, `userId`, `restaurantId`.

### 4.1 이 판정에 대한 devil's advocate

**Position**: `SeatHeld` 페이로드를 10개 중 3개(`reservationId`, `userId`, `restaurantId`)로 얇힌다.

**숨은 가정**:
1. payment가 실제로 쓰는 필드는 "결제 처리 개시"라는 한 문장(P2)에서 유추 가능한 만큼뿐이다 — 문서화되지 않은 실제 소비까지 우리가 안다고 가정한다.
2. 나중에 payment 도메인 문서가 닫혔을 때 필드가 부족하면 additive-only 진화(ADR-021)로 값싸게 메울 수 있다.
3. `date`/`startTime`/`endTime`/`tableNumber`는 정말로 receipt·감사 목적에도 안 쓰인다.

**반론 1 [assumption · medium]** — *payment가 영수증/명세서에 예약 일시·테이블을 표시해야 한다면 지금 3개 필드로는 부족하다.* 결제 실패 알림이나 영수증에 "언제·어느 테이블"이 흔히 들어간다. 이 경우 최초 배포 직후 바로 필드 추가 PR이 필요해져, "얇게 시작"의 실익이 "즉시 v2로 증분"의 비용에 잠식된다.
선례: no clear precedent — speculative concern (payment 도메인 문서가 없어 검증 불가).

**반론 2 [structural · low]** — *additive-only가 "값싸다"는 가정 2가 항상 참은 아니다.* 필드 추가 자체는 컴파일 관점에서 싸지만, 이미 배포된 컨슈머가 새 optional 필드를 무시하도록 짜여 있는지는 별도로 보장돼야 한다(ADR-021 §확인 항목 자체가 "코드 리뷰 체크리스트"로만 강제되는 사회적 규약이라고 명시). 필드 추가 = 무비용이라는 인상은 과장이다.
선례: no clear precedent.

**핵심 취약점**: 이 판정은 "payment가 실제로 쓰는 필드"를 판정할 근거 문서(payment 도메인 문서) 자체가 없는 상태에서 내려진다 — 채택/제외 모두 정황 추론(P2 한 문장, ADR-015 흐름 서술)에 기댈 뿐, payment 쪽 1차 근거가 없다. 즉 이 §4 판정 전체가 "payment 도메인이 아직 스코프 밖"이라는 구조적 제약 위에 서 있고, 그 제약이 풀리기 전까지는 어느 쪽으로 판정해도 추측을 완전히 벗어날 수 없다.

**가역성**: **혼합, 그러나 채택 쪽으로 기울여도 안전.** ADR-021의 additive-only 규율상 필드는 **추가는 가능, 삭제·rename은 불가**다 — 즉 지금 제외한 필드를 나중에 추가하는 것(reversible, one-way 아님)은 지금 채택한 필드를 나중에 빼는 것(불가능, 영구 계약)보다 훨씬 싸다. 이 비대칭이 "불확실하면 제외 쪽으로 판정한다"는 이 문서의 기본 태도를 정당화한다 — §4의 3개 채택은 P2/ADR-015가 실제로 언급한 흐름(결제 대상 식별·정산 대상 식별·reservation 상관관계)에 최소한으로 근거가 있는 것들이고, 근거가 추론조차 안 되는 4개 서술 필드는 제외했다.

---

## 5. `holdExpiresAt` 존치 여부 — 명시 판정

[[02-design-timetable]] §3의 `SeatHeld` 행은 이 필드를 다음과 같이 미결로 남겼다:

> "타임아웃 소유권 이전([[ADR-008-saga-orchestration-vs-choreography]] 개정) 후 만료 deadline은 reservation 소유이므로 `holdExpiresAt` 존치 여부는 페이로드 확정 시 판단(§4)"

**판정: 제외한다.** 근거:

1. **데이터 출처가 없다.** [[02-design-timetable]] §5 불변식 #3은 "timetable은 자체 TTL을 갖지 않고"라고 명시한다 — Slot 애그리거트 자체가 만료 시각을 상태로 갖지 않는다.
2. **상류 커맨드에도 값이 없다.** `SeatHeld`를 만드는 `HoldSeat`는 reservation의 `ReservationCreated`가 트리거한다([[02-design-timetable]] §2 전이#2). [[03-design-reservation]] §3의 `ReservationCreated` 페이로드(`reservationId, userId, restaurantId, tableNumber, tableSize, slotId, date, day, startTime, endTime, requestedAt`)에도 만료 시각 필드가 없다 — timetable이 전달받아 되실을 값 자체가 상류에 존재하지 않는다.
3. **넣으려면 timetable이 직접 계산해야 하는데, 그건 H1이 없앤 바로 그 문제를 되살린다.** timetable이 자체적으로 만료 시각을 계산해 `holdExpiresAt`을 채우면, "타임아웃 판정 권한을 reservation으로 일원화한다"는 [[ADR-008-saga-orchestration-vs-choreography]] H1 결정과 정면으로 어긋난다 — timetable이 다시 시계를 갖는 셈이 된다.

즉 `holdExpiresAt`은 §4의 일반 원칙(소비자 근거 부족 시 제외)과는 별개로, **애초에 채울 값이 없어서** 제외한다. payment가 만료 시각이 필요하다면 그 값의 권위 있는 출처는 reservation이고(reservation이 소유), timetable의 `SeatHeld`가 대신 실어줄 수 없다.

---

## 6. 직렬화 전략 확정

- **포맷**: JSON.
- **타입 판별**: `eventType` 문자열 필드(봉투에 이미 존재, `AbstractEvent.eventType`) — FQCN이 아니라 논리 이름 태그. [[ADR-010-event-schema-evolution]] 결정 #2("eventType↔클래스 = 명시 등록 매핑, 클래스명 변경과 분리")를 그대로 따른다.
- **Kotlin 지원**: `jackson-module-kotlin` — data class 주 생성자 바인딩·`null` 안전성을 어노테이션 없이 지원한다. contract-module `build.gradle.kts`에 라이브러리 추가는 이 결정의 실행 항목이지 이 문서의 산출물이 아니다(코드 없음 — 후속 서브골).
- **다형성 처리**: **어노테이션 기반 다형성(`@JsonTypeInfo`/`@JsonSubTypes`) 미사용.** contract-module의 각 이벤트 data class는 순수 데이터 홀더로만 존재하고, "이 JSON을 어느 클래스로 역직렬화할지"의 판단(다형적 디스패치)은 이 모듈이 갖지 않는다. [[ADR-010-event-schema-evolution]] 결정 #2가 `@JsonTypeName` 스캔 방식을 이미 기각했고(클래스 리팩터링이 저장된 이벤트 식별을 깨는 위험), `command-application`이 `eventType → 클래스` 명시 등록 매핑으로 직접 처리한다.
- **복원 레지스트리 소유**: `command-application`. [[DESIGN-019-event-execution-layering]] §6이 "`eventType → DomainEvent` 복원 레지스트리 ... 여기선 'application이 그 복원을 소유한다'만 배정"이라고 명시했고, [[ADR-010-event-schema-evolution]] 결정 #2("eventType↔클래스 = 명시 등록 매핑")가 그 배정의 구현 형태를 정한다. contract-module은 이벤트 **모양**(data class + 봉투)만 정의하고, 그 모양으로 **복원하는 절차**는 소유하지 않는다.
- **contract-module의 역할 경계**: 이 모듈은 (a) `AbstractEvent` 봉투 인터페이스, (b) 각 통합 이벤트의 data class 정의, (c) 직렬화 골든 테스트([[ADR-021-integrated-event-contract-module]] 옵션 B)만 갖는다. `ObjectMapper` 설정(Kotlin 모듈 등록·`Instant` 지원 등)과 `eventType → 클래스` 레지스트리는 `command-application`/발행-소비 경로 쪽 관심사다.

---

## 7. detekt `LongParameterList` 대응 방침

**현황 확인**: `detekt.yaml`에는 `LongParameterList` 규칙에 대한 override가 없다. 루트 `build.gradle.kts`의 `subprojects` 블록이 `detekt { buildUponDefaultConfig = true }`를 설정하므로(contract-module도 이 `subprojects` 블록 적용 대상), detekt 기본 프로파일의 생성자 파라미터 임계값이 그대로 적용된다.

**초과 확인**: `SeatHeld`가 `AbstractEvent`를 구현하는 단일 data class로 만들어질 경우, 봉투 10필드 + §4에서 확정한 페이로드 3필드 = **13개 생성자 파라미터**로 기본 임계값을 반드시 초과한다.

**결정**: `detekt.yaml`을 수정하지 않는다. 대신 V1 선례를 그대로 따른다 — `core-module/src/main/kotlin/com/reservation/timetable/TimeTable.kt`가 이미 이 문제를 클래스 레벨 `@Suppress("LongParameterList")`로 해결해 뒀다(해당 클래스도 생성자 파라미터가 많은 도메인 엔티티다). contract-module의 각 통합 이벤트 data class(`SeatHeld` 포함, 이후 reservation 등 다른 컨텍스트 이벤트도 동일)에 이 패턴을 반복 적용한다.

**이 방침을 선택한 이유**: (1) 전역 override(`detekt.yaml` 수정)는 이벤트 봉투 특유의 구조적 이유(추적 메타 자체가 여러 필드)를 프로젝트 전체 기준 완화로 일반화해 다른 곳의 파라미터 비대를 놓칠 위험을 만든다. (2) 클래스 레벨 `@Suppress`는 "이 특정 클래스는 봉투+페이로드 조합이라 파라미터가 많을 수밖에 없다"는 사유를 그 클래스 자리에서 명시적으로 남긴다 — V1이 이미 같은 논리로 쓴 선례가 있어 팀 관례와도 정합한다. (3) `detekt.yaml`은 `.claude/conventions/boundaries.md`가 정의한 하드 제약은 아니지만, 이 문서의 스코프(결정 산출물)에서 굳이 프로젝트 전역 품질 게이트를 건드릴 근거가 없다.

---

## 8. 이 문서가 남기는 것 (후속 서브골 인터페이스)

- **코드 없음** — `contract-module/src/main/kotlin/com/reservation/contract/event/timetable/SeatHeld.kt`를 실제로 만드는 것, `jackson-module-kotlin` 의존성을 `contract-module/build.gradle.kts`에 추가하는 것, 라운드트립 골든 테스트를 작성하는 것은 모두 후속 서브골의 몫이다.
- **후속 서브골이 그대로 가져다 쓸 결정**:
  - 클래스: `com.reservation.contract.event.timetable.SeatHeld` (패키지는 [[02-contract-module]] §4 구조를 따르되, 그 문서의 예시 주석 자체는 부정확하므로 §4 이름 나열이 아니라 이 문서 §1·§2·§4·§5를 근거로 삼을 것).
  - 필드: 봉투(`AbstractEvent` 10필드, 기존 정의 그대로) + `reservationId: String, userId: String, restaurantId: String` (payload 3필드).
  - `@Suppress("LongParameterList")`를 클래스 레벨에 적용.
  - 직렬화: `jackson-module-kotlin` 등록, 어노테이션 기반 다형성 없음, `eventType` 문자열 태그 필드로 판별(값은 예: `"SeatHeld"`).
  - 복원 레지스트리 코드는 contract-module에 두지 않는다(command-application 소관 — 이 서브골의 스코프 밖).
- **목표 (f) 단계(별도 서브골)에 전달할 정정 사항**: [[02-contract-module]] §4 구조 예시 주석의 `timetable/ # TimeTableCreated, SeatHeld, SeatReleased ...`은 `TimeTableCreated`(폐기, §2)와 `SeatReleased`(내부 이벤트, §1)를 모두 빼고 `SeatHeld`만 남겨야 한다. [[13-phase7-checklist]] §7-1 체크박스 중 "레퍼런스 컨텍스트(timetable·reservation) 통합 이벤트 클래스 정의"·"이벤트 버전/직렬화 전략 확정"·"eventType → 클래스 복원 레지스트리 소유 위치 확정" 3개는 이 문서로 **결정은 끝났으나 코드/문서 반영은 아직**이므로, 실제 클래스·문서 갱신이 끝나기 전에 체크 표시하지 말 것.
