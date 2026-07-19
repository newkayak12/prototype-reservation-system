# ADR-010: 이벤트 스키마 진화 — 명시 등록 업캐스터·타입 매핑, JSON 유지

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-022-event-schema-evolution]] · **설계**: [[DESIGN-009-event-store-lifecycle]]
- **연관 ADR**: [[ADR-005-event-store-mysql-table]] · [[ADR-002-selective-event-sourcing-scope]] · [[ADR-018-event-store-recovery-semantics]] · [[ADR-022-event-identity]]

---

## 맥락과 문제 (Context and Problem Statement)

이벤트 스토어는 append-only다([[ADR-005-event-store-mysql-table]]). 1월에 저장한 `ReservationCreated` v1 이벤트는 6월에 코드가 v2로 바뀌어도 그 모양 그대로 영원히 남는다. 예를 들어 1월엔 `{ "eventType": "ReservationCreated", "version": 1, "reservationId": "...", "guestCount": 4 }`만 있었는데, 6월에 "흡연/금연 좌석" 요구로 `seatingType` 필드가 추가된 v2로 코드가 바뀌면, 새 코드가 1월 이벤트를 읽는 순간 `seatingType`이 없다. 옛 이벤트는 고칠 수 없으니(append-only) 읽는 시점에 v1 모양을 v2 모양으로 끌어올려야 한다.

이 끌어올림(업캐스팅)의 메커니즘 자체 — JSON 페이로드, 읽을 때 업캐스팅, `eventType` 문자열 디스크리미네이터 — 는 이미 합의됐다([[RFC-001-v2-cqrs-and-event-sourcing]]). 이 ADR이 확정하는 건 그 메커니즘을 실제로 운영할 때 갈리는 결정 셋이다: 업캐스터를 어떻게 등록·탐색하는가, 저장된 `eventType` 문자열을 어느 클래스에 묶는가, 그리고 직렬화 포맷을 JSON으로 유지하는가.

**업캐스터·타입 매핑의 배선 방식과 직렬화 포맷을, 데이터 정합성과 리팩터링 안전성을 지키면서 어떻게 확정하는가.**

## 결정 동인 (Decision Drivers)

- 업캐스터는 데이터 정합성에 직결되는 코드다 — 잘못 매칭되면 옛 이벤트가 조용히 잘못 변환된다. 추적성이 편의보다 우선한다.
- 클래스명 리팩터링이 저장된 이벤트의 논리 타입 식별을 깨서는 안 된다.
- 지금은 우리 코드만 이 이벤트를 읽는다 — 외부·폴리글랏 컨슈머가 없는 상태에서 무거운 인프라(스키마 레지스트리)를 미리 들이지 않는다(YAGNI).

## 검토한 선택지 (Considered Options)

**업캐스터 등록·탐색**
- **어노테이션 스캔** — 변환기 클래스에 꼬리표를 붙이고 시작 시 classpath를 훑어 자동 수집.
- **명시 등록 빈** — `(eventType, fromVersion)`을 키로 한곳에 직접 등록.

**eventType↔클래스 매핑**
- **`@JsonTypeName` 스캔** — 클래스에 `@JsonTypeName("ReservationCreated")` 어노테이션.
- **명시 등록 매핑** — `registry.type("ReservationCreated", ReservationCreatedV2::class)`로 한곳에 등록.

**직렬화 포맷**
- **JSON 유지** — 텍스트, 필드명 포함(자기서술), 진화는 코드가 읽을 때 업캐스팅으로 흡수.
- **Avro/Protobuf + 스키마 레지스트리** — 바이너리, 별도 스키마 파일, 포맷이 호환 규칙(필드 추가+default 등)을 내장. 스키마 레지스트리는 포맷과 별개의 인프라 서비스로, 스키마 버전을 중앙에 보관하고 생산자·소비자 호환성을 기계로 강제한다(예: Confluent Schema Registry).

## 결정 (Decision Outcome)

