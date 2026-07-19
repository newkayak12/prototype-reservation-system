# ADR-003: command 모듈은 hexagonal, query 모듈은 layered로 구성한다 (의도된 아키텍처 비대칭)

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-001-v2-cqrs-and-event-sourcing]] · **설계**: [[DESIGN-002-module-structure]]
- **연관 ADR**: [[ADR-001-cqrs-command-query-module-split]] · [[ADR-002-selective-event-sourcing-scope]]

---

## 맥락과 문제 (Context and Problem Statement)

command와 query를 top-level 모듈로 갈랐다. 그러면 각 모듈을 **어떤 아키텍처 스타일로 짤지**가 남는다.

쓰기와 읽기는 복잡도가 다르다. 쓰기는 불변식과 상태 전이가 풍부한 리치 도메인이다. 읽기는 본질적으로 "저장소 → DTO" 변환이다.

두 모듈에 같은 스타일을 강요하면 한쪽은 반드시 손해를 본다. 양쪽에 hexagonal을 깔면 읽기 측에 격리할 도메인이 없는데도 포트/어댑터 격식을 지불한다. 양쪽에 layered를 깔면 쓰기 측 리치 도메인이 다시 빈약해질 위험이 생긴다.

**command와 query의 아키텍처 스타일을 같게 둘 것인가, 다르게 둘 것인가?**

## 결정 동인 (Decision Drivers)

- 각 측의 복잡도에 맞는 경제적 선택 — 쓰기는 도메인 격리가 필요하고, 읽기는 군더더기가 없어야 한다.
- 쓰기 측 도메인 불변식을 격리해 빈약 도메인 회귀를 막는다.
- query가 command 도메인에 의존하지 않게 결합을 끊는다.
- 대칭을 위한 대칭은 낭비다.

## 검토한 선택지 (Considered Options)

- **A. 양측 hexagonal** — 일관성 최고, query 측에 과설계.
- **B. 양측 layered** — 단순하지만 command 측 도메인 빈약화 위험.
- **C. 비대칭** — command=hexagonal, query=layered.

## 결정 (Decision Outcome)

**채택: C — 의도된 아키텍처 비대칭.**

각 측의 복잡도에 맞춘 경제적 선택이 A·B를 이긴다. A는 읽기에 헛격식을 지불하고, B는 쓰기 도메인을 빈약하게 만든다.

**command-module = hexagonal.** 도메인 불변식과 상태 전이를 포트/어댑터로 격리한다. command-core / command-application / command-adapter / command-infrastructure 네 서브모듈로 hexagonal 4층이 하나의 command 경계 안에서 완결된다. 애그리거트가 `handle(command) → List<DomainEvent>`와 `apply(event) → newState` 책임을 스스로 진다.

**query-module = layered.** 도메인 패키지마다 web / service / repository / projection / model로 구성한다. 포트/어댑터를 두지 않는다. 읽기는 DB→DTO에 가까워 layered가 경제적이다.

**query는 command 도메인에 의존하지 않는다.** query는 command-core를 import하지 않는다. `projection`이 `contract`의 이벤트를 구독해 read model을 채우는 것이 command과 query의 유일한 연결이다.

**query layered 내부의 두 경로는 책임과 트랜잭션 경계가 다르다.**

| 경로 | 방향 | 책임 | 트랜잭션 경계 |
|------|------|------|---------------|
| `projection` | 쓰기 | 이벤트를 받아 read model을 갱신 | 메시징 소비 단위 (한 이벤트 처리 = 한 트랜잭션 + 오프셋 커밋) |
| `service` | 읽기 | read model을 조회해 DTO로 반환 | service에서 닫음 (읽기 전용) |

두 경로는 같은 read model 테이블을 공유하되 방향이 반대다. 하나로 묶지 않는다.

상세 구조·의존성 매트릭스·경계 강제 규칙은 [[DESIGN-002-module-structure]]로, query 측 두 경로의 책임 분리는 [[DESIGN-004-read-model]]로 위임한다.

### 결과 (Consequences)

**좋은 점**

- 읽기에 hexagonal 격식을 부과하지 않아 보일러플레이트가 줄어든다.
- 쓰기 측을 hexagonal로 격리해 리치 도메인이 빈약해지는 회귀를 막는다.
- 책임이 명확하다 — command는 의사결정·불변식, query는 조회·표현.

**트레이드오프**

- 두 모듈의 스타일이 달라 일관성이 떨어지고 학습 곡선이 오른다. 신규 합류자가 두 패턴을 익혀야 한다.
- 코드 리뷰와 컨벤션을 모듈별로 따로 가져가야 한다.
- **재검토 트리거**: query 측에 격리할 만한 도메인 로직(불변식·상태 전이)이 실제로 자라나면 layered 유지를 재검토한다.

### 확인 (Confirmation)

- `query`는 `command-*` 전체를 import하지 못한다 — query의 build.gradle에 command 의존이 없어 Gradle 의존성 그래프가 강제한다.
- `command-core`의 build.gradle에 JPA·Spring 의존성이 없다 — hexagonal 도메인 순수성이 컴파일 타임에 보장된다.
- `projection`(쓰기 경로)과 `service`(읽기 경로)의 트랜잭션 경계 분리를 아키텍처 테스트로 검증한다.

## 선택지 상세 (Pros and Cons of the Options)

### A. 양측 hexagonal
- 장점: 양쪽 동일 멘탈 모델, 일관성.
- 단점: 읽기 측은 격리할 도메인 로직이 없어 포트/어댑터가 낭비다.
- 기각 사유: 읽기 측 과설계.

### B. 양측 layered
- 장점: 단순.
- 단점: layered가 command 측 리치 도메인을 담기 어려워 도메인이 다시 빈약해질 위험.
- 기각 사유: 쓰기 도메인 빈약화 위험.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: query layered의 세부 레이어 규약 — 다중 소스 프로젝션의 갱신 순서·원자성 등 트랜잭션 경계 이외의 세부는 구현 사이클에서 확정한다.
- 관련: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-002-read-model-consistency]] · [[DESIGN-002-module-structure]] · [[DESIGN-004-read-model]] · [[ADR-001-cqrs-command-query-module-split]] · [[ADR-004-read-model-projection-and-replica]]
