# V2 Design Doc — 16. Auth Token (인증 토큰 transport·무상태성·폐기)

- **개요**: [[00-design-overview]] · **근거**: [[RFC-019-auth-token-transport]]
- **관련 결정**: [[13-authorization]] · [[RFC-015-authorization-model]] · [[RFC-018-caching-redis-role]] · [[02-write-model]]

> 이 문서는 인증 토큰이 클라이언트와 서버 사이를 *어떻게 오가고*(transport), 서버가 그 사본·상태를 *드는가*(server-side state)를 확정한다. "검증된 주체가 이 자원에 이 행위를 해도 되는가"(인가)는 [[13-authorization]]의 몫이고, "누가 토큰을 발급·검증하나"의 호스팅·키 회전은 인증 인프라(백로그 T-13)·[[09-deployment-runtime]]의 몫이다. 여긴 토큰 *모델*만 정한다.

## 0. 출발점: V1은 transport를 이미 갈라 뒀고, 서버 사본을 하나 더 들었다

V1은 access를 응답 body로 내리고, refresh를 `HttpOnly`·`Secure` 쿠키(`RefreshTokenDefinitions`, `path=/`)로 심었다. 클라는 access를 Authorization(Bearer) 헤더로 들고(`JwtFilter`가 푼다), refresh는 브라우저가 `/refresh`에 자동 전송한다. **"refresh=쿠키, access=헤더"는 V2가 새로 정하는 게 아니라 V1 계승**이다.

V1은 그 위에 하나 더 얹었다 — refresh를 Redis에도 저장(`SaveGeneralUserRefreshToken`)해 `/refresh`마다 조회·대조한 뒤 새 access·refresh를 발급(rotation)했다. 즉 V1 refresh는 쿠키로 오가는 *동시에* 서버가 사본을 들고 검증하는 토큰이었다. V2는 이 사본을 들어낸다. (가르는 두 개념 — **transport**: 어떻게 오가나. **server-side state**: 서버가 사본·상태를 드나. V1은 전자는 갈라 뒀고 후자는 refresh에 대해 들었다.)

## 1. refresh = 무상태 서명 JWT — Redis 사본 제거

refresh를 self-contained 서명 JWT로 두고, 검증은 **서명·만료·클레임 검사만**으로 한다 — 서버 사본을 조회하지 않는다. 근거는 access가 무상태 서명 JWT인 것과 같다: 서명이 위변조를, 만료가 수명을 막으면 *검증*을 위해 서버가 사본을 들 이유가 없다. 서버 사본이 정당화되는 자리는 검증이 아니라 *폐기*(§3)인데, 그건 별도 요구라 검증 비용으로 끌어오지 않는다.

파급 — refresh 저장을 들어내면 [[RFC-018-caching-redis-role]]가 식별한 Redis의 "인증 부산물 = must-not-evict" 워크로드가 사라진다. Redis에 남는 건 손실 허용 조정 상태(레이트리밋·락·디듀프)뿐 → **단일 durability 등급**, `allkeys-lru` 하나로 충분([[03-read-model]] Redis 절). "Redis를 기능별로 쪼개야 하나"라는 물음이 *쪼개기가 아니라 워크로드 제거로* 닫힌다.

## 2. transport — V1 계승 + SameSite 보강

분담은 V1 그대로 잇는다: **refresh = `HttpOnly`·`Secure` 쿠키, access = body → Authorization 헤더.** 단 V1이 빠뜨린 `SameSite`(기본 Lax)와 path 스코프를 채워 `/refresh`의 CSRF 표면을 막는다.

비대칭이 원리적이다 — access를 body로 내리는 선택이 *오히려 CSRF 노출을 낮춘다*. 공격 페이지가 victim 쿠키로 `/refresh`를 위조 호출해도, 새 access는 cross-origin **body**라 그 응답을 읽지 못한다. 자동 전송이 필요한 refresh는 쿠키, CSRF 면역이 필요한 access는 헤더 — 우연이 아니라 원리다. (access의 *클라이언트* 저장은 in-memory가 정답 — localStorage는 XSS-readable이라 refresh를 `HttpOnly`로 숨긴 보람을 깬다. 새로고침 시엔 refresh 쿠키로 재발급한다.)

## 3. 폐기(denylist)는 포기 — 무엇을 잃고 무엇으로 덮나

무상태 토큰의 본질적 빈틈은 즉시 폐기다. **즉시 강제 폐기(denylist/revocation)를 V2 기본에서 포기한다.** 근거 셋 —

