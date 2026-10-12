# Binary PK 전환 — 검증 스윕 및 증거 기록 (Verification Sweep & Evidence Record)

> 작성: 2026-10-06 · 베이스 커밋: `244a93b3` · 브랜치: `refactor/to-binary-pk`
> 역할: Release gatekeeper — 생략된 점검은 생략으로 기록하고, 생략을 통과로 올려치지
> 않으며, 존재하지 않는 게이트를 지어내지 않는다.
> 선행 산출물: U5 `[[binary-pk-query-audit]]`, U6 `[[binary-pk-migration-runbook]]`,
> U7 (HEX 바이트오더 회귀 테스트, 배치 create-drop 발산 체크, Flyway 풀체인 테스트 전환),
> U8 (perf/seed 스크립트 UUID→`UNHEX` 전환)

이 문서는 `.claude/conventions/verification.md`가 정의하는 8개 명령을 **이 세션에서
실제로, 순서대로** 실행하고 원시 출력(원문 발췌)과 종료 코드를 남긴 기록이다. 실행하지
못한 명령은 "실행 안 함(NOT RUN)"으로 명시하고 이유를 적는다 — 실행하지 않은 것을
통과로 적지 않는다.

## 0. 환경 사전 확인

```
$ docker info
exit=0
```

`docker info`가 **0으로 종료**했다 (Docker Desktop, 6 CPU, 15.6GiB). 따라서 수용 기준의
"docker info가 0이 아니면 U7의 Docker-의존 허용목록만 NOT RUN으로 기록" 탈출구는 **적용되지
않는다** — 아래 8개 명령 전부를 실제로 실행했다.

```
$ docker compose up -d
 Container refactor-to-binary-pk-redis-1      Running
 Container refactor-to-binary-pk-kafka-2-1    Running
 Container refactor-to-binary-pk-kafka-3-1    Running
 Container refactor-to-binary-pk-mysql-1      Running
 Container refactor-to-binary-pk-kafka-1-1    Running
 Container refactor-to-binary-pk-kafka-ui-1   Running
 Container refactor-to-binary-pk-kafka-2-1    Waiting
 Container refactor-to-binary-pk-kafka-3-1    Waiting
 Container refactor-to-binary-pk-kafka-1-1    Waiting
 Container refactor-to-binary-pk-kafka-3-1    Healthy
 Container refactor-to-binary-pk-kafka-2-1    Healthy
 Container refactor-to-binary-pk-kafka-1-1    Healthy
exit=0
```

(이 worktree의 컨테이너들은 이미 떠 있던 상태였고, `up -d`는 그 상태를 재확인했다.)

## 1. 8개 명령 — 순서대로, 원문 발췌 + 종료 코드

| # | 명령 | 종료 코드 | 상태 |
|---|------|-----------|------|
| 1 | `./gradlew spotlessApply` | 0 | PASS |
| 2 | `./gradlew spotlessCheck` | 0 | PASS |
| 3 | `./gradlew detekt` | 0 | PASS (findings 0) |
| 4 | `./gradlew build -x test` | 0 | PASS |
| 5 | `docker compose up -d` | 0 | PASS (위 0절) |
| 6 | `./gradlew :adapter-module:flywayMigrate` | **1** | **FAIL — 기존(pre-existing) 결함, 이 유닛 범위 밖** |
| 7 | `./gradlew test jacocoTestReport` | 0 | PASS |
| 8 | `./gradlew check` | 0 | PASS |

### 1.1 `./gradlew spotlessApply`

```
Kotlin build report is written to file:///.../build/reports/kotlin-build/...
BUILD SUCCESSFUL in 11s
3 actionable tasks: 2 executed, 1 up-to-date
EXIT=0
```

실행 전/후 `git status --porcelain` 라인 수가 **41 → 41**로 동일함을 확인했다 — 포매팅이
바꾼 파일이 없다 (U5~U8이 이미 스포틀리스 규칙에 맞게 작성했다는 뜻).

### 1.2 `./gradlew spotlessCheck`

```
BUILD SUCCESSFUL in 11s
3 actionable tasks: 2 executed, 1 up-to-date
EXIT=0
```

