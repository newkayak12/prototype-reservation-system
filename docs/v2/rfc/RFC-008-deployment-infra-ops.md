# RFC-008 — 배포·인프라·운영

- **상태**: Open · 논의 중 · 2026-06-15
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[09-deployment-runtime]] 보강 + [[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]] 비준(Proposed→Accepted)

## 맥락

타깃 런타임은 EKS, Kafka는 self-managed Strimzi, command/query DB는 물리적으로 분리한다. 이 토폴로지의 *방향*은 라운드1에서 이미 잠갔다 — Kafka가 command와 query를 잇는 다리이고, binlog 복제가 HA를 떠받치며, 애플리케이션은 읽기/쓰기를 라우팅하지 않고, 호스팅 형태(RDS냐 자가 관리냐)는 그 위에서 투명하다. 그러니 여기서 다시 "EKS인가 ECS인가" 같은 걸 들출 일은 없다.

남은 건 *규격과 시점*이다. namespace를 어떻게 구획할 것인지, command/query를 물리적으로 갈라 배포하는 시점은 언제인지, standby와 복제 지연·페일오버를 어떤 규약으로 운영할지, Strimzi 클러스터를 어떤 스펙으로 세울지. 한 문장으로 줄이면 "방향은 맞는데, 어떤 규격·시점으로 운영하나"이고, 이 RFC는 그 질문을 닫는다. 닫고 나면 [[09-deployment-runtime]]을 보강하고, [[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]]를 Proposed에서 Accepted로 올린다.

## 논의

### namespace는 평탄하게 시작한다

가장 먼저 손에 잡히는 건 클러스터 안에서 워크로드를 어떻게 나눠 담느냐다. 단일 평탄 namespace에 다 올릴 수도, app과 data를 갈라 둘로 나눌 수도, 컨텍스트마다 namespace를 쪼갤 수도 있다([[09-deployment-runtime]]). 화려한 쪽으로 끌리기 쉽지만, 분리를 *정당화*하는 건 세 가지뿐이다 — RBAC 경계, 리소스 쿼터, 수명주기 독립. 셋 중 어느 것도 지금 압박이 아니라면 namespace 분리는 순수 비용(매니페스트 중복·NetworkPolicy·디버깅 동선)일 뿐이다.

그래서 나는 **단일 평탄 namespace를 기본값**으로 둔다. 다만 분리 트리거는 명시해 둔다 — 팀/역할이 갈려 RBAC 경계가 필요해지거나, 특정 워크로드의 리소스 폭주를 쿼터로 격리해야 하거나, 배포 수명주기가 갈라질 때. 트리거가 당겨지기 전까지 평탄화가 옳다. (이의 여지: 컨텍스트 수가 늘어 매니페스트가 한 namespace에서 시야를 잃는 시점이 트리거 셋보다 먼저 올 수 있다 — 그땐 평탄화를 재검토.)

### command/query 물리 분리는 신호가 임계를 넘을 때

다음은 command와 query를 별개 배포로 가르는 시점이다([[01.cqrs-command-query-module-split]]·[[06.strangler-migration]]). 여기서 자주 섞이는 게 *모듈 분리(논리)* 와 *배포 분리(물리)* 다. 모듈은 이미 갈라져 있어도, 그걸 두 개의 Deployment로 따로 굴릴 필요는 별개 문제다. 처음부터 별 배포로 가면 운영 표면적이 두 배가 되고, 단일 배포로 시작하면 그 비용을 미룬다.

내 입장은 **초기 단일 배포, 신호가 임계를 넘으면 분리**다. 분리를 당기는 신호는 둘 — 읽기 측 스케일 요구가 쓰기와 결합된 배포로는 감당이 안 될 때, 혹은 한쪽 장애가 다른 쪽으로 번지는 걸 격리해야 할 때. 이 임계에 닿기 전 분리는 전형적인 YAGNI다. 모듈이 이미 갈라져 있으니 분리 자체는 나중에도 싸게 할 수 있다는 점이 이 미루기를 안전하게 만든다. (Design에서 검증: 임계를 어떤 지표·수치로 잡을지는 운영 측정으로.)

### Redis는 v1의 Sentinel을 계승한다

캐시·세션 계층의 페일오버 토폴로지는 Sentinel과 Cluster 사이다([[13.db-hosting-and-read-write-topology]]). v1이 이미 Sentinel로 돌고 있으니 계승이 자연스러운 기본선이다. Cluster는 샤딩과 수평 확장이 *증명된* 요구일 때나 값을 한다 — 단일 마스터 메모리로 못 담는 데이터, 단일 노드 처리량을 넘는 부하.

