# 06 · command-infrastructure (횡단 기술 배관) [신규]

> 허브: [[00-module-index]] | 근거: [[DESIGN-002]] §4.2 · [[DESIGN-008]] (메시징) · [[DESIGN-009]] (ES 수명주기) · [[DESIGN-019]] · [[DESIGN-003]] 동시성

## 1. 책임

도메인을 모르는 **횡단 기술 배관**. event_store 경로에서 타입-불가지 `StoredEvent`만 다룬다(core 이벤트 타입 절대 미소유 — [[DESIGN-019]] §3).

- ES 엔진: append, replay 지원(bytes I/O), snapshot 저장/로드
- **Outbox relay** (폴링 publisher, `SKIP LOCKED` 단일성)
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
    │   ├── OutboxRelay.kt             # 폴링 publisher (SELECT ... FOR UPDATE SKIP LOCKED)
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

### 5.1 Outbox relay — 단일성 + 폴링 ([[DESIGN-008]] §4.9)

```kotlin
// 여러 relay 인스턴스가 서로 잠기지 않은 행만 경쟁 소비 → 중복 발행 최소화
// leader election 불필요 (별도 코디네이터 없음이 SKIP LOCKED의 결정적 이점)
@Scheduled(fixedDelayString = "\${outbox.poll-interval}")
fun relay() {
    val batch = outboxRepo.pollUnpublishedForUpdateSkipLocked(limit)
    batch.forEach { row ->
        kafkaTemplate.send(topicOf(row), row.aggregateId /* 파티션 키 */, row.payload)
            .whenComplete { _, ex -> if (ex == null) row.succeeded() else row.failed() }
    }
}
```

- 발행 방식: **폴링으로 시작**. CDC(Debezium)는 명시적 트리거 충족 시 전환([[DESIGN-008]] §4.9).
- **미결(반박)**: `SKIP LOCKED`는 aggregate별 발행 순서를 직렬화하지 않는다([[DESIGN-008]] 자기리뷰) — relay 병렬성↔순서 계약은 [[RFC-025]]에서 확정.

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
- [ ] Outbox relay (폴링 + `SKIP LOCKED`) + 재시도 스케줄러
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

**Position**: 인프라는 도메인을 모르는 배관으로 두고, 순서·원자성·단일성을 전부 DB 원시연산(`SKIP LOCKED`·`UNIQUE`·동일 datasource)과 Redisson 상시 락에 위임한다.
**Steel-man**: 별도 코디네이터·2PC·전용 ES 제품 없이 이미 붙어 있는 DB/Redis만으로 원자성과 relay 단일성을 싸게 얻고, event_store를 계약 버저닝의 인질에서 분리한다.

### 숨은 가정

1. **파티션 키만으로 aggregate별 발행 순서가 지켜진다** — relay가 병렬로 떠도 같은 aggregate의 `sequence_no` 순서가 Kafka 도착 순서로 보존된다고 암묵 전제(§5.1↔§5.2).
2. **event_store와 outbox는 영원히 동일 datasource에 함께 산다** — 성장·스케일아웃 이후에도 둘을 분리하지 않는다(§7 반박).
3. **모든 ES 쓰기에 Redis 왕복을 태워도 된다** — 락프리 낙관 append는 "나중 결정"으로 미뤄도 크리티컬 패스 비용이 수용 가능하다(§5.3).

### 반론

1. `[분산시스템]` · **심각도: 높음** · 선례: [[DESIGN-008]] 자기리뷰 §264에 자인 — **`SKIP LOCKED`는 aggregate별 발행 순서를 직렬화하지 않는다.** §5.1의 `batch.forEach`는 한 인스턴스 안에서만 순차 send다. 여러 relay 인스턴스가 경쟁 소비하면 같은 `aggregate_id`의 seq 5와 seq 6을 서로 다른 인스턴스가 서로 다른 시점에 `kafkaTemplate.send` 할 수 있고, Kafka 파티션은 producer 도착 순서만 보존하므로 순서가 뒤집힌다. §5.2가 "파티션 키 = aggregate_id → 애그리거트별 순서 보장"을 파는 것과 §5.1이 "leader election 불필요한 경쟁 소비"를 결정적 이점으로 파는 것은 **동시에 참일 수 없다**. 이 모순이 문서 안에 연결돼 있지 않고 [[RFC-025]] 미확정으로 방치된 채 구현 항목(§6)에 들어가 있다.

2. `[아키텍처/일관성]` · **심각도: 중상** · 선례: [[DESIGN-003]] 자기리뷰 §196에 채택 — **동일 datasource 전제는 확장의 one-way door다.** 2PC 회피를 위해 event_store append + outbox insert를 동일 트랜잭션·동일 커넥션에 묶는 순간, event_store는 outbox와 같은 MySQL 인스턴스에서 절대 떨어질 수 없다. §5.4는 파티셔닝을 YAGNI로 미루지만, append-only event_store가 성장해 outbox 폴링(핫 경로)과 I/O를 다투기 시작하면 "저장소 분리"라는 정상 해법이 원자성 상실 없이는 불가능하다. 전제를 "명문화 필요"로만 남긴 것은, 뒤집기 비싼 결정을 미결 상태로 구현에 태우는 것이다.

3. `[성능/미확정]` · **심각도: 중간** · 선례: [[DESIGN-003]] 자기리뷰 §188-190에 자인(k6 실측 후 확정 권장) — **Redisson 상시 락 vs 락프리 낙관 append 혼용이 미확정(C-7)인데 §5.3은 상시 락을 정상 경로로 확정해 버렸다.** §5.3 스스로 "L0 `UNIQUE`가 정확성 백스톱"이라 명시한다. 정확성이 UNIQUE로 이미 보장된다면, 예약 취소처럼 aggregate당 동시 쓰기가 드문 흐름은 락 없이 낙관 append + 충돌 시 리플레이-재시도가 상시 Redis 왕복보다 쌀 수 있다. 상시 락과 락프리의 비용을 정량 비교하지 않고 모든 ES 쓰기를 Redis 왕복에 태우는 선택은, Redis를 크리티컬 패스의 상시 HotSpot으로 굳힌다.

### 핵심 취약점

relay 병렬성 ↔ 파티션 순서 계약의 정면 모순(반론 1). 이 문서는 §5.1에서 병렬 경쟁 소비를, §5.2에서 aggregate별 순서 보장을 동시에 팔지만 [[RFC-025]] 미확정이다. 이대로 구현하면 프로젝터가 순서 역전된 이벤트를 받아 read model이 조용히 오염되고, 그 원인이 인프라 배관에 숨어 도메인 코드에서 재현·추적이 어렵다.

### 가역성

대부분 reversible(`SKIP LOCKED`·폴링·Redisson은 설정/CDC 전환으로 되돌리기 가능) — **단 event_store+outbox 동일 datasource 전제는 one-way door**로, 트랜잭션 경계가 굳은 뒤 저장소 분리 재설계 비용이 크다.