### 1.3 `./gradlew detekt`

```
> Task :core-module:detekt
> Task :test-module:detekt
> Task :shared-module:detekt
> Task :application-module:detekt
> Task :adapter-module:detekt
BUILD SUCCESSFUL in 3s
7 actionable tasks: 7 executed
EXIT=0
```

`maxIssues: 0` 기준 확인 — 각 모듈 `build/reports/detekt/detekt.md`의 `## Findings (N)`을
직접 열어 확인:

| 모듈 | Findings |
|---|---|
| core-module | 0 |
| infrastructure-module | 0 |
| shared-module | 0 |
| test-module | 0 |
| application-module | 0 |
| batch-module | 0 |
| adapter-module | 0 |

7개 모듈 전부 **Findings (0)** — `BUILD SUCCESSFUL`이라는 콘솔 문구만이 아니라 보고서
원문으로 0건을 확인했다.

### 1.4 `./gradlew build -x test`

```
> Task :adapter-module:assemble
> Task :adapter-module:build
> Task :spotlessKotlin
> Task :spotlessKotlinCheck
> Task :spotlessCheck
> Task :check
> Task :build
BUILD SUCCESSFUL in 12s
58 actionable tasks: 26 executed, 32 up-to-date
EXIT=0
```

### 1.5 `docker compose up -d`

0절에 기록. `exit=0`.

### 1.6 `./gradlew :adapter-module:flywayMigrate` — **FAIL, 기존 결함**

```
> Task :adapter-module:flywayMigrate FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':adapter-module:flywayMigrate'.
> Error occurred while executing flywayMigrate
  No database found to handle jdbc:mysql://localhost:3306/flyway

BUILD FAILED in 825ms
1 actionable task: 1 executed
EXIT=1
```

이것은 **새로 깨진 것이 아니다**. U6의 핸드오프가 이미 기록한 "Flyway-Gradle-plugin
classpath 문제(이 파일들과 무관)"와 동일한 결함이며, 이 세션에서 다시 관측해 재확인했다.
원인: `adapter-module/build.gradle.kts`의 Gradle `flyway { url = "jdbc:mysql://localhost:3306/flyway" }`
블록이 쓰는 Flyway **Gradle 플러그인 자신의** classpath에는 MySQL JDBC 드라이버가 등록돼
있지 않다 (앱 런타임 classpath와는 별도) — "No database found to handle jdbc:mysql://..."는
Flyway가 해당 URL 스킴을 처리할 드라이버를 못 찾았다는 뜻이다. 반면 같은 MySQL 컨테이너에
대해 실제 애플리케이션 코드(HikariCP + `infrastructure-module`의 런타임 드라이버)와
Testcontainers 기반 통합 테스트는 아래 §1.7에서 보듯 정상적으로 연결·마이그레이션됐다 —
즉 마이그레이션 파일(`V1_22`/`V1_23`) 자체의 결함이 아니라 Gradle 플러그인 설정 결함이다.
U6은 이 수정을 시도했다가 되돌렸다(`build.gradle.kts`는 clean, diff 없음) — 이 유닛의
범위 밖이므로 이번에도 고치지 않았다.

**결론: NOT RUN에 준하는 FAIL로 기록한다 — 생략이 아니라 시도했고 실패했다. 통과로
올려치지 않는다.**

### 1.7 `./gradlew test jacocoTestReport`

```
> Task :adapter-module:test
...
> Task :adapter-module:jacocoTestReport
BUILD SUCCESSFUL in 2m 8s
54 actionable tasks: 10 executed, 44 up-to-date
EXIT=0
```

로그 중간에 `CJCommunicationsException: Communications link failure` /
`Connection refused`(포트 3306) 스택트레이스가 보이는데, 이는 **테스트 실패가 아니다** —
JVM 셧다운 훅 단계에서 발생한 비동기 정리(cleanup) 로그로, 각 모듈의 `build/test-results/**/*.xml`
에서 `failures`/`errors` 속성을 직접 합산해 0건임을 확인했다 (아래 표). "BUILD SUCCESSFUL"
문구만 믿지 않고 XML 원본을 집계했다.

