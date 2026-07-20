# 05 · command-adapter (인바운드 · 아웃바운드 어댑터) [신규]

> 허브: [[00-module-index]] | 근거: [[DESIGN-002]] §4.2 · [[DESIGN-003]] · [[DESIGN-013]] (API 계약) · [[DESIGN-014]] (인가)

## 1. 책임

포트를 실제 기술에 잇는 어댑터. **application의 port/out을 infrastructure 배관으로 구현**하고, REST 인바운드를 받는다.

- `in/web` — Command REST Controller
- `out` — `EventStorePort`/`OutboxPort`/`StateStorePort` 구현체(JPA), 도메인↔JPA 수동 매핑
- command 측 Spring Security (엣지에서 검증된 클레임 헤더 수신 — pre-authenticated)

## 2. 의존성

| 항목 | 값 |
|------|-----|
| **허용 의존** | `command-application`, `command-infrastructure`, `contract-module`, `shared-module` |
| **금지** | `query` |
| **구현 시점** | **Phase 7-4** |

> 어댑터가 application(port)과 infrastructure(배관)를 **조합**한다. 예: `EventStoreJpaAdapter`는 `EventStorePort`(application)를 구현하되 실제 append/load는 infrastructure의 eventstore 엔진·JPA를 쓴다.

## 3. 사용 라이브러리

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| `spring-boot-starter-web` | `3.4.5` | Command REST Controller |
| `spring-boot-starter-validation` | `3.4.5` | 요청 DTO 검증(`hibernate-validator 8.0.1`) |
| `spring-boot-starter-security` | `3.4.5` | pre-authenticated 필터, 역할 게이트([[DESIGN-014]]) |
| `spring-boot-starter-data-jpa` | `3.4.5` | `StoredEventJpaEntity`·상태·outbox 엔티티 매핑 |
| `spring-security-crypto` | `6.4.2` | 민감정보 암호화(필요 시) |
| `springdoc-openapi-starter-webmvc-ui` | `2.6.0` | OpenAPI 문서 |
| `restdocs-api-spec-mockmvc` | `0.19.4` | Spring REST Docs → OpenAPI |
| (테스트) `kotest-*` + `kotest-extensions-spring` | `5.9.0` | 어댑터 슬라이스 (레이어 전략상 adapter=Kotest) |
| (테스트) `mockk`/`springmockk` | `1.13.10`/`4.0.2` | 목킹 |
| (테스트) `testcontainers-mysql`·`-junit`·`spring-boot-testcontainers` | `2.0.3` | 실제 MySQL 통합 테스트 |
| (테스트) `spring-security-test` | (Boot BOM) | 인증 컨텍스트 |

## 4. 구조

```
command-module/command-adapter
└── com.reservation.command.adapter
    ├── reservation/
    │   ├── in/web/
    │   │   ├── ReservationCommandController.kt   # POST /reservations, POST /reservations/{id}/cancel
    │   │   └── dto/                              # 요청/응답 DTO (+ validation)
    │   └── out/
    │       ├── EventStoreJpaAdapter.kt           # EventStorePort 구현 (append-only, L0 UNIQUE 백스톱 예외 번역 — ADR-016)
    │       ├── OutboxJpaAdapter.kt               # OutboxPort 구현
    │       ├── StoredEventJpaEntity.kt           # event_store 매핑
    │       ├── OutboxJpaEntity.kt
    │       └── mapper/                           # 도메인 상태 ↔ JPA (수동, MapStruct 미사용)
    ├── restaurant/  timetable/ …
    └── config/
        ├── CommandSecurityConfig.kt              # pre-authenticated (JwtFilter 없음 — 엣지가 검증)
        └── WebConfig.kt
```

## 5. 핵심 설계

### 5.1 EventStoreJpaAdapter — append-only + L0 예외 번역 ([[DESIGN-003]] §4.1 · [[ADR-016]])

```kotlin
@Component
class EventStoreJpaAdapter(private val repo: StoredEventJpaRepository) : EventStorePort {
    override fun append(events: List<StoredEvent>) {
        // (aggregate_id, sequence_no) UNIQUE = L0 정확성 백스톱(절대 제거 금지).
        // 정상 경로 경합은 04의 AggregateLockPort(Redisson L1 + DB 폴백 L1')가 append 이전에 이미 직렬화한다 —
        // 여기서의 UNIQUE 위반은 락 유실 등 잔여 엣지 케이스에서만 발생한다.
        try {
            repo.saveAll(events.map(::toEntity))
        } catch (e: DataIntegrityViolationException) {
            // 같은 세션에서 재시도하지 않는다 — 세션이 이미 오염됐을 수 있어 in-place 재시도는 비결정적이다.
            // 타입 있는 예외로 번역해 애플리케이션이 "infra는 raw 예외를 절대 노출하지 않는다"는 경계를 지키게 한다.
            throw AggregateConflictException(events.first().aggregateId)
        }
    }
    override fun load(aggregateId: String, fromSeq: Long) =
        repo.findByAggregateIdAndSequenceNoGreaterThanEqualOrderBySequenceNo(aggregateId, fromSeq)
            .map(::toStored)
}
```

### 5.2 API 계약·응답 신선도

command 응답은 read model 신선도를 약속하지 않는다(최종 일관성 기본 — [[DESIGN-004]] §4.7). "쓰고 바로 읽기"는 [[RFC-030]] 읽기-신선도 계약을 따른다.

### 5.3 인증

