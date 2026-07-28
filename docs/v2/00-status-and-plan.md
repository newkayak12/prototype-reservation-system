# V2 — 작업 현황 · 플랜 (Status & Plan)

> 이 문서가 V2 전환 사이클의 **현행 진행 상태 SSOT**다. (구 `00-roadmap.md`는 `archive/`로 동결)
> 사이클: `20260612-v2-cqrs-es-architecture` (Exploration) · 문서 허브(MOC): [[index]]
> 최종 갱신: 2026-07-28

---

## 1. 한눈에 보기

**설계 수렴 완료 · 구현 미착수.** 라운드1~3 RFC, Design Doc, ADR, 모듈 설계가 모두 확정 단계에 있고, 적대적 검증(devil's advocate) 트리아지 **C-1~C-7 전부 종결**됐다. 마지막까지 열려 있던 C-1(event_store·outbox 원자성)은 [[ADR-027-event-store-outbox-atomicity]]로 닫혔다.

남은 것은 문서 결함이 아니라 **계획된 후속 단계**다 — 이벤트 스토밍 재실시, ADR 승인 전환, Phase 7 구현(별도 사이클).

---

## 2. 산출물 현황

| 영역 | 상태 | 산출물 | 진실원 |
|---|---|---|---|
| 분석 (`analysis/`) | 완료 | 전수 감사 · 취약점 트리아지 · K8s/k6/이벤트전달/스키마 심화 스터디 (10편) | ✅ |
| RFC (`rfc/`) | 완료 | RFC-001~034 (34편, 서사·결정 큐) | ✅ |
| 설계 (`design_doc/`) | 완료 | DESIGN-001~020 (20편) | ✅ |
| ADR (`adr/`) | 완료 (상태 `Proposed`) | ADR-001~027 (26편, ADR-025는 조건부 보류) | ✅ |
| 모듈 (`modules/`) | 완료 | 00~12 (모듈 허브 + 구현 계획) | ✅ |
| 도메인 (`domain/`) | 진행 | 도메인 재설계 문서 (9편) | ✅ |

> `Proposed → Accepted` 전환은 사용자 권한이라, ADR은 완료돼도 상태는 Proposed로 남는다.

---

## 3. 확정 설계 요약

CQRS로 command / query를 모듈 분리하고, **선택적 이벤트 소싱**을 적용한다 — `reservation`·`timetable`·`restaurant`는 ES, `schedule`·`user`·`authenticate`는 상태+Outbox. 이벤트 스토어는 MySQL append-only `event_store` 테이블([[ADR-005-event-store-mysql-table]])이고, **event_store append와 contract outbox insert를 동일 datasource·동일 트랜잭션으로 묶어**(불변식 `I-OUTBOX-1`, [[ADR-027-event-store-outbox-atomicity]]) dual-write를 차단한다. 성장 시 이 결합은 **CDC(Debezium binlog) 졸업 경로**로 해소한다. 동시성은 비관 락(Redisson + DB 폴백 + `(aggregate_id, sequence_no)` UNIQUE 백스톱, [[ADR-016-optimistic-concurrency-control]]), 컨텍스트 간 조율은 코레오그래피 사가([[ADR-008]]), 이벤트 순서는 파티션 키=`aggregate_id` + at-least-once + 멱등([[ADR-009]])이다.

전체 결정 색인은 [[index]] 참조.

---

## 4. 열린 항목 · 다음 단계

| 항목 | 성격 | 상태 |
|---|---|---|
| 이벤트 스토밍 재실시 → 도메인 이벤트 카탈로그 확정 | 선행 작업 | **TBD** (기존 보드는 입문기 산출물, 참고용) |
| ADR `Proposed → Accepted` 전환 | 승인 | 사용자 권한 (미착수) |
| Phase 7 구현 — 모듈 재편 · ES 인프라 · 첫 애그리거트 전환 | 구현 | **별도 사이클** (미착수) |
| `I-OUTBOX-1` 강제 테스트 · 졸업 트리거 수치 | 구현 | Phase 7에서 |
| CDC 졸업 (Debezium binlog tailing) | 미래 | 트리거 관측 시 ([[ADR-027-event-store-outbox-atomicity]]) |

---

## 5. 적대적 검증 트리아지 종결 현황

| 항목 | 주제 | 종결 |
|---|---|---|
| C-1 | event_store·outbox 원자성 | [[ADR-027-event-store-outbox-atomicity]] (2026-07-28) |
| C-2 | 이벤트 순서 계약 | RFC-032 |
| C-3~C-7 | (동시성·스키마·복구 등) | 각 RFC/ADR로 해소 |

전 항목 종결. 상세는 [[06-design-weakness-triage]] 및 [[12-implementation-plan]] §3 참조.

---

## 6. 구현 로드맵 (Phase)

> 상세 착수 순서·태스크 분해는 [[12-implementation-plan]], 마이그레이션 전략은 [[DESIGN-005]] · [[ADR-006]](Strangler).

| Phase | 내용 | 상태 |
|---|---|---|
| 1~2 | V1 분석 · 전환 포인트 | 완료 (문서 `archive/`) |
| 3 | 도메인 확장 · 이벤트 스토밍 | 부분 (카탈로그 TBD) |
| 4~5 | Design Doc · ADR | 완료 |
| 6 | 플래닝 | 완료 |
| **7** | **모듈 재편 · ES 인프라 · 애그리거트 전환 · 테스트** | **미착수 (별도 사이클)** |
| 8 | Kafka 심화 | 설계 완료 · 구현 미착수 |
| 9 | 성능 테스트 (k6) | 전략 수립 · 실행 미착수 |
| 10 | 인프라 (k3s/AWS) | 설계 완료 · 구현 미착수 |

---

## 7. 문서 지도

- **진행 상태 · 플랜**: 이 문서 (SSOT)
- **전체 색인 (MOC)**: [[index]]
- **결정**: `adr/` (원자적 결정) · `rfc/` (서사·논의) · `design_doc/` (설계)
- **모듈 설계 · 구현 계획**: `modules/`
- **심화 분석**: `analysis/`
- **`archive/`**: 구 사이클 초기 문서 (`00-roadmap` · `01-v1-architecture-analysis` · `02-event-sourcing-transition-points` · `04-design-doc-module-structure` · `Report` · `Prepare`) — 역사적 참고용, 진실원 아님
