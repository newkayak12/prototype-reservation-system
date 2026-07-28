# Devil's Advocate · 10-test-module

> 대상: [[10-test-module]] §1-5. 근거: [[ADR-014-testing-strategy]]. 스트레스 테스트 전용 문서 — 결정 자체를 뒤집기 위한 것이 아니라 구현 전 결함을 드러내기 위함.

## Position

**Position**: 레이어별로 테스트 프레임워크를 갈라(Core/Adapter=Kotest, Application=JUnit+AssertJ) 각 레이어 성격에 맞는 도구를 쓰고, 공통 픽스처·Testcontainers·ES DSL을 test-module에 모아 재사용한다.

**Steel-man**: 도메인은 BDD 서술이, 애플리케이션은 파라미터라이즈드 단위 검증이 자연스럽다는 판단은 실제 각 레이어의 관심사 차이를 반영한 합리적 분업이며, 팀이 두 관용구를 모두 능숙히 다루고 contract 스키마가 안정적이며 CI 인프라 비용에 여유가 있을 때 가장 잘 작동한다.

## 숨은 가정

1. 팀이 Kotest DSL과 JUnit+AssertJ **두 관용구를 동시에** 유지보수할 만큼 여유가 있고, 레이어를 오가는 개발자가 컨텍스트 전환 비용을 감수한다.
2. 공통 픽스처를 `contract-module`(이벤트 픽스처)에 결합해도, contract 스키마가 진화해도 픽스처가 폭발적으로 깨지지 않는다.
3. ES/Projection E2E를 Testcontainers(Kafka+MySQL)로 도는 비용이 무트래픽 학습 규모 CI에서 감당 가능하다.

## 반론

1. **[structural] · severity: critical** — 이 문서는 §3 표에서 "Application = JUnit"이라 못박지만, 근거로 링크한 [[ADR-014-testing-strategy]]는 §행위 명세(BDD)에서 **usecase(application) 슬라이스를 Kotest `BehaviorSpec`으로** 깐다고 명시한다(ADR-014 원문: "usecase(application): 포트를 목으로 두고 오케스트레이션 행위 검증" — Kotest `BehaviorSpec` 절 아래). 즉 애플리케이션 레이어의 테스트 프레임워크가 근거 ADR과 이 문서에서 서로 다르다. "레이어별 혼용"이라는 이 문서의 대표 주장 자체가 상위 결정과 정면 충돌하며, 구현자는 어느 쪽을 따를지 문서만 봐서는 알 수 없다. §3 하단 "레이어별 매핑(CLAUDE.md 전략)" 표는 프로젝트 전역 CLAUDE.md를 인용하지만 정작 채택된 ADR을 인용하지 않는다 — 문서가 ADR 채택 이전의 낡은 입장을 그대로 복제하고 있을 가능성이 크다는 뜻이다. severity를 critical로 두는 이유는 이것이 구현 착수 시점에 반드시 터지는 결정성(deterministic) 충돌이지, 발생할 수도 있는 리스크가 아니기 때문이다.

2. **[assumption] · severity: medium** — 프레임워크 혼용의 명분은 "레이어 성격 차이"인데, 이 문서 어디에도 JUnit이 애플리케이션에서 Kotest보다 나은 **구체적 이득**(예: JUnit `@ParameterizedTest` 고유 기능)이 적혀 있지 않다. 이득이 명시되지 않은 혼용은 학습 곡선 2배 · 목킹 관용구 2종(MockK는 공유되나 assertion·lifecycle 관용구가 갈림) · CI 러너 설정 2벌이라는 순비용만 남긴다. 무트래픽 학습 프로젝트에서 프레임워크 단일화(전부 Kotest)에 반대할 근거로는 약하다. 선례: no clear precedent — speculative concern.

