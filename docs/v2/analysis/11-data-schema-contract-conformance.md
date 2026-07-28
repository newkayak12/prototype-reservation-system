# 11 · data 스키마 ↔ 계약 정합성 검토

> 대상: [[00-data-index]] · [[01-command-schema]] · [[02-query-schema]] · [[03-auth-schema]]
> 기준: **"컬럼 구성이 우리 계약 문서(RFC/DESIGN/ADR/모듈)와 일치하는가"** — 내 설계 취향이 아니라 계약 근거 유무만 판정.
> 검토 방식: 각 계약 문서를 열어 데이터 문서가 인용한 컬럼/제약을 대조. 물리 타입·길이(`VARCHAR(n)`·`DATETIME(6)`·`BINARY(16)` 등)를 계약이 안 정한 것을 데이터 문서가 채운 것은 **데이터 사전의 정당한 역할 → 결함 아님**으로 제외.

---

## 판정 요약

| # | 위치 | 유형 | 심각도 | 판정 |
|---|------|------|--------|------|
| 1 | 01 §1.3 outbox `sequence_no` | 계약 미근거 (기정사실화) | 높음 | ✅ 해소 — [[RFC-032-non-es-state-copy-reordering]]로 종결(비-ES 순서 토큰 불요, 셀 정정) |
| 2 | 03 §1 authenticate `jti_issued_at` | "확정" 아래 근거 없는 컬럼 | 중 | ✅ 해소 — 컬럼 삭제 |
| 3a | 02 §1.1 inbox GC 주석 | 인용 오류 | 중 | 데이터 문서 정정 |
| 3b | 03 §0 datasource | 인용 오류 | 중 | 데이터 문서 정정 |
| 3c | 03 §1 취약점 인용 | 인용 모호 | 중 | 데이터 문서 정정 |
| 4 | DESIGN-003 §4.4 (계약측) | 계약끼리 모순 | 중 | 계약 문서 갱신 |

우선순위: **1 > 2 ≈ 3 > 4**.

---

## 1. `outbox.sequence_no` — 비-ES 출처가 계약에 없다 (높음)

**데이터 문서 주장** — 01 §1.3:
- `sequence_no BIGINT NOT NULL` · "relay가 이 값 ASC로 폴링([[RFC-025]] 결정 1) — event_store의 sequence_no와 **동일 계열**"
- §2: schedule·user 등 비-ES는 "상태 테이블 + 같은 트랜잭션 outbox insert"

**계약 실제**:
- `sequence_no`는 **ES 이벤트 스트림 전용** 개념. event_store §4.1 = "애그리거트 내 순번(1부터)".
- RFC-025 결정1/5도 aggregate 이벤트 스트림의 순번을 다룸.
- DESIGN-003 §4.2(비-ES 계약)는 outbox insert만 말하고 `sequence_no`를 **언급 안 함**.

**문제**: 비-ES 쓰기(상태 테이블엔 순번 컬럼 없음)가 outbox row의 `sequence_no`를 **무엇으로 채우는지** 어느 계약도 정하지 않았는데, 데이터 문서는 "동일 계열"이라고 확정으로 적었다. ES/비-ES가 갈리는 핵심 지점.

**제안**: RFC-025 또는 DESIGN-003에서 **비-ES outbox 순서 키**를 먼저 확정 → 그 결정을 데이터 문서가 인용. 확정 전까지 01 §1.3의 해당 셀은 "비-ES 출처 미결"로 표기.

**결정 (2026-07-22 · [[RFC-032-non-es-state-copy-reordering]]):** ✅ 해소. 애초에 물음이 잘못 세워졌다 — 비-ES는 **순서 토큰을 채울 필요가 없다.** produce 재정렬의 유일한 발생 지점(relay→produce)을 [[RFC-025]] 결정 1의 **단일 순차 relay**가 ES/비-ES 공통으로 봉합하므로(삽입 순서 단독 드레인 + partition=`aggregate_id` + idempotent producer), 사본별 순서 토큰이 필요 없다. 조치: 01 §1.3의 `sequence_no`를 **NULL 허용(비-ES 해당 없음)**으로 정정하고 "event_store와 동일 계열" 기정사실화 철회, 전역 드레인 키는 PK `id`임을 명시. `version` 컬럼은 도입하지 않음(동시성 = [[RFC-014]] 소관, 재정렬 목적 신설 아님).

---

## 2. authenticate `jti_issued_at` — "확정" 아래 근거 없는 컬럼 (중)

**데이터 문서 주장** — 03 §1: 표 전체가 "확정 — [[09-auth-server-module]] §5·§6, [[DESIGN-017-auth-token]]".