현재 워크로드는 세션과 캐시다. 이게 Cluster의 샤딩을 요구한다는 신호는 아직 없다. 그래서 **Sentinel 계승**이 내 입장이고, Cluster 전환은 메모리/처리량이 단일 마스터를 실제로 압박할 때로 미룬다. (이의 여지: 세션 데이터가 예상보다 빨리 불어 단일 마스터 메모리를 위협하면 그 판단을 앞당긴다.)

### standby 1대 + 복제 지연 정책, 페일오버는 측정 후

HA를 binlog 복제로 가져가기로 한 이상, 남는 건 standby를 몇 대 둘지, 복제 지연을 얼마까지 허용할지, 페일오버를 수동으로 할지 자동으로 할지다([[13.db-hosting-and-read-write-topology]]). 사이드 프로젝트 규모에서 standby를 여러 대 두는 건 과하고, 0대는 HA가 아니다.

그래서 **standby 1대 + 복제 지연 허용 정책**을 기본 후보로 둔다. 다만 여기서 두 가지를 의도적으로 *지금 박지 않는다* — 허용 지연의 절대 숫자(몇 초/몇 밀리초)와 자동 페일오버 도입 여부다. 둘 다 측정 없이 정하면 허구의 SLO가 된다. 정책의 *형태*("standby 1, 지연 허용치를 둔다, 임계 초과 시 알람")는 지금 잡고, 절대 수치와 자동화 스위치는 운영에서 실측한 뒤 튜닝한다. (운영에서 검증.)

### DB는 환경별로 규격을 축소한다

command/query 물리 분리는 *프로덕션의 불변식*이지만, 로컬과 소규모 환경에까지 같은 규격을 강제하면 비용만 늘어난다([[13.db-hosting-and-read-write-topology]]·[[11-environments-and-testing]]). 환경 무관하게 두 인스턴스를 강제할 수도, 로컬·소규모는 단일 인스턴스로 합칠 수도, 같은 인스턴스에 스키마만 둘로 나눠 분리를 흉내 낼 수도 있다.

내 입장은 **프로덕션은 물리 분리 불변식을 지키되, 로컬·소규모는 축소를 허용**한다는 것이다. 어디까지 (b)단일 인스턴스 또는 (c)스키마 분리를 허용하느냐의 경계를 환경별로 정한다 — 로컬은 단일 인스턴스로 충분하고, 스테이지는 토폴로지 동형을 지키되 인스턴스 스펙은 축소. 핵심은 *프로덕션 불변식을 흐리지 않는 선에서* 하위 환경의 비용을 깎는 것이다. (Design에서 검증: 환경별 축소 매트릭스.)

### Strimzi는 KRaft로, 규격은 측정으로

Kafka를 self-managed Strimzi로 가기로 한 이상, 클러스터 규격과 메타데이터 관리 방식이 남는다([[12.kafka-hosting-msk-vs-self-managed]]). 메타데이터는 KRaft와 ZooKeeper 둘 중 하나인데, 신규 클러스터를 ZooKeeper에 묶을 이유가 없다 — 별도 앙상블 운영 부담, 그리고 업스트림이 이미 KRaft로 표준을 옮겼다. 그래서 **KRaft 방향**이 내 입장이다.

반면 브로커 수·복제 팩터·PDB·스토리지 클래스·리소스 한도 같은 규격은 *지금 숫자로 박지 않는다*. 이건 처리량·내구성 요구를 측정하고 구현하며 확정할 값이지, 책상에서 정할 값이 아니다. 방향(KRaft)은 지금, 스펙은 구현/측정으로. (운영에서 검증.)

### k3s~EKS 패리티는 속성별로 끊는다

로컬은 k3s, 타깃은 EKS다. 둘의 Strimzi 구성을 얼마나 같게 맞추느냐는 트레이드오프다([[12.kafka-hosting-msk-vs-self-managed]]·[[11-environments-and-testing]]). 완전 패리티면 "로컬에서 통과 = 프로덕션에서 통과"라는 신뢰가 오르지만 로컬 자원 부담이 커지고, 로컬을 단일 브로커로 깎으면 가볍지만 그 신뢰가 무너진다.

