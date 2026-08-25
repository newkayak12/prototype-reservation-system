# K8s 엣지 게이트웨이 선택 — 학습·비교 노트

> 작성: 2026-07-20 · 상태: **조사용(결정 전)**. 확정되면 이 노트의 결론을 ADR-024/DESIGN-010에 반영하고 이 문서는 참고 자료로 남긴다.
> 목적: SCG(Spring Cloud Gateway)를 안 쓰기로 확정한 뒤, "엣지가 해야 할 일"을 무엇이 지게 할지 K8s 운영 경험 없이도 판단할 수 있게 개념부터 정리한다.

---

## 1. 왜 이 결정이 필요한가

[[RFC-020-authentication-boundary-gateway]](✅ 종결)와 [[ADR-024-authentication-boundary]]는 **"엣지에서 JWT를 한 번 검증하고(모델 A), 별도 게이트웨이 앱은 만들지 않는다(②)"**로 이미 정했다. 그런데 [[DESIGN-010-deployment-runtime]]·[[11-runtime-topology]]·[[09-auth-server-module]]은 옛 그림(Ingress + **Spring Cloud Gateway 앱** 2계층)을 그대로 싣고 있다 — 결정과 문서가 어긋나 있었다.

SCG를 뺐으니, 엣지 검증을 **무엇이** 지느냐가 남은 빈칸이다. RFC-020 논점 2가 "구체 프록시 제품 택일은 DESIGN-010/구현 사이클로 위임"이라 명시했으므로, 이건 **종결된 RFC를 다시 여는 게 아니라 그 RFC가 남겨둔 빈칸을 채우는 일**이다.

---

## 2. 기초 개념 (K8s 처음이라면 여기부터)

### 2.1 "엣지"가 하는 일

엣지(edge) = 클러스터 바깥 트래픽이 처음 닿는 관문. 우리 시스템에서 엣지가 져야 하는 일:

| 기능 | 뜻 | 왜 엣지에서 |
|------|-----|-------------|
| **TLS 종단** | HTTPS 복호화 | 안쪽 통신을 평문/mTLS로 단순화 |
| **JWT 검증** | 토큰 서명·만료·클레임 확인 | 서비스마다 검증 코드 중복 제거([[RFC-020]] 모델 A) |
| **클레임→헤더** | 검증된 신원을 `X-User-Id`/`X-User-Role`로 주입 | 앱은 헤더만 신뢰(pre-authenticated) |
| **인입 헤더 strip** | 클라가 자칭 보낸 `X-User-*`를 제거 | 안 하면 헤더 위조로 뚫림([[ADR-024]] 결정 2) |
| **rate limit** | 과다 호출 차단 | 앱 앞에서 방어 |
| **경로별 정책** | `/auth/**`는 JWT 검증 skip, `/api/**`는 적용 | 로그인은 아직 토큰이 없으니까 |

### 2.2 Ingress vs Gateway API — **이게 핵심 갈림**

K8s에서 "바깥 트래픽을 안쪽 서비스로 라우팅"하는 표준이 두 세대 있다.

- **Ingress** (구세대): 2019년경부터 쓴 리소스. 기능이 빈약해서 각 구현체(ingress-nginx 등)가 **애노테이션**으로 기능을 덕지덕지 확장했다. 표준이 사실상 **동결**됐다 — 새 기능이 안 들어온다.
- **Gateway API** (신세대, 2023 GA): Ingress의 공식 후계자. 역할이 리소스로 쪼개져 있다:
  - `GatewayClass` — 어느 구현체를 쓸지
  - `Gateway` — 리스너·포트·TLS
  - `HTTPRoute` — 경로/호스트 매칭·헤더 조작 규칙
  - (구현체별 확장 정책) — JWT·rate limit 등

**지금 새로 정하는 거라면 Gateway API로 가는 게 맞다.** DESIGN-010이 적은 "ingress-nginx"는 구세대 표현이다.

### 2.3 리버스 프록시 / 사이드카 / 서비스 메시 (용어 정리)

- **리버스 프록시**: 클라 요청을 받아 뒤쪽 서비스로 넘기는 중계자(nginx·Envoy가 대표).
- **서비스 메시**: 모든 파드 옆에 프록시(사이드카)를 붙여 **서비스 간(east-west)** 통신까지 mTLS·검증하는 것(Istio가 대표). 엣지(north-south)만 막는 것보다 훨씬 무겁다. → 우리는 [[ADR-024]]가 "모델 B(제로트러스트)"로 **나중 승격 경로**로만 남겨둔 것.

---

## 3. 후보 비교

### 3.1 Envoy Gateway (Gateway API 구현) — 유력

