# 03 · command-core (순수 도메인) [신규]

> 허브: [[00-module-index]] | 근거: [[DESIGN-002]] §4.2 · [[DESIGN-003]] §4.1 · [[DESIGN-006]] (애그리거트 설계) · [[DESIGN-019]] §3

## 1. 책임

command-module hexagonal 4층 중 **가장 안쪽**. 순수 도메인.

- 애그리거트 (ES / 비-ES)
- 도메인 이벤트의 **내부 표현**(`DomainEvent`) — core 소유, contract와 무관
- 도메인 서비스 (단일 애그리거트로 안 풀리는 로직에 한정)
- 값 객체(VO), 불변식

빈약 도메인 탈피: 애그리거트가 `handle(command) → List<DomainEvent>` + `apply(event) → newState`를 스스로 진다([[DESIGN-003]] §4.1).

## 2. 의존성

| 항목 | 값 |
|------|-----|
| **허용 의존** | `shared-module` **만** |
| **금지** | JPA · Spring · `contract-module` · 다른 command-* 서브모듈 · `query` |
| **순수성 보장** | build.gradle.kts에서 JPA/Spring 의존성 **물리적 배제** → 컴파일 타임 강제(Konsist 아님, Gradle 그래프) — Konsist는 Gradle이 못 막는 컨텍스트 간 참조만 보완([[RFC-031]] 2-tier) |
| **구현 시점** | **Phase 7-2** |

> **contract 금지의 함의**([[DESIGN-002]] 자기리뷰 §301 · [[DESIGN-019]]): 애그리거트가 반환하는 `DomainEvent`는 **core 자체 타입**이다. 이를 contract 통합 이벤트로 번역하는 계층은 `command-application`이다. core는 발행 이벤트를 모른다.

## 3. 사용 라이브러리

**여기가 순수성의 핵심.** 외부 프레임워크 의존 0을 지향한다.

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| Kotlin stdlib | `2.0.10` | 도메인 표현 전부 |
| `shared-module` | (project) | enum·추상예외·유틸 |
| (테스트) `kotest-runner-junit5` | `5.9.0` | 상태 전이 BDD 테스트 |
| (테스트) `kotest-property` | `5.9.0` | 불변식 property-based |
| (테스트) `fixture-monkey-kotlin` / `-kotest` | `1.1.11` | 애그리거트 픽스처·엣지 케이스 |

> **배제 라이브러리 (build.gradle.kts에 없음)**: `spring-*`, `jakarta.persistence`, `jakarta.validation`, `hibernate-validator`, `spring-kafka`. 검증은 `jakarta.validation` 어노테이션이 아니라 **순수 Kotlin `require(...)`**로 한다([[DESIGN-002]] 자기리뷰 §306 반박에 대한 채택 방향).

## 4. 구조

```
command-module/command-core
└── com.reservation.command.core
    ├── reservation/
    │   ├── Reservation.kt              # 애그리거트 루트 (handle/apply)
    │   ├── event/                      # ReservationCancelled 등 내부 DomainEvent
    │   ├── vo/                         # 값 객체
    │   └── service/                    # 도메인 서비스(필요 시)
    ├── restaurant/
    ├── timetable/
    ├── schedule/  user/  authenticate/  menu/  category/  company/
    └── support/
        ├── EventSourcingAggregate.kt   # ES 추상 (handle→events, apply→newState)
        └── StatefulAggregate.kt        # 비-ES 추상 (상태 변경 중심)
```

## 5. 핵심 설계

### 5.1 ES 애그리거트 ([[DESIGN-003]] §4.1)

```kotlin
class Reservation private constructor(/* immutable state */) {
    fun handle(command: CancelReservation): List<DomainEvent> {
        require(canCancel(command.now)) { "취소 가능 기한 초과" }   // 불변식 = 애그리거트 안
        return listOf(ReservationCancelled(id, command.reason, command.now))
    }
    fun apply(event: ReservationCancelled): Reservation { /* 불변 복사 전이 */ }
    // 리플레이·신규 발생에 apply 공용
}
```

### 5.2 컨텍스트 간 참조 금지

`reservation → restaurant` 직접 import 금지. 서브모듈로는 못 막으므로(같은 서브모듈 내 패키지) **Konsist**로 강제, 위반 시 빌드 실패([[DESIGN-002]] §4.5 · [[RFC-009-testing-quality-gates]] 결정 1 · [[RFC-031]] R3).

> **규칙 설계(2026-07-20 갱신)**: `reservation`↔`restaurant` 같은 쌍을 도메인 개수만큼 일일이 열거하지 않는다. `command.core` 직계 자식 패키지 중 `support`(공유 베이스 클래스, 예외)를 제외한 목록을 "도메인 목록"으로 동적으로 잡고, 그 목록 안에서 서로 다른 두 도메인 간 상호 import를 금지하는 **일반 규칙 하나**로 강제한다([[RFC-031-architecture-fitness-functions-archunit]] R3). 신규 도메인이 패키지로 추가되면 이 목록에 자동 편입되므로, 도메인이 늘어날 때마다 규칙을 수동으로 갱신해야 하는 문제가 설계상 발생하지 않는다.

## 6. 할 일

