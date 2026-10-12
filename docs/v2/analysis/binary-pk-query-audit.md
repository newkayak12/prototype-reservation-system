# Binary PK 식별자 비교·정렬·bind-path 감사 (Query / Ordering / Bind-path Audit)

> 작성: 2026-10-06 · 베이스 커밋: `244a93b3` · 브랜치: `refactor/to-binary-pk`
> 입력: [[binary-pk-identifier-inventory]] §5 · [[binary-pk-association-verdicts]] ·
> [[ADR-028-binary-pk-identifier-storage]] 결정 4·잔존 위험 3 · U4 핸드오프
> (`TimeBasedPrimaryKey`/`RestaurantWorkingDayId` → `UuidBinaryIdentifierType`,
> 그 외 CONVERT 컬럼 → `UuidBinaryConverter`)
>
> 역할: 각 식별자 표현식이 "변환된 컬럼과 실제로 비교되는가"를 먼저 확인하고,
> 행 정렬(순서)을 계약의 일부로 다루는 쿼리 리뷰어 관점으로, QueryDSL/JPQL/배치
> 커서 전부를 사이트별로 판정한다. 이 문서 자체는 프로덕션 코드를 바꾸지
> 않는다 — 모든 판정이 "변경 불필요"로 나왔고, 근거는 각 절에 있다.

## 0. 요약 결론

| 영역 | 변경 필요? |
|---|---|
| `Expressions.constant` 2곳 (FindTimeTableAndOccupancyRepository) | **아니오** — 둘 다 projection literal, 비교에 쓰이지 않음 |
| `Expressions.FALSE` 5곳 | **아니오** — 전부 boolean literal, 식별자 아님 |
| 정렬 의존 5곳 (Restaurant 2 + TimeTableStepConfig 2 + CursorReader) | **아니오** — 바이트 순서가 기존 콜레이션 순서를 보존한다는 논증 + 통합 테스트로 핀 완료. 단, `schedule.id`는 이번 전환 대상이 아니라 비교 자체가 무의미(§3.3) |
| `@Query` JPQL 4개 레포지토리 | **아니오** — 전부 엔티티 속성 경로 비교라 매핑된 타입(UserType/Converter)을 그대로 따라간다 |
| **발견된 회귀(이 유닛 범위 밖, 수정하지 않음)** | `TimeTableOccupancyConcurrencyTest`, `WaitingQueueDatabaseAdapterIntegrationTest` — §5 |

---

## 1. `Expressions.constant` 2곳 — `FindTimeTableAndOccupancyRepository.kt`

```kotlin
// :22-36
Projections.constructor(
    FindTimeTableAndOccupancyResult::class.java,
    Expressions.constant(inquiry.timeTableId),              // :25
    timeTableEntity.restaurantId,
    ...
    Expressions.constant(inquiry.timeTableOccupancyId),      // :33
    timeTableOccupancyEntity.userId,
    timeTableOccupancyEntity.occupiedDatetime,
)
```

| # | file:line | 역할 | 비교에 쓰이는가? | 판정 | 변경 필요? |
|---|---|---|---|---|---|
| 1 | `FindTimeTableAndOccupancyRepository.kt:25` | `Projections.constructor` SELECT 리스트의 한 항목 — `inquiry.timeTableId`를 그대로 DTO 필드에 echo | **아니오** | **projection literal only** | 아니오 |
| 2 | `FindTimeTableAndOccupancyRepository.kt:33` | 위와 동일, `inquiry.timeTableOccupancyId` | **아니오** | **projection literal only** | 아니오 |

실제 식별자 술어는 같은 메서드의 `.where(...)`(:41-49)에 있다 — `Expressions.constant`가 아니라
`TimeTableIdQuerySpec.timeTableIdEq` / `TimeTableOccupancyIdQuerySpec.timeTableOccupancyIdEq`를
거친다:

```kotlin
// TimeTableIdQuerySpec.kt:6
fun timeTableIdEq(timeTableId: String) = timeTableEntity.identifier.eq(timeTableId)
// TimeTableOccupancyIdQuerySpec.kt:6-7
fun timeTableOccupancyIdEq(timeTableOccupancyId: String) =
    timeTableOccupancyEntity.identifier.eq(timeTableOccupancyId)
```

