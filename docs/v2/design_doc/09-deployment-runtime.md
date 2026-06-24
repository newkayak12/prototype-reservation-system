# V2 Design Doc — 09. Deployment / Runtime View (물리·배포 뷰)

- **상위 결정**: [[12.kafka-hosting-msk-vs-self-managed]] · [[13.db-hosting-and-read-write-topology]]
- **개요**: [[00-design-overview]] · **모듈**: [[01-module-structure]] · **읽기**: [[03-read-model]]

> 앞선 design_doc(00~05)이 *논리적으로 어떻게 짓는가*였다면, 본 문서는 그것이 *물리적으로 어디서 도는가*다. 모듈([[01-module-structure]])은 빌드·코드 경계이고, 여기서 다루는 워크로드는 그 모듈이 런타임에 어떤 프로세스·파드로 배치되는가다. 둘은 1:1이 아니다.

> ⚠️ 본 문서는 **목표 런타임 토폴로지**다. 현재(V1)는 단일 `adapter-module` 부트 앱 + docker-compose(MySQL·Redis)로 돈다([[01-current-state]]). 아래 EKS/Kafka(self-managed)/RDS 구성은 V2의 도착점이며, 전환은 [[06.strangler-migration]] 순서대로 단계적으로 깐다. 인프라부터 빅뱅으로 세우지 않는다(YAGNI).

## 배치 단위 (logical workload)

V2의 런타임 구성요소는 일곱이다. 모듈과의 대응 관계를 먼저 못 박는다.

| 워크로드 | 책임 | 출처 모듈 | 상태성 |
|----------|------|-----------|--------|
| **command 서비스** | 명령 수신·검증·쓰기(이벤트 스토어/상태+Outbox) | `command-module` | stateless (DB가 상태) |
| **query 서비스** | 조회 — query DB의 read model → DTO | `query-module` (web/service/repository) | stateless |
| **projector** | Kafka 구독 → read model 갱신 | `query-module` (projection) | stateless, **컨슈머 그룹 상태는 Kafka가 보관** |
| **outbox relay** | Outbox 테이블 → Kafka 발행 | `infrastructure-module` (Outbox 배관) | stateless, leader 단일성 필요 |
| **API 게이트웨이** | 엣지 — JWT 검증·거친 역할 게이트·레이트리밋·검증된 클레임 헤더 전달 | 별도 배포(Spring Cloud Gateway) | stateless |
| **인증 서버** | 토큰 발급(sign-in)·refresh rotation·JWKS 노출 | `user`·`authenticate` 컨텍스트(Spring Authorization Server) | stateless (DB가 상태) |
| **(데이터 면)** | Kafka(Strimzi) · command/query 분리 MySQL(각 binlog HA) · Redis(master/replica) | 호스팅 투명 | stateful |

핵심 결정 — **command/query는 코드(모듈)로는 분리하되, 초기 배포는 같이 갈 수 있다.** RFC 비목표에 "command/query의 물리적 서비스 분리(별도 배포)"가 명시돼 있다([[RFC-001-v2-cqrs-and-event-sourcing]]). 즉 모듈 분리가 곧 별도 파드를 강제하지 않는다. 물리 분리는 읽기 부하가 그것을 요구할 때 떼면 된다([[01-module-structure]] "향후 물리 분리 경로"). 단 **projector와 outbox relay는 처음부터 별 워크로드로 분리**한다 — 둘은 요청-응답 수명주기와 다른 동시성·스케일·장애 격리 특성을 갖기 때문이다(아래 §배치 근거).

## 목표 클러스터 토폴로지

