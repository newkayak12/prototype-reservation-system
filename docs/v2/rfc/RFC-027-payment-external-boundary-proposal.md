# RFC-027 — 결제 외부 경계(PG)의 확정 경로·멱등·공격면 (안 제시 · 미채택 · 구현 비참조)

> ⚠️ **이 RFC의 새 제안은 안(案)만 담는다.** 결제는 이 프로토타입에서 **실 PG 없이 log로 대체**하고 **PG 호출은 K8s 내부에서만** 일어난다. 아래 외부-PG 대응 축은 "실 PG를 붙인다면 이렇게 하는 게 정석"이라는 **설계 제안**일 뿐, **실제 구현은 이 문서를 참조하지 않는다**(결정·DESIGN·ADR 없음).
>
> **단, C34/C35가 제기한 "우리 쪽 outbox 멱등"은 미룸이 아니다** — 이건 외부 PG와 무관한 일반 문제이고, 기결정([[RFC-024-domain-event-type-and-replay-layering]]·[[RFC-025-ordering-relay-dlq-reconciliation]]·[[RFC-021-event-identity-and-global-ordering]])의 적용으로 **닫혔으며 [[DESIGN-015-payment-integration]] §6.6에 정리**했다. 이 RFC가 📎로 미루는 것은 오직 *외부 PG 표면*에 속하는 축들이다.

- **상태**: 📎 안 제시 · 미채택 (구현 비참조) — 2026-07-05
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **선행**: [[RFC-016-payment-integration-boundary]](결제 경계·ACL) · [[RFC-006-saga-process-manager]](코레오그래피 보상) · [[RFC-024-domain-event-type-and-replay-layering]]·[[RFC-025-ordering-relay-dlq-reconciliation]](outbox 멱등) · 인덱스 [[RFC-INDEX]]
- **분석 출처**: [[06-design-weakness-triage]] C34(D-015 §290·§294·§298) · C35(§292·§296)
- **왜 결정으로 안 가나**: 검증할 실 PG가 없다(log 대체). 확정하면 벤더별로 갈리는 미검증 인프라(웹훅·서명·부분환불 표현·Idempotency-Key 지원)를 기정사실로 박게 된다 → 안까지만.

---

## 배경 (Background)

### 전제: 결제 = log 대체 · PG 호출 = K8s 내부-only

**실 PG를 붙이면** 외부의 지저분한 현실이 온다 — at-least-once 웹훅·중복 통지·4xx/5xx·타임아웃·부분환불·위조 콜백·벤더별 서명 스킴. D-015는 이를 흡수하는 ACL 경계를 설계했으나, 세부(부분환불 표현·재시도 소진·대사 SLA·멱등키 공간·열거 공격면)를 "구현 사이클 위임"으로 미뤘고 그게 C34·C35다.

**이 프로토타입은** 그 현실을 두 전제로 제거한다:
- **log 대체** → PG의 실패·중복·부분환불 어휘 자체가 없다(항상 성공·웹훅 없음).
- **내부-only** → 위조·열거를 시도할 비신뢰 외부 행위자가 없다.

### 무엇이 어느 전제로 사라지나

| 축 | 없애는 전제 | 근거 |
|---|---|---|
| 열거/위조 공격면 (C35③) | **내부-only** | 외부 비신뢰 호출자 부재 → 공격 대상 없음 |
| 4xx/5xx·재시도소진·대사·부분환불·중복 웹훅 (C34·C35 외부축) | **log 대체** | log엔 실패·중복·부분 상태·정산 파일이 없음 |
| **우리 쪽 outbox 멱등** (C35①②) | *어느 전제도 아님* | 우리 relay·크래시가 만드는 중복은 log여도 그대로 → **기결정 적용으로 닫음(§6.6)** |

핵심 — **외부 PG에 속한 축만 이 📎로 미룬다.** 우리 쪽 축은 [[DESIGN-015-payment-integration]] §6.6에서 실제로 닫혔다.

---

## 제안 (안만 — 미채택)

### 안 ① 청구·환불 멱등키 공간 = 분리 (C35① 모순 정리)

**문제.** D-015 §6.1(line 182)은 "청구와 **같은** 멱등키 공간", §6.2(line 187)는 "청구·환불 키 **분리**" — 정면 모순.

**안.** **분리**가 정답. `(operation, businessId)`로 네임스페이스: `charge:{orderId}` vs `refund:{orderId}:{seq}`. 같은 공간이면 청구 키·환불 키가 우연히 겹쳐 "이미 처리함" 오판 → 환불이 청구에 흡수(이중환불 방어선이 스스로 붕괴). 분리는 구조적으로 제거. §6.1의 진짜 의도("이미 환불된 거래면 PG verify로 무시")는 *키 공간과 무관한 업무 가드*로 살아남는다 — 모순은 표기 착오지 설계 충돌이 아니다.

