# DESIGN-017: Auth Token (인증 토큰 transport·무상태성·폐기)

- **상태**: Accepted
- **작성자**: Team
- **작성일**: 2026-06-30
- **최종 수정일**: 2026-06-30
- **관련 RFC**: RFC-019-auth-token-transport · RFC-018-caching-redis-role · RFC-015-authorization-model
- **관련 ADR**: -
- **관련 Design Doc**: DESIGN-001-design-overview · DESIGN-014-authorization · DESIGN-013-api-contract · DESIGN-003-write-model · DESIGN-010-deployment-runtime

---

## 1. Background

V1은 access를 응답 body로 내리고, refresh를 `HttpOnly`·`Secure` 쿠키(`RefreshTokenDefinitions`, `path=/`)로 심었다. 클라는 access를 Authorization(Bearer) 헤더로 들고(`JwtFilter`가 푼다), refresh는 브라우저가 `/refresh`에 자동 전송한다. **"refresh=쿠키, access=헤더"는 V2가 새로 정하는 게 아니라 V1 계승**이다.

V1은 그 위에 하나 더 얹었다. refresh를 Redis에도 저장(`SaveGeneralUserRefreshToken`)해 `/refresh`마다 조회·대조한 뒤 새 access·refresh를 발급(rotation)했다. 즉 V1 refresh는 쿠키로 오가는 *동시에* 서버가 사본을 들고 검증하는 토큰이었다. V2는 이 사본을 들어낸다.

두 개념을 구분한다.
- **transport**: 어떻게 오가나.
- **server-side state**: 서버가 사본·상태를 드나.

V1은 transport는 갈라 뒀고, server-side state는 refresh에 대해 들었다. 이 문서는 인증 토큰이 클라이언트와 서버 사이를 *어떻게 오가고*(transport), 서버가 그 사본·상태를 *드는가*(server-side state)를 확정한다.

"검증된 주체가 이 자원에 이 행위를 해도 되는가"(인가)는 [[DESIGN-014-authorization]]의 몫이고, "누가 토큰을 발급·검증하나"의 호스팅·키 회전은 인증 인프라(백로그 T-13)·[[DESIGN-010-deployment-runtime]]의 몫이다. 여긴 토큰 *모델*만 정한다.

## 2. Goal

- 인증 토큰의 transport 모델 확정: refresh=쿠키, access=body→헤더 분담.
- 무상태성 확정: refresh도 self-contained 서명 JWT, Redis 사본 제거.
- 폐기 모델 확정: denylist 기본 포기, `current_refresh_jti` 단일 컬럼으로 재사용 탐지 + 강제 로그아웃 최소 구현.

## 3. Non-Goal

- **키 회전**: 서명 키의 호스팅·롤오버·OIDC/Vault 연동 — 인증 인프라 백로그 T-13·[[DESIGN-010-deployment-runtime]]의 몫.
- **OIDC / 외부 IdP 연동**: 본 문서 범위 밖.
- **인증 인프라 호스팅**: 어디서 JWT를 발급·검증하나 — 배포 사이클의 몫.
- **인가 결정**: 검증된 주체가 자원에 행위를 할 수 있는지 — [[DESIGN-014-authorization]]의 몫.

## 4. Proposed Solution

### 4.1 refresh = 무상태 서명 JWT — Redis 사본 제거

refresh를 self-contained 서명 JWT로 두고, 검증은 **서명·만료·클레임 검사만**으로 한다. 서버 사본을 조회하지 않는다. 근거는 access가 무상태 서명 JWT인 것과 같다. 서명이 위변조를, 만료가 수명을 막으면 *검증*을 위해 서버가 사본을 들 이유가 없다. 서버 사본이 정당화되는 자리는 검증이 아니라 *폐기*(§4.3)인데, 그건 별도 요구라 검증 비용으로 끌어오지 않는다.

