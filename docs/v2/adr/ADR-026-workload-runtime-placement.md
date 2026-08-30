# ADR-026: 앱 워크로드 런타임 배치 — 워크로드별 별도 배포 + 노드 분리, 단일 평탄 NS

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-007-deployment-infra-ops]] · **설계**: [[DESIGN-010-deployment-runtime]]
- **연관 ADR**: [[ADR-024-authentication-boundary]] · [[ADR-012-kafka-hosting-msk-vs-self-managed]] · [[ADR-013-db-hosting-and-read-write-topology]]

---

## 맥락과 문제 (Context and Problem Statement)

V2는 코드가 `command`/`query` 모듈로 갈라졌고, 런타임에는 엣지·인증·command·query·projector·relay가 각자 다른 수명주기(요청-응답 vs 컨슈머 루프 vs 폴링 단일성)로 뜬다. 그런데 이 워크로드들을 **어떤 배포 단위로, 어디에 배치해 격리하는가**를 정한 결정 문서가 없었다.

[[RFC-001-v2-cqrs-and-event-sourcing]]는 Non-goal에 "command/query의 물리적 서비스 분리(별도 배포)"를 올려 두었다. 그러나 이 항목은 CQRS/ES 강도를 정하는 그 RFC의 범위 밖으로 잘못 들어온 배치 결정이었고, 실제 의도(command/query를 처음부터 별도 서비스로 띄운다)와 정반대였다. 이 잘못된 비목표를 근거로 [[DESIGN-010-deployment-runtime]]·모듈 문서·분석 노트가 "초기엔 한 배포로 합친다"를 연쇄 전제로 깔았고, 여기서 "언제 물리 분리하나(트리거)"라는 없는 문제와 "네임스페이스 분리 = 격리"라는 오인이 파생됐다.

**앱 워크로드의 배포 단위(합침 vs 워크로드별 분리)와 격리 근거(네임스페이스 vs 노드)를 확정한다.**

## 결정 동인 (Decision Drivers)

- 실트래픽이 없는 **학습용 EKS**다 — "프로토타입이니 싸게 합쳐 둔다"는 존재하지 않는 비용을 최적화한다. 격리 메커니즘을 실제로 다뤄 보는 배치를 고른다.
- 모듈만 나누고 물리는 합쳐 두면, 분리 비용(포트/인터페이스)은 지금 다 내면서 그 대가로 사려던 독립 스케일·독립 장애 반경은 못 받는다.
- 워크로드마다 동시성 축·스케일 축·장애 특성이 다르다(요청-응답 / 컨슈머 그룹 / 폴링 단일성).
- 진짜 격리는 노드·네트워크 설정에서 나온다. 네임스페이스는 이름·권한·쿼터 구획이지 장애 도메인이 아니다(K8s 공식 원칙).

## 검토한 선택지 (Considered Options)

**배포 단위**
- **A — command/query 초기 동거(별도 배포 미강제)** — RFC-001 Non-goal 원안. 모듈만 나누고 배포는 합쳐 두다 부하가 요구하면 뗀다.
- **B — 워크로드별 별도 배포** — 6개 워크로드를 각자 Deployment로, 파드당 컨테이너 1개.

**격리 근거**
- **네임스페이스 분리(app-ns/data-ns)** — 논리 구획으로 나눈다.
- **노드 분리** — 워크로드를 서로 다른 노드에 배치한다.
- **단일 파드/노드 공유** — 한 노드에 몰아 둔다.

## 결정 (Decision Outcome)

**채택: B(워크로드별 별도 배포) + 노드 분리 + 단일 평탄 네임스페이스.**

| # | 결정 |
|---|------|
| 1 | 모든 앱 워크로드는 **워크로드별 별도 Deployment**, 파드당 **컨테이너 1개**. A(초기 동거)는 미채택 — [[RFC-001-v2-cqrs-and-event-sourcing]] Non-goal의 "command/query 물리적 서비스 분리(별도 배포)"를 이 ADR이 **supersede**한다 |
| 2 | 앱 워크로드 6개 — **① Envoy Gateway(엣지)** · **② 인증 서버(Spring Authorization Server)** · **③ command** · **④ query(read model)** · **⑤ projector(projection)** · **⑥ outbox relay**. 엣지는 Envoy Gateway 단일 홉([[ADR-024-authentication-boundary]] 개정) — ingress-nginx/SCG는 두지 않는다 |
| 3 | 격리 근거 = **노드 분리**. 각 워크로드는 서로 다른 노드에 뜬다. 한 워크로드의 자원 폭주·장애가 옆 워크로드를 같은 노드에서 끌고 내려가지 않는다 |
| 4 | 네임스페이스는 **단일 평탄**. app-ns/data-ns 분리는 하지 않는다. 네임스페이스 분리를 정당화하는 세 조건(RBAC 경계·리소스 쿼터·배포 수명주기 독립)이 실제로 압박이 될 때만 도입한다([[RFC-007-deployment-infra-ops]]) |
| 5 | 데이터 면(Kafka/Strimzi·command/query MySQL·Redis)은 **이 결정 범위 밖** — stateful 축으로 [[ADR-012-kafka-hosting-msk-vs-self-managed]]·[[ADR-013-db-hosting-and-read-write-topology]]가 다룬다 |
| 6 | 노드 분리 **구현 방식**(전용 node pool + taint/toleration vs anti-affinity)은 **작업가설**로 taint/toleration 전용 배치로 잡되, pool 수·규격은 [[08-k6-load-test-strategy]] 측정 후 확정한다 — 이 값을 실행 전 블로커로 굳히지 않는다 |

