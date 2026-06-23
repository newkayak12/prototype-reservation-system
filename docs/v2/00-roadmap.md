# V2 Event Sourcing + CQRS 전환 로드맵

> Cycle: `20260604-v2-event-sourcing-cqrs` | Type: Exploration | Budget: 3개월
> 이 문서는 사이클 전체 진행 상태의 SSOT(Single Source of Truth)이다.

## 현재 상태

| Phase | 상태 | 산출물 |
|---|---|---|
| 0. 로드맵 | **진행 중** | 이 문서 |
| 1. V1 구조 분석 | **완료 (재작성)** | [01-v1-architecture-analysis.md](./01-v1-architecture-analysis.md) |
| 2. 전환 포인트 도출 | Draft (리뷰 필요) | [02-event-sourcing-transition-points.md](./02-event-sourcing-transition-points.md) |
| 3. 도메인 확장 + 이벤트 스토밍 | 미시작 | — |
| 4. Design Docs | 미시작 | — |
| 5. ADR | 미시작 (Design Doc 확정 후) | — |
| 6. 플래닝 | 미시작 | — |
| 7. 모듈 구조 변경 + 구현 | 미시작 | — |
| 8. Kafka 심화 | 미시작 | — |
| 9. 성능 테스트 (k6) | 미시작 | — |
| 10. 인프라 (k3s + AWS) | 미시작 | — |

---

## Phase 구조

### Phase 0: 로드맵 수립
- 산출물: 이 문서
- 유형: collaborative (사용자 확인 필요)

### Phase 1: V1 구조 분석
- 산출물: `01-v1-architecture-analysis.md`
- 유형: solo (AI 주도, 사용자 리뷰)
- 내용: 모듈 구조, 도메인 맵, 이벤트 흐름, 인프라 패턴

### Phase 2: 전환 포인트 도출
- 산출물: `02-event-sourcing-transition-points.md`
- 유형: collaborative (판정은 사용자와 논의)
- 내용: 도메인별 전환/비전환 판정, CQRS 전략, Event Store 방향
- **의존**: Phase 1

### Phase 3: 도메인 확장 + 이벤트 스토밍
- 산출물: `03-domain-events.md` (이벤트 카탈로그), 도메인 모델 변경 문서
- 유형: **collaborative** — 이벤트 스토밍은 사용자와 함께
- 내용:
  - 전환 대상 도메인의 **도메인 이벤트 전수 식별** (이벤트 스토밍)
  - Aggregate 행위 재설계 (setter → command + event 패턴)
  - 새 도메인 개념 추가 (상태 전이 확장, 새 이벤트 등)
  - Aggregate 경계 재검토 (Schedule을 Restaurant에 통합? 별도?)
- **의존**: Phase 2 확정
- **이유 (끌어올린 근거)**: ES를 설계하려면 먼저 "어떤 이벤트가 존재하는지"를 알아야 한다. 도메인 이벤트 식별 없이 Event Store/Projection을 설계하면 추상적이 된다.

### Phase 4: Design Docs (iteration)
- 산출물: `04-design-doc-*.md` (주제별 복수 문서)
- 유형: **collaborative** — draft → 사용자 리뷰 → 수정 → 확정
- 예상 주제:
  - Event Store 설계 (Phase 3의 이벤트 카탈로그 기반)
  - ES Aggregate 추상화 (base class, command handler, event apply)
  - CQRS Read Model / Projection (Phase 3의 이벤트 → 어떤 Read Model?)
  - 모듈 구조 재편 (Write module / Read module 분리)
  - Kafka 토픽 전략 (이벤트별 토픽? Aggregate별 토픽?)
- **의존**: Phase 3 (이벤트 카탈로그가 있어야 구체적 설계 가능)

### Phase 5: ADR 작성
- 산출물: `05-adr-*.md`
- 유형: **gated** — Design Doc 확정 후, AI가 초안 작성, 사용자 승인
- 내용: 각 Design Doc의 핵심 결정을 MADR 형식으로 기록
- **의존**: Phase 4 확정

### Phase 6: Implementation Planning
- 산출물: `06-implementation-plan.md`
- 유형: collaborative
- 내용: 구현 순서, 태스크 분해, 브랜치 전략, 테스트 전략
- **의존**: Phase 5

### Phase 7: 모듈 구조 변경 + 코드 구현 + 테스트
- 산출물: 코드 + 테스트
- 유형: solo (AI 구현, 사용자 코드 리뷰)
- 내용: 모듈 재편, ES 인프라 코드, Aggregate 전환, Read Model, 테스트
- **의존**: Phase 6

### Phase 8: Kafka 심화 + 현업 이슈 재현
- 산출물: Kafka 통합 코드 + 이슈 시나리오 문서
- 유형: collaborative (이슈 설계) + solo (구현)
- 내용: 다중 토픽, 순서 보장, 재처리, 병렬 소비, 장애 시나리오
- **의존**: Phase 7 (일부 Phase 7과 병렬 가능)

### Phase 9: 성능 테스트 (k6)
- 산출물: k6 스크립트 + 성능 리포트
- 유형: solo (실행) + collaborative (결과 분석)
- 내용: ES Write 성능, Read Model 조회 성능, Kafka throughput
- **의존**: Phase 7, 8

### Phase 10: 인프라 (k3s + AWS 모방)
- 산출물: k3s 매니페스트, 인프라 구성 문서
- 유형: collaborative (설계) + solo (구현)
- 내용: k3s 클러스터, MySQL/Redis/Kafka 배포, 서비스 배포, 모니터링
- **의존**: Phase 7 (Phase 9와 병렬 가능)

---

## 의존성 그래프

```
Phase 1 (v1 분석) ✅
    ↓
Phase 2 (전환 포인트) ← 사용자 리뷰 필요
    ↓
Phase 3 (도메인 확장 + 이벤트 스토밍) ← 핵심: ES 설계의 입력
    ↓
Phase 4 (Design Docs) ← iteration 필수
    ↓
Phase 5 (ADR) ← Design Doc 확정 후
    ↓
Phase 6 (플래닝)
    ↓
Phase 7 (구현) ──┬──→ Phase 9 (k6)
    ↓            │
Phase 8 (Kafka) ─┘──→ Phase 10 (k3s)   ← 7, 8과 병렬 가능
```

**핵심 변경**: 도메인 확장(Phase 3)을 Design Doc(Phase 4) 앞으로 이동.
이벤트 스토밍으로 도메인 이벤트를 먼저 식별해야 Event Store/Projection 설계가 구체적이 된다.

## 시간 배분 (3개월)

| 기간 | Phase | 비고 |
|---|---|---|
| Month 1 전반 | 0, 1, 2 | 분석 + 전환 포인트 (완료/진행중) |
| Month 1 후반 | 3 | 도메인 확장 + 이벤트 스토밍 |
| Month 2 전반 | 4, 5 | Design Doc iteration + ADR |
| Month 2 후반 | 6, 7 시작 | 플래닝 + 모듈 재편 + ES 인프라 + 첫 Aggregate 전환 |
| Month 3 전반 | 7 완료, 8 | 나머지 Aggregate + Kafka 심화 |
| Month 3 후반 | 9, 10 | k6 + k3s (병렬) |

## 산출물 유형 범례

| 유형 | 의미 | AI 행동 |
|---|---|---|
| **solo** | AI가 주도, 사용자 리뷰 | 작성 후 리뷰 요청 |
| **collaborative** | 사용자와 iteration | draft 제시 → 피드백 반영 → 확정. **혼자 확정하지 않음** |
| **gated** | 이전 산출물 확정 필수 | 선행 조건 미충족 시 진행 불가 |
