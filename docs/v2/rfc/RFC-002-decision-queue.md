# RFC-002 — V2 결정 큐 (라운드2 인덱스)

- **상태**: Open (작업 큐) · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] (라운드1 — CQRS+ES+EDA 큰 그림)
- **성격**: 결정 문서가 아니라 **인덱스**다. 라운드1이 *큰 그림·메커니즘*을 잠갔고, 그 아래 구체 결정 ~65건이 design_doc·ADR 곳곳에 흩어져 있었다. 이걸 **주제별 RFC로 쪼개** 하나씩 논의 → design_doc 보강 / ADR로 닫는다. **미루는 게 아니라 작업 큐다.**

## 왜 이렇게 쪼갰나

[[RFC-001-v2-cqrs-and-event-sourcing]]은 비정상적으로 넓었다 — CQRS 모듈 분리 + 선택적 ES + 이벤트 드리븐을 한 RFC로 다뤄 design_doc 12개·ADR 14개를 한 번에 쏟아냈다. 그 부작용으로 "메커니즘은 정했는데 구체는 안 정한" 미결이 문서마다 박혔다. 라운드2는 반대로 간다 — **좁은 주제 RFC → 그 자리에서 논의해 결정 → design_doc/ADR 배출.** 정상 흐름이다.

## 닫는 방식 (범례)

- **논의** — 토론으로 지금 결정 가능 (대부분).
- **측정 트리거** — *정책은 지금* 결정(초기값 + 재검토 조건), *숫자는* 운영/부하 측정으로 튜닝. "미룸"이 아니다.
- 🌱 **스토밍 선행** — 이벤트가 뭔지 모르면 책상에서 못 정함. 이벤트 스토밍 후. (전체에서 4건뿐)

## 주제 RFC

| RFC | 주제 | 항목 | 닫으면 보강할 곳 | 상태 |
|---|---|---|---|---|
| [[RFC-003-read-model-consistency]] | 읽기 모델·일관성 | 7 | [[03-read-model]]·[[04.read-model-projection-and-replica]] | Open |
| [[RFC-004-messaging-delivery]] | 메시징·전달 보장 | 7 | [[07-messaging-topology]]·[[09.event-ordering-and-delivery-guarantee]] | Open |
| [[RFC-005-event-store-schema-evolution]] | 이벤트 스토어·스키마 진화 | 12 | [[08-event-store-lifecycle]]·[[05.event-store-mysql-table]]·[[10.event-schema-evolution]] | Open |
| [[RFC-006-pii-security]] | PII·보안 | 6 | [[11.es-pii-crypto-shredding]] | Open |
| [[RFC-007-saga-process-manager]] | Saga·프로세스 매니저 | 4 | [[06-consistency-and-sagas]]·[[08.saga-orchestration-vs-choreography]] | Open |
| [[RFC-008-deployment-infra-ops]] | 배포·인프라·운영 | 12 | [[09-deployment-runtime]]·[[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]] | Open |
| [[RFC-009-observability]] | 관측성 | 6 | [[10-observability]] | Open |
| [[RFC-010-testing-quality-gates]] | 테스트·품질 게이트 | 6 | [[11-environments-and-testing]]·[[14.testing-strategy]] | Open |
| [[RFC-011-module-structure-migration]] | 모듈 구조·마이그레이션 확정 | 5 | [[01-module-structure]]·[[04-migration]]·[[06.strangler-migration]]·[[07.command-domain-jpa-separation]] | Open |

> 각 RFC 본문이 **그 주제의 미결 전체 + 옵션 + 출처**를 담는다. 이 표는 지도일 뿐.

## RFC로 빼지 않는 것

- 🌱 **컨텍스트별 도메인 이벤트 카탈로그**(목록·페이로드·버전) — 이벤트 스토밍 재실시가 선행. RFC 아닌 **별도 워크숍**. 이게 풀려야 토픽 목록·ES 경계·PII 필드 분류·예약 외 흐름 분류가 닫힌다. → 의존 항목은 각 RFC에 🌱로 표기.
- **호스팅 선택**(RDS vs 자가 / ElastiCache vs 자가) — 앱 무관, **배포 사이클** 위임.
- **운영 백로그**(CI/CD·GitOps·SLO·KEDA·DR 런북·이미지 스캐닝) — [[index|docs/todo]] 백로그.

## 메타 — ADR 비준

ADR **08~14가 전부 `Proposed`**(미비준)다. 각 주제 RFC를 닫을 때, 관련 ADR을 함께 `Accepted`로 승급한다(또는 개정). 비준 자체가 결정 큐의 일부.

## 진행 규칙

- **WIP=1 권장** — 한 번에 한 주제 RFC만 연다(읽기↔메시징처럼 강결합이면 함께).
- 권장 순서: **003(읽기 모델)** → 004(메시징) → 005(스토어) … (RFC-001이 가장 엉성하게 남긴 게 읽기 모델).
- 한 RFC가 닫히면: design_doc 보강 + 신규/개정 ADR + 이 표 상태 `Resolved`.

## 관련 문서
- [[RFC-001-v2-cqrs-and-event-sourcing]] · [[index]]
