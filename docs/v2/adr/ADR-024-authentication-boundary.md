# ADR-024: 인증 경계 — 엣지 1회 검증(모델 A) + 기성 프록시 무상태 구현 + Spring Authorization Server 직접 구축

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-020-authentication-boundary-gateway]] · **설계**: [[DESIGN-010-deployment-runtime]]
- **연관 ADR**: [[ADR-017-authorization-model]] · [[ADR-020-auth-token-transport]]

---

## 맥락과 문제 (Context and Problem Statement)

V1은 인증이 앱 안에 있었다 — `JwtFilter`가 매 요청 Bearer 토큰을 풀어 `SecurityContextHolder`에 신원을 앉혔고, 토큰 발급(sign-in)·검증·refresh가 모두 같은 모놀리식 앱 안에서 일어났다. V2는 컨텍스트가 여럿(command·query·projector…)이라 각자 토큰을 푸는 대신 인증을 앱 밖으로 꺼내기로 했으나, 그 경계 자체 — 검증을 어디서 하고, 무엇으로 구현하고, 발급을 무엇이 지는가 — 를 결정한 문서가 없었다. [[ADR-017-authorization-model]]·[[ADR-020-auth-token-transport]]는 둘 다 "게이트웨이·인증 서버가 토큰을 검증해 신원·역할 클레임을 확정한다"를 전제로만 깔고 있어, 그 전제가 서지 않으면 두 ADR이 공중에 뜬다.

**토큰 검증의 위치(엣지 1회 vs 서비스마다)와 구현 수단(기성 프록시 vs 직접 만든 게이트웨이 앱), 그리고 그 검증이 기대는 발급 주체(인증 서버)를 확정해야 한다.**

## 결정 동인 (Decision Drivers)

- 컨텍스트가 여럿인 V2에서 서비스마다 토큰을 풀면 검증 로직 중복과 서명 키 확산이 반복된다.
- 검증을 엣지로 단일화하면 네트워크 신뢰 가정(헤더를 믿는다)이 따라붙는다 — 강제 장치 없이는 위조로 뚫린다.
- "인증을 앱 밖으로" 꺼내면서 또 다른 앱(게이트웨이)을 직접 만드는 것은 목적과 모순된다.
- access 검증은 무상태(서명·만료·클레임)이므로 상태 검사(JTI)를 엣지에 지우지 않아야 기성 프록시 설정만으로 끝난다.
- 인증 서버 결정은 블랙박스 IdP로는 [[ADR-020-auth-token-transport]]가 직접 정한 JTI 재사용 탐지를 코드로 남길 수 없다.

## 검토한 선택지 (Considered Options)

**검증 위치**
- **모델 A — 엣지 1회 검증 + 헤더 전파** — 게이트웨이가 엣지에서 토큰을 한 번 검증하고, 신원·역할을 헤더로 다운스트림에 전달. 앱은 다시 풀지 않는다.
- **모델 B — 서비스마다 Resource Server로 재검증(제로트러스트)** — 네트워크 신뢰 가정을 지지 않는다. 서비스 메시(Istio 등)가 있으면 사이드카 mTLS가 비용을 줄인다.

**엣지 검증 구현**
- **① 게이트웨이 앱 직접(Spring Cloud Gateway)** — 라우팅+검증+필터를 직접 만든 Spring 앱이 통째로 진다.
- **② 기성 프록시 자체 검증(Envoy/nginx ingress)** — ingress controller가 인증 서버 JWKS URL을 가리켜 설정만으로 무상태 JWT 검증.
- **③ 기성 프록시 + 외부 인가(ext_authz/auth_request)** — 프록시가 매 요청 통과 여부를 별도 판정 앱에 묻는다.

**인증 서버**
- **기성 IdP(Keycloak 등)** — 발급·JWKS·rotation·세션 무효화 내장, 빠르게 세울 수 있으나 내부가 블랙박스.
- **Spring Authorization Server 직접** — 발급·JWKS 노출·refresh rotation·JTI 재사용 탐지를 직접 구현.

## 결정 (Decision Outcome)

**채택: 모델 A(엣지 1회 검증 + 헤더 전파) + ②(기성 프록시 무상태 검증, 게이트웨이 앱 미구현) + Spring Authorization Server 직접 구축.**

