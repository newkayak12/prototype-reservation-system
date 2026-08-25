# 03 · auth 스키마 (authenticate + Spring Authorization Server)

> 허브: [[00-data-index]] | 근거: [[09-auth-server-module]] · [[DESIGN-017-auth-token]] · [[RFC-020-authentication-boundary-gateway]] · [[ADR-024-authentication-boundary]]

## 0. 배치

`auth-server-module`은 독립 Spring Boot 앱이라 자기 datasource를 가진다([[ADR-024-authentication-boundary]] 결정 6). command/query 어느 스키마와도 공유하지 않는다 — 물리 인스턴스를 command와 통합할지 별도로 둘지는 미결(M-8, [[09-auth-server-module]] §9)이지만, **스키마 경계가 독립**이라는 점은 이 문서의 확정 사항이다.

---

## 1. `authenticate` — credential + refresh rotation 상태

확정 — [[09-auth-server-module]] §5·§6, [[DESIGN-017-auth-token]]

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | `VARCHAR(128)` | **PK** | |
| `user_id` | `VARCHAR(128)` | NOT NULL | command `user` 컨텍스트 참조 — 물리 DB 분리라 FK 아님, 애플리케이션 레벨 참조 |
| `login_id` | `VARCHAR(32)` | NOT NULL | |
| `password_hash` | `VARCHAR(256)` | NOT NULL | `spring-security-crypto` 해싱 |
| `current_refresh_jti` | `VARCHAR(64)` | NULL | 무상태 refresh JWT의 현재 유효 jti. rotation마다 갱신, 로그아웃 시 NULL |
| `created_at` / `updated_at` | `DATETIME(6)` | NOT NULL | |

> **핵심 취약점(설계 확정, 미해소)**: `current_refresh_jti` **단일 컬럼**은 "한 주체 = 활성 refresh 1개"를 스키마 차원에서 전제한다 — 폰+웹 동시 세션은 이 컬럼 구조로는 표현 불가하다. 다중 세션을 지원하려면 이 컬럼을 세션 테이블(리스트/별도 테이블)로 재설계해야 하고, 그 순간 재사용 탐지·강제 로그아웃 로직도 전부 다시 설계된다([[09-auth-server-module]] devils-advocate 핵심취약점). 다중세션 지원 여부는 제품/보안 정책 결정 대기 — 자연히 해소되지 않는다.

---

## 2. Spring Authorization Server 표준 테이블

**채택 확정**([[RFC-020-authentication-boundary-gateway]] ✅ 종결 2026-06-30 · [[ADR-024-authentication-boundary]] 결정 6) — `spring-boot-starter-oauth2-authorization-server`가 요구하는 표준 스키마를 그대로 쓴다. 아래는 **테이블 존재·용도만** 정리한 것이며, 정확한 컬럼·타입·인덱스는 이 문서가 임의로 적지 않는다 — 라이브러리 버전에 종속되므로 실제 DDL은 `spring-security-oauth2-authorization-server` 공식 배포 스크립트(`oauth2-authorization-schema.sql`)를 구현 시점 버전 기준으로 그대로 가져와 Flyway 마이그레이션에 반영한다.

| 테이블 | 용도 |
|--------|------|
| `oauth2_registered_client` | 등록된 OAuth2 클라이언트(이 프로젝트에선 API Gateway/프론트엔드가 유일한 클라이언트) 메타데이터 |
| `oauth2_authorization` | 발급된 access/refresh 토큰, authorization code 등 인가 상태 |
| `oauth2_authorization_consent` | 사용자 동의(scope 승인) 기록 — 단일 신뢰 클라이언트 구조라 실질적 활용도는 낮음 |

- §5 구조도의 커스텀 `LoginEndpoint`/`RefreshEndpoint`/`JwtIssuer`가 SAS 기본 필터체인·엔드포인트와 겹치지 않는지는 Phase 7-4 재검토 대상([[09-auth-server-module]] §4).
- `redisson`/`spring-boot-starter-data-redis`는 이 스키마와 무관 — refresh 무상태화로 Redis 사본이 제거되었다([[DESIGN-017-auth-token]] §4.1).

---

## 3. 관련 문서

- [[00-data-index]] · [[01-command-schema]] · [[02-query-schema]]
- [[09-auth-server-module]] · [[DESIGN-017-auth-token]]
- [[RFC-020-authentication-boundary-gateway]] · [[ADR-024-authentication-boundary]] · [[ADR-020-auth-token-transport]]