JwtFilter는 **여기 없다**. 엣지(API Gateway)가 JWT를 검증하고 `X-User-Id`/`X-User-Role` 클레임 헤더를 넘기면, command-adapter는 pre-authenticated 필터로 수신([[DESIGN-010]] §4.2 · [[ADR-024]]). 발급/검증은 [[09-auth-server-module]].

## 6. 할 일

- [ ] `EventStoreJpaAdapter` (append-only + `DataIntegrityViolationException` → `AggregateConflictException` 번역)
- [ ] `StoredEventJpaEntity` + JPA Repository (event_store 매핑)
- [ ] `OutboxJpaAdapter` + 엔티티
- [ ] 비-ES: `StateStoreJpaAdapter` (도메인 상태 ↔ JPA 수동 매핑, `@Entity` 도메인 금지)
- [ ] Command REST Controller (레퍼런스 컨텍스트)
- [ ] pre-authenticated Security 설정
- [ ] REST Docs → OpenAPI 스니펫
- [ ] 통합 테스트 (Testcontainers MySQL)

## 7. 미결

- **M-3**: ES replay 오케스트레이션 위치 — 본 설계는 application이 fold([[DESIGN-019]]), adapter는 순수 JPA I/O.
- 도메인↔JPA 수동 매핑 반복 → 국소 컨벤션(같은 자리·시그니처), MapStruct 미사용([[DESIGN-002]] §4.7).

## 8. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: append-only event_store를 JPA(`StoredEventJpaEntity` + `saveAll`)로 구현하고, 인바운드 REST 컨트롤러와 아웃바운드 JPA 어댑터를 하나의 command-adapter 모듈에 둔다.
**Steel-man**: 팀이 이미 숙달한 Spring Data JPA/Testcontainers 스택을 재사용해 별도 이벤트스토어 엔진 도입 비용 없이 append-only + `(aggregate_id, sequence_no)` UNIQUE로 정확성을 확보하고, 컨텍스트별 수직 슬라이스(reservation/·restaurant/)로 인바운드·아웃바운드를 응집시킨다.

**숨은 가정**
1. ~~Hibernate가 insert-only 엔티티를 다룰 때 세션 오버헤드·의미론이 append 워크로드에 무해하다~~ — 더 이상 핵심 리스크가 아니다: 정상 경합은 애초에 락(04 `AggregateLockPort`)이 append 이전에 직렬화하므로, UNIQUE 위반 자체가 예외 케이스로 축소됐다.
2. UNIQUE 위반 이후 **같은 세션에서 재시도하지 않는다**는 전제가 이제 §5.1 코드에 명시적으로 반영돼 있다 — 재시도는 애플리케이션이 새 트랜잭션/유스케이스 호출로 다시 시도할 때만 일어난다.
3. 도메인↔JPA 수동 매핑과 파생 쿼리 메서드가 이벤트 스키마·애그리거트 수가 늘어도 국소적으로 싸게 유지된다.

**반론**
1. `[정확성]` · **high — 해소됨(2026-07-20 동기화)** · 선례: Axon/EventStoreDB가 JPA가 아닌 전용 JDBC/append 경로를 쓰는 이유. — 이 반론은 §5.1이 "UNIQUE 위반 → 같은 세션에서 리플레이-재시도"를 암묵 전제할 때 성립했다. §5.1을 갱신해 (a) 정상 경합은 04의 `AggregateLockPort`(Redisson L1+DB 폴백 L1')가 append 전에 이미 직렬화하고, (b) 잔여 UNIQUE 위반은 `DataIntegrityViolationException`을 잡아 **같은 세션에서 재시도하지 않고** `AggregateConflictException`으로 타입 번역해 던지도록 명시했다 — 오염된 세션에서의 비결정적 in-place 재시도라는 근본 결함이 제거됐다.
2. `[결합]` · **medium** · 선례: no clear precedent — speculative concern. — 어댑터가 event_store **물리 스키마**에 이중 강결합한다: 매핑은 컬럼명에, 로드는 파생 메서드명 `findByAggregateIdAndSequenceNoGreaterThanEqualOrderBySequenceNo`에 하드코딩된다. 파티셔닝·PK 변경·아카이빙 등 append-only 스토어에서 흔한 물리 진화가 포트 시그니처와 무관하게 어댑터를 깨뜨린다.
3. `[응집도]` · **medium** · 선례: 헥사고날 원전은 인바운드/아웃바운드 어댑터를 분리. — 컨트롤러(web-slice·MockK)와 영속(Testcontainers·실 MySQL)은 변경 동인과 테스트 비용이 정반대인데 한 모듈로 묶으면 web+jpa+security+testcontainers 의존을 전부 끌고 오고, 아웃바운드 한 줄 수정이 인바운드까지 재빌드·재테스트시킨다. (컨텍스트별 수직 슬라이스라는 반론 방어는 유효하나, 그것이 인바운드·아웃바운드 **기술 스택 혼재**까지 정당화하진 않는다.)

**핵심 취약점**: 정확성-재시도 결합(구 반론 1)은 해소됐다. **남은 핵심 취약점은 반론 2** — event_store 물리 스키마(컬럼명·파생 쿼리 메서드명)에 대한 이중 강결합으로, 파티셔닝·PK 변경 같은 흔한 진화가 포트 시그니처와 무관하게 어댑터를 깨뜨릴 수 있다는 점이다.

**가역성**: reversible — 포트 경계(`EventStorePort`)가 살아 있어 어댑터 내부를 JPA→JDBC/전용 append로 교체하는 건 되돌릴 수 있다. 단 "예외 기반 동시성"을 대외 계약으로 굳혀 application·테스트가 그 예외 매핑에 의존하기 시작하면 그 계약 자체는 one-way door에 가까워진다.
