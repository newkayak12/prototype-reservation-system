# RFC-015 — V2 인가 모델

- **상태**: ✅ 종결 (2026-06-29) · 하류 산출물 없음 — 표준 관행을 과잉 분석한 것이라는 지적으로 닫음
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-012-command-query-api-contract]] · 인덱스 [[RFC-INDEX]]

---

## 배경 (Background)

V1에서 인증·인가는 `JwtFilter` + `SecurityConfig` 경로-prefix 역할 게이트로 처리했다. 소유권("내 것만")은 컨트롤러가 principal로 자기 DB를 조회하니 자연히 걸렸다. V2에서 쓰기·읽기가 갈리면(CQRS) 이 "자연히 걸리던" 층이 사라지므로 인가 배치를 명시해야 한다.

---

## 맥락 (Context)

게이트웨이에서 토큰을 검증·decrypt해서 신원·역할을 헤더(`X-User-Id`, `X-Role` 등)로 내려주면 뒷단 서비스는 그 헤더를 신뢰하고 쓴다. 이것은 웹 앱의 표준 관행이며 RFC 수준의 분석이 필요한 문제가 아니었다.

---

## Goal / Non-goal

**Goal**
- V2에서 인증·인가의 배치를 정한다.

**Non-goal (이번에 하지 않음)**
- 인증 인프라(Vault·OIDC 등) — 운영 인프라 문제.

---

## 결정 요약

| # | 결정 | ADR |
|---|------|-----|
| 1 | 게이트웨이가 토큰 검증·decrypt → 신원·역할을 **헤더로 전파** | — |
| 2 | command 소유권 = **애그리거트 불변식** (신원은 헤더의 검증된 클레임) | — |
| 3 | query 소유권 = **`WHERE owner_id = :header_user_id`** 쿼리 스코프 | — |

**의견:** 게이트웨이 토큰 검증→헤더 전파, 소유권은 도메인/쿼리 WHERE — 웹 앱 인가의 기본 패턴이다. V1과 달라지는 건 CQRS 분리로 소유권 검사 위치를 command(애그리거트)·query(WHERE)에 명시해야 한다는 점뿐이며, 이것도 패턴이 자명하므로 RFC가 아니라 설계 메모로 충분했다.

---

## 결과

게이트웨이 → 헤더 전파 → 뒷단 신뢰. command는 애그리거트가 소유권 검사, query는 WHERE 조건.

---

## 관련 문서

- 인덱스: [[RFC-INDEX]]
- 연관 RFC: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-012-command-query-api-contract]] · [[RFC-002-read-model-consistency]]
