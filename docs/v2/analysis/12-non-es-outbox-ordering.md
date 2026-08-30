# 12 · 비-ES outbox 순서 키 — `sequence_no`가 필요한가

> [[11-data-schema-contract-conformance]] §1의 심화. "01 §1.3 outbox의 `sequence_no`를 비-ES 쓰기가 어떻게 채우나"라는 질문을, 계약이 실제로 뭘 결정해뒀는지까지 파고들어 정리한다.
> 근거: [[RFC-030-read-freshness-command-response-contract]] · [[RFC-025-ordering-relay-dlq-reconciliation]] · [[RFC-021-event-identity-and-global-ordering]] · [[DESIGN-003-write-model]] · [[DESIGN-004-read-model]] · [[08-query-read-model-server]]

---

## TL;DR

- **08의 "projection 없이"는 "read-model 사본이 없다"가 아니라 "ES-fold projection이 없다"는 뜻**(§3.5). 비-ES도 query DB에 사본을 두되(published-subscription), 그 사본을 이벤트 스트림 리플레이로 만들지 않을 뿐이다. 앞선 버전에서 "정면 모순·물리적으로 불가능"이라 한 건 "자기 테이블"을 command 테이블로 오독한 것이라 **철회**한다 — 프레이밍 A/B는 같은 것의 두 표현이다.
- **freshness(read-your-writes)용 seq는 비-ES에 불필요** — RFC-030이 비-ES를 bounded staleness로 명시. 고정.
- **LWW 재정렬 가드용 seq는 여전히 열려 있다.** 비-ES 사본이 published-subscription이어도, 상태 업데이트가 재정렬되면 옛 값이 새 값을 덮을 수 있다. 그래서 seq 필요 여부의 드라이버는 "projection이냐 published냐"(R-3)가 **아니라** "이 사본이 **order-sensitive하냐**"라는 별도 축이다.
- 데이터 문서 01 §1.3의 `sequence_no BIGINT NOT NULL` + "event_store와 동일 계열"은 여전히 문제 — 비-ES는 RFC-021이 seq 없다고 명시하는데 NOT NULL로 박혀 있고 채우는 법도 미정. 08/DESIGN-004의 "projection 없이 / 이벤트 없는데" 문구도 "사본조차 없다"로 오독되니 정리 대상.
- **결론: command 스키마의 seq 컬럼을 지금 확정하지 말 것.** 상위 모순(§3.5)을 먼저 닫고 → 비-ES query-side 사본이 order-sensitive(LWW 필요)한지 판정 → 그 결과가 seq 소스(version 컬럼 등) 필요 여부를 결정한다. version 컬럼/시퀀스 테이블을 선박는 것도, seq를 NOT NULL로 두는 것도 지금은 근거 없음.

---

## 1. `outbox.sequence_no`가 하는 일 (소비자 2명)

| 역할 | 누가 쓰나 | 근거 | 필요 성질 |
|------|-----------|------|-----------|
| (A) 발행 순서 키 | relay | RFC-025 결정1 | outbox 드레인 순서 — **전역 단조면 됨** |
| (B) LWW 재정렬 가드 | 컨슈머 inbox | 02 §1.1 | 들어온 seq > `last_applied_sequence_no`면 적용, 작으면 폐기 — **애그리거트별 단조 필요** |

- (A)는 총순서만 있으면 되므로 `outbox.id`로 대체 가능.
- (B)만 "애그리거트별 단조"를 요구한다. ES는 `event_store.sequence_no`(애그리거트 내 1부터)가 그 역할을 이미 한다. 비-ES 상태 테이블엔 그런 순번이 없다 — 이게 원래 걸렸던 지점.

---

## 2. `outbox.id`(auto-inc)로 대체되나 — 반은 된다

- **(A) 발행 순서**: 된다. `status` 필터 폴링(`WHERE status=PENDING ORDER BY id ASC`)이라 auto-inc의 커밋순서 역전(gap) 문제도 우회된다(RFC-025 결정1의 단일 리더 순차 발행이 id ASC로도 성립).
- **(B) freshness/LWW**: ES는 **불가**. RFC-030이 read-your-writes 토큰을 `event_store.sequence_no`에 못박아, read model의 `applied_sequence_no`와 같은 수 공간이어야 한다. `outbox.id`로 바꾸면 클라가 받은 `sequenceNo`와 수 공간이 달라져 `ReadFreshnessGate` 비교가 깨진다.

→ 그래서 "auto-inc면 끝"이 아니라, **(B)가 비-ES에 필요한지**가 관건이 된다. 여기서 계약을 뒤졌다.

---

## 3. 계약이 이미 결정해둔 것

