# V2 — Map of Content (MOC)

> V1 → V2 전환(CQRS 모듈 분리 + 선택적 이벤트 소싱 + 이벤트 드리븐)의 문서 허브.
> 사이클: `20260612-v2-cqrs-es-architecture` (exploration). 톤·결정은 v1 ADR을 계승.

## 읽는 순서

1. **분석** — 왜 바꾸는가: [[02-domain-limitations]]
2. **RFC** — 무엇을 어떻게 결정했나(서사): [[RFC-001-v2-cqrs-and-event-sourcing]]
3. **ADR** — 결정별 근거(원자적): 아래 ADR 목록
4. **설계** — 어떻게 짓는가: [[00-design-overview]]

## 분석 (analysis/)
- [[00-overview]] — 목표·구성·결론
- [[01-current-state]] — 현 아키텍처·9개 컨텍스트·기존 이벤트 인프라
- [[02-domain-limitations]] — 이벤트 소싱 관점 한계 (빈약 도메인 등)
- [[03-open-decisions]] — 논의 과정·옵션·합의 기록
- [[04-design-completeness-audit]] — **전수 감사(2026-06-16)**: 6/6 N·횡단 미결 7 + 선행 게이트(이벤트 카탈로그)·클러스터별 미결 체크리스트

## RFC (rfc/)
- [[RFC-001-v2-cqrs-and-event-sourcing]] — V2 방향 결정의 서사 (라운드1 — 큰 그림)
- [[RFC-INDEX]] — **라운드2 결정 큐 (인덱스)**: 미결 전체를 주제별 RFC로 쪼갠 맵 (우선순위 순)

주제 RFC — **정독·결정 순서**(EDA/ES 코어 → 전환 → K8s/운영 → 품질):
1. [[RFC-004-event-store-schema-evolution]] — 이벤트 스토어·스키마 진화
2. [[RFC-003-messaging-delivery]] — 메시징·전달 보장
3. [[RFC-011-projection-rebuild-catchup]] — 프로젝션 재구축·catch-up 운영
4. [[RFC-002-read-model-consistency]] — 읽기 모델·일관성
5. [[RFC-006-saga-process-manager]] — Saga·프로세스 매니저
6. [[RFC-012-command-query-api-contract]] — command/query API 계약·비동기 command
7. [[RFC-005-pii-security]] — PII·보안
8. [[RFC-010-module-structure-migration]] — 모듈 구조·마이그레이션 확정
9. [[RFC-013-data-migration-genesis-events]] — V1→V2 데이터 이행(제네시스 이벤트)
10. [[RFC-007-deployment-infra-ops]] — 배포·인프라·운영 (K8s/Strimzi)
11. [[RFC-008-observability]] — 관측성
12. [[RFC-009-testing-quality-gates]] — 테스트·품질 게이트

추가 주제 RFC — **라운드3**(기존 RFC가 열어놓고 안 닫은 실 + 아키텍처 결정면 보강):
13. [[RFC-014-aggregate-concurrency-control]] — 애그리거트 동시성·쓰기 경합 제어
14. [[RFC-015-authorization-model]] — V2 인가 모델
15. [[RFC-016-payment-integration-boundary]] — 결제 연동 경계 (payment ACL)
16. [[RFC-017-disaster-recovery-event-store]] — 재해 복구·이벤트 스토어 복구 의미론
17. [[RFC-018-caching-redis-role]] — 캐싱·Redis의 V2 역할
18. [[RFC-019-auth-token-transport]] — 인증 토큰 transport·무상태성·폐기 포기
19. [[RFC-020-authentication-boundary-gateway]] — 인증 경계: API 게이트웨이 + 인증 서버 (k3s 인클러스터)
20. [[RFC-021-event-identity-and-global-ordering]] — 이벤트 정체성(`event_id`, UUIDv7) (감사 ① 마감 · `global_seq`는 불채택·🔒닫힘) · 동시성 비관 전환은 [[RFC-014-aggregate-concurrency-control]] §재개
21. [[RFC-022-event-schema-evolution]] — 이벤트 스키마 진화: 업캐스터·eventType 매핑·Avro/Protobuf ([[RFC-004-event-store-schema-evolution]]에서 분리)

