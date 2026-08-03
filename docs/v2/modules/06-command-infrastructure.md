# 06 · command-infrastructure (횡단 기술 배관) [신규]

> 허브: [[00-module-index]] | 근거: [[DESIGN-002]] §4.2 · [[DESIGN-008]] (메시징) · [[DESIGN-009]] (ES 수명주기) · [[DESIGN-019]] · [[DESIGN-003]] 동시성

## 1. 책임

도메인을 모르는 **횡단 기술 배관**. event_store 경로에서 타입-불가지 `StoredEvent`만 다룬다(core 이벤트 타입 절대 미소유 — [[DESIGN-019]] §3).

- ES 엔진: append, replay 지원(bytes I/O), snapshot 저장/로드
- **Outbox relay** (폴링 publisher, 단일 순차 실행 — Quartz 클러스터 리더, [[ADR-009-event-ordering-and-delivery-guarantee]] · [[RFC-025]] 결정 1)
- **Kafka producer** 설정
- JPA/DB 설정, DataSource
- ID 생성기 (UUIDv7)
- 분산 락 (Redisson)

## 2. 의존성

| 항목 | 값 |
|------|-----|
| **허용 의존** | `contract-module`(발행 경로 한정), `shared-module` |
| **금지** | **`command-core`** (핵심!), `command-application`, `query` |
| **구현 시점** | **Phase 7-4** (adapter와 병렬) |

> **왜 core 금지인가**: 인프라가 도메인을 모르게 해 event_store를 계약 버저닝의 인질에서 분리한다. 리플레이의 도메인 조립(`apply` fold)은 application이 하고, infra는 `StoredEvent` bytes만 올린다([[DESIGN-019]] §7).

## 3. 사용 라이브러리

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| `spring-boot-starter-data-jpa` | `3.4.5` | event_store/outbox/snapshot JPA, DataSource |
| `mysql-connector-j` | `8.0.33` | command MySQL 드라이버 |
| `spring-kafka` | `3.3.1` | **Kafka producer** (Outbox relay → Kafka 발행) |
| `redisson-spring-boot-starter` | `3.52.0` | 분산 락 L1(`aggregate_id` 단위 — [[DESIGN-003]] §4.1) |
| `spring-boot-starter-quartz` | (Boot 관리) | **단일 순차 relay 리더 선출** — Quartz 클러스터 모드(`isClustered=true` + `@DisallowConcurrentExecution`)로 outbox 폴링을 한 노드만 실행([[ADR-009-event-ordering-and-delivery-guarantee]] · [[RFC-025]] 결정 1, SKIP LOCKED 경쟁 소비 supersede). [[ADR-008]] 예약 타임아웃 스케줄러와 공유 인프라 |
| `flyway-core` + `flyway-mysql` | `10.0.0` | event_store·outbox·snapshot DDL 마이그레이션 |
| `spring-retry` | (Boot BOM) | relay 발행 재시도 |
| `spring-tx` | `6.2.1` | Outbox AFTER_COMMIT / REQUIRES_NEW |
| `com.fasterxml.uuid:java-uuid-generator` | `4.3.0` | **UUIDv7** `event_id` 생성(정체성·keyset 커서) |
| `p6spy-spring-boot-starter` | `1.10.0` | SQL 로깅(개발/디버깅) |
| (테스트) `testcontainers-mysql`·`-kafka`·`-junit` | `2.0.3` | relay·producer 통합 테스트 |
| (테스트) `spring-kafka-test` | `3.3.1` | 임베디드 Kafka |

> **주의**: consumer(parallel-consumer)는 **여기 없다** — 이벤트 소비/투영은 query 측 projection 서버([[07-query-projection-server]]). infra는 발행(producer)만.

## 4. 구조

```
command-module/command-infrastructure
└── com.reservation.command.infrastructure
    ├── eventstore/
    │   ├── EventStoreEngine.kt        # append/load(StoredEvent), replay 지원
    │   └── snapshot/                  # N 이벤트마다 스냅샷 (DESIGN-009)
    ├── outbox/
    │   ├── OutboxRelay.kt             # 폴링 publisher (Quartz job, @DisallowConcurrentExecution — 클러스터 단일 리더, ADR-009/RFC-025)
    │   └── OutboxScheduler.kt         # 미발행분 재시도
    ├── kafka/
    │   └── KafkaProducerConfig.kt     # 파티션 키 = aggregate_id (DESIGN-008 §4.3)
    ├── lock/
    │   └── RedissonLockConfig.kt      # L1 분산 락 (+ DB FOR UPDATE 폴백 L1')
    ├── idgen/
    │   └── UuidV7Generator.kt
    └── persistence/
        ├── CommandDataSourceConfig.kt
        └── FlywayConfig.kt
```

