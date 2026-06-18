# RFC-021 — 인증 경계: API 게이트웨이 + 인증 서버 (k3s 인클러스터)

- **상태**: 합의됨 (2026-06-17) · design [[09-deployment-runtime]] 반영 · ADR 비준 대기
- **선행**: [[RFC-016-authorization-model]] · [[RFC-020-auth-token-transport]] · [[RFC-008-deployment-infra-ops]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[09-deployment-runtime]] 워크로드 토폴로지 보강 + 신규 ADR(인증 경계)

## 맥락

V1에서 인증은 앱 *안*에 있었다. `JwtFilter`가 매 요청 Bearer 토큰을 풀어 `SecurityContextHolder`에 신원을 앉혔고, 토큰 발급(sign-in)·검증·refresh가 모두 같은 모놀리식 앱 안에서 일어났다. "이 호출자가 누구인가"를 앱이 직접 풀었다.

V2는 인증을 앱 밖으로 꺼낸다. 그런데 [[RFC-016-authorization-model]](인가)·[[RFC-020-auth-token-transport]](토큰)이 둘 다 *"게이트웨이·인증 서버가 토큰을 검증해 신원·역할 클레임을 확정한다"*를 **전제로 깔고** 동작하는데, 정작 그 전제 — 인증 경계 자체 — 를 결정한 문서가 없다. design_doc 13·ADR-17이 출처를 [[12-api-contract]]·[[09-deployment-runtime]]로 떠넘기지만 거기에도 없다. 이 RFC가 그 빈 전제를 채운다.

가를 두 개념: **인증(authentication)** = 이 호출자가 누구인가(토큰 발급·검증). **인가(authorization)** = 검증된 주체가 뭘 해도 되나([[RFC-016-authorization-model]]). 이 RFC는 전자의 *경계와 토폴로지* — 누가 발급하고, 어디서 검증하며, 어디에 배치되나 — 만 정한다. 인가 규칙은 RFC-016, 토큰 모델(무상태·폐기 포기)은 RFC-020이 이미 잡았다.

시나리오: USER가 예약 command를 보낸다. (1) 토큰은 *어디서* 검증되나 — 앱마다? 한 곳에서? (2) 발급은 *누가* — 예약 앱이? 별도 서버가? (3) 그 컴포넌트는 *어디* 사나.

## 논의

### 검증은 엣지에서 한 번 — 앱은 토큰을 다시 풀지 않는다 (모델 A)

V1은 모든 앱이 `JwtFilter`로 토큰을 풀었다. V2는 컨텍스트가 여럿(command·query·projector…)이라 각자 토큰을 푸는 건 검증 로직 중복 + 서명 키를 모든 워크로드에 흩뿌리는 것이다.

택: **API 게이트웨이가 엣지에서 토큰을 한 번 검증(서명·만료·클레임)하고, 검증된 신원·역할을 헤더로 다운스트림에 전달**한다. 앱은 토큰을 다시 풀지 않고 전달된 클레임을 신뢰한다(모델 A). 이게 [[RFC-016-authorization-model]] "역할=엣지"·[[RFC-020-auth-token-transport]] 클레임 전파가 서는 토대다. 게이트웨이는 거친 역할 게이트도 겸한다.

모델 A는 두 가지를 **의무로** 진다 — 안 지키면 A는 뚫린다.
- **(헤더 strip)** 게이트웨이는 클라이언트가 보낸 신원 헤더(`X-User-Id` 등)를 **반드시 제거/덮어쓰기**한다. 안 하면 클라가 `X-User-Id: 남`을 끼워 위조한다.
- **(우회 차단)** **NetworkPolicy로 "게이트웨이만 앱에 도달"**을 강제한다. 안 하면 게이트웨이를 우회해 앱에 직접 위조 헤더를 보낸다.

이 둘이 모델 A의 비용이다 — 헤더 신뢰는 곧 *네트워크 신뢰* 가정이다. (대안 모델 B = 서비스마다 Resource Server로 JWKS 재검증(제로트러스트). 더 정석이고 네트워크 신뢰가 덜 치명적이지만 검증 설정이 서비스마다. **V2 기본은 A**, B는 분산 신뢰가 빡빡해질 때의 업그레이드 경로로 남긴다.)

### 발급은 별도 인증 서버로 — 예약 도메인과 분리

