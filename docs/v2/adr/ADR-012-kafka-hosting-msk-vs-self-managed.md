# ADR-012: Kafka 호스팅 — self-managed Strimzi 잠금 + 메타데이터 KRaft, k3s~EKS 패리티는 속성별 분리

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-007-deployment-infra-ops]] · **설계**: [[DESIGN-010-deployment-runtime]]
- **연관 ADR**: [[ADR-013-db-hosting-and-read-write-topology]] · [[ADR-005-event-store-mysql-table]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 단일 앱 안에서 트랜잭션이 끝났다. V2는 command가 만든 사실을 Kafka를 통해 query 측 projector에 비동기로 전달하고, 이 Outbox→Kafka→projector 경로가 읽기 모델 전체의 생명선이 된다 — Kafka는 1급 인프라다. 손님의 `ReserveTable` 커맨드가 EKS 위 command 파드에 쓰기+Outbox로 커밋되고, self-managed Strimzi가 command와 query를 잇는 다리를 서면, query 파드의 projector가 이를 구독해 read model을 갱신한다.

워크로드 런타임 방향(EKS)은 [[ADR-026-workload-runtime-placement]]에서 이미 잠겼고, DB/Redis 같은 다른 데이터 면 구성요소는 호스팅 형태(관리형 vs 자가 관리)를 투명 원칙으로 배포 사이클에 위임했다. 그러나 Kafka는 이 투명 원칙의 예외다 — command↔query 유일 접점이라는 무게 때문에 호스팅 형태를 배포 사이클에 넘기지 않고 이 ADR에서 직접 결정한다. 결정 대상은 세 축이다: 런타임 호스팅(관리형 MSK vs self-managed), 메타데이터 관리 방식, 로컬(k3s)~프로덕션(EKS) 환경 패리티를 어디까지 맞출 것인가.

**Kafka를 관리형(MSK)에 위임할 것인가 self-managed로 직접 운영할 것인가, 메타데이터는 무엇으로 관리하며, k3s~EKS 패리티를 어디까지 맞춰야 하는가.**

## 결정 동인 (Decision Drivers)

- command↔query 유일 접점이라는 무게 — 비용·운영 오버헤드보다 로컬~프로덕션 패리티 훼손 회피가 우선한다.
- 신규 클러스터를 ZooKeeper 앙상블에 새로 묶을 이유가 없다 — 별도 앙상블 운영 부담 + 업스트림이 KRaft로 표준 이동.
- 로컬(k3s)과 프로덕션(EKS)을 전 속성에서 맞추면 로컬 자원 부담이 과하다 — 패리티가 필요한 지점과 그렇지 않은 지점을 갈라야 한다.
- 무트래픽 학습 규모 — 브로커 수·스토리지 같은 규격 수치를 사전에 형식화하는 것은 과설계다.

## 검토한 선택지 (Considered Options)

**런타임 호스팅**
- **self-managed Strimzi (EKS/k3s 위)** — 자가 관리, 관리형 대비 비용·운영 오버헤드는 지지만 k3s~EKS 패리티를 훼손하지 않는다.
- **AWS MSK (관리형)** — 브로커 운영을 AWS에 위임하지만 비용·운영 오버헤드가 있고 로컬 패리티(k3s에서 MSK를 동형으로 재현하기 어려움)를 훼손한다.

**메타데이터 관리**
- **KRaft** — Kafka 자체 Raft 합의로 메타데이터 관리, 별도 앙상블 불필요.
- **ZooKeeper** — 별도 앙상블 운영 부담, 업스트림이 KRaft로 이동 중.

**k3s~EKS 패리티 범위**
- **전 속성 패리티** — 로컬이 프로덕션과 완전히 동형, 로컬 자원 부담이 크다.
- **속성별 분리 패리티** — 동작 정합성 속성(리스너·인증·토픽 토폴로지)만 맞추고 규모 속성(브로커 수·스토리지)은 로컬 축소를 허용한다.

## 결정 (Decision Outcome)

**채택: self-managed Strimzi(런타임) + KRaft(메타데이터) + 속성별 분리 패리티.** MSK는 비용·운영 오버헤드와 k3s~EKS 패리티 훼손을 이유로 기각한다. ZooKeeper는 별도 앙상블 운영 부담을 새로 지울 이유가 없어 기각한다. 전 속성 패리티는 로컬 자원 부담이 과해 기각하고, 속성별로 갈라 필요한 것만 맞춘다.

| 축 | 결정 |
|---|------|
| **런타임** | self-managed Strimzi(EKS/k3s 위) — 호스팅 형태 투명 원칙(DB/Redis에 적용)의 예외로 이 ADR에서 직접 결정. MSK는 채택하지 않는다. |
| **메타데이터** | KRaft. ZooKeeper는 기각 |
| **k3s~EKS 패리티 — 동작 정합성 속성** | 리스너·인증·토픽 토폴로지는 패리티 유지 (로컬에서 동작 정합을 검증) |
| **k3s~EKS 패리티 — 규모 속성** | 브로커 수·스토리지는 로컬 축소 허용 |

### 결과 (Consequences)

- 좋은 점: 브로커 운영 표면을 직접 쥐므로 관리형 뒤에 가려지는 디테일이 없다.
- 좋은 점: KRaft로 별도 ZooKeeper 앙상블 운영 부담을 지지 않는다.
- 좋은 점: 속성별 패리티 분리로 로컬 자원 부담 없이도 동작 정합성(리스너·인증·토픽 토폴로지)은 검증할 수 있다.
- 트레이드오프: self-managed Strimzi는 브로커 운영·업그레이드·스토리지(PV)·장애 복구를 전부 스스로 진다 — 규격이 미확정 상태로 뜬 브로커가 디스크 풀·리밸런스 폭주를 일으키면 운영 부담이 학습을 압도할 위험이 있다.
- 트레이드오프: 규모 속성을 로컬에서 축소하므로, 브로커 수·스토리지 관련 문제는 로컬에서 재현되지 않고 프로덕션에서만 드러날 수 있다.

### 확인 (Confirmation)

- 구현 사이클에서 정의: Strimzi 매니페스트가 KRaft 모드로 구성됐는지, ZooKeeper 의존이 없는지 확인.
- 구현 사이클에서 정의: k3s와 EKS 각각에서 리스너·인증·토픽 토폴로지 동작이 정합하는지 통합 테스트로 검증.

## 선택지 상세 (Pros and Cons of the Options)

### AWS MSK (관리형) (기각)
- 장점: 브로커·패치·복제·모니터링을 AWS가 운영해 운영 표면이 최소화된다.
- 단점: 비용·운영 오버헤드가 있고, k3s에서 MSK를 동형으로 재현하기 어려워 로컬~프로덕션 패리티가 훼손된다.
- 기각 사유: 비용·운영 오버헤드와 k3s~EKS 패리티 훼손.

### ZooKeeper 메타데이터 (기각)
- 단점: 별도 앙상블을 새로 운영해야 한다.
- 기각 사유: 신규 클러스터를 굳이 ZooKeeper에 묶을 이유가 없고, 업스트림도 KRaft로 표준 이동 중이다.

### 전 속성 k3s~EKS 패리티 (기각)
- 단점: 로컬 자원 부담이 크다.
- 기각 사유: 패리티가 실제로 필요한 것은 동작 정합성(리스너·인증·토픽 토폴로지)이지 규모(브로커 수·스토리지) 자체가 아니다.

## 추가 정보 (More Information)

- **미결정 (→ 구현/운영 사이클)**: 브로커 수, 복제 팩터, PDB, 스토리지 클래스·용량, 리소스 한도 — 처리량·내구성 측정 후 확정.
- 관련: [[RFC-007-deployment-infra-ops]] · [[DESIGN-010-deployment-runtime]] · [[ADR-013-db-hosting-and-read-write-topology]] · [[ADR-005-event-store-mysql-table]]