## 5. 핵심 설계

### 5.1 Outbox relay — 단일 순차 리더 + 폴링 ([[ADR-009-event-ordering-and-delivery-guarantee]] · [[RFC-025]] 결정 1, SKIP LOCKED 경쟁 소비 supersede)

```kotlin
// 단일 순차 relay — Quartz 클러스터 모드가 트리거를 한 노드에서만 발화시키고,
// @DisallowConcurrentExecution이 클러스터 전역 동시 실행을 막는다.
// 나머지 노드는 대기하므로 aggregate별 발행 순서(삽입 순서 id ASC)가 직렬화된다.
@DisallowConcurrentExecution
class OutboxRelayJob(...) : Job {
    override fun execute(ctx: JobExecutionContext) {
        // 삽입 순서 통짜 드레인 (I-RELAY-ORDER) — 경쟁 드레인 금지.
        // 전역 정렬 키는 PK id다. sequence_no는 애그리거트별 순번이라 혼합 outbox의 전역 키가 못 된다.
        val batch = outboxRepo.pollUnpublishedOrderById(limit)   // id ASC (삽입 순서)
        batch.forEach { row ->
            kafkaTemplate.send(topicOf(row), row.aggregateId /* 파티션 키 */, row.payload)
                .whenComplete { _, ex -> if (ex == null) row.succeeded() else row.failed() }
        }
    }
}
```

- 발행 방식: **폴링으로 시작**. CDC(Debezium)는 명시적 트리거 충족 시 전환([[DESIGN-008]] §4.9).
- **확정**([[ADR-009-event-ordering-and-delivery-guarantee]] 2026-08-03 · [[RFC-025]] 결정 1): relay는 **단일 순차(Quartz 클러스터 리더)** — `SKIP LOCKED` 경쟁 소비는 aggregate별 발행 순서를 직렬화하지 않아 supersede됐다. ShedLock 대신 Quartz를 쓰는 이유는 [[ADR-008]] 예약 타임아웃 스케줄러로 어차피 도입하는 인프라라서다. 처리량이 실제 병목이 되면 파티션드 relay·CDC로 졸업(RFC-025 논점 1), 그때 producer 펜싱 검토.

### 5.2 Kafka 토픽·파티션 ([[DESIGN-008]])

- 토픽: `<context>.<aggregate-type>` (예: `reservation.reservation`)
- 파티션 키: `aggregate_id` → 애그리거트별 순서 보장(파티션 내부)
- 파티션 수는 **순서 계약의 일부** — 고정 지향, 증설은 신규 토픽 마이그레이션

### 5.3 동시성 ([[DESIGN-003]] §4.1)