둘 다 `entity.identifier`(`@Id`, `UuidBinaryIdentifierType`)와 `String` 파라미터를 `.eq()`로
비교한다 — QueryDSL이 생성하는 JPQL에서 바인드 파라미터의 타입은 비교 대상인 엔티티 속성 경로의
매핑 타입(`UserType`)을 따라가므로, 평문 36자 문자열이 아니라 `UuidBinaryIdentifierType.nullSafeSet`를
거쳐 BINARY(16)으로 바인딩된다. 또한 join 조건(`FindTimeTableAndOccupancyRepository.kt:40`)
`timeTableEntity.identifier.eq(timeTableOccupancyEntity.timeTable.identifier)`도 양쪽 다
엔티티 경로(둘 다 같은 메커니즘)라 타입 불일치가 없다 — **이 세 지점(두 QuerySpec + join)이
"변환된 컬럼과 비교되는" 실제 식별자 술어이고, `Expressions.constant` 2곳은 그 술어 결과를
DTO에 담아 돌려줄 뿐이다.**

`Expressions.constant`가 비교에 쓰였다면(예: `.where(Expressions.constant(x).eq(entity.identifier))`)
QueryDSL이 그 리터럴의 **Java 타입**(`String`)으로만 파라미터를 바인딩해 컨버터를 거치지 않을
위험이 있었을 것이다 — 그런 사이트가 이번 조사에서 하나도 없다는 것 자체가 이 두 곳을
"변경 불필요"로 판정하는 근거다.

---

## 2. 그 외 `Expressions.*` 사용 전부 (전수)

```
$ grep -rn 'Expressions\.' infrastructure-module/src/main --include '*.kt'
UserQuerySpec.kt:37        } ?: Expressions.FALSE
MenuPhotoQuerySpec.kt:10   if (it.isEmpty()) return@let Expressions.FALSE
MenuQuerySpec.kt:10        ?: Expressions.FALSE
MenuQuerySpec.kt:14        ?: Expressions.FALSE
FindTimeTableAndOccupancyRepository.kt:25,33  (§1)
CompanyQuerySpec.kt:20     ?: Expressions.FALSE
```

| # | file:line | 판정 | 비고 |
|---|---|---|---|
| 1 | `UserQuerySpec.kt:37` | **not an identifier** | `Expressions.FALSE` — 조건이 비어 있을 때 "항상 거짓"으로 필터를 막는 boolean literal |
| 2 | `MenuPhotoQuerySpec.kt:10` | **not an identifier** | 동일 패턴 (빈 ID 리스트 가드) |
| 3 | `MenuQuerySpec.kt:10` | **not an identifier** | 동일 |
| 4 | `MenuQuerySpec.kt:14` | **not an identifier** | 동일 |
| 5 | `CompanyQuerySpec.kt:20` | **not an identifier** | 동일 |

5곳 전부 `BooleanExpression`이고 식별자 비교와 무관하다 — 작업 불필요.

---

## 3. ORDERING SEMANTICS — 바이트 순서가 콜레이션 순서를 보존하는가

전환 전: `prototype_reservation` DB는 `utf8mb4_general_ci`(`docker-entrypoint-initdb.d/init.sql:1`).
`general_ci`는 ASCII 영역에서 대소문자만 접고 코드포인트 순서를 보존하며, `UuidGenerator`가
만드는 값은 항상 소문자라 대소문자 접기가 개입하지 않는다 — 즉 "왼쪽부터 한 글자씩 비교"와
동치다. 모든 UUID 문자열은 하이픈이 같은 위치(8/13/18/23)에 오므로 "하이픈 포함 비교"는
"하이픈 제거 32자 16진수 비교"와 동치이고, 그 32자 16진수를 그대로 16바이트로 디코딩해
빅엔디언으로 비교하는 것과 같다. `UuidBinaryCodec`이 바로 이 순서로(스왑 없이) 인코딩한다
(ADR-028 "바이트 순서 근거"). **결론: 텍스트 순서와 바이트 순서는 동치다** — 이는 증명이
아니라 논증이므로(콜레이션 가중치 테이블의 예외까지 확인하지 않음, ADR-028이 명시), §3.4의
통합 테스트로 실제 핀한다.

### 3.1 `RestaurantQuerySpec.kt:13` — `restaurantEntity.identifier.gt(identifier)`

