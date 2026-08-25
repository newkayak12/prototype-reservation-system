
## DesignDoc 재료 인덱스 (RFC → 설계 매핑)

> confirm된 RFC들의 결정을 **어느 design_doc에 써넣을지**로 정렬한 재료 맵. 한 RFC가 여러 design_doc에 갈리고, 한 design_doc은 여러 RFC가 먹인다.
> 범위: RFC-011·012·013은 **하류 산출물 없음**(구현/클린슬레이트라 design_doc 재료 없음). 불채택·superseded·이력 항목은 재료에서 뺐다(RFC-021 `global_seq`, RFC-014 낙관 락 원안 등).

### [[DESIGN-001]] ← RFC-001
- **RFC-001**: 목표 아키텍처 = CQRS 모듈 분리 + 선택적 ES. 전체 설계 문서의 진입·요약점.

### [[DESIGN-002]] ← RFC-001 · RFC-010 · RFC-002
- **RFC-001**: command/query를 top-level Gradle 모듈로 분리, 도메인은 각 모듈 내 패키지.
- **RFC-010**: 순수 도메인 = 별도 `core-module` 현행 유지(통합 비채택); 도메인↔JPA = 엄격 hexagonal 수동 매핑(코드젠·공통 매퍼 비채택).
- **RFC-002**: query layered = projection이 갱신 / service가 조회로 책임 분리.
- ADR: [[01.cqrs-command-query-module-split]] · [[07.command-domain-jpa-separation]] · [[03.command-hexagonal-query-layered]]

### [[DESIGN-003]] ← RFC-001 · RFC-021 · RFC-004 · RFC-003 · RFC-014 · RFC-008 · RFC-015 · RFC-019
- **RFC-001**: command 측 = hexagonal; 진짜 ES는 reservation·timetable·restaurant만.
- **RFC-021**: `event_id` = 전 컨텍스트 공통 1급 정체성, 채번=append/Outbox 기록 TX 시점·전역 유일, inbox dedup 키 = `event_id`, 봉투에 추적메타 전파. 파티션 키 = `aggregate_id` 불변.
- **RFC-004**: 스냅샷 주기 = 이벤트 카운트 트리거(기본 N=100·타입별 오버라이드); 직렬화 규약(MySQL JSON·모르는 필드 무시·enum=이름·시간 ISO-8601 UTC·금액 정수 최소단위·null 명시).
- **RFC-003**: 통합 이벤트 / 내부 도메인 이벤트 분리 경계(부수).
- **RFC-014**: 동시성 충돌 ≠ 요청 멱등 ≠ 전달 멱등 — 세 층 경계 선언.
- **RFC-008**: `AbstractEvent` 추적 메타 봉투 필드 정의(correlationId 필수·causationId·traceparent).
- **RFC-015**: 인가용 스코프 키는 도메인 이벤트로 갱신(소유권 변경 전파).
- **RFC-019**: `current_refresh_jti`는 authenticate 상태(write-model) 컬럼.

### [[DESIGN-004]] ← RFC-001 · RFC-002 · RFC-018 · RFC-015
- **RFC-001**: query 측 = layered; 읽기 = 이벤트 프로젝션 read model, 저빈도는 경량 lookup, replica는 HA 전용.
- **RFC-002**: lookup 실현 = projection ∨ pub-sub 둘뿐(둘 다 async 로컬 카피·동기 cross-context fetch 금지·seed는 전략 아님); projection은 읽기 요구 입증된 곳부터(YAGNI); 비-ES 컨텍스트는 QueryDSL 유지.
- **RFC-018**: 읽기 캐시 기본 안 함(프로젝션이 이미 영속 캐시); 핫 쿼리 = 화면 전용 프로젝션 추가로 대응.
- **RFC-015**: 인가용 스코프 키를 프로젝션 1급 컬럼으로, 프로젝터가 강제 채움.
- ADR: [[04.read-model-projection-and-replica]] · [[03.command-hexagonal-query-layered]] · 신규(읽기 신선도 예외 정책, 필요 시)

