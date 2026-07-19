# 10 · test-module [유지]

> 허브: [[00-module-index]] | 근거: [[DESIGN-002]] · [[DESIGN-012]] (환경·테스트) · [[ADR-014]] (테스트 전략)

## 1. 책임

FixtureMonkey 설정, 공통 테스트 픽스처, Testcontainers 설정. command/query 테스트 유틸 추가.

## 2. 의존성

| 항목 | 값 |
|------|-----|
| **허용 의존** | `shared-module`, `contract-module`(이벤트 픽스처용) |
| **구현 시점** | Phase 7-2부터 점진 확장 |

## 3. 사용 라이브러리 (레이어별 테스트 전략 — [[ADR-014]])

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| `kotest-runner-junit5`·`-assertions`·`-framework`·`-property` | `5.9.0` | Core(도메인)·Adapter 테스트 |
| `kotest-extensions-spring` | `1.3.0` | Kotest + Spring 컨텍스트 |
| `junit-jupiter` | `5.10.2` | Application 레이어 테스트 |
| `mockk` / `springmockk` | `1.13.10` / `4.0.2` | 목킹 |
| `assertj-core` | `3.24.2` | Application 검증 |
| `fixture-monkey-starter-kotlin` / `-kotest` | `1.1.11` | property-based·엣지 케이스 |
| `testcontainers-mysql`·`-kafka`·`-junit` | `2.0.3` | 실제 인프라 통합 |
| `spring-boot-testcontainers` | `3.4.5` | Testcontainers 연동 |
| `spring-kafka-test` | `3.3.1` | 임베디드 Kafka |
| `spring-security-test` | (Boot BOM) | 인증 컨텍스트 |

### 레이어별 매핑 (CLAUDE.md 전략)

| 레이어 | 프레임워크 |
|--------|-----------|
| Adapter | Kotest + MockK + Testcontainers(MySQL/Redis/**Kafka**) |
| Application | JUnit + MockK + AssertJ |
| Core | Kotest (도메인 로직) |
| Projection(07) | Testcontainers Kafka+MySQL E2E |

## 4. 구조

```
test-module/com.reservation.test
├── fixture/          # FixtureMonkey 설정 + 도메인 픽스처
├── container/        # Testcontainers (MySQL/Kafka) 공통 설정
└── es/               # ES 테스트 헬퍼
    ├── EventStreamBuilder.kt     # 이벤트 스트림 빌더
    ├── AggregateTestDsl.kt       # Given(events)-When(command)-Then(events) DSL
    └── ProjectionTestHelper.kt   # 이벤트 → read model 검증
```

## 5. 할 일

- [ ] ES 테스트 헬퍼 (이벤트 스트림 빌더, Aggregate 테스트 DSL — Given events / When command / Then events)
- [ ] Projection 테스트 헬퍼 (이벤트 → read model 검증)
- [ ] Contract 이벤트 픽스처 (FixtureMonkey)
- [ ] Testcontainers Kafka 공통 설정 추가

## 6. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: 레이어별로 테스트 프레임워크를 갈라(Core/Adapter=Kotest, Application=JUnit+AssertJ) 각 레이어 성격에 맞는 도구를 쓰고, 공통 픽스처·Testcontainers·ES DSL을 test-module에 모아 재사용한다.

**Steel-man**: 도메인은 BDD 서술이, 애플리케이션은 파라미터라이즈드 단위 검증이 자연스럽다는 판단은 실제 각 레이어의 관심사 차이를 반영한 합리적 분업이다.

### 숨은 가정

1. 팀이 Kotest DSL과 JUnit+AssertJ **두 관용구를 동시에** 유지보수할 만큼 여유가 있고, 레이어를 오가는 개발자가 컨텍스트 전환 비용을 감수한다.
2. 공통 픽스처를 `contract-module`(이벤트 픽스처)에 결합해도, contract 스키마가 진화해도 픽스처가 폭발적으로 깨지지 않는다.
3. ES/Projection E2E를 Testcontainers(Kafka+MySQL)로 도는 비용이 무트래픽 학습 규모 CI에서 감당 가능하다.

### 반론

**[일관성] · severity: high · 선례: 상위 근거 문서와의 직접 모순 (ADR-014)**
이 문서는 Application=JUnit이라 못박지만, 근거로 링크한 [[ADR-014]]는 §행위 명세에서 **usecase(application) 슬라이스를 Kotest `BehaviorSpec`으로** 깐다고 명시한다. 즉 애플리케이션 레이어의 프레임워크가 근거 ADR과 이 문서에서 서로 다르다. "레이어별 혼용"이라는 이 문서의 대표 주장 자체가 상위 결정과 충돌하며, 구현자는 어느 쪽을 따를지 알 수 없다. 3.레이어별 매핑 표는 CLAUDE.md 전략을 인용하지만 채택된 ADR을 인용하지 않아, 문서가 이미 낡은 입장을 복제하고 있을 가능성이 크다.

**[유지비] · severity: medium · 선례: no clear precedent — speculative concern**
프레임워크 혼용의 명분은 "레이어 성격 차이"인데, 이 문서 어디에도 JUnit이 애플리케이션에서 Kotest보다 나은 **구체적 이득**(예: JUnit @ParameterizedTest 특정 기능)이 적혀 있지 않다. 이득이 명시되지 않은 혼용은 학습 곡선 2배·목킹 관용구 2종(MockK는 공유되나 assertion·lifecycle이 갈림)·CI 러너 설정 2벌이라는 순비용만 남긴다. 무트래픽 학습 프로젝트에서 프레임워크 단일화(전부 Kotest)의 반대 근거로는 약하다.

**[결합] · severity: medium · 선례: 공유 픽스처의 고전적 취약성 (shared test fixture anti-pattern)**
공통 픽스처를 test-module에 모으고 `contract-module`에 의존시키면, 이벤트 계약 한 필드 변경이 이를 참조하는 **모든 레이어의 테스트를 동시에 붉게** 만든다. 픽스처 재사용의 이득과 "한 곳 바꾸면 전 계층이 깨지는" 취약성은 같은 동전의 양면이며, 이 문서는 후자에 대한 격리 전략(레이어별 픽스처 분리, 빌더 기본값 캡슐화)을 전혀 언급하지 않는다.

### 핵심 취약점

가장 치명적인 것은 **근거 ADR과의 프레임워크 모순**(반론 1)이다. 이 문서의 정체성인 "레이어별 혼용" 명제가 채택된 상위 결정과 어긋나 있어, 구현을 시작하는 순간 어느 쪽으로도 정합성이 깨진다 — 픽스처 결합이나 CI 비용은 그 다음 문제다.

### 가역성

reversible — 테스트 프레임워크 선택과 픽스처 배치는 프로덕션 계약이 아니므로 되돌릴 수 있으나, Application 레이어 테스트가 한 관용구로 상당량 쌓인 뒤 전환하면 재작성 비용이 누적된다(초기에 정하면 저렴, 늦게 정하면 비쌈).
