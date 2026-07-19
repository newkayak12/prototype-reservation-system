# 11 · Runtime Topology (워크로드 ↔ 모듈 배치)

> 허브: [[00-module-index]] | 근거: [[DESIGN-010]] (배포·런타임 뷰) · [[RFC-007]] (배포·인프라·운영)

모듈(빌드·코드 경계)과 워크로드(런타임 프로세스)는 **1:1이 아니다**. 이 문서는 그 대응을 못 박는다. 특히 query-module 하나가 **두 워크로드**(projection 서버 / read model 서버)로 갈라지는 이유를 여기서 정리한다.

## 1. 7개 워크로드 ↔ 모듈 ([[DESIGN-010]] §4.1)

| 워크로드 | 책임 | 출처 모듈 | 상태성 | 상세 문서 |
|----------|------|-----------|--------|-----------|
| **command 서비스** | 명령 수신·검증·쓰기(event_store/state+Outbox) | `command-module` | stateless(DB가 상태) | [[05-command-adapter]] |
| **query 서비스** (read model 서버) | 조회 — read model → DTO | `query-module` (web/service/repository) | stateless | [[08-query-read-model-server]] |
| **projector** (projection 서버) | Kafka 구독 → read model 갱신 | `query-module` (projection) | stateless, 컨슈머 그룹 상태는 Kafka | [[07-query-projection-server]] |
| **outbox relay** | Outbox → Kafka 발행 | `command-infrastructure` | stateless, 단일성(SKIP LOCKED) | [[06-command-infrastructure]] |
| **API 게이트웨이** | 엣지 — JWT 검증·역할 게이트·레이트리밋·클레임 헤더 전달 | 별도(Spring Cloud Gateway) | stateless | [[09-auth-server-module]] |
| **인증 서버** | 토큰 발급·refresh rotation·JWKS | `auth-server-module` | stateless | [[09-auth-server-module]] |
| **(데이터 면)** | Kafka(Strimzi)·command/query 분리 MySQL(binlog HA)·Redis(master/replica) | 호스팅 투명 | stateful | — |

## 2. 핵심 배치 결정 ([[DESIGN-010]] §4.1)

- **command/query는 코드(모듈)로 분리하되, 초기 배포는 같이 갈 수 있다.** 모듈 분리 ≠ 별도 파드 강제. 물리 분리는 읽기 부하가 요구할 때([[DESIGN-002]] §4.8)
- **projector와 outbox relay는 처음부터 별 워크로드로 분리.** 요청-응답 수명주기와 다른 동시성·스케일·장애 격리 특성 때문
  - projector: 컨슈머 루프, 스케일 축 = 컨슈머 수(상한 = 파티션 수), lag이 쌓여도 조회는 무중단
  - relay: 폴링 + 단일성(leader/SKIP LOCKED), 발행 실패가 조회/명령과 격리

## 3. 목표 클러스터 토폴로지 ([[DESIGN-010]] §4.2)

```mermaid
graph TB
    subgraph EKS
        subgraph app-ns [namespace: app]
            CMD[command 서비스<br/>Deployment N]
            QRY[query 서비스<br/>Deployment N]
            PRJ[projector<br/>Deployment · 컨슈머그룹]
            RLY[outbox relay<br/>Deployment 1~소수 · leader]
        end
        subgraph data-ns [namespace: data]
            STRIMZI[Strimzi Kafka]
        end
        ING[Ingress · ingress-nginx TLS]
        SCG[API Gateway · Spring Cloud Gateway]
        AUTH[인증 서버 · SAS]
    end
    CMDDB[(command MySQL<br/>event_store/state/Outbox + HA)]
    QRYDB[(query MySQL<br/>read model + HA 레플리카)]
    REDIS[(Redis master/replica)]

    Client((Client)) --> ING
    ING -->|API| SCG
    ING -->|sign-in·refresh| AUTH
    SCG -->|클레임 헤더| CMD
    SCG -->|클레임 헤더| QRY
    SCG -.->|JWKS| AUTH
    CMD --> CMDDB
    CMD --> REDIS
    RLY --> CMDDB
    RLY --> STRIMZI
    STRIMZI --> PRJ
    PRJ --> QRYDB
    QRY --> QRYDB
```

## 4. 확장 축

| 워크로드 | 확장 방법 | 상한/제약 |
|----------|-----------|-----------|
| command 서비스 | replica 증설 | DB 커넥션 |
| query 서비스 | replica 증설 + query DB **HA 레플리카** | 캐시 아님, 인스턴스 분할 아님 |
| projector | 같은 컨슈머 그룹에 인스턴스 추가(competing consumers) | **파티션 수** |
| outbox relay | 소수 유지(SKIP LOCKED 경쟁 소비) | 단일성 우선 |

## 5. 관련 문서

- 배포·런타임: [[DESIGN-010]] · 인프라·운영 RFC: [[RFC-007]]
- Kafka 호스팅: [[ADR-012]] · DB 토폴로지: [[ADR-013]]
