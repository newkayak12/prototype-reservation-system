# RFC-022 — 이벤트 스키마 진화 (업캐스터·타입명 매핑·직렬화 포맷)

- **상태**: ✅ 종결 (2026-06-30)
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-004-event-store-schema-evolution]]에서 분리 · 인덱스 [[RFC-INDEX]]
- **닫으면**: [[10.event-schema-evolution]] 보강·비준

---

## 배경 (Background)

### 시나리오: 코드가 v2로 바뀌어도 1월에 저장한 v1 이벤트를 읽어야 한다

1월에 `ReservationCreated`를 v1으로 저장했다.

```json
{ "eventType": "ReservationCreated", "version": 1,
  "reservationId": "...", "guestCount": 4 }
```

6월에 "흡연/금연 좌석" 요구가 생겨 `seatingType` 필드를 더한 v2로 코드를 바꿨다. 이제 새 코드가 1월의 v1 이벤트를 읽으면 `seatingType`이 없다. 옛 이벤트는 고칠 수 없고(append-only), 새 코드는 v2 모양을 기대한다. 읽는 순간 v1을 v2 모양으로 끌어올려야 한다.

이 끌어올림의 메커니즘은 [[RFC-001-v2-cqrs-and-event-sourcing]]에서 합의됐다 — JSON 페이로드, 읽을 때 업캐스팅, `eventType` 문자열 디스크리미네이터. 본 RFC가 정하는 건 그 메커니즘을 운영할 때 갈리는 결정 셋이고, 셋은 두 층으로 나뉜다.

- **층 1 — 우리 코드 안의 배선**: ① 업캐스터를 어떻게 등록·탐색하나, ② `eventType` 문자열을 어느 클래스에 묶나.
- **층 2 — 직렬화 포맷**: ③ JSON을 유지하나, Avro/Protobuf + 스키마 레지스트리로 가나.

---

## 맥락 (Context)

[[RFC-004-event-store-schema-evolution]]은 append-only 이벤트 스토어가 운영에 닿을 때의 압력 넷을 다뤘다 — 리플레이 비용, 영구 성장, 시점 질의, 스키마 진화. 앞의 셋은 *저장소를 시간 축에서 어떻게 운영하나*라는 라이프사이클 문제다. 넷째는 *직렬화 계약이 시간에 따라 어떻게 진화하나*라 결이 달라 이 RFC로 분리했다(topical, not parked).

정하는 것은 위 ①②③이다. 직렬화 규약의 미세 항목(null·enum·시간·금액 표현)과 스냅샷 포맷 진화는 저장 계약이라 [[RFC-004-event-store-schema-evolution]]·[[05.event-store-mysql-table]]에 남긴다.

---

## Goal / Non-goal

**Goal**
- 옛 버전 이벤트를 새 코드로 읽는 **업캐스터의 등록·탐색 방식**을 정한다.
- 저장된 **`eventType` 문자열을 클래스명 변경으로부터 분리**한다.
- **직렬화 포맷**의 현재 결정(JSON 유지)과 전환 기준을 정한다.

**Non-goal**
- Avro/Protobuf·스키마 레지스트리 지금 도입.
- 직렬화 미세 규약(null·enum·시간·금액) → [[RFC-004-event-store-schema-evolution]]·[[05.event-store-mysql-table]].
- 스냅샷 포맷 진화 → [[RFC-004-event-store-schema-evolution]].

---

## 논의 (Discussion)

### 논점 1. 업캐스터를 어떻게 등록·탐색하나 → [[10.event-schema-evolution]]

업캐스터는 옛 버전 이벤트를 다음 버전 모양으로 바꾸는 변환 함수다 — 위 예라면 "v1 JSON에 `seatingType`이 없으면 기본값 `UNKNOWN`을 채워 v2로 만든다". 이벤트가 영원히 남으니 변환기가 버전마다 쌓인다([[08-event-store-lifecycle]]).

검토한 선택지:
- **어노테이션 스캔** — 변환기 클래스에 꼬리표를 붙이고 시작 시 classpath를 훑어 자동 수집.
- **명시 등록 빈** — `(eventType, fromVersion)`을 키로 한곳에 직접 등록.
  ```kotlin
  registry.upcaster("ReservationCreated", from = 1, ReservationV1toV2Upcaster())
  registry.upcaster("ReservationCreated", from = 2, ReservationV2toV3Upcaster())
  ```

