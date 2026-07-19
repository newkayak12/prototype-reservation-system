# V2 Analysis — 03. Open Decisions (논의용)

> 본 문서는 **결정이 아니라 미결정 사항**을 명시한다. 설계 단계(`docs/v2/design_doc/`)에서 합의 후 ADR로 확정한다.
> 핵심 질문: **(A) 무엇을 진짜 ES로, (B) 나머지는 어떻게, (C) 그때 Read/Write 모델은 어떻게.**

---

## ✅ 결정 요약 (2026-06-12 합의) — 상세는 `docs/v2/design_doc/`

| # | 결정 | 상세 |
|---|------|------|
| A. ES 범위 | **`reservation` · `timetable` · `restaurant` = 진짜 ES**(이벤트 스토어 + 리플레이). `schedule` · `user` · `authenticate` = 상태 + Outbox(CQRS, 비-ES). `menu` · `category` · `company` = 현행/lookup(구독 필요 시 Outbox) | [[02-write-model]] |
| B. 비-ES 처리 | 상태 기반 유지 + 통합 필요 시 Outbox 이벤트 발행(이벤트 드리븐 참여) | [[02-write-model]] |
| C. R/W 모델 | **command/query를 top-level Gradle 모듈로 분리.** `command` = hexagonal, `query` = layered, 도메인 = 패키지. query는 도메인 core **비의존**, `contract` 이벤트로만 소통 | [[01-module-structure]] |
| 읽기 전략 | 기본 = 이벤트 프로젝션(read model). 프로젝션 미적용분은 **read replica / 공유 read 스키마(나)** 로 조회 — command 테이블 직접 조회 금지(replica/뷰 경유만) | [[03-read-model]] |
| 마이그레이션 | Strangler 점진(권고, 미확정) — timetable 선행을 템플릿으로 | [[04-migration]] |

> 아래 본문은 합의에 이른 **논의 과정·옵션·근거**로 보존한다.

## Decision A — 이벤트 소싱 적용 범위 (컨텍스트별)

세 목표는 컨텍스트마다 강도가 다를 수 있다. 후보 분류(초안):

| 컨텍스트 | 변화 빈도 / 감사 필요성 | 후보 적용 강도 |
|----------|------------------------|----------------|
| `reservation` (핵심) | 높음 / 높음(예약·취소 이력) | **진짜 ES** 후보 1순위 |
| `timetable` | 높음 / 높음(점유 충돌) | 이미 이벤트 보유 → **진짜 ES** 후보 |
| `schedule` | 중간 | ES 또는 CQRS+Outbox |
| `restaurant` | 중간 / 낮음 | CQRS + Outbox 이벤트 |
| `user` / `authenticate` | 중간 / 일부 높음(보안 감사) | CQRS + Outbox, 일부 이벤트 |
| `menu` | 낮음 | CQRS 또는 현행 유지 |
| `category` (lookup) | 매우 낮음 | **현행 유지**(ES 과잉) |
| `company` | 낮음 | 현행 유지 / CQRS |

> ⚠️ 위 표는 **제안 초안**이며 합의 전이다. "전부 ES"는 lookup성 컨텍스트에 과잉, "전부 현행"은 목표 미달.

### 후보 전략
- **A1. 선택적 ES**: 핵심(`reservation`,`timetable`)만 진짜 ES, 나머지는 CQRS+Outbox / 현행. (위험·비용 최소, timetable 패턴 확장)
- **A2. 전면 ES**: 9개 전부 이벤트 스토어가 진실의 원천. (최대 일관성/감사성, 비용 매우 높음)
- **A3. CQRS 먼저, ES 후속**: V2는 R/W 분리 + Outbox 이벤트까지, ES는 다음 사이클.

## Decision B — "ES 아닌" 컨텍스트의 처리

ES를 안 쓰는 컨텍스트도 V2 목표(이벤트 드리븐)에 참여해야 하는가?

- **B1. 상태 기반 + Outbox 이벤트**: 현재처럼 상태를 저장하되, 상태 변경 시 Outbox로 통합 이벤트 발행. (timetable 패턴과 동일, 컨텍스트 간 이벤트 드리븐 달성)
- **B2. 완전 현행 유지**: lookup성만 — 이벤트조차 안 냄.
- 결정 기준: "다른 컨텍스트가 이 변화를 구독해야 하는가?" → 예면 B1, 아니면 B2.

## Decision C — Read / Write 모델 (전략별로 다름)

| 모델 | 쓰기(Write) | 읽기(Read) | R/W DB |
|------|-------------|-----------|--------|
| **C1. 같은 DB, 분리 모델** | 정규화 테이블/이벤트 | 별도 read 테이블·뷰(프로젝션) | 단일 DB, 스키마 분리 |
| **C2. 분리 DB** | 이벤트 스토어(append-only) | 별도 조회 DB(RDB/문서/캐시) | 물리 분리 |
| **C3. 동기 프로젝션 vs 비동기 프로젝션** | — | 커밋 시 동기 갱신 vs 이벤트 구독 비동기 갱신 | — |

미해결 하위 질문:
1. ES 컨텍스트의 쓰기 저장소: **전용 이벤트 스토어 제품(EventStoreDB/Axon 등)** vs **MySQL에 이벤트 테이블 직접 구현**?
2. 읽기 모델 갱신: **동기**(읽기 일관성↑, 결합↑) vs **비동기 프로젝션**(확장성↑, 최종 일관성)?
3. 읽기 저장소 종류: MySQL read replica · 별도 조회 스키마 · Redis · 검색엔진 중?
4. 비-ES 컨텍스트도 read projection을 둘 것인가, 아니면 기존 QueryDSL 조회를 유지할 것인가?

## Decision D — 마이그레이션 전략 (참고)

- **D1. 점진(Strangler)**: 컨텍스트 단위로 하나씩 전환, 기존과 병행. (현재 브랜치 + timetable 선행과 부합)
- **D2. 빅뱅**: V2 일괄 전환. (위험 높음)

---

## 다음 단계
이 문서의 A·B·C를 논의로 좁힌 뒤, 합의된 결정을 `docs/v2/design_doc/` 설계 문서와 `docs/v2/adr/` 로 옮긴다.
