# 08 · query — Read Model 서버 (조회 API) [신규]

> 허브: [[00-module-index]] | 근거: [[DESIGN-004]] (읽기 모델) · [[DESIGN-002]] §4.3 (layered) · [[DESIGN-010]] §4 (workload) · [[DESIGN-014]] (인가) · [[RFC-030]] (읽기 신선도 계약) · [[DESIGN-018]] (캐싱)

query-module의 **읽기 경로** — read model을 조회해 응답 DTO로 돌려주는 조회 API 서버. 쓰기 경로(이벤트 소비→투영)는 [[07-query-projection-server]].

> **projection 서버와의 관계**([[DESIGN-004]] §4.6): 둘은 같은 read model 테이블을 공유하되 **방향이 반대**다. projection은 들어오는 이벤트로 **쓰고**(쓰기 경로), 이 서버는 화면 질의로 **읽는다**(읽기 경로). 둘을 한 트랜잭션으로 묶지 않는다. 런타임 워크로드도 분리([[DESIGN-010]] §4.1) — 조회 트래픽이 폭증해도 projector는 영향받지 않고, projector가 lag이 쌓여도 조회는 계속 서빙(최종 일관성).

---

## 1. 책임

- Query REST Controller (조회 API)
- Query Service (read model → 응답 DTO 변환, 읽기 전용 트랜잭션)
- Repository (QueryDSL / JPA read model 조회)
- Read Model 엔티티 / 응답 DTO (도메인별 스키마 분리)
- 비-ES 컨텍스트의 기존 QueryDSL 직접 조회 유지

## 2. 의존성

| 항목 | 값 |
|------|-----|
| **허용 의존** | `contract-module`, `shared-module` |
| **금지** | **`command-*` 전체** — query는 command 테이블을 직접 조회하지 않는다(스키마 결합 = 안티패턴) |
| **구조** | Layered (web/service/repository/model — 포트/어댑터 없음. 읽기는 "DB→DTO"라 layered가 경제적) |
| **구현 시점** | **Phase 7-5** |

## 3. 사용 라이브러리

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| `spring-boot-starter-web` | `3.4.5` | Query REST Controller |
| `querydsl-jpa` (+ `querydsl-apt`, kapt) | `5.1.0` | **타입 세이프 read model 조회** (검색·정렬·페이징) |
| `spring-boot-starter-data-jpa` | `3.4.5` | read model 엔티티 조회 |
| `mysql-connector-j` | `8.0.33` | query MySQL(프라이머리/HA 레플리카) 드라이버 |
| `spring-boot-starter-security` | `3.4.5` | pre-authenticated 필터 + 스코프 조건([[DESIGN-014]]) |
| `springdoc-openapi-starter-webmvc-ui` | `2.6.0` | 조회 API OpenAPI |
| `restdocs-api-spec-mockmvc` | `0.19.4` | REST Docs → OpenAPI |
| `jakarta-annotation-api` / `jakarta-persistence-api` | `3.0.0` / `3.1.0` | QueryDSL Q타입 생성(kapt) |
| `spring-boot-starter-actuator` | `3.4.5` | 헬스/레디니스 |
| (테스트) `kotest-*` + `kotest-extensions-spring` | `5.9.0` | 조회 슬라이스 |
| (테스트) `testcontainers-mysql`·`-junit` | `2.0.3` | 실제 read model 조회 |
| (테스트) `fixture-monkey-kotlin` | `1.1.11` | read model 픽스처 |

> **주의 — Redis 없음**([[DESIGN-004]] §4.8): read model 앞에 **캐시 층을 얹지 않는다**. 프로젝션 read model이 이미 조회 모양으로 비정규화·영속된 "머티리얼라이즈드 캐시"라 캐시 위의 캐시가 된다. 핫 쿼리는 Redis가 아니라 **전용 프로젝션 추가**로([[07-query-projection-server]] §7 자격), 읽기 확장은 **HA 레플리카**로 분산. → 이 서버는 `spring-boot-starter-data-redis`를 의존하지 않는다.

## 4. 구조