### ① 비-ES 쓰기는 read-your-writes 대상이 아니다 — RFC-030 (R-1 합의 2026-07-05)
- causal token = "쓴 도메인 스토어 기준 `sequence_no`". 교차/크로스-BC는 **bounded staleness**만 보장(§결정, C'' 행: "토큰은 쓴 도메인분만 보장, 타 도메인은 bounded staleness — 스토어가 BC별 분리라 구조적").
- → freshness gate가 비-ES엔 구조적으로 안 걸린다. **freshness 때문에 비-ES seq가 필요할 일은 없다.**

### ② 비-ES는 ES-fold projection을 안 쓴다 — DESIGN-004 §4.2(다), 08
- "이벤트 없는데 projection 얹는 건 과투자"(08 §6). 08 §구조: 비-ES는 "repository/ # QueryDSL 직접 조회 (projection 없이 자기 테이블)".
- **정확한 의미**(§3.5): 여기서 "projection"은 ES 이벤트 스트림을 fold해 리플레이로 재구축하는 read model을 말한다. 비-ES는 이벤트 스트림이 없으니 그런 fold-projection이 없다는 것이지, **read-model 사본 자체가 없다는 뜻이 아니다.** 사본은 query DB에 있고(published-subscription), QueryDSL로 직접 읽힌다("자기 테이블" = query-side 사본).
- → 따라서 ②로부터 "seq 불필요"가 자동으로 나오지 않는다. 사본이 있는 이상 순서 보호(LWW) 필요 여부는 별도로 판정해야 한다(§3.5·§4).

### ③ 비-ES 이벤트는 sequence_no 정체성이 없다 — RFC-021 배경
- "비-ES·lookup 컨텍스트(상태+Outbox) 이벤트는 `sequence_no`가 없어 정체성이 아예 없다 — dedup 키도 causation 앵커도 없다."
- RFC-021은 이 정체성 문제를 **UUIDv7 `event_id`로만** 해결했고, per-aggregate 순번은 **의도적으로 안 만들었다.**

### ④ V1 optimistic-lock version 컬럼은 timetable에만 — V1_17
- V1_17 = `ALTER TABLE timetable ADD COLUMN version`. timetable은 V2에서 ES로 전환 → `event_store.sequence_no` 사용.
- 나머지 비-ES 상태 테이블(user·schedule 등)엔 version 컬럼 없음. 01 §2 "위 테이블 모두 변경 없음". DESIGN-003 §4.1 "비-ES는 DB 행 락 그대로"(낙관 version 아님).

---

## 3.5. "projection 없이"의 정확한 의미 — 모순이 아니라 어휘 정리

앞선 버전은 08(프레이밍 A)과 data/00·02(프레이밍 B)를 "정면 모순, A는 물리적으로 불가능"이라 판정했다. 이는 08의 "자기 테이블"을 command 상태 테이블로 오독한 것이라 **철회한다.** 어휘를 나누면 둘은 같은 것의 두 표현이다.

**어휘:**
- **projection** = ES 이벤트 스트림을 fold해 만든 read model. `event_store` 리플레이로 재구축 가능(07 projection-server).
- **published-subscription** = 원본이 흘리는 현재 상태를 받아 로컬 사본을 upsert. fold도 리플레이도 없음(DESIGN-004 §4.2(나), data/02 §3).

**두 프레이밍의 재해석:**

| | 프레이밍 A (08 §4·§6, DESIGN-004 §4.2다) | 프레이밍 B (data/00·02 §3, DESIGN-004 §4.3) |
|---|---|---|
| 문장 | "projection 없이 자기 테이블 / 이벤트 없는데 projection은 과투자" | query DB의 async-fed 로컬 사본, published-subscription |
| 실제 의미 | **ES-fold projection**을 안 쓴다 (그런 이벤트 스트림이 없으니까) | 그 사본을 **published-subscription**으로 채운다 |
| "자기 테이블" | = **query-side 사본 테이블** (command 테이블 아님) | = `query.{domain}.model` |

- command/query 물리 분리(ADR-013)와 충돌 없음: 비-ES 사본은 query DB에 있고 Kafka→projector로 채워진다(data/02 §0). command는 읽기를 서빙하지 않는다(DESIGN-004 §4.1)는 원칙도 그대로 지켜진다 — 비-ES 읽기도 query 사본에서 나오니까.
- 즉 A와 B는 **모순이 아니라 같은 설계를 fold 관점(A) / 데이터 관점(B)에서 쓴 것**이다.

**그래서 남는 진짜 잔여물은 두 가지뿐:**

1. **문구 정리(경미)** — 08/DESIGN-004의 "projection 없이 / 이벤트 없는데"가 "read-model 사본조차 없다"로 오독된다. "ES-fold projection은 안 쓰고 published-subscription 사본을 둔다"로 명시하면 끝. data/02 §3이 더 정확하다.
2. **순서 보호(핵심)** — 사본이 published-subscription이어도 상태 업데이트가 재정렬되면 옛 값이 새 값을 덮을 수 있다. **이 사본이 order-sensitive냐**가 seq/순서 토큰 필요 여부를 가른다. 이 축은 "projection이냐 published냐"(R-3)와 **직교**한다 — published-subscription 사본도 LWW가 필요할 수 있다.

