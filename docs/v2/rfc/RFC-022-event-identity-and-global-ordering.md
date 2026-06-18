# RFC-022 — 이벤트 정체성·전역 순서

- **상태**: Open · 논의 중 · 2026-06-17
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-005-event-store-schema-evolution]] · 인덱스 [[RFC-002-decision-queue]]
- **계기**: 전수 감사 [[04-design-completeness-audit]] 횡단 미결 ①
- **닫으면**: [[05.event-store-mysql-table]] 스키마 보강(신규 ADR) + [[02-write-model]]·[[08-event-store-lifecycle]]·[[RFC-012-projection-rebuild-catchup]] 보강

## 맥락

전수 감사가 ①로 묶은 것은 사실 **한 모순처럼 보이는 세 구멍**이었다. 풀어 보면 결정해야 할 건 둘이다.

1. **정체성 부재.** [[05.event-store-mysql-table]]의 `event_store` 스키마에 `event_id`가 없다. 그런데 [[09.event-ordering-and-delivery-guarantee]]의 멱등 컨슈머/inbox는 "이 메시지를 봤는가"를 가를 **정체성**을 요구하고, [[02-write-model]]의 추적 메타 `causationId`("직전 원인")도 가리킬 **안정 식별자**가 있어야 한다. ES 이벤트는 `(aggregate_id, sequence_no)`로 정체성이 서지만, **비-ES·lookup 컨텍스트(상태+Outbox)** 이벤트는 `sequence_no`가 없어 정체성이 아예 없다 — dedup 키도, causation 앵커도 없다.
2. **전역 열거 커서 부재.** `sequence_no`는 per-aggregate다. [[RFC-012-projection-rebuild-catchup]]는 재구축 원천을 이벤트 스토어 리플레이로 두는데, 전역 단조 컬럼이 없으면 **스토어 전체를 빠짐없이·재개 가능하게 훑을 기준**이 없다. 감사는 이를 "스캔 순서 정의 불가"로 적었다.

이 둘이 "프로젝션·사가는 애그리거트를 가로지르는데 보장은 per-aggregate 순서뿐"이라는 긴장과 얽혀 한 덩어리로 보였다. 본 RFC는 셋을 풀어, **정체성은 `event_id`로, 전역 열거는 `global_seq`로** 닫고, "순서"가 애초에 요구가 아니었음을 명시한다.

## 논의

### event_id — 왜 1급 컬럼이어야 하나

ES 이벤트의 dedup은 `(aggregate_id, sequence_no)`로 이미 가능하다([[09.event-ordering-and-delivery-guarantee]] inbox 키). 문제는 **그 키가 없는 이벤트**다 — `schedule`·`user`·`authenticate` 같은 비-ES, `menu`·`category`·`company` 같은 lookup은 상태+Outbox라 스트림도 시퀀스도 없다. 이들이 발행한 이벤트는 정체성이 없어, 컨슈머가 중복 전달을 가려낼 키도, `causationId`가 가리킬 대상도 만들 수 없다.

내 입장은 **`event_id`를 전 이벤트 공통의 1급 정체성으로** 둔다. 그러면:

- **inbox/dedup 키를 `event_id`로 일반화**한다. ES·비-ES를 가리지 않고 한 키로 중복을 흡수한다([[09.event-ordering-and-delivery-guarantee]]의 `(aggregate_id, sequence_no)` inbox 키는 ES의 특수 케이스로 흡수). ES Zero-Payload upsert는 `sequence_no` 버전 가드를 **병행** 유지한다 — dedup(누가 봤나)과 순서 가드(더 과거를 덮지 마라)는 다른 일이다.
- **`causationId` = 직접 원인 메시지의 `event_id`**(원인이 커맨드면 `commandId`). **`correlationId` = 사슬 루트**로, 신규 사슬은 originating 커맨드에서 채번하고 이후 무변경 전파한다. 이로써 [[02-write-model]] §공통의 추적 메타가 실제로 가리킬 앵커가 생긴다.
- **Kafka 봉투의 `messageId`**로도 쓴다. 단 **파티션 키는 `aggregate_id` 그대로**다([[09.event-ordering-and-delivery-guarantee]] 불변) — `event_id`는 정체성이지 라우팅 키가 아니다.

생성은 **append/Outbox 기록 시점**에 한다(트랜잭션 안). 전역 유일.

### global_seq — "순서"가 아니라 "열거 커서"다

감사는 "전역 단조 커서 부재 → 재구축 스캔 순서 정의 불가"로 적었지만, 여기에 숨은 오해가 있다. **재구축에 필요한 건 전순서(total order)가 아니라, 빠짐없이·중복 없이·재개 가능하게 훑는 열거(enumeration)다.**

핵심 재구성 — 프로젝터의 정확성은 이미 다음으로 선다:

> per-aggregate 순서(파티션 키=`aggregate_id`가 전달에서 보장) + 멱등 upsert + per-aggregate 버전 가드(`sequence_no`).

