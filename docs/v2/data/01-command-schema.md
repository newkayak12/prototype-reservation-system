# 01 · command 스키마 (event_store · outbox · snapshot · 비-ES 상태)

> 허브: [[00-data-index]] | 근거: [[DESIGN-003-write-model]] · [[DESIGN-009-event-store-lifecycle]] · [[ADR-005-event-store-mysql-table]] · [[06-command-infrastructure]] · [[RFC-014-aggregate-concurrency-control]]·[[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[RFC-025-ordering-relay-dlq-reconciliation]]

## 0. 소속·배치

command MySQL 인스턴스 하나에 **쓰기 모델 성격이 다른 3그룹**이 공존한다([[DESIGN-003-write-model]] §4).

| 그룹 | 컨텍스트 | 진실의 원천 | 절 |
|------|----------|-------------|-----|
| ES | `reservation`·`timetable`·`restaurant` | event_store(append-only) | §1 |
| 상태+Outbox | `schedule`·`user`·`authenticate`(credential 제외) | 상태 테이블 | §2 |
| 현행/lookup | `menu`·`category`·`company` | 상태 테이블(저빈도) | §3 |

> `authenticate`의 credential·refresh 상태는 [[03-auth-schema]] 소관이다 — auth-server-module이 독립 datasource를 쓰므로 이 스키마엔 없다([[09-auth-server-module]] §7).

---

## 1. ES 공용 인프라 테이블 (`reservation`·`timetable`·`restaurant` 공유)

### 1.1 `event_store` — 진실의 원천 (append-only)

확정 — [[DESIGN-003-write-model]] §4.1 · [[ADR-005-event-store-mysql-table]] · [[06-command-infrastructure]] §5.4

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `event_id` | `BINARY(16)` | **PK** | UUIDv7 — 전역 유일 정체성 + inbox dedup/causation 앵커 + 재구축 keyset 커서 겸용. 순서 정확성 보장은 아님([[DESIGN-003]] 자기리뷰) |
| `aggregate_type` | `VARCHAR(64)` | NOT NULL | `RESERVATION`/`TIMETABLE`/`RESTAURANT` 등 |
| `aggregate_id` | `VARCHAR(128)` | NOT NULL | 애그리거트 식별자 |
| `sequence_no` | `BIGINT` | NOT NULL | 애그리거트 내 순번(1부터) |
| `event_type` | `VARCHAR(128)` | NOT NULL | 이벤트 타입 판별자. **주의**: 이 값으로 런타임 분기(문자열/enum 스위치)하면 Konsist·Gradle 어느 쪽도 못 잡는다 — [[00-module-index]] §4 한계 참조 |
| `event_version` | `INT` | NOT NULL | 스키마 진화 버전([[RFC-022-event-schema-evolution]]) |
| `payload` | `JSON` | NOT NULL | event-carried(내용 실음) — [[RFC-029-event-carried-payload-uniform]] 확정 |
| `occurred_at` | `DATETIME(6)` | NOT NULL | |
| `correlation_id` | `BINARY(16)` | NOT NULL | 사슬 루트, 무변경 전파 |
| `causation_id` | `BINARY(16)` | NULL | 직전 원인 `event_id`(또는 `commandId`) |
| `traceparent` | `VARCHAR(64)` | NULL | W3C Trace Context |

- **UNIQUE** `(aggregate_id, sequence_no)` — L0 safety 백스톱. 비관 락(L1)이 liveness만 보장하므로 **절대 제거하지 않는다**([[ADR-016-aggregate-concurrency-pessimistic-lock]]).
- **인덱스**: `(aggregate_id, sequence_no)`가 스트림 조회(리플레이)를 그대로 커버.
- **파티셔닝**: 생성월(occurred_at) 기준 — 현재는 YAGNI, 성장 시 도입([[DESIGN-009-event-store-lifecycle]] §4.2).
- **컨텍스트별 구체 이벤트 카탈로그(이벤트 타입 목록·페이로드 shape)는 Non-Goal** — 이벤트 스토밍 재실시 후 별도 문서로 확정한다([[DESIGN-003-write-model]] §3).

### 1.2 `snapshot` — 리플레이 단축용 캐시 (버릴 수 있음)

확정 — [[DESIGN-009-event-store-lifecycle]] §4.4

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `aggregate_type` | `VARCHAR(64)` | NOT NULL | |
| `aggregate_id` | `VARCHAR(128)` | **PK** | 핫 DB엔 애그리거트당 **최신 1개만** |
| `sequence_no` | `BIGINT` | NOT NULL | 이 스냅샷이 반영한 마지막 이벤트 순번 |
| `schema_version` | `INT` | NOT NULL | 스냅샷 직렬화 스키마 버전 |
| `state` | `JSON` | NOT NULL | 애그리거트 상태 직렬화 |
| `created_at` | `DATETIME(6)` | NOT NULL | |

- 밀려난 직전본은 S3 Glacier로 이관(콜드) — 핫 DB 테이블 자체엔 없음.
- 스키마 버전 불일치 시 **업캐스팅하지 않고 폐기 후 이벤트 리플레이로 재생성**(진실은 이벤트).
- 스냅샷 생성 주기 N은 미결(M-5, [[06-command-infrastructure]] §7) — 측정 후 확정.

### 1.3 `outbox` — 대외 이벤트 발행 브릿지 (ES·비-ES 공용)

부분 확정 — V1 `outbox`(`V1_16__outbox.sql`) 계승 + [[RFC-025-ordering-relay-dlq-reconciliation]] 결정 1·5 반영. 정확한 컬럼은 구현 시 확정하되, 아래는 이미 결정된 제약을 반영한 설계.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | `BIGINT AUTO_INCREMENT` | **PK** | V1 계승 |
| `aggregate_id` | `VARCHAR(128)` | NOT NULL | relay 파티션 키(Kafka) — [[06-command-infrastructure]] §5.2 |
| `sequence_no` | `BIGINT` | NULL 허용 | ES 이벤트의 애그리거트 내 순번(추적·상관용). **비-ES 행은 해당 없음(NULL)** — 비-ES는 순서 토큰이 없고([[RFC-021]] §54), 발행 순서는 단일 순차 relay가 보장한다([[RFC-032-non-es-state-copy-reordering]] 결정 1·2). event_store의 sequence_no와 "동일 계열"이라는 기정사실화는 철회 |
| `event_type` | `VARCHAR(128)` | NOT NULL | Kafka 토픽 라우팅(`<context>.<aggregate-type>`) |
| `payload` | `JSON` | NOT NULL | |
| `status` | `ENUM` | NOT NULL | V1은 `PUBLISHED`/`PROCESSED`/`ERRORED` 3값. **V2는 발행 전 상태(예: `PENDING`)가 필요** — relay가 폴링 대상을 구분해야 하므로. 정확한 enum 값은 구현 시 확정 |
| `created_at` | `DATETIME(6)` | NOT NULL | V1 계승 |
| `updated_at` | `DATETIME(6)` | NOT NULL | V1 계승 |

- **동일 트랜잭션 전제**: event_store(또는 상태 테이블) append + outbox insert는 **같은 트랜잭션·같은 datasource**([[DESIGN-003-write-model]] §4.4 자기리뷰 채택) — 2PC 회피의 근거이자 저장소 분리 one-way door([[06-command-infrastructure]] 핵심취약점, 미해소).
- **인덱스**: `status`(폴링 대상 조회). relay는 outbox를 **삽입 순서(`id` ASC)로 통짜 드레인**해 순차 발행([[RFC-025]] 결정 1 · [[ADR-009-event-ordering-and-delivery-guarantee]] — **Quartz 클러스터 단일 리더**가 `SKIP LOCKED` 경쟁 소비를 supersede, 경쟁 드레인 금지 I-RELAY-ORDER). 전역 드레인 키는 PK `id`다 — `sequence_no`는 애그리거트별 순번이라 혼합 outbox의 전역 정렬 키가 될 수 없다.

### 1.4 `aggregate_lock` — DB 폴백 락 (L1′, Redis 장애 시)

**설계 공백 — 아직 확정 안 됨.** [[DESIGN-003-write-model]] §4.1은 "Redis 불가 시 DB `FOR UPDATE` 폴백(L1′)"을 명시하지만, ES 애그리거트는 event_store만 있고 락을 걸 "상태 행"이 없다. 폴백이 실제로 동작하려면 `aggregate_id`별로 `SELECT ... FOR UPDATE`할 최소 락 테이블이 필요하다는 뜻만 이 문서에 표시한다.

| 컬럼(제안) | 타입 | 설명 |
|------------|------|------|
| `aggregate_id` | `VARCHAR(128)` PK | |
| `locked_at` | `DATETIME(6)` | 참고용 — 실제 락은 트랜잭션 범위의 행 락 |

> 정확한 필요 여부·컬럼은 구현 사이클에서 확정([[06-command-infrastructure]] §5.3).

---

## 2. 비-ES 상태 테이블 — `schedule`·`user` (V1 계승, 상태+Outbox)

확정 — [[DESIGN-003-write-model]] §4.2: "V1 방식 유지, 같은 트랜잭션에서 Outbox 통합 이벤트만 추가". 컬럼은 V1 마이그레이션 그대로(`infrastructure-module/.../V1_13__schedule_context.sql`, `V1_1__create_user_context.sql`).

| 테이블 | 컨텍스트 | 주요 컬럼 | 비고 |
|--------|----------|-----------|------|
| `schedule` | schedule | `id` PK, `tables_configured`/`working_hours_configured`/`holidays_configured` BOOLEAN, `status` ENUM(ACTIVE/INACTIVE), `total_tables`, `total_capacity` | |
| `time_span` | schedule | `id` PK, `restaurant_id`, `day` ENUM, `start_time`/`end_time` TIME | INDEX `(restaurant_id)` |
| `holiday` | schedule | `id` PK, `restaurant_id`, `date` DATE | |
| `table` | schedule | `id` PK, `restaurant_id`, `table_number`, `table_size` | |
| `user` | user | `id` PK, `login_id`, `password`/`old_password`, `password_changed_datetime`, `email`, `nickname`, `mobile`, `role` ENUM, `fail_count`, `locked_datetime`, `user_status` ENUM | INDEX `(login_id, role)` |
| `user_change_history` | user | `id` PK, `user_id`, 변경 스냅샷 컬럼(email/nickname/mobile/role 등) | 감사 이력 |
| `user_access_history` | user | `id` PK, `user_uuid`, `access_status` ENUM, `access_datetime` | |
| `withdrawal_user` | user | `id` PK, 암호화된 개인정보 컬럼(`encrypted_*`) | 탈퇴 회원 — [[RFC-005-pii-security]] |

- `authenticate`의 credential(`current_refresh_jti` 등)은 이 스키마가 아니라 [[03-auth-schema]] 소관 — `authenticate` 컨텍스트 존속 범위 자체가 미결([[09-auth-server-module]] M-7).
- 위 테이블 모두 **변경 없음** — 추가되는 것은 같은 트랜잭션 내 `outbox` insert 하나뿐([[DESIGN-003-write-model]] §4.2).

---

## 3. 현행/lookup 테이블 — `menu`·`category`·`company` (변경 없음)

확정 — [[DESIGN-003-write-model]] §4.3: 저빈도·lookup, 다른 컨텍스트가 구독해야 할 때만 Outbox 추가. 컬럼은 V1 그대로.

| 테이블 | 컨텍스트 | 주요 컬럼 | 비고 |
|--------|----------|-----------|------|
| `menu` | menu | `id` PK, `restaurant_id`, `title`, `description`, `price`, `is_representative`/`is_recommended`/`is_visible`/`is_deleted` | INDEX `(title, is_deleted)`, `(restaurant_id)` |
| `menu_photo` | menu | `id` PK, `menu_id`, `url` | INDEX `(menu_id)` |
| `category` | category | `id` PK(AUTO_INCREMENT), `title`, `category_type` ENUM, `is_deleted` | INDEX `(category_type, id)` |
| `company` | company | `id` PK, `brand_name`, `business_number`, `representative_name` 등 | |
| `feature_flag` | (횡단) | `id` PK, `feature_flag_type` ENUM, `feature_flag_key`, `is_enabled` | 도메인 외 공용 설정 |

---

## 4. ES 대상 컨텍스트의 V1 상태 테이블 — 참고용 (V2 command DB엔 없음)

V1의 `reservation`·`timetable`·`timetable_occupancy`·`restaurant`(+`restaurant_photo`/`restaurant_tags`/`restaurant_nationalities`/`restaurant_cuisines`)는 **V2에서 event_store로 대체된다** — 이 테이블들을 V2 command 스키마에 그대로 재생성하지 않는다. 아래는 V2 read model(§[[02-query-schema]])의 초기 컬럼 설계·이벤트 페이로드 추정 시 참고할 V1 원본 컬럼 목록이다.

| V1 테이블 | 주요 컬럼(참고) | V2 행선지 |
|-----------|------------------|-----------|
| `reservation` | `user_id`, `restaurant_id`, `timetable_id`, `reservation_date`, `reservation_time`, `reservation_seat_size`, `reservation_status`, `reservation_cancelled_datetime` | event_store(`RESERVATION`) + [[02-query-schema]] §2.1 `ReservationView` |
| `timetable` | `restaurant_id`, `date`, `day`, `start_time`/`end_time`, `table_size`, `table_status`, `time_table_confirm_status` | event_store(`TIMETABLE`) + [[02-query-schema]] §2.2 `TimeTableAvailabilityView` |
| `timetable_occupancy` | `timetable_id`, `user_id`, `occupied_status`, `occupied_datetime`/`unoccupied_datetime` | event_store(`TIMETABLE`) 이벤트로 흡수(좌석 점유/해제) |
| `restaurant`(+ photo/tags/nationalities/cuisines) | `name`, `address`, `latitude`/`longitude`, 태그·국적·요리 조인 테이블 | event_store(`RESTAURANT`) + [[02-query-schema]] §2.3 `RestaurantSearchView` |

---

## 5. 관련 문서

- [[00-data-index]] · [[02-query-schema]] · [[03-auth-schema]]
- [[DESIGN-003-write-model]] · [[DESIGN-009-event-store-lifecycle]] · [[ADR-005-event-store-mysql-table]]
- [[06-command-infrastructure]] · [[RFC-014-aggregate-concurrency-control]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[RFC-025-ordering-relay-dlq-reconciliation]]