### [[DESIGN-005]] ← RFC-001 · RFC-010
- **RFC-001**: Strangler 점진 전환, timetable 선행을 템플릿으로.
- **RFC-010**: 전환 순서 = 의존성(잎부터) + 위험·학습 가치; 분할 단위 = 컨텍스트 1 = 사이클 1(첫 사이클 무겁게·이후 복제); 신규 기능 = 별도 command 컨텍스트(신구조 네이티브).
- ADR: [[06.strangler-migration]]

### [[DESIGN-006]] ← RFC-014
- **RFC-014**: 비관 락 채택(낙관 원안 supersede) — 핫 스트림 retry storm을 질서 있는 큐로. L0 안전 = `(aggregate_id, sequence_no)` UNIQUE(정확성 최종 심판); L1 = Redisson 분산 락, L1′ = Redis 다운 시 DB 비관 락 폴백; 충돌 = 도메인 판단/409; 락 재시도(재로드→재판단)가 요청 멱등 흡수; 핫 애그리거트 직렬화 수용; 교차 불변식 = 사가, 전역 락 금지·락 범위 = 단일 `aggregate_id`.
- ADR: [[16.optimistic-concurrency-control]] (비관 락으로 재작성)

### [[DESIGN-007]] ← RFC-006 · RFC-021 · RFC-014 · RFC-002 · RFC-016
- **RFC-006**: 코레오그래피 기본(PM 인프라 불필요); 타임아웃 = reservation 소유(2026-07-31 개정, 구 timetable TTL 자치); 보상 = 각 컨텍스트 자기 책임(멱등·append-only); 실패 = V1 PoisonMessage 계승; 취소·노쇼·환불 전부 코레오그래피.
- **RFC-021**: 교차-애그리거트 전순서는 정확성 요구 아님; point-in-time 교차 사실은 생산 시점 페이로드 박제; 진짜 교차 불변식은 사가.
- **RFC-014**: 교차 불변식은 락이 아니라 사가가 흡수(락↔사가 경계만 추가, 사가 본체는 RFC-006 소유).
- **RFC-002**: read-your-writes = 기본 최종 일관, 예외는 증명된 화면만.
- **RFC-016**: 사가 표면 = `PaymentConfirmed/Failed/Refunded` 3 이벤트 동결.
- ADR: [[08.saga-orchestration-vs-choreography]]

### [[DESIGN-008]] ← RFC-001 · RFC-003 · RFC-023 · RFC-002
- **RFC-001**: 비-ES 컨텍스트도 Outbox→Kafka로 통합 이벤트 발행.
- **RFC-003**: 통합 이벤트 = published language(내부 도메인 이벤트와 분리); 실패 루프 = 재시도→백오프→DLQ→Slack→수동 재생; 프로젝터별 독립 컨슈머 그룹 fan-out·cooperative-sticky; 파티션 수 고정 지향(증설=새 토픽 마이그레이션).
- **RFC-023**: 얇은 통합 이벤트 공유 계약 모듈(컴파일 보장, 생산자·소비자 공동 의존); 스키마 레지스트리·Avro/SCC/Pact 보류(멀티팀·외부 소비자 실증 시 졸업).
- **RFC-002**: 프로젝션 지연 = 측정 트리거 정책(p99 목표+알람, 절대값은 운영 튜닝).
- ADR: [[09.event-ordering-and-delivery-guarantee]] · [[05.event-store-mysql-table]](폴링→CDC 트리거) · [[12.kafka-hosting-msk-vs-self-managed]] · 신규(계약 안전망)