3. **[structural] · severity: medium** — 공통 픽스처를 test-module에 모으고 `contract-module`에 의존시키면, 이벤트 계약 한 필드 변경이 이를 참조하는 **모든 레이어의 테스트를 동시에** 붉게 만든다. 픽스처 재사용의 이득과 "한 곳 바꾸면 전 계층이 깨지는" 취약성은 같은 동전의 양면이며, 이 문서는 후자에 대한 격리 전략(레이어별 픽스처 분리, 빌더 기본값 캡슐화, 버전별 픽스처 스냅샷 등)을 전혀 언급하지 않는다. 선례: 공유 test fixture가 전 스위트의 단일 실패점이 되는 패턴은 여러 대형 코드베이스에서 반복적으로 보고된 anti-pattern이다.

## 다중 페르소나 공격

**레이어를 오가며 테스트를 작성하는 개발자**: Application 코드를 만지다가 Core나 Adapter로 넘어갈 때마다 assertion 문법(`assertThat` vs Kotest matcher)과 스펙 구조(JUnit `@Test` vs Kotest `BehaviorSpec`)를 전환해야 한다. 게다가 이 문서와 ADR-014가 Application 레이어 프레임워크를 서로 다르게 규정하고 있으니, 실제로 어떤 관용구로 새 테스트를 시작해야 하는지조차 문서만으로는 답이 없다. "레이어 성격에 맞는 도구"라는 명분은 구현자 입장에서 "매번 어느 문서를 믿어야 하는지 판단해야 하는 부담"으로 되돌아온다.

**CI/빌드 오너**: Testcontainers(MySQL+Kafka)를 ES/Projection E2E에 상시 돌리는 비용은 로컬 개발 루프와 CI 파이프라인 모두에 실체가 있다. 이 문서는 "무트래픽 학습 규모"라는 전제를 숨은 가정으로만 깔고 있을 뿐, 어떤 테스트가 CI 필수(매 커밋)이고 어떤 것이 정기/통합(느슨한 주기)인지 이 문서 자체에서는 구분하지 않는다 — ADR-014의 6범주 게이트 표를 참조해야 알 수 있는데, 이 문서는 그 매핑을 되풀이하지 않는다. 결과적으로 test-module 구현자가 임의로 게이트를 정할 위험이 있다.

**contract-module 오너**: 이벤트 픽스처를 test-module이 `contract-module`에 의존해 만든다는 결정은, contract 스키마 변경 시 파급 범위를 test-module 관리자가 아니라 contract-module 오너가 사실상 통제하게 만든다. 그런데 이 문서는 그 파급을 완화할 책임을 어느 쪽이 지는지 명시하지 않는다 — "픽스처가 폭발적으로 깨지지 않는다"는 가정이 깨지는 순간, 두 모듈 오너 사이에 책임 공백이 생긴다.

## 핵심 취약점

가장 치명적인 것은 **근거 ADR과의 프레임워크 모순**(반론 1)이다. 이 문서의 정체성인 "레이어별 혼용" 명제가 채택된 상위 결정(ADR-014)과 정면으로 어긋나 있어, 구현을 시작하는 순간 두 문서 중 어느 쪽을 따라도 나머지 한쪽과의 정합성이 깨진다. 이는 §2 근거 표기에 "[[ADR-014]] (테스트 전략)"이라고 명시적으로 링크해둔 문서 자신의 주장이 그 링크가 가리키는 대상과 모순된다는 점에서 더 심각하다 — 픽스처 결합이나 CI 비용 같은 실행상의 문제는 이 모순이 해소된 뒤에나 의미를 갖는 이차 문제다.

## 가역성

reversible — 테스트 프레임워크 선택과 픽스처 배치는 프로덕션 계약이 아니므로 되돌릴 수 있으나, Application 레이어 테스트가 한쪽 관용구(JUnit이든 Kotest든)로 상당량 쌓인 뒤 전환하면 재작성 비용이 누적된다 — 지금(ADR-014와의 불일치를 해소하는 시점)에 정하면 저렴하고, 늦게 정할수록 비용이 커지는 창(window)이다.
