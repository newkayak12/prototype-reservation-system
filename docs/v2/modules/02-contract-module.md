# 02 · contract-module [신규]

> 허브: [[00-module-index]] | 근거: [[DESIGN-002]] §8 · [[DESIGN-019]] · [[ADR-021]] (통합 이벤트 계약 모듈) · [[RFC-023]] (계약 관리) · [[ADR-022]] (이벤트 정체성)

## 1. 책임

command와 query(그리고 auth-server)의 **유일한 공유 접점**. 이 모듈의 이벤트 스키마가 곧 팀/서비스 간 계약이다.

- `AbstractEvent` — 통합 이벤트(Integration Event) 기반 타입/봉투
- 구체 도메인 **통합 이벤트** 클래스 (`ReservationCreated`, `SeatHeld`, `RestaurantRenamed` …)
- 공유 ID 타입, 이벤트 메타데이터(정체성·추적 봉투)

> **내부 도메인 이벤트 ≠ 통합 이벤트**([[RFC-023]] · [[DESIGN-008]] §4.12). command-core가 반환하는 `DomainEvent`는 core 소유(비공개)이고, Kafka 경계로 나가는 것은 여기 정의한 **통합 이벤트**뿐이다. core→contract 매핑은 `command-application`이 수행한다([[DESIGN-019]] §3).

## 2. 의존성

| 항목 | 값 |
|------|-----|
| **허용 의존** | `shared-module` |
| **피의존** | `command-application`, `command-infrastructure`(발행 경로), `query-module`, `auth-server-module`, `test-module` |
| **금지** | `command-core` 직접 의존 금지, `command-adapter`·`query` 직접 의존 금지 |
| **구현 시점** | **Phase 7-1 (최우선)** |

## 3. 사용 라이브러리

계약 모듈은 **가볍게** 유지한다 — Spring/JPA 없이 직렬화 가능한 데이터 클래스 + 봉투만.

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| Kotlin stdlib | `2.0.10` | data class 이벤트 |
| `jackson-module-kotlin` | (Boot BOM 정렬) | JSON 직렬화/역직렬화(이벤트 봉투). Kotlin data class 지원 |
| `com.fasterxml.uuid:java-uuid-generator` | `4.3.0` | `eventId`(UUIDv7) 타입 다룰 때 (생성 자체는 infra의 idgen) |
| (테스트) `kotest-*` | `5.9.0` | 직렬화 라운드트립 테스트 |

> **금지**: `spring-kafka`(발행은 infra), `jakarta.persistence`. contract는 "무엇을 주고받나"만 정의하고 "어떻게 나르나"(Kafka)·"어떻게 저장하나"(JPA)는 모른다.

## 4. 구조

```
contract-module
└── com.reservation.contract
    ├── event/
    │   ├── AbstractEvent.kt          # 봉투: eventId, aggregateId, aggregateType,
    │   │                             #        eventType, occurredAt, version + 추적메타
    │   ├── reservation/              # ReservationCreated, ReservationConfirmed, ReservationCancelled ...
    │   ├── timetable/                # TimeTableCreated, SeatHeld, SeatReleased ...
    │   └── restaurant/               # RestaurantRegistered, RestaurantRenamed ...
    ├── id/                           # 공유 ID 타입(있으면)
    └── meta/                         # correlationId/causationId/traceparent 봉투 타입
```

## 5. 핵심 설계

### 5.1 AbstractEvent 봉투 ([[DESIGN-003]] §4.4 · [[ADR-022]])

```kotlin
// 개념 — 실제 시그니처는 구현 사이클 확정
interface AbstractEvent {
    val eventId: UUID          // UUIDv7 — 전역 dedup/causation 앵커, Kafka messageId 겸용
    val aggregateType: String
    val aggregateId: String
    val sequenceNo: Long
    val eventType: String      // 타입 태그(FQCN 아님) — 업캐스팅 대상 (RFC-022)
    val eventVersion: Int      // 스키마 진화
    val occurredAt: Instant
    // 추적 봉투 (발행 경로에서 충전 — DESIGN-003 §4.4)
    val correlationId: String
    val causationId: String?
    val traceparent: String?
}
```

### 5.2 페이로드 정책 (미결, 방향만) — ES/비-ES 분기

[[DESIGN-008]] §4.12는 "thin(Zero Payload) 지향"을 잠정 전제로 깐다. [[DESIGN-003]] 자기리뷰는 **ES=이벤트-carried(불변이라 실어도 stale 아님) / 비-ES=Zero Payload** 분기를 제안한다. contract 이벤트 필드 설계 시 이 분기를 반영하되, 최종 확정은 스키마 RFC([[RFC-029]] event-carried-payload-uniform)에서.

## 6. 할 일

- [ ] `AbstractEvent` 봉투 설계 (정체성 + 추적메타 — [[ADR-022]])
- [ ] 레퍼런스 컨텍스트(`timetable`, `reservation`) 통합 이벤트 클래스 정의
- [ ] 이벤트 버전/직렬화 전략 확정 (JSON + `eventType` 매핑 — [[ADR-010]], [[RFC-022]])
- [ ] `eventType → 클래스` 복원 레지스트리의 **소유 위치** 확정 (application이 복원 소유 — [[DESIGN-019]] §6, 레지스트리 정의 자체는 여기/RFC-022 경계)
- [ ] Gradle 모듈 생성 + build.gradle.kts (Spring/JPA 배제 확인)

## 7. 미결

- **M**: 페이로드 thin/fat 및 ES/비-ES 분기 확정 → [[RFC-029]]
- **M**: 업캐스터·`eventType` 레지스트리를 contract에 둘지 infra/application에 둘지 → [[RFC-022]] · [[DESIGN-019]] §6