이 ADR은 **배포 단위와 격리 근거**만 확정한다. 구체 node pool 설계·HPA 축·NetworkPolicy·매니페스트는 [[DESIGN-010-deployment-runtime]]와 구현 사이클 소관이다.

### 결과 (Consequences)

- 좋은 점: 워크로드마다 독립 스케일(HPA)·독립 장애 반경을 갖는다 — "projector에 lag이 쌓여도 조회는 무중단" 같은 격리 이득이 종이가 아니라 노드 경계로 실제 뒷받침된다.
- 좋은 점: node pool·taint/toleration·워크로드별 HPA를 실제로 다뤄 보는 학습 표면이 열린다.
- 좋은 점: "언제 물리 분리하나" 트리거 문제가 사라진다 — 처음부터 나눠 뜨므로 나중에 쪼갤 대상이 없다.
- 트레이드오프: 워크로드 6개 × 별 노드 = 노드 수·비용이 오른다. 로컬(k3s)은 규모 축소를 허용한다([[RFC-007-deployment-infra-ops]] 패리티 원칙 — 동작 정합만 묶고 규모는 축소).
- 트레이드오프: 노드 분리 구현 방식이 아직 미확정이라, 측정 전까지는 작업가설로 남는다.

### 확인 (Confirmation)

- 각 앱 워크로드가 자신의 Deployment로 뜨고 파드당 컨테이너가 1개인지 매니페스트 리뷰로 확인한다.
- 워크로드가 서로 다른 노드에 배치되는지(taint/toleration 또는 anti-affinity가 실제 적용되는지) 배치 결과로 확인한다.
- 앱 워크로드가 단일 네임스페이스에 있는지 — app-ns/data-ns 분리가 없는지 확인한다.

## 선택지 상세 (Pros and Cons of the Options)

### A — command/query 초기 동거 (미채택)
- 장점: 초기 노드 수가 적어 저렴하다.
- 단점: 모듈 분리 비용은 다 내면서 독립 스케일·독립 장애 반경은 못 받는다. "부하가 요구하면 뗀다"는 오너·숫자가 없으면 영원히 발동하지 않는다.
- 기각 사유: 실제 의도(처음부터 별도 배포)와 반대고, 학습용이라 "싸게 합침"이 최적화할 비용이 없다.

### 네임스페이스 분리(app-ns/data-ns) (기각)
- 장점: 이름·권한·쿼터 구획이 생긴다.
- 단점: 네임스페이스는 장애 도메인이 아니다 — 컨트롤 플레인·CNI·노드 오토스케일러 장애 하나가 네임스페이스와 무관하게 전체를 끌고 내려간다. 매니페스트 중복·NetworkPolicy·디버그 동선 비용만 얹힌다.
- 기각 사유: 격리는 노드 분리(결정 3)에서 얻는다. 네임스페이스 분리는 RBAC·쿼터·수명주기 독립이 실제로 압박일 때만.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 노드 분리 구현 방식(전용 node pool + taint/toleration vs anti-affinity)·node pool 수·노드 규격 — [[08-k6-load-test-strategy]] 측정 후 [[DESIGN-010-deployment-runtime]]에서 확정.
- 이 ADR은 [[RFC-001-v2-cqrs-and-event-sourcing]] Non-goal의 "command/query의 물리적 서비스 분리(별도 배포)"를 **supersede**한다.
- 관련: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-007-deployment-infra-ops]] · [[DESIGN-010-deployment-runtime]] · [[ADR-024-authentication-boundary]] · [[ADR-012-kafka-hosting-msk-vs-self-managed]] · [[ADR-013-db-hosting-and-read-write-topology]]
