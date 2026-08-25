# V2 — 작업 현황 · 플랜 (Status & Plan)

> 이 문서가 V2 전환 사이클의 **현행 진행 상태 SSOT**다. (구 `00-roadmap.md`는 `archive/`로 동결)
> 사이클: `20260612-v2-cqrs-es-architecture` (Exploration) · 문서 허브(MOC): [[index]]
> 최종 갱신: 2026-07-28 (§6 구현 계획 상세화)

---

## 1. 한눈에 보기

**설계 수렴 완료 · 구현 미착수.** 라운드1~3 RFC, Design Doc, ADR, 모듈 설계가 모두 확정 단계에 있고, 적대적 검증(devil's advocate) 트리아지 **C-1~C-7 전부 종결**됐다. 마지막까지 열려 있던 C-1(event_store·outbox 원자성)은 [[ADR-027-event-store-outbox-atomicity]]로 닫혔다.

남은 것은 문서 결함이 아니라 **계획된 후속 단계**다 — 이벤트 스토밍 재실시, ADR 승인 전환, Phase 7 구현(별도 사이클).

---

## 2. 산출물 현황

| 영역 | 상태 | 산출물 | 진실원 |
|---|---|---|---|
| 분석 (`analysis/`) | 완료 | 전수 감사 · 취약점 트리아지 · K8s/k6/이벤트전달/스키마 심화 스터디 (10편) | ✅ |
| RFC (`rfc/`) | 완료 | RFC-001~034 (34편, 서사·결정 큐) | ✅ |
| 설계 (`design_doc/`) | 완료 | DESIGN-001~020 (20편) | ✅ |
| ADR (`adr/`) | 완료 (상태 `Proposed`) | ADR-001~027 (26편, ADR-025는 조건부 보류) | ✅ |
| 모듈 (`modules/`) | 완료 | 00~12 (모듈 허브 + 구현 계획) | ✅ |
| 도메인 (`domain/`) | 진행 | 도메인 재설계 문서 (9편) | ✅ |

> `Proposed → Accepted` 전환은 사용자 권한이라, ADR은 완료돼도 상태는 Proposed로 남는다.

---

## 3. 확정 설계 요약

CQRS로 command / query를 모듈 분리하고, **선택적 이벤트 소싱**을 적용한다 — `reservation`·`timetable`·`restaurant`는 ES, `schedule`·`user`·`authenticate`는 상태+Outbox. 이벤트 스토어는 MySQL append-only `event_store` 테이블([[ADR-005-event-store-mysql-table]])이고, **event_store append와 contract outbox insert를 동일 datasource·동일 트랜잭션으로 묶어**(불변식 `I-OUTBOX-1`, [[ADR-027-event-store-outbox-atomicity]]) dual-write를 차단한다. 성장 시 이 결합은 **CDC(Debezium binlog) 졸업 경로**로 해소한다. 동시성은 비관 락(Redisson + DB 폴백 + `(aggregate_id, sequence_no)` UNIQUE 백스톱, [[ADR-016-optimistic-concurrency-control]]), 컨텍스트 간 조율은 코레오그래피 사가([[ADR-008]]), 이벤트 순서는 파티션 키=`aggregate_id` + at-least-once + 멱등([[ADR-009]])이다.

전체 결정 색인은 [[index]] 참조.

---

## 4. 열린 항목 · 다음 단계

| 항목 | 성격 | 상태 |
|---|---|---|
| 이벤트 스토밍 재실시 → 도메인 이벤트 카탈로그 확정 | 선행 작업 | **TBD** (기존 보드는 입문기 산출물, 참고용) |
| ADR `Proposed → Accepted` 전환 | 승인 | 사용자 권한 (미착수) |
| Phase 7 구현 — 모듈 재편 · ES 인프라 · 첫 애그리거트 전환 | 구현 | **별도 사이클** (미착수) |
| `I-OUTBOX-1` 강제 테스트 · 졸업 트리거 수치 | 구현 | Phase 7에서 |
| CDC 졸업 (Debezium binlog tailing) | 미래 | 트리거 관측 시 ([[ADR-027-event-store-outbox-atomicity]]) |

---

## 5. 적대적 검증 트리아지 종결 현황

| 항목 | 주제 | 종결 |
|---|---|---|
| C-1 | event_store·outbox 원자성 | [[ADR-027-event-store-outbox-atomicity]] (2026-07-28) |
| C-2 | 이벤트 순서 계약 | RFC-032 |
| C-3~C-7 | (동시성·스키마·복구 등) | 각 RFC/ADR로 해소 |

전 항목 종결. 상세는 [[06-design-weakness-triage]] 및 [[12-implementation-plan]] §3 참조.

---

## 6. 구현 로드맵 (Phase)

> 태스크 레벨 SSOT는 [[12-implementation-plan]](모듈별 할 일), 마이그레이션 전략은 [[DESIGN-005]] · [[ADR-006]](Strangler). 이 절은 **실행 계획**(단계 목표·산출물·완료 기준·검증 게이트·크리티컬 패스)을 담는다.

### 6.1 전체 Phase 개요

| Phase | 내용 | 상태 |
|---|---|---|
| 1~2 | V1 분석 · 전환 포인트 | 완료 (문서 `archive/`) |
| 3 | 도메인 확장 · 이벤트 스토밍 | 부분 (카탈로그 TBD) |
| 4~5 | Design Doc · ADR | 완료 |
| 6 | 플래닝 | 완료 |
| **7** | **모듈 재편 · ES 인프라 · 애그리거트 전환 · 테스트** | **미착수 (별도 사이클)** |
| 8 | Kafka 심화 | 설계 완료 · 구현 미착수 |
| 9 | 성능 테스트 (k6) | 전략 수립 · 실행 미착수 |
| 10 | 인프라 (k3s/AWS) | 설계 완료 · 구현 미착수 |

### 6.2 Phase 7 구현 원칙 · 순서

- **Strangler Fig** ([[ADR-006]] · [[DESIGN-005]]): V1을 한 번에 갈아엎지 않고 **레퍼런스 컨텍스트 1개씩** 전환하며 패턴을 검증한 뒤 복제한다.
- **레퍼런스 컨텍스트 순서**: `timetable`(최단순 ES) → `reservation`(사가 포함 ES) → `restaurant`(ES 복제) → `schedule`(비-ES 레퍼런스, 상태+Outbox) → `user` → `authenticate` → 나머지(read-only 마이그레이션).
- **수직 슬라이스 우선**: `timetable` 하나를 Command→event_store→Outbox→Kafka→Projection→Query까지 **끝까지 관통**시켜 전 계층 계약을 실증한 다음에야 다음 컨텍스트로 넘어간다. 모듈을 가로로 다 만들고 세로로 붙이지 않는다.
- **불변식 우선(fail-closed)**: 각 단계는 아래 §6.4 검증 게이트를 통과하지 못하면 다음 단계로 진행하지 않는다.

### 6.3 Phase 7 세부 단계 (Definition of Done 포함)

> Day 추정은 [[12-implementation-plan]] §1과 정합. 각 단계는 **산출물**(무엇을 만드는가)과 **완료 기준(DoD)**(무엇을 만족해야 다음으로 가는가)로 못박는다.

#### 7-0 · 사전 정리 & 뼈대 (Day 1-2)
- **산출물**: V1 불필요 코드 정리(클린 베이스라인) · `shared → contract` 이동 대상 타입 목록 · 빈 멀티모듈 Gradle 뼈대(`settings.gradle.kts` 목표값, [[00-module-index]] §3).
- **DoD**: 8개 모듈이 빈 상태로 `./gradlew build` 통과 · 의존성 매트릭스([[00-module-index]] §2)를 위반하는 의존이 하나도 없음(빈 모듈이라 자명하게 성립) · `libs.versions.toml` 공통 스택 버전 확정.
- **근거**: [[01-shared-module]] · [[02-contract-module]]

#### 7-1 · contract-module (Day 3-5) → [[02-contract-module]]
- **산출물**: `AbstractEvent` 봉투 · `timetable`/`reservation` 통합 이벤트 타입 · JSON+`eventType` 판별자 직렬화 전략 · 공유 ID/타입.
- **DoD**: 이벤트 라운드트립(직렬화→역직렬화) 테스트 그린 · `eventType` 레지스트리로 봉투만 보고 구체 타입 해석 가능 · contract는 `shared`만 의존(command-*/query import 0).

#### 7-2 · command-core (Day 6-10) → [[03-command-core]]
- **산출물**: `EventSourcingAggregate` / `StatefulAggregate` 추상 · `TimeTable`·`Reservation` ES 전환(순수 Kotlin `require` 불변식, M-4) · Kotest 상태전이 스펙 · Konsist 경계 규칙([[RFC-031]]).
- **DoD**: core가 JPA·Spring·`contract`를 **전혀** import하지 않음(Konsist Tier2 + Gradle 그래프로 이중 확인) · 애그리거트 `apply(event)` fold가 결정적(동일 이벤트 시퀀스 → 동일 상태) · 상태전이 커버리지 확보.

#### 7-3 · command-application (Day 11-14) → [[04-command-application]]
- **산출물**: `EventStore`/`Outbox`/`StateStore`/`AggregateLock` **아웃바운드 포트** · `EventSerializer`(eventType 레지스트리) · UseCase(비관 락 경로 — [[ADR-016]]) · core→contract 매핑(M-2, application 소유) · ES replay fold(M-3, application 소유) · Kotest `BehaviorSpec`+MockK([[ADR-014]]).
- **DoD**: **core 이벤트 타입을 아는 유일한 계층이 application임**([[DESIGN-019]] 핵심 불변식) — infra/query는 타입-불가지 `StoredEvent`만 · UseCase가 `lock→load→handle→append(+outbox)`를 한 트랜잭션 경계로 조립 · 포트만 의존(어댑터 미구현 상태로 mock 그린).

#### 7-4 · command-adapter + infrastructure + auth-server (Day 15-22) → [[05-command-adapter]] · [[06-command-infrastructure]] · [[09-auth-server-module]]
- **산출물**: Flyway DDL(`event_store`·`outbox`·`snapshot`) · `EventStoreEngine`(append/load bytes, replay, snapshot) · **Outbox relay**(Quartz 클러스터 단일 리더 폴링·삽입 순서 통짜 드레인, [[ADR-009-event-ordering-and-delivery-guarantee]]·[[RFC-025]]) + 재시도 스케줄러 · Kafka producer(파티션 키=`aggregate_id`) · Redisson 락 + DB `FOR UPDATE` 폴백 · UUIDv7 생성기 · Command Controller · 인증 서버(Spring Authorization Server — [[RFC-020]]·[[ADR-024]], 별도 앱 [[ADR-026]]).
- **DoD**: **`I-OUTBOX-1` 강제** — `event_store` append와 `outbox` insert가 동일 datasource·동일 트랜잭션([[ADR-027]]), 한쪽만 커밋되는 케이스가 통합 테스트로 재현 불가 · **`L0` UNIQUE(`aggregate_id`, `sequence_no`) 백스톱** 존재 및 동시 append 충돌 시 `AggregateConflictException` 번역 · relay가 단일 리더로 `sequence_no ASC` 순차 발행(경쟁 소비 아님) · Testcontainers(MySQL+Kafka) 통합 테스트 그린 · infra가 `command-core`를 import하지 않음.

#### 7-5 · query — projection + read model 서버 (Day 23-28) → [[07-query-projection-server]] · [[08-query-read-model-server]]
- **산출물**: Parallel Consumer 설정 · `TimeTableAvailability`/`ReservationList`/`RestaurantSearch` Projector · inbox(멱등) · read model 엔티티/QueryDSL · Query Controller · **E2E**(Command→Event→Projection→Query 관통).
- **DoD**: query가 `command-*`를 **전혀** import하지 않음(CQRS 경계 대칭) · at-least-once 중복 이벤트가 inbox 멱등으로 흡수([[ADR-009]]) · `timetable` 수직 슬라이스가 커맨드부터 조회까지 끝까지 통과 · read-your-writes 게이트(`sequenceNo` 토큰 + `ReadFreshnessGate`, [[RFC-030]]) 동작.
- **마일스톤 ⭐ M1**: 여기까지가 **레퍼런스 패턴 확정점**. 이후 컨텍스트는 이 패턴 복제.

#### 7-6 · 나머지 컨텍스트 전환 (Day 29+) → [[12-implementation-plan]] §1
| 순서 | 컨텍스트 | 쓰기 모델 | 비고 |
|---|---|---|---|
| 3 | `restaurant` | ES | 레퍼런스 패턴 복제 |
| 4 | `schedule` | 상태+Outbox | **비-ES 레퍼런스** — 재정렬은 단일 순차 relay가 공통 봉합([[RFC-032]]), 별도 순서 토큰 없음 |
| 5 | `user` | 상태+Outbox | schedule 패턴 복제 |
| 6 | `authenticate` | 상태+Outbox / auth-server 흡수 | 미결 M-7 |
| 7 | `menu`·`category`·`company` | 현행 | read-only 마이그레이션만 |

### 6.4 검증 게이트 (단계 진입 조건 · 불변식)

각 단계는 아래 게이트를 **회귀 테스트로 상시 강제**한다. 하나라도 깨지면 진행 중단.

| 게이트 | 불변식 | 언제 | 근거 |
|---|---|---|---|
| G1 · 원자성 | `I-OUTBOX-1` — event_store append ⇔ outbox insert 동일 트랜잭션·datasource | 7-4~ | [[ADR-027]] |
| G2 · 동시성 백스톱 | `L0` UNIQUE(`aggregate_id`,`sequence_no`) **절대 제거 금지** | 7-4~ | [[ADR-016]] · [[06-command-infrastructure]] §5.3 |
| G3 · 발행 순서 | relay 단일 리더 순차(`sequence_no ASC`), 파티션 키=`aggregate_id` | 7-4~ | [[RFC-025]] · [[ADR-009]] |
| G4 · 멱등 소비 | at-least-once + inbox 멱등, 중복 이벤트 무해 | 7-5~ | [[ADR-009]] |
| G5 · 모듈 경계 | 의존성 매트릭스 위반 0 (특히 infra/query → core 금지, query → command-* 금지) | 전 단계 | [[00-module-index]] §2 · [[RFC-031]] |
| G6 · 타입 소유 | core 이벤트 타입을 아는 유일 계층 = application | 7-3~ | [[DESIGN-019]] |
| G7 · 결정성 | replay fold 결정적(동일 이벤트열 → 동일 상태) | 7-2~ | [[DESIGN-019]] §7 |

### 6.5 크리티컬 패스 · 마일스톤

```
7-0 뼈대 ─▶ 7-1 contract ─▶ 7-2 core ─▶ 7-3 application ─┐
                                                          ├─▶ 7-4 infra/adapter/auth ─▶ 7-5 query ─▶ ⭐M1 (레퍼런스 수직 슬라이스 완성)
                                                          │        (G1·G2·G3 강제)         (G4 강제)
                                                          └─ 7-4는 7-3 포트 확정 후 adapter와 병렬
7-5 ⭐M1 ─▶ 7-6 restaurant/schedule/user/authenticate 복제 ─▶ ⭐M2 (전 컨텍스트 전환) ─▶ Phase 8~10
```

- **⭐M1 (레퍼런스 완성, ~Day 28)**: `timetable` 한 컨텍스트가 커맨드→조회까지 관통 + G1~G7 그린. **패턴 확정점** — 이후는 저위험 복제.
- **⭐M2 (전 컨텍스트 전환)**: 모든 쓰기 컨텍스트가 V2로, V1 쓰기 경로 제거. Phase 8(Kafka 심화)·9(k6)·10(인프라) 진입.
- **병렬 가능 구간**: 7-4 infra/adapter/auth는 7-3 포트 시그니처 확정 후 상호 병렬. 그 외는 직렬(계약 의존).

### 6.6 리스크 · 구현 시 확정 항목

| ID | 항목 | 상태 | 확정 시점 |
|---|---|---|---|
| M-1 | batch-module 흡수 vs 별도 | 미결 | 7-4 (relay/rebuild 흡수 검토) |
| M-5 | Snapshot 주기 N (50/100/시간) | 미결 | 7-4 이후 측정(k6 Item B) |
| M-6 | Read DB 물리 분리 시점 | 미결 | 7-5 (초기 별 스키마 vs 추후) |
| M-7 | `authenticate` 존속 범위 | 미결 | 7-6 순서6 |
| C-2 잔여 | 비-ES 프로젝션 **부분 갱신** 수용 여부 | 첫 레퍼런스에서 확정 | 7-6 순서4(`schedule`) |
| C-6 잔여 | projector 쓰기 상한 수치 | 실측 필요 | Phase 9 (k6 Item B, 프로젝션 lag 발산 rate) |
| — | CDC(Debezium) 졸업 트리거 수치 | 관측 시 | Phase 9 이후 ([[ADR-027]]) |

> M-8(auth 별도 앱 [[ADR-026]])·M-9(SAS 채택 [[RFC-020]]·[[ADR-024]])·C-1(원자성 [[ADR-027]])·C-3~C-5·C-7은 **확정 종결**. 상세 트리아지는 [[12-implementation-plan]] §3.

---

## 7. 문서 지도

- **진행 상태 · 플랜**: 이 문서 (SSOT)
- **전체 색인 (MOC)**: [[index]]
- **결정**: `adr/` (원자적 결정) · `rfc/` (서사·논의) · `design_doc/` (설계)
- **모듈 설계 · 구현 계획**: `modules/`
- **심화 분석**: `analysis/`
- **`archive/`**: 구 사이클 초기 문서 (`00-roadmap` · `01-v1-architecture-analysis` · `02-event-sourcing-transition-points` · `04-design-doc-module-structure` · `Report` · `Prepare`) — 역사적 참고용, 진실원 아님
