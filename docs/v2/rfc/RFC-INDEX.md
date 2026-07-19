# RFC-INDEX — V2 결정 RFC 인덱스

- **성격**: 인덱스 (결정 없음, 추적 전용)
- **업데이트**: 2026-07-19

> RFC-001이 큰 그림을 잡았고, 이후 RFC들이 주제별 결정을 닫는다.
> 정독 순서는 아래 "열려있는 RFC" 섹션의 우선순위를 따른다.

## 상태 범례

| 기호 | 상태 | 의미 |
|---|---|---|
| 🔴 | 논의 중 | 아직 결정 전, 열려 있음 |
| 🏷 | 합의 | 결정 합의 완료 · ADR 비준 대기 |
| ✅ | 종결 | 합의 + ADR 비준/닫힘까지 완료 |
| 🔒 | 닫힘 | 결정 일부만 채택하고 닫음(나머지 불채택·이력 보존) |

## RFC 전체 목록

| # | RFC | 주제 | 상태 | 닫으면 보강할 곳 |
|---|---|---|---|---|
| 1 | [[RFC-001-v2-cqrs-and-event-sourcing]] | V2 방향 결정 (큰 그림 라운드1) | 🏷 합의 (2026-06-12) | 기준 문서 |
| 2 | [[RFC-002-read-model-consistency]] | 읽기 모델·일관성 | 🏷 합의 (2026-06-21) | [[03-read-model]]·[[04.read-model-projection-and-replica]] |
| 3 | [[RFC-003-messaging-delivery]] | 메시징·전달 보장 | 🏷 합의 (2026-06-21) | [[07-messaging-topology]]·[[09.event-ordering-and-delivery-guarantee]] |
| 4 | [[RFC-004-event-store-schema-evolution]] | 이벤트 스토어·스키마 진화 (진화는 [[RFC-022-event-schema-evolution]] 분리) | 🏷 합의 (2026-06-21) | [[08-event-store-lifecycle]]·[[05.event-store-mysql-table]] |
| 5 | [[RFC-005-pii-security]] | PII·보안 | 🏷 합의 (2026-06-21) | [[15-pii-security]]·[[11.es-pii-crypto-shredding]] |
| 6 | [[RFC-006-saga-process-manager]] | Saga·프로세스 매니저 | 🏷 합의 (2026-06-21) | [[06-consistency-and-sagas]]·[[08.saga-orchestration-vs-choreography]] |
| 7 | [[RFC-007-deployment-infra-ops]] | 배포·인프라·운영 (K8s/Strimzi) | 🏷 합의 (2026-06-21) | [[09-deployment-runtime]]·[[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]] |
| 8 | [[RFC-008-observability]] | 관측성 | 🏷 합의 (2026-06-21) | [[10-observability]] |
| 9 | [[RFC-009-testing-quality-gates]] | 테스트·품질 게이트 (이벤트 계약은 [[RFC-023-event-schema-contract-management]] 분리) | 🏷 합의 (2026-06-23) | [[11-environments-and-testing]]·[[14.testing-strategy]] |
| 10 | [[RFC-010-module-structure-migration]] | 모듈 구조·마이그레이션 확정 | 🏷 합의 (2026-06-23) | [[01-module-structure]]·[[04-migration]]·[[06.strangler-migration]]·[[07.command-domain-jpa-separation]] |
| 11 | [[RFC-011-projection-rebuild-catchup]] | 프로젝션 재구축·catch-up 운영 | 🏷 합의 (2026-06-25) | [[03-read-model]] |
| 12 | [[RFC-012-command-query-api-contract]] | command/query API 계약·비동기 command | ✅ 종결 (2026-06-23) | [[12-api-contract]] |
| 13 | [[RFC-013-data-migration-genesis-events]] | V1→V2 데이터 이행(제네시스 이벤트) | ✅ 종결 (2026-06-23) | [[04-migration]] |
| 14 | [[RFC-014-aggregate-concurrency-control]] | 애그리거트 동시성·쓰기 경합 제어 | 🏷 합의 (2026-06-17) · 비관 락 전환 | [[05-aggregate-design]]·[[16.optimistic-concurrency-control]] |
| 15 | [[RFC-015-authorization-model]] | V2 인가 모델 | ✅ 종결 (2026-06-16) · [[17.authorization-model]] | [[13-authorization]] |
| 16 | [[RFC-016-payment-integration-boundary]] | 결제 연동 경계 (payment ACL) | ✅ 종결 (2026-06-16) · [[15.payment-acl-boundary]] | [[14-payment-integration]]·[[06-consistency-and-sagas]] |
| 17 | [[RFC-017-disaster-recovery-event-store]] | 재해 복구·이벤트 스토어 복구 의미론 | ✅ 종결 (2026-06-16) · [[18.event-store-recovery-semantics]] | [[08-event-store-lifecycle]] |
| 18 | [[RFC-018-caching-redis-role]] | 캐싱·Redis의 V2 역할 | ✅ 종결 (2026-06-23) | [[17-caching]]·[[19.caching-redis-role]] |
| 19 | [[RFC-019-auth-token-transport]] | 인증 토큰 transport·무상태성과 폐기 포기 | ✅ 종결 (2026-06-23) | [[16-auth-token]]·[[20.auth-token-transport]] |
| 20 | [[RFC-020-authentication-boundary-gateway]] | 인증 경계: 엣지 1회 검증(기성 프록시 무상태) + 인증 서버=Spring AS(로그인/로그아웃·JTI 집중) | ✅ 종결 (2026-06-30) | [[09-deployment-runtime]] 워크로드 토폴로지 보강 |
| 21 | [[RFC-021-event-identity-and-global-ordering]] | 이벤트 정체성·전역 순서 (`event_id`만 채택, `global_seq` 불채택) | 🔒 닫힘 (2026-06-30) | [[22.event-identity-and-global-ordering]] |
| 22 | [[RFC-022-event-schema-evolution]] | 이벤트 스키마 진화 (업캐스팅·타입 레지스트리·스키마 포맷) | ✅ 종결 (2026-06-30) | [[08-event-store-lifecycle]]·[[10.event-schema-evolution]] |
| 23 | [[RFC-023-event-schema-contract-management]] | 이벤트 스키마 관리: 생산자·소비자 계약 (공유 통합-이벤트 모듈·계약 테스트) | ✅ 종결 (2026-06-30) | [[11-environments-and-testing]]·[[14.testing-strategy]] |
| 31 | [[RFC-031-architecture-fitness-functions-archunit]] | 아키텍처 적합성 함수 (ArchUnit) — V2 모듈 경계·순수성·컨텍스트 격리 자동 강제 | 🌱 초안 | [[00-module-index]]·[[03-command-core]] + 규칙 design_doc |