```
query-module/com.reservation.query
├── reservation/
│   ├── web/ReservationQueryController.kt   # GET /reservations, GET /reservations/{id}
│   ├── service/ReservationQueryService.kt  # read model → 응답 DTO (읽기 전용 txn)
│   ├── repository/ReservationQueryRepository.kt  # QueryDSL
│   └── model/
│       ├── ReservationView.kt              # read model 엔티티 (식당명 비정규화 포함)
│       └── ReservationResponse.kt          # 응답 DTO
├── timetable/  (GET /timetables/available)
├── restaurant/ (GET /restaurants, /restaurants/{id})
├── {비-ES: schedule/user/menu/category/company}/
│   └── repository/  # QueryDSL 직접 조회 (projection 없이 자기 테이블)
└── config/
    ├── QueryDslConfig.kt
    ├── JpaReadConfig.kt          # query DataSource (HA 레플리카 라우팅 가능)
    └── QuerySecurityConfig.kt    # pre-authenticated + 스코프
```

> **도메인별 스키마 분리**([[DESIGN-004]] §4.2): read model은 화면·조회 용도마다 여럿 생기는데, 한 query 인스턴스 안에서 **도메인별 스키마**(`query.{domain}.model`)로 나눠 담는다. 도메인 경계 = 스키마 경계 → command 측 컨텍스트 분리와 대칭.

---

## 5. 조회 경로 (read model → DTO)

```kotlin
@RestController
class ReservationQueryController(private val service: ReservationQueryService) {
    @GetMapping("/reservations")
    fun list(
        @AuthenticationPrincipal user: Principal,
        @RequestParam(required = false) sequenceNo: Long?,   // RFC-030 read-after-write 토큰(§7)
        page: Pageable,
    ): Page<ReservationResponse> =
        service.findMyReservations(user.id, sequenceNo, page)   // 조인 없이 비정규화 read model 읽기
}

@Service
@Transactional(readOnly = true)   // 읽기 전용 — projection 트랜잭션과 분리
class ReservationQueryService(
    private val repo: ReservationQueryRepository,
    private val freshnessGate: ReadFreshnessGate,   // RFC-030 §7 — seq ≥ N 짧은 대기(long-poll)/폴백
) {
    fun findMyReservations(userId: String, minSequenceNo: Long?, page: Pageable): Page<ReservationResponse> {
        minSequenceNo?.let { freshnessGate.awaitOrFallback(userId, it) }   // 못 왔으면 짧게 대기 후 폴백
        return repo.searchByUser(userId, page).map(ReservationResponse::from)
    }
}
```

### 5.0.1 `ReadFreshnessGate` ([[RFC-030]] §논점4·결정4)

read model row(`ReservationView` 등)는 [[07-query-projection-server]] §5.2가 적용한 원본 `appliedSequenceNo`를 보유한다. `ReadFreshnessGate`는 요청받은 `sequenceNo`(클라가 커맨드 응답의 권위 바디에서 받은 값)와 row의 `appliedSequenceNo`를 비교해 `seq ≥ N`이면 즉시 통과, 아니면 짧게 대기(bounded long-poll, 예: 최대 500ms 폴링)한 뒤에도 못 따라잡으면 폴백(그냥 현재 값 반환 — 최종 일관성으로 강등). 이 게이트가 [[RFC-030]] 결정 4의 유일한 구현 자리다 — command 서버나 별도 조정자가 아니라 **이 읽기 서버**가 신선도 계약을 진다.

### 5.1 QueryDSL 조회 (검색·정렬·페이징)

```kotlin
@Repository
class ReservationQueryRepository(private val query: JPAQueryFactory) {
    fun searchByUser(userId: String, page: Pageable): Page<ReservationView> {
        val where = reservationView.userId.eq(userId)
        val content = query.selectFrom(reservationView)
            .where(where)
            .orderBy(reservationView.visitAt.desc())
            .offset(page.offset).limit(page.pageSize.toLong())
            .fetch()
        // count 쿼리 분리 …
    }
}
```

---

## 6. 읽기 소스 전략 (컨텍스트별) ([[DESIGN-004]] §4.2~4.3)

