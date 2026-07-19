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
    │       ├── EventStoreJpaAdapter.kt           # EventStorePort 구현 (append-only, optimistic)
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

### 5.1 EventStoreJpaAdapter — append-only + 동시성 백스톱 ([[DESIGN-003]] §4.1)

```kotlin
@Component
class EventStoreJpaAdapter(private val repo: StoredEventJpaRepository) : EventStorePort {
    override fun append(events: List<StoredEvent>) {
        // (aggregate_id, sequence_no) UNIQUE = 정확성 백스톱(L0, 절대 제거 금지)
        // 위반 시 DataIntegrityViolation → application이 리플레이-재시도 or 409 매핑
        repo.saveAll(events.map(::toEntity))
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

- [ ] `EventStoreJpaAdapter` (append-only + optimistic concurrency)
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
1. Hibernate가 insert-only 엔티티를 다룰 때 세션(1차 캐시·dirty checking·flush 순서) 오버헤드와 의미론이 append 워크로드에 무해하다.
2. UNIQUE 위반이 flush 시점에 깔끔하게 잡히고, 그 예외 이후에도 같은 트랜잭션/세션에서 "리플레이-재시도"(5.1)가 성립한다.
3. 도메인↔JPA 수동 매핑과 파생 쿼리 메서드가 이벤트 스키마·애그리거트 수가 늘어도 국소적으로 싸게 유지된다.

**반론**
1. `[정확성]` · **high** · 선례: Axon/EventStoreDB가 JPA가 아닌 전용 JDBC/append 경로를 쓰는 이유. — `saveAll` flush 중 `(aggregate_id, sequence_no)` UNIQUE가 터지면 `DataIntegrityViolationException`이 나는데, 이 시점 Hibernate 영속성 컨텍스트는 이미 오염(부분 flush·insert 순서 깨짐)된다. 세션 `clear()` 없이 같은 트랜잭션에서 5.1이 약속한 "리플레이-재시도"를 돌리면 stale 엔티티가 재-flush되며 비결정적으로 깨진다. 즉 동시성 백스톱을 "예외로 잡아 재시도"하는 계약이 JPA 세션 의미론과 정면 충돌한다.
2. `[결합]` · **medium** · 선례: no clear precedent — speculative concern. — 어댑터가 event_store **물리 스키마**에 이중 강결합한다: 매핑은 컬럼명에, 로드는 파생 메서드명 `findByAggregateIdAndSequenceNoGreaterThanEqualOrderBySequenceNo`에 하드코딩된다. 파티셔닝·PK 변경·아카이빙 등 append-only 스토어에서 흔한 물리 진화가 포트 시그니처와 무관하게 어댑터를 깨뜨린다.
3. `[응집도]` · **medium** · 선례: 헥사고날 원전은 인바운드/아웃바운드 어댑터를 분리. — 컨트롤러(web-slice·MockK)와 영속(Testcontainers·실 MySQL)은 변경 동인과 테스트 비용이 정반대인데 한 모듈로 묶으면 web+jpa+security+testcontainers 의존을 전부 끌고 오고, 아웃바운드 한 줄 수정이 인바운드까지 재빌드·재테스트시킨다. (컨텍스트별 수직 슬라이스라는 반론 방어는 유효하나, 그것이 인바운드·아웃바운드 **기술 스택 혼재**까지 정당화하진 않는다.)

**핵심 취약점**: 5.1의 "UNIQUE 위반 → application이 리플레이-재시도"가 Hibernate 세션 오염 위에 세워져 있다는 점. 동시성 정확성의 근간이 ORM 우발 동작에 의존하므로, 재시도 시 조용한 이벤트 유실/중복 append가 발생할 수 있다.

**가역성**: reversible — 포트 경계(`EventStorePort`)가 살아 있어 어댑터 내부를 JPA→JDBC/전용 append로 교체하는 건 되돌릴 수 있다. 단 "예외 기반 동시성"을 대외 계약으로 굳혀 application·테스트가 그 예외 매핑에 의존하기 시작하면 그 계약 자체는 one-way door에 가까워진다.