파급 — refresh 저장을 들어내면 RFC-018-caching-redis-role이 식별한 Redis의 "인증 부산물 = must-not-evict" 워크로드가 사라진다. Redis에 남는 건 손실 허용 조정 상태(레이트리밋·락·디듀프)뿐. **단일 durability 등급**, `allkeys-lru` 하나로 충분([[DESIGN-018-caching]] §3). "Redis를 기능별로 쪼개야 하나"라는 물음이 *쪼개기가 아니라 워크로드 제거로* 닫힌다.

### 4.2 transport — V1 계승 + SameSite 보강

분담은 V1 그대로 잇는다: **refresh = `HttpOnly`·`Secure` 쿠키, access = body → Authorization 헤더.** 단 V1이 빠뜨린 `SameSite`(기본 Lax)와 path 스코프를 채워 `/refresh`의 CSRF 표면을 막는다.

비대칭이 원리적이다. access를 body로 내리는 선택이 *오히려 CSRF 노출을 낮춘다*. 공격 페이지가 victim 쿠키로 `/refresh`를 위조 호출해도, 새 access는 cross-origin **body**라 그 응답을 읽지 못한다. 자동 전송이 필요한 refresh는 쿠키, CSRF 면역이 필요한 access는 헤더 — 우연이 아니라 원리다.

access의 *클라이언트* 저장은 in-memory가 정답. localStorage는 XSS-readable이라 refresh를 `HttpOnly`로 숨긴 보람을 깬다. 새로고침 시엔 refresh 쿠키로 재발급한다.

### 4.3 폐기(denylist)는 포기 — 무엇을 잃고 무엇으로 덮나

무상태 토큰의 본질적 빈틈은 즉시 폐기다. **즉시 강제 폐기(denylist/revocation)를 V2 기본에서 포기한다.** 근거 셋 —

1. 폐기 목록을 들면 §4.1에서 막 들어낸 must-not-evict 서버 상태를 *다시* 들이는 것이라, Redis 역할 축소가 무의미해진다.
2. V1도 즉시 폐기를 못 했다 — `signOut`이 쿠키만 지웠으니, 무상태로 가도 잃을 게 없다.
3. 짧은 access TTL + refresh 만료로 대부분의 "로그아웃" 요구가 덮인다 — 쿠키 삭제로 클라가 refresh를 잃으면 잔여 access는 곧 자연 만료된다.

rotation(매 `/refresh`마다 새 refresh 발급)은 잇는다. 단 **재사용 탐지 + 강제 로그아웃**은 포기하지 *않고* 든다. denylist를 되살리지 않고 *최소 상태*로.

`authenticate` 테이블(쓰기 모델)에 `current_refresh_jti` 컬럼 하나를 두고, 발급한 refresh의 `jti`만 적어 둔다. 검증은 여전히 무상태(§4.1)다 — 일반 API는 JWT 서명만 보고, DB 조회는 `/refresh` 때만 일어난다.

`/refresh`가 들고 온 refresh의 `jti`를 `current_refresh_jti`와 대조해서:
- **일치하면**: 새 refresh를 발급하며 컬럼을 갱신한다.
- **불일치하면**: rotation으로 폐기됐어야 할 옛 refresh가 다시 쓰임 = 도난 의심. 컬럼을 `NULL`로 밀어 전 세션을 무효화한다.

강제 로그아웃도 같은 손잡이다 — 컬럼을 `NULL`로 두면 다음 `/refresh`가 대조에 실패하고, 잔여 access는 곧 자연 만료된다.

이건 §4.3 첫머리의 "denylist 포기"와 모순되지 않는다. denylist는 *발급한 모든 토큰*을 잔여 수명만큼 들고 있어야 하는 must-not-evict 목록이지만, `current_refresh_jti`는 주체당 *최신 refresh 한 개*의 식별자뿐이고 쓰기 모델 위에 산다. Redis must-not-evict 워크로드를 되살리지 않는다(§4.1 파급 유지).

무상태 토큰이 본질적으로 못 막는 "즉시 강제 폐기"는 여전히 포기하되, rotation이 *이미 만드는* "옛 refresh는 폐기됐다"는 사실을 한 컬럼으로 강제하는 것뿐이다.