내 입장은 **속성을 둘로 끊는 것**이다 — *동작 정합성*에 영향을 주는 속성(복제 팩터의 존재 여부가 아니라 리스너 구성·인증 방식·토픽 토폴로지의 동형)은 패리티로 묶고, *규모* 속성(브로커 수, 스토리지 용량)은 로컬에서 축소를 허용한다. 그러면 로컬에서 잡히는 버그의 대부분(인증·리스너·직렬화)은 잡으면서 자원 부담은 던다. 어떤 속성이 어느 쪽에 들어가는지의 정확한 목록은 Design으로 넘긴다. (Design에서 검증.)

### projection read model은 query DB에 동거시킨다

읽기 모델을 물리적으로 어디 둘지 — query DB primary에 같이 둘지, 별 인스턴스로 뺄지([[09-deployment-runtime]]·[[13.db-hosting-and-read-write-topology]]). 별 인스턴스는 추가 운영·비용이고, 동거는 그걸 회피한다.

기본은 **query DB primary 동거**다. 별 인스턴스로 빼는 건 읽기 부하가 query DB의 다른 작업을 실제로 압박하는 게 증명될 때다. 이 결정은 [[RFC-003-read-model-consistency]]의 프로젝션 범위 결정과 맞물린다 — projection이 무엇을, 얼마나 무겁게 들고 있느냐가 동거 한계를 정하기 때문이다. 그쪽이 정해지기 전까진 동거가 합리적 기본값이다. (이의 여지: RFC-003에서 projection이 예상보다 무겁게 잡히면 동거 가정을 다시 본다.)

### projector·outbox relay readiness 신호는 지금 정의한다

projector와 outbox relay가 "준비됨"을 무엇으로 신고하느냐([[09.event-ordering-and-delivery-guarantee]]·[[09-deployment-runtime]]). 단순히 프로세스가 떴다고 ready로 보면, 적체가 쌓인 채 트래픽을 받게 된다. readiness는 *진척*을 반영해야 한다 — consumer lag이 임계 아래인가, Outbox 적체 건수·연령이 임계 아래인가.

그래서 readiness 신호의 *정의*("lag과 Outbox 적체·연령을 readiness 조건에 묶는다")는 지금 잡는다. 다만 그 임계 *숫자*는 운영에서 정상 범위를 관측한 뒤 튜닝한다 — 측정 없이 박으면 기동 때마다 false-negative로 readiness가 막힌다. (운영에서 검증.)

### 핵심 SLI 목록은 지금, 임계는 측정으로

마지막으로 무엇을 보고 알람을 울릴지다([[09-deployment-runtime]]·[[10-observability]]). 이 시스템에서 의미 있는 SLI는 명확하다 — 프로젝션 지연, Outbox 적체, 소비 lag, 페일오버 소요 시간. 이 *목록*은 지금 확정한다.

하지만 대시보드 패널과 알람 임계 *숫자*는 운영 측정의 몫이다. 정상 범위를 모르는 채 임계를 박으면 알람이 노이즈가 되거나 침묵한다. 그리고 이 작업은 [[RFC-009-observability]]와 겹치는 영역이라, SLI 목록만 여기서 못 박고 구체적 계측·대시보드는 그쪽과 연계해 중복을 피한다. (운영에서 검증, RFC-009와 조율.)

## Design으로 넘기는 것

방향은 위에서 잡았고, 다음 값들은 구현/측정으로 확정한다:

- command/query 배포 분리를 당기는 신호의 지표·임계 수치.
- standby 복제 지연 허용치의 절대 숫자, 자동 페일오버 도입 여부.
- 환경별 DB 축소 매트릭스(어디까지 단일 인스턴스/스키마 분리를 허용하나).
- Strimzi 브로커 수·복제 팩터·PDB·스토리지 클래스·리소스 한도.
- k3s~EKS 패리티로 묶을 속성 / 축소 허용 속성의 정확한 목록.
- projector·outbox readiness 임계 숫자, 핵심 SLI의 대시보드·알람 임계.

## 위임

- 호스팅 선택(RDS vs 자가 관리 MySQL, ElastiCache vs 자가 관리 Redis)은 호스팅 형태 투명 원칙에 따라 본 RFC 밖이다. **배포 사이클**에서 운영 비용·관리 부담으로 판단한다.
- GitOps 도구(ArgoCD/Flux 등) 선택도 본 RFC 밖. [[index|docs/todo]] 백로그로 이관.

## 관련 문서

[[RFC-002-decision-queue]] · [[09-deployment-runtime]] · [[12.kafka-hosting-msk-vs-self-managed]] · [[13.db-hosting-and-read-write-topology]] · [[RFC-003-read-model-consistency]] · [[RFC-009-observability]]
