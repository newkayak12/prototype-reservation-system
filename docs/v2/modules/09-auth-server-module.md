# 09 · auth-server-module [신규]

> 허브: [[00-module-index]] | 근거: [[ADR-024]] (인증 경계) · [[DESIGN-017]] (인증 토큰) · [[DESIGN-010]] §4.2 (workload) · [[DESIGN-014]] (인가) · [[ADR-020]] (토큰 transport)

## 1. 책임

독립 인증 서버 (별도 배포 단위).

- JWT 발급 (access body + refresh cookie)
- JWKS 노출 (`/.well-known/jwks.json`) — 엣지 프록시가 access 검증에 참조
- refresh rotation + `current_refresh_jti` 관리
- JTI 재사용 탐지 → 전 세션 무효화
- 로그인/로그아웃 (V1 General/Seller 통합 → 단일 `/auth/login`)

## 2. 왜 별도 모듈인가 ([[ADR-024]])

V1은 인증(JwtFilter·토큰 발급)이 adapter-module 안에 있었다. V2는:

1. **command/query 다중 서비스** — 각자 토큰 풀면 검증 중복 + 서명 키 확산
2. **엣지 프록시가 JWKS로 access 검증** — 발급 주체(인증 서버)가 JWKS 노출해야 함
3. **refresh rotation + JTI 재사용 탐지**([[ADR-020]]) — 인증 서버가 `current_refresh_jti` 관리

→ 인증은 command-module의 `authenticate` 컨텍스트가 아니라 **독립 인증 서버**로 분리.

## 3. 의존성

| 항목 | 값 |
|------|-----|
| **허용 의존** | `shared-module`, `contract-module`(인증 이벤트 발행용) |
| **금지** | `command-*`, `query` 직접 의존 |
| **구조** | 독립 Spring Boot 앱 (stateless — DB가 상태) |
| **구현 시점** | **Phase 7-4** (command-adapter와 병렬) |

## 4. 사용 라이브러리

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| `spring-boot-starter-security` | `3.4.5` | 인증 서버 보안 기반 |
| `spring-boot-starter-oauth2-authorization-server` | ⚠️ **카탈로그 미등재 — 추가 필요** | Spring Authorization Server(발급·JWKS 표준) |
| `jjwt` | `0.12.6` | JWT 생성/서명 (V1 계승 — SAS 미채택 시 직접 발급 경로) |
| `spring-security-crypto` | `6.4.2` | credential 해싱(password) |
| `spring-boot-starter-data-jpa` | `3.4.5` | `authenticate` 테이블(`current_refresh_jti` 컬럼) |
| `mysql-connector-j` | `8.0.33` | 드라이버 |
| `spring-boot-starter-web` | `3.4.5` | 엔드포인트 |
| (테스트) `spring-security-test` | (Boot BOM) | 인증 흐름 |
| (테스트) `kotest-*` / `testcontainers-mysql` | `5.9.0` / `2.0.3` | rotation·JTI 재사용 테스트 |

> ⚠️ **라이브러리 결정 필요**: Spring Authorization Server(`spring-boot-starter-oauth2-authorization-server`)를 `libs.versions.toml`에 추가할지, 아니면 V1처럼 `jjwt` 직접 발급으로 갈지 확정. [[DESIGN-010]] §4.2는 "Spring Authorization Server"를 명시 → **카탈로그 추가 권장**. redisson/`data-redis`는 **불필요** — refresh 무상태화로 Redis 사본 제거([[DESIGN-017]] §4.1).

## 5. 구조