- **정체**: Gateway API를 구현하는 CNCF 프로젝트. 내부 엔진은 Envoy(고성능 L7 프록시).
- **JWT**: `SecurityPolicy` 리소스에 인증 서버 JWKS URL을 걸면 **설정만으로** 검증. 클레임을 헤더로 매핑하는 기능 내장(`claimToHeaders`류). → [[ADR-024]] ②번이 말한 "기성 프록시가 설정만으로 무상태 검증"과 정확히 일치.
- **경로별 정책**: `SecurityPolicy`가 `HTTPRoute` 단위로 붙는다 → `/api/**`엔 JWT 정책 붙이고 `/auth/**`엔 안 붙이면 끝. "게이트웨이는 거치되 인증만 bypass"가 앱 없이 실현.
- **rate limit**: `BackendTrafficPolicy`로 로컬(인스턴스별, Redis 불필요) + 글로벌(Redis 백엔드 필요) 둘 다.
- **ext_authz**: 미래 per-user 세밀 rate limit이 필요해지면 `SecurityPolicy`의 외부 인가 훅으로 승격([[ADR-024]] 결정 5 경로와 정합).
- **메시 승격**: 내부가 Envoy라 나중에 모델 B(Istio 등 mTLS)로 가는 길이 이어짐.
- **약점**: Gateway API·Envoy 개념을 새로 배워야 함. CRD 필드가 버전마다 바뀔 수 있음.

### 3.2 ingress-nginx + 보조(oauth2-proxy 또는 작은 ext_authz)

- **정체**: 가장 오래 쓴 Ingress 구현체. 자료·예제가 압도적으로 많음(학습 장벽 낮음).
- **문제**: 무상태 JWT 검증이 OSS의 1급 기능이 **아니다**(NGINX Plus 상용 기능). OSS에선 `auth_request`로 **외부 검증 서비스를 하나 붙여야** 한다(oauth2-proxy 등).
- → 결국 **작은 앱이 다시 생긴다.** [[ADR-024]]가 피하려던 "인증 위해 앱 세우기"에 가까워지고, ②(무상태 프록시)가 아니라 ③(ext_authz)로 슬쩍 넘어감. 우리 결정 취지와 어긋남.

### 3.3 Istio (Gateway API 구현 + 풀 메시)

- **정체**: 가장 강력한 서비스 메시. `RequestAuthentication`+`AuthorizationPolicy`로 JWT 검증 다 됨.
- **문제**: 엣지만 필요한데 **east-west 메시 전체**를 떠안음(사이드카·컨트롤플레인 운영). [[ADR-013]]의 "무트래픽 규모엔 최소 machinery" 원칙에 정면 배치.
- → **모델 B 도착지로 남겨둘 것.** 지금 도입은 과투자.

### 3.4 Kong (Ingress/Gateway API)

- **정체**: 플러그인 생태계가 풍부한 API 게이트웨이(JWT·rate limit 플러그인 내장).
- **문제**: 또 하나의 제품·DB(전통적으로 Postgres 백엔드)·운영 표면. Envoy Gateway가 더 가볍고 "Gateway API 표준"에 가까움.
- → 유효한 대안이나 우선순위 낮음.

### 3.5 한눈 비교

| 기준 | Envoy Gateway | ingress-nginx+보조 | Istio | Kong |
|------|:---:|:---:|:---:|:---:|
| Gateway API 네이티브 | ✅ | △(주로 Ingress) | ✅ | ✅ |
| 무상태 JWT 검증(앱 0) | ✅ | ❌(보조앱 필요) | ✅ | ✅(플러그인) |
| 경로별 정책 | ✅ | △ | ✅ | ✅ |
| 운영 무게 | 가벼움 | 가벼움 | **무거움** | 중간 |
| 학습 자료 많음 | 중간 | **많음** | 많음 | 중간 |
| 모델 B(mTLS) 승격 | 자연 | 별개 | (이미 메시) | 별개 |
| ADR-024 ② 취지 부합 | **최상** | 어긋남 | 과함 | 부합 |

---

## 4. 잠정 추천 — Envoy Gateway

한 줄 근거: **[[ADR-024]] ②("기성 프록시가 설정만으로 무상태 JWT 검증, 앱 안 세움")를 가장 곧이곧대로 실현하면서, 경로별 정책·ext_authz 승격·메시 전환까지 한 제품 안에서 열려 있다.**

목표 구성:
```
Client → [Envoy Gateway: TLS + JWT(SecurityPolicy) + 클레임 헤더 + strip + rate limit]
              ↑ JWKS                              │
        인증 서버(Spring AS)                        ├─ /api/**  → command/query  (JWT 정책 O)
                                                  └─ /auth/** → 인증 서버       (JWT 정책 X)
+ NetworkPolicy: Envoy Gateway 파드에서 오는 ingress만 command/query/auth 허용
```

단, **확정 전 직접 만져보고 판단하길 권함**(경험 부족 보완). 아래 §5.

---

## 5. 공부하며 확인할 것 (로컬 실습 경로)