### [[DESIGN-009]] ← RFC-001 · RFC-004 · RFC-017 · RFC-021 · RFC-022 *(최대 합류점)*
- **RFC-001**: 이벤트 스토어 = MySQL 이벤트 테이블 직접 구현.
- **RFC-004**: 스냅샷 = 1핫 + S3/Glacier 콜드, 2-슬롯 토글 원자 교체; 정합성 = 인라인 1회 + 배경 표본 2겹(불일치 시 폐기→리플레이); 핫/콜드 경계 = 종결 상태 + 유예; 콜드 = S3 export→drop / load→replay; 시점 질의 = 운영·디버깅 한정(YAGNI); 스냅샷 포맷 변경 = 폐기 후 리플레이 재생성; 시간(생성월) 파티셔닝.
- **RFC-017**: 백업 1급 = 이벤트 스토어 단일(파생물은 best-effort); 복원 의미론(물리 장애 PITR·논리 사고 보상 이벤트·절단 PITR은 미발행 꼬리만); 셰딩 정합(암호문/키 분리 백업·키 무효화·이벤트 시계만 되감음); 복구 순서(진실 원천 먼저→재구축→readiness).
- **RFC-021**: `event_id`(UUIDv7) 시간정렬 단조를 재구축/백필 keyset 커서로(`WHERE event_id > :last ORDER BY event_id`); 재구축 완전성 = (백필 ≤ HWM) ∪ 라이브 tail + 멱등 가드·"구독 먼저"; 단일 as-of = `occurred_at` + `sequence_no` tiebreak.
- **RFC-022**: 업캐스터 = 명시 등록 빈(시작 시 `(eventType, fromVersion)` 충돌 빠른 실패); eventType↔클래스 = 명시 등록 매핑(클래스명 변경과 분리); 직렬화 = JSON + 업캐스팅 유지.
- ADR: [[05.event-store-mysql-table]] · [[10.event-schema-evolution]] · [[18.event-store-recovery-semantics]] · [[22.event-identity-and-global-ordering]]

### [[DESIGN-010]] ← RFC-007 · RFC-020 · RFC-008 · RFC-018 · RFC-003
- **RFC-007**: 단일 평탄 namespace 기본(분리는 트리거 시); command/query 초기 단일 배포·신호 임계 시 물리 분리; virtual thread 레버(off)·코루틴 비채택; 데이터 = 각 1 + HA 레플리카·standby 1; Strimzi KRaft·k3s~EKS 속성 패리티; readiness probe 게이팅 와이어링.
- **RFC-020**: 인증 경계 = 모델 A(엣지 1회 검증 + 헤더 전파, 앱은 다시 풀지 않음) — 의무 = 인입 신원 헤더 strip + NetworkPolicy "게이트웨이만 앱 도달"(안 지키면 헤더 위조로 뚫림); 모델 B(서비스마다 JWKS 재검증)는 서비스 메시(Istio) 도입 시 전환 경로. 엣지 검증 = 기성 프록시 무상태 JWT 검증(②, 게이트웨이 앱 미구현)·도메인 앱은 pre-auth 필터만(V1 `JwtFilter`·JWKS·서명검증 제거)·ext_authz(③)/SCG(①)는 per-user 세밀 rate limit 실증 시. 구체 프록시 제품(Envoy Gateway vs nginx ingress)·배치·인증 서버 워크로드 토폴로지가 여기 거처.
- **RFC-008**: (공유) 코루틴 기각·블로킹 MVC 유지, IO 확장은 virtual thread 레버.
- **RFC-018**: 읽기 확장 = query HA 레플리카(Redis 캐시 아님).
- **RFC-003**: Outbox relay 단일성 = `SELECT … FOR UPDATE SKIP LOCKED`(leader election 미도입).
- ADR: [[12.kafka-hosting-msk-vs-self-managed]] · [[13.db-hosting-and-read-write-topology]] · 신규(인증 경계)

### [[DESIGN-011]] ← RFC-008 · RFC-021
- **RFC-008**: 추적 전파 출처 = OTel Context·MDC는 투영; correlationId를 모든 root span 필수 attribute로 격상; 재처리 시 correlationId 유지 + causationId 체인; `AbstractEvent` 추적 메타 공통 발행 경로 자동 충전; 구조화 로깅 = AOP 스코프 키 + OTel Baggage; 메트릭 이름·라벨·단위 카탈로그·SLI 층 분리.
- **RFC-021**: 봉투 추적 메타 — `event_id`가 causationId/correlationId 앵커·Kafka messageId 통일.
- ADR: 없음(전부 design_doc 보강으로 닫힘)