> ※ RFC 번호와 ADR 번호는 1:1이 아니다 — 이벤트 정체성·전역 순서는 RFC-021이 소유하지만 대응 ADR은 [[22.event-identity-and-global-ordering]](22번)다. (이전 RFC-022 결번은 RFC-023→022·RFC-024→023 당김으로 메워졌다.)

## 열려있는 RFC (우선순위 순)

- [[RFC-031-architecture-fitness-functions-archunit]] 🌱 초안 — V2 모듈 구조([[00-module-index]] §2)의 경계·순수성·컨텍스트 격리를 ArchUnit fitness function으로 자동 강제. 강제 시점은 Phase 7.

> ✅ RFC-021(닫힘)·RFC-011 합의 완료 (2026-06-25) · RFC-022 종결 (2026-06-30) — 게이트·정합 확인 후 닫음.
> 🌱 RFC-031 신규 오픈 (WIP=1) — 이전 전 RFC 닫힘 이후 첫 후속 결정.

## 진행 규칙

- **WIP=1** — 한 번에 한 RFC만 열린다
- 한 RFC가 닫히면: design_doc 보강 + 신규/개정 ADR + 본문 상태 `✅ 종결`

## 관련 문서

- [[RFC-001-v2-cqrs-and-event-sourcing]] · [[index]]