무트래픽 학습 프로젝트니 로컬에서 직접 띄워보는 게 문서 읽기보다 빠르다.

1. **로컬 K8s**: `kind` 또는 `k3d`로 1노드 클러스터(docker-compose 대체 학습). [[ADR-012]]가 말한 "k3s~EKS 패리티"의 로컬판.
2. **Envoy Gateway 설치**: Helm으로 설치 → `GatewayClass`/`Gateway`/`HTTPRoute` 최소 예제로 라우팅부터.
3. **JWT 검증 붙이기**: 더미 JWKS(또는 로컬 Spring AS)로 `SecurityPolicy` JWT 붙여 `/api`만 검증, `/auth`는 통과 확인.
4. **헤더 확인**: 검증 후 `X-User-*` 헤더가 백엔드에 주입되는지, 클라 자칭 헤더가 strip되는지 curl로 확인.
5. **판단 포인트**: 이 설정이 "SCG를 Kotlin으로 짜는 것"보다 실제로 단순한지 몸으로 확인 → 확정 근거.

체크할 질문:
- 경로별 JWT skip이 정말 선언만으로 되나? (된다면 Envoy Gateway 확정 강한 근거)
- rate limit 로컬 모드로 초기 요구 충분한가? (글로벌=Redis는 나중)
- correlationId를 엣지에서 생성/주입하는 게 깔끔한가, 아니면 앱이 낫나? ([[DESIGN-011-observability]]는 현재 command-adapter 생성 — 엣지로 옮길지 별도 판단)

---

## 6. 확정 시 수정할 문서 리스트

Envoy Gateway로 확정되면 아래를 SCG 제거 + Envoy Gateway 명시로 정정한다. **[[RFC-020]]은 종결 상태이고 논점 2에서 이미 제품 택일을 위임했으므로 건드리지 않는다**(빈칸 채우기지 재론 아님).

| # | 문서 | 고칠 곳 | 성격 |
|---|------|---------|------|
| 1 | [[ADR-024-authentication-boundary]] | 결정 4의 "Envoy/nginx ingress **택일**" → **Envoy Gateway(Gateway API) 확정**으로 핀. 상태 Proposed → (사용자 승인 시) 반영 | 핵심 |
| 2 | [[DESIGN-010-deployment-runtime]] | §4.1 워크로드 표의 "API 게이트웨이 = Spring Cloud Gateway" 행, §4.2 mermaid의 `SCG` 박스, §4.4 "nginx=입구·TLS, SCG=검증" 문단, §5 Alternatives의 SCG 언급 — 전부 "Envoy Gateway가 입구+검증 겸함(1계층)"으로 | 핵심(모순 제거) |
| 3 | [[11-runtime-topology]] | §1 워크로드 표(API 게이트웨이=SCG), §3 mermaid(ING+SCG 2박스 → Envoy Gateway 1박스), §4 확장 축 | 핵심(모순 제거) |
| 4 | [[09-auth-server-module]] | §6 연동 흐름의 "API Gateway", §5 구조 설명의 게이트웨이 표현 → Envoy Gateway. `/auth/**` 경로가 JWT 정책 미적용 라우트임을 명시 | 중간 |
| 5 | [[05-command-adapter]] | pre-authenticated·엣지 표현, devils-advocate 반론2(헤더 위조 SPOF)의 완화책에 "Envoy Gateway + NetworkPolicy" 구체화 | 중간 |
| 6 | [[08-query-read-model-server]] §8 | "엣지(API Gateway)" 표현 → Envoy Gateway(또는 일반 "엣지"로 통일) | 소 |
| 7 | [[00-module-index]] | §5 관련 문서 링크 점검(런타임 뷰 참조) | 소 |
| 8 | [[00-data-index]]/[[03-auth-schema]] | 게이트웨이 일반 표현 점검(SCG 언급 없으면 무변경) | 소 |
| 9 | (선택) [[DESIGN-011-observability]] | correlationId 생성 지점을 엣지로 옮길지 **별도 결정** — 이번 스코프 아님, 플래그만 | 별건 |

수정 순서 권장: **2·3(모순 제거)이 먼저**, 그다음 1(ADR 핀), 4~8(표현 정합), 9는 분리.

---

## 7. 관련 문서

- 결정: [[RFC-020-authentication-boundary-gateway]] · [[ADR-024-authentication-boundary]]
- 런타임: [[DESIGN-010-deployment-runtime]] · [[11-runtime-topology]]
- 인증: [[09-auth-server-module]] · [[DESIGN-017-auth-token]] · [[ADR-020-auth-token-transport]]
- 인프라 원칙: [[ADR-012-kafka-hosting-msk-vs-self-managed]] · [[ADR-013-db-hosting-and-read-write-topology]] · [[RFC-007-deployment-infra-ops]]
