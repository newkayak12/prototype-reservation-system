# V2 Data Index

> 이 디렉토리는 V2 물리 데이터 계층(스키마·테이블)의 **데이터 사전(data dictionary)**을 담는다.
> [[00-module-index]]가 코드 모듈 경계를, [[00-domain-overview]]가 컨텍스트·이벤트 스토밍을 다루는 것과 대칭으로, 여기서는 **어느 물리 스키마에 어떤 테이블이 있고 컬럼이 무엇인가**만 다룬다.

---

## 물리 스키마 맵 ([[ADR-013-db-hosting-and-read-write-topology]])

```mermaid
graph LR
    subgraph CMD[command MySQL 인스턴스]
        ES[(event_store)]
        SNAP[(snapshot)]
        OBX[(outbox)]
        STATE[(schedule·user·menu·category·company 상태 테이블)]
    end

    subgraph QRY[query MySQL 인스턴스]
        RM1[(query.reservation.model)]
        RM2[(query.timetable.model)]
        RM3[(query.restaurant.model)]
        RM4[(query.{schedule,user,menu,category,company}.model)]
        INBOX[(inbox)]
    end

    subgraph AUTHDB[auth-server 데이터소스]
        AUTHT[(authenticate)]
        SAS[(oauth2_* — Spring Authorization Server)]
    end

    CMD -->|Kafka·projector<br/>물리적 다리, binlog 아님| QRY
```

- **command ↔ query는 물리적으로 완전 분리**된 별도 MySQL 인스턴스다 — 다리는 Kafka/projector뿐, binlog는 각 DB 자신의 HA 전용([[ADR-013-db-hosting-and-read-write-topology]]). query가 command 스키마를 코드 규율이 아니라 물리적으로 못 본다.
- **auth-server**는 독립 배포 단위([[ADR-024-authentication-boundary]])라 자기 datasource를 가진다. command/query 어느 쪽과도 스키마를 공유하지 않는다. 다만 별도 MySQL **인스턴스**로 갈지, command 인스턴스 안 별도 **스키마**로 얹을지는 미결이다([[09-auth-server-module]] M-8) — 물리 인스턴스 여부와 무관하게 스키마 경계는 독립이라는 점만 이 문서의 확정 사항이다.

---

## 스키마 분류

| # | 스키마 | 물리 DB | 문서 | 비고 |
|---|--------|---------|------|------|
| 1 | `command` | command MySQL | [[01-command-schema]] | event_store·outbox·snapshot(ES 공용) + schedule·user·menu·category·company 상태 테이블 |
| 2 | `query` | query MySQL | [[02-query-schema]] | 도메인별 read model(`query.{domain}.model`) + inbox |
| 3 | `auth` | auth-server 전용 datasource | [[03-auth-schema]] | `authenticate`(credential·refresh) + Spring Authorization Server 표준 테이블 |

> **컨텍스트 대응**: 각 스키마 문서 안의 도메인 절은 [[00-domain-overview]]의 9개 컨텍스트(`reservation`·`timetable`·`restaurant`·`schedule`·`user`·`authenticate`·`menu`·`category`·`company`)와 1:1로 대응한다. 다만 물리 스키마는 컨텍스트별로 나뉘지 않고 **쓰기 모델 성격**(ES / 상태+Outbox / 현행-lookup) 및 **command/query 분리**를 기준으로 3개로 묶인다 — 컨텍스트 수만큼 스키마를 쪼개지 않는 이유는 [[DESIGN-002-module-structure]]의 모듈 경계와 스키마 경계가 다른 축이기 때문이다(모듈=코드 경계, 스키마=DB 인스턴스 경계).

---

## 문서 구조 (각 스키마)

```
0. 소속·배치 (어느 물리 DB, 어느 컨텍스트들이 공존하는가)
1. 공용 인프라 테이블 (해당 스키마 전체가 공유하는 테이블)
2. 컨텍스트별 테이블 (컬럼·PK·인덱스·근거 문서)
3. 미결/설계 공백 (아직 RFC·ADR로 확정되지 않은 컬럼·정책)
```

각 테이블 정의는 **근거 문서가 있는 것만 "확정"으로 표기**한다. 근거가 없는 컬럼(예: 이벤트 카탈로그처럼 도메인 이벤트 스토밍 후 확정 예정인 것)은 "설계 예시 — 구현 시 확정"으로 명시하고, 없는 걸 있는 것처럼 적지 않는다.

---

## 관련 문서

- 쓰기 모델: [[DESIGN-003-write-model]] · 읽기 모델: [[DESIGN-004-read-model]] · 이벤트 스토어 수명주기: [[DESIGN-009-event-store-lifecycle]]
- DB 토폴로지: [[ADR-013-db-hosting-and-read-write-topology]] · 이벤트 스토어 구현: [[ADR-005-event-store-mysql-table]]
- 동시성: [[RFC-014-aggregate-concurrency-control]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]]
- 순서·relay·DLQ: [[RFC-025-ordering-relay-dlq-reconciliation]] · 읽기 신선도: [[RFC-030-read-freshness-command-response-contract]]
- 인증 경계: [[ADR-024-authentication-boundary]] · 인증 토큰: [[DESIGN-017-auth-token]]
- 모듈 허브: [[00-module-index]] · 도메인 허브: [[00-domain-overview]]