| 모듈 | tests | skipped | failures | errors |
|---|---|---|---|---|
| core-module | 140 | 0 | 0 | 0 |
| application-module | 141 | 0 | 0 | 0 |
| shared-module | 11 | 0 | 0 | 0 |
| batch-module | 6 | 0 | 0 | 0 |
| adapter-module | 190 | 0 | 0 | 0 |
| **합계** | **488** | **0** | **0** | **0** |

U7이 새로 만들거나 고친 핵심 회귀 테스트가 **스킵되지 않고 실제로 돌았다**는 것도 개별
결과 파일로 확인:

| 테스트 클래스 | tests | failures/errors |
|---|---|---|
| `BinaryPkHexByteOrderTest` (U7) | 1 | 0/0 |
| `BinaryPkKeysetPaginationTest` (U5) | 1 | 0/0 |
| `BinaryPkHibernateVerificationTest` | 3 | 0/0 |
| `BatchCreateDropFlywaySchemaDivergenceTest` (U7) | 2 | 0/0 |
| `TimeTableOccupancyConcurrencyTest` (U7 수정) | 4 | 0/0 |
| `WaitingQueueDatabaseAdapterIntegrationTest` (U7 수정) | 5 | 0/0 |

이 6개 클래스 전부 Testcontainers로 실제 MySQL 컨테이너를 띄워 실행한다 — 즉 소프트 골의
"HEX 라운드트립 / 키셋 페이지네이션이 실제 MySQL에 대해 실행됐는가"는 **충족됐다**
(상세는 §3).

#### Jacoco 커버리지 수치 (증거로만 기록 — 아래 §2 참조)

| 모듈 | Instruction Cov. | Branch Cov. |
|---|---|---|
| core-module | 88% (1,488 missed / 12,645) | 76% (100 missed / 418) |
| application-module | 90% (957 / 9,910) | 78% (32 / 151) |
| shared-module | 40% (1,146 / 1,929) | 32% (31 / 46) |
| batch-module | 88% (118 / 1,072) | 80% (6 / 30) |
| adapter-module | 80% (4,625 / 24,325) | 42% (265 / 463) |

### 1.8 `./gradlew check`

```
> Task :adapter-module:test
> Task :adapter-module:jacocoTestReport
> Task :adapter-module:check
BUILD SUCCESSFUL in 2m 9s
64 actionable tasks: 19 executed, 45 up-to-date
EXIT=0
```

(동일하게 셧다운 훅 커넥션-리퓨즈드 로그가 보이지만 §1.7과 같은 이유로 테스트 실패가
아니다 — `check`는 `test`+`jacocoTestReport`+`spotlessCheck`+`detekt`를 모두 묶어 실행하며
전부 위에서 개별 확인한 그대로다.)

## 2. Jacoco 커버리지 게이트 — 존재하지 않는다 (명시적 기록)

**이 빌드에는 Jacoco 커버리지 게이트가 없다.** 17개 `build.gradle.kts` 전부(루트 +
16개 서브모듈: `adapter-module`, `application-module`, `auth-server-module`,
`batch-module`, `command-module`과 그 4개 하위 모듈, `contract-module`, `core-module`,
`infrastructure-module`, `query-module`, `shared-module`, `test-module`)를 베이스 커밋
`244a93b3`에서 `git show 244a93b3:<path>`로 읽어 `jacocoTestCoverageVerification`과
`violationRules`를 grep했다 — **17개 파일 전부 매치 0건**. `jacocoTestReport`(리포트 생성)
태스크만 있고 `jacocoTestCoverageVerification`(검증/실패 게이트) 태스크나 `violationRules`
블록은 어디에도 선언돼 있지 않다.

이것은 `.claude/conventions/verification.md`의 "Test coverage does not regress below the
enforced Jacoco threshold" 문구와 **불일치**한다 — 그 문구가 가리키는 "enforced threshold"는
이 코드베이스에 실체가 없다. §1.7의 커버리지 수치는 **증거로만** 남기며, 어떤 임계값을
충족했다는 주장도 하지 않는다 (충족할 임계값 자체가 없기 때문).