```kotlin
fun identifierAfter(identifier: String): BooleanExpression? =
    if (StringUtils.hasText(identifier)) restaurantEntity.identifier.gt(identifier) else null
```

`restaurantEntity.identifier`는 `TimeBasedPrimaryKey.identifier`(§U4, `UuidBinaryIdentifierType`,
BINARY(16))다 — **변환된 컬럼과 비교된다.** 위 논증에 따라 바이트 비교 결과는 전환 전
콜레이션 비교 결과와 같다 — **변경 불필요.**

### 3.2 `FindRestaurantsRepository.kt:157` — `.orderBy(restaurantEntity.identifier.desc())`

3.1과 짝을 이루는 `ORDER BY` — 같은 컬럼, 같은 전환 영향, 같은 결론 — **변경 불필요.**

> **별도 관찰 (범위 밖, 고치지 않음):** `identifierAfter`(`.gt()`)와 이 `orderBy(...desc())`의
> 조합은 역방향이다 — 내림차순으로 보여주면서 "이 값보다 **큰** 것"을 계속 요청하면, 다음
> 페이지의 커서(이번 페이지의 마지막 = 가장 작은 값)로 다시 질의했을 때 **이번 페이지를
> 다시 돌려준다**(건너뛰지도 중복 제거되지도 않는다 — 같은 구간을 영원히 반복한다). 이것은
> BINARY(16) 전환이 만든 문제가 아니라 베이스 커밋(`244a93b3`)부터 있던 방향 불일치이고,
> 이번 조사의 범위(식별자 비교·정렬의 **전/후 동치성**)를 벗어난 애플리케이션 레벨 정합성
> 질문이라 고치지 않는다 — 발견 사실만 기록한다. `batch-module`의 `TimeTableStepConfig`가
> 쓰는 `.gt()` + `.asc()` 조합이 표준적인 전진 키셋 패턴이다(§3.3 비교).

### 3.3 `TimeTableStepConfig.kt:48,50` — `scheduleEntity.restaurantId.gt(it)` / `.orderBy(...asc())`

```kotlin
// ScheduleEntity.kt:19-22
class ScheduleEntity(
    @Id @Column(name = "id")
    val restaurantId: String,   // schedule.id = restaurant.id의 사본 (FK가 아니라 공유 PK)
    ...
)
```

`ScheduleEntity`는 `TimeBasedPrimaryKey`를 상속하지 않고, 그 자신의 `restaurantId`는
[[binary-pk-identifier-inventory]] 53개 표면 중 **어디에도 없다** — U5(`V1_22`/`V1_23`)가
명시적으로 "`schedule` 테이블은 건드리지 않는다"고 기록했다
(`V1_22__binary_pk_identifier_expand.sql:28-34`, `V1_23__binary_pk_identifier_contract.sql:45`).
즉 `schedule.id`는 지금도 `VARCHAR(128)`이고 Kotlin 매핑도 평문 `String` `@Column`이며
어떤 컨버터/`UserType`도 걸려 있지 않다.

**판정: 이 사이트는 "변환된 컬럼과 비교"되지 않는다 — 비교·정렬 모두 전환 전과 글자 하나
다르지 않은 VARCHAR 문자열 비교다.** "바이트 순서가 텍스트 순서를 보존하는가"라는 질문
자체가 이 사이트에는 적용되지 않는다(적용 대상인 컬럼이 아직 바뀌지 않았으므로). **변경
불필요** — 단, 이 사이트가 참조하는 `restaurant.id` 자체는 이미 BINARY(16)로 바뀌었다는
**불일치**(한쪽은 BINARY, 복제된 `schedule.id`는 여전히 VARCHAR)는 inventory/ADR 소유자가
닫아야 할 별도 갭이고(V1_22 주석이 이미 그렇게 기록), 이 감사는 그 사실만 재확인한다.

### 3.4 `QueryDslCursorItemReader.kt:15,37,42,46` — `lastId: String?`, `ExecutionContext` 직렬화

이 리더의 유일한 현재 사용처는 3.3의 `scheduleEntityItemReader`(`TimeTableStepConfig.kt:33-55`,
`grep -rl QueryDslCursorItemReader batch-module/src/main`로 확인 — 3개 파일 중 나머지 둘은
리더 정의 자신과 그걸 감싸는 `TimeTableCompositeItemReader`)다. §3.3의 결론(`schedule.id`는
전환 대상이 아님)이 그대로 적용된다 — **지금 당장은 바이트 순서 변화의 영향을 받지 않는다.**

