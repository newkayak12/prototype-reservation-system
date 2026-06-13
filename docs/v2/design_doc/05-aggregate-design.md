# V2 Design Doc — 05. Aggregate Design (도메인 로직 배치)

- **개요**: [[00-design-overview]] · **근거**: [[02-domain-limitations]]
- **관련 결정**: [[03.command-hexagonal-query-layered]] · [[02-write-model]]

> 이 문서는 command 측 **애그리거트를 어떻게 설계하고, 로직을 어디에 둘지**의 참고 기준이다. 구현 사이클에서 컨텍스트별 애그리거트를 설계할 때 이 규칙을 따른다.

## 다루는 취약점 (V1)

> **취약점 W-1 — 빈약한 애그리거트 + 흩어진 도메인 로직**
> V1은 상태 변경 로직의 ~95%를 **16개 이상의 `*DomainService`** 에 두고, 애그리거트는 `var` + setter 데이터 홀더로 만들었다([[02-domain-limitations]] #1). 그 결과 "어떤 이벤트가 언제 발생하는가"의 권위가 애그리거트에 없어, 이벤트 소싱으로 가는 데 가장 큰 장애가 된다.

핵심 진단: V1의 `*DomainService` **대부분은 진짜 도메인 서비스가 아니라 애플리케이션 오케스트레이션**이다(`validate → 객체 생성 → save → snapshot`). 도메인 로직과 절차가 한 곳에 뭉쳐 있다.

## V2 원칙 — 리치 애그리거트

애그리거트는 자신의 불변식과 상태 전이를 **스스로** 책임진다.

```kotlin
// 개념 예시 — 시그니처는 구현 사이클에서 확정
class Reservation private constructor(/* state */) {
    // 1) 명령을 받아 불변식 검증 후 발생할 이벤트를 반환
    fun handle(command: CancelReservation): List<DomainEvent> {
        require(canCancel(command.now)) { "취소 가능 기한(방문 3일 전) 초과" }
        return listOf(ReservationCancelled(id, command.reason, command.now))
    }
    // 2) 이벤트를 상태에 적용 (불변 전이) — 리플레이·신규 발생 공용
    fun apply(event: ReservationCancelled): Reservation { /* ... */ }
}
```

- 불변식 검증은 **애그리거트 안**에서 (V1처럼 서비스에서 하지 않는다).
- 상태 변경은 **이벤트를 통해** 표현(ES) 또는 이벤트와 함께 발행(비-ES).

## 로직 배치 규칙 (triage)

V1의 `*DomainService` 에 있던 로직을 다음 기준으로 재배치한다.

| 로직 성격 | V2 행선지 | 위치 |
|-----------|-----------|------|
| 단일 애그리거트의 불변식·상태 전이 | **애그리거트** | `command.<ctx>.domain` |
| 적재 → 애그리거트 호출 → 저장 → 이벤트 발행 (절차) | **애플리케이션 서비스(유스케이스)** | `command.<ctx>.application.service` |
| 진짜 여러 애그리거트에 걸친 도메인 규칙 / 순수 도메인 계산 | **얇은 도메인 서비스** (소수만) | `command.<ctx>.domain` |

> 결과: V1의 16개 서비스 대부분이 **애그리거트 + 유스케이스**로 흡수되고, 도메인 서비스는 몇 개만 남는다. "도메인 서비스가 많다 = 빈약 도메인 신호"로 본다.

## 애그리거트 설계 가이드

1. **불변식 경계로 식별** — "항상 함께 일관성이 지켜져야 하는 것"이 한 애그리거트.
2. **작게 유지** — 큰 애그리거트는 경합·잠금 범위를 키운다. 의심되면 쪼갠다.
3. **애그리거트 간은 ID 참조** — 객체 직접 참조 금지(예: `Reservation` 은 `restaurantId` 만 안다).
4. **부수효과는 이벤트로** — 다른 애그리거트/컨텍스트 변경은 직접 호출이 아니라 이벤트 발행으로([[02-write-model]]).
5. **불변(immutable) 전이 선호** — `apply` 는 새 상태를 반환(ES 리플레이 안전성).

## 적용 메모

- 컨텍스트별 구체 애그리거트·이벤트 목록은 **이벤트 스토밍 재실시 후** 확정(기존 보드는 참고용).
- ES 컨텍스트는 `handle/apply` 가 이벤트 스토어와 직접 맞물린다. 비-ES도 동일한 리치 애그리거트 원칙을 따르되, 상태 테이블에 저장 + Outbox 발행한다([[02-write-model]]).

## 관련 문서
- [[00-design-overview]] · [[02-write-model]] · [[01-module-structure]]
- 근거: [[02-domain-limitations]]
- ADR: [[03.command-hexagonal-query-layered]]