### [[DESIGN-012]] ← RFC-009 · RFC-023 · RFC-004 · RFC-005
- **RFC-009**: 아키텍처 강제 = Konsist(ArchUnit 비채택); 행위 명세 = Kotest BehaviorSpec 3슬라이스; 동적 분산 6범주 메커니즘별; 인가 = controller(standalone) 시나리오; 카오스 = Chaos Monkey + Chaos Mesh; 게이트 정책(CI 필수 vs 정기·ratchet); localstack 살아있는 목록.
- **RFC-023**: 직렬화 골든 테스트(wire JSON 핀 박기)·additive-only 규율 → CI 게이트.
- **RFC-004**: 절대값(스냅샷 N·검증 표본률·파티셔닝 전제)은 k6 스윕·운영 측정 위임.
- **RFC-005**: 셰딩 누락 = ArchUnit/Konsist 빌드 타임 규칙.
- ADR: [[14.testing-strategy]]

### [[DESIGN-013]] — *재료 없음*
- **RFC-012** ✅ 종결: 하류 산출물 없음 — 구현 시점에 결정.

### [[DESIGN-014]] ← RFC-015
- **RFC-015**: 인가 이분 = 역할 기반(엣지) / 소유권·스코프 기반(앱); command 소유권 = 애그리거트 불변식·검증된 클레임만(자칭 신원 불신); query 소유권 = 쿼리 시점 스코프 조건(사후 필터 아님); 프로젝션 스코프 키 1급 컬럼; 역할 클레임 횡단 전파·인가용 컨텍스트 간 런타임 조회 금지.
- ADR: [[17.authorization-model]] (+ 신규 예정)

### [[DESIGN-015]] ← RFC-016
- **RFC-016**: payment = ACL(PG↔도메인 번역만)·상태+Outbox; 인바운드 = 웹훅+verify가 진실, 입구 3겹(서명·멱등 디듀프·verify); 아웃바운드 = 의도 기록→멱등키 릴레이→결과 이벤트(effectively-once); 환불 = 정방향 경로 + 이중 환불 가드(보상은 새 정방향); 정합성 = 주기 단방향 대사·보정 큐; 학습 = PG 포트 정의 + 결함 주입 스텁.
- ADR: [[15.payment-acl-boundary]]

### [[DESIGN-016]] ← RFC-005
- **RFC-005**: 셰딩 키 = 주체당 1행 전용 키 테이블(동적 생성·즉시 하드 삭제·이벤트와 분리); 모든 필드 PII 여부 명시 선언 강제(분류표는 보류); 셰딩 누락 = 빌드 타임 규칙; 키 로테이션·백업·접근통제 = 방향만(수치 별도 ADR); 셰딩 표현 = 익명 토큰·복호 실패 정상 경로; 암호화 PII는 쿼리 키 아님(좁은 동등 검색만 blind index); 콜드 = 암호문만·키 핫 잔류·1회 삭제로 동시 셰딩.
- ADR: [[11.es-pii-crypto-shredding]] (Proposed→Accepted 비준 대기)

### [[DESIGN-017]] ← RFC-019 · RFC-018
- **RFC-019**: refresh = 무상태 서명 JWT(Redis 사본 제거); transport = refresh HttpOnly 쿠키(SameSite Lax)/access는 Authorization 헤더(V1 계승 + SameSite·path 보완); rotation; 재사용 탐지 + 강제 로그아웃(`current_refresh_jti` 대조); 즉시 폐기(denylist) 기본 포기.
- **RFC-018**: 세션 상태 무상태 제거 — 인증 부산물(refresh 저장·denylist)은 V2 기본에서 Redis에 남지 않음.
- ADR: [[20.auth-token-transport]]

### [[DESIGN-018]] ← RFC-018 · RFC-014
- **RFC-018**: Redis 역할 = 분산 조정·휘발성 상태(요청-단 멱등 디듀프·레이트리밋 카운터·일시적 분산 락)·읽기 가속 캐시 아님; 등급 1 단일 durability → 단일 eviction 정책·단일 인스턴스(분리는 추후).
- **RFC-014**: L1 Redisson 분산 락 키 포맷·리스 TTL·watchdog·Redis up/down 폴백 전환(락 의미론 본체는 [[DESIGN-006]] 소유).
- ADR: [[19.caching-redis-role]]

---
