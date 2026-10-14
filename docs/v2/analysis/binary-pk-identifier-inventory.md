# Binary PK 식별자 표면 전수조사 (Identifier Surface Inventory)

> 생성: 2026-10-06 · 베이스 커밋: `244a93b3` · 브랜치: `refactor/to-binary-pk`
> 방법: `: TimeBasedPrimaryKey(` / `columnDefinition = "VARCHAR(128)"` / `@JoinColumn` / `foreign key` 를 정규식으로 전수 grep한 뒤, 각 히트를 생성자 호출부까지 따라가 값의 출처(UuidGenerator인지, 로그인 아이디인지, nonce인지)를 확인. 의미를 코드로 확인하지 못한 컬럼은 행을 만들지 않고 "writer 미확인"으로 Open Question에 남긴다.
> 목적: VARCHAR(128) UUID 텍스트 → BINARY(16) 전환 대상 표면을 한 장에 모아, 변환/보류 판정과 그 근거를 영속화한다. 이 문서 자체는 구현하지 않는다.

## 범례

- **CONVERT**: 이 컬럼은 `UuidGenerator.generate()`(JUG `Generators.timeBasedEpochGenerator()`, `java.util.UUID.toString()` 형식 — 소문자 하이픈 36자)가 만든 값이거나 그런 값을 참조하는 식별자 컬럼이다. BINARY(16) 전환 대상.
- **LEAVE-AS-VARCHAR**: 이 컬럼은 UUID가 아니다(로그인 아이디, nonce, 죽은 컬럼 등) — 전환 대상이 아니다.
- 모든 행은 `file:line` 인용과 위 두 판정 중 정확히 하나를 가진다. 중간 상태를 나타내는 placeholder는 쓰지 않는다 — writer를 못 추적한 건은 표에 넣지 않고 맨 끝 "추적 실패" 절에 따로 기록한다.

---

## 0. 레포 전수 카운트 (관찰값, 통과 기준 아님)

베이스 커밋 `244a93b3`에서 재현:

| 패턴 | 명령 | 카운트 |
|---|---|---|
| Kotlin `columnDefinition = "VARCHAR(128)"` | `grep -rc 'columnDefinition = "VARCHAR(128)"' --include='*.kt' infrastructure-module/src/main` | **5** |
| 마이그레이션 `varchar(128)` (대소문자 무시) | `grep -rio 'varchar(128)' infrastructure-module/src/main/resources/db/migration/` | **43** |
| `: TimeBasedPrimaryKey(` 서브클래스 | `grep -rl ': TimeBasedPrimaryKey(' --include='*.kt' infrastructure-module/src/main` | **18** |
| `@JoinColumn` 사이트 | `grep -rc '@JoinColumn' --include='*.kt' infrastructure-module/src/main` | **7** |
| `foreign key` (대소문자 무시) | `grep -rci 'foreign key' infrastructure-module/src/main/resources/db/migration/` | **0** |

이 다섯 수치는 아래 1~3절 표의 행 수와 1:1로 맞는다(18행 = PK, 5개 명시 사이트 = 2절 일부, 7 = 3절 JoinColumn).

---

## 1. PK `id` 컬럼 — `TimeBasedPrimaryKey` 서브클래스 (18개 전부)

베이스 클래스 정의: `infrastructure-module/src/main/kotlin/com/reservation/persistence/common/TimeBasedPrimaryKey.kt:18`
```kotlin
@Column(name = "id", columnDefinition = "VARCHAR(128)", nullable = false, updatable = false)
val identifier: String = UuidGenerator.generate()
```
`UuidGenerator.generate()` (`shared-module/.../UuidGenerator.kt:12`) → `Generators.timeBasedEpochGenerator().generate().toString()` — UUIDv7, 소문자 하이픈 36자. 18개 서브클래스 전부 이 컬럼을 **그대로 상속**하며 자체 `columnDefinition`을 재정의하지 않는다(= 암묵적으로 베이스의 VARCHAR(128)을 그대로 받음).

