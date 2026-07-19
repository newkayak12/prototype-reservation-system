# ADR-001: command / query 를 top-level Gradle 모듈로 분리한다

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-001-v2-cqrs-and-event-sourcing]] · **설계**: [[DESIGN-002]]
- **연관 ADR**: [[ADR-003-command-hexagonal-query-layered]] · [[ADR-007-command-domain-jpa-separation]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 `core-module` / `application-module` / `adapter-module` 단일 계층 구조에서 읽기와 쓰기 코드가 한 모델·한 DB를 공유했다([[DESIGN-001]]). V2의 목표는 CQRS — 읽기/쓰기 경로를 분리해 각자 독립적으로 진화·확장하게 만드는 것이다.

패키지 분리만으로는 컴파일 타임 경계가 서지 않는다. query 코드가 command 도메인을 import할 경로가 남아 있는 한, 읽기가 쓰기 모델을 오염시키는 결합이 시간이 지나며 되돌아온다.

**분리를 무엇으로 강제하는가 — 패키지 규약인가, 물리 모듈 경계인가?**

## 결정 동인 (Decision Drivers)

- 읽기/쓰기를 컴파일 단위로 격리(규약이 아니라 구조로 강제).
- query가 command 도메인/스키마에 의존할 경로 자체를 없앤다.
- 단일 배포(모듈러 모놀리스)로 시작하되, 나중에 물리 분리 가능한 구조.

## 검토한 선택지 (Considered Options)

- **A. 공유 core + 애플리케이션/읽기 계층만 분리** — 모듈 수는 적으나 CQRS 축이 약하고 query가 core에 결합할 유혹이 남는다.
- **B. 풀 버티컬 — top-level `command` / `query` 모듈 분리** — command↔query 격리가 가장 깔끔, query→core 의존 경로 없음. 대신 top-level 축이 CQRS라 후일 "도메인별 서비스 분할"이 비싸진다.
- **C. 컨텍스트(도메인)-top 분리** — 도메인별 분할은 쉬우나 "command/query 분리"라는 의도와 축이 다르고 대규모 재구성.

## 결정 (Decision Outcome)

**채택: B — 풀 버티컬 `command-module` / `query-module` top-level 분리, 도메인은 각 모듈 내부 패키지.**

CQRS를 컴파일 단위로 강제하는 것이 이번 결정의 최우선 동인이고, B만이 query→command 의존 경로를 구조적으로 제거한다.

- `command-module`, `query-module` 을 top-level Gradle 모듈로 둔다.
- 각 모듈 내부에서 바운디드 컨텍스트를 **패키지**로 나눈다(`com.reservation.command.reservation` 등).
- **의존성 규칙**: `query` → `command`/도메인 core 의존 **금지**. 두 모듈의 유일한 접점은 `contract-module`의 이벤트.
- 모듈 경계는 Gradle로 1차 강제. 상세 트리·서브모듈 구조는 [[DESIGN-002]].

### 결과 (Consequences)

- 좋은 점: 읽기/쓰기가 컴파일 단위로 격리된다 / query가 command 도메인을 import할 경로가 없다 / `query-module`을 별도 배포로 떼기 쉽다([[DESIGN-010]]).
- 나쁜 점 / 트레이드오프: 후일 한 컨텍스트를 서비스로 떼려면 command/query 양 트리에서 같은 이름 패키지를 함께 들어내야 한다. 도메인을 깨끗한 패키지로 유지하면 수용 가능하다고 판단하고 이 트레이드오프를 알고 택한다. 모듈 수 증가.

### 확인 (Confirmation)

도메인 패키지 간 경계는 Konsist 아키텍처 규칙으로 강제([[DESIGN-012]]) — `query`가 `command`/core를 참조하면 빌드 실패.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: query를 물리적으로 분리할 시점·기준(현재 단일 배포).
- 관련: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[DESIGN-002]] · [[ADR-003-command-hexagonal-query-layered]]
