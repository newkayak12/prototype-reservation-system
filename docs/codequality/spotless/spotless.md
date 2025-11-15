# Spotless

> - Spotless는 코드 포매팅 및 정적 코드 스타일 자동화 툴
> - 주로 코드 스타일 일관성을 보장하기 위해 사용합니다.
    >   - 코드 스타일 체크 및 교정
>   - 자동 포매팅
> - Gradle 플러그인 형태로 제공되어 빌드 파이프라인에 통합할 수 있습니다.

### 1. Settings
```kotlin
spotless {
    kotlin {
        target("**/*.kt") // 대상 파일: 모든 .kt 파일
        ktlint("1.2.1")  // Ktlint 1.2.1 버전 포매터 사용
        trimTrailingWhitespace() // 라인 끝 공백 자동 제거
        indentWithSpaces(4)      // 공백 4칸 들여쓰기 강제
        endWithNewline()         // 파일 끝에 빈 줄 삽입
    }
}
```

### 2. Apply
#### 1. Editing Phase
- 수동 적용: `gradle spotlessKotlinApply`을 직접 입력하여 포매팅한다.
#### 2. Commit Phase
- [github pre-commit hook](../../pre_commit/gitPreCommitHook.md): `gradle spotlessKotlinApply`을 커밋 전에 실행하여 적용한다.
#### 3. Github Action Phase
- workflow 중 [Pull_Request_Check_Style](workflows/pull-request-check-style.yaml)에서 `./gradlew  spotlessKotlinCheck`를 통해서 체크하고 실패하면 빌드를 종료한다.
