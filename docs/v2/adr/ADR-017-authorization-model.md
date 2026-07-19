# ADR-017: V2 인가 모델 — 소유권은 command 애그리거트 불변식·query WHERE 스코프로 이분

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-015-authorization-model]] · **설계**: [[DESIGN-014-authorization]]
- **연관 ADR**: [[ADR-024-authentication-boundary]]

---

## 맥락과 문제 (Context and Problem Statement)

V1에서 인증·인가는 `JwtFilter` + `SecurityConfig` 경로-prefix 역할 게이트로 처리했다. 소유권("내 것만")은 컨트롤러가 principal로 자기 DB를 조회하니 자연히 걸렸다 — 같은 트랜잭션·같은 테이블이 그 검사를 대신했다. V2는 쓰기·읽기가 갈리므로(CQRS) 이 "자연히 걸리던" 층이 사라져, 인가 배치를 명시해야 한다.

**게이트웨이가 신원·역할을 확정한 뒤, command와 query 각각에서 소유권 검사를 어디에 어떻게 두는가.**

## 결정 동인 (Decision Drivers)

- CQRS로 command/query가 갈리며 V1의 "자연히 걸리던" 소유권 검사 층이 사라진다 — 인가 배치를 명시해야 한다.
- 역할 판단(자원 상태 무관)은 엣지에서, 소유권 판단(자원 상태 필요·도메인 지식)은 애플리케이션 안에서 갈라야 한다 — 게이트웨이가 도메인 지식을 알면 게이트웨이가 도메인에 결합되는 안티패턴이 된다.
- query 측 소유권은 사후 필터링이 아니라 쿼리 조건으로 걸어야 한다 — 사후 필터링은 페이징을 깨고 필터 누락 시 전량 유출 위험이 있다.
- 신원은 검증된 헤더 클레임만 신뢰한다 — command 바디의 자칭 신원은 무시하거나 클레임과의 일치를 강제해야 위조를 막는다.

## 검토한 선택지 (Considered Options)

**신원·역할 전파**
- **게이트웨이 헤더 전파 (채택)** — 게이트웨이가 토큰 검증·decrypt 후 신원·역할을 헤더로 내리고 뒷단은 그 헤더를 신뢰한다.
- **컨텍스트 횡단 동기 역할 조회** — 인가가 필요할 때마다 신원/역할 원천을 동기 조회하면 항상 최신 상태를 볼 수 있으나, 컨텍스트 간 동기 결합이 생겨 V2가 피하려는 모놀리식 결합으로 되돌아간다.

**command 측 소유권 검사 위치**
- **애그리거트 불변식 (채택)** — 소유권 검사("주인만 취소 가능")를 도메인 규칙으로 애그리거트 안에 둔다.
- **게이트웨이에서 소유권까지 판단** — 단일 지점에서 인가를 완결할 수 있으나, 게이트웨이가 도메인 지식(이 자원의 주인이 누구인지)을 알아야 해 도메인에 결합된다.

**query 측 소유권 검사 위치**
- **쿼리 WHERE 스코프 (채택)** — 조회 쿼리 자체에 소유권 조건(`WHERE owner_id = :header_user_id`)을 건다.
- **사후 필터링** — 구현은 단순하나 페이징이 깨지고, 필터 한 줄 누락 시 전량 유출 위험이 있다.

## 결정 (Decision Outcome)

**채택.** 게이트웨이에서 토큰 검증·decrypt → 신원·역할을 헤더(`X-User-Id`, `X-Role` 등)로 전파하고, 뒷단 서비스는 그 헤더를 신뢰하고 쓴다.

| # | 결정 |
|---|------|
| 1 | 게이트웨이가 토큰 검증·decrypt → 신원·역할을 **헤더로 전파** |
| 2 | command 소유권 = **애그리거트 불변식** (신원은 검증된 헤더 클레임만 믿는다 — 자칭 신원은 불신) |
| 3 | query 소유권 = **`WHERE owner_id = :header_user_id`** 쿼리 시점 스코프 (사후 필터링 아님) |

V1 대비 달라지는 것은 CQRS 분리로 소유권 검사 위치를 command(애그리거트)·query(WHERE)에 명시해야 한다는 점뿐이다.

인증 메커니즘과 "검증된 헤더를 신뢰한다"는 전제(엣지 검증 위치·모델 A)는 이 ADR의 범위가 아니다 — [[ADR-024-authentication-boundary]]에서 다룬다.

### 결과 (Consequences)

- 좋은 점: 게이트웨이 → 헤더 전파 → 뒷단 신뢰라는 구조로, command는 애그리거트가 소유권을 검사하고 query는 WHERE 조건이 소유권을 검사한다 — CQRS로 사라진 "자연 소유권 검사" 층을 각 축에 명시적으로 되살린다.
- 트레이드오프: 인증 인프라(Vault·OIDC 등)는 이 결정의 범위가 아니다 — 별도 ADR([[ADR-024-authentication-boundary]])이 다룬다.

### 확인 (Confirmation)

구현 사이클에서 정의.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 인증 인프라(Vault·OIDC 등) — [[ADR-024-authentication-boundary]] 소관. 인가 세부 설계(역할/스코프 경계, 프로젝션 스코프 키 등)는 [[DESIGN-014-authorization]] 소관.
- 관련: [[RFC-015-authorization-model]] · [[DESIGN-014-authorization]] · [[ADR-024-authentication-boundary]]
