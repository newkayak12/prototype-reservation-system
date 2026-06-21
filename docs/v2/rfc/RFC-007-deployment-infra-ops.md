# RFC-007 — 배포·인프라·운영

- **상태**: 합의됨 (2026-06-21) · design [[09-deployment-runtime]] 반영 · ADR [[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]] 비준 대기
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-INDEX]]
- **닫으면**: [[09-deployment-runtime]] 보강 + [[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]] 비준(Proposed→Accepted)

## 맥락

타깃 런타임은 EKS, Kafka는 self-managed Strimzi, command/query DB는 물리적으로 분리한다. 이 토폴로지의 *방향*은 라운드1에서 이미 잠갔다 — Kafka가 command와 query를 잇는 다리, binlog 복제가 HA를 떠받치고, 애플리케이션은 읽기/쓰기를 라우팅하지 않으며, 호스팅 형태(RDS냐 자가 관리냐)는 그 위에서 투명하다. "EKS냐 ECS냐"를 다시 들출 일은 없다.

남은 건 *규격과 시점*이다 — namespace 구획, command/query 물리 분리 시점, DB standby·복제·페일오버 규약, DB 환경별 축소, Strimzi 스펙·메타데이터, k3s~EKS 패리티. 본 RFC는 이것만 닫는다. 닫으면 [[09-deployment-runtime]]을 보강하고 [[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]]를 Accepted로 올린다.

> **이 문서가 소유하지 않는 것(포인터만).** read model 내부 조직(도메인 스키마)은 [[RFC-002-read-model-consistency]]가, readiness 신호 정의·핵심 SLI·메트릭 카탈로그·알람 임계는 [[RFC-008-observability]]가(catch-up readiness는 [[RFC-011-projection-rebuild-catchup]]) 소유한다. 여기서는 *배포 측 hook*(query 토폴로지, readiness probe 게이팅)만 두고 정의·수치는 그 문서로 넘긴다. 초안에 이 주제들이 섞여 있던 것을 분리했다.

## 결정의 공통 형태 — 방향은 지금, 숫자는 측정 후

아래 결정은 전부 같은 모양이다: **방향(정성)은 지금 잠그고, 절대 수치는 운영 실측 후 튜닝한다.** 복제 지연 허용치·분리 임계·브로커 수·복제 팩터·페일오버 소요 같은 숫자는 정상 범위를 관측하기 전엔 허구의 SLO가 되므로 책상에서 박지 않는다. 그래서 각 절은 *방향*만 적고, 수치는 말미 〈Design으로 넘기는 것〉에 모은다.

## A. Kubernetes 배치·배포 시점

**namespace는 평탄하게 시작.** 단일 평탄 namespace를 기본값으로 둔다. 분리를 정당화하는 건 셋뿐 — RBAC 경계, 리소스 쿼터, 수명주기 독립 — 이고, 셋 다 압박이 아니면 namespace 분리는 순수 비용(매니페스트 중복·NetworkPolicy·디버깅 동선)이다. 분리 트리거(팀/역할 분화, 리소스 폭주 격리, 배포 수명주기 분기)는 명시해 두고, 당겨지기 전까진 평탄이 옳다([[09-deployment-runtime]]). (이의 여지: 컨텍스트 수가 늘어 한 namespace에서 시야를 잃는 게 트리거 셋보다 먼저 올 수 있다.)

**command/query 물리 분리는 신호가 임계를 넘을 때.** *모듈 분리(논리)* 와 *배포 분리(물리)* 는 별개다 — 모듈이 갈라져 있어도 두 Deployment로 굴릴 필요는 별개다([[01.cqrs-command-query-module-split]]). 초기 단일 배포로 시작해, 읽기 스케일이 결합 배포로 감당 안 되거나 한쪽 장애 격리가 필요할 때 분리한다. 모듈이 이미 갈라져 있어 분리는 나중에도 싸므로 이 미루기가 안전하다(전형적 YAGNI). 임계 지표·수치는 측정으로.

**웹 티어 IO 확장 레버 = virtual thread.** 영속화가 블로킹 JPA라 non-blocking(코루틴/WebFlux)은 이득 없이 복잡도만 진다 — 채택 안 한다([[RFC-008-observability]] 코루틴 기각). 동시성 확장이 필요해지면 `spring.threads.virtual.enabled=true`(JDK21·Boot 3.4) 한 줄로 명령형 MVC·JPA 코드 그대로 블로킹 비용을 낮춘다(MDC·추적도 안 깨짐). 지금 켤 필요는 없고, *레버*만 박아 둔다.

## B. 데이터 계층 — HA·토폴로지·환경 규격

**query/command 토폴로지 = 각 1 인스턴스 + HA 레플리카.** 읽기 부하는 HA 레플리카가 흡수하므로 "query를 여러 인스턴스로 쪼갠다"는 기본이 아니다 — 실트래픽이 없어 인스턴스 분할을 유발할 부하 자체가 없고, 분할 샤딩은 부하가 실재할 때의 운영 수단일 뿐 CQRS·프로젝션·HA 레플리카가 이미 주는 것 너머를 가르치지도 않는다. **읽기 확장은 레플리카로, query 인스턴스 분할은 명시적으로 off**([[13.db-hosting-and-read-write-topology]]). (그 한 인스턴스 *안*에서 read model을 도메인별 스키마로 나누는 조직 규약은 [[RFC-002-read-model-consistency]] 소유 — 여기선 토폴로지만.)