토큰 발급(sign-in)·refresh rotation·서명 키 보유는 예약 도메인의 책임이 아니다. V1은 이게 모놀리식 앱에 섞여 있었다. 택: **별도 인증 서버**로 분리한다 — `user`·`authenticate` 컨텍스트(상태+Outbox, [[02.selective-event-sourcing-scope]])를 다루고, [[RFC-020-auth-token-transport]]의 무상태 서명 JWT(RS256)를 발급·rotation하며, 게이트웨이·서비스가 검증에 쓸 **JWKS 엔드포인트**를 노출한다. 도메인 앱은 발급을 모른다.

### 제품 — Spring Cloud Gateway + Spring Authorization Server

택: 엣지 게이트웨이 = **Spring Cloud Gateway**(reactive, 필터로 JWT 검증·역할 게이트·레이트리밋[[[19.caching-redis-role]] Redis 카운터]), 인증 서버 = **Spring Authorization Server**(OIDC·JWKS 노출). 근거 — 스택이 Spring/Kotlin이라 인증 로직을 코드/필터로 두는 게 자연스럽고, 둘 다 정석(공식) 컴포넌트라 학습가치가 높다([[v2-optimize-for-learning-not-cost]]). Keycloak(풀 IdP)·자작 발급은 각각 과하거나 비표준이라 기각.

### 배치 — k3s/EKS 인클러스터

게이트웨이·인증 서버를 관리형(클라우드 API GW·Cognito)이 아니라 **클러스터 안의 워크로드**로 둔다. 근거: V2 학습 목표가 K8s 토폴로지를 직접 운영([[12.kafka-hosting-msk-vs-self-managed]] Strimzi 자가운영과 같은 결), 로컬 k3s~EKS 패리티([[RFC-008-deployment-infra-ops]])를 위해 게이트웨이도 같은 평면에 있어야 인증·리스너 동작 정합을 로컬에서 검증한다. 관리형은 로컬 재현 불가라 패리티가 깨진다. **트래픽 흐름**: `Client → Ingress(ingress-nginx, TLS 종단) → Spring Cloud Gateway(검증·필터) → command/query 서비스`. ingress 제품(nginx) 같은 세밀 선택은 [[09-deployment-runtime]] 배포 디테일이지 이 RFC 사안이 아니다.

### 비동기 command의 인증 신선도 (열어둠)

엣지 검증 시점과 핸들러 실행 시점(202 비동기)이 어긋나, 그 사이 토큰이 만료/강등될 수 있다([[04-design-completeness-audit]] 지적). [[RFC-020-auth-token-transport]]가 즉시 폐기를 포기했으니 in-flight 통과는 받아들이는 게 기본선이나, 민감 command의 재검증 필요 여부는 [[RFC-016-authorization-model]] 민감도 분류와 함께 Design에서.

## Design으로 넘기는 것

- 게이트웨이 제품 구성 — Spring Cloud Gateway 라우트·필터, ingress(nginx) ↔ SCG 역할 분담, 클레임 전달 형식(헤더 이름·서명 헤더 vs 평문). → [[09-deployment-runtime]]·[[12-api-contract]]
- 모델 A 강제 장치 — 인입 신원 헤더 strip 규칙, "게이트웨이만 앱 도달" NetworkPolicy(또는 mTLS) 구체. → [[09-deployment-runtime]]
- 인증 서버 구현 — Spring Authorization Server 설정, 키 회전·OIDC·외부 IdP 흡수. → 인프라 백로그 T-13·[[RFC-008-deployment-infra-ops]]
- 비동기 command 인증 신선도 재검증 정책. → [[RFC-016-authorization-model]]
- V1 `General`/`Seller` 분리 sign-in/refresh/signout 컨트롤러의 인증 서버 통합. → [[RFC-020-auth-token-transport]]와 함께
- 🌱 모델 B(서비스 Resource Server) 승격 트리거 — 분산 신뢰 요구가 네트워크 신뢰 가정을 넘어설 때.

## 관련 문서

- [[RFC-016-authorization-model]] · [[RFC-020-auth-token-transport]] · [[RFC-008-deployment-infra-ops]] · [[RFC-013-command-query-api-contract]] · [[RFC-002-decision-queue]]
- 설계: [[09-deployment-runtime]] · [[12-api-contract]] · [[13-authorization]] · [[16-auth-token]]
- ADR: [[17.authorization-model]] · [[20.auth-token-transport]] · [[02.selective-event-sourcing-scope]] · [[12.kafka-hosting-msk-vs-self-managed]]