다만 이 리더는 제네릭(`QueryDslCursorItemReader<T>`)이고 `idExtractor: (T) -> String` /
`lastId: String?`로 커서를 **항상 문자열로** `ExecutionContext`에 저장한다 — 장래에 변환된
식별자 컬럼(예: `restaurant.id` 자체)을 이 리더로 커서 처리하게 되면, [[binary-pk-identifier-inventory]]
§5가 이미 경고한 위험(배포 중 재시작되는 Job의 중간 체크포인트가 전/후 바이트 비교 규칙에
걸쳐 있으면 안전하지 않음)이 그대로 유효해진다. **변경 불필요(현재 사용처 기준) — 이
리더를 새로운 CONVERT 대상 컬럼에 쓰게 되는 순간 이 결론은 재검토해야 한다**는 조건을
명시적으로 남긴다.

### 3.5 핀 테스트 — `BinaryPkKeysetPaginationTest`

`adapter-module/src/test/kotlin/com/reservation/persistence/binarypk/BinaryPkKeysetPaginationTest.kt`
(이 감사의 산출물, 신규 파일). `RestaurantEntity.identifier`(BINARY(16) 전환됨)에 7개 row를
심은 뒤, production 함수 `RestaurantQuerySpec.identifierAfter`(`.gt()`, 수정 없이 재사용)를
전진 방향 표준 키셋 패턴(`orderBy(...asc())` — 3.2의 역방향 관찰을 피해 3.3과 같은 방향으로
맞춤)으로 페이지 크기 3씩 반복 조회하고, 전체를 한 번에 읽은 "ground truth" 순서와 바이트
단위로 같은지, 그리고 방문한 identifier 집합에 중복이 없는지를 확인한다.

**실행 결과(Testcontainers MySQL 8.0, 이 워크트리에서 직접 실행):**
```
$ ./gradlew :adapter-module:test --tests "com.reservation.persistence.binarypk.BinaryPkKeysetPaginationTest"
BUILD SUCCESSFUL
```
GREEN — 1 test, 0 failed. BINARY(16) `.gt()` + `orderBy(asc())` 키셋 페이지네이션이 변환된
식별자에서 모든 행을 정확히 한 번씩, 스킵·중복 없이, 기대한 순서로 돌려준다는 것을 실측으로
핀했다.

---

## 4. `@Query` JPQL 전수 — 4개 레포지토리

