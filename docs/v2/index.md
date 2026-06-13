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

## RFC (rfc/)
- [[RFC-001-v2-cqrs-and-event-sourcing]] — V2 방향 결정의 서사

## ADR (adr/) — V2 트랙, 01부터
- [[01.cqrs-command-query-module-split]] — command/query 모듈 분리
- [[02.selective-event-sourcing-scope]] — 선택적 ES 범위
- [[03.command-hexagonal-query-layered]] — 아키텍처 비대칭
- [[04.read-model-projection-and-replica]] — 읽기 모델 전략
- [[05.event-store-mysql-table]] — 이벤트 스토어 구현
- [[06.strangler-migration]] — 점진 마이그레이션
- [[07.command-domain-jpa-separation]] — DDD/JPA 엔티티 분리 유지 (도메인 순수성)

## 설계 (design_doc/)
- [[00-design-overview]] — 목표 아키텍처 개요
- [[01-module-structure]] — 모듈·패키지 트리, 의존성 규칙
- [[02-write-model]] — ES / 상태+Outbox 쓰기 모델
- [[03-read-model]] — 프로젝션·replica 읽기 모델
- [[04-migration]] — 점진 전환 순서 (한 번에 하나씩)
- [[05-aggregate-design]] — 애그리거트 설계·도메인 로직 배치 (취약점 W-1 대응)

## 계승 (v1)
- [[07.reservation]] — Kafka 기반 Timetable·Reservation EDA (Outbox·Zero Payload·PoisonMessage)
- [[02.hexagonal]] — 포트와 어댑터
- [[01.ddd]] — 도메인/JPA 엔티티 분리

## 선행 작업 / 후속
- **이벤트 스토밍 재실시** — 기존 보드(`eventstoming.excalidraw`)는 입문기 산출물, 참고용. 도메인 이벤트 카탈로그 확정의 선행.
- 컨텍스트별 도메인 이벤트 카탈로그 (TBD).
- 단계별 구현은 [[06.strangler-migration]] 순서대로 **별도 사이클**.