```mermaid
graph TB
    subgraph AWS
        subgraph EKS [EKS 클러스터]
            subgraph app-ns [namespace: app]
                CMD[command 서비스<br/>Deployment N replica]
                QRY[query 서비스<br/>Deployment N replica]
                PRJ[projector<br/>Deployment · 컨슈머그룹]
                RLY[outbox relay<br/>Deployment 1~소수 · leader]
            end
            subgraph data-ns [namespace: data]
                STRIMZI[Strimzi Kafka<br/>self-managed]
            end
            ING[Ingress · ingress-nginx<br/>TLS 종단]
            SCG[API Gateway · Spring Cloud Gateway<br/>JWT 검증·역할 게이트·레이트리밋]
            AUTH[인증 서버 · Spring Authorization Server<br/>발급·refresh·JWKS]
        end
        subgraph managed [데이터 면 · 호스팅 투명]
            CMDDB[(command MySQL · primary<br/>event_store/state/Outbox)]
            CMDDBR[(command MySQL · standby<br/>binlog HA)]
            QRYDB[(query MySQL · primary<br/>프로젝션 read model)]
            QRYDBR[(query MySQL · standby<br/>binlog HA)]
            REDIS[(Redis · master/replica)]
        end
    end
    actor((Client)) --> ING
    ING -->|API| SCG
    ING -->|sign-in·refresh| AUTH
    SCG -->|검증된 클레임 헤더| CMD
    SCG -->|검증된 클레임 헤더| QRY
    SCG -.->|JWKS| AUTH
    CMD -->|쓰기·event_store·Outbox| CMDDB
    RLY -->|Outbox 폴링| CMDDB
    RLY -->|발행| STRIMZI
    STRIMZI --> PRJ
    PRJ -->|프로젝션 갱신| QRYDB
    QRY -->|read model 조회| QRYDB
    CMDDB -.->|binlog HA| CMDDBR
    QRYDB -.->|binlog HA| QRYDBR
    CMD --> REDIS
    QRY --> REDIS
```

> Kafka=**self-managed Strimzi**([[12.kafka-hosting-msk-vs-self-managed]]), DB=**command/query 분리 MySQL**(각각 binlog HA, [[13.db-hosting-and-read-write-topology]]). **command→query 다리는 Kafka/projector**(이벤트)이지 binlog가 아니다 — binlog는 각 DB의 이중화(HA)용. query DB엔 프로젝션만 있어 경계가 물리로 성립.

> ⚠️ 위 그림은 `app`/`data` 두 namespace로 그렸지만, 이는 *분리가 정당화된 뒤*의 모습이다. 기본값은 **단일 평탄 namespace**다([[RFC-007-deployment-infra-ops]]). namespace 분리를 정당화하는 건 셋뿐 — RBAC 경계, 리소스 쿼터, 배포 수명주기 독립. 셋 중 무엇도 압박이 아니면 분리는 순수 비용(매니페스트 중복·NetworkPolicy·디버깅 동선)이다. 그래서 트리거가 당겨지기 전까지 평탄화로 시작하고, 위 `app`/`data` 분할은 그 트리거(특히 data 면의 수명주기·쿼터 독립)가 실제로 당겨질 때 도입한다.

## 워크로드 배치 근거

### API 게이트웨이 / 인증 서버 — 인클러스터, 검증 모델 A

근거·기각된 대안은 [[RFC-020-authentication-boundary-gateway]]. 핵심만 —

- **흐름**: `Client → Ingress(ingress-nginx · TLS) → Spring Cloud Gateway(검증·필터) → command/query 서비스`. nginx=클러스터 입구·TLS, SCG=앱 레벨 인증/레이트리밋/클레임. 둘 다 인클러스터(관리형 아님) — k3s~EKS 패리티로 인증·리스너 동작 정합을 로컬에서 검증([[RFC-007-deployment-infra-ops]], [[12.kafka-hosting-msk-vs-self-managed]] Strimzi와 같은 결).
- **검증 모델 A**: SCG가 엣지에서 JWT를 한 번 검증(인증 서버 JWKS로)하고, 검증된 신원·역할을 헤더로 전달. 앱은 토큰을 다시 풀지 않고 그 헤더를 신뢰한다. 이게 [[13-authorization]] "역할=엣지"·[[16-auth-token]] 클레임 전파의 런타임 실체.
- **모델 A의 의무 두 가지(안 지키면 위조로 뚫림)**: ① SCG가 클라이언트 인입 신원 헤더(`X-User-Id` 등)를 **반드시 strip/덮어쓰기**, ② **NetworkPolicy로 "SCG만 command/query에 도달"** 강제(게이트웨이 우회 차단). 헤더 신뢰 = 네트워크 신뢰 가정이다. (제로트러스트 모델 B[서비스마다 Resource Server 재검증]는 분산 신뢰가 빡빡해질 때의 승격 경로 — [[RFC-020-authentication-boundary-gateway]].)
- **인증 서버**: 발급(sign-in)·refresh rotation([[16-auth-token]] 무상태 서명 JWT)·JWKS 노출 전담. `user`·`authenticate` 컨텍스트(상태+Outbox). 도메인 앱은 발급을 모른다. 서명 키 회전·OIDC는 인프라 백로그 T-13.

