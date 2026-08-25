# 12 · Implementation Plan (Phase 7 세부 순서 · 미결)

> 허브: [[00-module-index]] | 근거: [[DESIGN-005]] (마이그레이션) · [[00-status-and-plan]] (Phase 7) · [[ADR-006]] (Strangler)

원칙: **Strangler Fig** — 한 번에 하나의 레퍼런스 컨텍스트를 전환하며 패턴 검증.
레퍼런스 컨텍스트: `timetable`(가장 단순한 ES 대상) → `reservation`(사가 포함).

## 1. Phase 7 세부 순서

### 7-0: 사전 정리 (Day 1-2)

| 작업 | 산출물 | 문서 |
|------|--------|------|
| V1 불필요 코드 정리 | 클린 베이스라인 | — |
| shared→contract 이동 타입 식별 | 이동 대상 목록 | [[01-shared-module]] |
| Gradle 멀티모듈 뼈대 생성(빈 모듈) | settings.gradle.kts + build.gradle.kts | [[00-module-index]] §3 |

### 7-1: contract-module (Day 3-5) → [[02-contract-module]]

`AbstractEvent` 봉투 · timetable/reservation 통합 이벤트 · 직렬화(JSON+eventType) 전략.

### 7-2: command-core (Day 6-10) → [[03-command-core]]

`EventSourcingAggregate`/`StatefulAggregate` · TimeTable·Reservation ES 전환 · Kotest 상태전이 · Konsist 컨텍스트 경계([[RFC-031]] — ArchUnit 아님).

### 7-3: command-application (Day 11-14) → [[04-command-application]]

EventStore/Outbox/StateStore/AggregateLock 포트 · EventSerializer(eventType 레지스트리) · UseCase(비관 락 — [[ADR-016]]) · core→contract 매핑 · Kotest `BehaviorSpec`+MockK([[ADR-014]]).

### 7-4: command-adapter + infrastructure + auth-server (Day 15-22) → [[05-command-adapter]] · [[06-command-infrastructure]] · [[09-auth-server-module]]

Flyway(event_store/outbox) · EventStoreJpaAdapter(예외 번역: `AggregateConflictException`) · Outbox relay(Quartz 클러스터 단일 리더 — [[ADR-009-event-ordering-and-delivery-guarantee]]·[[RFC-025]]) · Kafka producer · Command Controller · 인증 서버(Spring Authorization Server — [[RFC-020]]·[[ADR-024]]) · Testcontainers.

### 7-5: query — projection + read model 서버 (Day 23-28) → [[07-query-projection-server]] · [[08-query-read-model-server]]

Parallel Consumer 설정 · TimeTableAvailability/ReservationList/RestaurantSearch Projector · inbox · read model 엔티티/QueryDSL · Query Controller · E2E(Command→Event→Projection→Query).

### 7-6: 나머지 컨텍스트 전환 (Day 29+)

| 순서 | 컨텍스트 | 쓰기 모델 | 비고 |
|------|----------|-----------|------|
| 3 | `restaurant` | ES | 레퍼런스 패턴 적용 |
| 4 | `schedule` | 상태+Outbox | 비-ES 레퍼런스 — 재정렬은 [[RFC-032]](합의)로 봉합: 단일 순차 relay가 ES/비-ES 공통 처리, 별도 순서 토큰 없음 |
| 5 | `user` | 상태+Outbox | schedule 패턴 복제 |
| 6 | `authenticate` | 상태+Outbox / auth-server 흡수 | M-7 |
| 7 | `menu`·`category`·`company` | 현행 | read-only 마이그레이션만 |

## 2. 모듈 구성 미결 사항