> 정정된 결론: 닫아야 할 건 "A vs B 토폴로지 모순"이 아니라(그건 어휘 문제였다) **"어느 비-ES 사본이 order-sensitive해서 순서 토큰이 필요한가"**다.

---

## 4. 안 결정된 것 (열린 지점 — 상위→하위 순서)

| 순위 | 항목 | 내용 | 출처 |
|------|------|------|------|
| 0 (루트) | 비-ES 사본 order-sensitivity | 각 비-ES query-side 사본이 재정렬에 취약한가(LWW 필요) vs 자연 멱등(event_id-only) | §3.5 |
| 1 | R-3 | menu·category·company를 projection vs published-subscription 중 무엇으로 (order-sensitivity와 직교) | 08 §11 |
| 1 | R-2 | schedule을 projection vs 경량 lookup 중 무엇으로 | 08 §11 |
| 2 | seq 소스 | (사본이 LWW 필요 판정 시) 순서 소스 = version 컬럼? 이벤트 실린 값? | 미정 |
| 경미 | 문구 | 08/DESIGN-004 "projection 없이 / 이벤트 없는데" → "ES-fold 없이 published-subscription 사본"으로 정리 | §3.5 |
| 참고 | C12 | 혼합 패러다임(ES/비-ES) 순서·원자성 미표시 | 06-triage "partially-decided" |
| 참고 | — | "Outbox가 event store·상태 테이블 양쪽에서 relay되는데 순서·중복 보장 주체 불명" | DESIGN-001 §4.1 |

**seq가 load-bearing해지는 조건:**
- 비-ES 사본이 "순서 역전 없음 + 자연 멱등 upsert"면 → **event_id-only inbox로 축소**(RFC-025 논점2), seq 불필요.
- "최신 상태 upsert"라 재정렬에 취약하면(rename 덮어쓰기 등) → LWW 필요 → per-aggregate 순서 소스 필요. 이때 seq 소스를 정한다(순위 2).
- 판정 단위는 **사본별**이다 — 어떤 비-ES 사본은 order-sensitive(LWW), 어떤 건 멱등(event_id-only)일 수 있다. projection/published 선택(R-3)과 별개.

---

## 5. 결론 · 반영안

### 결론
- **freshness용 seq는 비-ES에 확실히 불필요**(RFC-030 bounded staleness). 고정.
- **A vs B는 모순이 아니었다** — "projection 없이"의 어휘 오독이었고 철회(§3.5). 비-ES는 query DB에 published-subscription 사본을 둔다.
- **LWW용 seq는 미결** — "비-ES seq 불필요"라 앞서 강하게 낸 결론은 철회한다. 사본이 order-sensitive면 seq가 필요할 수 있다. 다만 이는 **사본별 판정**이고, version 컬럼/시퀀스 테이블을 지금 전면 선박는 건 여전히 근거 없음.

### 데이터 문서에 반영할 것 (검토 후)
- **01 §1.3**: `sequence_no`의 "event_store와 동일 계열" 문구 삭제. 비-ES row NULL 허용 + relay 정렬 키를 `id`로 명시, seq는 "ES 전용 + order-sensitive 사본 확정 시 확장"으로 표기.
- **08 · DESIGN-004 §4.2(다)**: "projection 없이 / 이벤트 없는데" → "ES-fold projection 없이 **published-subscription 사본**"으로 문구 정리(모순이 아니라 표현 부정확).

### 계약에 열어둘 것 (순서대로)
1. **사본별 order-sensitivity 판정** — 각 비-ES 사본(menu·category·company·schedule·user View)이 재정렬에 취약한가.
2. R-2/R-3: 각 사본을 projection vs published-subscription 중 무엇으로 (1과 직교).
3. (order-sensitive 사본이 있으면) 그 사본에 한해 순서 소스 확정. 이 문서를 입력으로 링크.

---

## 관련 문서
- [[11-data-schema-contract-conformance]] §1 (이 문서가 심화하는 원 항목)
- [[01-command-schema]] §1.3 outbox · §2 비-ES 상태 테이블 · [[02-query-schema]] §3 (비-ES 사본) · [[00-data-index]] (물리 맵)
- [[ADR-013-db-hosting-and-read-write-topology]] (물리 분리) · [[DESIGN-004-read-model]] §4.1·§4.2·§4.3 · [[08-query-read-model-server]] §2·§4·§6·§11 (R-2·R-3)
- [[RFC-030-read-freshness-command-response-contract]] · [[RFC-025-ordering-relay-dlq-reconciliation]] · [[RFC-021-event-identity-and-global-ordering]]
