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
| `kotest-runner-junit5`·`-assertions`·`-framework`·`-property` | `5.9.0` | Core(도메인)·Adapter·**Application** 테스트 — `BehaviorSpec`([[ADR-014]]) |
| `kotest-extensions-spring` | `1.3.0` | Kotest + Spring 컨텍스트 |
| `mockk` / `springmockk` | `1.13.10` / `4.0.2` | 목킹 (usecase 슬라이스는 포트를 목으로 — [[ADR-014]]) |
| `fixture-monkey-starter-kotlin` / `-kotest` | `1.1.11` | property-based·엣지 케이스 |
| `testcontainers-mysql`·`-kafka`·`-junit` | `2.0.3` | 실제 인프라 통합 |
| `spring-boot-testcontainers` | `3.4.5` | Testcontainers 연동 |
| `spring-kafka-test` | `3.3.1` | 임베디드 Kafka |
| `spring-security-test` | (Boot BOM) | 인증 컨텍스트 |

### 레이어별 매핑 ([[ADR-014]] — 행위 명세 3슬라이스)

| 레이어 | 프레임워크 |
|--------|-----------|
| Adapter(controller standalone) | Kotest `BehaviorSpec` + standalone MockMvc + Testcontainers(MySQL/Redis/**Kafka**) |
| Application(usecase) | Kotest `BehaviorSpec` + MockK (포트를 목으로 오케스트레이션 행위 검증) |
| Core(service/도메인) | Kotest `BehaviorSpec` (도메인 불변식을 비즈니스 언어로 고정) |
| Projection(07) | Testcontainers Kafka+MySQL E2E |

> 세 슬라이스(usecase·service·controller) 전부 Kotest `BehaviorSpec`으로 통일한다 — CLAUDE.md의 레이어별 프레임워크 혼용 전략은 [[ADR-014]] 채택으로 supersede됐다.

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

**Position**: Core·Adapter·Application 세 슬라이스 모두 Kotest `BehaviorSpec`으로 통일하고([[ADR-014]]), 공통 픽스처·Testcontainers·ES DSL을 test-module에 모아 재사용한다.

**Steel-man**: 세 슬라이스를 한 관용구로 통일하면 학습 곡선·CI 러너 설정이 하나로 줄고, 레이어를 오가는 개발자가 매번 다른 assertion/lifecycle 문법으로 전환할 필요가 없다.

### 숨은 가정

1. 공통 픽스처를 `contract-module`(이벤트 픽스처)에 결합해도, contract 스키마가 진화해도 픽스처가 폭발적으로 깨지지 않는다.
2. ES/Projection E2E를 Testcontainers(Kafka+MySQL)로 도는 비용이 무트래픽 학습 규모 CI에서 감당 가능하다.

### 반론

**[일관성] · severity: high — 해소됨(2026-07-19 동기화)**
이 반론은 §3이 "Application=JUnit"이라 못박아 근거 [[ADR-014]](usecase 슬라이스=Kotest `BehaviorSpec`)와 정면 모순됐을 때 성립했다. §3 라이브러리 표·레이어별 매핑을 ADR-014대로 **3슬라이스 전부 Kotest `BehaviorSpec`**으로 갱신했다 — 이제 이 문서와 근거 ADR이 정합한다.

**[결합] · severity: medium · 선례: 공유 픽스처의 고전적 취약성 (shared test fixture anti-pattern)**
공통 픽스처를 test-module에 모으고 `contract-module`에 의존시키면, 이벤트 계약 한 필드 변경이 이를 참조하는 **모든 레이어의 테스트를 동시에 붉게** 만든다. 픽스처 재사용의 이득과 "한 곳 바꾸면 전 계층이 깨지는" 취약성은 같은 동전의 양면이며, 이 문서는 후자에 대한 격리 전략(레이어별 픽스처 분리, 빌더 기본값 캡슐화)을 전혀 언급하지 않는다.

### 핵심 취약점

근거 ADR과의 프레임워크 모순(구 반론 1)은 해소됐다. **남은 핵심 취약점은 공유 픽스처 결합**(반론 2) — contract 스키마 변경이 격리 전략 없이 전 레이어 테스트를 동시에 깨뜨릴 수 있다.

### 가역성

reversible — 테스트 프레임워크 선택과 픽스처 배치는 프로덕션 계약이 아니므로 되돌릴 수 있으나, Application 레이어 테스트가 한 관용구로 상당량 쌓인 뒤 전환하면 재작성 비용이 누적된다(초기에 정하면 저렴, 늦게 정하면 비쌈).
