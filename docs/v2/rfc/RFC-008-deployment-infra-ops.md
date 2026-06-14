# RFC-008 — 배포·인프라·운영

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[09-deployment-runtime]] 보강 + [[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]] 비준(Proposed→Accepted)

## 배경

타깃 런타임은 EKS, Kafka는 self-managed Strimzi, command/query DB는 물리적으로 분리한다(Kafka가 둘을 잇는 다리, binlog 복제=HA, 애플리케이션 라우팅 없음, 호스팅 형태는 투명). 토폴로지의 *방향*은 라운드1에서 잠갔다. 남은 건 namespace 구획, command/query 물리 분리 시점, 복제·페일오버 규약, Strimzi 규격 — 즉 "방향은 맞는데 어떤 규격·시점으로 운영하나"다. 여기서 닫는다.

## 논의 항목

### Q1. 클러스터 namespace 토폴로지 (단일 평탄화 vs app/data 분리)
- **출처**: [[09-deployment-runtime]]
- **옵션**: (a) 단일 평탄화 namespace / (b) app·data namespace 분리 / (c) 컨텍스트별 분리
- **쟁점**: 분리를 정당화하는 건 RBAC 경계인가, 리소스 쿼터인가, 수명주기 독립인가 — 셋 중 무엇도 아니면 분리는 비용. 사이드 프로젝트 규모에선 **평탄화를 기본값**으로 두고 분리 트리거를 명시.

### Q2. command/query 물리 서비스(배포) 분리 시점
- **출처**: [[01.cqrs-command-query-module-split]] · [[06.strangler-migration]] · [[09-deployment-runtime]]
- **옵션**: (a) 처음부터 별 배포 / (b) 단일 배포로 시작, 신호 도달 시 분리
- **쟁점**: 모듈 분리(논리)와 배포 분리(물리)는 별개. **초기 단일 배포**, 분리 트리거를 정의(읽기 스케일 요구·장애 격리 신호의 임계). 임계 미달 시 분리는 YAGNI.

### Q3. Redis failover 토폴로지 (Sentinel vs Cluster)
- **출처**: [[13.db-hosting-and-read-write-topology]]
- **옵션**: (a) Sentinel(v1 계승) / (b) Cluster
- **쟁점**: v1이 Sentinel이므로 계승이 기본선. Cluster는 샤딩·수평 확장 요구가 증명될 때만. 현 워크로드(세션·캐시)가 Cluster를 요구하는지 확인.

### Q4. standby 개수·복제 지연 허용치·페일오버 자동화
- **출처**: [[13.db-hosting-and-read-write-topology]]
- **옵션**: standby 수(0/1/n), 복제 지연 허용치(정책), 페일오버(수동 vs 자동)
- **쟁점**: HA=binlog 복제 전제. standby 1개+허용 지연 정책이 기본 후보. 절대 지연 숫자·자동 페일오버 도입은 **측정 후** 결정.

### Q5. DB 인스턴스 환경별 축소 기준
- **출처**: [[13.db-hosting-and-read-write-topology]] · [[11-environments-and-testing]]
- **옵션**: (a) 환경 무관 command/query 물리 분리 유지 / (b) 로컬·소규모는 단일 인스턴스 / (c) 같은 인스턴스·다른 스키마로 분리 흉내
- **쟁점**: 물리 분리는 프로덕션 불변식. 로컬·소규모에서 동일 규격을 강제하면 비용만 늘어남 — 환경별 축소 기준(어디까지 (b)/(c)를 허용하나)을 정한다.

### Q6. Strimzi 클러스터 규격 + KRaft vs ZooKeeper
- **출처**: [[12.kafka-hosting-msk-vs-self-managed]]
- **옵션**: 브로커 수·복제 팩터·PDB·스토리지 클래스·리소스 한도 / 메타데이터: KRaft vs ZooKeeper
- **쟁점**: **KRaft 권장 방향**(ZooKeeper 의존 제거, 신규 클러스터 표준). 브로커 수·RF·PDB·스토리지 규격은 측정·구현으로 확정.

### Q7. 로컬(k3s)~타깃(EKS) Strimzi 구성 패리티 수준
- **출처**: [[12.kafka-hosting-msk-vs-self-managed]] · [[11-environments-and-testing]]
- **옵션**: (a) 완전 패리티 / (b) 토폴로지 동형·규격 축소 / (c) 로컬은 단일 브로커
- **쟁점**: 패리티가 높을수록 "로컬에서 통과 = 프로덕션에서 통과" 신뢰가 오르나 로컬 자원 부담. 어떤 속성(RF·리스너·인증)을 패리티로 묶고 어떤 걸 축소 허용하나를 정한다.

### Q8. projection read model 물리 위치 (query DB primary 동거 vs 별 인스턴스)
- **출처**: [[09-deployment-runtime]] · [[13.db-hosting-and-read-write-topology]]
- **옵션**: (a) query DB primary에 동거 / (b) 별 인스턴스
- **쟁점**: 동거가 기본(추가 인스턴스 비용 회피). 별 인스턴스는 읽기 부하 격리가 증명될 때. [[RFC-003-read-model-consistency]] 프로젝션 범위 결정과 연동.

### Q9. projector·outbox relay readiness 신호 상세
- **출처**: [[09.event-ordering-and-delivery-guarantee]] · [[09-deployment-runtime]]
- **측정 트리거**: readiness를 무엇으로 신고하나 — consumer lag 임계, Outbox 적체 건수·연령. 신호 *정의*는 지금, 임계 숫자는 운영 측정으로 튜닝.

### Q10. 핵심 SLI 대시보드·알람 임계
- **출처**: [[09-deployment-runtime]] · [[10-observability]]
- **측정 트리거**: 핵심 SLI(프로젝션 지연·Outbox 적체·소비 lag·페일오버 시간) 목록은 지금, 대시보드·알람 임계 숫자는 운영 측정으로. [[RFC-009-observability]]와 연계해 중복 방지.

## 닫는 방식

- Q1·Q2·Q3·Q5·Q7·Q8 = **논의로 지금 결정**(기본값·트리거·축소 기준).
- Q4·Q6·Q9·Q10 = **측정/구현 트리거**(정책·방향 지금, 절대 숫자·규격은 운영 측정·구현으로).
- 🌱 없음.

## 위임

- 호스팅 선택(RDS vs 자가 관리 MySQL, ElastiCache vs 자가 관리 Redis) = 호스팅 형태 투명 원칙에 따라 본 RFC 밖. **배포 사이클**에서 운영 비용·관리 부담으로 판단.
- GitOps 도구(ArgoCD/Flux 등) 선택 = 본 RFC 밖. [[index|docs/todo]] 백로그로 이관.

## 산출물

- [[09-deployment-runtime]] §namespace·배포 분리·readiness·SLI 보강.
- [[12.kafka-hosting-msk-vs-self-managed]] Strimzi 규격·KRaft 결정 반영 → `Proposed`→`Accepted`.
- [[13.db-hosting-and-read-write-topology]] standby·복제 지연·환경별 축소 반영 → `Proposed`→`Accepted`.

## 관련 문서
- [[RFC-002-decision-queue]] · [[09-deployment-runtime]] · [[12.kafka-hosting-msk-vs-self-managed]] · [[13.db-hosting-and-read-write-topology]] · [[RFC-003-read-model-consistency]] · [[RFC-009-observability]]