| 컨텍스트 | 읽기 소스 | projection | 비고 |
|----------|-----------|:---:|------|
| `reservation` | 이벤트 프로젝션 | O | ES — 예약 목록/상세, 식당명 비정규화 |
| `timetable` | 이벤트 프로젝션 | O | ES — 가용시간 뷰 |
| `restaurant` | 이벤트 프로젝션 | O | ES — 검색·상세 |
| `schedule` | 프로젝션 or 경량 lookup | TBD | 변화 빈도 측정 후 |
| `user` | 경량 프로젝션 | 단순 | Outbox 이벤트 구독 |
| `menu` | (나) lookup — projection/published | 소유자 확정 후 | 저빈도 참조 |
| `category`·`company` | (나) lookup — projection/published | 소유자 확정 후 | 저빈도 참조 |

- **ES 컨텍스트는 최소 1개 현재상태 프로젝션 필수**(이벤트 스트림은 임의 조회 불가)
- **비-ES 컨텍스트는 기존 QueryDSL 직접 조회 유지**([[DESIGN-004]] §4.2 다) — 이벤트 없는데 projection 얹는 건 과투자
- **cross-context 동기 조회 금지**([[DESIGN-004]] §4.2) — 조회 시점에 command/타 컨텍스트 원본을 동기 호출하면 CQRS를 깸. 남이 흘리는 걸 비동기로 받아 로컬 테이블 갱신만

---

## 7. 일관성 · 읽기 신선도 ([[DESIGN-004]] §4.7 · [[RFC-030]])

- **기본 = 최종 일관성**. "쓰고 바로 읽으면 아직 없을 수 있다"를 **버그가 아니라 기본 사양**으로 못 박는다. projection은 이벤트 구독으로 갱신되어 본질적으로 뒤처짐
- **read-your-writes는 `sequenceNo` 토큰으로 구현한다**([[RFC-030]] 🏷 합의 2026-07-05, 결정 4 — **확정**). 클라가 command 응답(권위 바디)에서 받은 `sequenceNo`를 조회 시 함께 실으면, §5.0.1 `ReadFreshnessGate`가 `seq ≥ N` 반영 여부를 확인해 짧게 대기(long-poll) 후 폴백한다. 동기 프로젝션·command 직접 읽기 같은 더 무거운 대안은 채택하지 않는다.
- 프로젝션 지연 p99 목표 + 초과 알람(골격만, 절대값은 lag 측정 후 — [[DESIGN-008]] §4.7)

**확정**([[RFC-030]] 결정 4): "예약 확정 직후 방금 예약한 걸 바로 못 본다"는 예약 시스템의 핵심 플로우이므로, 신선도 토큰 구현을 이 읽기 서버의 §5 read path·§5.0.1 게이트로 직접 반영했다 — R-1(§11)로 자기 밖에 미루지 않는다.

## 8. 인가 · 보안 ([[DESIGN-014]] · [[DESIGN-010]] §4.2)

- JwtFilter **없음**. 엣지(API Gateway)가 JWT 검증 후 `X-User-Id`/`X-User-Role` 클레임 헤더 전달 → pre-authenticated 필터로 수신
- 조회 스코프 조건: "내 예약만" 같은 행 수준 필터를 서비스/쿼리에서 적용(subject 스코프)
- 발급/검증 인프라는 [[09-auth-server-module]]

## 9. 확장 · 배포 ([[DESIGN-010]])

- **stateless** — query DB가 상태. N replica로 수평 확장
- 읽기 확장 = query MySQL **HA 레플리카**로 분산(인스턴스 분할 아님, 캐시 아님)
- projector(07)와 **별 Deployment** — 조회/투영 장애 격리

## 10. 할 일

