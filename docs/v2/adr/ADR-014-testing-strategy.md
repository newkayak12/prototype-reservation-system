# ADR-014: V2 테스트 전략 — 정적 구조 강제(Konsist) + 3슬라이스 행위 명세 + 동적 분산 6범주 게이트

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-009-testing-quality-gates]] · [[RFC-023-event-schema-contract-management]] · **설계**: [[DESIGN-012-environments-and-testing]]
- **연관 ADR**: [[ADR-011-es-pii-crypto-shredding]] · [[ADR-021-integrated-event-contract-module]] · [[ADR-010-event-schema-evolution]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-008-saga-orchestration-vs-choreography]] · [[ADR-017-authorization-model]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 읽기·쓰기가 같은 모델·DB를 공유해 "경계를 넘었다"는 개념 자체가 약했다 — 리뷰가 경계를 지켰고, 동기·단일 모델이라 검증할 분산 행위도 거의 없었다. V2는 CQRS+ES로 command/query 모듈이 갈라지고 Kafka를 통해 비동기로 사실이 전달되면서, 테스트가 두 층을 동시에 잡아야 한다.

1. **정적 구조** — "query 모듈은 command 모듈을 import 하지 않는다", "도메인은 JPA 애너테이션을 모른다" 같은 경계 규칙은 사람 리뷰로 막을 수 있는 종류의 침범이 아니다.
2. **동적 분산 행위** — 같은 이벤트가 두 번 적용돼도 상태가 같은가, 리플레이로 다시 세운 read model이 라이브와 같은가, 사가가 중간에 실패해도 종결에 도달하는가 — 다른 RFC가 결정한 메커니즘이 실제로 성립하는지는 정적 테스트로 안 보인다.

**정적 구조 강제와 동적 분산 행위 검증을 무엇으로 세우고, CI 필수/정기/비-차단 관측으로 어떻게 나눌 것인가.**

## 결정 동인 (Decision Drivers)

- 검증 대상(Kotlin 패키지·import)과 도구 언어의 일치 — JVM 바이트코드/리플렉션이 아니라 Kotlin 소스 구조를 직접 본다.
- 결정적 항목은 빠르고 값싸게 CI에 물리고, 무거운 분산 통합 항목은 CI를 느리게 만들지 않는다.
- 인가·인증처럼 책임이 갈리는 영역은 이 계층이 검증할 몫만 지고 나머지는 소유 ADR로 넘긴다.
- 무트래픽 학습 규모에 맞는 최소 machinery — 카오스는 두 레벨 모두 하되 비-차단 관측으로 시작한다.

## 검토한 선택지 (Considered Options)

**아키텍처 강제**
- **ArchUnit** — JVM 표준급 성숙도. 바이트코드/리플렉션 기반이라 Java 타입 세계를 본다.
- **Konsist** — Kotlin 네이티브 DSL로 소스 구조를 직접 질의. 신생이라 기능군이 얇을 수 있다.

## 결정 (Decision Outcome)

**채택: Konsist.** 검증 대상(Kotlin 패키지·import)과 도구 언어가 일치한다. (이의 여지: 사이클 탐지 등 복잡한 규칙이 필요해지면 ArchUnit 재검토.) Konsist 규칙 위에 [[ADR-011-es-pii-crypto-shredding]]의 PII 셰딩 빌드타임 분류 강제(개인정보 필드 `@Pii` 미선언 시 빌드 실패)를 얹는다 — 규칙 본문은 ADR-011 소관이고, 여기서는 그 규칙이 이 아키텍처 강제 계층 위에 탑재된다는 사실만 확정한다.

### 행위 명세(BDD) — 3슬라이스

**Kotest `BehaviorSpec`**으로 Given-When-Then 행위 명세를 세 슬라이스에 깐다:

- **usecase(application)**: 포트를 목으로 두고 오케스트레이션 행위 검증.
- **service(domain)**: 도메인 불변식을 비즈니스 언어로 고정.
- **controller(standalone)**: standalone MockMvc로 API 계약 검증 + REST Docs 연동.

property-based(무작위 입력으로 불변식 검증)와는 보완 관계다.

### 인가 검증

controller(standalone) 슬라이스에 역할 기반 인가 시나리오를 얹는다. 토큰 발급·검증은 인증 서버 책임([[ADR-024-authentication-boundary]])이고, 이 계층은 [[ADR-017-authorization-model]]의 인가 결정에만 집중한다.

### 동적 분산 행위 — 6범주 게이트

| 범주 | 게이트 |
|---|---|
| 멱등성·재전달 | CI 필수 |
| 재생·스냅샷 등가 | CI 필수 |
| 동시성·비관 락([[ADR-016-aggregate-concurrency-pessimistic-lock]]) | CI 필수 |
| 재구축·catch-up | 정기/통합 |
| 사가 보상·타임아웃([[ADR-008-saga-orchestration-vs-choreography]]) | 정기/통합 |
| 종단 비동기 라운드트립 | 정기/통합 |

결정적 범주(멱등성·재생 등가·동시성)는 CI 필수, 무거운 통합 범주(재구축·사가·종단)는 정기/통합 단계.

### 카오스

두 레벨 모두 하되 둘 다 비-차단 관측이다:

- **앱 레벨**: Chaos Monkey for Spring Boot — 스프링 빈 레벨에서 지연·예외·메모리 압박 주입.
- **인프라 레벨**: Chaos Mesh — 쿠버네티스 네이티브·CRD 기반으로 파드 kill·네트워크 분단.

### 게이트 정책과 localstack

CI 필수 게이트: 아키텍처 강제·property-based·계약·업캐스팅 회귀·동적 분산 행위 결정적 3범주. 비-차단 관측: Chaos(별도 리포팅으로 가시화). 커버리지·k6 등 절대 수치는 지금 잠그지 않고 **베이스라인 측정 후 ratchet(후퇴 금지)** 으로 점진 상향한다.

localstack은 AWS 의존이 굳는 대로 채우는 **살아있는 목록**으로 둔다 — 지금 완결하지 않는다.

### 이벤트 스키마 계약·업캐스팅

생산자·소비자 wire 계약(공유 계약 모듈 + 직렬화 골든 테스트)은 [[ADR-021-integrated-event-contract-module]] 소관이고, 업캐스팅 회귀(과거 이벤트를 새 코드로 읽기)는 [[ADR-010-event-schema-evolution]] 소관이다. 이 ADR은 두 계약 절을 재서술하지 않고 링크만 건다.

### 결과 (Consequences)

- 좋은 점: 경계 침식이 사람 리뷰가 아니라 Konsist가 컴파일/테스트 시점에 잡는다.
- 좋은 점: 결정적 동적 범주만 CI 필수로 좁혀, 빌드 무게와 정확성 보장의 균형을 잡는다.
- 좋은 점: PII·인가·계약·업캐스팅·동시성·사가 각각의 상세 규칙은 소유 ADR에 남고 여기서는 링크로만 엮여 중복이 없다.
- 나쁜 점 / 트레이드오프: Konsist는 신생 도구라 복잡한 규칙(사이클 탐지 등)에서 기능이 얇을 수 있다 — 재검토 트리거: 그런 규칙이 필요해지면 ArchUnit을 재검토한다.
- 나쁜 점 / 트레이드오프: 커버리지·k6 절대 임계가 지금 없어 베이스라인 측정 전까지는 회귀를 정량 판단할 수 없다.
- 나쁜 점 / 트레이드오프: 카오스가 비-차단이라 가시화 경로 없이는 죽은 테스트가 될 위험이 있다.

### 확인 (Confirmation)

- Konsist 규칙(모듈 경계·JPA 비침투·PII `@Pii` 선언 강제)이 CI에서 빌드를 실패시키는지 확인한다.
- usecase/service/controller 3슬라이스에 Kotest `BehaviorSpec`이 존재하고 CI에서 실행되는지 확인한다.
- controller(standalone) 슬라이스의 역할 기반 인가 시나리오가 [[ADR-017-authorization-model]]의 인가 결정과 일치하는지 확인한다.
- 동적 분산 행위 결정적 3범주(멱등성·재생 등가·동시성)가 CI 필수 게이트에 실제로 물려 있는지, 나머지 3범주가 정기/통합 파이프라인에 배치돼 있는지 확인한다.
- Chaos Monkey·Chaos Mesh 실행 결과가 별도 리포팅 경로로 가시화되는지 확인한다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 커버리지·k6 절대 임계 수치, localstack 대상 서비스의 구체 목록, Chaos 카오스 시나리오의 세부 설계.
- 관련: [[RFC-009-testing-quality-gates]] · [[RFC-023-event-schema-contract-management]] · [[DESIGN-012-environments-and-testing]] · [[ADR-011-es-pii-crypto-shredding]] · [[ADR-021-integrated-event-contract-module]] · [[ADR-010-event-schema-evolution]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-008-saga-orchestration-vs-choreography]] · [[ADR-017-authorization-model]] · [[ADR-024-authentication-boundary]]
