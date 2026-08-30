# ADR-013: 읽기/쓰기 DB 토폴로지 — command/query 물리 분리 + Kafka 다리, binlog=HA

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-007-deployment-infra-ops]] · **설계**: [[DESIGN-010-deployment-runtime]]
- **연관 ADR**: [[ADR-005-event-store-mysql-table]] · [[ADR-004-read-model-projection-and-replica]] · [[ADR-009-event-ordering-and-delivery-guarantee]] · [[ADR-012-kafka-hosting-msk-vs-self-managed]] · [[ADR-019-caching-redis-role]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 단일 MySQL 인스턴스가 읽기·쓰기를 모두 받는다. 읽기 부하와 쓰기 정합성이 같은 인스턴스에서 경합하고, 한쪽 장애가 다른 쪽을 끌고 내려간다. V2는 command/query를 모듈로 이미 분리했지만([[ADR-001-cqrs-command-query-module-split]]), 그 논리 분리가 물리 DB 토폴로지까지 자동으로 결정하지 않는다.

시나리오: command가 `ReserveTable` 커맨드를 받아 event_store·상태·Outbox를 한 트랜잭션으로 커밋한다([[ADR-005-event-store-mysql-table]]). query는 손님의 조회 요청에 read model을 돌려줘야 하는데, 이 read model이 command와 같은 인스턴스에 있으면 "query가 command 테이블을 못 본다"는 CQRS 경계가 디시플린(코드 규율)에만 의존하게 된다. 읽기 폭주가 실제로 나면 그 규율은 인스턴스 경합 앞에서 무너진다.

**command/query DB를 물리적으로 어떻게 나누고, 무엇으로 잇고, 각각의 HA·읽기 확장·환경별 규격을 어떤 형태로 고정할 것인가.**

## 결정 동인 (Decision Drivers)

- CQRS 물리 성립 — query가 command 스키마를 볼 수 없다는 경계가 코드 규율이 아니라 물리로 서야 한다.
- 호스팅 형태 투명 원칙 — RDS vs 자가 관리 MySQL은 앱에 영향을 주지 않아야 한다([[ADR-012-kafka-hosting-msk-vs-self-managed]]과 같은 논리).
- 무트래픽 학습 규모에 맞는 최소 machinery — 실트래픽이 없어 인스턴스 분할을 유발할 부하 자체가 없다.
- V1의 검증된 자산을 계승한다 — Redis Sentinel.
- 방향(정성)은 지금 잠그고 절대 수치는 운영 실측 후 확정한다.

## 검토한 선택지 (Considered Options)

**물리 토폴로지**
- **T-1. 단일 인스턴스(논리 분리만)** — 읽기 부하가 쓰기를 압박, 물리 분리 미달.
- **T-2. 단일 primary + read replica(query가 primary의 binlog 미러를 읽음)** — replica가 command 테이블까지 미러해 query가 물리적으로 command 스키마를 보게 된다.
- **T-3. command DB / query DB 완전 분리, 다리는 Kafka/projector** — 각 DB는 binlog로 자기 자신만 이중화(HA).

**Redis 토폴로지**
- **v1 Sentinel 계승** — 현 워크로드(세션·캐시)에 샤딩을 요구하는 신호가 없다.
- **Cluster 전환** — 단일 마스터 압박이 실측되기 전에는 근거가 없다.

**DB HA 형태**
- **standby 0대** — HA가 아니다.
- **standby 1대 + 복제 지연 허용치 + 임계 초과 알람(정책 형태)** — 사이드 프로젝트 규모에 맞는 최소선.
- **standby 다수** — 규모 대비 과하다.

## 결정 (Decision Outcome)

**채택: T-3(command/query DB 완전 분리, 다리=Kafka/projector, binlog=각 DB의 HA 전용) + Redis v1 Sentinel 계승 + standby 1대 정책.** T-2는 binlog를 모델 간 다리로 써서 query가 쓰기 스키마에 물리 결합되므로 CQRS 경계를 스스로 허문다. T-1은 물리 분리 목표에 미달한다.

| 항목 | 결정 | 비고 |
|---|---|---|
| 인스턴스 구성 | **command 1 인스턴스 + query 1 인스턴스**, 각각 HA 레플리카(binlog) | 도메인별 인스턴스 분할은 off |
| 다리 | **Kafka/projector**(이벤트) | binlog는 다리가 아니라 각 DB의 이중화 전용 |
| 읽기 확장 | **HA 레플리카가 흡수** | 인스턴스 분할이 아니라 레플리카 분산으로 읽는다. 도메인별 스키마 조직은 [[RFC-002-read-model-consistency]] 소유 |
| Redis | **v1 Sentinel 계승** | Cluster 전환은 단일 마스터 압박 시로 보류. Redis 역할 자체는 [[ADR-019-caching-redis-role]] 소유 |
| DB HA | **standby 1대 + 복제 지연 허용치 + 임계 초과 알람**(정책 형태) | 허용 지연 절대값·자동 페일오버 도입 여부는 측정 후 |
| 환경별 규격 | **프로덕션 = 분리 불변식 유지 · 로컬 = 단일 인스턴스 · 스테이지 = 동형 + 스펙 축소** | 축소 매트릭스 상세는 구현/운영 사이클 |

부속 규칙:

- **command/query 물리 배포(애플리케이션 워크로드) 분리는 이 ADR의 결정 대상이 아니다.** 초기엔 단일 배포로 시작하고, 읽기 스케일 또는 장애 격리 신호가 임계를 넘을 때만 물리 분리한다(YAGNI) — 이는 [[RFC-007-deployment-infra-ops]]가 정한 배포 토폴로지 결정이며, 본 ADR은 그 아래 **데이터 계층**(DB 인스턴스·HA·다리)만 다룬다.
- **Kafka는 클러스터 하나를 공유한다** — command가 produce, query 측 projector가 consume(별도 클러스터 아님).

### 결과 (Consequences)

- 좋은 점: query가 command 스키마를 물리적으로 볼 수 없어 CQRS 경계가 디시플린이 아니라 물리로 선다.
- 좋은 점: 읽기 확장이 인스턴스 분할 없이 HA 레플리카만으로 흡수돼, 무트래픽 규모에 맞는 최소 machinery를 유지한다.
- 좋은 점: Redis Sentinel을 그대로 계승해 v1에서 검증된 자산을 재사용한다.
- 트레이드오프: DB 인스턴스가 늘어난다(command primary/standby + query primary/standby) — 비용·운영 표면 증가. 로컬·소규모 환경은 축소로 흡수한다.
- 트레이드오프: query DB는 이벤트로만 채워져 프로젝션 지연(최종 일관성)이 생긴다.
- 트레이드오프: standby 1대 + 정책 형태만 지금 고정하고, 복제 지연 허용치의 절대값과 자동 페일오버 도입 여부는 미확정으로 남는다 — **재검토 트리거**: 운영에서 정상 범위를 실측한 뒤 튜닝.

### 확인 (Confirmation)

- command-module과 query-module이 각자 단일 datasource에 정적 바인딩돼 있고, 앱 코드에 R/W 라우팅 분기가 없는지 코드 리뷰로 확인한다.
- query DB 스키마에 command 측 테이블(event_store·상태·Outbox)이 존재하지 않는지 마이그레이션 스크립트로 확인한다.
- command→query 데이터 흐름이 오직 Kafka/projector 경로로만 발생하고 binlog 기반 직접 조회 경로가 없는지 확인한다.
- 구현 사이클에서 정의: standby 복제 지연 허용치의 구체 임계, 환경별 축소 매트릭스의 세부 항목.

## 선택지 상세 (Pros and Cons of the Options)

### T-2. 단일 primary + read replica(binlog 다리) (기각)

- 장점: 인스턴스 하나로 R/W를 나눠 운영 표면이 T-3보다 작다.
- 단점: replica가 command 테이블까지 미러하므로 query가 물리적으로 command 스키마를 보게 된다.
- 기각 사유: CQRS 경계가 물리가 아니라 디시플린(코드 규율)에만 의존하게 돼, 목표 자체를 약화시킨다.

### T-1. 단일 인스턴스 (기각)

- 장점: 구성이 가장 단순하다.
- 단점: 읽기 부하와 쓰기 정합성이 계속 같은 인스턴스에서 경합한다.
- 기각 사유: 물리 분리라는 목표에 미달한다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클 · 배포 사이클)**: 복제 지연 허용치의 절대 숫자, 자동 페일오버 도입 여부(측정 후 확정) · 호스팅 형태(RDS vs 자가 관리 MySQL) — 앱에 투명한 원칙에 따라 배포 사이클로 위임, 환경별 축소 매트릭스의 세부 항목.
- 관련: [[RFC-007-deployment-infra-ops]] · [[DESIGN-010-deployment-runtime]] · [[ADR-005-event-store-mysql-table]] · [[ADR-004-read-model-projection-and-replica]] · [[ADR-009-event-ordering-and-delivery-guarantee]] · [[ADR-012-kafka-hosting-msk-vs-self-managed]] · [[ADR-019-caching-redis-role]] · [[RFC-002-read-model-consistency]]
- 계승: `13.db-hosting-and-read-write-topology.md`(v2 초기 스케치) — command/query 완전 분리 + Kafka 다리 + binlog=HA라는 골격은 유지하되, Redis 계승·standby 정책·환경별 규격은 [[RFC-007-deployment-infra-ops]] 합의로 이 ADR이 확정한다.