```
auth-server-module/com.reservation.auth
├── endpoint/
│   ├── LoginEndpoint.kt          # POST /auth/login (General/Seller 통합)
│   ├── LogoutEndpoint.kt         # POST /auth/logout (쿠키 삭제 + jti NULL)
│   ├── RefreshEndpoint.kt        # POST /auth/refresh (rotation + JTI 대조)
│   └── JwksEndpoint.kt           # GET /.well-known/jwks.json
├── token/
│   ├── JwtIssuer.kt              # access/refresh JWT 생성
│   ├── RefreshRotationService.kt # rotation + current_refresh_jti 갱신
│   └── JtiReusageDetector.kt     # 옛 refresh 재사용 = 탈취 의심 → 전 세션 무효화
├── domain/
│   ├── Credential.kt             # credential 검증(password hashing)
│   ├── AuthenticateEntity.kt     # authenticate 테이블 (current_refresh_jti)
│   └── AuthenticateRepository.kt
└── config/
    ├── SecurityConfig.kt
    ├── KeyConfig.kt              # 서명 키 로딩 (RSA/EC)
    └── CookieConfig.kt          # refresh 쿠키 (HttpOnly, Secure, SameSite=Lax)
```

## 6. 핵심 설계 ([[DESIGN-017]])

- **refresh = 무상태 서명 JWT** — Redis 사본 제거. 검증은 서명·만료·클레임만
- **transport**: refresh = `HttpOnly`·`Secure`·`SameSite=Lax` 쿠키, access = body → Authorization 헤더(V1 계승 + SameSite 보강)
- **폐기**: 즉시 denylist 포기. `current_refresh_jti` 단일 컬럼으로 재사용 탐지 + 강제 로그아웃 최소 구현. 짧은 access TTL + refresh 만료로 대부분의 로그아웃 요구 커버

### 연동 흐름 ([[DESIGN-010]] §4.2)

```
[클라이언트] → POST /auth/login → [auth-server] credential 검증 → JWT 발급
[auth-server] → JWKS 노출
[API Gateway] → access JWT 서명 검증(JWKS 참조) → 검증된 클레임 헤더(X-User-Id, X-User-Role)
[command-adapter / query] → pre-authenticated 필터 수신 (JwtFilter 없음)
```

## 7. auth ↔ command `authenticate` 컨텍스트 관계

| 관심사 | 소속 |
|--------|------|
| 토큰 발급/검증/rotation | `auth-server-module` |
| credential 저장/검증 | `auth-server-module` |
| 사용자 가입/탈퇴/프로필 변경 | `command-module` (`user` 컨텍스트) |
| `current_refresh_jti` 관리 | `auth-server-module` |
| 인증 이벤트 발행 | `auth-server-module` → `contract-module` (`UserLoggedIn`, `SessionInvalidated`) |

## 8. 할 일

- [ ] SAS 채택 여부 확정 + 카탈로그 의존성 추가(⚠️ §4)
- [ ] 모듈 뼈대 + JWT 발급(access+refresh) + JWKS
- [ ] refresh rotation + `current_refresh_jti`
- [ ] JTI 재사용 탐지 → 전 세션 무효화
- [ ] V1 로그인 통합 (General/Seller → `/auth/login`)
- [ ] V1 JwtFilter/토큰 코드 제거(command/query에서)

## 9. 미결

- **M-7**: `authenticate` 컨텍스트 존속 범위 — (a) auth-server 완전 흡수 (b) command-user 병합 (c) 축소 유지
- **M-8**: 배포 단위 — (a) 별도 Spring Boot 앱 (b) 같은 프로세스, 모듈만 분리
- **라이브러리**: SAS vs jjwt 직접 발급 확정

## 10. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: access가 이미 무상태 서명 JWT이니 refresh도 무상태 서명 JWT로 통일하고, denylist 대신 `current_refresh_jti` 단일 컬럼으로 재사용 탐지·강제 로그아웃을 최소 상태로 지으며, 엣지 프록시가 JWKS로 1회 검증하고 앱은 pre-authenticated만 둔다.