**채택: 업캐스터=명시 등록 빈, eventType↔클래스=명시 등록 매핑, 직렬화=JSON+업캐스팅 유지.** 셋 다 같은 철학을 공유한다 — 데이터 정합성에 닿는 계약은 스캔의 편의보다 한곳에 명시적으로 박아 추적성을 얻는 쪽을 택했고, 직렬화는 지금 이 이벤트를 읽는 게 우리 코드뿐이라는 사실이 무거운 대안의 값을 없앤다.

| # | 결정 | 근거 |
|---|------|------|
| 1 | 업캐스터 = **명시 등록 빈**. 애플리케이션 시작 시 `(eventType, fromVersion)` 키 충돌을 탐지해 **빠른 실패** | 같은 키에 변환기가 둘 등록되면 뜨자마자 죽는 게, 운영 중 옛 이벤트를 조용히 잘못 변환하는 것보다 안전하다 |
| 2 | eventType↔클래스 = **명시 등록 매핑**. 클래스명 변경과 분리 | `@JsonTypeName`은 논리명을 클래스에 얹어 클래스 리팩터링 시 사고 위험이 남는다. 명시 매핑은 "이 논리명 = 이 클래스" 계약을 코드 한곳에 박는다 |
| 3 | 직렬화 = **JSON+업캐스팅 유지**. Avro/레지스트리 미도입(YAGNI) | 판단 기준은 *누가 이벤트를 푸느냐*다. 지금은 우리 코드만 읽으니 스키마 계약을 업캐스터(결정 1·2)가 들고 있는 게 더 싸고 통제된다 |

예시:

```kotlin
registry.upcaster("ReservationCreated", from = 1, ReservationV1toV2Upcaster())
registry.upcaster("ReservationCreated", from = 2, ReservationV2toV3Upcaster())
registry.type("ReservationCreated", ReservationCreatedV2::class)
```

업캐스터는 옛 버전 이벤트를 다음 버전 모양으로 바꾸는 변환 함수다 — 위 시나리오라면 "v1 JSON에 `seatingType`이 없으면 기본값 `UNKNOWN`을 채워 v2로 만든다". 결정 1·2는 같은 레지스트리 층에서 함께 확정된다 — 둘 다 시작 시 누락·충돌을 검사하는 명시 등록이라는 같은 배선 위에 선다.

Avro/Protobuf+레지스트리로의 전환 기준은 *우리 코드 밖의 컨슈머가 이 이벤트를 직접 구독·역직렬화한다는 게 증명될 때*이며, 그때는 새 ADR로 도입한다.

**스냅샷 포맷 진화는 이 ADR의 범위가 아니다.** [[DESIGN-009-event-store-lifecycle]] §5.2·§6.1은 스냅샷 `schema_version`이 코드 버전과 불일치하면 업캐스팅하지 않고 폐기 후 이벤트 리플레이로 재생성하는 결정을 이미 담고 있다 — 스냅샷은 "버릴 수 있는 캐시"라 이벤트 수준 업캐스터 유지 비용을 지지 않는다. 스냅샷 저장 계약의 세부(스키마 버전 필드 형식, 정합성 검증 절차)는 [[RFC-022-event-schema-evolution]] §33·§47이 [[RFC-004-event-store-schema-evolution]]으로 위임한 문서화된 seam이며, 이 ADR은 그 경계를 [[RFC-004-event-store-schema-evolution]]로 상호참조할 뿐 결정하지 않는다.

**재해 복구·백업·PITR·파티션(콜드/핫) 셰딩은 이 ADR의 결정이 아니다.** 이벤트 스토어가 손상되거나 복구가 필요한 상황에서 업캐스터·타입 매핑이 어떻게 작동해야 하는지는 [[ADR-018-event-store-recovery-semantics]]가 다룬다.

### 결과 (Consequences)