그러면 **교차-애그리거트 적용 순서는 정확성 의존이 아니다** — 최종 상태가 순서 불변이다(멱등이 흡수). [[09.event-ordering-and-delivery-guarantee]]·[[16.optimistic-concurrency-control]]가 이미 "전역 순서·전역 불변식은 사지 않고, 교차는 사가"로 같은 선을 그어 뒀다. 즉 감사가 가리킨 "스캔 순서"는 *정확성으로서의 순서*가 아니라 *진행/열거로서의 커서* 문제였다.

그래서 내 입장은 **`global_seq BIGINT AUTO_INCREMENT`를 전역 삽입 커서로** 둔다. 용도는 딱 하나 — 재구축/백필이 `ORDER BY global_seq`로 스토어를 페이지네이션하고 중단점에서 재개하는 것. **교차-애그리거트 전순서 정확성은 보장하지 않음을 명문화**한다. 이게 안전한 이유:

- 재구축 백필은 **배치**다 — 모든 row가 이미 커밋된 과거를 훑으므로, AUTO_INCREMENT의 commit-순서 skew·gap(라이브 tailing의 함정)이 문제되지 않는다.
- 실시간 catch-up은 스토어 tailing이 아니라 **Kafka 구독**으로 한다([[RFC-012-projection-rebuild-catchup]] 2단 구조: 스토어로 과거, 토픽으로 현재).

#### 기각한 대안

- **`occurred_at` + tiebreak를 소비 순서로 승격**: 시계 의존(skew·역행)이라 단조 보장이 약하고, tiebreak가 per-aggregate `sequence_no`면 교차에서는 무의미. 열거 커서로 불안정.
- **`event_id`를 UUIDv7로 만들어 정체성·커서 겸용**: 컬럼 하나 아끼지만 정체성과 순서를 한 컬럼에 결합하고 단조성이 근사(시간 기반)다. 전용 `global_seq`가 "열거"라는 단일 책임으로 더 명확.

### temporal/as-of 와의 정합

[[08-event-store-lifecycle]] §3의 as-of time은 `occurred_at <= T` + `sequence_no` tiebreak인데, `sequence_no`가 per-aggregate라 **단일 애그리거트 안에서만** 결정적이다. 교차 시점 스냅이 필요하면 `global_seq <= G`가 결정적 정렬을 준다. 다만 as-of는 단일 애그리거트 복원이 기본 용도(감사·디버깅)라 영향은 작다 — 본 RFC는 "교차 as-of가 필요하면 `global_seq`가 그 기준"이라는 점만 박는다.

## 결정 요약 (스키마 델타)

```
event_store(
  global_seq   BIGINT AUTO_INCREMENT,   -- 전역 스캔 커서(재구축/백필 열거·재개 전용). 교차 전순서 아님
  event_id     BINARY(16),              -- 전역 유일 정체성. inbox/dedup·causation 앵커·Kafka messageId
  aggregate_type, aggregate_id, sequence_no,
  event_type, event_version, payload(JSON), occurred_at,
  -- 봉투 추적메타: correlation_id, causation_id, traceparent  ([[02-write-model]]·[[10-observability]])
  PRIMARY KEY (global_seq),
  UNIQUE (aggregate_id, sequence_no),    -- 순서 + 동시성 백스톱(기존, 불변)
  UNIQUE (event_id)
)
```

- 비-ES/lookup Outbox 이벤트도 `event_id`를 보유한다. **inbox dedup 키 = `event_id`**(공통).
- `(aggregate_id, sequence_no)` UNIQUE는 그대로 — 순서·동시성 제어([[16.optimistic-concurrency-control]])의 토대.

## Design으로 넘기는 것

- `event_id` 물리 표현 — `BINARY(16)` vs `CHAR(36)`, UUIDv7 채택 여부(대략 시간 정렬 보너스).
- Outbox 경로의 `event_id` 전파 배선 — 기록 시점 채번 → 봉투 → inbox.
- 비-ES inbox 적용 범위 — 자연 멱등(Zero-Payload upsert)이라 inbox를 생략할 컨텍스트 판정([[09.event-ordering-and-delivery-guarantee]] 미결과 연동).
- `global_seq` 기반 재구축 페이지네이션·재개 체크포인트 구현([[RFC-012-projection-rebuild-catchup]]).

## 닫음

- [[05.event-store-mysql-table]]는 **합의됨**이라 제자리 수정 불가 → 스키마 보강은 신규 ADR로(본 RFC가 그 근거). adr/09·dd02·dd08·RFC-012 동반 보강.

> 한 줄: **`event_id`가 정체성을 통일하고, `global_seq`는 순서가 아니라 재구축 *열거* 커서다 — 교차 순서는 여전히 사가로 푼다.**

## 관련 문서

- [[RFC-002-decision-queue]] · [[RFC-012-projection-rebuild-catchup]] · [[RFC-005-event-store-schema-evolution]]
- ADR: [[05.event-store-mysql-table]] · [[09.event-ordering-and-delivery-guarantee]] · [[16.optimistic-concurrency-control]]
- 설계: [[02-write-model]] · [[08-event-store-lifecycle]]
