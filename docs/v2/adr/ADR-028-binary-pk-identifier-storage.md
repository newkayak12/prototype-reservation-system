# ADR-028: 식별자 컬럼 BINARY(16) 전환 — 영속성 경계 변환자 선택과 바이트 순서

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture` (문서 허브 소속 — 결정의 대상 자체는 V1 인프라. 필기 위치 근거는 "추가 정보" 참조)
- **상위 RFC**: 없음 — V1 인프라 전환 작업(`refactor/to-binary-pk`)에서 직접 발생한 결정. **선행 산출물**: [[binary-pk-identifier-inventory]]
- **연관 ADR**: 없음

---

## 맥락과 문제 (Context and Problem Statement)

V1의 `TimeBasedPrimaryKey`(`infrastructure-module/.../persistence/common/TimeBasedPrimaryKey.kt:18`)는 모든 PK를 `@Column(columnDefinition = "VARCHAR(128)")`로 선언한 `String` 식별자로 저장한다. 값은 `UuidGenerator.generate()`가 만드는 UUIDv7(JUG `Generators.timeBasedEpochGenerator()`, 소문자 하이픈 36자)이다. [[binary-pk-identifier-inventory]]가 전수조사한 53개 표면(PK 18 + 명시 컬럼 22 + 암묵 컬럼 13) 중 49개가 이 값이거나 이 값을 참조한다. VARCHAR(128)은 실제로 필요한 36바이트보다 훨씬 넓고, 저장 형식이 사람이 읽는 텍스트라 PK/FK 비교·인덱스가 전부 문자열 비교로 처리된다.

이 ADR이 다루는 결정은 "바꿀지"가 아니라(그건 이미 `refactor/to-binary-pk` 브랜치의 목표로 주어졌다) **"어떻게 바꾸는가"**다 — 구체적으로:

1. 영속성 경계에서 `String ↔ BINARY(16)` 변환을 **어느 메커니즘**으로 할 것인가(JPA `AttributeConverter`, Hibernate 레벨 변환, 필드 타입 자체를 바꾸는 것, 또는 SQL 쪽에서만 처리).
2. 16바이트를 **어떤 바이트 순서**로 쓸 것인가 — 텍스트의 16진수 순서를 그대로 쓸지, MySQL `UUID_TO_BIN(x, 1)` 스타일 스왑을 적용할지.

이 결정이 까다로운 이유는 JPA 명세가 `@Convert`를 `@Id` 속성에 쓰지 말라고 명시하기 때문이다(JPA 11.1.10: "`@Convert` ... should not be used to specify a converter for ... an attribute that is otherwise exempted from conversion" — ID 속성이 그 예외 중 하나). 18개 전부가 `@MappedSuperclass`에 선언된 `@Id` 필드를 상속받는 이 코드베이스에서는, 가장 자연스러운 접근(베이스 클래스의 `identifier` 필드에 `@Convert` 한 번 선언)이 **명세상 권장되지 않는 경로**를 그대로 밟는다. Hibernate가 이를 실제로 적용하는지는 구현 세부사항이라 코드를 읽는 것만으로는 확정할 수 없다 — 통합 테스트로 확인해야 한다.

**이 영속성 경계 변환을 어떤 메커니즘으로 구현하고, 16바이트를 어떤 순서로 쓸 것인가?**

## 결정 동인 (Decision Drivers)

- **식별자 표현 불변**: 목표가 명시한 대로 domain/application/API 계층은 소문자 하이픈 UUID 문자열 표현을 그대로 유지해야 한다 — 변환은 영속성 경계에만 있어야 한다.
- **JPA 명세 제약**: `@Convert`는 `@Id` 속성에 쓰지 말라고 명세가 권고한다(JPA 11.1.10). `@MappedSuperclass`에 선언된 `@Id`라는 점이 이 제약을 더 날카롭게 만든다 — 적용 여부가 보증되지 않는다.
- **단계적 롤아웃 안전성**: `ddl-auto: validate`가 매 배포마다 Hibernate 매핑과 실제 DB 스키마의 일치를 요구한다(`local.yaml:8`, `stage.yaml:8`, `production.yaml:8`, `temporary.yaml:8` 전부 `validate`). 컬럼 타입 전환은 한 번의 돌이킬 수 없는 ALTER가 아니라 되돌릴 수 있는 단계로 쪼개야 한다(D-04).
- **반복 적용 가능성**: 53개 표면(PK 18, 명시 22 중 18 CONVERT, 암묵 13)에 동일하게 적용돼야 한다. 엔티티마다 수작업이 늘어나면 누락·drift 위험이 커진다.
- **성능/인덱스 크기/locality는 결정 동인에서 제외한다** — 이 환경은 실트래픽이 없는 학습용([[ADR-026-workload-runtime-placement]]과 동일 전제)이고, 이번 작업 범위에서 측정값이 없다(O-02). "더 빠르다/더 작다"는 주장을 이 ADR의 근거로 쓰지 않는다.

## 검토한 선택지 (Considered Options)

- **A. `AttributeConverter<String, ByteArray>`** — 영속성 경계에 선언하는 표준 JPA 변환자. `TimeBasedPrimaryKey.identifier`에 `@Convert`로 직접 선언.
- **B. Hibernate 레벨 변환(필드는 계속 `String`)** — A가 `@Id`에서 적용되지 않을 경우를 위한 대안. 18개 concrete 엔티티 각각에 `@Convert(attributeName = "identifier")`를 재선언하거나, JPA `@Convert` 메커니즘을 거치지 않는 커스텀 Hibernate `UserType`/`BasicValueConverter`를 타입 디스크립터 레벨에서 바인딩한다.
- **C. Kotlin 필드 타입을 `java.util.UUID`로 변경 + `@JdbcTypeCode(SqlTypes.BINARY)`** — Hibernate 6의 네이티브 UUID-바이너리 매핑을 쓴다. `identifier: String` → `identifier: UUID`.
- **D. 현상 유지 — SQL 쪽 `UUID_TO_BIN`/`BIN_TO_UUID`만 사용** — JPA 매핑(컬럼 타입 선언)은 그대로 두고, 필요할 때마다 쿼리/뷰/함수에서 SQL 레벨 변환 함수로 처리한다.

## 결정 (Decision Outcome)

**채택: A — `AttributeConverter<String, ByteArray>`를 `TimeBasedPrimaryKey.identifier`(및 `RestaurantWorkingDayId.identifier`, 그리고 참조하는 쪽의 명시/암묵 컬럼)에 직접 선언한다. 지정 폴백: B.**

> **U4 실측 갱신(2026-10-06)**: A는 `@Id`/`@EmbeddedId` 구성요소(식별자
> 2곳)에서는 실제로 폴백 B(Hibernate `UserType`)로 전환됐다 — "U4 실행
> 결과" 절 참고. 그 외 CONVERT 컬럼(일반 `@Column`, 식별자 아닌 47곳)은
> A가 그대로 적용된다. 아래 본문은 당시 "검증 전" 결정 서술이고, 실측
> 결과는 "확인(Confirmation)" 절 아래 "U4 실행 결과"에 있다 — 둘을 같이
> 읽는다.

A가 B보다 앞서는 이유는 결정 동인 "반복 적용 가능성"이다 — 변환자 하나를 베이스 클래스·VO 필드에 한 번 선언하는 것이, 18개 concrete 엔티티 전부에 동일한 애노테이션을 반복하거나 커스텀 `UserType`을 유지하는 것보다 drift 위험이 작다. 단, 이 선택은 "JPA 명세가 권고하지 않는 경로가 실제로 동작하는가"라는 **검증되지 않은 가정**에 의존한다. 따라서 이 가정이 깨지는 경우를 대비해 폴백을 명명한다.

| # | 결정 |
|---|------|
| 1 | **변환자 위치**: `AttributeConverter<String, ByteArray>` 하나를 `infrastructure-module`(영속성 경계)에 두고, 이미 `shared-module`에 존재하는 순수 코덱 `UuidBinaryCodec.encode/decode`(`shared-module/.../generator/uuid/UuidBinaryCodec.kt`)에 위임한다. 변환자 자신은 JPA 어댑터일 뿐, 바이트 변환 로직을 직접 갖지 않는다. |
| 2 | **적용 지점**: `TimeBasedPrimaryKey.identifier`(PK, 18개 상속), `RestaurantWorkingDayId.identifier`(`@EmbeddedId`의 절반), 그리고 2c/2e/3a절의 CONVERT 판정 명시·암묵 컬럼 각각에 동일 변환자를 `@Convert`로 선언한다. `autoApply`에 의존하지 않고 매 사이트에 명시 선언한다 — `@Id`/`@EmbeddedId`에서는 암묵 적용이 더 불확실하기 때문이다. |
| 3 | **폴백 트리거**: Testcontainers 기반 통합 테스트로 "JPA insert 후 JDBC로 원시 행을 읽었을 때 저장된 바이트가 `UuidBinaryCodec.encode`의 출력과 같다"를 확인한다. 이 테스트가 실패하거나(즉 `@Convert`가 `@Id`에서 무시돼 평문 문자열이 그대로 들어가거나 타입 오류가 나거나), `ddl-auto: validate` 부팅이 타입 불일치로 실패하면 **B로 전환**한다 — 먼저 18개 concrete 엔티티에 `@Convert(converter = ..., attributeName = "identifier")`를 개별 재선언하고, 그래도 적용되지 않으면 JPA `@Convert` 경로를 완전히 벗어나는 Hibernate `UserType`/`BasicValueConverter`로 교체한다. B 어느 경로든 Kotlin 필드 타입은 계속 `String`이다 — 목표의 "domain/application/API는 문자열 표현을 유지"는 A/B 어느 쪽이 되어도 성립한다. |
| 4 | **바이트 순서**: 16바이트는 **텍스트의 16진수 순서를 그대로** 쓴다(`UUID_TO_BIN(x, 1)`류 스왑을 적용하지 않는다) — 이미 `UuidBinaryCodec`이 이 순서로 구현돼 있다(`mostSignificantBits`/`leastSignificantBits`를 빅엔디언으로 그대로 씀, MySQL `UNHEX(REPLACE(uuid,'-',''))`와 바이트 단위로 동일). 근거는 아래 "바이트 순서 근거" 참조. |
| 5 | **Flyway 경로 모양**: **단일 버전이 아니라 expand/contract 분리 버전으로 낸다.** 같은 컬럼명을 유지한 채 타입만 바꾸는 단일 `ALTER ... MODIFY`는 `ddl-auto: validate`가 매 배포마다 매핑=스키마 일치를 요구하는 상황에서 되돌릴 창을 주지 않는다(D-04 "한 PR에 단계를 합치지 않는다"). 구체적으로: **(expand)** 각 CONVERT 대상 컬럼 옆에 `*_bin BINARY(16) NULL` 형제 컬럼을 추가하고 기존 값을 `UNHEX(REPLACE(col,'-',''))`로 백필한다(D-05 재실행 가능하게 — `WHERE *_bin IS NULL`로 미처리 행만 처리). 이 단계는 앱 코드를 바꾸지 않는다. **(migrate = 앱 배포, DDL 없음)** 엔티티 매핑이 `*_bin` 컬럼을 가리키도록 전환자를 적용한 코드가 배포된다 — 이것이 D-04의 "migrate" 단계이며 별도 Flyway 버전이 필요 없다(DDL이 아니라 코드 전환). **(contract)** 구 VARCHAR 컬럼을 drop하고 `*_bin`을 원래 이름으로 rename한다. 각 단계는 별도 Flyway 버전·별도 배포이며, 이전에 적용된 마이그레이션 파일은 수정하지 않는다(append-only, boundaries.md). |

### 바이트 순서 근거

현재 VARCHAR(128) 컬럼의 비교·정렬은 `prototype_reservation` DB의 콜레이션 `utf8mb4_general_ci`로 수행된다(`docker-entrypoint-initdb.d/init.sql:1`). `general_ci`는 ASCII 영역에서 대소문자만 접고 코드포인트 순서를 보존한다 — 숫자 `0`-`9`(0x30-0x39)는 영문자 `a`-`f`(0x61-0x66)보다 앞선다. `UuidGenerator`가 만드는 값은 항상 소문자이므로 대소문자 접기가 실제로 개입할 일이 없고, 결과적으로 현재의 문자열 비교는 "왼쪽부터 한 글자씩, 숫자<문자 순서로 비교"와 같다.

모든 UUID 문자열은 하이픈이 **같은 위치**(8, 13, 18, 23번째 문자)에 온다. 두 UUID를 비교할 때 같은 위치의 하이픈은 항상 서로 같은 문자와 비교되므로 비교 결과에 영향을 주지 않는다 — 즉 "하이픈 포함 문자열 비교"는 "하이픈을 뗀 32자 16진수 문자열 비교"와 동치다. 그리고 32자 16진수 문자열을 왼쪽부터 비교하는 것은, 그 16진수를 16바이트로 그대로 디코딩한 뒤 바이트 단위로(빅엔디언) 비교하는 것과 동일하다. 다시 말해 **스왑 없이 텍스트 순서 그대로 저장하면, VARCHAR 시절의 정렬 결과를 가장 가깝게 보존한다.**

반대로 `UUID_TO_BIN(x, 1)`의 스왑은 `time_low`/`time_mid`/`time_hi_and_version` 필드가 UUID 문자열 안에서 시간 역전 순서로 흩어져 있는 **UUIDv1**을 시간순 정렬 가능하게 만들기 위한 보정이다. 이 코드베이스가 쓰는 값은 UUIDv1이 아니라 **UUIDv7**(`UuidGenerator.kt:12`, JUG `Generators.timeBasedEpochGenerator()`)이고, UUIDv7은 48비트 밀리초 타임스탬프를 처음부터 빅엔디언·시간순으로 앞쪽 비트에 둔다 — 즉 텍스트 순서 자체가 이미 시간순이다. UUIDv1용 스왑을 UUIDv7에 적용하면 이미 올바른 시간순 배치를 오히려 흐트러뜨릴 뿐, 얻는 것이 없다. 따라서 스왑을 적용하지 않는다.

이 근거는 "정렬 순서가 전혀 안 바뀐다"는 보장이 아니다 — `general_ci`의 콜레이션 가중치와 순수 바이트 비교가 이론상 모든 경우에 1:1로 같다고 증명하지는 않았고(예: 콜레이션이 내부적으로 쓰는 가중치 테이블의 예외 케이스까지는 확인하지 않았다), 그래서 "키셋 페이지네이션의 정렬 의미론 변화"를 아래 잔존 위험에 별도로 남긴다.

### 기록 위치와 D-12 상속

이 ADR은 `docs/v2/adr/`에 기록한다 — 프로젝트 컨벤션(`boundaries.md`: "유의미한 아키텍처 결정은 `docs/v2/adr/` 아래 ADR을 요구한다")이 ADR의 기록 위치를 사이클이 아니라 단일 경로로 못박기 때문이다. 이 결정의 대상 자체는 V1의 `infrastructure-module`이고, V2의 `command-module`/`query-module`/`auth-server-module`/`contract-module`/`batch-module`은 현재 JPA를 전혀 갖지 않지만(해당 모듈 소스 확인), 그 모듈들이 나중에 식별자 컬럼을 영속화하게 되면 **`.claude/conventions/data.md`의 `D-12`가 V2가 그대로 물려받는 식별자 저장 규칙**이다 — 이 ADR은 그 규칙의 근거 문서다.

### 결과 (Consequences)

- 좋은 점: 변환자를 한 곳(베이스 클래스/VO)에 선언하므로 53개 표면에 반복 작업이 생기지 않는다(A가 성립하는 한).
- 좋은 점: 바이트 순서가 기존 코덱(`UuidBinaryCodec`, 이미 테스트됨)과 일치해 추가 변환 로직이 필요 없다.
- 좋은 점: expand/contract 분리로 각 배포 단계에서 `ddl-auto: validate`가 항상 매핑=스키마 일치를 유지한다 — 중간에 멈춰도 안전하다.
- 나쁜 점 / 트레이드오프: A가 폴백으로 전환되면(결정 3) 18개 엔티티에 반복 선언이 생기거나 커스텀 `UserType` 유지 비용이 생긴다 — "검증 전까지는 모른다"는 불확실성 자체가 비용이다.
- 나쁜 점 / 트레이드오프: expand/contract 3단계(또는 앱 배포 포함 시 사실상 3회전)는 단일 ALTER보다 배포 횟수가 늘어난다.

### 잔존 위험 (Residual Risks)

- **`@MappedSuperclass` 안 `@Id`에 걸린 `@Convert`** — JPA 11.1.10이 권고하지 않는 경로를 그대로 쓴다. 결정 3의 통합 테스트로만 확인되며, 실패 시 B로 전환한다.
- **`@EmbeddedId`(`RestaurantWorkingDayId`) 안의 `@Convert`** — 복합 키의 구성 요소에 변환자가 적용되는지는 `@Id` 단독 케이스와 별도로 검증해야 한다. 동일한 통합 테스트 스위트에 `restaurant_working_day` 케이스를 반드시 포함한다.
- **명시 `columnDefinition` 없는 연관/조인 컬럼** — [[binary-pk-identifier-inventory]] 3a절의 7개 `@JoinColumn` 사이트는 참조되는 `@Id`의 컬럼 타입을 그대로 물려받는다. PK 전환과 이 7개 사이트의 전환이 같은 배포에 들어가지 않으면 `ddl-auto: validate`가 부팅 시점에 타입 불일치로 실패한다 — expand/contract 각 단계에서 PK와 그 PK를 참조하는 모든 `@JoinColumn`을 **항상 같은 단계**에 포함해야 한다.
- **키셋 페이지네이션의 정렬/범위비교 의미론 변화** — [[binary-pk-identifier-inventory]] 5절의 5개 사이트(`RestaurantQuerySpec`/`FindRestaurantsRepository`/`TimeTableStepConfig`/`QueryDslCursorItemReader`)가 문자열 비교에 의존한다. 스왑 없이 저장해도 "바이트 순서 근거"가 증명이 아니라 논증이므로, 전환 창에는 이 리더를 쓰는 배치 Job이 멈춰 있어야 한다([[binary-pk-identifier-inventory]] §5 결론과 동일).
- **`schedule.id` — 의도적으로 전환하지 않은 UUID 보유 컬럼 (수용된 잔존 위험)** — `V1_13__schedule_context.sql:3` `id VARCHAR(255) PRIMARY KEY`, 매핑: `ScheduleEntity.kt:20-22` `@Id @Column(name="id") val restaurantId: String`. 값 자체는 `restaurant.id`(1절 #1, 이번에 BINARY(16)으로 전환)를 그대로 복사해 쓴다 — `TimeBasedPrimaryKey`를 상속하지 않는 독립 `@Id String` 필드라서 U1 조사가 1절(서브클래스 전수조사)에도 2절(명시적 추가 컬럼 전수조사)에도 걸리지 않았고, 이번 전환의 인벤토리에서 빠진 채로 남았다. 현재 FK 제약 0건, 어떤 `@JoinColumn`/연관관계도 이 컬럼을 참조하지 않으며, 접근 경로는 `findById(restaurantId: String)` 단일 경로뿐이라 **기능적으로는 안전**하다 — 다만 전환 후에는 `restaurant.id`가 BINARY(16)인데 `schedule.id`는 여전히 VARCHAR(255) 텍스트라서, 같은 논리값이 두 테이블에서 서로 다른 저장 형태로 존재하는 비일관 상태가 남는다. **이번 전환 범위에는 포함하지 않는다** — 후속 작업으로 [[binary-pk-identifier-inventory]]의 CONVERT 표에 추가하고 별도 expand/contract 쌍(가제 V1_24)으로 처리할 것.

### 확인 (Confirmation)

- Testcontainers 통합 테스트: `TimeBasedPrimaryKey` 상속 엔티티 하나 + `RestaurantWorkingDayId`를 쓰는 `RestaurantWorkingDayEntity`를 각각 persist한 뒤, 같은 커넥션으로 raw JDBC `SELECT`를 날려 저장된 바이트가 `UuidBinaryCodec.encode(원본 문자열)`과 바이트 단위로 같은지 확인한다. 실패하면 결정 3에 따라 B로 전환한다.
- `ddl-auto: validate` 부팅 테스트: expand 단계 배포 후 애플리케이션 컨텍스트가 정상 기동하는지(스키마-매핑 불일치로 실패하지 않는지) 각 프로필에서 확인한다.
- 코드 리뷰 체크: PK 전환과 그 PK를 참조하는 모든 `@JoinColumn`이 같은 Flyway 버전/같은 배포 단위에 포함됐는지 — [[binary-pk-identifier-inventory]] 3a절 7개 사이트 전부가 체크리스트에 있는지 확인한다.

### U4 실행 결과 (2026-10-06 실측)

위 Confirmation의 Testcontainers 통합 테스트를 실제로 작성·실행했다 —
`adapter-module/src/test/kotlin/com/reservation/persistence/binarypk/BinaryPkHibernateVerificationTest.kt`
(`hibernate.hbm2ddl.auto=create-drop`으로 Flyway 없이 Hibernate가 스스로 스키마를
만들게 해서 확인 — U6의 마이그레이션 존재 여부와 무관하게 돈다).

- **결정 3의 가정이 깨졌다 — A(JPA `AttributeConverter`)는 `@Id`에서 적용되지
  않는다.** `TimeBasedPrimaryKey.identifier`에 필드 레벨 `@Convert`를 건 채
  insert하면 `org.hibernate.exception.DataException` → `MysqlDataTruncation:
  Data too long for column 'id'`로 실패한다(Hibernate 6.6.13이 평문 36자
  문자열을 그대로 보내려 한 것 — JPA 11.1.10의 "쓰지 말라"가 Hibernate
  구현에서는 "무시한다"로 나타난다).
- **B의 첫 번째 경로(엔티티별 `@Convert(attributeName = "identifier")`
  재선언)도 안 됐다.** `RestaurantEntity`에 클래스 레벨로 재선언해도 동일한
  `Data truncation`이 재현됐다 — `@Id` 속성에는 JPA `@Convert`가 선언 위치와
  무관하게 전혀 적용되지 않는 것으로 확인.
- **B의 두 번째 경로(Hibernate `UserType`)로 전환해 해결했다.** 새 클래스
  `infrastructure-module/.../persistence/converter/UuidBinaryIdentifierType.kt`
  (`org.hibernate.usertype.UserType<String>`, `UuidBinaryCodec`에 위임)를
  만들고 `TimeBasedPrimaryKey.identifier`와 `RestaurantWorkingDayId.identifier`
  양쪽에 `@org.hibernate.annotations.Type(UuidBinaryIdentifierType::class)`로
  교체했다 — `@Convert`를 거치지 않는 Hibernate 네이티브 메커니즘이라 `@Id`
  제약의 적용 대상이 아니다. 이후 두 케이스 모두 raw JDBC로 읽은 바이트가
  `UuidBinaryCodec.encode`의 출력과 정확히 일치했다.
  - **PRIMARY BRANCH 유지**: 이 전환은 Kotlin 필드 타입(`String`)과
    `Persistable<String>`을 바꾸지 않는다 — ADR이 명명한 폴백 B 그대로다.
  - 일반 `@Column`(비-`@Id`, 예: `RestaurantEntity.companyId`/`userId`,
    `UserChangeHistoryEntity.userUuid` 등)은 **A(`UuidBinaryConverter`,
    JPA `AttributeConverter`)를 그대로 쓴다** — `@Id`가 아닌 속성에는 JPA
    `@Convert`가 정상 적용되므로 폴백이 필요 없다. 즉 이 코드베이스는
    "`@Id`/`@EmbeddedId` 구성요소는 `UserType`, 그 외 CONVERT 컬럼은
    `AttributeConverter`"로 **두 메커니즘을 함께 쓴다** — 둘 다
    `infrastructure-module/.../persistence/converter/` 패키지에 있고 둘 다
    바이트 변환 로직은 `UuidBinaryCodec`에 위임한다(코드 중복 없음).
- **잔존 위험 3의 "타입은 물려받지만 길이는 아닐 수 있다"는 가능성이
  실제로 일어났다 — 단, `@MapsId` 케이스에서만.** `RestaurantWorkingDayEntity`의
  `@JoinColumn(name = "restaurant_id")`(`@MapsId("identifier")`로
  `RestaurantWorkingDayId.identifier`와 물리 컬럼을 공유)는
  `columnDefinition` 없이는 Hibernate가 `BINARY(255)`를 생성했다(`UserType`의
  기본 길이) — 바이트 변환 자체는 맞았지만(처음 16바이트가 정확히 일치,
  나머지는 0 패딩) 길이가 달라 `ddl-auto: validate`가 실패할 상황이었다.
  `@JoinColumn`에 `columnDefinition = "BINARY(16)"`를 명시해 해결했다. 반면
  `@MapsId`가 없는 순수 `@ManyToOne @JoinColumn`(예: `RestaurantCuisinesEntity`)은
  타입과 길이 모두 참조 PK에서 정확히 물려받았다 — 명시 선언이 필요 없다.
  13개 연관관계 사이트 전부의 사이트별 판정은
  [[binary-pk-association-verdicts]]에 기록했다(7개 `@JoinColumn` + 6개
  역방향 `@OneToMany`, 이 문서의 산문이 아니라 체크리스트 형태로).
- **매핑이 겨냥하는 스키마 상태 — 명시적 결정.** 이 단위가 변경한 Kotlin
  매핑은 원래 컬럼명(`id`, `restaurant_id`, …)에 `BINARY(16)`을 선언한다 —
  이는 결정 5가 말하는 3단계 중 **contract 이후의 최종 상태**와 일치하고,
  **expand만 적용된 중간 상태**(원래 컬럼명이 아직 VARCHAR(128)이고
  `*_bin` 형제 컬럼이 따로 있는 상태)와는 불일치한다. 즉 이 매핑이 가리키는
  배포 시점은 "V1_22(expand)와 V1_23(contract)이 **같은 Flyway 실행에서
  순서대로 모두 적용된 뒤**"다. 결정 5가 서술한 "expand → (별도 앱 배포로
  `*_bin`을 가리키는) migrate → contract"의 3단계 중 가운데 단계를 가리키는
  Kotlin 매핑(= `*_bin` 컬럼명을 쓰는 중간 버전)은 **이 저장소에 존재하지
  않는다** — 그런 중간 매핑을 만들지 않기로 한 것은 이번 단위의 의도된
  범위 결정이다: 왜냐하면 이 프로젝트의 배포 방식(Flyway `migrate`가 앱
  부팅 시 자동 실행되는 방식, `application-test.yaml:36-44`의
  `baseline-on-migrate`/`validate-on-migrate` 설정)에서는 보류 중인 모든
  마이그레이션이 **앱이 `ddl-auto: validate`를 수행하기 전에 전부 적용된다**
  — 즉 V1_22와 V1_23이 항상 한 번에 같이 적용되는 운영 방식이라면 중간 상태를
  가리키는 별도 앱 배포 자체가 발생하지 않는다. **단, 운영자가 의도적으로
  V1_22만 먼저 적용하고(가역성 확보를 위해) 한 배포 주기를 쉬었다가 V1_23을
  적용하는 운영 절차를 쓴다면, 그 사이 창에서는 이 브랜치의 애플리케이션
  코드를 배포할 수 없다**(현재 매핑이 `*_bin`을 가리키지 않으므로
  `ddl-auto: validate`가 그 중간 상태에서 실패한다). 이 트레이드오프를
  감수하는 이유: "매핑이 `*_bin`을 가리키는 중간 버전"을 추가로 만들면
  53개 표면 전부에 또 한 번의 임시 매핑 변경이 생겨 drift 위험이 결정 동인
  "반복 적용 가능성"을 두 배로 늘린다. 이 창에서 운영을 멈추지 않으려면
  운영자는 V1_22와 V1_23을 **같은 배포에서 연속 적용**해야 한다 — 이것이
  V1_22/V1_23 두 마이그레이션 파일을 분리해 두고도 "사실상 한 쌍으로
  배포"하도록 요구하는 이유이며, [[binary-pk-migration-runbook]]에 운영
  절차로 명시해야 한다(U6 몫, 이 ADR은 그 요구사항만 기록한다).

## 선택지 상세 (Pros and Cons of the Options)

### A. `AttributeConverter<String, ByteArray>` (채택)
- 장점: 표준 JPA 메커니즘, 선언 지점 하나, 기존 `UuidBinaryCodec`과 바로 결합 가능.
- 단점: `@Id`(`@MappedSuperclass` 안)·`@EmbeddedId` 구성요소에서 적용이 명세상 보증되지 않는다 — 검증 필요.
- 비용: 통합 테스트 작성 비용(1회성) + 실패 시 B로 전환하는 재작업 비용(조건부).

### B. Hibernate 레벨 변환, 필드는 `String` 유지 (지정 폴백)
- 장점: A가 `@Id`에서 실패하더라도 Kotlin 필드 타입을 바꾸지 않아 목표의 문자열 표현 불변을 유지한다. `UserType`/`BasicValueConverter` 경로는 JPA `@Convert` 제약 자체를 우회한다(Hibernate 타입 디스크립터 레벨에서 바인딩되므로 "ID 속성에는 컨버터를 적용하지 않는다"는 JPA 레벨 규칙의 적용 대상이 아니다).
- 단점: 18개 concrete 엔티티 각각에 애노테이션을 반복해야 하거나(재선언 경로), 커스텀 `UserType`을 작성·유지해야 한다(표준 메커니즘 대비 보일러플레이트·러닝커브).
- 비용: 18곳 반복 선언의 drift 위험(새 엔티티 추가 시 빠뜨릴 수 있음) 또는 `UserType` 구현·테스트 비용.

### C. 필드 타입을 `java.util.UUID`로 변경 + `@JdbcTypeCode(SqlTypes.BINARY)` (기각)
- 장점: Hibernate 6 네이티브 지원이라 커스텀 변환자가 필요 없다. JPA `@Convert`-on-`@Id` 제약 자체가 적용되지 않는다(타입 자체가 이미 Hibernate의 네이티브 UUID 타입이므로).
- 단점: **domain/application 계층의 필드 타입이 `String`에서 `UUID`로 바뀐다** — `Persistable<String>`을 구현하는 `TimeBasedPrimaryKey`의 제네릭 파라미터, `UuidGenerator.generate(): String`과의 경계, `core-module`이 참조할 식별자 타입(현재 core-module은 JPA 의존이 없지만 식별자 "타입" 자체는 공유 개념이다) 전부에 파급된다. 이는 목표가 명시한 "domain/application/API 식별자 표현은 문자열 유지"를 **대체(supersede)**하는 변경이다.
- 기각 사유: 목표 승인 범위를 벗어난다. 이 경로를 실제로 선택하려면 목표의 문자열-표현 조항을 대체한다는 점을 명시하고, U4(구현) 착수 전 요청자 승인을 다시 받아야 한다 — 이 ADR은 그 승인을 갖고 있지 않으므로 C를 채택하지 않는다.

### D. 현상 유지 — SQL 쪽 `UUID_TO_BIN`/`BIN_TO_UUID`만 사용 (기각)
- 장점: JPA 매핑을 전혀 바꾸지 않는다 — 코드 변경이 가장 적다.
- 단점: 컬럼의 **물리 타입**을 BINARY(16)으로 바꾸는 것이 이번 전환의 목표인데, JPA 매핑(`columnDefinition`)을 안 바꾸면 Hibernate가 기대하는 타입과 실제 DB 타입이 어긋나 `ddl-auto: validate`가 모든 프로필에서 부팅 실패한다. 뷰·함수로 양쪽을 다 유지하는 방식은 영속성 경계가 아니라 SQL 레이어 여기저기에 변환 책임을 흩어 놓는다 — 목표가 요구하는 "영속성 경계에서만 변환"과 정반대 방향이다.
- 기각 사유: 목표(물리 타입을 BINARY(16)으로)와 제약(경계에서만 변환) 둘 다를 동시에 만족시키지 못한다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클, U6 이후)**: expand 단계 백필 SQL의 정확한 문법(배치 크기, `UNHEX`/`REPLACE` 표현식), contract 단계의 컬럼 rename 순서, 폴백(B) 전환이 실제로 필요한지 여부(통합 테스트 결과로 결정).
- **닫는 항목**: [[binary-pk-identifier-inventory]]가 남긴 "PK 전환과 3a절 7개 `@JoinColumn`은 반드시 같은 배포" 요구사항을 이 ADR의 잔존 위험·확인 절로 흡수했다.
- 관련: [[binary-pk-identifier-inventory]] · `.claude/conventions/data.md#D-12` · `.claude/conventions/boundaries.md`