**결론: 명시 등록 빈.** 업캐스터는 데이터 정합성에 직결되는 코드라 추적성이 스캔의 편의보다 우선한다. 애플리케이션 시작 시 `(eventType, fromVersion)` 키 충돌을 탐지해 즉시 실패시킨다 — 같은 키에 변환기가 둘 등록되면 뜨자마자 죽는 게 운영 중 옛 이벤트를 조용히 잘못 변환하는 것보다 안전하다. (이의 여지: 변환기가 늘면 명시 등록의 보일러플레이트가 는다 — 그 명시성이 곧 추적성이라 감수한다.)

### 논점 2. eventType 문자열을 어느 클래스에 묶나 → [[10.event-schema-evolution]]

저장된 이벤트의 `"eventType": "ReservationCreated"`로 역직렬화할 클래스를 고른다. 이 문자열을 클래스명에 묶으면, 클래스명을 `ReservationCreated` → `ReservationPlaced`로 리팩터링하는 순간 저장된 옛 이벤트가 어느 클래스도 못 가리켜 깨진다. 목표는 논리 타입명을 클래스명에서 떼어내 고정하는 것이다.

검토한 선택지:
- **`@JsonTypeName` 스캔** — 클래스에 `@JsonTypeName("ReservationCreated")` 어노테이션.
- **명시 등록 매핑** — `registry.type("ReservationCreated", ReservationCreatedV2::class)`.

**결론: 명시 등록 매핑.** `@JsonTypeName`은 논리명을 클래스에 얹어 클래스 리팩터링 시 사고 위험이 남는다. 명시 매핑은 "이 논리명 = 이 클래스" 계약을 코드 한곳에 박아 클래스명 변경과 분리한다(논점 1과 같은 철학). 시작 시 eventType↔클래스 누락·충돌도 검사한다.

### 논점 3. JSON을 유지하나, Avro/Protobuf로 가나 → [[10.event-schema-evolution]]

직렬화 포맷 후보를 같은 층에서 비교한다.

| | JSON (현재) | Avro / Protobuf |
|---|---|---|
| 형태 | 텍스트, 필드명 포함(자기서술) | 바이너리, 필드명 없이 태그·순서 |
| 스키마 위치 | 우리 코드(클래스) | 별도 스키마 파일(.avsc / .proto) |
| 진화 방식 | 읽을 때 코드가 업캐스팅(논점 1·2) | 포맷이 호환 규칙 내장(필드 추가+default 등) |
| 크기·속도 | 큼·느림 | 작음·빠름 |

스키마 레지스트리는 포맷이 아니라 별도 인프라 서비스다 — Avro/Protobuf를 쓸 때 스키마 버전을 중앙에 보관하고 생산자·소비자 호환성을 기계로 강제한다(예: Confluent Schema Registry). 메시지엔 `schema-id`만 싣고 소비자가 그 id로 스키마를 받아 푼다. "Avro로 간다"와 "레지스트리를 둔다"는 별개 결정이다.

**결론: JSON+업캐스팅 유지, Avro/레지스트리 미도입(YAGNI).** 판단 기준은 *누가 이벤트를 푸느냐*다. 지금은 우리 코드만 이 이벤트를 읽으니, 스키마 계약을 업캐스터(논점 1·2)가 들고 있는 게 더 싸고 통제된다. Avro+레지스트리의 값은 우리가 통제하지 못하는 외부·폴리글랏 컨슈머가 이 이벤트를 직접 역직렬화·구독할 때 나온다. 도입 기준은 *우리 코드 밖의 컨슈머가 이 이벤트를 직접 구독·역직렬화한다는 게 증명될 때*이며, 그때 별도 ADR로 도입한다([[RFC-003-messaging-delivery]]의 통합 이벤트 페이로드 논의와 연계).

---

## 결정 요약

| # | 결정 | ADR |
|---|------|-----|
| 1 | 업캐스터 = **명시 등록 빈**, 시작 시 `(eventType, fromVersion)` 충돌로 빠른 실패 | [[10.event-schema-evolution]] |
| 2 | eventType↔클래스 = **명시 등록 매핑** (클래스명 변경과 분리) | [[10.event-schema-evolution]] |
| 3 | 직렬화 = **JSON+업캐스팅 유지**, Avro/레지스트리는 외부·폴리글랏 컨슈머 직접 구독 증명 시 별도 ADR | [[10.event-schema-evolution]] · [[RFC-003-messaging-delivery]] |

---

## 관련 문서

- [[RFC-004-event-store-schema-evolution]] (분리 원본) · [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-003-messaging-delivery]]
- [[10.event-schema-evolution]] · [[05.event-store-mysql-table]] · [[08-event-store-lifecycle]]