### 안 ② PG 호출 실패 분류와 재시도소진→failed (C34②)

**문제.** 릴레이가 4xx(영구 거절)와 5xx·타임아웃(재시도 가능)을 안 가르면 죽은 의도를 영원히 두드리고 `PaymentFailed`가 안 나가 사가가 매달린다. `payment_intent`에 "재시도소진→failed" 전이가 없다.

**안.** ACL이 PG 응답을 **재시도 가능/불가**로 분류: 4xx→즉시 `failed` 전이(재시도 무의미), 5xx·타임아웃→백오프 재시도. **재시도 상한(N회) 초과 시 `failed`로 확정 전이** + `PaymentFailed` 발행. `payment_intent` 상태 머신에 이 전이를 명시.

### 안 ③ 대사(reconciliation) 지연 상한과 이중환불 가드 (C34③)

**문제.** 웹훅 유실 시 대사가 "늦게라도" 확정하는데 주기가 "벤더 의존"이라 SLA가 없다 — 하루 1회면 확정 최대 24시간 지연.

**안.** 대사 지연 상한을 **명시적 SLI/SLA**로 못박고(예: 확정 지연 p99 < X분), 대사 발행 경로도 `event_id` dedup을 거쳐 웹훅 경로와 이중 발행하지 않게 한다(이중환불·이중확정 가드 = §6.6의 발행 멱등 재사용).

### 안 ④ 부분환불 표현 (C34①)

**문제.** 표면 3이벤트 동결인데 상태표엔 `partially_refunded`가 이미 있다 — 계약 공백.

**안 (권장).** **표면 3개 유지 + 페이로드로 흡수** — `PaymentRefunded{refundedAmount, remaining}`. 표면 개수(경계 최소)를 안 늘리고 계약만 채운다. 부분환불이 여러 번 누적되는 *상태 전이*로 풍부해지면 그때 표면 확장(`PaymentPartiallyRefunded`)을 재검토.

### 안 ⑤ 열거/위조 공격면 (C35③) — 내부-only라 범위 밖

**문제(실 PG 시).** 공격자가 가짜 거래 ID로 verify를 대량 유발 → 리소스 소모.

**안(실 PG 시).** verify를 **intent-구동**으로: 우리가 `payment_intent`(state=requested)를 가진 결제만 verify. 미지 거래 ID는 매칭 intent 없음 → PG 호출 전 기각. 콜백은 비신뢰 힌트일 뿐, 검증은 우리 intent 원장 기준. 심층방어로 rate-limit + bounded verify 재시도.

> **경고 — 틀린 교훈 금지**: "내부라 신뢰됨 → 서명검증 불필요"를 *원칙*으로 박지 말 것. eggshell/perimeter 보안 안티패턴 — 파드 탈취·SSRF·NetworkPolicy 오설정으로 뚫린다. 프로토타입에선 **"범위 밖"**이지 **"설계상 불필요"**가 아니다([[no-smuggling-undecided-infra]] 취지).

---

## 이 안들을 채택하지 않는 이유 (명시)

- **검증 대상 부재.** log 대체·내부-only라 위 축들을 실증할 실 PG·외부 트래픽이 없다.
- **벤더 의존 미검증.** 웹훅 재전송·서명 스킴·부분환불 표현·Idempotency-Key 지원은 토스/아임포트/스트라이프가 서로 다르다(D-015 §300) — 확정하면 미검증 가정을 동결.
- **결론.** 안으로 남기고 **미채택**. log→실 PG 승격 사이클에서 이 문서를 *출발점*으로 검증 후 확정. 그 전까지 **실제 구현은 이 문서를 참조하지 않는다.**

---

## 경계 (이 RFC가 다루지 않는 것)

- **우리 쪽 outbox/이벤트 발행 멱등** → 미룸 아님. [[DESIGN-015-payment-integration]] §6.6에서 [[RFC-024-domain-event-type-and-replay-layering]]·[[RFC-025-ordering-relay-dlq-reconciliation]] 적용으로 닫힘.
- **결제 경계·ACL·코레오그래피 보상 뼈대** → [[RFC-016-payment-integration-boundary]]·[[RFC-006-saga-process-manager]] 기결정.

## 관련 문서

- 원 경계: [[RFC-016-payment-integration-boundary]] · 설계: [[DESIGN-015-payment-integration]] (§6.6 우리 쪽 멱등 · Weakness §290~300)
- 분석: [[06-design-weakness-triage]] (C34·C35)
- 재사용 자산: [[RFC-021-event-identity-and-global-ordering]] · [[RFC-024-domain-event-type-and-replay-layering]] · [[RFC-025-ordering-relay-dlq-reconciliation]]