## 8. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: command·query·auth의 유일한 공유 접점을 통합 이벤트 계약 모듈로 반출하고, JSON + `eventType` 디스크리미네이터 + `eventVersion` 봉투(`AbstractEvent`)로 계약·진화를 담는다.

**Steel-man**: command/query/auth가 **서로 다른 배포 주기·서로 다른 팀**으로 실제 분리되고, 통합 이벤트를 **우리가 통제하지 못하는 외부 컨슈머**가 직접 구독·역직렬화하는 순간, 이 봉투와 버전 필드는 그 독립 라이프사이클 사이의 유일한 안정 계약이 되어 값을 한다.

### 숨은 가정

1. command/query/auth가 **독립 배포체**라서, 런타임 업캐스팅과 버전드 published 계약이 필요할 만큼 릴리스 주기가 어긋난다. (실제로는 단일 레포·단일 팀 — 같이 배포될 개연성이 높다.)
2. 하나의 `AbstractEvent` 봉투가 **세 관심사를 동시에** 무충돌로 담는다 — 이벤트 스토어의 정체성/순서(`sequenceNo`, dedup 앵커 `eventId`), Kafka 전송(`messageId`), 그리고 서비스 간 published 계약.
3. JSON + `eventType` 문자열이 **영구 와이어 계약**으로 충분하다 — 기계 강제 호환(스키마 레지스트리)이 필요해질 컨슈머는 안 생긴다.

### 반론

1. **[structural · high]** *§5.2 페이로드 정책이 이미 종결된 상위 결정과 모순된다.* — Steel-man: "최종 확정은 스키마 RFC에서"라 미결로 남기는 건 신중해 보인다. 그러나 [[RFC-029]]는 **2026-07-05 합의**로 "ES=event-carried / 비-ES=Zero Payload" **분기 제안 자체(D-003 line 193)를 supersede**하고 event-carried 일원화로 닫았으며, 통합 이벤트는 이미 값을 싣는다고 못박았다([[RFC-023]] 정합, RFC-029 Non-goal). 이 문서 §5.2·§7-M은 폐기된 분기를 여전히 "방향"으로 들고 미결로 표기한다 — **이미 죽은 정책에 맞춰 이벤트 필드를 설계할 위험**이 그대로 남아 있다. 선례: 상위 RFC가 supersede한 뒤 갱신 안 된 하위 스펙에 맞춰 스키마를 구현하는 drift — 흔한 실패 패턴.
2. **[assumption · high]** *모듈 존재 근거가 상위 RFC의 YAGNI 논거와 정면충돌한다.* — Steel-man: 계약을 별도 모듈로 반출하면 경계가 명시되어 규율이 선다. 그러나 [[RFC-022]] 논점 3은 "**지금은 우리 코드만 이 이벤트를 읽으니**" JSON 유지·Avro/레지스트리 미도입(YAGNI)을 결론냈다. 같은 전제(외부 컨슈머 없음)라면 §1의 "이벤트 스키마가 곧 **팀/서비스 간 계약**"이라는 격상도, `eventVersion`·published 봉투 apparatus도 아직 **없는 생산자/소비자 독립성**을 위한 선반영이다. RFC-022는 그 전제로 Avro를 유예하면서, 이 모듈은 같은 전제 위에 계약 세리머니를 Phase 7-1 최우선으로 세운다 — 동일 YAGNI 기준의 비일관 적용. 선례: 서비스를 결합만 늘리는 공용 "common/contract" jar의 조기 반출.
3. **[structural · medium]** *하나의 봉투가 이벤트 스토어 의미를 published 계약으로 누설한다.* — Steel-man: 봉투 통일은 추적메타·정체성을 한곳에 모아 편하다. 그러나 `sequenceNo`는 쓰기모델/이벤트 스토어의 순서 개념인데, 이걸 query/auth가 소비하는 **공유 계약 봉투**에 실으면 내부 저장 순서가 published 언어로 샌다. 게다가 [[DESIGN-019]] line 114는 event_store에 **contract(통합) 이벤트를 저장하지 말라**(리플레이가 발행 계약에 묶임)고 명시하고 §6은 스토어에 내부 `StoredEvent`를 넣는다 — 그렇다면 통합 이벤트 봉투가 왜 스토어 정체성 필드(`sequenceNo`)를 지녀야 하는지 근거가 무너진다. 선례: 전송·저장·계약을 겸하는 봉투-갓오브젝트가 관심사 누설로 진화를 잠근다.

### 핵심 취약점

가장 깊은 결함은 특정 필드가 아니라 **모듈이 "계약 경계"를 독립 라이프사이클이 존재하기 전에 실체화한다**는 점이다. 이 문서의 존재 이유(§1 "팀/서비스 간 계약")와 그 하위 기계 전부(`eventVersion`, 업캐스터/레지스트리 소유 다툼 §7, published 봉투)는 **생산자-소비자 독립 배포**를 전제하는데, 실제 소비자(command/query/auth)는 같이 배포되는 한 팀의 모듈이다. RFC-022가 "외부 컨슈머 없음 → YAGNI"로 Avro를 유예한 바로 그 논리가, 이 모듈이 주장하는 계약 스코프의 근거를 그대로 잘라낸다. 경계는 증명된 게 아니라 선언돼 있다.

### 가역성

**혼합**. 모듈 존재·내부 필드 조정은 reversible. 그러나 `eventType` 디스크리미네이터 + `eventVersion`이 박힌 JSON은 append-only로 **저장/발행되는 순간 one-way door** — 한번 나간 와이어/저장 형태는 영원히 forward-읽기(업캐스팅)로 떠안아야 한다.
