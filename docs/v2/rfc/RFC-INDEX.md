# RFC-INDEX — V2 결정 RFC 인덱스

- **성격**: 인덱스 (결정 없음, 추적 전용)
- **업데이트**: 2026-06-23

> RFC-001이 큰 그림을 잡았고, 이후 RFC들이 주제별 결정을 닫는다.
> 정독 순서는 아래 "열려있는 RFC" 섹션의 우선순위를 따른다.

## 상태 범례

| 기호 | 의미 |
|---|---|
| ✅ | Closed · 합의 완료 |
| 🔴 | Open · 논의 중 |
| 🏷 | 합의됨 · ADR 비준 완료 |

## RFC 전체 목록

| # | RFC | 주제 | 상태 | 닫으면 보강할 곳 |
|---|---|---|---|---|
| 1 | [[RFC-001-v2-cqrs-and-event-sourcing]] | V2 방향 결정 (큰 그림 라운드1) | 🏷 합의됨 (2026-06-12) | 기준 문서 |
| 2 | [[RFC-002-read-model-consistency]] | 읽기 모델·일관성 | 🔴 Open | [[03-read-model]]·[[04.read-model-projection-and-replica]] |
| 3 | [[RFC-003-messaging-delivery]] | 메시징·전달 보장 | 🔴 Open | [[07-messaging-topology]]·[[09.event-ordering-and-delivery-guarantee]] |
| 4 | [[RFC-004-event-store-schema-evolution]] | 이벤트 스토어·스키마 진화 | 🔴 Open | [[08-event-store-lifecycle]]·[[05.event-store-mysql-table]]·[[10.event-schema-evolution]] |
| 5 | [[RFC-005-pii-security]] | PII·보안 | 🔴 Open | [[11.es-pii-crypto-shredding]] |
| 6 | [[RFC-006-saga-process-manager]] | Saga·프로세스 매니저 | 🔴 Open | [[06-consistency-and-sagas]]·[[08.saga-orchestration-vs-choreography]] |
| 7 | [[RFC-007-deployment-infra-ops]] | 배포·인프라·운영 (K8s/Strimzi) | 🔴 Open | [[09-deployment-runtime]]·[[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]] |
| 8 | [[RFC-008-observability]] | 관측성 | 🔴 Open | [[10-observability]] |
| 9 | [[RFC-009-testing-quality-gates]] | 테스트·품질 게이트 | 🔴 Open | [[11-environments-and-testing]]·[[14.testing-strategy]] |
| 10 | [[RFC-010-module-structure-migration]] | 모듈 구조·마이그레이션 확정 | 🔴 Open | [[01-module-structure]]·[[04-migration]]·[[06.strangler-migration]]·[[07.command-domain-jpa-separation]] |
| 11 | [[RFC-011-projection-rebuild-catchup]] | 프로젝션 재구축·catch-up 운영 | 🔴 Open | [[03-read-model]] |
| 12 | [[RFC-012-command-query-api-contract]] | command/query API 계약·비동기 command | ✅ Closed · 2026-06-23 | 신규 design_doc + ADR |
| 13 | [[RFC-013-data-migration-genesis-events]] | V1→V2 데이터 이행(제네시스 이벤트) | ✅ Closed · 2026-06-23 | [[04-migration]] |
| 14 | [[RFC-014-aggregate-concurrency-control]] | 애그리거트 동시성·쓰기 경합 제어 | 🔴 Open | [[05-aggregate-design]] + 신규 ADR |
| 15 | [[RFC-015-authorization-model]] | V2 인가 모델 | 🔴 Open | 신규 design_doc + ADR |
| 16 | [[RFC-016-payment-integration-boundary]] | 결제 연동 경계 (payment ACL) | 🏷 합의됨 (2026-06-16) | [[06-consistency-and-sagas]] + 신규 design_doc·ADR |
| 17 | [[RFC-017-disaster-recovery-event-store]] | 재해 복구·이벤트 스토어 복구 의미론 | 🔴 Open | [[08-event-store-lifecycle]] + 신규 ADR |
| 18 | [[RFC-018-caching-redis-role]] | 캐싱·Redis의 V2 역할 | ✅ Closed · 2026-06-23 | [[03-read-model]] + 신규 ADR |
| 19 | [[RFC-019-auth-token-transport]] | 인증 토큰 transport·무상태성과 폐기 포기 | ✅ Closed · 2026-06-23 | 신규 design_doc(인증 토큰) + ADR |
| 20 | [[RFC-020-authentication-boundary-gateway]] | 인증 경계: API 게이트웨이 + 인증 서버 | ✅ Closed · 2026-06-23 | [[09-deployment-runtime]] 워크로드 토폴로지 보강 + ADR |
| 21 | [[RFC-021-event-identity-and-global-ordering]] | 이벤트 정체성·글로벌 순서 | 🔴 Open | [[07-messaging-topology]]·[[08-event-store-lifecycle]] |

## 열려있는 RFC (우선순위 순)

EDA/ES 코어 → 전환 실무 → K8s/운영 → 품질 → 라운드3 하류 순.

1. [[RFC-004-event-store-schema-evolution]] — 이벤트 스토어·스키마 진화
2. [[RFC-003-messaging-delivery]] — 메시징·전달 보장
3. [[RFC-011-projection-rebuild-catchup]] — 프로젝션 재구축·catch-up 운영
4. [[RFC-002-read-model-consistency]] — 읽기 모델·일관성
5. [[RFC-006-saga-process-manager]] — Saga·프로세스 매니저
6. [[RFC-005-pii-security]] — PII·보안
7. [[RFC-010-module-structure-migration]] — 모듈 구조·마이그레이션 확정
8. [[RFC-007-deployment-infra-ops]] — 배포·인프라·운영 (K8s/Strimzi)
9. [[RFC-008-observability]] — 관측성
10. [[RFC-009-testing-quality-gates]] — 테스트·품질 게이트
11. [[RFC-014-aggregate-concurrency-control]] — 애그리거트 동시성 제어
12. [[RFC-015-authorization-model]] — V2 인가 모델
13. [[RFC-017-disaster-recovery-event-store]] — 재해 복구
14. [[RFC-021-event-identity-and-global-ordering]] — 이벤트 정체성·글로벌 순서

## 진행 규칙

- **WIP=1** — 한 번에 한 RFC만 열린다
- 한 RFC가 닫히면: design_doc 보강 + 신규/개정 ADR + 본문 상태 `Closed · 합의`

## 관련 문서

- [[RFC-001-v2-cqrs-and-event-sourcing]] · [[index]]