- [ ] Read Model JPA 엔티티 + QueryDSL Repository (도메인별 스키마, `appliedSequenceNo` 컬럼 포함)
- [ ] `ReadFreshnessGate` — `sequenceNo` 비교 + bounded long-poll + 폴백([[RFC-030]] §5.0.1)
- [ ] Query Service (읽기 전용 txn) + 응답 DTO 매핑
- [ ] Query REST Controller (레퍼런스: reservation/timetable/restaurant)
- [ ] pre-authenticated Security + 스코프 조건
- [ ] 비-ES 컨텍스트: V1 QueryDSL 조회 코드 이전
- [ ] QueryDSL Q타입 생성(kapt) 설정
- [ ] HA 레플리카 라우팅 설정(선택)
- [ ] REST Docs → OpenAPI
- [ ] 조회 슬라이스 테스트 (Testcontainers MySQL)

## 11. 미결 요약

| # | 항목 | 귀속 |
|---|------|------|
| R-1 | ~~read-your-writes 예외 정책~~ — **확정**: `sequenceNo` 토큰 + `ReadFreshnessGate`(§5.0.1·§7) | [[RFC-030]] (합의 2026-07-05) |
| R-2 | `schedule` projection vs 경량 lookup | 변화 빈도 측정 |
| R-3 | menu·category·company projection/published 귀속 | 소유권 확정(구현 사이클) |
| R-4 | 비-ES가 ES 데이터 조인 필요 시(예약 상세의 메뉴) | 첫 레퍼런스에서 결정([[DESIGN-004]] §4.2) |
| M-6 | Read DB 물리 분리 시점 | [[ADR-004]] |

## 12. 악마의 변호인 (Devil's Advocate)

> 이 문서 설계에 대한 가장 강한 반론 (구현 전 스트레스 테스트용).

**Position**: 읽기 경로는 "DB→DTO"라 layered로 충분하고, 최종 일관성을 기본 사양으로, read-your-writes·캐시는 예외로만 연다.
**Steel-man**: 읽기를 포트/어댑터·캐시·동기결합으로 무겁게 만들지 않고, 프로젝션이라는 이미 있는 머티리얼라이즈드 캐시를 얇게 서빙하면 조회/투영 장애를 격리하면서 운영 면적이 최소가 된다.

### 숨은 가정
1. read-your-writes를 "예외"로 미뤄두면 읽기 서버 구조(§5 read path)는 단순 최종 일관성 조회로 충분하다 — 신선도 토큰은 나중에 얹을 수 있는 부가물이다.
2. 프로젝션 read model이 "머티리얼라이즈드 캐시"이므로 그 앞의 in-memory 캐시는 불필요하다 — DB 버퍼풀/디스크/연결 지연을 in-memory 접근과 동급으로 본다.
3. 읽기 확장은 HA 레플리카 분산으로 풀린다 — tail latency와 처리량이 같은 축이라고 본다.

### 반론

**반론 1 — 이 문서가 자기 책임인 신선도 토큰 계약을 R-1로 미뤄, 이미 합의된 RFC-030을 읽기 서버가 반영 못 한다** `[일관성/설계누락]` · 심각도: 높음 — **해소됨(2026-07-19 동기화)**
이 반론은 §5 read path에 `sequenceNo` 파라미터·projection row의 원본 seq 컬럼·long-poll 로직이 전혀 없을 때 성립했다. §5·§5.0.1을 갱신해 `sequenceNo` 파라미터, `ReadFreshnessGate`, [[07-query-projection-server]] §5.2의 `appliedSequenceNo` 컬럼을 read path에 실제로 연결했다 — RFC-030 결정 4가 이제 서버 구조에 반영됐다.

**반론 2 — Redis 금지 + "핫 쿼리엔 전용 프로젝션 추가"가 tail latency 비용을 프로젝션 조합 폭발로 치환** `[성능]` · 심각도: 중간
§45/§9는 캐시 대신 "전용 프로젝션 추가 + HA 레플리카"로 핫 쿼리를 흡수한다고 한다. 그러나 "오늘 X식당 예약 목록"처럼 (식당 × 날짜 × 필터) 파라미터가 많은 핫 조회는 미리 만들 프로젝션 조합이 폭발한다 — 프로젝션은 특정 조회 모양을 물질화하는 것이지 임의 파라미터 질의를 상수시간으로 만들지 못한다. 결국 QueryDSL이 레플리카 MySQL을 직격하고, 캐시 완충이 없으니 콜드 버퍼풀·offset 페이징·count 분리 쿼리(§5.1)의 p99가 그대로 사용자에게 노출된다. HA 레플리카는 처리량(동시성)을 나눌 뿐 단건 tail latency를 줄이지 못한다. 선례: DESIGN-004 자기리뷰 §187이 동일 지적("프로젝션 N개의 운영·정합성 부담 N배") — 문서 내부에서 이미 제기된 미해소 반론.

