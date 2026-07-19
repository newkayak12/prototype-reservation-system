# ADR-007: command 측 순수 도메인 엔티티와 JPA 엔티티를 분리 유지한다

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-001-v2-cqrs-and-event-sourcing]] · **설계**: [[DESIGN-002-module-structure]]
- **연관 ADR**: [[ADR-002-selective-event-sourcing-scope]] · [[ADR-003-command-hexagonal-query-layered]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 순수 도메인 엔티티와 JPA 엔티티를 완전히 분리했다. 도메인은 JPA를 모르고, 둘 사이는 손으로 쓴 매핑이 이었다. 그 대가로 Mutator 패턴과 `toEntity` 변환의 반복 비용을 치렀다.

V2 command 측은 hexagonal로 가고, 애그리거트는 리치하게 재설계된다. 저장 방식도 컨텍스트별로 갈린다. ES 컨텍스트는 이벤트 스토어에, 비-ES 컨텍스트는 상태 테이블에 쓴다.

이 지점에서 유혹이 하나 생긴다. 애그리거트를 그대로 `@Entity`로 만들면 매핑 비용이 사라진다. 하지만 그 순간 JPA 제약이 도메인으로 밀려든다. 무인자 생성자, 가변 필드, open 클래스가 강제되고 `command-core`의 순수성이 무너진다. 매핑 비용을 없애려다 hexagonal 결정과 정면으로 모순된다.

**command 측 도메인과 영속을 통합할 것인가, 분리를 유지할 것인가.**

## 결정 동인 (Decision Drivers)

- 도메인 계층의 외부 의존성 0 — V1이 달성한 순수성을 계승한다.
- command = hexagonal 결정과 일관해야 한다. 도메인은 JPA를 모르고, JPA는 어댑터에 산다.
- ES와 비-ES가 동일하게 순수 애그리거트라는 하나의 멘탈 모델로 유지한다.
- 경계의 엄격함이 보일러플레이트 절감보다 우선한다. 명시적·예측 가능한 매핑에 값을 둔다.

## 검토한 선택지 (Considered Options)

- **A. 분리 유지** — 순수 도메인 애그리거트 + 별도 JPA 엔티티 + 수동 매핑. V1 방식.
- **B. 통합** — 애그리거트를 `@Entity`로 만들어 매핑을 없앤다.
- **C. 하이브리드** — 불변식이 풍부한 곳은 분리, trivial CRUD는 통합.

## 결정 (Decision Outcome)

**채택: A — 분리 유지. 도메인은 JPA를 갖지 않는다.**

순수성과 hexagonal 일관성이라는 동인이 매핑 비용 절감을 이긴다. B는 JPA로 도메인을 오염시켜 hexagonal 결정과 모순되고, C는 멘탈 모델을 둘로 쪼갠다.

결정의 구조는 다음과 같다.

**순수 도메인은 별도 모듈로 현행 유지한다.** 순수 도메인은 `command-core` 서브모듈에 산다. 이 모듈의 build.gradle에는 JPA·Spring 의존성이 없다. 따라서 순수성은 정적분석 규칙이 아니라 Gradle 의존성 그래프가 컴파일 타임에 강제한다. 도메인과 영속을 한 클래스로 합치는 통합은 채택하지 않는다.

**애그리거트에 `@Entity`를 붙이지 않는다.** 영속 관심사가 도메인으로 흘러드는 것을 원천에서 막는다.

**도메인↔JPA는 엄격한 hexagonal 수동 매핑으로 잇는다.** 컨텍스트별로 손으로 쓴 매핑 함수가 `command-adapter`의 out 어댑터 안쪽에 명시적으로 남는다. 코드 생성 도구(MapStruct류)와 공통 매퍼 추상은 채택하지 않는다. 보일러플레이트는 분리의 정당한 대가이지 제거할 결함이 아니며, 명시적·예측 가능한 수동 매핑이 경계의 엄격함을 지키는 값이다.

저장 방식에 따라 매핑의 성격이 갈린다.

| 분류 | 컨텍스트 | 영속 방식 | 도메인↔JPA 매핑 |
|------|----------|-----------|------------------|
| **ES** | `reservation` · `timetable` · `restaurant` | 이벤트 스토어 · 스냅샷 테이블 | JPA는 인프라 레코드이지 애그리거트 필드의 거울이 아니다. 1:1 매핑 자체가 없다 |
| **비-ES · 현행/lookup** | `schedule` · `user` · `authenticate` (비-ES) + `menu` · `category` · `company` (현행/lookup) | 상태 테이블 | 순수 도메인 + 별도 JPA 엔티티 + 수동 매핑. V1 방식 유지 |

상세 배치와 의존성 규칙은 [[DESIGN-002-module-structure]]로 위임한다.

### 결과 (Consequences)

**좋은 점**

- 도메인 계층이 JPA 없이 순수하게 유지된다. V1의 "도메인 외부 의존성 0"을 계승한다.
- command = hexagonal 결정과 일관된다.
- ES와 비-ES가 동일하게 순수 애그리거트라 멘탈 모델이 하나로 유지된다.
- JPA 교체·테스트가 도메인에 영향을 주지 않는다.

**트레이드오프**

- 매핑 비용을 진다. V1이 Mutator 패턴·`toEntity` 복잡성으로 겪은 바로 그 고통이다. 완화 요인은 둘이다. 애그리거트가 리치해지고, ES 컨텍스트는 이벤트·스냅샷 저장이라 매핑이 아예 사라진다. 그래서 매핑 부담은 비-ES 컨텍스트로 한정·축소된다.
- 수동 매핑의 누락은 컴파일이 아니라 런타임·테스트에서만 잡힌다. 도메인 필드가 바뀔 때 매핑 함수를 손으로 동기화해야 하므로, 누락 시 데이터 정합성 결함으로 번질 수 있다. 매핑 골든 테스트로 방어한다.
- **재검토 트리거**: 컨텍스트가 크게 늘어 동일 매핑 패턴 반복이 과해지면 재검토한다. 답은 공통 추상이 아니라 경계를 흐리지 않는 국소 컨벤션(같은 자리·같은 시그니처)이다. 실제 반복량은 한두 컨텍스트 전환 후에 판단한다.

### 확인 (Confirmation)

- `command-core`의 build.gradle에 JPA·Spring 의존성이 없는지 확인한다. Gradle 의존성 그래프가 이를 물리적으로 강제한다.
- 애그리거트에 `@Entity`가 붙지 않는지, 도메인 패키지가 JPA를 import하지 않는지 ArchUnit/Konsist로 강제한다. 위반 시 빌드 실패.
- 비-ES 컨텍스트의 도메인↔JPA 왕복 매핑을 골든 테스트로 검증한다.

## 선택지 상세 (Pros and Cons of the Options)

### B. 통합 (애그리거트 = @Entity)

- 장점: 매핑 비용 0.
- 단점: JPA가 도메인을 오염시킨다. V1이 분리로 피하려던 바로 그 문제다. hexagonal 결정과 모순된다.
- 기각 사유: 도메인 순수성 상실. command = hexagonal 결정과 충돌.

### C. 하이브리드 (풍부=분리, trivial=통합)

- 장점: 빈약한 CRUD 컨텍스트에서 격식을 절약한다.
- 단점: 멘탈 모델이 둘로 갈린다. 컨텍스트마다 도메인이 순수한지 아닌지 확인해야 해 일관성이 떨어진다.
- 기각 사유: 멘탈 모델 이원화.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 매핑 반복이 과해질 때 도입할 국소 컨벤션의 구체 형태. 한두 컨텍스트 전환 후 실제 반복량을 보고 확정한다.
- 관련: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-010-module-structure-migration]] · [[DESIGN-002-module-structure]] · [[DESIGN-003-write-model]] · [[ADR-002-selective-event-sourcing-scope]] · [[ADR-003-command-hexagonal-query-layered]]
- 계승: V1 도메인/JPA 엔티티 분리 결정.
