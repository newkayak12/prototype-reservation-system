# ADR-004: 읽기는 전부 query DB의 이벤트 프로젝션 read model에서 읽는다

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-001-v2-cqrs-and-event-sourcing]] · **설계**: [[DESIGN-004-read-model]]
- **연관 ADR**: [[ADR-001-cqrs-command-query-module-split]] · [[ADR-002-selective-event-sourcing-scope]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 별도 read model 없이 쓰기 테이블을 그대로 공유 조회한다. 읽기 트래픽이 쓰기 경로에 부담을 주고, 교차 컨텍스트 조인이 필요한 화면에서 command 스키마에 강결합된다.

ADR-001에서 command/query를 top-level 모듈로 분리했다. 그 귀결로 query는 command 테이블을 직접 조회할 수 없다 — 그렇게 하면 스키마 결합이라는 안티패턴이 되살아난다. 게다가 ES 컨텍스트(ADR-002)의 이벤트 스트림은 append-only라 임의 조회에 쓸 수 없다.

그렇다면 query는 무엇을 읽는가? 특히 프로젝션을 만들지 않을 만큼 저빈도인 lookup 데이터의 읽기 소스를 정해야 한다.

**분리된 query 측은 무엇을 읽기 소스로 삼는가?**

## 결정 동인 (Decision Drivers)

- CQRS에서 command의 책임은 검증+쓰기뿐 — 조회는 항상 query 측이 처리한다.
- command 스키마 결합을 물리적으로 차단한다.
- 프로젝션은 공짜가 아니므로 읽기 요구가 입증된 곳부터 만든다(YAGNI).
- 읽기 소스가 하나로 균일해야 query가 자기 DB만 읽는 단순함을 지킨다.

## 검토한 선택지 (Considered Options)

- **A. 전부 프로젝션** — 모든 컨텍스트가 이벤트로 read model을 채운다. 균일하나 저빈도 lookup까지 projector를 만들어야 하는 과투자.
- **B. 프로젝션 기본 + 저빈도는 경량 lookup 프로젝션** — 프로젝션이 기본이되, 교차조인·고읽기가 필요 없는 저빈도분은 query DB 안의 경량 프로젝션으로 흡수.
- **C. command DB(또는 그 read replica) 직접 조회** — query가 command 스키마를 직접 읽는다. 단순하지만 스키마 결합이고 ES 컨텍스트에는 애초에 불가능.

## 결정 (Decision Outcome)

**채택: B — 이벤트 프로젝션을 기본으로 하고, 저빈도는 query DB 안의 경량 lookup 프로젝션으로 흡수한다.** query는 언제나 자기 query DB의 프로젝션만 읽는다. A는 저빈도 트리비얼 데이터까지 헛투자하고, C는 command 스키마 결합을 되살려 CQRS의 이점을 소거한다.

읽기 소스를 두 갈래로 정의하되, 둘 다 query DB 안의 async-fed 로컬 카피라는 점에서 하나다.

| 갈래 | 언제 | 갱신 방식 |
|------|------|-----------|
| **이벤트 프로젝션 read model (기본)** | 교차 컨텍스트 조인·고읽기·다른 모양의 조회 | `contract` 이벤트를 구독해 비정규화 `model` 갱신 |
| **경량 lookup 프로젝션** | 거의 안 변하는 저빈도 참조 데이터 | 소유 컨텍스트의 이벤트/published 변경을 구독해 로컬 테이블 갱신 |

**경계 규칙 — 읽기 소스는 언제나 query DB의 프로젝션 하나다.**

- ES 컨텍스트는 최소 1개의 현재상태 프로젝션을 반드시 가진다. 이벤트 스트림으로는 임의 조회가 안 되기 때문이다. "프로젝션 미적용"은 *추가* 프로젝션을 안 만든다는 뜻이지 읽기 뷰가 0개라는 뜻이 아니다.
- 저빈도 lookup도 예외가 아니다. 남이 흘리는 변경을 비동기로 받아 query DB의 로컬 테이블을 갱신한다. 조회 시점에 원본을 동기 호출(cross-context fetch)하는 것은 읽기 경로에 런타임 결합을 다시 들이므로 금지한다.
- **command DB 직접 읽기·replica 직접 읽기는 금지한다.** query DB는 command DB와 물리 분리이므로 이 경계가 물리적으로 성립한다.
- **replica는 읽기 분산용이 아니라 HA 전용이다.** 읽기 확장은 replica를 읽기 소스로 삼는 게 아니라 query 인스턴스의 HA 레플리카로 분산한다.
- 프로젝션은 읽기 요구가 입증된 곳부터 만든다. "있으면 편하다"는 입증이 아니며, 실재하는 화면/부하 요구만 입증으로 친다.

상세 읽기 소스 정의·컨텍스트별 초기 전략·일관성 정책·Redis 경계는 [[DESIGN-004-read-model]]로 위임한다.

### 결과 (Consequences)

**좋은 점**

- 읽기가 쓰기와 독립적으로 최적화·확장된다.
- query가 query DB의 프로젝션만 읽어 command 스키마 의존이 0이다(물리 분리).
- 읽기 소스가 하나로 균일해 어느 컨텍스트든 읽기 경로가 같다.
- 프로젝션을 입증된 곳부터 만들어 저빈도 트리비얼 데이터에 헛투자하지 않는다.

**트레이드오프**

- 프로젝션은 이벤트 구독으로 갱신되어 최종 일관성이다 — 쓰고 바로 읽으면 아직 없을 수 있다. 이를 버그가 아니라 기본 사양으로 감수한다.
- 저빈도 트리비얼 데이터에도 이벤트→경량 프로젝션 배관이 필요하다.
- **재검토 트리거**: "쓰고 바로 읽어야 하는" 화면이 실제로 증명되면, 그 화면에 한해 신선도 예외 정책을 신규 ADR로 연다.

### 확인 (Confirmation)

- query 코드가 command DB·replica를 참조하면 빌드 실패 — 아키텍처 테스트로 강제.
- ES 컨텍스트마다 최소 1개의 현재상태 프로젝션 존재를 검증.
- 조회 경로에 cross-context 동기 호출이 없는지 코드 리뷰·아키텍처 테스트로 확인.

## 선택지 상세 (Pros and Cons of the Options)

### A. 전부 프로젝션
- 장점: 균일하고 깔끔하다. query가 완전히 독립적이다.
- 단점: 저빈도 lookup까지 projector를 만들어야 한다.
- 기각 사유: 트리비얼 데이터에까지 프로젝션 배관을 강제해 과투자다.

### C. command DB / replica 직접 조회
- 장점: 저빈도 projector가 불필요해 단순하다.
- 단점: replica를 읽어도 결국 command 스키마에 결합한다. ES 컨텍스트는 상태 테이블이 없어 애초에 불가능하다.
- 기각 사유: 스키마 결합 안티패턴으로 CQRS의 이점을 소거한다. replica는 읽기 소스가 아니라 HA 전용이다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 저빈도 lookup을 경량 프로젝션과 published-subscription 중 무엇으로 둘지는 컨텍스트별 소유권이 드러나는 구현 사이클에서 확정. 프로젝션 지연 허용치·동기 프로젝션이 필요한 예외 화면도 측정·증명 후 결정.
- 관련: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-002-read-model-consistency]] · [[RFC-018-caching-redis-role]] · [[DESIGN-004-read-model]] · [[ADR-001-cqrs-command-query-module-split]] · [[ADR-002-selective-event-sourcing-scope]] · [[ADR-013-db-hosting-and-read-write-topology]]
