## 테스트 전략

- `Slice Test`로 진행한다.
  - 빠른 테스트 속도
  - 각 layer 별 순수한 동작 체크.
  - Mock, Stubbing을 적극 활용한 테스트
- [fixturemonkey-naver](https://github.com/naver/fixture-monkey)를 사용하여 EdgeCase를 검증한다.


### Layer 별 사용하는 의존성과 이유

- Adapter:
  - Dependency
    - Kotest: kt기반 테스트 프레임워크
    - mockk: kt mocking library
    - kotest-extensions-spring: Kotest와 Spring을 연동해주는 Kotest 공식 확장 모듈
    - springmockk: Spring에서 mockk를 쉽게 쓸 수 있게 해주는 Spring 확장 라이브러리(MockkBean 등)
    - spring-boot-testcontainers: mysql, redis 등에 대한 테스트 환경 분리
  - 이유 
    - kotest에 대한 적응 
    - 표현력 높은 테스트 진행
    - BDD Style 적용
- Application
  - Dependency
    - JUnit: 전통적인 방식의 범용성이 높은 테스트 프레임워크
    - mockk: kt mocking library
    - AssertJ: JVM 기반 강력하고 직관적인 테스트 라이브러리. 주로 결과에 대한 검증을 진행할 때 사용
  - 이유
    - kt에서의 jUnit 사용에 대한 숙달
    - 행위 기반 검증과 결과 기반 검증에 대한 차이를 인식하고 적절하게 사용할 수 있는 방안을 알고 싶어서
- Core
  - Dependency
    - Kotest: kt 기반 테스트 프레임워크
  - 이유
    - kotest에 대한 적응
    - 다양한 Spec을 사용해서 상황에 맞는 테스트를 진행하기 위해서