**계약 실제**:
- `current_refresh_jti`: DESIGN-017 §4.3이 명시 → **확정 OK**.
- `jti_issued_at`: **어느 계약에도 없음** → 순수 창작인데 "확정" 블록에 포함.
- (`login_id`도 계약 미언급이나 credential 필드로 자연스러움 — `jti_issued_at`이 가장 명확.)

**제안**: authenticate 표의 라벨을 "핵심 구조(`current_refresh_jti` 회전 모델)는 확정 / 세부 컬럼(`jti_issued_at`·audit 등)은 구현 시 확정"으로 분리. 또는 DESIGN-017에 근거 추가.

**결정(2026-07-22)**: `jti_issued_at` **삭제**. 확정 모델(DESIGN-017 §4.3)은 발급 *시각*을 요구하지 않고(“jti만 적어 둔다”), 이를 정당화할 유일한 후보 용도인 **슬라이딩 만료**는 §6 미결. 경합(병렬 `/refresh`)은 이 컬럼으로 풀리지 않는 별개 문제(낙관적 락/CAS/grace window 소관). → 선취 대신, 슬라이딩 만료 결정이 내려질 때 그 근거와 함께 추가한다.

---

## 3. 인용 오류 (근거를 잘못 가리킴)

### 3a. 02 §1.1 inbox GC 주석
- **주장**: "한 테이블 vs (`event_id` 로그 / `aggregate` 커서) 분리는 미확정 **([[07-query-projection-server]] §5.2)**".
- **실제**: 07 §5.2엔 GC 예외(aggregate 커서 제외)만 있고 **테이블 분리 갈래 문장은 없다**. 이 갈래는 데이터 문서 자체 서술.
- **제안**: GC 예외 부분만 07 §5.2 유지, 테이블 분리는 "(이 문서 판단 — 계약은 개념만 정함)"으로 표기.

### 3b. 03 §0 datasource
- **주장**: "독립 Spring Boot 앱이라 자기 datasource **([[ADR-024]] 결정 6)**".
- **실제**: ADR-024 결정6은 *SAS 직접 구축* 얘기지 datasource/배포 단위가 아님. "독립 앱"은 09 §3 소관, 배포 단위는 **M-8 미결**.
- **제안**: 인용을 `[[09-auth-server-module]] §3`으로 교체(M-8 미결은 이미 뒤에 언급됨).

### 3c. 03 §1 취약점 인용
- **주장**: "([[09-auth-server-module]] **devils-advocate** 핵심취약점)".
- **실제**: devils-advocate가 **둘**(09 §10 임베디드 vs 별도 파일)이고 핵심취약점이 서로 다름. 데이터 문서 내용과 맞는 건 **09 §10**. 별도 파일 쪽은 RFC-020 종결로 낡은 다른 이슈.
- **제안**: `[[09-auth-server-module]] §10 핵심취약점`으로 특정.

---

## 4. 계약끼리 모순 — DESIGN-003 §4.4 (데이터 문서는 옳게 골랐음)

- 데이터 문서 event_store `payload JSON (event-carried)`는 **RFC-029(Zero Payload 폐기)**를 정확히 따름.
- 그런데 인용 계약 중 하나인 **DESIGN-003 §4.4**는 아직 "Zero Payload 원칙 계승"이라 적혀 있어 RFC-029와 정면 충돌.
- 데이터 문서 잘못 아님 → **DESIGN-003 §4.4를 RFC-029에 맞게 갱신**해야 인용 사슬이 깨끗해짐(계약측 작업).

---

## 문제 아님 (확인 후 통과)

- **snapshot** 6컬럼 = DESIGN-009 §4.4와 완전 일치.
- **inbox** 핵심(`event_id` + `last_applied_sequence_no` + aggregate 키) = RFC-025 결정5 + 07 §5.2 근거. GC 예외도 07 §5.2 근거.
- **`applied_sequence_no` / ReadFreshnessGate** = RFC-030 논점4 + 08 §5.0.1 정확히 일치.
- **event_store** 컬럼 이름·핵심 타입(BINARY(16), JSON)·UNIQUE(aggregate_id,sequence_no) = ADR-005 + DESIGN-003 §4.1 + ADR-016 + 06 §5.4 근거.
- **ReservationView 등** "설계 예시", **aggregate_lock** "(제안)/설계 공백", **비-ES 상태 테이블** "V1 유지" — 모두 정직하게 라벨링됨.
- 물리 타입/길이 전반 — 계약 미지정을 데이터 사전이 채운 것, 정당.
- 사소: 01 event_store `INDEX(aggregate_id,sequence_no)`는 UNIQUE와 중복(무해). 02 "user = 확정"은 08 §6에 pending 마커가 없다는 정황 추론이지 "확정" 명시는 아님(약한 과장).