### command 서비스 / query 서비스
- 둘 다 stateless HTTP. EKS `Deployment` + HPA(CPU/RPS). Ingress(ALB)로 라우팅.
- 초기엔 **한 배포에 합쳐도 무방**(RFC 비목표). 읽기 스케일이 쓰기와 독립적으로 커지면 `query-module`만 별 Deployment로 떼어 독립 스케일·독립 장애 격리를 얻는다. top-level 모듈 분리가 이 분리를 *값싸게* 만든다([[01-module-structure]]).

### projector — 왜 별 워크로드인가
- Kafka 컨슈머라 **요청-응답이 아니라 구독 루프**다. HTTP 트래픽과 스케일·재시작 특성이 다르다.
- read model 갱신은 **멱등**해야 한다(Zero Payload·재처리, [[03-read-model]]·[[07.reservation]]). 같은 이벤트를 두 번 받아도 안전.
- 스케일은 **파티션 수에 종속**된다 — 컨슈머 그룹 replica는 토픽 파티션 수를 넘어도 노는 인스턴스만 는다. 따라서 HPA 상한은 파티션 수에 맞춘다.
- 장애 격리: projector가 막혀도 command 쓰기는 계속된다(최종 일관성). 별 워크로드라 read 지연이 write 가용성을 끌어내리지 않는다.

### outbox relay — 왜 단일성(leader)이 필요한가
- Outbox 테이블을 폴링해 미발행분을 Kafka로 흘린다([[02-write-model]] 발행 경로). v1의 `AFTER_COMMIT REQUIRES_NEW` + 스케줄러 재처리를 워크로드로 격상한 것.
- 여러 replica가 같은 Outbox 행을 동시에 집으면 **중복 발행** 위험 → leader election(또는 `SELECT ... FOR UPDATE SKIP LOCKED` 기반 경합 회피, 구현 사이클 결정)으로 단일 처리자를 보장한다.
- Kafka는 어차피 **at-least-once**이므로 중복 발행 자체는 컨슈머 멱등(Zero Payload)으로 흡수되지만, relay는 불필요한 중복을 줄이는 게 책임이다.
- 따라서 relay는 `replicas: 1`(+ 무중단 위해 standby) 또는 분산 락 기반 소수 replica로 둔다. HPA 대상이 아니다.

> **대안 — 폴링 relay 대신 CDC(Debezium).** Outbox 테이블 변경을 CDC로 Kafka에 직결하면 relay 워크로드를 없앨 수 있다. v1 [[07.reservation]]의 CDC 후속 계획·[[05.event-store-mysql-table]]의 "CDC로의 전환 기준"과 정합한다. 다만 Kafka Connect/Debezium이라는 새 운영 표면이 생긴다 → **초기엔 폴링 relay, CDC는 트래픽·운영 성숙도가 정당화할 때**(YAGNI). TBD.

## 데이터 면 배치

| 구성요소 | 위치(권고) | 근거 |
|----------|-----------|------|
| Kafka | **EKS/k3s 위 Strimzi**(self-managed) · 메타데이터 **KRaft** | [[12.kafka-hosting-msk-vs-self-managed]] |
| **command MySQL** (event_store/state/Outbox) | 쓰기 모델 DB · 1 인스턴스 + **standby 1대**(binlog HA) | [[13.db-hosting-and-read-write-topology]] |
| **query MySQL** (프로젝션 read model) | 읽기 모델 DB · 1 인스턴스 + HA 레플리카 · read model은 도메인별 스키마로 분리 · projector가 Kafka로 채움 | [[13.db-hosting-and-read-write-topology]] |
| Redis (read=replica, write=master) | 호스팅 투명(ElastiCache/자가) · **v1 Sentinel 계승** | 읽기/쓰기 엔드포인트 분리·failover — [[13.db-hosting-and-read-write-topology]] |