**Steel-man**: 검증 모델을 access·refresh 양쪽 무상태로 일원화하면 서명 키 확산과 must-not-evict Redis 워크로드가 동시에 사라지고, 운영 표면적을 컬럼 하나·프록시 설정 한 벌로 압축할 수 있다 — 서비스가 늘어도 인증 검증 코드가 복제되지 않는 일관성 있는 최소 구조다.

### 숨은 가정

1. **"한 주체 = 활성 refresh 1개"** — `current_refresh_jti`가 단일 컬럼이라는 스키마 선택 자체가 사용자당 동시 세션 1개를 전제한다. 예약 시스템의 폰+웹 동시 사용은 검토조차 되지 않았다.
2. **즉시 강제 폐기는 "요구가 입증될 때까지" 없다** — 계정 탈취·비밀번호 변경·관리자 강제 차단 시 전 토큰 즉시 무효화가 제품/보안 요구가 아니라는 가정. "V1도 안 했다"를 요구 부재의 근거로 삼는다.
3. **신뢰 경계 설정이 항상 켜져 있다** — 엣지 헤더 strip + NetworkPolicy가 배포·설정 실수·긴급 롤백 중에도 유지된다는 가정. 앱은 재검증을 지우므로 이 둘이 유일한 방어선이다([[ADR-024]] §2).

### 반론

1. **`current_refresh_jti` 단일 컬럼은 "최소 상태"가 아니라 두 기능을 동시에 못 하게 묶는 구조적 제약이다** `[design]` · **severity: high** · 선례: [[DESIGN-017]] §7이 "즉시 강제 폐기 불가"·"NULL화 후 잔여 access 활성"을 이미 리스크로 인정. — steel-man은 "컬럼 하나로 재사용 탐지·강제 로그아웃을 다 덮는다"지만, 이 문서(§6·§7)는 그 컬럼이 동시에 (a) 무상태 refresh라 만료까지 서버가 못 끊는 즉시 폐기 불능과 (b) 다중 디바이스 세션 불가를 *한 몸으로* 만든다는 걸 다루지 않는다. 폰에서 rotate하는 순간 노트북 refresh의 jti가 stale이 되어 노트북의 다음 `/refresh`가 "탈취 의심"으로 전 세션을 무효화한다. 즉 정상 다중 기기 사용이 §6의 JTI 재사용 탐지를 *스스로* 발동시킨다. 탈취된 refresh 하나로 만료까지 무제한 rotation이 가능하다는 점과 합쳐지면, "최소 상태"의 대가가 보안 약화와 제품 기능 상실 양쪽이다.

2. **재사용 탐지 → 전 세션 무효화는 동시성 미해결 상태에서 켜는 로그아웃 폭탄이다** `[operational]` · **severity: high** · 선례: [[DESIGN-017]] §6·§7이 "병렬 `/refresh` 경합으로 갱신 충돌"을 미해결로 명시. — steel-man은 "rotation이 이미 만드는 사실을 컬럼으로 강제할 뿐"이라 하지만, 이 문서는 grace window·refresh 체이닝·유예 없이 `JtiReusageDetector`(§5)가 곧바로 "컬럼 NULL화 = 전 세션 무효화"라는 최대 처벌로 이어지게 설계했다. 병렬 탭, 모바일+웹, 네트워크 재시도로 인한 refresh 중복 전송, rotation 응답 유실 후 재시도 — 모두 정상 흐름인데 이 미해결 경합이 그대로 대량 강제 로그아웃으로 번진다. §7의 `SessionInvalidated` 이벤트가 정상 사용자에게 발화하는 오탐 엔진이 된다.