| # | 항목 | 선택지 | 관련 |
|---|------|--------|------|
| M-1 | batch-module 처리 | (a) command-infrastructure 흡수 (b) 별도 유지 | [[06-command-infrastructure]] |
| M-2 | core→contract 매핑 위치 | **(a) application 채택** ([[DESIGN-019]]) | [[04-command-application]] |
| M-3 | ES replay 오케스트레이션 | **application이 fold** ([[DESIGN-019]]) | [[04-command-application]] |
| M-4 | core에서 jakarta.validation 허용 | **(a) 순수 Kotlin require** 기본 | [[03-command-core]] |
| M-5 | Snapshot 주기 N | 50? 100? 시간 기반? — 측정 후 | [[06-command-infrastructure]] |
| M-6 | Read DB 물리 분리 시점 | (a) 초기부터 별 스키마 (b) 추후 분리 | [[08-query-read-model-server]] |
| M-7 | `authenticate` 컨텍스트 존속 범위 | (a) auth 흡수 (b) user 병합 (c) 축소 유지 | [[09-auth-server-module]] |
| M-8 | ~~auth-server 배포 단위~~ | **확정: (a) 별도 앱** — [[ADR-026]] 결정1·2 (auth server = 별도 Deployment, 워크로드 #2) | [[09-auth-server-module]] |
| M-9 | ~~SAS vs jjwt 직접 발급~~ | **확정: SAS 채택** — [[RFC-020]](종결 2026-06-30)·[[ADR-024]] 결정 6 | [[09-auth-server-module]] |

## 3. 설계 반박 → 구현 시 확정 항목 (Devil's Advocate 트리아지)

**2026-07-22 갱신**: C-3·C-4·C-5·C-7은 RFC 합의·모듈 반영으로 **해소**. **C-6은 배치가 [[ADR-026]]로 확정**되어 실측(k6 [[08-k6-load-test-strategy]] Item B)으로 쓰기 상한 수치만 남았다. C-2 순서 갈래는 [[RFC-032]](합의 2026-07-22)로 **닫힘** — 단일 순차 relay가 ES/비-ES 재정렬을 공통 봉합, 별도 순서 토큰 불요([[DESIGN-020]] 반영). 원자성(부분 갱신 수용 여부)만 미결.

**2026-07-28 갱신**: **C-1 닫힘** — [[ADR-027]]로 확정. A안(동일 datasource 트랜잭셔널 아웃박스)을 명명 불변식(I-OUTBOX-1)으로 못박고, one-way door 우려는 **CDC(Debezium binlog 테일링) 졸업 경로**를 공식 exit ramp로 지정해 해소. 결정은 A now / CDC later. 남는 건 졸업 트리거 수치(k6 Item B)뿐. 이로써 트리아지 C-1~C-7 전부 종결.

| # | 항목 | 귀속 | 상태 |
|---|------|------|------|
| C-1 | ~~event_store + outbox **동일 트랜잭션·datasource** 명문화~~ | [[06-command-infrastructure]] · [[DESIGN-003]] · [[ADR-027]] | **해소** — [[ADR-027]] 확정: A(동일 datasource 트랜잭셔널 아웃박스, 불변식 I-OUTBOX-1) + CDC 졸업 경로. one-way door → 탈출 가능한 문으로 전환 |
| C-2 | 다중 소스 프로젝션 원자성·순서 | [[07-query-projection-server]] §6 · [[09-event-delivery-and-offsets]] §5 · [[12-non-es-outbox-ordering]] · [[RFC-032]] · [[DESIGN-020]] | 순서 갈래 **해소** — [[RFC-032]](합의) 단일 순차 relay가 봉합, 별도 토큰 불요. 원자성=부분 갱신을 정상 동작으로 받아들일지만 첫 레퍼런스에서 확정 필요 |
| C-3 | ~~Zero Payload 재처리 time-travel 오염~~ | [[02-contract-module]] · [[RFC-029]] | **해소** — event-carried 일원화 확정(합의 2026-07-05), §5.2 갱신 완료 |
| C-4 | ~~DLQ 재생·relay 병렬성 순서 보존~~ | [[ADR-009-event-ordering-and-delivery-guarantee]] · [[RFC-025]] | **해소** — Quartz 클러스터 단일 relay + offset 순서 apply·`event_id` dedup + DLQ=알림/재구축(2026-08-03 개정: LWW 폐기), 06·07 갱신 완료 |
| C-5 | ~~read-your-writes(예약 확정 직후) 정책~~ | [[RFC-030]] · 신규 ADR | **해소** — `sequenceNo` 토큰 + `ReadFreshnessGate`, 08 갱신 완료 (대기 상한 수치는 구현 시 결정) |
| C-6 | projector 쓰기 병목 스케일(HA 레플리카는 읽기만) | [[DESIGN-004]] · [[DESIGN-010]] · [[ADR-026]] · [[08-k6-load-test-strategy]] | 배치 확정 — projector = 독립 워크로드 + 노드 격리([[ADR-026]] 결정2·3). 쓰기 상한 수치만 미결 — k6 Item B(프로젝션 lag 발산 rate)로 측정, 레플리카로 못 가려짐 |
| C-7 | ~~상시 Redis 락 vs 락프리 낙관 append~~ | [[04-command-application]] · [[DESIGN-003]] | **해소** — 비관 락(Redisson L1+DB 폴백 L1')+UNIQUE 백스톱 확정([[RFC-014]]·[[ADR-016]]), 04 갱신 완료 |

## 4. 관련 문서

- 마이그레이션: [[DESIGN-005]] · Strangler: [[ADR-006]] · 로드맵: [[00-status-and-plan]]
- 런타임 배치: [[ADR-026]] · [[11-runtime-topology]] · [[DESIGN-010]] · 부하 측정: [[08-k6-load-test-strategy]]
- 이벤트 전달/순서: [[09-event-delivery-and-offsets]] · [[12-non-es-outbox-ordering]] · [[RFC-032]] · 데이터 사전: [[00-data-index]]
- 모듈 허브: [[00-module-index]]
