# Devil's Advocate — 11-runtime-topology.md

## 1. Position + Steel-man

- **핵심 주장**: 모듈(코드 경계)과 워크로드(런타임 배치)는 별개이며, 각 워크로드는 자신의 동시성·확장·장애격리 특성에 따라 독립적으로 배치 결정을 내려야 한다 — command/query는 초기엔 합쳐도 되지만 projector/relay는 처음부터 분리한다.
- **Steel-man**: 읽기 트래픽이 쓰기보다 수 배~수십 배 크고 스파이크성이며, 프로젝션 지연(eventual consistency)이 비즈니스적으로 허용되고, 팀이 이미 Kafka/K8s 운영 역량을 보유한 조직이라면 — 이 토폴로지는 정확히 필요한 만큼만 분리하는 합리적 설계다.

## 2. 숨은 가정

1. 읽기 부하가 언젠가 쓰기 부하를 압도해 command/query 물리 분리를 실제로 요구하게 될 것이다 — 트래픽 모델·수치 근거 없음.
2. 팀이 Strimzi Kafka의 컨슈머 그룹 재조정, lag 모니터링, 파티션 용량 계획을 운영할 역량을 (지금 또는 곧) 갖췄다.
3. 단일 EKS 클러스터 내 namespace 분리(app-ns/data-ns)가 워크로드 간 의미 있는 장애·네트워크 격리를 제공한다 — 별도 노드풀/AZ/NetworkPolicy는 문서 어디에도 명시되지 않음.
4. command 쓰기와 query 읽기 사이의 이벤트 기반 최종 일관성(프로젝션 지연)이 이 시스템을 사용하는 비즈니스 프로세스에 항상 수용 가능하다.

## 3. 반론

### C1 — command/query 물리 분리의 트리거 부재
- **type**: assumption / **severity**: critical
- "물리 분리는 읽기 부하가 요구할 때"라고만 되어 있고, 그 요구를 판단할 지표·임계값·오너가 없다. 이런 식으로 유예된 결정은 실무에서 트리거를 소유한 사람이 없어 영원히 발동하지 않는 경우가 흔하다. 결과적으로 모듈 레벨 분리(포트/인터페이스 비용)만 미리 다 치르고, 그걸로 사려던 런타임 확장성 이득은 끝내 현금화되지 못하는 구조가 된다.
- **선례**: no clear precedent — speculative concern, 다만 "나중에 필요하면 분리"식 유예된 마이크로서비스 준비 패턴이 실제로 발동되지 않는 경우는 업계에서 흔히 관찰되는 패턴.

### C2 — namespace 분리를 격리로 오인
- **type**: structural / **severity**: high
- app-ns/data-ns 분리를 "네트워크 경계"라 칭하지만, K8s namespace는 기본적으로 RBAC/조직 구획이지 장애 도메인 경계가 아니다. 별도 노드풀, taint/toleration, PodDisruptionBudget, NetworkPolicy가 명시되지 않는 한, 클러스터 컨트롤 플레인/CNI/노드 오토스케일러 장애 하나가 namespace 구분과 무관하게 app-ns 전체(command/query/projector/relay)를 동시에 끌고 내려간다. 그런데 문서는 이 구조로부터 "projector lag이 쌓여도 조회는 무중단" 같은 격리 이득을 주장한다 — 그 이득의 전제(진짜 격리)가 검증되지 않았다.
- **선례**: no clear precedent for this specific system — speculative concern, 다만 "namespace ≠ 격리 경계"는 Kubernetes 공식 문서에도 명시된 일반 원칙.

### C3 — 이중 HA DB + Kafka + Redis의 운영 부담이 "prototype" 목표와 불일치
- **type**: execution / **severity**: high
- command MySQL과 query MySQL이 각각 별도 HA(레플리카) 세트를 갖고, 여기에 Strimzi Kafka 클러스터, Redis master/replica까지 더하면 stateful 시스템만 최소 4~5개 군을 백업·복구·페일오버까지 운영 가능한 수준으로 유지해야 한다. 이 저장소는 스스로를 "Prototype Reservation System"이라 칭하는데, 이 정도 상시 운영 부담이 학습 목표 대비 비례하는지에 대한 근거가 문서에 없다 — 더 가벼운 단일 DB/스키마 분리 버전으로 같은 CQRS+Outbox 교훈을 먼저 얻고 점진적으로 이 토폴로지로 졸업하는 경로가 검토되지 않았다.
- **선례**: no clear precedent — speculative concern, 다만 "팀 역량 대비 과잉 프로비저닝된 운영 부채"는 마이크로서비스 조기 도입 실패 사례에서 일반적으로 지적되는 패턴.

## 4. 다중 페르소나 공격

**On-call/SRE**: 7개 워크로드 + Kafka 컨슈머 그룹 + SKIP LOCKED 리더 선출 + 이중 HA DB + Redis + JWKS. "read model이 최신이 아니다" 같은 단일 증상 하나를 진단하려면 최소 4개 시스템(Kafka lag, 컨슈머 그룹 상태, projector-QRYDB 네트워크, binlog replication)을 순서대로 확인해야 한다. Strimzi 장애 모드를 아무도 실전에서 겪어본 적 없는 초기 단계에서, 이 정도 구성 요소 수는 MTTR을 구조적으로 늘린다.

**CFO(인프라 비용)**: command/query 각각의 HA MySQL, Kafka 클러스터(브로커+스토리지), Redis master/replica, 그리고 5개 이상의 앱 워크로드 — 이건 실사용 트래픽이 명시되지 않은 프로토타입에 대해 프로덕션급 인프라 청구서다. "학습을 위한 것"이라는 방어가 가능하더라도, 같은 학습 효과를 훨씬 저렴한 단일 클러스터/단일 DB 구성으로 먼저 달성할 수 있는지 이 문서는 검토조차 하지 않고 곧바로 "목표 토폴로지"로 점프한다.

**주니어(온보딩)**: 문서 첫 줄부터 "모듈과 워크로드는 1:1이 아니다"라고 선언한다 — 이는 신규 엔지니어가 코드 레이아웃만 보고 배포 구조를 직관적으로 추론할 수 없다는 뜻이다. query 관련 버그 하나를 고치려 해도, 그게 query-service 워크로드의 문제인지 projector 워크로드의 문제인지부터 먼저 판단해야 한다 — 이 매핑을 체화하기 전까지는 첫 배포조차 두렵다.

## 5. 핵심 취약점

문서가 주장하는 "장애 격리"(projector 격리, relay 격리, namespace 경계)는 전부 실제로 검증되지 않은 인프라 전제 — 별도 노드풀/AZ/NetworkPolicy — 위에 세워진 종이 위의 격리다. namespace 분리만으로는 그 격리를 실제로 보장하지 않으며, 문서는 이 격차를 인지조차 하지 않은 채 격리 이득을 기정사실처럼 서술한다.

## 6. 가역성

혼합형: 워크로드 배치(replica 수, command/query co-location 여부)는 언제든 되돌릴 수 있는 reversible 결정이지만, command/query DB 분리와 Kafka 이벤트 스키마 채택은 실 데이터·다운스트림 컨슈머가 생기는 순간 one-way door가 된다.
