# 02 · query 스키마 (도메인별 read model + inbox)

> 허브: [[00-data-index]] | 근거: [[DESIGN-004-read-model]] · [[07-query-projection-server]] · [[08-query-read-model-server]] · [[RFC-025-ordering-relay-dlq-reconciliation]] · [[RFC-030-read-freshness-command-response-contract]] · [[ADR-013-db-hosting-and-read-write-topology]]

## 0. 조직 원칙

- **도메인별 스키마 분리**(`query.{domain}.model`) — read model은 화면·조회 용도마다 여럿 생기는데, 한 query 인스턴스 안에서 도메인별 스키마로 나눠 담는다. 도메인 경계 = 스키마 경계로, command 측 컨텍스트 분리와 대칭([[DESIGN-004-read-model]] §4.2).
- **프로젝션을 만들 자격 — '읽기 요구 입증' 기준**([[DESIGN-004-read-model]] §4.4). 아래 중 하나라도 해당해야 프로젝션(read model)을 가진다: (1) 교차 컨텍스트 조인 회피, (2) ES 컨텍스트의 현재상태 조회(필수), (3) 읽기 모양이 쓰기 모델과 다름, (4) 읽기 부하 격리. 안 닿으면 lookup(§3)로 간다.
- **command 테이블 직접 조회 금지** — query DB는 command DB와 물리 분리([[ADR-013-db-hosting-and-read-write-topology]]). 유일한 유입 경로는 Kafka→projector.
- **컨텍스트별 구체 이벤트 카탈로그·정확한 read model 컬럼 셋은 이벤트 스토밍 재실시 후 확정하는 Non-Goal** — 아래 §2의 컬럼은 [[07-query-projection-server]]·[[08-query-read-model-server]]가 예시로 든 것 + V1 원본 컬럼([[01-command-schema]] §4)을 근거로 한 **설계 예시**다.

---

## 1. 공용 인프라 테이블

### 1.1 `inbox` — 멱등 dedup + LWW 순서 가드 (프로젝터마다 인스턴스, 소속 도메인 스키마에 위치)

확정 — [[RFC-025-ordering-relay-dlq-reconciliation]] 결정 5 · [[07-query-projection-server]] §5.2

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `event_id` | `BINARY(16)` | **PK** | dedup 대상(이미 처리했나?) |
| `aggregate_id` | `VARCHAR(128)` | NOT NULL, INDEX | LWW 가드 조회 키 |
| `last_applied_sequence_no` | `BIGINT` | NOT NULL | 이 aggregate에 마지막으로 적용한 sequence_no — 갱신 시 e2(seq6)가 e1(seq5)보다 먼저 와도 e2가 이기고 뒤늦은 e1은 가드가 떨어뜨린다(재정렬 자가치유) |
| `processed_at` | `DATETIME(6)` | NOT NULL | |

- **GC 정책**: 무한 축적 금지 — 재처리 윈도를 덮을 만큼만 보존 + 주기적 GC. 단 **aggregate별 `last_applied_sequence_no`는 GC 대상에서 제외**(LWW 가드의 영구 토대) — `event_id` dedup 로그와 aggregate별 최신 커서는 보존 수명이 다르다는 뜻이므로, 실제 구현에서 한 테이블로 둘지 `event_id` 로그/`aggregate` 커서 두 테이블로 분리할지는 **미확정**([[07-query-projection-server]] §5.2, 구현 시 확정).
- **inbox 생략 자격**: "순서 역전 없음 + 자연 멱등 upsert"를 동시에 만족하는 컨슈머만 `event_id`-only로 축소 가능(commutative 집계 등). 대부분의 프로젝션은 위 전체 스키마를 유지한다([[RFC-025]] 논점 2).

### 1.2 read model 공통 컬럼 관례 — `appliedSequenceNo`

확정 — [[07-query-projection-server]] §5.2 · [[08-query-read-model-server]] §5.0.1

모든 프로젝션 read model 테이블은 자신이 반영한 원본 이벤트의 `sequence_no`를 `applied_sequence_no` 컬럼으로 보유한다. `ReadFreshnessGate`([[RFC-030-read-freshness-command-response-contract]] 결정 4)가 이 컬럼과 클라이언트가 커맨드 응답에서 받은 `sequenceNo`를 비교해 read-your-writes를 판단하는 유일한 자리다 — 이 컬럼이 없으면 §1.2의 신선도 계약 자체가 성립하지 않는다.

---

## 2. 도메인별 read model — ES 컨텍스트 (프로젝션 필수)

### 2.1 `reservation` — `ReservationView` (`query.reservation.model`)

설계 예시 — [[08-query-read-model-server]] §4·§5, V1 `reservation` 테이블([[01-command-schema]] §4) 근거

