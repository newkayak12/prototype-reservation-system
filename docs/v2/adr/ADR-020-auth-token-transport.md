# ADR-020: 인증 토큰 transport·무상태성 — refresh 쿠키/access 헤더 계승, 서명검증 무상태화, 즉시 폐기 기본 포기

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-019-auth-token-transport]] · **설계**: [[DESIGN-017-auth-token]]
- **연관 ADR**: [[ADR-017-authorization-model]] · [[ADR-024-authentication-boundary]] · [[ADR-019-caching-redis-role]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 로그인 시 access를 응답 body로 내리고 refresh를 `HttpOnly`·`Secure` 쿠키(`path=/`)로 심는다. 클라이언트는 access를 Authorization(Bearer) 헤더로 들고 다니고, refresh는 브라우저가 `/refresh`에 자동 전송한다. 그 위에 V1은 하나 더 얹었다 — refresh를 서버 측 Redis에도 저장해 `/refresh`마다 그 사본을 조회·대조한 뒤 새 access·refresh를 rotation 발급했다. `signOut`은 쿠키만 `maxAge=0`으로 지울 뿐 Redis 사본은 끊지 않아, V1도 즉시 폐기는 실은 하지 못했다.

시나리오: 로그인한 USER의 access가 만료돼 쿠키의 refresh로 `/refresh`를 친다. 서버는 이 refresh를 *무엇으로* 검증하는가(Redis 사본 조회 vs 서명만)? 반대로 운영자가 "이 사용자 지금 강제 로그아웃"을 누르면, 무상태 서명 토큰은 발급 후 만료까지 스스로 유효해 서버가 사본을 안 들면 즉시 끊을 손잡이가 없다.

V2에서 access는 이미 무상태 서명 JWT로 정리됐고([[ADR-017-authorization-model]] 토대), Redis 역할은 분산 조정·휘발성 상태로 좁혀지는 중이다([[ADR-019-caching-redis-role]]). refresh만 서버 사본을 들고 검증하는 비대칭이 남아 있다.

**refresh의 검증을 무상태로 들어내 Redis 역할 축소를 완성하되, 그 대가인 즉시 폐기 불능을 메울 것인가 받아들일 것인가를 토큰 수명과 한 몸으로 정해야 한다.**

## 결정 동인 (Decision Drivers)

- access가 이미 무상태 서명 JWT로 정리됐다 — 검증에 서버 사본이 필요 없다는 논리를 refresh에도 그대로 적용할 수 있는가.
- Redis 역할이 "분산 조정·휘발성 상태"로 좁혀지는 흐름과, refresh의 must-not-evict 서버 저장분이 그 좁힌 역할에 맞는지.
- transport 분담(refresh=쿠키, access=body→헤더)은 V1에서 이미 검증된 자산 — 처음부터 새로 정하지 않고 빈틈(`SameSite`)만 메운다.
- V1도 즉시 폐기를 실제로 하지 못했다 — 무상태로 가서 잃는 실효 기능이 있는지.

## 검토한 선택지 (Considered Options)

**refresh 검증**
- **V1식 Redis 사본 조회** — `/refresh`마다 서버 저장분을 조회·대조.
- **무상태 서명 검증** — 서명·만료·클레임 검사만으로 검증, Redis 사본 제거.

**transport**
- **V1 그대로(SameSite 미설정)** — refresh 쿠키 자동 전송, `/refresh`가 CSRF 표면.
- **V1 계승 + SameSite·path 보강** — 분담은 그대로 잇되 `SameSite`(Lax)·path 스코프를 채운다.

**즉시 폐기(denylist)**
- **denylist 유지** — 발급한 모든 토큰을 잔여 수명만큼 목록으로 들고 있는다.
- **폐기 포기, 짧은 TTL + jti 재사용 탐지로 덮기** — 즉시 강제 폐기는 기본에서 포기하고, rotation·재사용 탐지·강제 로그아웃은 최소 서버 상태(`current_refresh_jti` 컬럼)로만 처리.

## 결정 (Decision Outcome)

**채택: 무상태 서명 검증 + transport V1 계승(SameSite 보강) + denylist 기본 포기(jti 컬럼으로 재사용 탐지·강제 로그아웃만 유지).**

| 항목 | 결정 |
|---|---|
| **transport(refresh)** | `HttpOnly` 쿠키, `SameSite=Lax`, path 스코프. V1 계승 + 빈틈 보강 |
| **transport(access)** | 응답 body로 발급 → 클라이언트가 Authorization Bearer 헤더로 들고 다님. body 발급이 CSRF 노출을 원리적으로 낮춘다 — 위조 `/refresh` 호출이 성공해도 새 access는 cross-origin body라 공격 페이지가 읽지 못한다 |
| **refresh 검증** | 무상태 서명 검증(서명·만료·클레임만). Redis 사본 대조 제거 |
| **rotation** | `/refresh`마다 새 refresh 발급 |
| **재사용 탐지 + 강제 로그아웃** | `authenticate` 테이블의 `current_refresh_jti` 컬럼과 대조 — DB 조회는 오직 `/refresh` 시에만, 일반 API는 서명 검사만. jti 불일치 시 탈취 의심으로 보고 컬럼을 NULL로 밀어 전 세션을 무효화 |
| **즉시 폐기(denylist)** | V2 기본에서 포기. must-not-evict 재유입 방지, V1도 미지원, 짧은 access TTL로 대체. 도메인·규제 요구가 입증될 때만 부활 |
| **access 클라이언트 저장** | in-memory 권장 — localStorage는 XSS-readable이라 refresh를 HttpOnly로 숨긴 보람을 깬다 |

인가 규칙(소유권 검사 위치·구현)과 엣지 검증 모델은 이 ADR의 범위가 아니다 — [[ADR-017-authorization-model]]·[[ADR-024-authentication-boundary]]가 다룬다. denylist 부활 시의 Redis must-not-evict 등급 연동은 [[ADR-019-caching-redis-role]] 소관이다.

### 결과 (Consequences)

- 좋은 점: refresh 검증에서 서버 사본 조회가 사라져 access와 동일한 무상태 검증 모델로 통일된다.
- 좋은 점: transport 분담의 비대칭(refresh=쿠키·access=body)이 CSRF 노출을 구조적으로 낮춘다.
- 좋은 점: 재사용 탐지·강제 로그아웃을 denylist 없이 최소 컬럼(`current_refresh_jti`) 하나로 해결해, must-not-evict 서버 상태를 다시 들이지 않는다.
- 트레이드오프: 무상태 refresh는 발급 후 만료까지 서버가 즉시 강제 폐기하지 못한다 — 짧은 access TTL로 노출 창을 줄이는 데 의존한다.
- 트레이드오프: denylist를 기본에서 포기하므로, 즉시 강제 로그아웃 요구가 도메인·규제로 입증되는 순간에만 [[ADR-019-caching-redis-role]] 쪽 must-not-evict 등급을 되살려 부활시켜야 한다.

### 확인 (Confirmation)

구현 사이클에서 정의.

## 선택지 상세 (Pros and Cons of the Options)

### V1식 Redis 사본 조회 (기각)
- 장점: 서버가 사본을 쥐고 있어 즉시 폐기·강제 무효화가 자연스럽다.
- 단점: must-not-evict 서버 상태를 유지해야 해 Redis 역할 축소와 부딪힌다.
- 기각 사유: access가 이미 무상태로 정리된 마당에 refresh만 사본 검증을 유지할 이유가 없다.

### denylist 유지 (기각, 요구 입증 시 부활)
- 장점: 발급 즉시 폐기가 가능하다.
- 단점: 막 들어낸 must-not-evict 워크로드를 다시 들여, Redis 역할 축소를 무의미하게 만든다.
- 기각 사유: V1도 즉시 폐기를 하지 못했으므로 무상태로 가도 잃을 실효 기능이 없고, 짧은 TTL + jti 재사용 탐지로 대부분의 요구가 덮인다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: refresh JWT 클레임 구성·TTL·서명 키와 access TTL의 구체 값, 쿠키 속성 확정(`SameSite` Lax vs Strict, path 범위, 도메인), rotation의 만료 연장 방식과 `current_refresh_jti` 갱신·대조 동시성, 즉시 폐기 부활 트리거의 세부 조건, 서명 키의 호스팅·회전, V1 `General`/`Seller` 분리 컨트롤러의 V2 통합 여부.
- 관련: [[RFC-019-auth-token-transport]] · [[DESIGN-017-auth-token]] · [[ADR-017-authorization-model]] · [[ADR-024-authentication-boundary]] · [[ADR-019-caching-redis-role]]
- 계승: `20.auth-token-transport.md`(v2 초기 스케치) — transport 분담·무상태 refresh·denylist 포기의 골격은 유지하되, 재사용 탐지는 [[DESIGN-017-auth-token]] 정합화(트리아지 C25)에 따라 "포기"가 아니라 `current_refresh_jti` 단일 컬럼으로 채택된 형태로 이 ADR이 대체한다.
