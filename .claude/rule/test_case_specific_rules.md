# 테스트 케이스 작성 가이드

이 문서는 프로젝트의 테스트 케이스 작성 시 따라야 할 주요 규칙과 특이사항을 설명합니다.

## 1. BDD (행위 주도 개발) 스타일 준수

- **Kotest `BehaviorSpec` 활용**: `Given`, `When`, `Then` 블록을 사용하여 테스트의 시나리오를 명확하게 표현합니다. 이를 통해 비즈니스 요구사항과 테스트 코드를 일치시킬 수 있습니다.
- **상세한 테스트 설명**: `@DisplayName` 어노테이션이나 Kotest의 `test("설명")` 구문을 사용하여 각 테스트의 목적을 한글로 명확하게 기술합니다.

## 2. 테스트 프레임워크의 조합적 사용

본 프로젝트는 여러 테스트 프레임워크를 조합하여 사용하므로, 각 프레임워크의 역할을 이해해야 합니다.

- **Kotest**: 테스트의 전체적인 구조(Spec)와 단언(assertion) (`shouldBe`, `shouldHaveSize` 등)을 담당합니다.
- **JUnit 5**: `@Test`, `@ExtendWith` 등 테스트 실행 환경을 설정하는 어노테이션을 주로 사용합니다.
- **MockK**: 의존성을 모의(mock) 처리합니다.
    - **주의**: Spring 통합 테스트, 특히 `@WebMvcTest`에서는 Spring Context와 연동되는 `@MockkBean`을 사용해야 합니다. 일반 `@MockK`를 사용하면 의존성 주입이 실패합니다.

## 3. `FixtureMonkey`를 통한 테스트 데이터 생성

- 테스트에 필요한 객체나 데이터는 수동으로 생성하지 않고 `FixtureMonkey` 라이브러리를 사용하여 생성하는 것을 원칙으로 합니다.
- `FixtureMonkeyFactory`를 통해 일관된 방식으로 Fixture를 생성하고, `.giveMeBuilder()`나 `.giveMeOne()` 등을 활용하여 다양한 형태의 테스트 데이터를 만듭니다.

## 4. `Spring REST Docs`를 이용한 API 문서 자동화 

- **필수 사항**: Controller 테스트(`@WebMvcTest`) 작성 시, 기능 검증과 더불어 API 문서를 반드시 생성해야 합니다.
- `RestDocuments` 헬퍼 클래스를 사용하여 요청/응답 필드, 헤더, URL 파라미터 등을 상세히 기술해야 합니다. 이는 단순한 테스트를 넘어 프로젝트의 공식적인 산출물을 만드는 과정입니다.

## 5. Spring 테스트 환경 설정

- **`@ActiveProfiles("test")`**: 모든 테스트 클래스 상단에 `@ActiveProfiles("test")`를 명시하여 테스트 환경용 `application.yaml` 설정이 적용되도록 합니다.
- **정확한 어노테이션 사용**: 테스트의 목적에 맞게 `@WebMvcTest` (Controller 슬라이스 테스트), `@ExtendWith(SpringExtension::class)` (통합 테스트) 등을 구분하여 사용합니다.