| 컬럼 | 설명 |
|------|------|
| `id` | 예약 PK |
| `user_id` | |
| `restaurant_id` | |
| `restaurant_name` | **비정규화** — `restaurant` 컨텍스트의 `RestaurantRenamed` 이벤트를 구독해 갱신([[DESIGN-004-read-model]] §4.5) |
| `timetable_id` | |
| `reservation_date` / `reservation_day` / `reservation_time` | |
| `seat_size` | |
| `status` | `RESERVED`/`CANCELLED` 등 |
| `visit_at` / `cancelled_at` | |
| `applied_sequence_no` | §1.2 공통 컬럼 |

- INDEX `(user_id, reservation_date, status)` — V1 계승.
- **다중 소스 프로젝션**: 이 read model 행은 `reservation` 이벤트 스트림과 `restaurant` 이벤트 스트림(식당명) **두 소스**에서 갱신된다. "한 이벤트=한 트랜잭션+오프셋 커밋" 규칙 아래 식당명이 바뀐 뒤 아직 리네임 이벤트를 처리 못 한 행과 이미 처리한 행이 공존하는 **부분 갱신이 정상 동작**이다 — 갱신 순서·원자성·"어느 시점 스냅샷"은 아직 구현 사이클 미결([[DESIGN-004-read-model]] §4.5·[[07-query-projection-server]] §6).

### 2.2 `timetable` — `TimeTableAvailabilityView` (`query.timetable.model`)

설계 예시 — [[07-query-projection-server]] §4, V1 `timetable` 테이블 근거

| 컬럼 | 설명 |
|------|------|
| `restaurant_id` | |
| `date` / `day` | |
| `start_time` / `end_time` | |
| `table_size` | |
| `status` | 가용/점유 |
| `applied_sequence_no` | §1.2 공통 컬럼 |

- INDEX `(restaurant_id, date, start_time, status)` — V1 계승.

### 2.3 `restaurant` — `RestaurantSearchView` (`query.restaurant.model`)

설계 예시 — [[07-query-projection-server]] §4, V1 `restaurant`(+tags/nationalities/cuisines) 테이블 근거

| 컬럼 | 설명 |
|------|------|
| `id` | |
| `name` | |
| `address` / `zip_code` / `latitude` / `longitude` | |
| `tags` | 태그·국적·요리 비정규화 — 조인 테이블(V1 `restaurant_tags` 등)을 어떻게 비정규화할지(칼럼 vs 별도 검색 인덱스)는 미확정 |
| `applied_sequence_no` | §1.2 공통 컬럼 |

- 검색 인덱스 구현(단순 `name` INDEX vs 풀텍스트/외부 검색엔진)은 구현 시 확정.

---

## 3. 도메인별 read model — 비-ES lookup (소유권 미확정)

`schedule`·`user`·`menu`·`category`·`company`는 이벤트 프로젝션이 아니라 **projection 또는 published-subscription** 중 하나로, 데이터 소유 컨텍스트가 확정돼야 정해진다([[DESIGN-004-read-model]] §4.2 (나)). 잠정적으로 V1 컬럼과 거의 동일한 async-fed 로컬 카피로 본다.

| 컨텍스트 | 잠정 read model | 소유권 상태 |
|----------|-----------------|-------------|
| `schedule` | `ScheduleView` (V1 `schedule`/`time_span`/`holiday`/`table` 근거) | 프로젝션 vs 경량 lookup — 변화 빈도 측정 후([[08-query-read-model-server]] R-2) |
| `user` | `UserView` (V1 `user` 근거, 경량) | 확정 — Outbox 이벤트 구독 |
| `menu` | `MenuView` (V1 `menu`/`menu_photo` 근거) | 소유권 미확정([[08-query-read-model-server]] R-3) |
| `category` | `CategoryView` (V1 `category` 근거) | 소유권 미확정(R-3) |
| `company` | `CompanyView` (V1 `company` 근거) | 소유권 미확정(R-3) |

- **cross-context 동기 조회 금지** — 조회 시점에 원본을 동기 호출하지 않는다. 남이 흘리는 걸 비동기로 받아 로컬 테이블만 갱신한다([[DESIGN-004-read-model]] §4.2).
- 비-ES가 ES 데이터를 조인해야 하는 경우(예: 예약 상세의 메뉴)의 처리는 미결([[08-query-read-model-server]] R-4).

---

## 4. 관련 문서

- [[00-data-index]] · [[01-command-schema]] · [[03-auth-schema]]
- [[DESIGN-004-read-model]] · [[07-query-projection-server]] · [[08-query-read-model-server]]
- [[RFC-025-ordering-relay-dlq-reconciliation]] · [[RFC-030-read-freshness-command-response-contract]] · [[RFC-011-projection-rebuild-catchup]]