3. **엣지 pre-authenticated는 헤더 위조 단일 실패점을 앱 전체에 심는다** `[architecture]` · **severity: high** · 선례: [[ADR-024]] §2·결과 트레이드오프가 "헤더 strip과 NetworkPolicy 중 하나라도 빠지면 위조로 뚫린다"고 자인. — steel-man은 "검증을 엣지 한 곳에 모아 중복 제거"지만, 이 문서 §6 흐름은 command/query가 JwtFilter를 제거하고 `X-User-Id`·`X-User-Role` 헤더를 *무조건 신뢰*하도록 만든다. 서비스 메시(mTLS) 없이 모델 A를 쓰는 한 방어 심도는 0이며, NetworkPolicy 오설정·사이드카 누락·앱을 우회하는 내부 경로 하나가 전 인증을 무력화한다. "인증을 앱 밖으로"라는 목적이 "앱이 스스로를 방어할 능력을 버림"과 맞바꿔졌다.

### 다중 페르소나

**보안/Compliance 관점** — 이 설계는 계정 탈취 대응의 골든타임을 구조적으로 포기한다. 사용자가 "내 계정이 털린 것 같다"고 신고하거나 비밀번호를 바꿔도, 무상태 access는 TTL 만료까지 살아 있고 강제 로그아웃 손잡이는 오직 `current_refresh_jti` NULL화 → 다음 `/refresh` 실패까지 지연된다. GDPR·PCI·개인정보보호법이 요구하는 "세션 즉시 종료" 감사 항목에 "즉시"가 없다. 게다가 강제 로그아웃과 도난 탐지가 *같은 컬럼·같은 NULL 손잡이*를 공유하므로, 감사 로그에서 "관리자가 끊었나 / 도난이 탐지됐나 / 정상 경합이었나"를 사후 구별할 수 없다. §7의 이벤트 발행이 이 구별을 남기지 않으면 인시던트 포렌식이 불가능하다. "요구가 입증되면 그때 denylist 부활"은 사고가 터진 뒤에야 방어를 켜는 사후약방문이다.

**On-call/SRE 관점** — in-memory access 강제(V1 계승, [[DESIGN-017]] §4.2)와 짧은 TTL이 결합되면 모든 새로고침·새 탭·앱 복귀가 `/auth/refresh` 왕복을 강제해, 독립 배포된 인증 서버가 전체 트래픽의 리프레시 급증을 단독으로 받아낸다 — 인증 서버가 죽으면 토큰 갱신이 멎어 짧은 TTL 때문에 *전 서비스가 몇 분 내 동반 인증 실패*한다(짧은 TTL이 blast radius를 키운다). 여기에 반론 2의 오탐이 겹치면 "정상 트래픽 스파이크 → refresh 폭주 → jti 경합 오탐 → 대량 SessionInvalidated → 재로그인 폭주"의 자기증폭 루프가 열린다. 그런데 이 문서에는 인증 서버 다중화·refresh 경합 처리·오탐 억제·`current_refresh_jti` 쓰기 경합의 락 전략이 §9 미결이거나 아예 없다. on-call은 무상태라는 이유로 관측 가능한 서버 상태(누가 왜 무효화됐나)마저 잃는다.

### 핵심 취약점

`current_refresh_jti` **단일 컬럼**에 (재사용 탐지 · 강제 로그아웃 · 세션 수 제한)이라는 세 관심사가 과적재되어, 하나를 쓰면 나머지가 강제로 결정된다 — 다중 세션을 허용하려 컬럼을 리스트/테이블로 바꾸는 순간 재사용 탐지·강제 로그아웃 로직이 전부 재설계되고, 그건 사실상 이 문서가 포기한 "세션 상태 테이블(denylist의 사촌)"의 재도입이다. 최소주의가 확장 지점에서 되레 전면 재작업을 부른다.

### 가역성

**혼합** — 토큰 모델(무상태 refresh·jti 컬럼)은 denylist 부활 경로가 명시돼 있어 대체로 reversible. 그러나 엣지 pre-authenticated 결정(모든 도메인 앱에서 JwtFilter·서명검증 제거, [[ADR-024]] #1)은 서비스 메시 도입 전까지 **one-way door**에 가깝다 — 앱에서 걷어낸 재검증 능력을 되살리려면 전 서비스 재작업이 필요하다.