| # | 테이블 | 클래스 선언 (`file:line`) | 판정 |
|---|---|---|---|
| 1 | `restaurant` | `RestaurantEntity.kt:41` | CONVERT |
| 2 | `restaurant_cuisines` | `RestaurantCuisinesEntity.kt:40` | CONVERT |
| 3 | `restaurant_tags` | `RestaurantTagsEntity.kt:40` | CONVERT |
| 4 | `restaurant_nationalities` | `RestaurantNationalitiesEntity.kt:40` | CONVERT |
| 5 | `restaurant_photo` | `RestaurantPhotoEntity.kt:32` | CONVERT |
| 6 | `holiday` | `HolidayEntity.kt:19` | CONVERT |
| 7 | `table` | `TableEntity.kt:19` | CONVERT |
| 8 | `time_span` | `TimeSpanEntity.kt:24` | CONVERT |
| 9 | `withdrawal_user` | `WithdrawalUserEntity.kt:20` | CONVERT |
| 10 | `reservation` | `ReservationEntity.kt:34` | CONVERT |
| 11 | `user_access_history` | `UserAccessHistoryEntity.kt:22` | CONVERT |
| 12 | `user_change_history` | `UserChangeHistoryEntity.kt:26` | CONVERT |
| 13 | `user` | `UserEntity.kt:32` | CONVERT |
| 14 | `menu` | `MenuEntity.kt:35` | CONVERT |
| 15 | `menu_photo` | `MenuPhotoEntity.kt:32` | CONVERT |
| 16 | `timetable_occupancy` | `TimeTableOccupancyEntity.kt:47` | CONVERT |
| 17 | `timetable` | `TimeTableEntity.kt:52` | CONVERT |
| 18 | `company` | `CompanyEntity.kt:21` | CONVERT |

`restaurant_working_day` 는 이 목록에 없다 — `@EmbeddedId`(복합 PK, `RestaurantWorkingDayId`)를 쓰고 `TimeBasedPrimaryKey`를 상속하지 않는다. 그 PK 컬럼은 2절 #1에서 다룬다.

---

## 2. 명시적 추가 식별자 컬럼 (own `@Column` with identifier semantics)

### 2a. Kotlin `columnDefinition = "VARCHAR(128)"` 사이트 (나머지 4개 — 1절의 베이스는 이미 다룸)