**Redis는 v1의 Sentinel 계승.** 현재 워크로드는 세션·캐시이고 Cluster의 샤딩을 요구하는 신호가 없다. v1이 이미 Sentinel로 도므로 계승이 자연스럽다. Cluster 전환은 메모리/처리량이 단일 마스터를 실제로 압박할 때로 미룬다([[13.db-hosting-and-read-write-topology]]). (Redis가 *왜* 읽기 캐시가 아니라 조정 상태 전용인지는 [[RFC-018-caching-redis-role]].)

**standby 1대 + 복제 지연 허용 정책.** 사이드 프로젝트 규모에 standby 다수는 과하고 0대는 HA가 아니다. **standby 1 + 지연 허용치 + 임계 초과 알람**이라는 정책 *형태*만 지금 잡고, 허용 지연 절대값과 자동 페일오버 도입 여부는 측정 후 — 둘 다 측정 없이 정하면 허구의 SLO다([[13.db-hosting-and-read-write-topology]]).

**DB는 환경별로 규격 축소.** 물리 분리는 *프로덕션 불변식*이지만 로컬·소규모까지 강제하면 비용만 는다. 프로덕션은 분리 불변식을 지키고, 로컬은 단일 인스턴스, 스테이지는 토폴로지 동형을 지키되 스펙 축소 — *프로덕션 불변식을 흐리지 않는 선에서* 하위 환경 비용을 깎는다. 환경별 축소 매트릭스는 Design([[11-environments-and-testing]]).

## C. Kafka / Strimzi

**메타데이터는 KRaft.** 신규 클러스터를 ZooKeeper에 묶을 이유가 없다 — 별도 앙상블 운영 부담 + 업스트림이 KRaft로 표준 이동. 브로커 수·복제 팩터·PDB·스토리지 클래스·리소스 한도는 처리량·내구성 측정으로 확정([[12.kafka-hosting-msk-vs-self-managed]]).

**k3s~EKS 패리티는 속성별로 끊는다.** *동작 정합성*에 영향 주는 속성(리스너 구성·인증 방식·토픽 토폴로지 동형)은 패리티로 묶고, *규모* 속성(브로커 수·스토리지 용량)은 로컬 축소를 허용한다. 그러면 로컬에서 잡히는 버그 대부분(인증·리스너·직렬화)은 잡으면서 자원 부담은 던다. 속성 분류 목록은 Design([[12.kafka-hosting-msk-vs-self-managed]]·[[11-environments-and-testing]]).

## 다른 문서가 소유 — 여기선 배포 측 hook만

- **read model 내부 조직(도메인별 스키마).** → [[RFC-002-read-model-consistency]] / design [[03-read-model]]. 본 RFC는 query *토폴로지*(1 인스턴스 + HA 레플리카, 분할 off)만 소유.
- **readiness 신호 정의·임계.** projector·outbox relay가 lag·Outbox 적체·연령으로 "준비됨"을 신고한다는 *정의*와 임계는 [[RFC-008-observability]](메트릭)·[[RFC-011-projection-rebuild-catchup]](catch-up readiness)가 소유. 본 RFC는 **배포가 그 신호를 readiness probe로 게이팅한다**는 와이어링만 [[09-deployment-runtime]]에 둔다.
- **핵심 SLI·메트릭 카탈로그·알람 임계.** 프로젝션 지연·Outbox 적체·소비 lag 등의 이름·라벨·단위와 알람 임계는 [[RFC-008-observability]] 소유(SLI=사용자 체감, 카탈로그=내부 파이프라인, 같은 현상 이중 명명 금지). 본 RFC는 HA 고유 지표(**페일오버 소요 시간**)만 위 standby 정책에 묶어 둔다.

## Design으로 넘기는 것

방향은 위에서 잡았고, 다음 수치는 구현/측정으로 확정한다:

- command/query 배포 분리를 당기는 신호의 지표·임계 수치.
- standby 복제 지연 허용치 절대값, 자동 페일오버 도입 여부, 페일오버 소요 SLI.
- 환경별 DB 축소 매트릭스(어디까지 단일 인스턴스/스키마 분리를 허용하나).
- Strimzi 브로커 수·복제 팩터·PDB·스토리지 클래스·리소스 한도.
- k3s~EKS 패리티로 묶을 속성 / 축소 허용 속성의 정확한 목록.

## 위임

- 호스팅 선택(RDS vs 자가 관리 MySQL, ElastiCache vs 자가 관리 Redis)은 호스팅 형태 투명 원칙에 따라 본 RFC 밖이다. **배포 사이클**에서 운영 비용·관리 부담으로 판단한다.
- GitOps 도구(ArgoCD/Flux 등) 선택도 본 RFC 밖. [[index|docs/todo]] 백로그로 이관.

## 관련 문서

[[RFC-INDEX]] · [[09-deployment-runtime]] · [[12.kafka-hosting-msk-vs-self-managed]] · [[13.db-hosting-and-read-write-topology]] · [[RFC-002-read-model-consistency]] · [[RFC-008-observability]] · [[RFC-011-projection-rebuild-catchup]] · [[RFC-018-caching-redis-role]]