- **command MySQL**: event_store·상태·Outbox가 한 트랜잭션([[05.event-store-mysql-table]])이라 *같은 인스턴스*. binlog standby로 이중화(HA).
- **query MySQL**: projector가 Kafka 이벤트를 받아 프로젝션 read model을 여기에 쓰고, query가 읽는다. **command→query 다리는 Kafka/projector**(binlog 아님). query DB엔 프로젝션만 있어 "query가 command 테이블을 못 본다"가 *물리적으로* 성립. **앱 라우팅 코드 없음**(command-module→command DB, query-module→query DB 정적 바인딩). read model은 화면·조회 용도마다 여럿이라 **한 query 인스턴스 안에서 도메인별 스키마로 분리**해 담는다(도메인 경계=스키마 경계). 읽기 확장은 인스턴스 분할이 아니라 HA 레플리카로 분산하고, '무거운 도메인을 별 인스턴스로 뗀다'는 접는다([[RFC-007-deployment-infra-ops]]). 상세 [[13.db-hosting-and-read-write-topology]].
- **HA 정책의 *형태*는 지금, *숫자*는 측정 후**: 두 DB 모두 **standby 1대 + 복제 지연 허용치 + 임계 초과 시 알람**이라는 정책 형태로 고정한다. 다만 허용 지연의 절대값(몇 초/밀리초)과 자동 페일오버 도입 여부는 *지금 박지 않는다* — 측정 없이 정하면 허구의 SLO가 된다. 운영에서 정상 범위를 관측한 뒤 튜닝한다([[RFC-007-deployment-infra-ops]]).
- **Strimzi=KRaft**: 신규 클러스터를 ZooKeeper 앙상블에 묶을 이유가 없다(업스트림이 KRaft로 표준 이동). 브로커 수·복제 팩터·PDB·스토리지 클래스·리소스 한도 같은 *규격*은 처리량·내구성을 측정해 구현 사이클에서 확정한다([[12.kafka-hosting-msk-vs-self-managed]]).
- **Redis=Sentinel 계승**: v1이 이미 Sentinel로 도니 계승이 기본선. Cluster(샤딩·수평 확장)는 단일 마스터 메모리/처리량이 *실제로* 압박받을 때로 미룬다 — 현재 워크로드(세션·캐시)는 그 신호가 없다([[13.db-hosting-and-read-write-topology]]).

## 이벤트·트래픽 흐름 (런타임)

```mermaid
sequenceDiagram
    participant C as Client
    participant CMD as command 서비스
    participant DB as command MySQL<br/>(event_store/state + Outbox)
    participant RLY as outbox relay
    participant K as Kafka(Strimzi)
    participant PRJ as projector
    participant RM as query MySQL<br/>(read model)
    participant QRY as query 서비스
    C->>CMD: command
    CMD->>DB: 이벤트/상태 + Outbox (한 트랜잭션 커밋)
    RLY->>DB: 미발행 Outbox 폴링
    RLY->>K: 발행 (at-least-once)
    K->>PRJ: 구독
    PRJ->>RM: read model 멱등 갱신
    C->>QRY: query
    QRY->>RM: 프로젝션 조회 (query DB)
```

쓰기 가용성과 읽기 신선도가 **분리**된다: relay/projector가 느려도 command는 계속 커밋되고, 읽기만 잠깐 뒤처진다(최종 일관성, [[03-read-model]]).

## 운영 관심사 (요약)

