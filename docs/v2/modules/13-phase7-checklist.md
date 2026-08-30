# 13 · Phase 7 진행 체크리스트 (실시간 트래킹)

> 허브: [[00-module-index]] | 태스크 SSOT: [[12-implementation-plan]] | 실행 계획: [[00-status-and-plan]] §6
> 이 문서는 **진행 상태**를 세부 체크박스로 추적한다. 각 항목의 근거(할 일 원본)는 해당 모듈 문서 §6/§8/§9/§10.
> 최종 갱신: 2026-07-28

범례: `[ ]` 미착수 · `[~]` 진행중 · `[x]` 완료 · ⭐ 마일스톤 · `[G#]` 검증 게이트 연결

---

## 7-0 · 사전 정리 (Day 1-2)

- [ ] **7-0-①** V1 불필요 코드 정리 → 클린 베이스라인 (빌드·테스트 green 고정)
- [ ] **7-0-②** `shared → contract` 이동 대상 타입 식별 → 이동 목록 ([[01-shared-module]])
- [x] **7-0-③** Gradle 멀티모듈 뼈대(빈 모듈) 생성
  - [x] `settings.gradle.kts` V2 7개 모듈 include (V1 유지, Strangler)
  - [x] 각 모듈 `build.gradle.kts` 의존성 [[00-module-index]] §2 매트릭스대로 배선 (금지 의존 미선언)
  - [x] `contract`/`command-core` Spring·JPA 물리 배제 (exclude 블록)
  - [x] `gradle projects` 등록 + 전 모듈 `compileKotlin` 통과 (`BUILD SUCCESSFUL`)

## 7-1 · contract-module (Day 3-5) → [[02-contract-module]] §6

- [ ] `AbstractEvent` 봉투 설계 (정체성 `eventId`/`sequenceNo` + 추적메타 `correlation/causation/traceparent` — [[ADR-022]])
- [ ] 레퍼런스 컨텍스트(`timetable`·`reservation`) 통합 이벤트 클래스 정의 (event-carried — [[RFC-029]])
- [ ] 이벤트 버전/직렬화 전략 확정 (JSON + `eventType` 매핑 — [[ADR-010]]·[[RFC-022]])
- [ ] `eventType → 클래스` 복원 레지스트리 **소유 위치** 확정 (복원은 application, 정의 경계는 여기 — [[DESIGN-019]] §6)
- [x] `build.gradle.kts` Spring/JPA 배제 확인 (7-0-③에서 완료)

## 7-2 · command-core (Day 6-10) → [[03-command-core]] §6

- [ ] `EventSourcingAggregate` 추상 (`handle`→events / `apply`→newState 계약)
- [ ] `StatefulAggregate` 추상 (비-ES, 상태 변경 중심)
- [ ] 레퍼런스: `TimeTable` 애그리거트 ES 전환 (가장 단순한 ES)
- [ ] 레퍼런스: `Reservation` 애그리거트 ES 전환 (사가 포함)
- [ ] 순수 Kotlin `require` 검증 전략 확정 (jakarta.validation 대체 — 미결 **M-4**)
- [ ] Konsist 컨텍스트 간 참조 금지 규칙 — `support` 제외 도메인 목록 동적 순회 일반 규칙 ([[RFC-031]] R3) **[G5]**
- [ ] 단위 테스트 (Kotest 상태 전이 BDD)

## 7-3 · command-application (Day 11-14) → [[04-command-application]] §6

- [ ] 포트 4종: `EventStorePort` / `OutboxPort` / `StateStorePort` / `AggregateLockPort`
- [ ] `EventSerializer` — `eventType` 복원 레지스트리(명시 등록 — [[DESIGN-009]] §4.2) **[G6]**
- [ ] `AggregateRehydrator` — load → 역직렬화 → fold(apply)
- [ ] `AggregateConflictException`(409) · 도메인 거절(422) 예외 매핑 (판별축: 상태 실제 변경 — [[ADR-016]])
- [ ] 레퍼런스 UseCase: `OccupyTimeTable` · `CreateReservation` · `CancelReservation` (비관 락 — [[ADR-016]])
- [ ] core `DomainEvent` → contract 통합 이벤트 매핑 계층 (매핑 위치=application, **M-2** 확정)
- [ ] 비-ES: `StateStorePort` + 상태 저장 유스케이스
- [ ] 단위 테스트 (Kotest `BehaviorSpec` + MockK — [[ADR-014]])