> **스코프 — 주체당 단일 활성 세션 (의도, 2026-07-06 확정, 트리아지 C25).**
> `current_refresh_jti`가 주체당 하나이므로 이 모델은 **주체당 단일 활성 refresh 세션**을 의도한다. 다기기·병렬 세션 동시 유지는 **애초 요구가 아니었고 스코프 밖**이다 — 폰에서 rotation하면 노트북 세션이 다음 `/refresh`에서 끊기는 것은 결함이 아니라 이 스코프의 귀결(마지막 로그인 기기가 세션을 잡음). 다기기 동시 세션이 제품 요구로 입증되면 그때 디바이스별 세션 레코드(`current_refresh_jti` → `(subject, device) → jti`)로 확장하되, 기본은 단일 세션이다. 이 결정으로 "단일 jti = 다기기 불가"는 미검토 결함이 아니라 **명시적 스코프**가 된다. (같은 기기 병렬 탭·재시도의 rotation 경합 오탐은 별개 축 — §6 rotation 동시성으로 위임.)

> **예외** — "즉시 강제 로그아웃"이 도메인·규제 요구로 실제 입증되면, 그때만 RFC-018-caching-redis-role의 must-not-evict 등급을 되살려 denylist를 둔다(토큰 잔여 수명만큼만 사는 목록이라 TTL로 자청소). 모델 기본은 "폐기 없음", 예외는 "요구가 입증될 때만". 이는 [[DESIGN-014-authorization]]의 "역할 = 토큰 발급 시점 스냅샷"이 남긴 권한 강등 즉시성 문제와 한 몸이다 — 토큰 수명을 짧게 잡을수록 폐기 없이도 덮인다.

## 5. Alternatives Considered

### 5.1 Redis refresh 저장 유지 (비채택)

V1 방식대로 refresh를 Redis에 저장·대조하는 것. 검증이 stateful해지고, RFC-018-caching-redis-role이 식별한 must-not-evict 워크로드가 유지돼 Redis durability 등급 분리 문제가 남는다. Redis 역할 축소 방향([[DESIGN-018-caching]])과 역행한다. 비채택.

### 5.2 denylist (비채택 — 기본에서 포기)

발급한 모든 토큰을 잔여 수명만큼 Redis에 들고 있는 구조. must-not-evict 워크로드를 되살리고, V1에서도 즉시 폐기를 하지 않았으므로 무상태로 가도 잃을 게 없다. 기본에서 포기. 도메인·규제 요구가 입증될 경우의 부활 경로는 §4.3 예외 참조.

## 6. Details

### 후속으로 넘기는 것

- refresh JWT 클레임 구성·TTL·서명 키와 access TTL의 구체 값 — [[DESIGN-014-authorization]] "발급 시점 스냅샷"의 stale 창과 한 몸으로 정한다(수명이 즉시성을 대신).
- 쿠키 속성 확정 — `SameSite` Lax vs Strict(외부 링크 진입 영향), path 스코프(`/` vs `/…/refresh`), 도메인.
- rotation 정책 — 매 refresh 발급의 만료 연장 방식(슬라이딩 vs 고정 만료)과 `current_refresh_jti` 갱신·대조의 동시성(같은 주체의 병렬 `/refresh` 경합) 처리.
- 즉시 폐기가 요구로 입증될 경우의 denylist 부활 트리거 — RFC-018-caching-redis-role must-not-evict 등급과 함께.
- 서명 키의 *호스팅·회전*(키 롤오버, OIDC/Vault 등) — 인증 인프라 백로그 T-13·[[DESIGN-010-deployment-runtime]].
- V1의 `General`/`Seller` 분리 sign-in/refresh/signout 컨트롤러를 V2에서 통합할지.

## 7. Risks & Mitigations

| 위험 | 완화 |
|------|------|
| 잔여 access 수명 동안 강제 폐기 불가 | 짧은 access TTL; 도메인 요구 입증 시 denylist 부활 경로 확보 |
| `current_refresh_jti` NULL화 후 잔여 access 활성 | access TTL 자연 만료로 덮음; 수명 길이 조정이 핵심 |
| 병렬 `/refresh` 경합으로 `current_refresh_jti` 갱신 충돌 | rotation 동시성 처리 — 후속 사이클로 넘김 |
| localStorage에 access 저장 시 XSS 노출 | in-memory 저장 강제; 클라이언트 구현 가이드라인 |
| `SameSite` 미설정 시 CSRF 표면 | SameSite Lax 기본 + 필요 시 Strict 검토(§6 후속) |

