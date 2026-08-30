# RFC-029 — 이벤트 페이로드 정책: event-carried 일원화 (Zero Payload 폐기, 트리아지 C02)

- **상태**: 🏷 합의 (2026-07-05) — 모든 내부 도메인 이벤트를 event-carried로 통일, Zero Payload(소비 측 최신 조회) 정책 폐기. ADR 비준 대기
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **선행**: [[RFC-002-read-model-consistency]](읽기 모델·페이로드) · [[RFC-021-event-identity-and-global-ordering]]·[[22.event-identity-and-global-ordering]](생산 시점 박제·seq 가드) · [[RFC-023-event-schema-contract-management]](내부↔통합 이벤트) · 인덱스 [[RFC-INDEX]]
- **닫으면**: [[DESIGN-003-write-model]] §4.4 본문·용어집·계승 정정 + §Weakness §4.4 항목 종결 + ADR-22 일반화(비준은 후속)
- **분석 출처**: [[06-design-weakness-triage]] C02 (D-003 §4.4 line 124·용어집 line 161 · Weakness line 191-193)

---

## 배경 (Background)

### 시나리오: v3 시점 이벤트를 재생하는데 v5가 박힌다

V1에서 계승한 **Zero Payload**([[07.reservation]])는 "이벤트에 식별자만 싣고, 소비 측이 최신 상태를 조회해 채운다"였다. 정상 흐름에선 "최신 조회"의 비용이 latency(delay)뿐이지만, **ES 컨텍스트가 과거 이벤트를 재생(replay)** 하면 조회가 **지금(v5) 값**을 읽어 v3 이벤트에 박는다 — time-travel 오염(C02). delay를 0으로 만들어도 남는 정확성 결함이다.

### 먼저 용어

| 말 | 뜻 |
|---|---|
| **Zero Payload** | 이벤트엔 식별자만, 소비 측이 최신 상태를 조회해 채움 (V1 계승) |
| **event-carried** | 이벤트가 자기 시점의 값을 페이로드에 실음 — 불변이라 stale 안 됨 |
| **replay / 재구축** | 저장된 이벤트를 재생해 상태·프로젝션을 다시 만듦 |
| **point-in-time** | "그 사건이 난 그 시점"의 값 (지금 값이 아니라 당시 값) |
| **불변 참조** | 절대 안 바뀌는 식별자(ID) — 조회해도 time-travel 없음 |

---

## 맥락 (Context)

- **자산 — 강한 버전은 이미 event-carried로 결정됐다.** 교차-애그리거트 point-in-time 오염은 [[RFC-021-event-identity-and-global-ordering]] #4 + [[22.event-identity-and-global-ordering]]가 "생산 시점 값 박제 + `sequence_no` 가드"로 막았다. → 이 RFC는 그 원칙을 **모든 이벤트로 일반화**한다.
- **자산 — 이벤트는 append-only·불변이다.** 값을 실어도 나중에 안 바뀌므로 stale이 될 수가 없다(사용자 note, D-003 line 192). → Zero Payload의 존재 이유(stale 방지)가 ES에선 애초에 성립 안 함.
- **한계 — 결정 본문이 아직 Zero Payload다.** D-003 §4.4 line 124·용어집 line 161은 "Zero Payload=최신 조회"로 남아 있고, 분기 제안은 리뷰노트(line 193)에만 있다. → 결정 본문을 정정해야 갭이 닫힌다.

핵심 긴장 — **C02를 분기(ES=event-carried / 비-ES=Zero Payload)로 닫을 수도 있으나, 매 이벤트마다 "ES냐"를 판단해야 한다. 일관된 단일 규칙이 인지 부담·오적용 위험을 없앤다.**

---

## Goal / Non-goal

**Goal**
- 모든 내부 도메인 이벤트의 페이로드 정책을 **event-carried 하나로** 확정한다.
- Zero Payload 정책을 폐기하고 D-003 결정 본문·용어집을 정정한다.

**Non-goal**
- 통합 이벤트(contract) 스키마 — 이미 published 언어로 값을 싣는다([[RFC-023-event-schema-contract-management]]). 본 RFC는 *내부* 이벤트 정책.
- 스키마 진화·업캐스팅. → [[RFC-022-event-schema-evolution]].
- ADR-22 비준 자체(사용자 권한).