- 좋은 점: 업캐스터·타입 매핑이 시작 시 충돌·누락을 fast-fail로 잡아, 운영 중 옛 이벤트가 조용히 잘못 읽히는 사고를 방지한다.
- 좋은 점: 클래스명 리팩터링이 저장된 이벤트의 식별을 깨지 않는다 — 논리 타입명과 클래스명이 분리됐다.
- 좋은 점: 지금 필요 없는 스키마 레지스트리 인프라를 들이지 않는다. 전환 기준(외부 컨슈머 직접 구독 증명)이 명확해 나중에 판단이 쉽다.
- 나쁜 점 / 트레이드오프: 변환기·타입 매핑이 늘수록 명시 등록의 보일러플레이트가 쌓인다 — 그 명시성이 곧 추적성이라 감수한다.
- 나쁜 점 / 트레이드오프: 읽기 시점 업캐스팅은 리플레이마다 체인을 반복 실행한다. v1→v2→v3로 스키마가 누적되면 가장 오래된 이벤트는 매 읽기마다 다단 체인을 탄다. **재검토 트리거**: 업캐스팅 누적 비용이 리플레이 p99에 실증적으로 영향을 주면 in-place 마이그레이션이나 스냅샷 재생성 주기 단축을 재검토한다.

### 확인 (Confirmation)

- 애플리케이션 시작 시 `(eventType, fromVersion)` 키 충돌 또는 eventType↔클래스 매핑 누락·충돌이 있으면 부팅이 실패하는지 통합 테스트로 검증한다.
- v1로 저장된 이벤트를 v2 코드로 로드했을 때 업캐스터 체인을 거쳐 정확한 v2 모양으로 역직렬화되는지 테스트한다.
- 클래스명을 변경해도 기존에 저장된 이벤트의 `eventType` 문자열 기반 역직렬화가 깨지지 않는지 리팩터링 회귀 테스트로 확인한다.

## 선택지 상세 (Pros and Cons of the Options)

### 어노테이션 스캔 (업캐스터, 기각)
- 장점: 변환기 클래스를 추가하기만 하면 자동 수집돼 등록 보일러플레이트가 없다.
- 단점: 등록 목록이 코드베이스에 흩어져 한눈에 안 보이고, classpath 스캔 시점까지 충돌을 알 수 없다.
- 기각 사유: 데이터 정합성에 직결되는 코드라 "여기 다 적혀 있다"는 추적성이 스캔의 편의보다 우선한다.

### `@JsonTypeName` 스캔 (타입 매핑, 기각)
- 장점: 클래스 선언부만 보면 논리 타입명을 알 수 있어 별도 등록 코드가 없다.
- 단점: 논리명이 클래스에 얹혀 있어 클래스명 리팩터링 시 어노테이션을 함께 옮기지 않으면 조용히 깨질 위험이 남는다.
- 기각 사유: 명시 등록 매핑이 "이 논리명 = 이 클래스" 계약을 클래스 밖 한곳에 박아 클래스명 변경과 분리한다.

### Avro/Protobuf + 스키마 레지스트리 (직렬화, 기각)
- 장점: 바이너리라 크기·속도 이득이 있고, 포맷 자체가 필드 추가+default 같은 호환 규칙을 내장한다.
- 단점: 별도 스키마 파일(.avsc/.proto)과 레지스트리 인프라(예: Confluent Schema Registry)를 새로 운영해야 한다.
- 기각 사유: 지금은 우리 코드만 이 이벤트를 읽어 업캐스터가 스키마 계약을 들고 있는 게 더 싸다. 외부·폴리글랏 컨슈머가 직접 구독·역직렬화한다는 게 증명되면 재검토한다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 업캐스터 레지스트리 빈의 구체 API 시그니처, 시작 시 검증 로직의 구현 위치(부트스트랩 vs 별도 헬스체크), 업캐스터 체인 누적 비용의 실측 및 재검토 트리거 판정.
- 관련: [[RFC-022-event-schema-evolution]] · [[RFC-001-v2-cqrs-and-event-sourcing]] · [[RFC-004-event-store-schema-evolution]] · [[DESIGN-009-event-store-lifecycle]] · [[ADR-005-event-store-mysql-table]] · [[ADR-018-event-store-recovery-semantics]]