- **헬스/레디니스**: command·query는 HTTP probe. projector·relay의 readiness는 *프로세스가 떴는가*가 아니라 *진척이 따라붙었는가*를 반영해야 한다 — **projector는 consumer lag이 임계 아래, relay는 Outbox 적체 건수·연령이 임계 아래**일 때 ready. 단순 liveness로 ready를 신고하면 적체가 쌓인 채 트래픽을 받는다. 신호의 *정의*는 지금 고정하고, 임계 *숫자*는 정상 범위를 관측한 뒤 튜닝한다(미측정 임계는 기동마다 false-negative, [[RFC-007-deployment-infra-ops]]).
- **확장 축**: query·projector는 읽기 부하로, command는 쓰기 부하로 독립 스케일. relay는 스케일하지 않음(단일성).
- **웹 티어 IO 확장 레버 = virtual thread(레버만 박고 지금은 off)**: 영속화가 블로킹 JPA라 non-blocking(코루틴/WebFlux)은 이득 없이 복잡도만 진다 — 채택 안 한다([[RFC-008-observability]] 코루틴 기각). command·query 웹 티어의 동시성 확장이 필요해지면 `spring.threads.virtual.enabled=true`(JDK21·Boot 3.4) 한 줄로 명령형 MVC·JPA 코드 그대로 블로킹 비용을 낮춘다(MDC·추적도 안 깨짐). 지금 켤 필요는 없고 *레버*만 박아 둔다([[RFC-007-deployment-infra-ops]]).
- **핵심 SLI 목록(확정) / 임계(측정)**: 이 시스템에서 의미 있는 SLI는 넷이다 — **프로젝션 지연, Outbox 적체, 소비 lag, 페일오버 소요 시간**(최종 일관성 건강도 + HA 건강도). 이 *목록*은 지금 못 박는다. 대시보드 패널·알람 임계 *숫자*는 운영 측정의 몫이고, 구체 계측·대시보드는 [[10-observability]]/[[RFC-008-observability]]와 연계해 중복을 피한다([[RFC-007-deployment-infra-ops]]).
- **GitOps**: 매니페스트를 Git 단일 출처로 두고 ArgoCD/Flux로 동기화하는 방향을 **선호**한다. 다만 채택·도구 선정·파이프라인 설계는 본 사이클 범위 밖 — **별도 todo로 보류**한다(아키텍처 결정 아님).

## 미결정 (TBD) — 형태는 확정, 숫자·목록은 구현/측정으로
> 방향·정책 형태는 위에서 고정했고, 다음 값들은 운영 측정·구현에서 확정한다([[RFC-007-deployment-infra-ops]] "Design으로 넘기는 것").

- outbox relay의 단일성 구현(leader election vs `SKIP LOCKED`) — 구현 사이클.
- 폴링 relay → CDC(Debezium) 전환 기준 — [[05.event-store-mysql-table]]·[[07.reservation]]와 정합, 트래픽 의존.
- command/query 서비스(앱) 물리 분리 시점 — 읽기 스케일 요구가 증명되거나 장애 격리가 필요할 때([[01-module-structure]]). (DB 토폴로지는 query 1 인스턴스+HA 레플리카로 고정·read model 도메인 스키마 분리로 확정 — [[13.db-hosting-and-read-write-topology]].)
- standby 복제 지연 허용치의 절대 숫자, 자동 페일오버 도입 여부 — 운영 측정.
- 환경별 DB 축소 매트릭스(어디까지 단일 인스턴스/스키마 분리를 허용하나) — 프로덕션 물리 분리 불변식은 유지, 하위 환경만 축소([[11-environments-and-testing]]).
- k3s~EKS Strimzi 패리티로 묶을 속성(리스너·인증·토픽 토폴로지 동형) / 축소 허용 속성(브로커 수·스토리지 용량)의 정확한 목록 — [[11-environments-and-testing]].
- projector·outbox readiness 임계 숫자, 핵심 SLI 대시보드·알람 임계 — 운영 측정([[10-observability]] 연계).
- GitOps 도구·파이프라인 — 별도 todo.

## 관련 문서
- [[00-design-overview]] · [[01-module-structure]] · [[02-write-model]] · [[03-read-model]] · [[04-migration]] · [[11-environments-and-testing]]
- RFC: [[RFC-007-deployment-infra-ops]]
- ADR: [[12.kafka-hosting-msk-vs-self-managed]] · [[13.db-hosting-and-read-write-topology]] · [[05.event-store-mysql-table]]
- 계승: [[07.reservation]]
