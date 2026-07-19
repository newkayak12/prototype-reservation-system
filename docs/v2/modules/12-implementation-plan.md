# 12 · Implementation Plan (Phase 7 세부 순서 · 미결)

> 허브: [[00-module-index]] | 근거: [[DESIGN-005]] (마이그레이션) · [[00-roadmap]] (Phase 7) · [[ADR-006]] (Strangler)

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

`EventSourcingAggregate`/`StatefulAggregate` · TimeTable·Reservation ES 전환 · Kotest 상태전이 · ArchUnit 컨텍스트 경계.

### 7-3: command-application (Day 11-14) → [[04-command-application]]

EventStore/Outbox/StateStore 포트 · EventSerializer(eventType 레지스트리) · UseCase · core→contract 매핑 · JUnit+MockK.

### 7-4: command-adapter + infrastructure + auth-server (Day 15-22) → [[05-command-adapter]] · [[06-command-infrastructure]] · [[09-auth-server-module]]

Flyway(event_store/outbox) · EventStoreJpaAdapter · Outbox relay(SKIP LOCKED) · Kafka producer · Command Controller · 인증 서버 · Testcontainers.

### 7-5: query — projection + read model 서버 (Day 23-28) → [[07-query-projection-server]] · [[08-query-read-model-server]]

Parallel Consumer 설정 · TimeTableAvailability/ReservationList/RestaurantSearch Projector · inbox · read model 엔티티/QueryDSL · Query Controller · E2E(Command→Event→Projection→Query).

### 7-6: 나머지 컨텍스트 전환 (Day 29+)

| 순서 | 컨텍스트 | 쓰기 모델 | 비고 |
|------|----------|-----------|------|
| 3 | `restaurant` | ES | 레퍼런스 패턴 적용 |
| 4 | `schedule` | 상태+Outbox | 비-ES 레퍼런스 |
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
| M-8 | auth-server 배포 단위 | (a) 별도 앱 (b) 같은 프로세스, 모듈만 분리 | [[09-auth-server-module]] |
| M-9 | SAS vs jjwt 직접 발급 | 카탈로그 의존성 추가 여부 | [[09-auth-server-module]] |

## 3. 설계 반박 → 구현 시 확정 항목 (Devil's Advocate 트리아지)

| # | 항목 | 귀속 |
|---|------|------|
| C-1 | event_store + outbox **동일 트랜잭션·datasource** 명문화 | [[06-command-infrastructure]] · [[DESIGN-003]] |
| C-2 | 다중 소스 프로젝션 원자성·순서 | [[07-query-projection-server]] §6 |
| C-3 | Zero Payload 재처리 time-travel 오염 → ES=event-carried 분기 | [[02-contract-module]] · [[RFC-029]] |
| C-4 | DLQ 재생·relay 병렬성 순서 보존 | [[RFC-025]] |
| C-5 | read-your-writes(예약 확정 직후) 정책 | [[RFC-030]] · 신규 ADR |
| C-6 | projector 쓰기 병목 스케일(HA 레플리카는 읽기만) | [[DESIGN-004]] · [[DESIGN-010]] |
| C-7 | 상시 Redis 락 vs 락프리 낙관 append — 도메인별 혼용 | [[04-command-application]] · [[DESIGN-003]] |

## 4. 관련 문서

- 마이그레이션: [[DESIGN-005]] · Strangler: [[ADR-006]] · 로드맵: [[00-roadmap]]
- 모듈 허브: [[00-module-index]]
