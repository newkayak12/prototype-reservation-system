# Detekt

> - Detekt는 Kotlin 코드 품질 검사(정적 분석)를 위한 오픈소스 도구입니다.
> - 주로 코딩 스타일, 버그, 냄새(code smell), 복잡도 등을 자동 진단해 리포트합니다.

### 1. Settings
#### build.gradle.kts
```kotlin
    detekt {
        config.setFrom(files("$rootDir/detekt.yaml"))
        buildUponDefaultConfig = true
    }
```
#### detekt.yaml
```yaml
build:
  maxIssues: 0                        # 허용되는 최대 이슈 수 (0이면 이슈 발생 시 빌드 실패)
config:
  validation: true                    # 설정 파일 자체의 유효성 검사 활성화
performance:
  SpreadOperator:
    active: false                     # 성능 관련: Spread 연산자 검사 비활성화
style:
  WildcardImport:
    active: true                      # import * 와 같은 와일드카드 import 사용 검사 활성화
  NewLineAtEndOfFile:
    active: true                      # 파일 끝에 빈 줄 없을 경우 경고 활성화
  UnusedImports:
    active: true                      # 사용하지 않는 import가 있으면 경고 활성화
  UnusedPrivateMember:
    active: false                     # 사용하지 않는 private 함수/멤버 검사 비활성화
  UnusedPrivateProperty:
    active: false                     # 사용하지 않는 private 프로퍼티 검사 비활성화
naming:
  ClassNaming:
    active: true                      # 클래스 명명 규칙 검사 활성화
    classPattern: '[A-Z][a-zA-Z0-9]*' # 클래스명은 대문자로 시작, 알파벳/숫자 조합만 허용
complexity:
  LongMethod:
    active: true                      # 함수가 너무 길 경우 경고 활성화
```

### 2. Apply
#### 1. Editing Phase
- 수동 적용: `gradle detekt`을 직접 입력하여 포매팅한다.
#### 2. Commit Phase
- [github pre-commit hook](../../pre_commit/gitPreCommitHook.md): `gradle detekt`을 커밋 전에 실행하여 적용한다.