| # | 결정 |
|---|------|
| 1 | 검증 위치 = **모델 A** — 게이트웨이가 엣지에서 1회 검증(서명·만료·클레임), 검증된 신원·역할을 헤더로 다운스트림에 전파. 도메인 앱은 **pre-authenticated 필터만** 두고 다시 풀지 않는다 — V1 `JwtFilter`·JWKS·서명검증 로직은 제거 |
| 2 | 모델 A의 두 의무 — (1) 게이트웨이가 인입 신원 헤더(`X-User-Id` 등)를 **strip/덮어쓰기**해 클라이언트 자칭 헤더를 무효화한다 (2) **NetworkPolicy**로 "게이트웨이만 앱에 도달"을 강제한다. 이 둘을 지키지 않으면 모델 A는 헤더 위조로 뚫린다 |
| 3 | 모델 B는 **미채택** — 서비스 메시(Istio 등) 도입 시 사이드카 mTLS로 자연스럽게 전환할 수 있는 업그레이드 경로로 남긴다 |
| 4 | 엣지 검증 구현 = **② 기성 프록시 무상태 JWT 검증**(Envoy/nginx ingress, JWKS 참조). ①(SCG 게이트웨이 앱 직접)은 **기각** — "인증을 앱 밖으로" 꺼내면서 또 앱을 세우는 모순 |
| 5 | ③(ext_authz/auth_request)은 **조건부** — per-user 세밀 rate limit 등 매 요청 상태 검증이 실증적으로 필요해질 때만 도입. 거친 rate limit(IP·경로)은 프록시 내장 기능으로 흡수 |
| 6 | 인증 서버 = **Spring Authorization Server 직접 구현**. 기성 IdP(Keycloak 등)는 **기각** — 블랙박스라 [[ADR-020-auth-token-transport]]가 정한 JTI 재사용 탐지를 코드로 남길 수 없다. 발급·JWKS 노출·refresh rotation·JTI 재사용 탐지·**로그인/로그아웃**(V1 `General`/`Seller` 분리 컨트롤러를 통합)에 집중 |
| 7 | JTI 상태 검사는 **refresh 제출 시점에만** 일어난다(rotate된 refresh 재등장 = 탈취 의심). refresh는 인증 서버 엔드포인트로만 가므로, **매 API 요청(access 검증)에선 JTI를 보지 않는다 → 엣지는 무상태(②)를 유지** |

이 ADR은 **어디서 검증하고(엣지 vs 서비스별) 무엇으로 구현하는가(프록시 vs 앱)**만 다룬다. 인가 규칙(소유권 검사 위치)은 [[ADR-017-authorization-model]], 토큰 transport·rotation·재사용 탐지의 구체(쿠키/헤더 분담, `current_refresh_jti` 컬럼, denylist 포기 등)는 [[ADR-020-auth-token-transport]]가 다룬다 — 이 문서는 그 결정들을 반복 서술하지 않는다.

### 결과 (Consequences)

- 좋은 점: 검증 로직과 서명 키가 엣지 한 곳으로 모여, 서비스가 늘어나도 검증 코드가 각자 중복되지 않는다.
- 좋은 점: 기성 프록시 설정만으로 검증이 끝나 게이트웨이 앱을 직접 만들고 운영할 필요가 없다.
- 좋은 점: JTI 상태 검사가 refresh 경로로만 격리돼 엣지가 상태를 지지 않는다 — access 검증이 무상태로 남아 ②의 전제가 흔들리지 않는다.
- 트레이드오프: 모델 A는 네트워크 신뢰 가정(헤더를 믿는다)을 진다 — 헤더 strip과 NetworkPolicy 중 하나라도 빠지면 위조로 뚫린다.
- 트레이드오프: per-user 세밀 rate limit 등 상태 의존 검증이 실제로 필요해지면 ③(ext_authz)로 전환해야 한다 — 지금은 그 비용을 지불하지 않는다.
- 트레이드오프: 서비스 메시 없이 모델 A를 쓰는 한 제로트러스트(모델 B)의 방어 심도는 없다 — Istio 등 도입 전까지는 NetworkPolicy가 유일한 우회 차단선이다.

### 확인 (Confirmation)

- 도메인 앱 코드에 V1 `JwtFilter`·JWKS 조회·서명검증 로직이 남아 있지 않은지(코드 리뷰) — pre-authenticated 필터만 존재해야 한다.
- 게이트웨이 설정이 인입 신원 헤더를 strip/덮어쓰기하는지, NetworkPolicy가 "게이트웨이만 앱 도달"을 강제하는지 통합 테스트/설정 리뷰로 확인한다.
- 일반 API 요청 경로가 JTI 상태 조회를 수행하지 않는지(무상태 유지) 코드 리뷰로 확인한다 — JTI 조회는 refresh 엔드포인트에서만 나타나야 한다.

## 선택지 상세 (Pros and Cons of the Options)