| 레포지토리 | 메서드 | JPQL이 참조하는 식별자 경로 | 메커니즘 | 판정 |
|---|---|---|---|---|
| `ReservationJpaRepository` | `existsReservation` | `reservation.timeTable.timeTableId` / `reservation.timeTableOccupancy.timetableOccupancyId` | `@Embedded` VO(`ReservationTimeTable`/`ReservationTimeTableOccupancy`)의 `@Column` + `UuidBinaryConverter`(일반 컬럼이라 `@Convert` 정상 적용, ADR-028 U4 결과) | **엔티티 경로 비교, 변경 불필요** |
| `WaitingQueueJpaRepository` | `countPreceding`/`countAdmitted`/`findWaitingForUpdate`/`findWaitingSlots` | `waitingQueue.restaurantId` (SLOT_CONDITION, 3개 쿼리에서 재사용) | `WaitingQueueEntity.restaurantId`, 일반 `@Column` + `UuidBinaryConverter` | **엔티티 경로 비교, 변경 불필요** |
| `WaitingQueueJpaRepository` | `findByRestaurantIdAnd...` (파생 메서드, `@Query` 아님) | 동일 `restaurantId`/`userId` | 동일 | **엔티티 경로 비교, 변경 불필요** (참고용으로 같이 기재 — `@Query`는 아니지만 같은 바인딩 메커니즘) |
| `TimeTableJpaRepository` | `findBookableTimeTable` | `timetable.restaurantId` + 서브쿼리 `timetableOccupancy.timeTable.identifier = timetable.identifier` | `restaurantId`는 `UuidBinaryConverter`(일반 컬럼), `identifier`끼리 비교는 양쪽 다 `UuidBinaryIdentifierType`(`@Id`) | **엔티티 경로 비교, 변경 불필요** |
| `TimeTableJpaRepository` | **`claimTimeTable`** (`@Modifying` 벌크 UPDATE) | `WHERE timetable.identifier = :identifier` | `TimeTableEntity.identifier`(`@Id`, `UuidBinaryIdentifierType`) — **벌크 DML이라도 Hibernate의 HQL 파서는 WHERE절 파라미터 타입을 같은 엔티티 메타모델에서 그대로 끌어온다**(SELECT와 다른 바인딩 경로가 아님) | **엔티티 경로 비교, 변경 불필요** — §5에서 이 경로를 실제 통합 테스트(`TimeTableOccupancyConcurrencyTest`)로 간접 확인했으나, 그 테스트 자체가 스키마 drift로 깨져 있어 "통과로 확인"이라고는 말할 수 없음(§5) |
| `TimeTableOccupancyJpaRepository` | `lockActiveOccupancy` | `occupancy.userId` + `occupancy.timeTable.restaurantId`/`.date`/`.startTime` | `userId`(2c#14, `UuidBinaryConverter`), `timeTable.restaurantId`(연관 경로를 통한 일반 컬럼 접근) | **엔티티 경로 비교, 변경 불필요** |
| `TimeTableOccupancyJpaRepository` | `lockExpiredHolds` | 식별자 비교 없음(`occupiedStatus`/`releasedAt`/`occupiedDatetime`만) | — | **대상 아님** |

**네 레포지토리 전부, 모든 `@Query`가 리터럴을 엔티티 속성 경로와 직접 비교하거나(위
§1의 "비교에 쓰인 적 없는" `Expressions.constant`와 대조적으로) 연관 경로를 통해 접근한다
— QueryDSL이 아니라 Spring Data `@Query` 문자열이라는 점은 바인딩 메커니즘에 영향을 주지
않는다(Hibernate가 HQL을 파싱해 같은 엔티티 메타모델에서 타입을 끌어오는 것은 QueryDSL이
생성한 JPQL과 동일한 레이어다). `claimTimeTable`을 포함해 재작성이 필요한 `@Query`는
하나도 없다.** 성능을 위한 쿼리 재작성이나 새 인덱스 추가는 이 감사의 범위가 아니다(요구사항
명시 — 별도 결정).

---

## 5. 발견된 회귀 — 이 유닛의 수정 범위 밖 (기록만)

`claimTimeTable`의 bind-path를 확인하려고 그 경로를 실제로 때리는 기존 통합 테스트를
**실행**했다(이 감사의 "쿼리 리뷰어" 역할 — 코드만 읽지 않고 실측한다). 결과, 두 개의
기존 Testcontainers 통합 테스트가 **이번 U4 매핑 전환 때문에 깨져 있다** — 둘 다
`adapter-module/src/test/kotlin/...`에 있어 이 유닛이 건드릴 수 있는 경로
(`infrastructure-module/src/main/.../repository/`, `batch-module/src/main/`, 감사 문서,
키셋 테스트)의 **바깥**이라 고치지 않는다.

| 테스트 | 실패 원인 | 재현 |
|---|---|---|
| `adapter-module/src/test/kotlin/com/reservation/persistence/timetable/TimeTableOccupancyConcurrencyTest.kt` | `MIGRATIONS` 리스트(`:89-96`)가 `V1_13/V1_15/V1_16/V1_17/V1_21`만 적용한다 — `V1_22`/`V1_23`(binary-pk expand/contract)이 없어 실제 DB 컬럼은 여전히 `VARCHAR(128)`인데, `TimeTableEntity.restaurantId`는 이미 `UuidBinaryConverter`로 BINARY(16) 바이트를 만들어 insert하려 한다. MySQL이 `Incorrect string value: '\xA1\x11u,\xC7~...' for column 'restaurant_id'`로 거부한다(utf8mb4 컬럼에 유효하지 않은 바이트) | `./gradlew :adapter-module:test --tests "com.reservation.persistence.timetable.TimeTableOccupancyConcurrencyTest"` → 2 failed, 1 skipped (이 워크트리에서 직접 실행, 2026-10-06) |
| `adapter-module/src/test/kotlin/com/reservation/persistence/queue/WaitingQueueDatabaseAdapterIntegrationTest.kt` | 같은 패턴 — `V1_19`/`V1_20`만 적용, `V1_22`/`V1_23` 없음. `WaitingQueueEntity.restaurantId`/`userId`가 BINARY(16) 바이트를 VARCHAR(128) 컬럼에 insert하려다 실패 | `./gradlew :adapter-module:test --tests "com.reservation.persistence.queue.WaitingQueueDatabaseAdapterIntegrationTest"` → 3 failed (이 워크트리에서 직접 실행, 2026-10-06) |

이 둘은 "식별자가 변환된 컬럼과 비교되는가"의 판정과는 무관하다(§4의 결론을 뒤집지
않는다) — 실패 원인은 바인딩 메커니즘이 아니라 **테스트 자신이 수작업으로 골라 적용하는
Flyway 파일 목록이 V1_22/V1_23을 포함하지 않아 스키마가 엔티티 매핑과 어긋난 것**이다.
같은 원인으로 "이 두 테스트 외에 손으로 migration 목록을 고르는 테스트가 더 있는가"를
확인했다 — `grep -rl "db/migration/V1_"`의 결과는 이 두 파일이 전부다. 수정은 이 유닛의
권한 밖(파일이 `infrastructure-module`/`batch-module` 바깥)이며, U6(마이그레이션 적용·배포
절차) 또는 이 두 테스트의 소유자가 `MIGRATIONS`/리소스 목록에 `V1_22`/`V1_23`을 추가해야
닫힌다 — [[binary-pk-migration-runbook]]에 이 갭을 다음 작업 항목으로 남긴다.

---

## 6. BEHAVIOUR PRESERVED — 변경 요약

이 감사가 다룬 모든 사이트에서 **프로덕션 코드 변경이 필요한 곳은 없었다** — join 조건,
필터 조건, projection 순서, 반환 컬럼 집합 중 어느 것도 바뀌지 않는다. 유일한 신규 산출물은:

- `docs/v2/analysis/binary-pk-query-audit.md` (이 문서)
- `adapter-module/src/test/kotlin/com/reservation/persistence/binarypk/BinaryPkKeysetPaginationTest.kt` (신규 테스트, §3.5)

---

## 7. 빌드·게이트 결과

```
$ ./gradlew :infrastructure-module:build -x test
BUILD SUCCESSFUL

$ ./gradlew :batch-module:build -x test
BUILD SUCCESSFUL

$ ./gradlew :infrastructure-module:detekt :batch-module:detekt
BUILD SUCCESSFUL
```

**`spotlessCheck`는 모듈별 태스크가 아니다** — `:infrastructure-module:spotlessCheck`/
`:batch-module:spotlessCheck`를 그대로 실행하면 `Cannot locate tasks` 오류가 난다(루트
`build.gradle.kts:37`가 Spotless를 루트에만 적용하고, `Kotlin` 소스셋을 레포 전체 기준으로
검사한다 — `subprojects`에 재적용하지 않음, 직접 실행해 확인). 동치로 실행 가능한 것은
루트 `spotlessCheck`뿐이다:
```
$ ./gradlew spotlessCheck
BUILD SUCCESSFUL
```
(최초 실행 시 이 감사가 추가한 `BinaryPkKeysetPaginationTest.kt`의 import 순서 위반 1건이
걸렸다 — `javax.sql.DataSource`가 `java.math.BigDecimal`보다 뒤에 와야 하는 프로젝트 import
순서 규칙을 어김. 직접 고쳐 통과시켰다. `spotlessApply`를 레포 전체에 돌리지 않은 이유는
이 유닛이 건드릴 수 없는 다른 모듈의 미커밋 변경분까지 재포맷할 위험이 있어서다.)

(이 유닛은 `infrastructure-module`/`batch-module`의 소스를 바꾸지 않았으므로 — 위 gate들이
U4가 남긴 상태에 대해 통과함을 재확인한 것이다.)

## 관련

- [[binary-pk-identifier-inventory]] §5 — 이 감사가 사이트 목록의 출발점으로 쓴 원본 전수조사
- [[binary-pk-association-verdicts]] — 13개 연관관계 사이트 판정(이 문서가 다루지 않는 영역)
- [[ADR-028-binary-pk-identifier-storage]] — 바이트 순서 근거, 변환자 메커니즘 결정
- `adapter-module/src/test/kotlin/com/reservation/persistence/binarypk/BinaryPkKeysetPaginationTest.kt`
- `adapter-module/src/test/kotlin/com/reservation/persistence/binarypk/BinaryPkHibernateVerificationTest.kt` (U4 산출물, 이 문서가 재사용하는 패턴의 출처)

## U7 실행 결과 (2026-10-06) — 회귀 수정과, 이 감사가 놓친 raw bind-path 1곳

U5가 "이 유닛 범위 밖, 수정하지 않음"으로 남긴 두 회귀
(`TimeTableOccupancyConcurrencyTest`, `WaitingQueueDatabaseAdapterIntegrationTest`)를
실제로 고쳤다 — 근본 원인은 두 테스트 모두 "V1_22/V1_23을 포함하지 않는 손으로
고른 마이그레이션 목록"이었고, 단순히 그 목록에 두 파일을 추가하는 것으로는
부족했다: 두 테스트가 쓰던 메커니즘(수작업 `;` 분리기, Spring
`ResourceDatabasePopulator`) 모두 V1_22/V1_23의 `CREATE PROCEDURE ... BEGIN
... END` 블록(본문에 중첩 괄호가 여럿 있다)을 올바르게 파싱하지 못한다. 그래서
손으로 고른 부분집합 자체를 버리고, **실제 Flyway 라이브러리로 전체 체인
(`classpath:db/migration`, V1_0~최신)을 그대로 적용**하도록 바꿨다 — 운영과
같은 파서를 쓰므로 새 마이그레이션이 추가될 때마다 같은 drift가 재발하는
구조적 결함이 사라진다.

**이 감사(U5)가 놓친 raw bind-path 1곳**: `batch-module`의
`TimeTableJdbcItemWriter`(`timetable` 테이블에 raw JDBC `PreparedStatement.
setString`으로 `id`/`restaurant_id`를 쓰는 배치 writer)는 이 감사의 "비교/정렬"
관점(QueryDSL/JPQL/커서 리더)에 들지 않아 전수조사 대상에서 빠졌다. 실제로
전체 Flyway 체인을 적용한 뒤 `TimeTableJobTest`로 배치를 실제 실행해서야
`Data truncation: Data too long for column 'id'`로 드러났다 — JPA를 거치지
않는 raw JDBC insert라 `UuidBinaryIdentifierType`/`UuidBinaryConverter`가
전혀 적용되지 않고 36자 평문이 BINARY(16) 컬럼에 그대로 들어가려 했기
때문이다. `UuidBinaryCodec.encode` + `PreparedStatement.setBytes`로 고쳤다.
**교훈**: "비교식에 쓰이는 식별자"만 감사해서는 안 된다 — JPA를 우회하는 raw
JDBC write 경로는 변환자가 전혀 개입하지 않으므로 별도로 찾아야 한다
(`grep -rl "prepareStatement\|JdbcTemplate" --include='*.kt'`가 이 경로를
찾는 데 충분했다 — 레포 전체에 이런 사이트가 이 하나뿐이었다).

**로깅 포매터도 Flyway 실행을 막을 수 있다**: 전체 체인을 Flyway로 적용하자
`ReservationApplicationTest`(Spring Boot 전체 컨텍스트, Flyway 오토컨피그
경로)가 `NoSuchElementException`으로 깨졌다 — p6spy가 SQL을 로깅하면서
Hibernate `BasicFormatterImpl`(DDL 포매터)로 `CREATE PROCEDURE` 본문을
예쁘게 출력하려다 중첩 괄호 처리가 깨진 것이다. `P6SpyFormatter`의 포맷팅을
`runCatching`으로 감싸 실패 시 원본 SQL로 폴백하도록 고쳤다 — 로그 가독성은
best-effort이지, 부팅/배치 실행을 막을 이유가 될 수 없다.

증거: `./gradlew test`(Docker 기동 상태) — 전체 모듈 0 failed, 0 errored.
`BatchCreateDropFlywaySchemaDivergenceTest`(신규, batch-module의
`ddl-auto:create-drop`이 만드는 41개 식별자 컬럼 표본을 Flyway 전체 체인
결과와 직접 비교 + `ddl-auto:validate` 부팅 확인)도 같은 실행에서 GREEN —
U4가 남긴 엔티티 매핑이 U6의 Flyway 스키마와 실제로 일치함을 코드 리딩이
아니라 실행으로 확인했다.