| # | 표면 | `file:line` | writer 추적 | 판정 |
|---|---|---|---|---|
| 1 | `restaurant_working_day.restaurant_id` (복합 PK의 절반, `@MapsId`) | `RestaurantWorkingDayId.kt:15` | `RestaurantWorkingDayEntity.kt:25-33` `@MapsId("identifier") @ManyToOne @JoinColumn(name="restaurant_id")` → `RestaurantEntity.identifier`(1절 #1) | CONVERT |
| 2 | `user_access_history.user_uuid` | `UserAccessHistoryEntity.kt:23` | `CreateUserAccessAdapter.kt:17` ← `CreateUserHistoryInquiry.authenticateId`(`CreateUserAccessHistories.kt:11`) — 같은 클래스 라인 12에 별도 `loginId: LoginId` 필드가 있어 `authenticateId`가 로그인 아이디가 아님을 확인 | CONVERT |
| 3 | `user_change_history.user_uuid` | `UserChangeHistoryEntity.kt:29` | `CreateUserHistoryAdapter.kt:16` ← `inquiry.uuid` ← `CreateGeneralUserChangeHistoryCommand.uuid`(생성자 위치 1번째) ← `ChangeGeneralUserNicknameService.kt:42` `loadUserResult.id` | CONVERT |
| 4 | `user_change_history.user_id` | `UserChangeHistoryEntity.kt:36` | `CreateUserHistoryAdapter.kt:17` ← `inquiry.userId` ← `CreateGeneralUserChangeHistoryCommand.userId`(생성자 위치 2번째) ← `ChangeGeneralUserNicknameService.kt:43` **`loadUserResult.loginId`** — 로그인 아이디, UUID 아님 | **LEAVE-AS-VARCHAR** |

> 질문 (a) 답: `user_change_history.user_id`는 **로그인 아이디**를 담는다(UUID가 아님). `V1_1__create_user_context.sql:27`의 컬럼 코멘트 `'식별키'`는 그래서 **오래된/잘못된 코멘트**다 — 실제로는 `user.login_id`(VARCHAR(32), 사용자가 고른 문자열)가 흘러온다. 반면 같은 테이블의 `user_uuid`(위 #3)가 진짜 UUID PK 참조다. 이 둘을 혼동하면 `user_id`를 BINARY(16)으로 바꾸는 사고가 난다 — **하지 말 것**.

### 2b. 로그인 아이디 컬럼 (UUID 아님, 참고용으로 같이 기재)

| # | 표면 | `file:line` | 판정 |
|---|---|---|---|
| 5 | `user.login_id` | `UserEntity.kt:33-40` | LEAVE-AS-VARCHAR (사용자가 고른 문자열, `UuidGenerator` 미사용) |
| 6 | `withdrawal_user.login_id` | `WithdrawalUserEntity.kt:21-28` | LEAVE-AS-VARCHAR (#5의 탈퇴 시점 사본) |

### 2c. 비정규화된 참조 식별자 (FK 제약 없이 다른 애그리거트의 UUID를 그대로 들고 있는 컬럼)

| # | 표면 | `file:line` | 참조 대상 | 판정 |
|---|---|---|---|---|
| 7 | `restaurant.company_id` | `RestaurantEntity.kt:42-45` | `company.id`(1절 #18) | CONVERT |
| 8 | `restaurant.user_id` | `RestaurantEntity.kt:47-50` | `user.id`(1절 #13) — `user.login_id`(2b #5)와는 다른 컬럼이니 혼동 주의 | CONVERT |
| 9 | `timetable.restaurant_id` | `TimeTableEntity.kt:26-28` | `restaurant.id` | CONVERT |
| 10 | `menu.restaurant_id` | `MenuEntity.kt:36-38` | `restaurant.id` | CONVERT |
| 11 | `holiday.restaurant_id` | `HolidayEntity.kt:16-17` | `restaurant.id` | CONVERT |
| 12 | `table.restaurant_id` | `TableEntity.kt:15-16` | `restaurant.id` | CONVERT |
| 13 | `time_span.restaurant_id` | `TimeSpanEntity.kt:19-20` | `restaurant.id` | CONVERT |
| 14 | `timetable_occupancy.user_id` | `TimeTableOccupancyEntity.kt:45-46` | `user.id` — writer: `ConfirmTimeTableOccupancyCommand.kt:10` 주석 "인증에서 얻은 `userId`" (인증 주체의 PK) | CONVERT |

### 2d. `waiting_queue` (명시적 요구 대상 3개)

`WaitingQueueEntity`는 `TimeBasedPrimaryKey`를 상속하지 않는다 — PK는 `@GeneratedValue(IDENTITY)` `Long`(`WaitingQueueEntity.kt:46-50`)이고, 아래 3개는 전부 `length = 128`로만 선언된 별개의 `@Column`이다(`columnDefinition` 리터럴은 없음 — grep 집계 5건에 안 잡히는 이유).

| # | 표면 | `file:line` | writer 추적 | 판정 |
|---|---|---|---|---|
| 15 | `waiting_queue.restaurant_id` | `WaitingQueueEntity.kt:30-32` | `WaitingQueueDatabaseAdapter.kt:88` ← `slot.restaurantId`(`WaitingQueueSlot.kt:14`) ← `EnterWaitingQueueService.kt:41` ← `command.restaurantId`(`EnterWaitingQueueCommand.kt:8`) — 컨트롤러가 넘기는 매장 UUID | CONVERT |
| 16 | `waiting_queue.user_id` | `WaitingQueueEntity.kt:39-41` | 동일 경로, `command.userId`(`EnterWaitingQueueCommand.kt:7`) — 인증된 사용자 UUID | CONVERT |
| 17 | `waiting_queue.ticket_id` | `WaitingQueueEntity.kt:42-44` | `WaitingQueueDatabaseAdapter.kt:92` ← `candidate.ticketId`(`EnterWaitingQueueService.kt:48`) ← `IssueWaitingTicketDomainService.kt:48-54` `nonce()` — **`SecureRandom`으로 뽑은 16바이트를 32자 hex로 인코딩한 값**. `UuidGenerator`를 쓰지 않고, 생성 포맷도 UUID(8-4-4-4-12 하이픈)가 아니다 | **LEAVE-AS-VARCHAR** |

> 질문 (b) 답: `waiting_queue.ticket_id`는 `UuidGenerator`가 아니라 **`java.security.SecureRandom` 기반 32자 hex nonce**로 만들어진다(`IssueWaitingTicketDomainService.kt:9,14,27-33,48-53`). 과거 "결정적 해시(SHA-256(userId:slot))" 방식의 길이를 유지하려고 일부러 32자로 맞췄다는 주석이 같은 파일에 있다. 목표(goal)가 말하는 "UUID-bearing identifier columns"에 해당하지 않으므로 이번 전환 범위 밖이다 — BINARY(16)으로 바꾸면 포맷이 달라져 오히려 깨진다.

### 2e. `reservation` (명시적 요구 대상 4개 — `@Embedded` VO 경유)

`ReservationEntity`(1절 #10) 자신은 `id`만 선언하고, 나머지 4개는 전부 `@Embeddable` VO의 `@Column`이다.

| # | 표면 | `file:line` | 참조 대상 | 판정 |
|---|---|---|---|---|
| 18 | `reservation.user_id` | `reservation/entity/vo/ReservationBooker.kt:9-11` | `user.id` | CONVERT |
| 19 | `reservation.restaurant_id` | `reservation/entity/vo/ReservationRestaurant.kt:9-11` | `restaurant.id` | CONVERT |
| 20 | `reservation.timetable_id` | `reservation/entity/vo/ReservationTimeTable.kt:10-12` | `timetable.id` | CONVERT |
| 21 | `reservation.timetable_occupancy_id` | `reservation/entity/vo/ReservationTimeTableOccupancy.kt:13-15` | `timetable_occupancy.id` | CONVERT |

### 2f. 죽은 컬럼 (DB에는 있지만 어떤 `@Entity`도 매핑하지 않음)

| # | 표면 | `file:line` | 판정 |
|---|---|---|---|
| 22 | `user_change_history.log_id` | `V1_1__create_user_context.sql:28`만 존재 | LEAVE-AS-VARCHAR (쓰는 Kotlin 코드가 없음 — 전환 대상이 될 "현재 값"이 없다) |

> 질문 (c) 답: `user_change_history.log_id`는 **어떤 `@Entity`에도 매핑되어 있지 않다**. `grep -rn "log_id" infrastructure-module/src`가 `V1_1__create_user_context.sql:28` 단 한 줄만 반환한다 — `UserChangeHistoryEntity.kt`(위 2a #3,#4)는 `user_uuid`/`user_id`만 매핑하고 `log_id`는 건너뛴다. JPA `ddl-auto: validate`가 "엔티티가 모르는 컬럼이 테이블에 더 있는 것"은 통과시키므로(그 반대만 fail) 이 불일치가 지금까지 드러나지 않았다. 전환 범위에서 손댈 쓰기 경로가 없다.

### 2g. 뒤늦게 발견된 미전환 UUID 보유 컬럼 (이번 조사가 놓쳤던 것 — 수용된 잔존 위험)

| # | 표면 | `file:line` | writer 추적 | 판정 |
|---|---|---|---|---|
| 23 | `schedule.id` | `V1_13__schedule_context.sql:3` (`VARCHAR(255) PRIMARY KEY`), 매핑 `ScheduleEntity.kt:20-22` `@Id @Column(name="id") val restaurantId: String` | 값은 `restaurant.id`(1절 #1)를 그대로 복사 — `TimeBasedPrimaryKey`를 상속하지 않는 독립 `@Id String`이라 1절(서브클래스 전수조사)과 2절(명시적 추가 컬럼 전수조사) 양쪽 전수조사 기준에서 다 빠졌다 | **ACCEPTED-RESIDUAL (이번 전환 범위 밖)** |

> 늦은 발견에 대한 기록: `schedule.id`는 논리적으로 `restaurant.id`의 참조값이지만 PK 자신이 독립된 `@Id String` 필드(1절의 `TimeBasedPrimaryKey` 상속 패턴도, 2절의 "own `@Column`" 추가 컬럼 패턴도 아님)라서 이번 전수조사 양쪽 기준 어디에도 걸리지 않았다. FK 제약 0건, 참조하는 `@JoinColumn`/연관관계 없음, 접근 경로는 `findById(restaurantId: String)` 단일 경로뿐이라 오늘 시점 기능적으로는 안전하다. 전환하면 `restaurant.id`(BINARY(16))와 저장 형태가 다시 일치하지만, 이번 전환 묶음에는 포함하지 않고 **ADR-028 §잔존위험**에 수용된 잔존 위험으로 기록하고 후속 V1_24 expand/contract 쌍으로 넘긴다. 아래 §8 요약의 ACCEPTED-RESIDUAL 1건이 이 행이다.

---

## 3. IMPLICIT COLUMNS — `ddl-auto: validate`에서 제일 먼저 터지는 부류

`@JoinColumn`은 그 자체로 `columnDefinition`을 선언하지 않는다 — 참조되는 쪽(`@Id`가 걸린 PK)의 컬럼 타입을 Hibernate가 그대로 복사한다. PK를 `VARCHAR(128)` → `BINARY(16)`으로 바꾸면, 아래 7개 사이트는 **코드를 한 글자도 안 고쳐도 자동으로 타입이 따라간다** — 그리고 이게 바로 `ddl-auto: validate`가 걸리는 지점이다(Flyway DDL과 Hibernate가 기대하는 컬럼 타입이 다르면 부팅이 fail).

### 3a. `@JoinColumn` 사이트 (7개 전부)

| # | 소유 엔티티.컬럼 | `file:line` (`@JoinColumn`) | 같은 줄의 `@ManyToOne` | 참조 PK | 판정 |
|---|---|---|---|---|---|
| 1 | `restaurant_working_day.restaurant_id` | `RestaurantWorkingDayEntity.kt:27` | `:26` | `restaurant.id` | CONVERT — 단, 2a#1과 동일 물리 컬럼(이 사이트는 `@MapsId`로 2a#1의 명시적 `columnDefinition`을 공유하는 것이라, 순수 암묵 사례는 아니고 "명시 선언 + JoinColumn 재확인"이 겹친 케이스) |
| 2 | `restaurant_cuisines.restaurant_id` | `RestaurantCuisinesEntity.kt:30` | `:29` | `restaurant.id` | CONVERT (순수 암묵) |
| 3 | `restaurant_photo.restaurant_id` | `RestaurantPhotoEntity.kt:21` | `:20` | `restaurant.id` | CONVERT (순수 암묵) |
| 4 | `restaurant_nationalities.restaurant_id` | `RestaurantNationalitiesEntity.kt:30` | `:29` | `restaurant.id` | CONVERT (순수 암묵) |
| 5 | `restaurant_tags.restaurant_id` | `RestaurantTagsEntity.kt:30` | `:29` | `restaurant.id` | CONVERT (순수 암묵) |
| 6 | `menu_photo.menu_id` | `MenuPhotoEntity.kt:21` | `:20` | `menu.id` | CONVERT (순수 암묵) |
| 7 | `timetable_occupancy.timetable_id` | `TimeTableOccupancyEntity.kt:39` | `:43` | `timetable.id` | CONVERT (순수 암묵) |

7곳 전부 `foreignKey = ForeignKey(value/NO_CONSTRAINT)`를 명시하고 있다(각 `file:line`의 다음 줄들) — JPA 레벨에서도 FK를 만들지 말라고 명시했고, 실제 마이그레이션에도 FK가 없다(0절). DB가 타입 불일치를 막아줄 제약이 없다는 뜻이라, 1절 PK 전환과 이 7곳의 전환은 **반드시 같은 배포에 같이 들어가야** `ddl-auto: validate`가 통과한다.

### 3b. `@OneToMany` 역방향 사이드 (6개 — 컬럼을 직접 갖지 않음, `mappedBy`로 위 3a 사이트를 가리킴)

| # | 소유 엔티티.필드 | `file:line` | `mappedBy` 대상 (= 위 3a 행) | 판정 |
|---|---|---|---|---|
| 8 | `RestaurantEntity.workingDays` | `RestaurantEntity.kt:92-98` | 3a #1 | CONVERT (자체 컬럼 없음, owning side를 따라감) |
| 9 | `RestaurantEntity.tags` | `RestaurantEntity.kt:100-107` | 3a #5 | CONVERT |
| 10 | `RestaurantEntity.nationalities` | `RestaurantEntity.kt:109-116` | 3a #4 | CONVERT |
| 11 | `RestaurantEntity.cuisines` | `RestaurantEntity.kt:118-125` | 3a #2 | CONVERT |
| 12 | `RestaurantEntity.photos` | `RestaurantEntity.kt:127-134` | 3a #3 | CONVERT |
| 13 | `MenuEntity.photos` | `MenuEntity.kt:70-77` | 3a #6 | CONVERT |

`@OneToOne`은 레포 전체에 0건(`grep -rn '@OneToOne' infrastructure-module/src/main` 결과 없음).

---

## 4. FOREIGN KEY 및 식별자 컬럼을 덮는 제약/인덱스

### 4a. FOREIGN KEY — 검증된 사실: 0건

`grep -rci 'foreign key' infrastructure-module/src/main/resources/db/migration/` 의 각 파일별 매치를 합산하면 **0**이다. 3a절의 7개 `@JoinColumn`이 전부 `foreignKey = ForeignKey(NO_CONSTRAINT)`로 명시한 것과 정확히 들어맞는다 — ORM 레벨 선언과 DB 레벨 현실이 일치한다. 원래 요청에 있던 "FK 재생성 순서"는 **대상이 없다** — FK를 끊고 다시 거는 단계 자체가 계획에서 빠져야 한다.

### 4b. 전환 대상 식별자 컬럼을 덮는 제약/인덱스 (FK가 없는 대신 이게 실질적 안전장치)

| 제약/인덱스 | `file:line` | 덮는 컬럼 | 전환 영향 |
|---|---|---|---|
| `unique_slot_ticket_id` | `V1_19__waiting_queue.sql:11` | `(restaurant_id, date, start_time, ticket_id)` | `restaurant_id`만 CONVERT, `ticket_id`는 LEAVE(2d#17) — 바이트 구성 바뀌지만 유니크 의미는 유지 |
| `index_slot_status_id` | `V1_19__waiting_queue.sql:12` | `(restaurant_id, date, start_time, status, id)` | `restaurant_id` CONVERT |
| `unique_slot_user_id` | `V1_20__waiting_queue_user_id.sql:9` | `(restaurant_id, date, start_time, user_id)` | `restaurant_id`, `user_id` 둘 다 CONVERT |
| `unique_active_occupancy` | `V1_21__timetable_occupancy_hold.sql:25` | `(timetable_id, active_marker)` | `timetable_id` CONVERT (`active_marker`는 생성 컬럼, 식별자 아님) |
| `index_restaurant_id` | `V1_13__schedule_context.sql:24`, `V1_12__create_menu_context.sql:14` | `restaurant_id` (schedule, menu 각각) | CONVERT |
| `index_restaurant_date_start_time_table_status` | `V1_13__schedule_context.sql:64` | `(restaurant_id, date, start_time, table_status)` | `restaurant_id` CONVERT |
| `index_user_id_reservation_date_reservation_status` | `V1_18__reservation.sql:15` | `(user_id, reservation_date, reservation_status)` | `user_id` CONVERT |
| `index_restaurant_category` (3개, 동명) | `V1_11__create_restaurant_context.sql:56,70,84` | `(restaurant_id, {tags_id\|nationalities_id\|cuisines_id})` | `restaurant_id`만 CONVERT — `tags_id`/`nationalities_id`/`cuisines_id`는 `category.id`(BIGINT AUTO_INCREMENT)를 가리키는 `Long`이라 UUID 전환과 무관(검증: `RestaurantCuisinesEntity.kt:37-39`가 `cuisinesId: Long`으로 선언) |
| `index_menu_id` | `V1_12__create_menu_context.sql:26` | `menu_id` | CONVERT (3a#6과 동일 컬럼) |
| `index_timetable_id_occupied_status` | `V1_15__timetable.sql:9` | `(timetable_id, occupied_status)` | `timetable_id` CONVERT |
| `index_category_type` | `V1_4__create_category_context.sql:11` | `(category_type, id)` | **무관** — 이 `id`는 `category.id`, `BIGINT AUTO_INCREMENT`(`V1_4__create_category_context.sql:4`)다. VARCHAR도 UUID도 아니다. 이번 전환과 아무 관계 없음(인용 요구에 포함돼 있어 확인 차 기재) |

---

## 5. ORDERING DEPENDENTS — 커서/정렬이 바이트 순서에 의존하는 지점

컬렉션 정렬 기준: `docker-entrypoint-initdb.d/init.sql:1` → `prototype_reservation` DB는 `utf8mb4_general_ci`(대소문자 무시, 로케일 기반 콜레이션)로 생성된다. `VARCHAR(128)` UUID 문자열의 `>`/`ORDER BY`는 이 콜레이션 규칙으로 비교된다. `BINARY(16)`으로 바꾸면 **raw byte 비교**가 되므로, 같은 UUID 집합이라도 전/후의 정렬 순서가 달라질 수 있다 — 특히 시간 기반 UUID(UUIDv7, `UuidGenerator`)가 생성 시각 순서와 문자열 순서가 거의 일치하는 성질에 암묵적으로 의존하는 코드가 있다면 더 그렇다.

| # | `file:line` | 코드 | 왜 순서-의존인가 |
|---|---|---|---|
| 1 | `infrastructure-module/src/main/kotlin/com/reservation/persistence/restaurant/repository/dsl/RestaurantQuerySpec.kt:13` | `restaurantEntity.identifier.gt(identifier)` | 커서 페이지네이션 조건(`identifierAfter`) — `restaurant.id` 문자열 `>` 비교. 콜레이션 순서 → 바이트 순서로 바뀌면 "다음 페이지"의 경계가 달라짐 |
| 2 | `infrastructure-module/src/main/kotlin/com/reservation/persistence/restaurant/repository/dsl/FindRestaurantsRepository.kt:157` | `.orderBy(restaurantEntity.identifier.desc())` | #1과 짝을 이루는 `ORDER BY` — 같은 컬럼, 같은 전환 영향 |
| 3 | `batch-module/src/main/kotlin/com/reservation/batch/timetable/step/TimeTableStepConfig.kt:48` | `id?.let { scheduleEntity.restaurantId.gt(it) }` | Spring Batch 키셋 커서 조건 — `schedule.restaurant_id` 문자열 비교 |
| 4 | `batch-module/src/main/kotlin/com/reservation/batch/timetable/step/TimeTableStepConfig.kt:50` | `.orderBy(scheduleEntity.restaurantId.asc())` | #3과 짝 — 같은 컬럼 |
| 5 | `batch-module/src/main/kotlin/com/reservation/batch/querydsl/QueryDslCursorItemReader.kt:15,37,42,46` | `private var lastId: String?` ; `idExtractor(buffer.last())` ; `executionContext.getString("lastId", ...)` / `putString("lastId", it)` | #3/#4를 포함해 이 리더를 쓰는 모든 스텝의 커서 상태가 `String`으로 Spring Batch `ExecutionContext`에 직렬화·역직렬화된다 — **배포 중 재시작되는 Job의 중간 체크포인트**가 전환 전/후에 걸쳐 있으면, 저장된 `lastId`(옛 문자열 순서 기준)를 새 비교 규칙(바이트 순서)으로 재사용하는 셈이 되어 행을 누락하거나 중복 처리할 수 있다 |

> 결론: PK를 BINARY(16)으로 바꾸는 배포 창에는 **이 리더를 쓰는 배치 Job이 실행 중이면 안 된다** — 체크포인트가 걸쳐 있으면 안전하지 않다. expand/contract 계획의 contract 단계에, "배치 Job 정지 확인"을 명시적 전제조건으로 넣어야 한다(별도 ADR/계획 문서 몫, 이 문서는 사실만 기록).

---

## 6. 스키마 검증 컨텍스트 (수정하지 않음, 기록만)

| 파일:라인 | `ddl-auto` | 비고 |
|---|---|---|
| `infrastructure-module/src/main/resources/persistence/datasource/local.yaml:8` | `validate` | `database-platform: org.hibernate.dialect.H2Dialect`(`:6`) |
| `infrastructure-module/src/main/resources/persistence/datasource/stage.yaml:8` | `validate` | 동일 H2Dialect |
| `infrastructure-module/src/main/resources/persistence/datasource/production.yaml:8` | `validate` | 동일 H2Dialect |
| `infrastructure-module/src/main/resources/persistence/datasource/temporary.yaml:8` | `validate` | `database-platform: org.hibernate.dialect.MySQL8Dialect`(`:6`) — 유일하게 MySQL dialect |
| `adapter-module/src/test/resources/application-test.yaml:29` | `validate` | `database-platform: org.hibernate.dialect.MySQL8Dialect`(`:17`) |
| `batch-module/src/test/resources/application-test.yaml:29` | **`create-drop`** | 나머지와 다름 — 이 모듈 테스트는 스키마 drift를 숨길 수 있음 |

**기존·범위 밖 불일치 (손대지 않고 기록만)**
- `local.yaml`/`stage.yaml`/`production.yaml`이 선언하는 `H2Dialect`는 `infrastructure-module/src/main/resources/db/migration/*.sql`의 MySQL 전용 문법(ENUM, `COMMENT`, `GENERATED ALWAYS AS (...) STORED`, 백틱 식별자 등)과 맞지 않는다 — `ddl-auto: validate`가 세 프로필 모두에서 통과하려면 실제 접속 DB는 MySQL이어야 하는데 dialect 선언만 H2로 남아 있다.
- `adapter-module/build.gradle.kts:18`의 Flyway 설정은 `jdbc:mysql://localhost:3306/flyway` — Flyway 북키핑 커넥션을 `flyway` 스키마로 보낸다. 반면 모든 마이그레이션 스크립트는 `prototype_reservation.<table>`로 테이블을 명시한다(예: `V1_1__create_user_context.sql:1` `CREATE TABLE prototype_reservation.\`user\``). Flyway 자신의 히스토리 테이블(`flyway_version_control`, `application-test.yaml:40`)과 실제 DDL 대상 스키마가 분리되어 있는 구성이다.

---

## 7. 추적 실패 — writer를 못 확인해 표에서 뺀 것

없음. 이번 조사에서 발견한 모든 식별자 표면은 writer까지 추적됐다(위 1~5절). 추적에 성공하지 못한 표면이 생기면 이 절에 `file:line` + "왜 못 추적했는지"로 추가하고, CONVERT/LEAVE-AS-VARCHAR 판정은 내리지 않는다.

---

## 8. 요약 — 표면 수

| 분류 | 행 수 | CONVERT | LEAVE-AS-VARCHAR | ACCEPTED-RESIDUAL |
|---|---|---|---|---|
| 1절 PK `id` (TimeBasedPrimaryKey) | 18 | 18 | 0 | 0 |
| 2절 명시적 추가 컬럼 (2a-2f) | 22 | 18 | 4 | 0 |
| 2g절 뒤늦게 발견된 미전환 컬럼 | 1 | 0 | 0 | 1 |
| 3절 암묵 컬럼 (JoinColumn + OneToMany) | 13 | 13 | 0 | 0 |
| **합계** | **54** | **49** | **4** | **1** |

4절(제약/인덱스)과 5절(정렬 의존)은 "컬럼"이 아니라 "그 컬럼을 덮는 객체"라서 위 합계에 넣지 않았다 — 각자의 절 표가 곧 그 전수조사다. 2g절의 `schedule.id`(ACCEPTED-RESIDUAL)는 이번 전환 범위 밖이며 근거는 해당 절과 ADR-028 §잔존위험을 참조.