**반론 3 — 도메인별 스키마 read model 증식이 한 인스턴스 안에서 관리비·프로젝션 쓰기 병목으로 누적** `[운영/복잡도]` · 심각도: 중간
§4/§68은 read model을 한 query 인스턴스 안에 도메인별 스키마로 몰아 담는다. read model은 화면 용도마다 여럿 생기므로(§68 자인) 각각이 백필·재구축·스키마 진화·정합성 검증을 독립 자산으로 지고 온다. 더 근본적으로, 읽기는 레플리카로 나눠도 **프로젝션 쓰기(07의 이벤트 소비→갱신)는 프라이머리 하나에 집중**된다 — 스키마를 한 인스턴스에 몰아넣은 구조에서 핫 스트림 프로젝션 lag이 커지면 §7의 p99 목표를 레플리카 증설로는 못 지킨다. 선례: DESIGN-004 자기리뷰 §188(쓰기 병목이 HA 레플리카로 안 가려짐) — no clear precedent가 아니라 문서군이 이미 인지한 구조적 제약.

### 다중 페르소나

**고객/사용자**: 나는 방금 예약을 확정했다. 확인 화면까지는 "○○식당 예약 완료"가 보인다. 앱을 껐다 켜거나 새로고침해서 "내 예약" 목록을 다시 열 때 — 콜드 조회다 — 클라가 command 응답의 `sequenceNo`를 목록 조회 요청에 함께 실으면(§5·§5.0.1), `ReadFreshnessGate`가 projection이 그 seq까지 따라잡을 때까지 짧게 대기한 뒤 보여준다. **(이 시나리오는 반론 1 해소로 닫혔다 — 남은 리스크는 아래 On-call 관점의 게이트 자체 비용이다.)**

**On-call/SRE**: 새벽에 프로젝션 lag 알람이 뜬다(§7은 p99 골격만, 절대값은 lag 측정 후 — 즉 지금은 임계값 자체가 없다). 하필 이 순간이 가장 위험하다 — `ReadFreshnessGate`가 이제 실제로 존재하므로(§5.0.1), lag이 커진 바로 그 시점에 읽기 요청들이 `seq ≥ N`을 기다리며 readOnly 트랜잭션/연결을 붙든 채 쌓여 read 서버 스레드가 고갈될 수 있다 — 투영이 밀릴 때 조회까지 같이 죽어 "장애 격리"(§7·§9) 주장이 깨질 위험은 **여전히 미해소**다(게이트의 bounded wait 상한·타임아웃 정책이 아직 수치화되지 않음). 게다가 캐시 완충이 없어(§45) 조회 트래픽 폭증은 레플리카를 곧장 때리고, 내가 급히 쓸 수 있는 손잡이가 "프로젝션 추가"(코드·배포 필요)뿐이라 즉시 완화 수단이 없다.

### 핵심 취약점
읽기 서버의 신선도 토큰 계약(반론 1)은 §5·§5.0.1로 반영해 해소했다. **남은 핵심 취약점은 `ReadFreshnessGate`의 bounded wait가 lag 급증 시 읽기 스레드/커넥션 풀을 고갈시킬 위험**(On-call 페르소나) — 대기 상한·타임아웃·폴백 임계값이 아직 수치로 정해지지 않았다.

### 가역성
대체로 reversible(read path·스키마 배치는 코드) — 단 신선도 토큰(long-poll·`seq` 비교)을 뒤늦게 끼우려면 readOnly 트랜잭션 경계·스레드/연결 모델·projection row 스키마(원본 seq 컬럼)를 함께 되짚어야 해 **준-one-way**. 처음부터 read path 시그니처에 토큰 자리를 열어두는 편이 싸다.
