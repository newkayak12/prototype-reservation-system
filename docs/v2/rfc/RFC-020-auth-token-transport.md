# RFC-020 — 인증 토큰의 transport·무상태성과 폐기 포기

- **상태**: Closed · 합의 · 2026-06-23
- **선행**: [[RFC-016-authorization-model]] · [[RFC-019-caching-redis-role]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: 신규 design_doc(인증 토큰) 또는 [[RFC-016-authorization-model]] design_doc 보강 + 신규 ADR

## 결정 요약

- **transport**: refresh=HttpOnly 쿠키(SameSite Lax), access=body→Authorization 헤더. V1 계승 + SameSite 보완.
- **refresh 검증**: 서명 JWT로 무상태 검증. Redis 서버 사본 제거.
- **Refresh rotation**: `/refresh` 마다 새 refresh 발급.
- **재사용 탐지 + 강제 로그아웃**: `authenticate` 테이블에 `current_refresh_jti` 컬럼 추가. `/refresh` 시 jti 대조(DB 조회는 /refresh 시에만, 일반 API는 JWT 서명만). jti 불일치 = 탈취 의심 → jti NULL로 전 세션 무효화. 강제 로그아웃 = jti NULL → 다음 /refresh 실패 → access 자연 만료.
- **즉시 폐기(denylist)**: 기본 포기. 요구 입증 시에만 부활.

## 맥락

V1을 열어 보면 토큰 transport는 이미 갈려 있다. 로그인 시 `GeneralUserSignInController`는 access 토큰을 응답 body(`LoginGeneralUserResponse`)로 내리고, refresh 토큰은 `HttpOnly`·`Secure` 쿠키(`RefreshTokenDefinitions`: `path=/`)로 심는다. 클라이언트는 access를 Authorization 헤더(Bearer)로 들고 다니고(`JwtFilter`가 그걸 푼다), refresh는 브라우저가 `/refresh`에 자동으로 실어 보낸다(`RefreshGeneralUserController`가 쿠키에서 꺼낸다). 즉 **"refresh=쿠키, access=body"는 V2가 새로 정할 결정이 아니라 V1이 이미 하고 있는 것**이다 — V2는 이 transport를 잇는다.

V1이 그 위에 *하나 더* 얹은 게 있다. refresh 토큰을 서버 측 Redis에도 저장하고(`SaveGeneralUserRefreshToken`), `/refresh` 때 그 저장분을 조회해(`FindGeneralUserRefreshToken`) 검증한 뒤 새 access·새 refresh를 발급(rotation)한다. 즉 V1의 refresh는 쿠키로 오가는 동시에 *서버가 사본을 들고 검증하는* 토큰이었다. (한편 V1 `GeneralUserSignOutController`는 쿠키만 `maxAge=0`으로 지울 뿐 Redis 사본을 끊지 않는다 — 아래 시나리오 B의 출발점이다.)

가를 두 개념을 먼저 평이하게 박는다 — **transport**: 토큰이 클라이언트와 서버 사이를 *어떻게 오가나*(쿠키냐 헤더냐 body냐). **server-side state**: 서버가 토큰의 사본·상태를 *들고 있나*(검증·폐기를 위해). V1은 전자는 갈라 뒀고, 후자는 refresh에 대해 들고 있었다. 이 RFC는 후자를 들어낸다.

V2가 이 그림을 흔드는 지점은 둘이다. 첫째, access는 무상태 서명 JWT로 정리됐다([[RFC-016-authorization-model]] 토대 — 신원·역할은 서명된 클레임으로 흐르고 매 요청 서버 조회가 없다). 둘째, Redis의 역할이 "분산 조정·휘발성 상태"로 좁혀지면서([[RFC-019-caching-redis-role]]), refresh의 서버 저장분이 그 좁힌 역할에 맞는지가 도마에 오른다.

구체 시나리오 둘로 긴장을 끌어온다.

- **(시나리오 A) 로그인한 USER의 access가 만료된다.** 클라가 쿠키의 refresh로 `/refresh`를 쳐 새 access를 body로 받는다. 이때 서버는 refresh를 *무엇으로* 검증하나 — V1처럼 Redis 사본을 조회하나, 아니면 서명만으로 푸나?
- **(시나리오 B) 운영자가 "이 사용자 지금 강제 로그아웃"을 누른다.** 무상태 서명 토큰은 발급 후 만료까지 스스로 유효하다 — 서버가 사본을 안 들면 *즉시 끊을* 방법이 없다. 그런데 V1도 실은 못 했다(signout이 쿠키만 지웠다). 이 빈틈을 V2는 메우나, 받아들이나?

## 논의

### refresh를 서명만으로 검증하고 Redis 사본을 들어낸다

시나리오 A다. V1은 refresh를 Redis에 저장해 두고 `/refresh`마다 조회·대조했다. 나는 **refresh를 self-contained 서명 JWT로 두고, 검증을 서명·만료·클레임 검사로만 하며, Redis 서버 사본을 제거하는** 쪽을 택한다. 이유는 access가 이미 무상태 서명 JWT로 정리된 것과 정확히 같다 — 서명이 위변조를 막고 만료가 수명을 막으면, *검증*을 위해 서버가 사본을 들 이유는 없다. 서버 사본이 정당화되는 자리는 검증이 아니라 *폐기*(아래 절)인데, 그건 별도 요구이므로 검증 비용으로 끌어오지 않는다.

이 결정의 파급이 [[RFC-019-caching-redis-role]]에 곧장 닿는다. 거기서 Redis는 "인증 부산물 = must-not-evict" 등급(리프레시 저장·폐기 목록)과 "조정 상태 = 손실 허용" 등급으로 갈렸는데, refresh 저장을 들어내면 **must-not-evict 워크로드가 Redis에서 사라진다**. 그러면 Redis에 남는 건 손실 허용 조정 상태(레이트리밋·락·디듀프)뿐 — 단일 durability 등급이다. 이게 "Redis가 다재다능해 인스턴스를 기능별로 쪼개야 하나"라는 물음을 *쪼개기가 아니라 워크로드 제거로* 해소한다. (이의 여지: refresh를 무상태로 두면 한 번 발급된 refresh는 만료까지 서버가 통제하지 못한다 — 이 통제 불능이 받아들일 만한지는 아래 폐기 절의 트레이드오프와 한 몸이다.)

### transport는 V1을 잇되 V1이 빠뜨린 SameSite를 채운다

V1 쿠키(`RefreshTokenDefinitions`)는 `Secure`·`HttpOnly`·`path=/`까지만 두고 `SameSite`를 박지 않았다. refresh 쿠키는 브라우저가 자동 전송하므로 `/refresh`가 CSRF 표면이 된다. 나는 **transport 분담(refresh=HttpOnly 쿠키, access=body→Authorization 헤더)은 V1 그대로 잇되, 쿠키에 `SameSite`(기본 Lax)와 path 스코프를 채워 V1의 빈틈을 메우는** 쪽을 택한다.

여기 좋은 상호작용이 하나 있다 — access를 body로 내리는 V1·V2 공통 선택이 *오히려 CSRF 노출을 낮춘다*: 공격 페이지가 victim의 쿠키로 `/refresh`를 위조 호출해도, 새 access는 cross-origin **body**라 그 응답을 읽지 못한다. access를 쿠키로 내렸다면 자동 심겨 위험했을 표면이, body라 막힌다. 비대칭(자동 전송이 필요한 refresh는 쿠키, CSRF 면역이 필요한 access는 헤더)이 우연이 아니라 원리적이다. (덧붙임: access의 *클라이언트* 저장은 in-memory가 정답 — localStorage는 XSS-readable이라 refresh를 HttpOnly 쿠키로 숨긴 보람을 깬다. 새로고침 시엔 refresh 쿠키로 재발급한다.)

### 폐기를 포기한다 — 무엇을 잃고 무엇으로 덮나

시나리오 B다. 무상태 토큰의 본질적 빈틈은 즉시 폐기다. 나는 **즉시 강제 폐기(블랙리스트/denylist)를 V2 기본에서 포기하는** 쪽을 택한다. 셋을 근거로 —

1. 폐기 목록을 들면 위 절에서 막 들어낸 must-not-evict 서버 상태를 *다시* 들이는 것이라, [[RFC-019-caching-redis-role]]의 Redis 역할 축소가 무의미해진다.
2. V1도 사실 즉시 폐기를 못 했다 — `signOut`이 쿠키만 지웠으니, 무상태로 가도 *잃을 게 없다*.
3. 짧은 access TTL + refresh 만료로 대부분의 "로그아웃" 요구가 덮인다 — 쿠키 삭제로 클라가 refresh를 잃으면 잔여 access는 곧 자연 만료된다.

rotation은 매 `/refresh`마다 새 refresh를 발급해 잇되, **재사용 탐지**(같은 refresh가 두 번 쓰이면 도난으로 보고 끊기)는 *서버 상태가 필요*하므로 함께 포기한다. (이의 여지: "즉시 강제 로그아웃"이 도메인·규제 요구로 실제 입증되면, 그때만 [[RFC-019-caching-redis-role]]의 must-not-evict 등급을 *되살려* denylist를 둔다 — 토큰 잔여 수명만큼만 사는 목록이라 TTL로 자청소된다. 모델 기본은 "폐기 없음", 예외는 "요구가 입증될 때만". 이건 [[RFC-016-authorization-model]]이 "역할은 토큰 발급 시점 스냅샷"으로 남긴 권한 강등 즉시성 문제와 같은 결이다 — 토큰 수명을 짧게 잡을수록 폐기 없이도 덮인다.)

## Design으로 넘기는 것

- refresh JWT의 클레임 구성·TTL·서명 키와 access TTL의 구체 값 — [[RFC-016-authorization-model]] "역할은 발급 시점 스냅샷"의 stale 창과 한 몸으로 정한다(수명이 즉시성을 대신한다).
- 쿠키 속성 확정 — `SameSite` Lax vs Strict(외부 링크 진입 영향), path 스코프(`/` vs `/…/refresh`), 도메인.
- rotation 정책 — 매 refresh 발급의 만료 연장 방식과, 재사용 탐지를 끝까지 두지 않을지의 최종 확인.
- 즉시 폐기가 요구로 입증될 경우의 denylist 부활 트리거 — [[RFC-019-caching-redis-role]] must-not-evict 등급·[[RFC-016-authorization-model]] 토큰 수명과 함께.
- 서명 키의 *호스팅·회전*(키 롤오버, OIDC/Vault 등)은 인증 인프라(백로그 T-13)·[[RFC-008-deployment-infra-ops]]로 넘긴다 — 여긴 토큰 *모델*만 정한다.
- V1의 `General`/`Seller` 분리 sign-in/refresh/signout 컨트롤러를 V2에서 통합할지.

## 관련 문서

- [[RFC-016-authorization-model]] · [[RFC-019-caching-redis-role]] · [[RFC-013-command-query-api-contract]] · [[RFC-008-deployment-infra-ops]] · [[RFC-002-decision-queue]]
- [[02-write-model]] (`user`·`authenticate` 상태 테이블 + Outbox)