## 7-4 · adapter + infrastructure + auth-server (Day 15-22) — 일부 병렬

### 7-4a · command-adapter → [[05-command-adapter]] §6

- [ ] `EventStoreJpaAdapter` (append-only + `DataIntegrityViolationException` → `AggregateConflictException` 번역, 동일 세션 재시도 금지) **[G2]**
- [ ] `StoredEventJpaEntity` + JPA Repository (event_store 매핑)
- [ ] `OutboxJpaAdapter` + `OutboxJpaEntity`
- [ ] 비-ES: `StateStoreJpaAdapter` (도메인 상태 ↔ JPA 수동 매핑, `@Entity` 도메인 금지)
- [ ] Command REST Controller (레퍼런스 컨텍스트)
- [ ] pre-authenticated Security 설정 (JwtFilter 없음 — 엣지 검증)
- [ ] REST Docs → OpenAPI 스니펫
- [ ] 통합 테스트 (Testcontainers MySQL)

### 7-4b · command-infrastructure → [[06-command-infrastructure]] §6

- [ ] Flyway: `event_store` / `outbox` / `snapshot` DDL (append + `UNIQUE(aggregate_id, sequence_no)`) **[G1][G2]**
- [ ] `EventStoreEngine` (append/load bytes, replay 지원, snapshot)
- [ ] Outbox relay (폴링 + Quartz 클러스터 단일 리더 · 삽입 순서 id ASC 통짜 드레인) + 재시도 스케줄러 **[G3]**
- [ ] Kafka producer 설정 (파티션 키 = `aggregate_id`, 토픽 `<context>.<aggregate>` — [[DESIGN-008]])
- [ ] Redisson 락(L1) 설정 + DB `FOR UPDATE` 폴백(L1')
- [ ] UUIDv7 ID 생성기
- [ ] V1 `infrastructure-module` 이전
- [ ] 통합 테스트 (Testcontainers MySQL + Kafka)

### 7-4c · auth-server-module → [[09-auth-server-module]] §8

- [ ] SAS 카탈로그 의존성 추가 (`spring-boot-starter-oauth2-authorization-server` — [[RFC-020]]·[[ADR-024]])
- [ ] 모듈 뼈대 + JWT 발급(access body + refresh cookie) + JWKS 노출
- [ ] refresh rotation + `current_refresh_jti` 관리
- [ ] JTI 재사용 탐지 → 전 세션 무효화
- [ ] V1 로그인 통합 (General/Seller → 단일 `/auth/login`)
- [ ] V1 JwtFilter/토큰 코드 제거 (command/query에서)

## 7-5 · query — projection + read model (Day 23-28)

### 7-5a · projection 서버 (쓰기 경로) → [[07-query-projection-server]] §9

- [ ] `ParallelConsumerConfig` — `ordering=KEY`, `PERIODIC_TRANSACTIONAL`, `max-concurrency` 4→8→16 점진
- [ ] consumer group per projector + `cooperative-sticky`
- [ ] 레퍼런스: `TimeTableAvailabilityProjector`
- [ ] 레퍼런스: `ReservationListProjector` (+ 식당명 비정규화 다중 소스)
- [ ] 레퍼런스: `RestaurantSearchProjector`
- [ ] inbox 테이블 (`event_id` dedup만) + 멱등 기록/GC ([[ADR-009-event-ordering-and-delivery-guarantee]] — 구 LWW `last-applied sequence_no` 폐기, 순서는 offset 순서가 보존) **[G4]**
- [ ] read model row `appliedSequenceNo` 컬럼 (read-after-write 신선도용)
- [ ] Flyway: read model + inbox 스키마 (도메인별 분리)
- [ ] 재구축·catch-up·blue-green 오케스트레이션 ([[RFC-011]])
- [ ] DLQ + 재시도/백오프 + Slack 알람 (라이브 재주입 금지 — [[RFC-025]] 결정 3)
- [ ] Actuator lag 관측
- [ ] E2E (Command → Event → Projection → Query, Testcontainers Kafka+MySQL)

### 7-5b · read model 서버 (읽기 경로) → [[08-query-read-model-server]] §10

- [ ] Read Model JPA 엔티티 + QueryDSL Repository (도메인별 스키마, `appliedSequenceNo` 포함)
- [ ] `ReadFreshnessGate` — `sequenceNo` 비교 + bounded long-poll + 폴백 ([[RFC-030]])
- [ ] Query Service (읽기 전용 txn) + 응답 DTO 매핑
- [ ] Query REST Controller (레퍼런스: reservation/timetable/restaurant)
- [ ] pre-authenticated Security + 스코프 조건 (행 수준 "내 것만")
- [ ] 비-ES 컨텍스트: V1 QueryDSL 조회 코드 이전
- [ ] QueryDSL Q타입 생성(kapt) 설정
- [ ] HA 레플리카 라우팅 설정 (선택)
- [ ] REST Docs → OpenAPI
- [ ] 조회 슬라이스 테스트 (Testcontainers MySQL)

- [ ] ⭐ **M1: `timetable` 수직 슬라이스 완성** — Command → event_store/outbox → Kafka → projection → read model 조회까지 관통, 레퍼런스 패턴 검증 완료

## 7-6 · 나머지 컨텍스트 전환 (Day 29+) → [[12-implementation-plan]] §7-6

각 컨텍스트는 7-1~7-5 슬라이스를 레퍼런스 패턴대로 반복.

- [ ] **3. `restaurant`** (ES) — 레퍼런스 패턴 적용
- [ ] **4. `schedule`** (상태+Outbox) — 비-ES 레퍼런스, 단일 순차 relay 공통 처리 ([[RFC-032]])
- [ ] **5. `user`** (상태+Outbox) — schedule 패턴 복제
- [ ] **6. `authenticate`** (상태+Outbox / auth 흡수) — **M-7** 존속 범위 확정
- [ ] **7. `menu`·`category`·`company`** (현행) — read-only 마이그레이션만
- [ ] ⭐ **M2: 전 컨텍스트 전환 완료** — replay 결정성 게이트 G7 통과, V1 모듈 제거

---

## 검증 게이트 (fail-closed) — [[00-status-and-plan]] §6.4

- [ ] **G1** 원자성: `event_store` append + `outbox` insert 동일 트랜잭션/datasource (I-OUTBOX-1 — [[ADR-027]])
- [ ] **G2** `L0` `UNIQUE(aggregate_id, sequence_no)` 백스톱 ([[ADR-016]])
- [ ] **G3** 발행 순서: Quartz 클러스터 단일 리더 순차 relay, 삽입 순서 통짜 드레인 ([[ADR-009-event-ordering-and-delivery-guarantee]]·[[RFC-025]])
- [ ] **G4** 멱등·순서: inbox `event_id` 중복 차단 + 파티션 offset 순서 apply(단일 스레드, I-CONSUME-ORDER) ([[ADR-009-event-ordering-and-delivery-guarantee]]·[[RFC-025]])
- [ ] **G5** 모듈 경계: Gradle 그래프 + Konsist ([[RFC-031]])
- [ ] **G6** 타입 소유: core 이벤트 타입은 command-application만 인지 ([[DESIGN-019]])
- [ ] **G7** replay 결정성 ([[RFC-011]])

## 구현 중 확정 항목 (첫 레퍼런스에서 결정) — [[00-status-and-plan]] §6.6

- [ ] 다중 소스 프로젝션 **부분 갱신** 원자성 수용 여부 (C-2 잔여 — [[07-query-projection-server]] §6)
- [ ] `ReadFreshnessGate` 대기 상한·타임아웃·폴백 임계값 수치 (읽기 스레드 고갈 방지)
- [ ] read model DB upsert 쓰기 상한 실측 (C-6 잔여 — k6 [[08-k6-load-test-strategy]] Item B)
- [ ] Snapshot 주기 N (M-5 — 측정 후)
- [ ] CDC(Debezium) 졸업 트리거 수치 ([[ADR-027]] exit ramp)