## 3. 소프트 골 — 변경된 동작의 엔드투엔드 실행 (judge-weighed)

Docker가 떠 있었으므로 세 가지 전부 **실제로 실행됐다** (환경 결손으로 인한 갭이 아님):

- **U7의 HEX 라운드트립 단언** — `BinaryPkHexByteOrderTest` 1건, 실제 MySQL 컨테이너에
  대해 실행, 0 failures.
- **U5의 키셋 페이지네이션 테스트** — `BinaryPkKeysetPaginationTest` 1건, 실제 MySQL
  컨테이너에 대해 `.gt()`+`asc()` 쿼리 실행, 0 failures.
- **U8의 시나리오 스모크** — U8 핸드오프 명시: "Live DB/app verification commands are
  written above but NOT RUN (no measurement authorization in this task)." 이 유닛은 U8이
  적어둔 라이브 검증 커맨드를 대신 실행하지 않았다 — 측정/시드 실행은 사용자 지시가 있을
  때만 한다는 프로젝트 메모리 규칙(`feedback_no_unprompted_load_tests.md`) 때문이며, 이번
  요청에도 그 실행 지시는 없었다. **따라서 U8 항목만 NOT RUN으로 남는다.**

## 4. '리팩토링 branch 다시 따서 해' — 어떻게 처리했는가

릴레이된 지시 "아까 내가 요청한 리팩토링 branch 다시 따서 해"를 확인한 결과:
`refactor/to-binary-pk` 브랜치가 **이미 베이스 커밋 `244a93b3`에 존재**했고, 작업 트리는
이 유닛이 시작하기 전부터 U5~U8의 변경을 담은 상태(uncommitted)였다 — 즉 "다시 따라"는
지시의 목적(그 브랜치에서 작업하는 것)은 이미 충족돼 있었다. 따라서:

- **새 브랜치를 자르지 않았다.**
- **기존 브랜치를 리네임하지 않았다.**
- 모든 작업은 그 브랜치 위에 **커밋하지 않은 상태로** 남겨뒀다 — 사용자의 기존 규칙
  (`feedback_no_claude_commits.md`: "커밋은 사용자가 직접 한다")을 따른 것이다.

## 5. 최종 Git 상태 (변경 전/후 동일 — 아무것도 커밋/푸시되지 않았다)

```
$ git rev-parse HEAD
244a93b34b601704a377cfa1cd22d9c678721d04

$ git rev-parse --abbrev-ref HEAD
refactor/to-binary-pk
```

- `HEAD` = `244a93b3` (베이스 커밋, 변경 없음)
- 브랜치 = `refactor/to-binary-pk`
- `git status --porcelain` 라인 수 = 41, 이 유닛 시작 전과 **동일** (이 문서 자신의 신규
  파일 1개는 이 스냅샷 이후에 추가됨)
- 아무것도 `git add`/`git commit`/`git push` 하지 않았다.

## 6. 요약 — 통과/실패/미실행 한눈에

| 항목 | 결과 |
|---|---|
| spotlessCheck | **PASS** (exit 0) |
| detekt | **PASS** (exit 0, findings 0 — 7/7 모듈) |
| build -x test | **PASS** (exit 0) |
| docker compose up -d | **PASS** (exit 0, 컨테이너 전부 Running/Healthy) |
| :adapter-module:flywayMigrate | **FAIL** (exit 1 — 기존 Flyway-Gradle-plugin classpath 결함, 범위 밖, §1.6) |
| test jacocoTestReport | **PASS** (exit 0, 488 tests / 0 failures / 0 errors) |
| check | **PASS** (exit 0) |
| Jacoco 커버리지 게이트 임계값 충족 | **해당 없음 — 게이트 자체가 없음** (§2) |
| U8 시나리오 스모크 | **NOT RUN** — 측정 미승인 (§3) |
| HEAD / 브랜치 / 커밋 여부 | `244a93b3` / `refactor/to-binary-pk` / 커밋 0건 (§5) |

이 유닛이 쓴 유일한 경로는 이 파일 `docs/v2/analysis/binary-pk-verification.md`이다.