### 모델 B — 서비스마다 재검증 (미채택)
- 장점: 네트워크 신뢰 가정을 지지 않는다 — 제로트러스트.
- 단점: 서비스 메시 없이 쓰면 검증 설정이 서비스마다 붙어 중복이 재발한다.
- 기각 사유: 서비스 메시(Istio) 도입 전까지는 모델 A + NetworkPolicy로 같은 목적(현업 표준 패턴)을 더 적은 비용으로 달성한다. 메시 도입 시 전환 경로로 남겨둔다.

### ① 게이트웨이 앱 직접(SCG) (기각)
- 장점: 라우팅·필터를 한 코드베이스에서 유연하게 제어할 수 있다.
- 단점: 별도 워크로드·홉 +1, "인증을 앱 밖으로"라는 목적과 모순.
- 기각 사유: access 검증이 무상태라 기성 프록시 설정만으로 충분하다.

### 기성 IdP(Keycloak 등) (기각)
- 장점: 발급·JWKS·rotation·세션 무효화가 내장돼 빠르게 세울 수 있다.
- 단점: 내부가 블랙박스라 JTI 재사용 탐지 같은 직접 정한 메커니즘이 "설정 토글"로 흡수돼 코드로 남지 않는다.
- 기각 사유: [[ADR-020-auth-token-transport]]가 이미 JTI 재사용 탐지·강제 로그아웃을 직접 정했고, 그 결정을 코드로 구현하려면 Spring Authorization Server가 필요하다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 구체 프록시 제품 택일(→ 아래 2026-07-20 개정에서 **Envoy Gateway** 확정)·배치, 라우트·필터 설정, 클레임 헤더 이름·형식, 신원 헤더 strip 규칙과 NetworkPolicy(또는 mTLS)의 구체 구현 — [[DESIGN-010-deployment-runtime]] 소관. 인증 서버 구현 설정(키 회전·OIDC 흡수, V1 `General`/`Seller` sign-in/refresh/signout 컨트롤러 이관 절차)은 cycle 소관.
- 관련: [[RFC-020-authentication-boundary-gateway]] · [[DESIGN-010-deployment-runtime]] · [[ADR-017-authorization-model]] · [[ADR-020-auth-token-transport]]

---

## 개정 (2026-07-20) — 엣지 프록시 제품 확정: Envoy Gateway (Gateway API)

결정 4는 "② 기성 프록시 무상태 JWT 검증"까지만 정하고 **구체 제품 택일**은 [[DESIGN-010-deployment-runtime]]·구현 사이클로 미뤄 뒀다(위 "추가 정보"). [[07-k8s-edge-gateway-study]] 검토를 사용자가 수용해 그 미결정을 닫는다.

**결정: 엣지 프록시 = Envoy Gateway(Gateway API 구현). ingress-nginx는 기각.**

- **ingress-nginx 기각** — OSS엔 무상태 JWT 검증이 1급 기능이 아니다(NGINX Plus 상용). OSS에선 `auth_request`로 외부 검증 앱(oauth2-proxy 등)을 하나 더 세워야 하는데, 이는 결정 4가 피하려던 "인증 위해 앱 세우기"로 미끄러진다 — ②가 아니라 ③(ext_authz)로 넘어가 이 ADR 취지와 어긋난다.
- **Envoy Gateway가 ②를 곧이곧대로 실현** — `SecurityPolicy`에 인증 서버 JWKS URL을 걸면 설정만으로 무상태 검증, `claimToHeaders`로 클레임→헤더 주입 내장. `SecurityPolicy`가 `HTTPRoute` 단위라 `/api/**`엔 JWT 정책, `/auth/**`엔 미부착 → 결정 1의 "게이트웨이는 거치되 인증만 bypass"가 앱 없이 선언으로 성립.
- **승격 경로 유지** — 결정 2(헤더 strip·NetworkPolicy)·결정 5(ext_authz)·모델 B(mesh mTLS)가 모두 한 제품(내부 엔진 Envoy) 안에서 열려 있다. Gateway API는 표준이고 구세대 ingress controller 개념을 대체한다.

이로써 [[DESIGN-010-deployment-runtime]]의 SCG(=기각된 ①)·ingress-nginx 서술도 Envoy Gateway로 정합화한다(해당 문서 2026-07-20 개정).

**확인(이 선택 한정)**: 확정 근거는 로컬 실습으로 보강한다 — kind/k3d + Envoy Gateway로 경로별 JWT skip이 선언만으로 되는지, 검증 후 `X-User-*` 주입·클라 자칭 헤더 strip이 되는지 확인([[07-k8s-edge-gateway-study]] §5). 실습이 이 전제를 깨면 재검토.
