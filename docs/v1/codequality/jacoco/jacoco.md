# Jacoco를 통한 SonarQube CodeCov

## SonarQube
### 1. 정의
> - SonarQube는 소스 코드의 정적 분석(Static Analysis)을 수행하는 오픈소스 플랫폼입니다.
> - 코드 품질, 버그, 보안 취약점, 코드 스멜(code smell), 중복, 스타일 위반 등 다양한 품질 문제를 자동으로 진단하고 대시보드로 제공합니다.

### 2. 도입 이유
1. Detekt와 유사하지만 Detekt가 잡아주지 못하는 더 넓은 범위를 커버
2. CI phase에서 SonarQube를 통한 코드 퀄리티 검사를 받기 위해서
3. 또한 SonarQube의 `Quality Gate`를 통해서 일정 수준 이하가 되면 `MergeBlock`을 하기 위해서

### 3. 적용
- [Pull Request Ci](../../workflows/pull-request-ci.yaml)에서 테스트 중 생긴 `jacocoReport`를 바탕으로 진행한다.
- `github action`의 `sonarsource/sonarcloud-github-action@v2`를 사용한다.


## CodeCov
### 1. 정의 
> - Codecov는 소스 코드의 **테스트 커버리지(Test Coverage)**를 수집·분석·시각화하는 클라우드 서비스입니다.
> - CI 빌드 결과로 생성된 커버리지 리포트를 업로드하면, 라인별 커버리지, 변경 코드 커버리지 등 상세 통계를 웹 대시보드에서 제공합니다.

### 2. 도입 이유
1. 테스트 코드 커버리지가 절대적인 척도는 아니지만, 최대한 넓은 범위의 테스트를 커버하기 위해서
2. 정량적 수치를 통해서 테스트의 범위를 시각화 하기 위해서 

### 3. 적용
- [Pull Request Ci](../../workflows/pull-request-ci.yaml)에서 테스트 중 생긴 `jacocoReport`를 바탕으로 진행한다.
- `github action`의 `codecov/codecov-action@v4`를 사용한다.