## 8. Appendix

### 8.1 Glossary

| 용어 | 설명 |
|------|------|
| transport | 토큰이 클라이언트·서버 사이를 어떻게 오가는가 (쿠키 vs 헤더 vs body) |
| server-side state | 서버가 토큰의 사본·상태를 보관하는가 |
| denylist | 폐기된 토큰 식별자를 잔여 수명만큼 들고 있는 목록. must-not-evict 워크로드 |
| current_refresh_jti | 주체당 최신 refresh 토큰 JTI 하나만 저장하는 컬럼. 재사용 탐지·강제 로그아웃 손잡이 |
| rotation | 매 `/refresh` 호출마다 새 refresh를 발급하고 이전 것을 폐기하는 정책 |
| must-not-evict | Redis eviction 정책 중 메모리 압박 시 절대 퇴거하지 않아야 하는 등급 |

### 8.2 Reference

- RFC-019-auth-token-transport
- RFC-018-caching-redis-role
- RFC-015-authorization-model
- DESIGN-014-authorization
- DESIGN-018-caching
- DESIGN-013-api-contract
- DESIGN-003-write-model
- DESIGN-010-deployment-runtime

## Changelog

| 날짜 | 변경 내용 |
|------|-----------|
| 2026-07-06 | §4.3에 **주체당 단일 활성 세션 = 의도된 스코프** 명문화(다기기 동시 세션은 애초 요구 아님·스코프 밖, 입증 시 디바이스별 레코드로 확장). 트리아지 **C25** 반영 — Weakness의 "다기기 불가"·"오탐 엔진(다기기 축)"을 스코프 결정으로 해소, access TTL 값·rotation 동시성은 기존 위임 유지. 재사용 탐지 채택(`current_refresh_jti`)과 [[20.auth-token-transport|ADR-20]] "재사용 탐지=기각" 문구 모순은 ADR-20 측 정합화(재사용 탐지는 최소 단일-jti로 채택, 기각된 건 denylist/서버사본). |
| 2026-06-30 | DESIGN-017로 재포맷. 템플릿 구조(Background/Goal/Non-Goal/Proposed Solution/Alternatives/Details/Risks/Appendix) 적용. "관련 문서" → Appendix > Reference 이동. 상호 참조 번호 갱신 |

---

## Weakness (Devil's Advocate 반박 포인트)

- **무상태 refresh + 즉시 폐기 포기 = 탈취 노출 창을 명시적으로 수용했으나 창 크기·보완책이 얕다** — §4.3은 "잔여 access 수명 동안 강제 폐기 불가"를 리스크로 인정하고 완화를 "짧은 access TTL"로 돌린다. 그런데 정작 그 TTL 값은 §6에서 [[DESIGN-014-authorization]]와 함께 후속으로 미뤄, 노출 창의 실제 크기가 이 문서에서 결정되지 않는다. 게다가 refresh는 쿠키 수명 내내 유효하고 서버 사본이 없어 *탈취된 refresh 하나로 만료까지 무제한 rotation*이 가능한데, 디바이스 바인딩·IP/UA 핀·토큰 지문 같은 보완책은 언급조차 없다. "짧은 TTL이 다 덮는다"는 서사가 실측 없이 결론을 지탱한다.