## ADR (adr/) — V2 트랙, 01부터
- [[01.cqrs-command-query-module-split]] — command/query 모듈 분리
- [[02.selective-event-sourcing-scope]] — 선택적 ES 범위
- [[03.command-hexagonal-query-layered]] — 아키텍처 비대칭
- [[04.read-model-projection-and-replica]] — 읽기 모델 전략
- [[05.event-store-mysql-table]] — 이벤트 스토어 구현
- [[06.strangler-migration]] — 점진 마이그레이션
- [[07.command-domain-jpa-separation]] — DDD/JPA 엔티티 분리 유지 (도메인 순수성)
- [[08.saga-orchestration-vs-choreography]] — 컨텍스트 간 조율: 코레오그래피 기본 (오케스트레이션·혼합 기각)
- [[09.event-ordering-and-delivery-guarantee]] — 순서(파티션 키=aggregate_id)·at-least-once+멱등
- [[10.event-schema-evolution]] — 읽기 시 업캐스팅·eventVersion 버저닝 (in-place 마이그레이션 기각)
- [[11.es-pii-crypto-shredding]] — append-only PII 삭제: 크립토 셰딩 + PII 최소화
- [[12.kafka-hosting-msk-vs-self-managed]] — Kafka 호스팅: self-managed Strimzi (MSK 실사용 불가·운영 학습)
- [[13.db-hosting-and-read-write-topology]] — R/W 물리 분리: binlog 복제 + 모듈별 datasource(라우팅 없음)·호스팅 투명
- [[14.testing-strategy]] — 정확성·경계·진화 테스트, 아키텍처를 ArchUnit/Konsist로 강제
- [[15.payment-acl-boundary]] — 결제 연동 경계: payment=ACL(상태+Outbox)·사가 3 이벤트 동결·실 PG 별도 사이클 연기
- [[16.optimistic-concurrency-control]] — **동시성(2026-06-17 비관 전환)**: Redisson 분산 락(1차)+DB 비관 락(Redis 다운 폴백)+`(aggregate_id, sequence_no)` UNIQUE 백스톱·전역 락 없음·교차는 사가
- [[17.authorization-model]] — 인가: 역할=엣지·소유권=앱(애그리거트 불변식)·query 스코프 조건·프로젝션 스코프 키
- [[18.event-store-recovery-semantics]] — 복구 의미론: 이벤트 스토어 1급 보호·되감기 없음(보상 이벤트)·셰딩 복원 견딤
- [[19.caching-redis-role]] — Redis=읽기 캐시 아님, 분산 조정·휘발성 상태 전용(단일 durability)
- [[20.auth-token-transport]] — 인증 토큰: transport V1 계승+SameSite·무상태 refresh·즉시 폐기 포기
- [[22.event-identity-and-global-ordering]] — 이벤트 정체성: `event_id`(UUIDv7 공통 dedup/causation 앵커 + 재구축 keyset 커서 겸용, 전순서 아님)·adr/05 스키마 보강 (`global_seq` 불채택)

## 설계 (design_doc/)
- [[00-design-overview]] — 목표 아키텍처 개요
- [[01-module-structure]] — 모듈·패키지 트리, 의존성 규칙
- [[02-write-model]] — ES / 상태+Outbox 쓰기 모델
- [[03-read-model]] — 프로젝션·replica 읽기 모델
- [[04-migration]] — 점진 전환 순서 (한 번에 하나씩)
- [[05-aggregate-design]] — 애그리거트 설계·도메인 로직 배치 (취약점 W-1 대응)
- [[06-consistency-and-sagas]] — 일관성 경계(애그리거트=트랜잭션)·사가/프로세스 매니저
- [[07-messaging-topology]] — 토픽 분할·파티션 키·컨슈머 그룹·전달 보장
- [[08-event-store-lifecycle]] — 스냅샷·정합성 검증·보존/파티셔닝·temporal 조회
- [[09-deployment-runtime]] — EKS 5개 워크로드 토폴로지·배포/런타임 뷰
- [[10-observability]] — correlation/causation id 전파 규약·추적 메타데이터 슬롯
- [[11-environments-and-testing]] — compose/k3s/k6 세 축·테스트 피라미드·로컬 패리티
- [[12-api-contract]] — command/query 외부 인터페이스 (202 기본·서버측 멱등·한 표면)
- [[13-authorization]] — 인가 모델 (역할=엣지·소유권=앱·프로젝션 스코프 키)
- [[14-payment-integration]] — 결제 연동 경계 (그린필드·외부 PG·ACL)
- [[15-pii-security]] — 크립토 셰딩 (주체별 키·전용 스키마·blind index)
- [[16-auth-token]] — 인증 토큰 transport·무상태 refresh·폐기 포기
- [[17-caching]] — Redis 역할: 읽기 캐시 아님·분산 조정/휘발성 상태 전용·단일 durability

## 계승 (v1)
- [[07.reservation]] — Kafka 기반 Timetable·Reservation EDA (Outbox·Zero Payload·PoisonMessage)
- [[02.hexagonal]] — 포트와 어댑터
- [[01.ddd]] — 도메인/JPA 엔티티 분리

## 선행 작업 / 후속
- **이벤트 스토밍 재실시** — 기존 보드(`eventstoming.excalidraw`)는 입문기 산출물, 참고용. 도메인 이벤트 카탈로그 확정의 선행.
- 컨텍스트별 도메인 이벤트 카탈로그 (TBD).
- 단계별 구현은 [[06.strangler-migration]] 순서대로 **별도 사이클**.
