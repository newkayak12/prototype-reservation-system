# RFC-INDEX — V2 결정 RFC 인덱스

- **성격**: 인덱스 (결정 없음, 추적 전용)
- **업데이트**: 2026-07-04

> RFC-001이 큰 그림을 잡았고, 이후 RFC들이 주제별 결정을 닫는다.
> 정독 순서는 아래 "열려있는 RFC" 섹션의 우선순위를 따른다.

## 상태 범례

| 기호 | 상태 | 의미 |
|---|---|---|
| 🔴 | 논의 중 | 아직 결정 전, 열려 있음 |
| 🏷 | 합의 | 결정 합의 완료 · ADR 비준 대기 |
| ✅ | 종결 | 합의 + ADR 비준/닫힘까지 완료 |
| 🔒 | 닫힘 | 결정 일부만 채택하고 닫음(나머지 불채택·이력 보존) |
| 📎 | 안 제시(미채택) | 안만 기록, 결정 아님 · 구현 비참조 (실제 수행 대상 아님) |

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
| 24 | [[RFC-024-domain-event-type-and-replay-layering]] | 도메인 이벤트 타입 소유·contract 매핑·리플레이 실행의 계층 분업 (트리아지 C03) | 🏷 합의 (2026-07-04) | [[DESIGN-019-event-execution-layering]]·[[DESIGN-002-module-structure]] |
| 25 | [[RFC-025-ordering-relay-dlq-reconciliation]] | aggregate 순서 계약 × relay 병렬성·DLQ 상호작용 봉합 (트리아지 C09) | 🏷 합의 (2026-07-04) | [[DESIGN-020-ordering-and-failure-handling]]·[[DESIGN-008-messaging-topology]] |
| 26 | [[RFC-026-migration-seam-rollback-proposal]] | V1→V2 마이그레이션 접합·롤백·성공기준·전환순서 (트리아지 C16·C45) | 📎 안 제시·미채택 (2026-07-05) | 없음 — 구현 비참조, DESIGN/ADR 없음 |
| 27 | [[RFC-027-payment-external-boundary-proposal]] | 결제 외부 경계(PG) 확정경로·멱등·공격면 (트리아지 C34·C35) | 📎 안 제시·미채택 (2026-07-05) | 우리 쪽 멱등만 [[DESIGN-015-payment-integration]] §6.6 실결정 · 외부축 구현 비참조 |
| 28 | [[RFC-028-redis-fault-fallback-semantics]] | Redis 장애 폴백 시맨틱·단일 인스턴스 순서 정합 (트리아지 C40) | 🏷 합의 (2026-07-05) | [[DESIGN-018-caching]] §6.1·§7·§8 |
| 29 | [[RFC-029-event-carried-payload-uniform]] | 이벤트 페이로드 = event-carried 일원화, Zero Payload 폐기 (트리아지 C02) | 🏷 합의 (2026-07-05) | [[DESIGN-003-write-model]] §4.4·용어집 |
| 30 | [[RFC-030-read-freshness-command-response-contract]] | 읽기 신선도 계약: 동기 권위 응답 기본(RFC-012 "202 기본" 부분 supersede) + `sequence_no` read-after-write 토큰 (트리아지 C14) | 🏷 합의 (2026-07-05) | [[DESIGN-013-api-contract]]·[[DESIGN-004-read-model]] (반영 대기) |

> ※ RFC 번호와 ADR 번호는 1:1이 아니다 — 이벤트 정체성·전역 순서는 RFC-021이 소유하지만 대응 ADR은 [[22.event-identity-and-global-ordering]](22번)다. (이전 RFC-022 결번은 RFC-023→022·RFC-024→023 당김으로 메워졌다.)

## 열려있는 RFC (우선순위 순)

열린 RFC 없음. 아래 🏷 합의 — Design 완료, ADR 비준 대기:
- [[RFC-024-domain-event-type-and-replay-layering]] (2026-07-04) — [[DESIGN-019-event-execution-layering]]
- [[RFC-025-ordering-relay-dlq-reconciliation]] (2026-07-04) — [[DESIGN-020-ordering-and-failure-handling]]
- [[RFC-028-redis-fault-fallback-semantics]] (2026-07-05) — [[DESIGN-018-caching]] §6.1·§7·§8

🏷 합의 — **Design 반영 대기**(design doc 정정 후 ADR 비준):
- [[RFC-030-read-freshness-command-response-contract]] (2026-07-05) — [[DESIGN-013-api-contract]](202→동기 권위 응답)·[[DESIGN-004-read-model]](신선도 등급·토큰). RFC-012 "202 기본" 부분 supersede.
- [[RFC-029-event-carried-payload-uniform]] (2026-07-05) — [[DESIGN-003-write-model]] §4.4·용어집

> 📎 [[RFC-026-migration-seam-rollback-proposal]] (C16) 는 결정 트랙이 아니다 — 실제 이행 대상이 없어 **안만 제시·미채택**. DESIGN/ADR 없음, 구현 비참조.
> 📎 [[RFC-027-payment-external-boundary-proposal]] (C34·C35) 외부-PG 축은 log 대체·내부-only 전제라 **안만 제시·미채택**. 단 "우리 쪽 outbox 멱등"은 미룸 아님 — [[DESIGN-015-payment-integration]] §6.6 실결정으로 닫힘.

> ✅ RFC-021(닫힘)·RFC-011 합의 완료 (2026-06-25) · RFC-022 종결 (2026-06-30) — 게이트·정합 확인 후 닫음.

## 진행 규칙

- **WIP=1** — 한 번에 한 RFC만 열린다
- 한 RFC가 닫히면: design_doc 보강 + 신규/개정 ADR + 본문 상태 `✅ 종결`

## 관련 문서

- [[RFC-001-v2-cqrs-and-event-sourcing]] · [[index]]