- **재사용 탐지가 오히려 정상 사용자를 강제 로그아웃시키는 오탐 엔진** — §4.3의 `current_refresh_jti` 불일치 → 전 세션 무효화 로직은, 공격자뿐 아니라 *정상 클라이언트의 흔한 경합*에서도 발동한다: 병렬 탭·모바일+웹 동시 로그인·네트워크 재시도로 인한 refresh 중복 전송·rotation 응답 유실 후 재시도. §6이 인정하듯 병렬 `/refresh` 동시성이 미해결인 채, 이 미해결 경합이 곧바로 "도난 의심 → NULL화 → 전 세션 무효화"라는 최대 처벌로 이어진다. 재사용 탐지의 false positive 처리(유예·grace window·refresh 체이닝) 설계 없이 탐지만 켜는 것은 로그아웃 폭탄이다.
  - **부분 해소 (2026-07-06, 트리아지 C25):** 다기기·모바일+웹 동시 로그인 축은 단일 세션 스코프로 제거(§4.3 스코프 노트). 남는 건 **같은 기기 병렬 탭·네트워크 재시도의 rotation 경합** 오탐뿐이고, 이는 grace window·refresh 체이닝으로 다루는 rotation 동시성 문제 — 아키텍처가 아니라 §6 구현 사이클 항목으로 이미 위임됨.

- **`current_refresh_jti` = 주체당 refresh 1개 → 다중 디바이스 동시 세션 불가** — 컬럼이 "최신 refresh 한 개"만 들면, 폰에서 rotation한 순간 노트북의 refresh jti가 stale이 되어 노트북 다음 `/refresh`가 무효화된다. 즉 이 모델은 구조적으로 *동시 다중 세션을 지원하지 못한다*. 예약 시스템에서 한 사용자가 여러 기기를 쓰는 건 일상적인데, 문서는 이 기능 상실을 리스크로도 다루지 않는다 — "최소 상태"의 대가가 제품 요구와 충돌하는지 검토되지 않았다.
  - **✅ 해소 (2026-07-06, 트리아지 C25):** 다기기 동시 세션은 **애초 요구가 아니었다** — 단일 활성 세션이 의도된 스코프임을 §4.3 스코프 노트로 명문화. "기능 상실"이 아니라 명시적 스코프 결정. 다기기 요구 입증 시 디바이스별 세션 레코드로 확장하는 경로만 남김.

- **denylist 포기의 논거 2("V1도 못 했으니 잃을 게 없다")는 부채의 정당화** — §4.3-근거2와 §5.2는 "V1 `signOut`이 쿠키만 지웠으니 무상태로 가도 잃을 게 없다"고 한다. 이는 V1의 미구현을 V2의 요구 부재로 치환하는 논리다. 계정 탈취·비밀번호 변경·관리자 강제 차단 시 "지금 즉시 모든 토큰 무효화"는 보안 기본기이며, V1이 안 했다는 사실은 그 요구가 없었다는 증거가 아니라 V1의 결함일 수 있다. "요구가 입증되면 그때"라는 예외 경로는 사고가 터진 뒤에야 부활하는 사후약방문이다.

- **CSRF 방어를 SameSite Lax + body 비대칭에만 의존** — §4.2는 access를 cross-origin body로 내려 CSRF 면역이라 주장하나, 공격자가 `/refresh` CSRF를 성공시키면 응답을 못 읽어도 *서버 측에서 rotation이 발생*해 victim의 유효 refresh가 교체되고(정상 세션 파괴 = 이 자체가 DoS), SameSite Lax는 top-level GET 네비게이션을 허용하므로 `/refresh`가 GET이거나 method 혼용 시 뚫린다. 진짜 방어인 CSRF 토큰·Origin 검증·Strict 채택은 모두 §6 후속으로 미뤄, 이 문서가 "CSRF 표면을 막는다"고 단언한 것과 실제 보장 사이에 간극이 있다.

- **in-memory access 저장 강제가 SPA UX·리프레시 폭주를 유발** — §4.2는 access를 in-memory에만 두고 새로고침 시 refresh로 재발급하라 한다. 이는 모든 페이지 리로드·새 탭·앱 복귀마다 `/refresh` 왕복을 강제해 인증 서버 부하와 지연을 만들고, 짧은 access TTL과 결합되면 refresh 호출 빈도가 급증한다(재사용 탐지 오탐 확률도 함께 상승). "localStorage는 XSS-readable"이라는 반대급부만 보고, in-memory 강제가 만드는 운영 비용·UX 저하는 계량되지 않았다.

> 본 절은 리뷰용 반박 정리이며, 문서의 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상.