- [ ] `EventSourcingAggregate` 추상 클래스 (`handle`/`apply` 계약)
- [ ] `StatefulAggregate` 추상 클래스 (비-ES)
- [ ] 레퍼런스: `TimeTable` 애그리거트 ES 전환 (가장 단순한 ES 대상)
- [ ] 레퍼런스: `Reservation` 애그리거트 ES 전환 (사가 포함)
- [ ] 순수 Kotlin 검증 전략 확정 (jakarta.validation 대체 — 미결 M-4)
- [ ] Konsist 컨텍스트 간 참조 금지 규칙 — `support` 제외 도메인 목록을 동적으로 순회하는 일반 규칙으로 구현(쌍 열거 금지, §5.2 · [[RFC-031]] R3)
- [ ] 단위 테스트 (Kotest — 상태 전이 검증)

## 7. 미결 / 반박 대응

- **M-4**: core에서 검증 라이브러리 허용 여부 — (a) 순수 `require` (b) shared 경량 검증 (c) 허용. 기본은 (a).
- **반박**([[DESIGN-002]] 자기리뷰 §300): ES 리플레이의 `apply` 조립을 누가 하나 → [[DESIGN-019]] §5가 **application이 fold**로 확정(core는 `apply` 제공, 조립은 application). infra는 `StoredEvent`만.

## 8. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: command-core는 프레임워크 의존 0의 순수 도메인이며, 이벤트 타입 소유·불변식은 core가 지되 버저닝·매핑·리플레이 조립은 전부 application으로 밀어낸다.

**Steel-man**: 타입 소유를 계층 분리([[DESIGN-019]] §3)하면 core는 "최신 도메인 타입"만 알면 되고, 직렬화/역직렬화/업캐스팅/fold라는 오염원을 바깥에 격리해 도메인 로직을 프레임워크·스키마 진화로부터 독립시킬 수 있다 — ES의 이상적 관심사 분리다.

### 숨은 가정

- **A1**: 스키마 진화가 application의 업캐스팅([[DESIGN-019]] §6, RFC-022)에서 "끝난다"고 전제. 그러나 업캐스팅은 payload를 최신 `DomainEvent` *구조*로 올릴 뿐, 그 최신 타입에 대응하는 `apply` 분기(의미론)는 core가 영구히 진다.
- **A2**: `apply`가 "리플레이·신규 발생 공용"(§5.1)이면서 fold의 base가 항상 빈 초기 상태라고 암묵 전제. 스냅샷에서 base를 시작하는 최적화는 [[DESIGN-019]] §9에서 DESIGN-009로 미뤄졌고, 이 문서엔 스냅샷 언급이 전혀 없다.
- **A3**: 순수 Kotlin `require`가 도메인 불변식 검증으로 충분하다고 전제. 그러나 프로젝트 표준(CLAUDE.md: i18n 에러 메시지, 필드 단위 누적 검증)과의 정합은 미배정 상태(M-4).

### 반론

- **R1 · `[structural]` · severity: high** — "versioning은 core 밖"은 *타입 소유*에 한정해서만 참이고, 의미론적 부담은 core에 남는다. 이벤트 의미가 바뀌면(예: `ReservationCancelled`에 취소 사유 체계 추가/변경) 과거 이벤트를 fold한 `apply` 결과가 과거와 달라질 수 있고, core의 `apply`는 이벤트 이력 전체에 대한 시맨틱 하위호환을 영구히 떠안는다. 업캐스팅은 구조는 맞춰줘도 이 부담을 없애지 못한다. *선례: Greg Young 등이 반복 지적한 "event versioning is the hardest part of ES" — 스키마 변경 후 리플레이 결과가 과거 스냅샷/투영과 불일치하는 실패 패턴.*
- **R2 · `[timing]` · severity: high** — 스냅샷을 core 설계 시점에서 배제(DESIGN-009로 미룸)하면 `apply`/불변식이 "빈 상태부터의 전체 fold" 전용으로 굳는다. 나중에 스냅샷 base 재개를 넣을 때 (a) 스냅샷 버전과 이후 이벤트 버전의 정합, (b) 부분 상태에서 재개 가능한 불변식 재설계를 core가 뒤늦게 떠안게 된다. §5.1이 `apply`를 "공용"으로 못박은 것이 오히려 이 재설계를 어렵게 만든다. *선례: 스냅샷을 뒤늦게 도입한 ES 시스템에서 스냅샷/이벤트 버전 불일치로 손상 스냅샷을 무효화하고 전체 재리플레이한 사례.*
- **R3 · `[assumption]` · severity: medium** — `require` 검증은 순수성 유지엔 정당하나, 프로젝트가 명시한 i18n 에러·필드 단위 누적 검증과 충돌한다. jakarta.validation을 버리면 그 로직을 shared/application에 재구현해야 하는데 이 문서는 "기본 (a) require"로만 두고 책임 소재를 미결(M-4)로 남긴다. 또한 `require`는 `handle` 시점만 검증하고 `apply`(리플레이) 경로엔 검증이 없어 "과거 이벤트는 무조건 신뢰"라는 전제가 손상 이벤트에 무방비다. *선례: no clear precedent — speculative concern (프로젝트 고유 규약 충돌).*

### 핵심 취약점

core의 `apply`가 결국 "이벤트 이력 전체의 시맨틱 하위호환 + (도입 시) 스냅샷 base 정합"을 영구히 지는 지점. "versioning은 core 밖"이라는 이 문서의 핵심 주장은 타입 소유에 한정해서만 참이며, 의미론적 버저닝 부담은 그대로 core에 남는다 — 순수성의 실질적 한계다.

### 가역성

대체로 **reversible** (초기 설계·구현 전, Phase 7-2). 단, `apply` 시그니처를 "빈 상태부터의 전체 fold 전용"으로 굳히고 이벤트를 프로덕션에 축적하기 시작하면 스냅샷·버전 재설계는 **one-way door**에 근접한다.