`lock(aggregate_id)`(Redisson L1, Redis 불가 시 DB `FOR UPDATE` L1') → load→handle→append. **L0 `(aggregate_id, sequence_no)` UNIQUE는 최종 safety 백스톱, 절대 제거 금지.**

### 5.4 event_store 저장 형태 ([[DESIGN-003]] §4.1 · [[DESIGN-009]])

`event_store(event_id BINARY(16) UUIDv7 PK, aggregate_type, aggregate_id, sequence_no, event_type, event_version, payload JSON, occurred_at, correlation_id, causation_id, traceparent, UNIQUE(aggregate_id, sequence_no))`. 스냅샷은 핫 DB 최신 1개 + 직전본 S3 이관. 파티셔닝은 생성월 기준(성장 시 도입 — YAGNI).

## 6. 할 일

- [ ] Flyway: event_store / outbox / snapshot 테이블 DDL
- [ ] `EventStoreEngine` (append/load bytes, replay 지원, snapshot)
- [ ] Outbox relay (폴링 + Quartz 클러스터 단일 리더, `@DisallowConcurrentExecution`, 삽입 순서 id ASC 드레인) + 재시도 스케줄러
- [ ] Kafka producer 설정 (파티션 키 = aggregate_id)
- [ ] Redisson 락 설정 + DB FOR UPDATE 폴백
- [ ] UUIDv7 ID 생성기
- [ ] V1 infrastructure-module 이전
- [ ] 통합 테스트 (Testcontainers MySQL + Kafka)

## 7. 미결

- **M-1**: batch-module 흡수 여부 (relay·rebuild로 흡수 검토).
- **M-5**: Snapshot 주기 N (50? 100? 시간 기반?) — 측정 후.
- **반박**: event_store + outbox **동일 트랜잭션·동일 datasource** 전제 명문화 필요([[DESIGN-003]] 자기리뷰 채택). 2PC 회피의 근거.

## 8. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: 인프라는 도메인을 모르는 배관으로 두고, 순서(단일 순차 relay=**Quartz 클러스터**)·원자성(`UNIQUE`·동일 datasource)·단일성을 기존 인프라에 위임하고, aggregate 락은 Redisson 상시 락(+DB 폴백)에 둔다.
**Steel-man**: 별도 코디네이터·2PC·전용 ES 제품 없이 이미 붙어 있는 DB/Redis만으로 원자성과 relay 단일성을 싸게 얻고, event_store를 계약 버저닝의 인질에서 분리한다.

### 숨은 가정

1. ~~파티션 키만으로 aggregate별 발행 순서가 지켜진다~~ — **해소됨.** relay가 Quartz 클러스터 단일 리더로 삽입 순서 통짜 드레인하므로(§5.1, I-RELAY-ORDER), 이제 파티션 키만이 아니라 relay 자체의 직렬화가 순서를 보장한다.
2. **event_store와 outbox는 영원히 동일 datasource에 함께 산다** — 성장·스케일아웃 이후에도 둘을 분리하지 않는다(§7 반박).
3. **모든 ES 쓰기에 Redis 왕복을 태워도 된다** — 락프리 낙관 append는 "나중 결정"으로 미뤄도 크리티컬 패스 비용이 수용 가능하다(§5.3).

### 반론

1. `[분산시스템]` · **심각도: 높음 — 해소됨(2026-07-19 동기화)** · 선례: [[DESIGN-008]] 자기리뷰 §264에 자인 — 이 반론은 §5.1이 여전히 `SKIP LOCKED` 경쟁 소비를 규범으로 적고 있을 때 성립했다. [[RFC-025]](🏷 합의 2026-07-04) 결정 1 · [[ADR-009-event-ordering-and-delivery-guarantee]](2026-08-03)에 맞춰 §5.1을 **Quartz 클러스터 단일 순차 리더**로 갱신했으므로, "경쟁 소비"와 "파티션별 순서 보장"이 동시에 참일 수 없던 모순은 더 이상 존재하지 않는다. 남은 리스크는 처리량이 실제로 병목이 될 때 파티션드 relay/CDC로 졸업하는 전환 트리거를 아직 정하지 않았다는 점뿐이다(RFC-025 논점 1, 트리아지 C47).

2. `[아키텍처/일관성]` · **심각도: 중상** · 선례: [[DESIGN-003]] 자기리뷰 §196에 채택 — **동일 datasource 전제는 확장의 one-way door다.** 2PC 회피를 위해 event_store append + outbox insert를 동일 트랜잭션·동일 커넥션에 묶는 순간, event_store는 outbox와 같은 MySQL 인스턴스에서 절대 떨어질 수 없다. §5.4는 파티셔닝을 YAGNI로 미루지만, append-only event_store가 성장해 outbox 폴링(핫 경로)과 I/O를 다투기 시작하면 "저장소 분리"라는 정상 해법이 원자성 상실 없이는 불가능하다. 전제를 "명문화 필요"로만 남긴 것은, 뒤집기 비싼 결정을 미결 상태로 구현에 태우는 것이다.

3. `[성능/미확정]` · **심각도: 중간 — 방향은 해소됨(ADR-016), 정량 비교는 여전히 미측정** · 선례: [[DESIGN-003]] 자기리뷰 §188-190 — [[RFC-014-aggregate-concurrency-control]](🏷 합의 2026-06-29)·[[ADR-016]]이 "상시 Redisson 비관 락 + DB 폴백, UNIQUE는 safety 백스톱"을 이미 확정해, §5.3의 상시 락 경로는 더 이상 미확정 방향이 아니다(핫 스트림 retry storm/라이브락 회피가 근거 — ADR-016 결정 동인). 다만 ADR-016이 채택 근거로 든 비용 비교(낙관 재시도 부담 vs 상시 Redis 왕복)는 여전히 k6 실측 전이라, "상시 락이 실제로 더 싸다"는 정량 검증은 남은 과제다.

### 핵심 취약점

**event_store+outbox 동일 datasource 전제가 명문화 없이 구현에 태워지는 것(반론 2)**이 남은 핵심 취약점이다. relay 순서(반론 1)는 RFC-025 동기화로, 락 방향(반론 3)은 ADR-016으로 각각 해소됐지만, 저장소 분리 불가 제약은 여전히 "명문화 필요"로만 §7에 남아 있고 이는 뒤집기 비싼 one-way door다.

### 가역성

대부분 reversible(`SKIP LOCKED`·폴링·Redisson은 설정/CDC 전환으로 되돌리기 가능) — **단 event_store+outbox 동일 datasource 전제는 one-way door**로, 트랜잭션 경계가 굳은 뒤 저장소 분리 재설계 비용이 크다.