1. 폐기 목록을 들면 §1에서 막 들어낸 must-not-evict 서버 상태를 *다시* 들이는 것이라, Redis 역할 축소가 무의미해진다.
2. V1도 즉시 폐기를 못 했다 — `signOut`이 쿠키만 지웠으니, 무상태로 가도 잃을 게 없다.
3. 짧은 access TTL + refresh 만료로 대부분의 "로그아웃" 요구가 덮인다 — 쿠키 삭제로 클라가 refresh를 잃으면 잔여 access는 곧 자연 만료된다.

rotation(매 `/refresh`마다 새 refresh 발급)은 잇는다. 다만 **재사용 탐지 + 강제 로그아웃**은 포기하지 *않고* 든다 — 단, denylist를 되살리지 않고 *최소 상태*로. `authenticate` 테이블(쓰기 모델)에 `current_refresh_jti` 컬럼 하나를 두고, 발급한 refresh의 `jti`만 적어 둔다. 검증은 여전히 무상태(§1)다 — 일반 API는 JWT 서명만 보고, DB 조회는 `/refresh` 때만 일어난다. `/refresh`가 들고 온 refresh의 `jti`를 `current_refresh_jti`와 대조해서, **일치하면** 새 refresh를 발급하며 컬럼을 갱신하고, **불일치하면**(rotation으로 폐기됐어야 할 옛 refresh가 다시 쓰임 = 도난 의심) 컬럼을 `NULL`로 밀어 전 세션을 무효화한다. 강제 로그아웃도 같은 손잡이다 — 컬럼을 `NULL`로 두면 다음 `/refresh`가 대조에 실패하고, 잔여 access는 곧 자연 만료된다.

이건 §3 첫머리의 "denylist 포기"와 모순되지 않는다. denylist는 *발급한 모든 토큰*을 잔여 수명만큼 들고 있어야 하는 must-not-evict 목록이지만, `current_refresh_jti`는 주체당 *최신 refresh 한 개*의 식별자뿐이고 쓰기 모델 위에 산다 — Redis must-not-evict 워크로드를 되살리지 않는다(§1 파급 유지). 무상태 토큰이 본질적으로 못 막는 "즉시 강제 폐기"는 여전히 포기하되, rotation이 *이미 만드는* "옛 refresh는 폐기됐다"는 사실을 한 컬럼으로 강제하는 것뿐이다.

> **예외** — "즉시 강제 로그아웃"이 도메인·규제 요구로 실제 입증되면, 그때만 [[RFC-018-caching-redis-role]]의 must-not-evict 등급을 되살려 denylist를 둔다(토큰 잔여 수명만큼만 사는 목록이라 TTL로 자청소). 모델 기본은 "폐기 없음", 예외는 "요구가 입증될 때만". 이는 [[13-authorization]]의 "역할 = 토큰 발급 시점 스냅샷"이 남긴 권한 강등 즉시성 문제와 한 몸이다 — 토큰 수명을 짧게 잡을수록 폐기 없이도 덮인다.

## Design / 후속으로 넘기는 것

- refresh JWT 클레임 구성·TTL·서명 키와 access TTL의 구체 값 — [[13-authorization]] "발급 시점 스냅샷"의 stale 창과 한 몸으로 정한다(수명이 즉시성을 대신).
- 쿠키 속성 확정 — `SameSite` Lax vs Strict(외부 링크 진입 영향), path 스코프(`/` vs `/…/refresh`), 도메인.
- rotation 정책 — 매 refresh 발급의 만료 연장 방식(슬라이딩 vs 고정 만료)과 `current_refresh_jti` 갱신·대조의 동시성(같은 주체의 병렬 `/refresh` 경합) 처리.
- 즉시 폐기가 요구로 입증될 경우의 denylist 부활 트리거 — [[RFC-018-caching-redis-role]] must-not-evict 등급과 함께.
- 서명 키의 *호스팅·회전*(키 롤오버, OIDC/Vault 등) — 인증 인프라 백로그 T-13·[[09-deployment-runtime]].
- V1의 `General`/`Seller` 분리 sign-in/refresh/signout 컨트롤러를 V2에서 통합할지.

## 관련 문서

- 근거: [[RFC-019-auth-token-transport]]
- [[13-authorization]] · [[RFC-015-authorization-model]] · [[RFC-018-caching-redis-role]] · [[12-api-contract]] · [[02-write-model]] · [[09-deployment-runtime]]