---

## 논의 (Discussion)

### 논점 1. 분기냐 일원화냐

분기(ES=event-carried / 비-ES=Zero Payload)는 정확하지만 **매 이벤트 설계마다 "이 컨텍스트가 ES인가"를 판단**해야 하고, 경계가 흐린 컨텍스트에서 오적용 위험이 있다. 일원화(event-carried 전부)는:

- **판단 제거** — 규칙 하나. "이벤트는 자기 시점 사실을 담는다"만 기억.
- **버그 클래스 소멸** — 조회가 없으니 time-travel이 원천 봉쇄.
- **내부↔통합 정합** — 통합 이벤트는 이미 값을 싣는다([[RFC-023-event-schema-contract-management]]). 내부도 같은 결.
- **비용은 실질 0** — 무트래픽 프로토타입이라 이벤트 크기 증가가 실 문제 아님([[v2-optimize-for-learning-not-cost]]).

**결정: event-carried 일원화. Zero Payload 폐기.**

### 논점 2. "Zero Payload 안 함" ≠ "모든 바이트 인라인"

과대 적용을 막는 정밀 규칙:

> 이벤트는 자기가 주장하는 **그 시점의 사실**을 담는다 — 값, 또는 **불변 참조(ID)**. 소비 측은 **가변 최신 상태를 조회해 재생된 이벤트를 채우지 않는다.**

- 큰 blob·문서는 **불변 ID로 참조** 후 ID로 조회 — 불변이라 time-travel 없음. 이건 Zero Payload가 아니다(Zero Payload는 *가변* 최신 상태 조회가 문제였다).
- 즉 담는 것은 "당시 사실"이고, 참조는 불변인 것만 허용.

### 논점 3. 교차-컨텍스트로 실은 값은 stale 아닌가

reservation 이벤트가 restaurant 이름을 실었는데 식당이 개명하면? **그건 stale이 아니라 point-in-time으로 정확하다** — "그 예약 시점의 이름"이다. "현재 이름"을 보여주려는 프로젝션은 restaurant 개명 이벤트를 별도 구독·조인하면 된다 — **페이로드 정책이 막지 않는다**(프로젝션 설계의 몫). event-carried는 *재생 결정성*을 주고, "현재 뷰" 요구는 프로젝션이 별도로 해결.

---

## 결정 요약

1. **모든 내부 도메인 이벤트 = event-carried.** Zero Payload 정책 폐기.
2. **규칙**: 이벤트는 그 시점 사실(값 또는 불변 참조)을 담는다. 소비 측은 가변 최신 상태를 조회해 재생 이벤트를 채우지 않는다.
3. **불변 참조 예외 유지**: 큰 blob은 불변 ID 참조 허용(time-travel 없음).
4. **[[RFC-021-event-identity-and-global-ordering]] #4/ADR-22의 일반화** — 교차-애그리거트 박제 원칙을 전 이벤트로 확장. ADR-22 비준은 후속(사용자 권한).

## supersede / 정합

- **supersede**: [[DESIGN-003-write-model]] §4.4 line 124(Zero Payload 계승)·용어집 line 161·계승 line 172, 그리고 리뷰노트 line 193의 *분기* 제안 — 모두 event-carried 일원화로 대체.
- **재사용**: 생산 시점 박제·seq 가드([[RFC-021-event-identity-and-global-ordering]]), 통합 이벤트 값 적재([[RFC-023-event-schema-contract-management]]).
- **후속**: 재구축이 그 시점 값을 정확히 복원하는지 검증은 [[DESIGN-009-event-store-lifecycle]](재구축)·[[DESIGN-004-read-model]](프로젝션 재빌드) 소관.

## 관련 문서

- 읽기 모델: [[RFC-002-read-model-consistency]] · 쓰기 모델: [[DESIGN-003-write-model]]
- 분석: [[06-design-weakness-triage]] (C02)
- 재사용 자산: [[RFC-021-event-identity-and-global-ordering]] · [[22.event-identity-and-global-ordering]] · [[RFC-023-event-schema-contract-management]]
