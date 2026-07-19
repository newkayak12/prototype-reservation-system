# DESIGN Weakness 트리아지 (V2)

> 생성 2026-07-02 · 워크플로우 자동 생성 산출물 · **결정 권한은 사용자에게 있음 — 아래 처분안(disposition)은 제안일 뿐**
> 파이프라인: 18개 DESIGN 문서 116개 Weakness → 51개 교차문서 클러스터 → 각 클러스터를 ADR/RFC와 대조 판정 → 처분안 제안
> ⚙️ = verdict 기반 자동도출 처분안(에이전트 미완분, **검토 필요**). 나머지는 에이전트 제안.

## 요약

- **판정 분포**: open 9 · partially-decided 38 · already-decided 4
- **처분안 분포**: accept-new-rfc 27 · mitigate-note 23 · reject 1 · defer 0
- 클러스터 51개 · 자동도출 처분 38건

## 랭크 테이블

| #   | 클러스터                                                                 | 문서                      | 심각도  | 판정                | 처분안               | 우선   |
|-----|----------------------------------------------------------------------|-------------------------|------|-------------------|-------------------|------|
| C03 | core↔contract 의존 금지가 도메인 이벤트 반환과 리플레이 apply 오케스트레이션과 충돌              | D-002                   | HIGH | open              | accept-new-rfc    | HIGH |
| C09 | SKIP LOCKED와 DLQ 수동 재생이 애그리거트별 발행·소비 순서 계약을 파괴한다                     | D-008                   | HIGH | open              | accept-new-rfc    | HIGH |
| C16 | 마이그레이션의 genesis 시딩·단방향 동기·롤백 접합점 미정의로 이중반영·데이터 유실                    | D-005                   | HIGH | open              | accept-new-rfc ⚙️ | HIGH |
| C34 | 결제 사가 표면 동결이 부분환불·분쟁·재시도 소진을 표현 못 하고 확정 경로가 얕다                       | D-007,D-015             | HIGH | open              | accept-new-rfc ⚙️ | HIGH |
| C35 | 멱등키·의도-먼저 기록의 문서 내 모순과 verify 경로의 dual-write·열거 공격면                  | D-015                   | HIGH | open              | accept-new-rfc ⚙️ | HIGH |
| C40 | 장애 폴백 미정 상태로 단일 인스턴스만 먼저 확정한 순서 역전                                   | D-018                   | HIGH | open              | accept-new-rfc ⚙️ | HIGH |
| ~~C45~~ | ✅ 종결(OVER 2026-07-08) leaf-first 준수→모순 소멸 + V1→V2 비실시간 컷오버→식당명 갭·브리지 불요 | D-005 | HIGH | closed | over ✅ | — |
| C02 | Zero Payload 재처리가 미래 상태로 과거 이벤트를 오염시키는 time-travel 결함                | D-004                   | HIGH | partially-decided | accept-new-rfc    | HIGH |
| C10 | 주인 없는 코레오그래피가 사가 상태의 단일 조회지점·정적 전역 불변식 검증을 없앤다                       | D-007,D-011             | HIGH | partially-decided | accept-new-rfc    | HIGH |
| C11 | 코레오그래피 채택 근거가 '선형 2~3스텝'에 걸려 있고 전환 트리거·부분보상 복구가 미결인 채 Accepted       | D-007                   | HIGH | partially-decided | accept-new-rfc    | HIGH |
| C13 | 이미 나간 사가 부수효과·결제 실패는 보상·상태 가드로 되돌릴 수 없다                              | D-006,D-007             | HIGH | partially-decided | accept-new-rfc    | HIGH |
| C24 | 인가 클레임을 이벤트 봉투에 실어 전파하면 봉투가 위변조·재생 공격면이 된다                           | D-014                   | MED  | open              | accept-new-rfc ⚙️ | MED  |
| C33 | 추적 3필드 의무화 저장·전송 비용과 SLI 카탈로그 이중 계측·AOP 스코프 경계 미정의                   | D-011                   | MED  | open              | accept-new-rfc ⚙️ | MED  |
| C01 | Zero Payload가 command DB 역참조를 강제해 CQRS 유일접점 불변식을 무너뜨린다               | D-001,D-003,D-008       | HIGH | partially-decided | accept-new-rfc    | MED  |
| C08 | 다중 소스·교차 스트림 프로젝션의 순서·원자성이 부분 갱신을 정상 동작으로 만든다                        | D-001,D-004             | HIGH | partially-decided | accept-new-rfc    | MED  |
| C14 | read-your-writes를 Non-Goal로 미뤘으나 핵심 여정(202 취소·예약 직후 조회)이 이미 예외 후보    | D-004,D-013             | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C15 | 9개 컨텍스트 수동 도메인↔JPA 매핑의 반복 비용과 데이터 정합성 결함 위험 과소평가                     | D-002,D-005             | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C17 | event_store 무한 성장·스냅샷 재생성과 콜드 파티션 이관·업캐스팅 누적 비용의 충돌                  | D-003,D-009             | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C18 | 스냅샷 N값·reconciliation·업캐스팅 전략이 예약 도메인 실제 스트림 특성과 자기모순                | D-009                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C19 | 크립토 셰딩의 물리 잔존·백업 전파·at-rest 평문·DR 복구 완결성이 보장되지 않는다                   | D-009,D-016             | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C20 | 콜드 이벤트의 핫 키 저장소 종속이 재해복구·키 수명주기·감사 요구와 충돌                            | D-009,D-016             | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C21 | YAGNI 비대칭·학습가치 정당화·트래픽 발생 시 재검토 경로 폐쇄                                | D-001,D-003,D-006       | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C22 | query 측 수평 권한상승·역할 게이트 단일지점 방어가 개발자 규율·엣지 단일점에만 의존                   | D-014                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C23 | 소유권 판단의 결과적 일관성 창이 조회 유출과 command 인가 우회를 동시에 열고, 도메인에 역할 어휘가 샌다      | D-014                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C25 | 무상태 refresh·재사용 탐지·단일 refresh 컬럼·denylist 포기가 세션 보안 기본기를 훼손          | D-017                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C26 | CSRF 방어를 SameSite Lax+body 비대칭에만 걸고 in-memory access 강제 UX·부하 비용 미계량 | D-017                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C27 | 솔로 운영자의 self-managed 인프라 운영 표면과 진실원천 HA·relay failover 미스매치          | D-010                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C28 | 파티션 수를 계약으로 못박고 HPA 상한=파티션 수로 두어 초기 추정 실패의 벌칙을 극대화                   | D-008,D-010             | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C29 | command/query 배포 합침이 독립 스케일·단일 이미지 격리 주장과 충돌                         | D-010,D-012             | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C30 | 검증 환경이 게이트로 거는 다중브로커·HA 특유 위반을 재현할 수 없는 구성 모순                        | D-012                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C31 | 무거운 E2E 게이트·비-차단 관측·절대 SLO 부재로 게이트가 실효를 잃고 완만한 회귀가 누적                | D-012                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C32 | 관측성 규약이 Context 전파 메커니즘·traceparent 위치·백엔드 배선을 전부 미결로 두어 종이 위에서만 성립  | D-011                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C36 | 클라이언트 Idempotency-Key 거부·422/409 경계·cursor 안정성 전제가 재시도·경합에서 취약       | D-013                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C37 | 생성 도메인 유니크와 동시성 UNIQUE가 물리적으로 같은 제약이면 재시도 storm을 분리 못 한다             | D-006                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C38 | 핫 슬롯 직렬화 구간이 사가 다단계 트랜잭션 길이가 되고 granularity 완화는 순환에 빠진다              | D-006                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C39 | 단일 Redis durability 등급 전제가 멱등 디듀프 must-not-evict 성격 및 미확정 상위 결정과 모순  | D-018                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C41 | read model 캐시 거부→전용 프로젝션 방침의 영구 운영·조합 폭증 비용이 저울질되지 않았다               | D-004,D-018             | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C42 | 컨텍스트 경계가 잠정값인데 하위 설계가 이미 결합됐고 수직 분산이 미래 분할을 막는다                      | D-001,D-002             | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C43 | 컴파일 의존성 우위 원칙이 정작 컨텍스트 간 격리에는 적용되지 않는 자기모순                           | D-002                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C44 | 신규 기능 게이팅이 사업 우선순위와 교착하며 실행 가능한 답이 없다                                | D-002,D-005             | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C46 | inbox 생략 자격·effectively-once가 검증 불가 전역 가정과 부분 보장에 기댄다                | D-008                   | HIGH | partially-decided | accept-new-rfc ⚙️ | MED  |
| C47 | CDC 전환 트리거가 전부 주관적이라 폴링 부채를 발화 못 시킨다                                 | D-008                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C49 | ES/비-ES 추상화 누수와 hexagonal/layered 비대칭이 read side 실제 복잡도를 과소평가        | D-001                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C50 | 비-ES가 ES 데이터를 조인하는 문제가 첫 레퍼런스(예약 상세)에서 바로 터진다                        | D-004                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C51 | 깨진 크로스레퍼런스가 CI 필수 게이트 강제력을 미확정 상류에 인질로 잡는다                           | D-012                   | MED  | partially-decided | mitigate-note ⚙️  | MED  |
| C04 | L1 분산 락이 append와 원자적이지 않아 정확성은 사실상 L0 UNIQUE에만 의존한다                  | D-003,D-006,D-013,D-018 | HIGH | already-decided   | mitigate-note     | MED  |
| C06 | event_store와 Outbox의 트랜잭션 경계 공유 조건이 명시되지 않아 '원자적' 주장이 취약             | D-003                   | HIGH | already-decided   | mitigate-note     | MED  |
| C12 | 혼합 패러다임(ES/비-ES) 사가와 보상 인터리빙이 원자성·순서를 훨씬 어렵게 하나 표시되지 않는다             | D-003,D-007             | MED  | partially-decided | mitigate-note     | LOW  |
| C48 | core에서 Spring 배제가 흔한 도메인 헬퍼(Bean Validation·로깅)까지 배제한다               | D-002                   | LOW  | partially-decided | mitigate-note ⚙️  | LOW  |
| C05 | 분산 락의 안전성·토폴로지 가정(Redlock·단일 인스턴스 SPOF·상시 왕복 비용)이 검증되지 않았다           | D-006,D-018             | HIGH | already-decided   | reject            | LOW  |
| C07 | UUIDv7 커서가 '순서 정확성 아님'인데 전역 재구축이 이 부정확성에 노출된다                        | D-003                   | LOW  | already-decided   | mitigate-note     | LOW  |

## 클러스터 상세

### C03 — core↔contract 의존 금지가 도메인 이벤트 반환과 리플레이 apply 오케스트레이션과 충돌

`HIGH` · 판정 **open** (conf high) · 처분안 **accept-new-rfc** · 우선 HIGH

- **문서/항목**: D-002 §4.2, D-002 §4.4
- **설명**: 애그리거트가 반환하는 도메인 이벤트가 contract 타입이면 core→contract 매트릭스 위반이고, core 타입이면 application 매핑 계층이 필요한데 그 흔적이 없다. 또 ES
  엔진이 사는 infrastructure는 command-core를 import 못 해 리플레이의 apply(event) 재구성 오케스트레이션이 어댑터로 새고 ES 엔진은 raw I/O 반쪽이 된다. 계층 규칙과
  이벤트 소싱 실행 모델 사이의 분업선이 그려지지 않았다.
- **판정 근거**: C03의 두 갈래 모두 결정 문서로 닫히지 않았다. (1) 애그리거트 반환 이벤트가 core 타입이냐 contract 타입이냐, 그리고 core 도메인 이벤트→contract 통합 이벤트를
  누가/어디서 매핑하느냐 — DESIGN-008 §4.12·DESIGN-012·RFC-023이 "내부 도메인 이벤트 ≠ 통합 이벤트(contract)"라는 두 범주의 구분은 확정했지만, RFC-023은 오히려 "
  내부 도메인 이벤트와 contract의 경계"를 Design 후속으로 명시적 유보했고, 그 매핑 계층(어느 모듈이 번역하는가)을 짚은 문서가 없다. RFC-010·ADR-07은 도메인↔JPA 수동 매핑만 확정하고
  도메인이벤트↔contract 매핑은 다루지 않으며, ADR-15/RFC-016의 ACL 번역은 외부 PG↔도메인 경계 전용이지 내부 도메인→contract 경계가 아니다. (2) ES 엔진이
  command-infrastructure에 거주하고 이 모듈은 command-core import 금지(DESIGN-002 §4.4 매트릭스)인데, 리플레이는 core 소유의 apply(event)로 상태를
  재구성한다(DESIGN-009 §3의 events.fold{state,e->state.apply(e)}). 재구성 오케스트레이션이 core를 못 부르는 infrastructure에서 어디로 가는지(어댑터로
  새는지) 어느 문서도 분업선을 긋지 않았다. 결정적으로 이 두 갈래는 D-002 자신의 미해결 자기리뷰 논점(§4.2·§4.4에 붙은 라인 299-300)으로 그대로 적혀 있어, 문서 작성자도 미결로 표시한
  상태다. 인접 결정들이 문제를 프레이밍하나 해소하지 않으므로 open.
- **인용**:
    - **DESIGN-002 (D-002)** (§4.2 command-core / §4.4 의존성 매트릭스 (라인 95, 115, 170-175)) — 애그리거트는 handle(command)
      →List<DomainEvent> + apply(event)→newState를 스스로 진다. 매트릭스는 command-core에 contract 금지(허용=shared만),
      command-infrastructure에 command-core 금지를 명시. 그러나 DomainEvent가 core 타입인지 contract 타입인지, 매핑 주체가 누구인지는 규정 안 함.
    - **DESIGN-002 (D-002)** (§ 논점 / 자기리뷰 (라인 299-300)) — 문서 스스로 두 결함을 미결 논점으로 기록: (299) ES 엔진이 infra에 거주+core import
      금지라 리플레이 apply 재구성 조립이 어댑터로 새고 엔진이 raw I/O 반쪽이 됨—분업선 미기재; (300) handle()→List<DomainEvent>의 DomainEvent가 contract면
      매트릭스 위반, core면 application 매핑 계층 필요한데 그 흔적 없음.
    - **DESIGN-008 (07-messaging-topology)** (§4.12 무엇을 Kafka로 내보내는가 (라인 188-190, 239)) — 토픽으로 나가는 것은 내부 도메인 이벤트가 아니라 통합
      이벤트(published language). 내부 도메인 이벤트와 분리하며 내부 모델을 그대로 흘리지 않는다—즉 core 도메인 이벤트≠contract 이벤트라는 두 범주는 확정. 다만 그 사이 번역을
      누가 하는지는 미기재.
    - **RFC-023 (event-schema-contract-management)** (§결론/이의 여지 (라인 103, 75)) — 공유 계약 모듈=통합 이벤트만; '공유 계약 모듈의 위치·소유·버저닝
      규약과 내부 도메인 이벤트와의 경계는 [[07-messaging-topology]] Design'으로 명시적 유보. 즉 내부↔contract 경계는 열린 채로 Design에 위임됨.
    - **DESIGN-012 (environments-and-testing)** (§ 이벤트 계약 (라인 125, 372)) — '얇은 통합 이벤트 모델만 공유. 내부 도메인 이벤트는 절대 공유 금지'—두 범주
      분리는 재확인하나 core→contract 매핑 계층이나 리플레이 apply 위치는 다루지 않음.
    - **DESIGN-009 (event-store-lifecycle)** (§3 리플레이 (라인 94)) — 리플레이 재구성이 events.fold(base.state){state,e->state.apply(
      e)}로 표현—apply는 core 소유 메서드. 그러나 이 fold를 어느 모듈이 실행하는지(ES 엔진은 infra라 core import 금지), 엔진과 어댑터의 분업은 미기재.
    - **RFC-010 (module-structure-migration)** (논점2 매핑 보일러플레이트 (라인 105-118, 189)) — 도메인↔JPA 수동 매핑만 확정(코드젠·공통 추상 비채택).
      도메인 이벤트↔contract 이벤트 매핑은 이 RFC 범위 밖—프롱1의 매핑 계층을 닫지 않음.
    - **ADR-15 / RFC-016 (payment-acl-boundary)** (결정 요약 (ADR-15 라인 33, RFC-016 라인 69)) — ACL 번역 패턴은 외부 PG 어휘↔도메인 이벤트(
      PaymentConfirmed/Failed/Refunded) 경계 전용. 내부 도메인 이벤트→contract 통합 이벤트 번역이나 리플레이 재구성 분업에는 적용되지 않음.
- **처분안(제안)**: accept-new-rfc → *RFC: 도메인 이벤트 타입 소유·contract 매핑·리플레이 apply 오케스트레이션의 계층 분업선*
    - 근거: 두 갈래(애그리거트 반환 이벤트의 타입 소유·core→contract 매핑 주체, 그리고 core를 import 못 하는 infrastructure ES 엔진과 core 소유 apply(event)
      리플레이 재구성의 분업)는 결국 "계층 의존 규칙이 이벤트 소싱 실행 모델과 만나는 하나의 경계"라는 동일 seam이며, D-002 자기리뷰(라인 299-300)가 스스로 미결로 적었고 RFC-023이 이
      경계를 명시적으로 Design에 유보한 채 어느 문서도 분업선을 긋지 않아 open이다. RFC-010(도메인↔JPA)·ADR-15/RFC-016(외부 PG ACL)은 인접 경계만 닫을 뿐 내부
      도메인이벤트→contract 매핑과 리플레이 조립 위치를 다루지 않으므로 doc-tweak로 봉합 불가하다. 애그리거트 실행·발행·리플레이라는 command 경로 핵심을 규정하는 material·open
      결함이므로 좁은 토픽 RFC로 열어 타입 소유·매핑 계층·리플레이 오케스트레이션 배치를 한 번에 확정할 것을 제안한다(최종 채택은 소유자 판단).

### C09 — SKIP LOCKED와 DLQ 수동 재생이 애그리거트별 발행·소비 순서 계약을 파괴한다

`HIGH` · 판정 **open** (conf high) · 처분안 **accept-new-rfc** · 우선 HIGH

- **문서/항목**: D-008 §4.9, D-008 §4.10
- **설명**: relay 단일성을 SKIP LOCKED 경쟁 소비로 풀면 같은 aggregate_id의 두 이벤트가 서로 다른 relay에서 발행돼 Kafka 도착 순서가 뒤집힌다. DLQ 격리 후 수동 재생도
  앞 순서를 건너뛴 뒤 재생해 파티션 순서를 정면으로 깬다. inbox는 '이미 처리했는가'만 볼 뿐 '앞 순서를 건너뛰었는가'는 못 봐 이 역전을 흡수하지 못한다. 순서 계약과 이 두 메커니즘의 상호작용이
  연결되지 않았다.
- **판정 근거**: 순서 계약(파티션 키=aggregate_id)과 두 메커니즘(SKIP LOCKED 경쟁 relay, DLQ→수동 재생)은 각각 결정됐으나 그 상호작용을 봉합하는 결정은 어디에도 없다. 오히려
  D-008 자신의 Weakness §264가 "SKIP LOCKED는 순서를 직렬화하지 않는다 — 여러 relay가 같은 aggregate_id의 두 이벤트를 sequence_no 순서와 다르게 Kafka로
  publish할 수 있고, 이 상호작용이 문서에 없다"고 명시적으로 자인한다. DLQ 재주입 순서 역전도 completeness audit §101이 미결(unchecked)로 "순서 깨고 재주입 시 정확성
  위험"이라 표시. ADR-09 §63·RFC-021은 inbox/dedup을 정체성·버전 가드로만 정의해 "앞 순서를 건너뛰었는가"를 못 본다는 concern의 지적과 정확히 일치한다. 즉 결정된 것은 메커니즘일
  뿐, 순서 계약과의 충돌 해소는 open이다.
- **인용**:
    - **D-008 (DESIGN-008-messaging-topology)** (§4.9 relay 단일성 및 Weakness §264) — §4.9는 relay 단일성을 SELECT … FOR UPDATE
      SKIP LOCKED 경쟁 소비로 확정. 그러나 같은 문서의 Devil's Advocate §264가 'SKIP LOCKED는 순서를 직렬화하지 않는다 … relay 병렬성이 outbox의
      sequence_no 순서를 Kafka 도착 순서에서 뒤집을 수 있다 … §4.3 발행 순서대로 소비가 성립하려면 relay가 aggregate별로 직렬화하거나 outbox가 순서를 강제해야 하는데
      SKIP LOCKED는 정확히 그 직렬화를 포기하는 선택이다 — 이 상호작용이 문서에 없다'라고 자인. 미해결.
    - **D-008 (DESIGN-008-messaging-topology)** (§4.10 DLQ 실패 루프) — DLQ 격리 후 기본 수동 재생을 결정하나, 앞 순서를 건너뛴 뒤 재주입할 때 파티션 순서를
      어떻게 보존하는지에 대한 언급이 전혀 없음. 재시도/자동 재생 조건은 TBD로 운영 사이클 위임.
    - **analysis/04-design-completeness-audit** (§101 (미체크 항목)) — 'DLQ 토픽/스키마/replay 경로 미정 — 순서 깨고 재주입 시 정확성 위험'을
      unchecked open 항목으로 명시. DLQ 재생의 순서 파괴가 미결 리스크로 공식 등재됨.
    - **ADR-09 (event-ordering-and-delivery-guarantee)** (결정사항 §57-63, 미결정 §97-101) — 파티션 키=aggregate_id로 애그리거트별 순서 보장(
      §57), inbox는 event_id로 '봤는가'만 dedup하고 sequence_no는 '더 과거를 덮지 마라' 순서 가드로 병행(§63) — '앞 순서를 건너뛰었는가'를 감지하는 장치는 없음.
      relay 경쟁 소비/DLQ 재생과 순서 계약의 상호작용은 다루지 않음.
    - **RFC-003 (messaging-delivery)** (논점 3 §104-116, 논점 5 §128-136) — SKIP LOCKED 경쟁 소비 채택(논점3)·재시도→백오프→DLQ→Slack→수동
      재생(논점5)을 방향으로 결정하되 둘 다 〔근거 확인/보강 필요〕 표기. 두 결정이 aggregate별 순서 계약을 깨는지에 대한 분석·봉합은 없음.
    - **RFC-021 (event-identity-and-global-ordering)** (논점 1 §83-93) — inbox/dedup 키를 event_id로 일반화하되 'dedup(누가 봤나)과 순서
      가드(더 과거를 덮지 마라)는 다른 일'이라 명시 — inbox가 갭(건너뛴 앞 순서) 감지 책임은 지지 않음을 재확인. concern의 '역전 흡수 불가' 지적과 일치.
- **처분안(제안)**: accept-new-rfc → *RFC: aggregate별 순서 계약과 병렬 relay(SKIP LOCKED)·DLQ 재생의 상호작용 봉합*
    - 근거: 인용이 모두 확인됨 — D-008이 §264(SKIP LOCKED가 순서를 직렬화 안 함)·§272(DLQ 수동 재생이 순서 파괴)에서 두 상호작용을 스스로 자인하고, audit §101이 DLQ
      재생 순서 역전을 미결 정확성 리스크로 등재, ADR-09·RFC-021은 inbox가 dedup만 하고 '앞 순서 건너뜀'은 못 본다고 명시한다. 즉 메커니즘은 각각 결정됐으나 순서 계약과의 충돌 해소
      결정이 어디에도 없어 read model 오염으로 이어지는 실질 갭이며, 문서 각주가 아니라 별도 결정이 필요하다. 순서 계약이 핵심 데이터 정합성 기반이고 두 메커니즘 모두 이미 채택돼 상시 발동되므로
      HIGH.

### C16 — 마이그레이션의 genesis 시딩·단방향 동기·롤백 접합점 미정의로 이중반영·데이터 유실

`HIGH` · 판정 **open** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 HIGH

- **문서/항목**: D-005 §4.3, D-005 §4.6, D-005 §4.7
- **설명**: 긴 병행 창에서 V1→V2 동기와 V1 현재 상태 genesis 시딩의 스냅샷 커서·오프셋 접합이 없어 둘 다면 이중 반영, 둘 다 아니면 유실이다. 역동기가 배제돼 컷오버 후 V2에 쌓인 이벤트가
  V1에 없으므로 트래픽 롤백은 그 사이 예약·취소를 증발시킨다. '재구성 상태=V1 현재 상태' 동등성 축은 genesis가 V1을 통째 옮긴 것이라 순환 논리라 게이트로 약하다.
- **판정 근거**: C16의 세 축(genesis 시딩·단방향 동기의 스냅샷 커서/오프셋 접합점, 역동기 배제로 인한 롤백 데이터 유실, '재구성 상태=V1 현재 상태' 순환 게이트)은 D-005의
  Weakness 절 §4.3·§4.6·§4.7 세 불릿과 사실상 동일한 문구이며, 문서가 이를 스스로 '문서가 다루지 않는다', '보존 기간은 Design 디테일로만 처리', '게이트로서 약하다', '후속 검토
  대상'이라고 명시적으로 미해결로 남겼다. 이행을 소유한 RFC-013은 접합점·보존기간·부분 역방향을 모두 'Design에서 검증'으로 이월했고 클린 슬레이트라며 하류 산출 없이 종결했다. 대응 ADR도 없다(
  결정 표 전 행 ADR=—). 접합점/오프셋을 해소한 다른 RFC/ADR도 없다(RFC-011은 프로젝션 재구축 catch-up이지 V1→V2 쓰기 동기 접합이 아니다). 따라서 결정 문서로 해소된 바 없이 열려
  있다.
- **인용**:
    - **DESIGN-005-migration (D-005)** (§Weakness §4.3 (line 173)) — 문서 스스로 미해결로 명시: 시딩 스냅샷 시점 이후·컷오버 이전 V1 변경이 (a)
      genesis 포함인지 (b) 동기 파이프 별도 이벤트인지 '경계가 없다. 둘 다면 이중 반영, 둘 다 아니면 유실'이며 '스냅샷 커서와 동기 스트림 오프셋의 접합점을 문서가 다루지 않는다'고 자인.
      C16의 이중반영/유실 축과 동일.
    - **DESIGN-005-migration (D-005)** (§Weakness §4.6 (line 176)) — 컷오버 후 V2 신규 이벤트가 보존 V1에 반영 안 됨(역동기 배제), 트래픽 롤백 시 그
      사이 예약·취소 증발. '보존 기간이 길수록 유실 창이 커지는데 문서는 보존 기간을 Design 디테일로만 처리한다'—C16의 롤백 접합점·데이터 유실 축과 동일하며 미결로 표시.
    - **DESIGN-005-migration (D-005)** (§Weakness §4.7 (line 174)) — genesis가 V1을 통째로 옮긴 것이라 '재구성 상태 = V1 현재 상태'는 정의상 참인
      순환이라 게이트로 약하다고 자인. C16의 순환 논리 지적과 동일.
    - **DESIGN-005-migration (D-005)** (§Weakness 마무리 (line 179)) — '본 절은 리뷰용 반박 정리이며, 각 항목은 후속 검토 대상' — 세 항목 모두 미해결·후속
      과제로 명시.
    - **DESIGN-005-migration (D-005)** (§4.6 (line 103) / §7 Risks (line 142)) — 데이터 롤백 = V1 보존 + 트래픽 토글, '보존 기간 구체값은
      Design'으로만 처리. 오프셋/커서 접합, 유실 창 정량화 없음.
    - **RFC-013-data-migration-genesis-events** (논점 2 결론 (line 120) / 논점 4 결론 (line 140)) — '컷오버 직전 V2가 받은 쓰기를 V1로 되돌리는
      부분적 역방향은 Design에서 검증', '보존 기간을 얼마로 잡을지... Design에서 검증'으로 접합점·보존기간을 모두 Design으로 이월 — RFC는 결론을 열어둠.
    - **RFC-013-data-migration-genesis-events** (상태 헤더 (line 3) / 결정 요약 표 (line 156-163)) — RFC-013 '✅ 종결'이나 클린 슬레이트로 이행
      불필요라 닫음(하류 산출물 없음). 결정 표 전 행 ADR='—' — 마이그레이션 접합/롤백을 비준한 ADR이 없음.
    - **ADR-06 strangler-migration** (결정/병행 창 (line 22-33), 미결정 사항 (line 60-62)) — 점진 전환·단방향·긴 병행 방향만 확정. 스냅샷 커서↔동기 오프셋
      접합, 보존 기간, 역동기 롤백 데이터 유실은 다루지 않고 '단계별 사이클 분할·일정'만 미결로 남김.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'open' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C34 — 결제 사가 표면 동결이 부분환불·분쟁·재시도 소진을 표현 못 하고 확정 경로가 얕다

`HIGH` · 판정 **open** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 HIGH

- **문서/항목**: D-015 §4.2, D-015 §6.1, D-015 §7, D-007 §4.6
- **설명**: 3이벤트 사가 표면 동결이 이미 존재하는 partially_refunded와 충돌해 '동결'이 이름뿐이고 스키마는 진화하며, 전액 기준 되감기 보상과 부분환불 현실의 간극을 이 경계가 흡수한다는
  근거가 없다. PG의 4xx 영구 거절과 5xx/타임아웃 재시도 가능 구분이 미정이라 오판 시 사가가 타임아웃까지 매달리고 '재시도 소진→failed' 전이가 산출물에 없다. 대사를 유일 백스톱으로 세우면서 '
  늦게라도 발행'의 지연 상한이 없어 웹훅 유실 시 확정이 최대 24시간 지연될 수 있다.
- **판정 근거**: C34의 세 갈래 모두 어느 ADR/RFC에서도 해소되지 않았다. (1) 3이벤트 동결 vs partially_refunded 충돌: D-015 §4.4가 partially_refunded
  상태를 두면서도 '상세 표현은 구현 사이클'로 미루고, 전액 기준 보상과 부분환불 간극 흡수 근거는 없음 — 이는 D-015 자체 Weakness §290이 그대로 제기한 반박. (2) PG 4xx 영구거절 vs
  5xx/타임아웃 재시도 구분과 '재시도 소진→failed' 전이: D-015·ADR-15·RFC-016 어디에도 규칙이 없고 §7이 '재시도 정책은 구현 사이클 위임', Phase 2 산출물에 failed 전이
  부재 (Weakness §294 동일 지적). (3) 대사 지연 상한/SLA 부재: §7이 대사를 유일 백스톱으로 세우나 대사 주기는 '벤더 의존·구현 사이클 위임'이라 24h 지연 가능 (Weakness
  §298). ADR-15와 RFC-016은 표면 3이벤트 동결·ACL·단방향 대사라는 상위 뼈대만 확정하고, 부분환불 표현·재시도 정책·대사 주기·이중환불 가드를 명시적으로 구현 사이클로 미뤘다. 요컨대 C34는
  D-015의 Weakness(Devil's Advocate) 절을 거의 그대로 옮긴 것이며, 이를 닫는 결정 문서가 존재하지 않는다. 따라서 open.
- **인용**:
    - **D-015 (DESIGN-015-payment-integration)** (§4.2 Key Design Decisions + §4.4 Data Model) — §4.2가 사가 표면을
      PaymentConfirmed/PaymentFailed/PaymentRefunded 3개로 '동결'한다고 선언하지만, §4.4 상태표에는 이미 partially_refunded 상태가 존재하고 그 옆에 '
      상세 표현은 구현 사이클'이라 명시. 즉 부분환불 표현은 결정된 게 아니라 미룸. 3이벤트 동결이 부분환불을 어떻게 흡수하는지에 대한 계약(금액 페이로드 여부 등)은 문서에 없음.
    - **D-015 (DESIGN-015-payment-integration)** (§4.1 이의 여지(Design) + §5 Alternatives(ES 도입 기각)) — '분쟁·부분환불·재시도가 겹치면 내부
      상태 전이가 풍부해져 ES가 다시 후보'라고 문서 스스로 인정하면서도 ES↔상태 선택은 '컨텍스트 내부 결정으로 미룸'. 부분환불 현실과 전액 기준 되감기 보상 사이의 간극을 경계가 흡수한다는 근거 제시
      없음 — 미룸이지 해소가 아님.
    - **D-015 (DESIGN-015-payment-integration)** (§6.1 아웃바운드 + §7 Risks(verify 타임아웃 행) + §8 Phase 2 산출물) — 아웃바운드 릴레이
      재시도로 at-least-once를 effectively-once로 만든다고만 하고, PG 4xx(영구 거절) vs 5xx/타임아웃(재시도 가능) 구분 규칙은 미정. §7은 'verify 조회
      타임아웃→재시도 정책 + 대사 안전망. 구현 사이클에 위임'으로 미룸. §8 Phase 2(payment_intent 테이블/릴레이 워커/멱등키) 산출물에 '재시도 소진→failed' 상태 전이가 없음.
      이 결함은 D-015 자체 Weakness §290·§294가 명시적으로 지적.
    - **D-015 (DESIGN-015-payment-integration)** (§7 Risks 첫 행 + §3 Non-Goal + §6.3) — '웹훅 유실→대사가 누락 이벤트를 늦게라도 발행'으로 대사를
      유일 백스톱으로 세우는데, §3 Non-Goal과 §6.3이 대사 주기·소스를 '벤더에 달리며 구현 사이클에 위임'으로 미룸. 지연 상한(SLA)이 문서에 없어 일 1회 정산 파일이면 확정이 최대 24시간
      지연 가능 — Weakness §298가 동일 지적.
    - **ADR-15 (15.payment-acl-boundary)** (결정사항 + '미결정 사항 및 추가 논의(→ 구현 사이클)') — ADR은 3이벤트 동결·ACL·의도먼저기록·환불=새정방향·단방향 대사만
      결정. 미결 목록에 '부분환불 표현·ES 재검토 트리거', '멱등키 미지원 시 verify-before-call', '대사 주기·소스', '이중환불 가드'를 모두 구현 사이클로 위임. 4xx/5xx
      구분·재시도소진→failed 전이·대사 지연 상한은 결정 항목에 부재.
    - **RFC-016-payment-integration-boundary** (§Non-Goal(라인 61) + 논점4 대사(라인 83-85) + 결론) — RFC도 '상태 모델·스키마·릴레이 배치·대사
      주기 → Design'으로 명시 위임. 논점4는 '웹훅 유실·verify 타임아웃 등 잔여 불일치를 주기적 단방향 대사로 보정, 자동 못 풀면 운영 큐'라고만 하고 지연 상한이나 4xx/5xx 재시도
      정책은 다루지 않음. 표면 3이벤트 동결만 재확인.
    - **D-007 (DESIGN-007-consistency-and-sagas)** (§4.6 결제 단계의 외부 경계 (라인 242-244)) — D-007은 payment를 '사가의 한 참여 컨텍스트'로만
      다루고 '외부 PG 호출의 비동기·타임아웃·재시도·환불 API 실패 처리'는 payment 경계 안 흡수 대상으로 명시 위임(→DESIGN-015). 즉 D-007은 문제를 해소하지 않고 D-015로
      넘김 — Weakness §362가 이를 '경계 설정이 아니라 회피'로 지적.
    - **D-007 (DESIGN-007-consistency-and-sagas)** (§4.8 / §7 미결 항목 (라인 291, 319) + RFC-006 라인 125) — 부분 보상 상태(
      SeatReleased는 됐는데 PaymentRefunded 실패)를 v1 PoisonMessage 모델이 담는지가 TBD(구현 사이클). 부분환불/부분보상 현실을 다룰 복구 경로가 미결정 상태로 남아
      C34의 '확정 경로가 얕다'를 뒷받침.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'open' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C35 — 멱등키·의도-먼저 기록의 문서 내 모순과 verify 경로의 dual-write·열거 공격면

`HIGH` · 판정 **open** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 HIGH

- **문서/항목**: D-015 §6.1, D-015 §6.1
- **설명**: 환불·청구 멱등키가 §6.1 '같은 공간'과 §6.2 '분리'로 문서 내에서 상충해 이중 환불 방어 핵심 축이 자기모순이고, 같은 공간이면 우연 충돌로 환불↔청구가 서로 흡수될 위험이다.
  verify 후 Outbox append 직전 죽으면 재-verify·재-append가 발생하며 디듀프 키(PG 거래 ID)를 verify 시점에 이미 알아야 성립하고, 위조 가능 콜백의 거짓 거래 ID로
  verify를 유발하는 리소스 소모·열거 공격면이 다뤄지지 않는다.
- **판정 근거**: 두 축 모두 미결이다. (1) 청구·환불 멱등키 '같은 공간(§6.1)' vs '분리(§6.2)' 모순은 DESIGN-015가 스스로 Weakness 4번째 항목에서 '이중 환불 방어의 핵심
  축이 문서 내에서 상충'이라 인정하고 후속 검토로 남겼고, ADR-15 line 67이 '청구·환불 멱등키 공유 모델·이중 환불 가드'를 구현 사이클 위임 항목으로 명시해 결정이 없음을 확정한다 —
  RFC-016·RFC-003 어디에도 공간 분리/공유를 확정한 결정이 없다. (2) verify 후 Outbox append 직전 크래시 시 재-verify·재-append 멱등, 그리고 위조 콜백의 거짓 거래
  ID로 verify를 유발하는 리소스 소모·열거 공격면은 오직 DESIGN-015 Weakness 2번째 항목에 '다루지 않는다'로 명시된 오픈 이슈이며, 어떤 ADR/RFC도 이를 해소하지 않는다(보안 절
  §6.2·ADR-15 결과·RFC-016 모두 웹훅 서명 검증만 다루고 verify 트리거 남용/열거는 침묵). 따라서 open.
- **인용**:
    - **DESIGN-015** (§6.1 보상 절 (line 182)) — '정방향 청구와 같은 멱등키 공간을 쓰되, 이미 환불된 거래면 무시를 PG verify로 판별' — 청구·환불이 같은 공간이라 서술.
    - **DESIGN-015** (§6.2 Security Considerations (line 187)) — '청구 멱등키와 환불 멱등키는 이중 청구·이중 환불을 방지하기 위해 충돌하지 않도록 분리한다. 구체
      키 생성·전달 규약은 구현 사이클에 위임.' — §6.1의 같은 공간과 정면 상충하며, 정작 규약은 미결로 위임.
    - **DESIGN-015** (Weakness/Devil's Advocate 4번째 항목 (line 296)) — 문서가 스스로 '환불과 청구가 같은 멱등키 공간이라는 §6.1/6.2가 서로 모순 … 이중
      환불 방어의 핵심 축이 문서 내에서 상충한다'고 인정. 우연 충돌로 환불↔청구 흡수 위험까지 명시. 결론 각주는 '후속 검토 대상'으로 미해결 처리.
    - **DESIGN-015** (Weakness/Devil's Advocate 2번째 항목 (line 292)) — 'verify 응답을 받고 PaymentConfirmed를 Outbox에 적기 직전
      프로세스가 죽으면 재기동 후 다시 verify하고 다시 적으려 한다 … 디듀프 키(PG 거래 ID)를 verify 시점에 이미 알고 있어야 성립 … 거짓 거래 ID로 verify를 유발하는 리소스
      소모/열거 공격면은 다루지 않는다.' — 문서가 미해결 약점으로 명시.
    - **ADR-15** (결정 세부 line 37 및 위임 항목 line 67) — line 37은 '환불=새 정방향, 같은 의도→멱등키 릴레이→결과 경로, 반드시 멱등(이중 환불 가드는 PG
      verify로)'만 말할 뿐 키 공간 공유/분리를 확정하지 않음. line 67은 '보상·멱등키 공간 — 청구·환불 멱등키 공유 모델, 이중 환불 가드'를 구현 사이클 위임 항목으로 명시 = 미결.
    - **RFC-016** (§인바운드/아웃바운드 결정 표 (line 76-77, 99, 124-125)) — 인바운드 3겹(서명·PG 거래 ID 멱등 디듀프·verify 순서 무력화)과 아웃바운드
      의도-먼저+멱등키 릴레이는 확정하나, 청구/환불 멱등키 공간 분리 여부, verify-append 크래시 재-append 멱등, 위조 거래 ID 열거 공격면은 전혀 다루지 않음.
    - **RFC-003** (논점2 결정 (line 100-102, 185)) — 비-멱등 외부 부수효과는 inbox/부수효과 outbox로 감싸고 외부 API 멱등은 idempotency-key로 처리한다고
      일반 원칙만 정함. 청구↔환불 키 공간 충돌, verify 경로 재-append, 열거 공격면 등 payment-특정 쟁점은 미해결.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'open' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C40 — 장애 폴백 미정 상태로 단일 인스턴스만 먼저 확정한 순서 역전

`HIGH` · 판정 **open** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 HIGH

- **문서/항목**: D-018 §6.1
- **설명**: Redis 장애 시 레이트리밋(통과 vs 차단)·멱등 디듀프(중복 허용) 폴백을 모두 후속으로 미뤘으면서 '인스턴스 하나로 충분'을 먼저 확정했다. 폴백 미정 상태로 단일 인스턴스를 확정하면
  Redis가 흔들리는 순간 과부하 통과나 결제·예약 중복 생성 중 무엇이 터질지 모른 채 운영에 들어간다. 가용성 결정을 미루면서 인스턴스 수를 확정한 것은 순서 역전이다.
- **판정 근거**: 순서 역전 우려는 미해소(open)다. 단일 인스턴스 확정은 ADR-19(D-2, Proposed)·RFC-018(결정4)에서 명시 결정됐으나, 레이트리밋(통과 vs 차단)과 멱등 디듀프(중복
  허용) 장애 폴백은 DESIGN-018 §6.1·§8에서 '후속 사이클'로 명시 연기됐고 ADR-19 미결정 목록에도 오르지 않았다. 확정된 폴백은 분산 락 L1→L1′(RFC-014/ADR-16)뿐 — 이는 락
  경로만 덮고 레이트리밋·디듀프는 공백. 결정적으로 이 우려는 DESIGN-018 자신의 Devil's Advocate(line 259)에 C40과 사실상 동일 문장으로 이미 기재돼 있으나, 해소하는 결정이
  아니라 '미해소 리스크'로 남아 있다. 즉 우려는 인지·기록됐지만(그래서 완전 open은 아니고 인식됨) 이를 해결하는 ADR/RFC 결정은 존재하지 않는다.
- **인용**:
    - **DESIGN-018** (§6.1 Error Handling (lines 140-141)) — Redis 장애 시 레이트리밋(통과 허용 vs 요청 거부)은 '정책 결정 필요 — 후속 사이클로 넘김',
      멱등 디듀프(중복 처리 위험)는 '후속 사이클에서 허용 staleness·폴백 정책 확정'으로 명시 연기. 두 폴백 모두 미결.
    - **DESIGN-018** (§4.4 결정3 (line 92) / §9.2 (lines 213-217)) — '인스턴스 하나면 충분'을 확정. §9.2는 단일 인스턴스 충분성 벤치마크 근거까지 제시. 폴백
      미정 상태에서 인스턴스 수는 확정됨.
    - **DESIGN-018** (§ Weakness / Devil's Advocate (line 259)) — 본 문서가 이 우려를 스스로 명시: '레이트리밋·디듀프 장애 폴백이 전부 미결인데 단일 인스턴스로
      확정 … 가용성 결정을 미루면서 인스턴스 수 결정을 확정한 것은 순서가 뒤집혔다.' C40과 동일 문장. 해소가 아니라 미해소 리스크로 기록.
    - **ADR-19** (결정사항 D-2 채택 / 미결정 사항 목록) — '단일 durability 등급 … 인스턴스 하나면 충분'을 결정(상태 Proposed). 그러나 미결정 사항 목록에는
      레이트리밋/디듀프 장애 폴백 정책이 아예 없음 — 키 구성·maxmemory-policy·호스팅/토폴로지만 연기. 즉 폴백 연기가 ADR 오픈아이템으로도 추적되지 않음.
    - **RFC-018** (결론표 결정4 (line 141) / line 128) — '단일 인스턴스(분리는 추후)'를 확정하나 Redis 장애 시 레이트리밋·디듀프의 통과/차단·중복허용 폴백 시맨틱은 언급
      없음.
    - **RFC-014 / ADR-16** (L1→L1′ 폴백) — 분산 락 폴백(Redis 불가 시 DB 비관 락 강등, 낙관 회귀 금지)은 확정됨 — 그러나 이는 락 경로만. 레이트리밋·디듀프 폴백은 여기서
      다루지 않음(대비: 락은 결정, 나머지 둘은 미결).
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'open' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C45 — '잎 먼저' 1차 기준과 허브 reservation을 앞세운 실제 순서의 모순

`HIGH` · 판정 **closed (OVER)** (2026-07-08) · ~~처분안 accept-new-rfc~~ → **결정 불요** · 우선 —

> **✅ 종결 (2026-07-08, OVER — 결정 불요):** leaf-first를 준수하면(restaurant→reservation) "규칙 vs 순서" 모순 자체가 소멸 — fail-fast override를 버리는 것으로 끝. 파생 문제(restaurant가 V1인 동안 식당명 비정규화 갭)는 갈래1에 기생한 것으로, restaurant를 먼저 V2로 올리면 reservation 프로젝션 시점엔 이벤트 소스가 이미 있고 **V1→V2가 비실시간 배치 컷오버**라 두 컨텍스트가 V1/V2로 공존하는 창이 없다 → 임시 이벤트 브리지·컷오버후 백필 결정 모두 불요. 요구하던 새 RFC 2건 증발. **잔여 조치(결정 아님):** D-005 §4.2·RFC-010 순서표를 leaf-first로 정정 + "fail-fast는 leaf-first 위반 않는 범위에서만 tie-break"로 강등 명시.

- **문서/항목**: D-005 §4.2
- **설명**: 의존성(잎 먼저)을 1차 기준으로 못박지만 실제 시퀀스는 restaurant·timetable에 의존하는 허브 reservation을 restaurant보다 먼저 옮긴다. fail-fast를
  tie-break로 1차 기준 위에 올려 규칙 우선순위가 결과에 맞춰 사후 조정됐고, restaurant가 아직 V1이면 reservation 프로젝션이 식당명 비정규화를 성립시킬 이벤트를 못 받는다.
- **판정 근거**: 순서 "규칙"(의존성 1차 + 위험·학습 결선)은 RFC-010 논점3과 ADR-06에서 확정됐다. 그러나 C45가 지적한 모순 자체 — 즉 그 1차 기준(잎 먼저)과 실제 1~5 시퀀스(허브
  reservation #2가 restaurant #3보다 앞)가 어긋난다는 점 — 를 해소하는 결정은 어디에도 없다. 오히려 (1) ADR-06과 RFC-010, D-005 §4.2 모두 1~5 시퀀스를
  명시적으로 "초안(draft)"으로만 표기하고, (2) RFC-010 논점3 결론이 "실제 의존성 그래프 위 1~5단계 확정은 Design"으로 미뤘으며, (3) 정작 Design 문서인 D-005는 §4.2
  시퀀스를 초안 그대로 재수록한 뒤 이 모순을 §Weakness 첫 항목으로 자기 적시하고 "각 항목은 후속 검토 대상"이라 명시해 미결로 남겼다. reservation 프로젝션이 아직 V1인 restaurant의
  식당명 비정규화 이벤트를 못 받는다는 파생 문제(DESIGN-004 §4.5 의존)도 어느 문서에서도 다뤄지지 않는다. 규칙 우선순위가 결과에 맞춰 사후 조정됐다는 지적을 반박·정합화하는 결정이 부재하므로
  open.
- **인용**:
    - **DESIGN-005-migration** (§4.2 전환 순서 (초안 — 의존성 기반) 표·mermaid) — 1 timetable → 2 reservation(핵심 ES) → 3
      restaurant → 4 schedule·user → 5 menu 등. reservation(#2, 허브)이 restaurant(#3)보다 앞. 제목부터 '초안'이라 명시.
    - **DESIGN-005-migration** (§4.2 '순서를 정하는 규칙' 불릿) — 1차 기준=의존성(잎 먼저), 결선=위험·학습가치. timetable이 1번인 근거로 tie-break를 든다 —
      규칙 자체는 잎 먼저를 1차로 못박음.
    - **DESIGN-005-migration** (§Weakness 첫 번째 불릿 + 말미 주석) — '§4.2 순서와 §4.1 잎 먼저 규칙이 서로 어긋난다 … fail-fast로 tie-break를 1차
      기준 위에 올린 것, 규칙 우선순위가 결과에 맞춰 사후 조정됐다 … reservation을 restaurant보다 먼저 옮기면 식당명 비정규화(DESIGN-004 §4.5)가 성립 안 한다.' 그리고 '각
      항목은 후속 검토 대상'이라 미결로 명시. C45와 동일한 지적이 문서 자체의 미해결 반박으로 남아 있음.
    - **ADR-06 (06.strangler-migration)** ('전환 순서(초안, 의존성 기반)' 목록 #37-39 및 장점 #49) — 동일 순서를 '초안'으로 제시(2 reservation, 3
      restaurant)하고 장점에 'fail-fast — 위험·핵심(ES)을 앞에 배치'라 적음. 즉 허브 reservation 선행을 fail-fast로 정당화 — C45가 지적한 사후 조정을 확정 없이
      그대로 담음.
    - **RFC-010-module-structure-migration** (§논점3 결론 (line 133)) — '전환 순서 기준 = 의존성 1차 + 위험·학습 결선. (이의 여지: 잎이 너무 사소하면 …
      더 대표성 있는 컨텍스트를 첫 타자로 올리는 게 나을 수 있다 — 실제 의존성 그래프 위 첫 컨텍스트와 1~5단계 확정은 Design.)' → 실제 시퀀스 확정을 Design으로 명시적으로 위임, 규칙만
      확정.
    - **DESIGN-004-read-model** (§4.5 (D-005 §Weakness가 인용) 식당명 비정규화) — reservation 프로젝션이 restaurant 이벤트에 의존해 식당명을 비정규화.
      restaurant가 아직 V1이면 이 이벤트가 없어 성립 불가 — 이 파생 의존 문제를 해소하는 결정은 부재.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'open' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C02 — Zero Payload 재처리가 미래 상태로 과거 이벤트를 오염시키는 time-travel 결함

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** · 우선 HIGH

- **문서/항목**: D-004 §4.7
- **설명**: projector가 최신 상태를 조회해 채우므로 재처리가 안전하다는 주장은, 과거 이벤트 재생 시 미래 상태값이 박히는 시점 오염을 놓친다. Zero Payload는 재처리를 단순화한 게 아니라
  시점 상태 부재라는 근본 제약을 프로젝션 재구축 정합성으로 떠넘긴 것이다. 이 재구축 정합성이 RFC로 위임만 되고 검증되지 않았다.
- **판정 근거**: C02는 두 층위다. (1) 교차-애그리거트 point-in-time 파생 사실 오염 — 이건 RFC-021 결정4 + ADR-22 §정확성 불변식이 '생산 시점 페이로드 박제 +
  sequence_no 버전 가드'로 정면 결정했고, C02의 '미래 상태가 과거 이벤트에 박힌다'는 프레이밍을 직접 반박한다. (2) C02가 든 리터럴 예시(PriceChanged v3 재처리 시 v5 조회)의
  단일-애그리거트 시점 복원 — 버전 가드는 stale-overwrite만 막지 v3 시점 프로젝션 값을 준다는 보장은 아니고, 이를 근본 해결할 'ES=이벤트-carried vs 비-ES=Zero Payload
  분기' 처방은 RFC-002/DESIGN-003 리뷰 노트에만 있고 결정 본문·용어집(여전히 '최신 상태 조회')에 접히지 않았다. 게다가 대응 ADR-22는 아직 'Proposed'다. 원칙은 결정됐으나
  C02가 지목한 '재구축 정합성이 RFC로 위임만 되고 검증 안 됨'이라는 정확히 그 갭(페이로드 정책 확정·시점 복원 검증)이 남아 partially-decided.
- **인용**:
    - **RFC-021** (논점 3 (line 105) · 결정 요약 #4 (line 180) · 결과(line 210)) — C02의 강한 버전(교차-애그리거트 point-in-time 파생 사실)을
      정면으로 결정: '예약 순간 그 슬롯이 열려 있었나' 같은 point-in-time 관계는 리플레이 interleaving에 따라 값이 달라지므로, 생산 시점에 이벤트 페이로드로 박아 넣어(커맨드 핸들러가
      이미 맥락을 쥠) 프로젝션이 cross-stream 순서를 재구성하지 않게 한다. 진짜 교차 불변식은 사가 몫. → '미래 상태가 과거 이벤트에 박힌다'는 오염 자체를 페이로드 박제로 원천 차단한다는
      결정.
    - **ADR-22** (§정확성 불변식(line 56-58) · 정정 노트(line 3, 상태 Proposed)) — 프로젝터 정확성 = per-aggregate 순서 + 멱등 upsert +
      per-aggregate 버전 가드(sequence_no). sequence_no 버전 가드가 '더 과거를 덮지 마라'를 강제하므로 과거 이벤트 재처리가 이미 반영된 더 최신 상태를 되돌리지 못한다. 단
      ADR 상태는 아직 'Proposed'(비준 전).
    - **RFC-011** (논점 4 멱등(line 129) · 결정 #4(line 171)) — 재구축·catch-up에서 dedup(event_id inbox)과 순서 가드(sequence_no)를 분리.
      순서 가드가 '더 과거로 덮기'를 막아 재처리 안전을 담보한다고 명시 — 그러나 이는 stale-overwrite 방지이지, 'v3 시점 프로젝션에 v3 값을 넣는다'는 point-in-time 복원을
      보장하는 서술은 아니다.
    - **RFC-002** (§4.4 검토 코멘트(감사/리뷰 섹션, DESIGN-003 line 191-193)) — C02와 동일 결함을 리뷰가 이미 지적: 'ES 컨텍스트에서 최신 상태 조회 = 리플레이'라
      Zero Payload 전제가 ES에서 어긋난다. 처방으로 'ES=이벤트-carried(내용 실음)/비-ES=Zero Payload 분기'를 제시(ES 이벤트는 불변이라 실어도 안 stale → 컨슈머
      조회 제거). 그러나 이 처방은 리뷰 노트에 머물고 §4.4 결정 본문에 반영 요망 상태.
    - **DESIGN-003** (§4 결정 본문(line 124) · Glossary(line 165)) — 결정 본문·용어집은 여전히 Zero Payload를 '컨슈머가 최신 상태/이벤트를 직접 조회'로
      정의한다. RFC-002의 ES=event-carried 분기 처방이 아직 확정 결정으로 접히지 않아, C02가 지적한 'Zero Payload가 시점 상태 부재를 프로젝션으로 떠넘김'의 근본 처방(
      페이로드 정책 ES/비-ES 분기)이 검증·명문화되지 않았다.
- **처분안(제안)**: accept-new-rfc → *RFC: 프로젝션 페이로드 정책 — ES=이벤트-carried vs 비-ES=Zero Payload 분기와 시점 복원 정합성*
    - 근거: C02의 강한 버전(교차-애그리거트 point-in-time 파생 사실 오염)은 RFC-021 결정#4의 '생산 시점 페이로드 박제 + sequence_no 버전 가드'가 이미 정면
      반박·해결했으나, C02가 지목한 실제 갭은 남아 있다. RFC-002 §4.4 리뷰 노트(line 191-193)가 'ES=이벤트-carried/비-ES=Zero Payload 분기'라는 처방을 명확히
      냈지만 그 처방은 리뷰 노트에만 있고 DESIGN-003 결정 본문·용어집(line 124·161)은 여전히 'Zero Payload=최신 상태 조회'로 남아 ES 리플레이 모순이 미해소이며, 대응
      ADR-22도 아직 Proposed다. 페이로드 정책 확정과 시점 복원 검증은 문서 각주가 아니라 결정이 필요한 사안이므로, topical RFC 한 건으로 분기를 확정·명문화하고 ADR로 접는 것이
      프로젝트 규율에 맞다.

### C10 — 주인 없는 코레오그래피가 사가 상태의 단일 조회지점·정적 전역 불변식 검증을 없앤다

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** · 우선 HIGH

- **문서/항목**: D-007 §4.4, D-007 §4.4, D-011 §4.4
- **설명**: '흐름의 주인 없음'은 사가 현재 상태를 한곳에서 볼 수 없고 부분 보상 상태를 correlationId로 사후 상관분석해야 함을 뜻한다. 상태 가드가 컨텍스트마다 흩어진 암묵적 분산 상태 머신이라
  한 곳이 가드를 잘못 구현해도 개별 테스트는 통과하고 오버부킹이 난다. 재처리 시 correlationId 불변 유지가 '이 흐름이 성공했나'를 오히려 흐린다. 운영 가시성과 전역 불변식 정적 검증 비용이 기각
  계산에서 빠졌다.
- **판정 근거**: 이 우려는 신규 리뷰 지적이 아니라 설계 문서 자신의 Devil's-Advocate 절(DESIGN-007 §Weakness)을 거의 그대로 옮긴 것이다. ADR/RFC가 관련 메커니즘은 '
  건드렸으나(touched)' 우려의 핵심 청구는 미해결로 남아 있어 partially-decided로 판정한다.

건드린 부분(부분 해소): (1) '단일 조회지점 없음'은 ADR-08 §트레이드오프에서 명시적으로 인정됨("사가의 어느 단계인가를 한 곳에서 질의할 수 없다 — 각 aggregate 상태 조합"). (2) 부분
보상 상태(SeatReleased 됐는데 PaymentRefunded 실패)의 correlationId 사후 상관분석 문제는 RFC-006 §논점4에서 "PoisonMessage 모델이 담을 수 있는지 TBD"로
남김. (3) 재처리 시 correlationId 불변 유지는 RFC-008 §논점3이 다루되, causationId 체인으로 '재처리 사실'만 표식하고 "이 흐름이 성공했나"라는 종료/성공 상태의 질의 가능성은
채우지 못해 오히려 단일 조회지점 부재 한계로 회귀한다. (4) 운영 가시성 일부는 RFC-008 correlationId 교차 조회(Tempo↔Loki)가 제공.

미해결(gap): 우려의 가장 날카로운 청구 — '흩어진 상태 가드가 곧 암묵적 분산 상태 머신이라 한 컨텍스트가 가드를 잘못 구현해도 개별 테스트는 통과하고 오버부킹이 난다 / 전역 불변식을 정적으로 검증할 방법이
없다 / 정적 전역-불변식 검증 비용이 기각 계산에서 빠졌다' — 는 어떤 ADR/RFC도 해소하지 않는다. ADR-08의 기각 계산은 '관리 포인트 N vs 1+N'만 저울질하고 정적 전역-불변식 검증 비용은
계산에 넣지 않았다. '오버부킹'·'정적 전역 불변식 검증'은 ADR/RFC 본문 어디에도 없고, 분석 문서(analysis/04·05)와 DESIGN-007 §Weakness에만 등장하며 후자는 "후속 검토 대상"
으로 못박혀 있다. 게다가 ADR-08은 여전히 Proposed(비준 대기)이고 RFC-006/analysis-05는 ADR-08 비준을 G0 이후로 미룬 상태라, 이 우려를 종결하는 accepted 결정 자체가
아직 없다.

- **인용**:
    - **ADR-08** (08.saga-orchestration-vs-choreography.md §트레이드오프 L83 / §맥락 L3 상태) — '이 예약이 지금 사가의 어느 단계인가를 한 곳에서 질의할 수
      없다 — 각 aggregate 상태를 조합해야 한다'로 단일 조회지점 부재를 명시 인정. 그러나 기각 계산(§고려된 옵션·L74 장점)은 '관리 포인트 N vs 1+N'만 저울질하고 정적 전역-불변식 검증
      비용은 계산에 넣지 않음. 상태도 'Proposed'(비준 전).
    - **RFC-006** (§논점4 L125 (사가 실패 운영 처리)) — '미결: 부분 보상 상태(SeatReleased는 됐는데 PaymentRefunded 실패)를 PoisonMessage 모델이 담을
      수 있는지는 구현 사이클에서 확정(TBD)' — 우려가 지적한 부분 보상 사후 상관분석/복구를 미결로 남김. 문서 헤더 L3: 'ADR-08 비준 대기'.
    - **DESIGN-007** (§Weakness L350·L354, 마감 L364) — 우려 C10의 원문. L350 '주인 없음=관측 불가능성, 부분 보상 상태를 correlationId로 조인해 사후
      상관분석, 기각 논거가 운영 가시성 비용을 계산에서 뺐다'; L354 '상태 가드가 흩어진 암묵적 상태 머신, 한 컨텍스트가 가드 잘못 구현→개별 테스트 통과하지만 오버부킹, 전역 불변식을 정적으로 검증할
      방법 없다'. L364 '본 절은 리뷰용 반박 정리이며 문서 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상' — 즉 미해소 상태로 명시.
    - **RFC-008** (§논점2 L115·117, §논점3 L125·127) — correlationId를 모든 root span 필수 attribute로 격상해 추적↔로그 교차 조회 보장, 재처리 시
      correlationId 유지+causationId로 재처리 식별. 운영 가시성/재처리 식별은 다루나 '이 사가가 성공/종료했나'의 단일 상태 질의는 제공하지 않음.
    - **analysis/05-rfc-closure-plan** (R8 L58, L151) — '만료 hold에 confirm=overbooking, PM 크래시 rehydrate+open-saga read
      model·부분 보상 추적' 규칙을 DESIGN-006/007에 착지시키되 'ADR-08 비준만 G0 뒤로'로 명시 — 이 우려 영역의 종결 결정이 아직 유보됨.
    - **analysis/04-design-completeness-audit** (L115) — 'confirm 시점 hold 재검증 없음 — 만료된 hold에 confirm = overbooking'을 미완
      항목으로 열거 — 오버부킹 정적 검증 부재가 열린 감사 항목임을 확인.
- **처분안(제안)**: accept-new-rfc → *RFC-024 코레오그래피 전역 불변식(오버부킹-금지)의 계약화·정적/계약 검증 전략*
    - 근거: 우려의 가장 날카로운 청구 — 흩어진 상태 가드가 곧 암묵적 분산 상태 머신이라 한 컨텍스트가 가드를 잘못 구현해도 개별 테스트는 통과하고 오버부킹이 나며, 이 전역 불변식을 정적으로 검증할
      방법·비용이 ADR-08 기각 계산에서 통째로 빠졌다 — 는 어떤 accepted 결정으로도 해소되지 않았고 DESIGN-007 §Weakness에 "후속 검토 대상"으로만 못박혀 있어 material +
      open이다. 단일 조회지점 부재는 ADR-08이 트레이드오프로 인정했으니 재론 불필요하지만, '오버부킹-금지 전역 불변식을 어떤 계약(가드 소유자·인수/계약 테스트·집계 검증)으로 강제하는가'는 좁고
      위험이 큰 별도 주제라 자체 RFC가 필요하다. ADR-08이 아직 Proposed이므로 이 RFC는 ADR-08 비준 전 기각 계산에 '정적 전역-불변식 검증 비용' 항을 되넣어 결정 근거를 보강하는
      입력이 된다.

### C11 — 코레오그래피 채택 근거가 '선형 2~3스텝'에 걸려 있고 전환 트리거·부분보상 복구가 미결인 채 Accepted

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** · 우선 HIGH

- **문서/항목**: D-007 §4.3, D-007 §4.8
- **설명**: 채택이 '흐름이 2~3스텝 선형'이라는 사실 하나에 걸려 있으나 이미 5개 흐름이 상태 가드로 간섭하며, 6번째 흐름 추가 시 근거가 무너지는데 PM 전환 트리거가 없다. 부분 보상 복구가 v1
  PoisonMessage 모델로 가능한지가 TBD인데 이것이 코레오그래피 우위를 판가름하는 핵심이다. 결론이 근거를 앞선 순서 도치다.
- **판정 근거**: 부분 결정이다. C11은 두 갈래인데 상태가 갈린다. (1) 채택 자체와 그 근거: ADR-08/RFC-006/DESIGN-007이 '2~3스텝 선형'을 근거로 코레오그래피를 명시 채택했고,
  C11 지적대로 근거가 이 사실 하나에 걸려 있음이 확인된다(이 부분은 결정됨). (2) '전환 트리거 없음' 주장은 부정확하다 — ADR-08 §결정 기준에 PM 재검토 3조건이 명시돼 있어 트리거 자체는
  존재한다(다만 정성적이라 운영화는 안 됨). (3) 그러나 C11이 '코레오그래피 우위를 판가름하는 핵심'으로 지목한 '부분 보상 복구를 v1 PoisonMessage로 표현·복구 가능한가'는 DESIGN-007
  §4.8·§7, RFC-006 논점4, DESIGN-015 모두에서 명시적으로 TBD/미결로 남아 있다. 더 결정적으로, C11의 문장 대부분('6번째 흐름 시 근거 붕괴', '부분 보상 복구 TBD인 채
  Accepted', '결론이 근거를 앞선 순서 도치')은 DESIGN-007 자신의 Weakness 절 문구와 사실상 동일하며, 그 절은 '문서의 결정을 뒤집지 않는다, 후속 검토 대상'으로만 처리해 미해소
  상태다. 따라서 채택 결정은 내려졌으나 C11이 핵심으로 지목한 부분 보상 복구 갭은 여전히 열려 있어 partially-decided로 판정한다.
- **인용**:
    - **ADR-08** (§결정 기준 (PM이 필요해지는 조건), lines 54-59) — C11이 '전환 트리거가 없다'고 하나, PM 도입 재검토 조건 3개가 명시돼 있다: (1) 단계 5개 이상+조건부
      분기 복잡, (2) 여러 컨텍스트 상태를 중앙 조합해야 다음 단계 결정 가능, (3) 자치 보상으로 정합성 보장 불가 — 세 조건 모두 충족 시. 즉 전환 트리거 자체는 존재한다.
    - **ADR-08** (§트레이드오프/결과, lines 82-93) — 채택은 Proposed 상태로 확정됐고 '2~3스텝 선형' 근거에 명시적으로 걸려 있음. 한계로 '5스텝 이상/조건부 분기 증가 시
      PM 재검토'를 인정하나, 부분 보상 복구 가능 여부는 다루지 않음.
    - **DESIGN-007** (§4.3 코레오그래피 채택 (lines 67-80)) — 채택 근거 1번이 '예약 흐름이 전부 2~3스텝 선형'이라는 사실에 명시적으로 의존. C11의 '근거가 선형 2~
      3스텝에 걸려 있다'는 정확한 서술.
    - **DESIGN-007** (§4.8 중복·순서·재처리 (line 291)) — 핵심 미결 명시: 'v1 PoisonMessage 모델이 부분 보상 상태를 담는가... 사가 전용 보정 경로가 따로
      필요한지는 구현 사이클에서 확정한다(TBD)'. C11이 지목한 부분 보상 복구가 미결임을 문서가 자인.
    - **DESIGN-007** (§7 Risks & Mitigations (line 319)) — 위험표에 '부분 보상 상태(사가 단계 실패) → 구현 사이클에서 v1 PoisonMessage 모델 적용 여부
      확정 (TBD)'. 완화책이 아직 미정인 채 리스크로만 등재.
    - **DESIGN-007** (§Weakness 반박 포인트 (lines 352, 360)) — C11과 사실상 동일 문구가 문서 자체의 devil's advocate 절에 있음: '6번째 흐름 추가 시
      채택 근거가 무너지는데 전환 트리거를 명시하지 않는다', '부분 보상 복구를 TBD로 남긴 채 Accepted... 결론이 근거를 앞선 순서 도치다'. 결론은 '후속 검토 대상'으로만 남기고 미해소.
    - **RFC-006** (논점4 사가 실패 (line 125) + 결정 요약 #4 (line 146)) — '부분 보상 상태(SeatReleased는 됐는데 PaymentRefunded 실패)를
      PoisonMessage 모델이 담을 수 있는지는 구현 사이클에서 확정(TBD)'. C11의 핵심 미결 항목을 RFC도 TBD로 명시. RFC-006 상태는 '합의'이나 이 항목은 여전히 미결.
    - **RFC-006** (논점1 결론 (line 100)) — 'PM 도입은 현재 불필요 — 미래에 5스텝 이상·조건부 분기가 복잡한 흐름이 생기면 재검토' — 전환 트리거의 정성적 기준은 존재하나 운영화(
      누가 언제 판정)되진 않음.
    - **DESIGN-015** (§4.4 및 Weakness (lines 109, 118, 290)) — 부분환불 표현·ES 재검토 트리거를 구현 사이클로 위임. 사가 표면 3이벤트 동결이 부분환불·부분보상
      현실을 흡수한다는 근거 없음을 자체 인정 — 부분 보상 복구 미결과 맞물림.
- **처분안(제안)**: accept-new-rfc → *RFC-xxx: 사가 부분 보상 복구 모델 — v1 PoisonMessage 재사용 vs 사가 전용 보정 경로*
    - 근거: 코레오그래피 채택과 그 근거·전환 조건(ADR-08 §결정 기준의 PM 재검토 3조건)은 이미 결정돼 재론 대상이 아니며, C11의 '전환 트리거 없음'은 부정확하다. 그러나 C11이 핵심으로
      지목한 '부분 보상 상태(SeatReleased 후 PaymentRefunded 실패)를 v1 PoisonMessage로 표현·복구 가능한가'는 DESIGN-007 §4.8·§7, RFC-006 논점4,
      DESIGN-015 모두에서 TBD로 열려 있고, 이는 코레오그래피 우위를 판가름하는 실질 갭이다. DESIGN-007 Weakness 절이 동일 지적을 '후속 검토'로만 미뤄둔 상태이므로, 채택은
      건드리지 않고 이 부분 보상 복구 하나만 좁게 다루는 topical RFC로 승격해 미결을 닫는 것이 옳다.

### C13 — 이미 나간 사가 부수효과·결제 실패는 보상·상태 가드로 되돌릴 수 없다

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** · 우선 HIGH

- **문서/항목**: D-006 §7, D-007 §4.7
- **설명**: 락 키 evict나 UNIQUE 거절 시 거절된 쪽이 이미 수행한 사가 선행 단계(결제 승인 등)의 보상이 트리거되지 않아, L0의 '부정확 아님'은 append 계층에서만 참이다.
  paid-after-expiry 상태 가드는 오버부킹만 막고 사용자는 결제→즉시 환불 실패를 겪으며 PG 환불 자체가 실패하면 무력하다. 이는 완화가 아니라 결함을 payment 컨텍스트로 밀어낸 것이다.
- **판정 근거**: C13의 세 갈래 중 둘은 문서가 명확히 해소하지만, 하나(환불 자체 실패 시 잔류 부분보상 상태)는 RFC가 명시적으로 미결(TBD)로 남겨 갭이 남는다. (1) '거절된 쪽의 선행 결제
  승인이 보상되지 않는다'는 전제는 D-007 §4.4 시퀀스에서 반박된다 — 좌석 점유/확정(L0 UNIQUE·락이 심판하는 지점)은 결제보다 앞서므로 UNIQUE 거절 시점엔 보상할 선행 결제가 없다. 결제가
  선행하는 유일 경로인 paid-after-expiry는 §4.7(라)가 EXPIRED+PaymentConfirmed→확정거부→RefundRequired→payment 환불로 명시 보상한다. 즉 보상은 append
  계층이 아니라 상태 가드가 발행하는 보상 이벤트로 트리거된다 → '보상이 트리거되지 않는다'는 주장은 문서상 거짓. (2) '상태 가드가 오버부킹만 막고 사용자는 결제→환불을 겪는다'는 정확한 관찰이나, 이는
  D-007 §4.7(라)·RFC-006 논점5에서 환불을 정합성 해소 수단으로 삼는 채택된 트레이드오프다(예방이라 주장한 적 없음). (3) 'PG 환불 자체 실패' — ADR-15/RFC-016이 환불=멱등
  정방향 재호출 + 단방향 대사(PG=진실) + 운영 보정 큐 + PoisonMessage 흐름으로 구조적 방향은 제시하나, RFC-006 논점4(line 125)가 부분보상 상태(SeatReleased
  성공·PaymentRefunded 실패)를 PoisonMessage 모델이 담을 수 있는지 명시적으로 구현 사이클 TBD로 미룬다 — 정확히 C13이 찌른 지점에 공식 갭이 남음. 따라서 open이 아니라
  partially-decided.
- **인용**:
    - **DESIGN-007-consistency-and-sagas** (§4.7 (라) 두 시계가 충돌할 때 — paid-after-expiry 레이스) — EXPIRED 상태에서
      PaymentConfirmed 수신 시 확정 거부하고 RefundRequired 이벤트를 발행해 payment가 환불 처리. PM 없이 aggregate 상태 가드만으로 정합성 보장 — 즉 보상은 상태
      가드가 발행하는 보상 이벤트로 트리거된다.
    - **DESIGN-007-consistency-and-sagas** (§4.4 예약 사가 이벤트 흐름 (Happy Path 시퀀스)) — SeatHeld → 결제 화면 진입 → ProcessPayment →
      PaymentConfirmed 순서. 좌석 점유/확정(L0 UNIQUE가 심판하는 append)이 결제보다 선행하므로, UNIQUE 거절 시점엔 보상할 선행 결제 단계가 통상 존재하지 않는다.
    - **RFC-006-saga-process-manager** (논점 4 — 사가 실패를 운영이 어떻게 집어 올리나 (line 121-125)) — 사가 스텝 실패는 V1 PoisonMessage 운영 흐름
      계승(저장·추적·재처리·알림). 그러나 '미결: 부분 보상 상태(SeatReleased는 됐는데 PaymentRefunded가 실패)를 PoisonMessage 모델이 담을 수 있는지는 구현 사이클에서
      확정(TBD)' — C13의 3번째 갈래가 찌른 지점이 명시적 미결.
    - **RFC-006-saga-process-manager** (논점 3 · 논점 5 · 결정요약 3,5) — 보상=각 컨텍스트가 자기 aggregate 상태를 보고 판단, 멱등·append-only. 환불은
      취소·노쇼 안의 한 단계로 payment가 자기 책임 처리. 보상 트리거는 append 계층이 아닌 상태 기반.
    - **ADR-15 (payment-acl-boundary)** (결정 §37 환불=새 정방향, §38 대사=상시 안전망 단방향 보정) — 환불은 롤백이 아니라 또 하나의 아웃바운드 호출로 같은 의도→멱등키
      릴레이→결과 이벤트 경로, 반드시 멱등(이중 환불 가드는 PG verify). 잔여 어긋남은 단방향 대사(PG가 진실) + 자동으로 못 푸는 건 운영 보정 큐 — PG 환불 실패의 구조적 흡수 경로.
    - **RFC-016-payment-integration-boundary** (논점 3 아웃바운드 · 논점 4 대사 · 결정요약 3,4) — 아웃바운드=의도 기록→멱등키 릴레이(
      effectively-once), 환불도 같은 경로. 웹훅 유실·verify 타임아웃 등 잔여 불일치는 주기적 PG 원장 대조로 단방향 보정, 자동 불가건은 운영 보정 큐.
    - **RFC-014-aggregate-concurrency-control** (논의 §100, 결정요약 §149-151, line 146) — Redisson 락은 liveness 도구지 safety 아님;
      evict·리스 만료로 풀려도 L0 UNIQUE가 정확성 최종 심판. 잔여 UNIQUE 위반은 재로드→재판단 흐름으로 흡수/409. 교차 불변식은 사가 보상으로 위임(line 146 '교차 불변식 -.->
      사가 보상').
- **처분안(제안)**: accept-new-rfc → *RFC: 부분 보상 상태(SeatReleased 성공·PaymentRefunded 실패)의 표현·복구 — PoisonMessage 계승 vs 사가 전용 보정
  경로*
    - 근거: C13의 세 갈래 중 둘(UNIQUE 거절 시점엔 선행 결제 없음, paid-after-expiry는 RefundRequired로 상태 가드가 보상 발행)은 D-007 §4.4·§4.7(라)에서
      문서상 명확히 해소되므로 '보상이 트리거되지 않는다'는 전제는 거짓이고 재론 대상 아님. 그러나 세 번째 갈래 — PG 환불 자체 실패로 남는 부분 보상 잔류 상태를 V1 PoisonMessage 모델이
      표현·복구할 수 있는가 — 는 RFC-006 논점4(line 125)와 D-007 §4.8/§7이 명시적 TBD로 남겼고, 리뷰어 자신도 D-007 line 356·360에서 이 지점을 코레오그래피 채택의
      핵심 미결로 지목한다. 이는 완화 노트로 덮을 갭이 아니라 좁고 토픽화된 RFC로 결정해야 할 실질 결함이므로 accept-new-rfc를 제안하되, 이미 해소된 두 갈래는 스코프에서 제외한다.

### C24 — 인가 클레임을 이벤트 봉투에 실어 전파하면 봉투가 위변조·재생 공격면이 된다

`MED` · 판정 **open** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-014 §4.5
- **설명**: 역할·신원을 command/이벤트 봉투에 직렬화해 다운스트림이 읽게 하면 다운스트림 인가 정확성이 봉투 무결성에 의존하는데, 오래된 봉투 재생·내부 큐 접근·필드 재기록에 대한 서명·불변성 보장이
  명시되지 않았다. '직렬화 표현'은 표현만 다루고 봉투 신뢰 경계를 다루지 않는다.
- **판정 근거**: 인가 클레임을 "지속되는 이벤트/커맨드 봉투"에 실어 전파할 때 그 봉투의 무결성(서명·불변성)·재생 방어를 다룬 결정을 어느 ADR/RFC에서도 찾지 못했다. 인접 결정들은 모두 다른 신뢰
  경계를 다룬다. (1) RFC-020(인증 경계)은 게이트웨이가 클라이언트 신원 헤더(X-User-Id) strip + NetworkPolicy로 "게이트웨이만 앱 도달"을 강제한다 — 이는 요청 시점 HTTP
  헤더 전파의 신뢰 경계이지, 이벤트 스토어에 기록되고 Kafka/inbox로 흐르는 내부 봉투의 무결성이 아니다. (2) ADR-20/RFC-019의 "서명"은 JWT 토큰 자체의 위변조 방지이지 다운스트림
  봉투가 아니다. (3) ADR-10/DESIGN-003의 불변성(append-only)은 감사·스키마 진화용이며 인가 클레임 신뢰 경계를 대상으로 하지 않는다. 결정적으로 DESIGN-003 §127과
  ADR-22 확정 스키마의 봉투 추적메타는 correlation_id·causation_id·traceparent만 명시하고 신원·역할 클레임의 봉투 직렬화 표현·서명·재생 방어를 명시하지 않는다. 게다가
  RFC-003은 "재처리는 정상"(오래된 이벤트 재전달)을 설계 불변식으로 못박아 C24가 지목한 재생 시나리오를 오히려 실재화한다. C24는 D-014 §4.5의 자기-리뷰 항목(line 195)과 축자
  일치하며, DESIGN-014 §6은 "command/이벤트 봉투의 인가 클레임 직렬화 표현"만 후속으로 남기고 봉투 신뢰 경계는 열어두지 않아 미결이다.
- **인용**:
    - **DESIGN-014** (§4.5 신원·역할의 전파 (line 98)) — 게이트웨이/인증이 확정한 신원·역할이 command 메타에 실리고 공통 발행 경로에서 correlationId를 채우는
      자리에서 봉투에 함께 직렬화되며 다운스트림은 봉투에서 읽는다고 규정. 봉투의 무결성·재생 방어는 명시하지 않음 — 개념 자체가 이 신뢰 경계 문제의 출처.
    - **DESIGN-014** (§6 후속 설계 (line 131) 및 §Risks (line 195)) — §6은 '봉투의 인가 클레임 직렬화 표현'만 후속으로 남긴다. line 195 자기-리뷰가 '봉투
      위변조/재생 공격면'을 지적하며 '§6의 직렬화 표현은 표현만 다루지 봉투 신뢰 경계를 다루지 않는다'고 명시 — 미결임을 문서 스스로 인정.
    - **ADR-17** (결정 §역할은 클레임으로 전파 (line 36)) — 역할이 command/이벤트 메타에 실린 검증된 클레임으로 컨텍스트를 횡단, correlationId와 함께 봉투에 직렬화된다고
      확정. 봉투 무결성·재생 방어 조항 없음.
    - **RFC-020** (논점1 모델 A 의무 (line 90) · 결론 (line 94)) — 게이트웨이가 클라이언트 신원 헤더(X-User-Id) strip + NetworkPolicy로 '게이트웨이만
      앱 도달' 강제. 이는 요청 시점 HTTP 헤더 전파의 신뢰 경계이며, 지속·재전달되는 내부 이벤트 봉투의 무결성이 아니다.
    - **ADR-20** (V-2 무상태 서명 JWT (line 19)) — '서명이 위변조를 막는다'는 refresh/access JWT 토큰 자체에 대한 것. 다운스트림 이벤트/커맨드 봉투의 서명이 아니다.
    - **DESIGN-003** (§공통 추적 메타 충전 (line 127) · event_store 스키마 (line 63)) — 봉투 추적메타로
      correlationId·causationId·traceparent만 명시. 신원·역할 클레임의 봉투 직렬화·서명·불변성은 언급 없음.
    - **ADR-22** (정정 노트 확정 스키마 (line 17) · 봉투 (line 44)) — 확정 event_store 봉투 컬럼은
      correlation_id·causation_id·traceparent. 인가 클레임의 봉투 무결성 보장 조항 부재.
    - **RFC-003** (논점1 결론 (line 87) · line 23) — '재처리는 정상' — 같은(오래된) 이벤트가 두 번 올 수 있음을 설계 불변식으로 채택. C24가 지목한 '오래된 봉투 재생'
      시나리오를 실재화하나 봉투 위변조·재생에 대한 무결성 방어는 다루지 않음.
    - **ADR-10** (§불변 규칙 (line 91) · 채택 근거 (line 99)) — append-only·불변성은 감사 원본 보존·스키마 진화 정합성 목적. 인가 클레임 봉투의 위변조·재기록 신뢰
      경계를 대상으로 하지 않음.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'open' + 심각도 MED 기준 자동도출 — 검토 필요

### C33 — 추적 3필드 의무화 저장·전송 비용과 SLI 카탈로그 이중 계측·AOP 스코프 경계 미정의

`MED` · 판정 **open** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-011 §4.3, D-011 §4.6, D-011 §4.5
- **설명**: correlationId/causationId/traceparent를 매 이벤트·헤더·행에 강제하면 Zero Payload 이벤트에서 메타데이터가 페이로드보다 커지는 역전이 생기나 저장·전송 비용이
  저울에 없다. 카탈로그와 RFC-007 SLI가 프로젝션 지연·lag·Outbox 적체를 양쪽에서 같은 현상으로 재는 이중 계측·대시보드 중복 위험이 봉합되지 않았다. AOP 스코프 주입도 표준 키 집합·적용
  경계를 미뤄 불균일과 Baggage PII 유출 표면이 남는다.
- **판정 근거**: C33의 세 갈래 모두 "메커니즘"은 RFC-008/DESIGN-011에서 결정됐으나 concern이 지목한 구체 gap은 닫히지 않았다. (1) 저장·전송 비용 저울: 3필드(
  correlationId/causationId/traceparent) 의무화는 RFC-008 논점 3·4, DESIGN-011 §4.7에서 확정됐지만, Zero Payload 이벤트에서 메타데이터>페이로드 역전과
  event_store 매 행·매 Kafka 헤더 비용을 저울에 올린 문서가 RFC-008·RFC-004·ADR-05·DESIGN-011 §7 Risks 어디에도 없다 — DESIGN-011 line 234가
  스스로 "문서가 저울에 올리지 않는다"고 자인. 완전 open. (2) SLI·카탈로그 이중 계측: RFC-008 논점 6과 DESIGN-011 §4.6/line 162가 "SLI=사용자 체감, 카탈로그=내부
  파이프라인 건강"으로 층 분리 원칙만 세우고, 프로젝션 지연·lag·Outbox 적체를 양쪽에서 재는 실제 경계 조정(메트릭 목록·라벨 카디널리티·RFC-007 SLI 경계)은 Design/측정으로 명시 위임.
  RFC-007 line 69도 RFC-008로 되던짐 — 원칙만, 봉합 미완. (3) AOP 스코프 경계·Baggage PII: RFC-008 논점 5가 AOP 자동 주입+Baggage+MDC 투영 메커니즘은
  결정했으나 "표준 스코프 키 집합·아스펙트 적용 경계·Baggage 전파 범위는 Design 구체화"로 명시 이월(line 149, DESIGN-011 §4.5 line 148). Baggage PII 유출
  표면은 DESIGN-011 Weakness line 236에서 제기만 되고 해소한 문서 없음. 세 갈래 다 결정 문서가 gap을 인정·이월하거나 아예 다루지 않음 → open.
- **인용**:
    - **DESIGN-011-observability** (§Weakness (line 234)) — 3필드 의무화가 Zero Payload 이벤트에서 '메타데이터가 페이로드보다 커지는 역전'을 낳는데 '이
      저장·전송 비용과 카디널리티를 문서가 저울에 올리지 않는다'고 문서 스스로 자인 — 비용 결정 부재를 명시.
    - **DESIGN-011-observability** (§7 Risks & Mitigations (lines 196-200)) — correlationId 전파 누락·재처리 추적 끊김·메트릭 파편화·백엔드
      미배포 리스크는 나열하나, 3필드 저장·전송 비용/메타>페이로드 역전 리스크는 목록에 없음 — 비용 축이 결정 저울에 없음.
    - **RFC-008-observability** (논점 3·4 결론 (lines 129,139) + 결정표 4 (line 170)) — AbstractEvent 추적 메타=correlationId(필수)
      ·causationId·traceparent 확정 및 공통 발행 경로 자동 충전 — 3필드 의무화는 결정됐으나 그 저장·전송 비용은 논의 없음.
    - **RFC-008-observability** (논점 6 결론 (line 159) + 결정표 6 (line 172)) — '프로젝션 지연·Outbox 적체·PoisonMessage·consumer lag
      카탈로그 고정 + SLI와 층 분리' 결정하나, '구체 메트릭 목록·라벨 카디널리티와 RFC-007 SLI 경계는 Design'으로 명시 위임 — 이중 계측 봉합은 미완.
    - **DESIGN-011-observability** (§4.6 (line 162)) — SLI(사용자 체감) vs 카탈로그(내부 파이프라인 건강) 층 분리 원칙만 세우고 '같은 현상을 두 이름으로 재면
      혼선', 구체 목록·라벨 카디널리티는 RFC-007 SLI 경계와 맞춰 Design에서 다듬는다고 이월.
    - **RFC-007-deployment-infra-ops** (라인 69) — 'readiness 신호·핵심 SLI·메트릭 카탈로그·알람 임계 → RFC-008-observability'로 되던짐. 본
      RFC는 배포 측 hook만 — SLI/카탈로그 경계 조정 주체가 RFC-008이며 RFC-007에서 봉합 안 함.
    - **RFC-008-observability** (논점 5 결론 (line 149) + 결정표 5 (line 171)) — 'AOP 도메인 경계 스코프 키 자동 주입 + Baggage 전파 후 MDC 투영'
      메커니즘은 결정하나 '어떤 스코프 키를 표준으로 넣을지·아스펙트를 어느 경계마다 걸지·Baggage 전파 범위는 Design에서 구체화'로 명시 이월 — 표준 키 집합·적용 경계 미정.
    - **DESIGN-011-observability** (§4.5 (line 148) + §Weakness (line 236)) — 표준 스코프 키 집합·아스펙트 적용 경계·OTel Baggage 전파
      범위는 '구현 사이클에서 구체화'로 이월; Weakness 236은 경계 정의 공백에 따른 불균일과 'Baggage가 페이로드로 전 하류 전파되어 카디널리티·PII 유출 표면을 넓힌다'는 문제를 제기만
      하고 해소 문서 없음.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'open' + 심각도 MED 기준 자동도출 — 검토 필요

### C01 — Zero Payload가 command DB 역참조를 강제해 CQRS 유일접점 불변식을 무너뜨린다

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** · 우선 MED

- **문서/항목**: D-001 §4.3, D-003 §4.4, D-008 §4.12
- **설명**: 불변식은 command↔query 접점을 이벤트로 못박지만 Zero Payload는 식별자만 발행해 컨슈머/projector가 최신 상태를 command 측에서 역조회해야 한다. 이는 CQRS가
  없애려던 읽기-쓰기 결합을 뒷문으로 되살리고 command 부하가 read 팬아웃에 비례해 늘게 만든다. ES 컨텍스트에서는 이 '최신 상태 조회'가 곧 리플레이라 비용이 근본적으로 달라진다. 조회 경로와
  페이로드 정책(ES/비-ES 분기)이 정의되지 않았다.
- **판정 근거**: C01은 두 갈래로 나뉜다. (1) 핵심 두려움 — "Zero Payload가 컨슈머/projector에게 command DB 역참조를 강제해 CQRS 읽기-쓰기 결합을 뒷문으로 되살린다" —
  는 이미 명시적으로 기각됐다. ADR-04가 바로 그 메커니즘(query가 command DB 직접 조회, Option C)을 검토 대상에 올려 "스키마 결합 안티패턴, ES엔 불가능"으로 배제했고,
  RFC-002(§1·§109)는 lookup 실현을 projection ∨ published-subscription 두 async 로컬 카피로만 한정하며 "동기 cross-context fetch 금지"를
  못박았다. 즉 컨슈머가 "최신 상태를 command 측에서 역조회"하는 경로 자체가 아키텍처적으로 금지돼 있어, 부하가 read 팬아웃에 비례한다는 전제가 성립하지 않는다. RFC-021(§105·§111)은
  여러 애그리거트 상대시점에 의존하는 point-in-time 파생 사실을 "생산 시점에 페이로드로 박제"해 프로젝션이 cross-stream 재구성(=리플레이성 역조회)을 하지 않게 하여 이 우려를 한 번 더
  닫는다. (2) 그러나 C01이 마지막에 지목한 "페이로드 정책(thin/fat) 및 ES/비-ES 분기 정의"는 실제로 열려 있다. RFC-003이 §71·§162~166에서 "통합 이벤트라는 경계만 확정,
  페이로드의 모양(thin/fat·직렬화·버저닝)은 별도 RFC로 분리"라고 명시적으로 미뤘는데, 그 thin/fat 리치니스 결정은 끝내 어느 RFC에도 착지하지 않았다(RFC-022=스키마 진화·업캐스팅,
  RFC-023=생산자·소비자 wire 계약 테스트로, 둘 다 '모양 합의'이지 '얼마나 담느냐'의 thin/fat 판단이 아니다). 따라서 불변식 붕괴라는 메커니즘은 닫혔고, 명시적 페이로드 리치니스 정책은
  여전히 미결 → partially-decided.
- **인용**:
    - **ADR-04** (04.read-model-projection-and-replica.md §Option C·§34-36·§52) — 'query가 command DB(또는 read replica) 직접
      조회'(Option C)를 명시 검토 후 '스키마 결합 안티패턴, ES엔 불가능'으로 배제. 경량 lookup 프로젝션도 'command DB 직접 조회·replica 읽기 금지', query는 query
      DB projection만 읽어 command 스키마 의존 0. 컨슈머의 command 측 역참조를 정면 기각한다.
    - **RFC-002** (read-model-consistency §논점1 §96·§109·§111·결정표#1) — lookup 실현 = projection ∨ published-subscription
      둘뿐(둘 다 async 로컬 카피), '동기 cross-context fetch 금지'. '조회 시점에 원본을 동기 호출해 가져오면 안 된다 — 읽기 경로에 cross-context 호출을 붙여 CQRS가
      떼어내려던 런타임 결합을 다시 들인다'라고 우려 그대로 반박. 즉 read 팬아웃 비례 command 부하 경로가 금지됨.
    - **RFC-021** (event-identity §논점2 §105·§111) — 여러 애그리거트 상대시점 의존 파생 사실(point-in-time)은 '생산 시점에 이벤트 페이로드로 박아 넣어'
      프로젝션이 cross-stream 순서를 재구성(=리플레이 기반 역조회)하지 않게 한다. ES에서의 '최신 상태 리플레이 조회' 필요를 페이로드 박제로 제거.
    - **RFC-003** (messaging-delivery §71·§162~166·§별도 RFc로 분리) — '통합 이벤트라는 경계만 확정(내부 도메인 이벤트와 분리). 페이로드의 모양(
      thin/fat·직렬화·스키마 버저닝)은 별도 RFC로 분리.' — 페이로드 리치니스 정책을 명시적으로 미결로 남김. 이 별도 RFC의 thin/fat 결정은 끝내 착지하지 않음(미결 gap의 근거).
    - **ADR-10 / ADR-09 / ADR-11** (10.event-schema-evolution §12; 09.event-ordering §41·§63; 11.es-pii §31-32·§69) —
      Zero Payload는 '식별자 중심·컨슈머가 최신 조회'로 서술되고 자연 멱등 upsert의 근거로만 쓰인다. ES/비-ES 진화 충격 차이는 언급되나(비-ES는 충격 작음), thin/fat
      리치니스를 '정책'으로 확정하거나 ES/비-ES 페이로드 분기를 명문화한 결정은 어디에도 없음 — 원칙 계승만 있고 정책 채택은 없음.
- **처분안(제안)**: accept-new-rfc → *RFC-024 — 통합 이벤트 페이로드 리치니스 정책 (thin/fat 기준·ES/비-ES 분기)*
    - 근거: 불변식 붕괴 메커니즘(컨슈머가 command DB를 역참조) 자체는 이미 닫혀 있다 — ADR-04가 Option C(command DB/replica 직접 조회)를 배제하고, RFC-002가 동기
      cross-context fetch를 금지(lookup은 projection·published-subscription 비동기 로컬 카피만)하며, RFC-021이 point-in-time 파생 사실을 생산
      시점 페이로드로 박제하므로 read 팬아웃 비례 부하 전제가 성립하지 않는다. 그러나 RFC-003 §71·§162~166이 명시적으로 미룬 "페이로드 리치니스(thin/fat) 및 ES/비-ES 분기"
      정책은 어느 RFC에도 착지하지 않았다(RFC-022=업캐스팅, RFC-023=wire 계약으로 둘 다 '모양'이지 '얼마나 담느냐'가 아님). 메커니즘은 닫혔고 정책만 열려 있으므로, 좁은 토픽 RFC로
      그 미결 gap만 착지시키는 것이 프로젝트의 topical-RFC 규율에 맞다.

### C08 — 다중 소스·교차 스트림 프로젝션의 순서·원자성이 부분 갱신을 정상 동작으로 만든다

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** · 우선 MED

- **문서/항목**: D-004 §4.6, D-001 §4.1, D-004 §4.2
- **설명**: read model 한 행이 두 스트림(예: 예약+식당)으로 갱신되는데 한 이벤트=한 트랜잭션+오프셋 커밋 규칙 아래서는 한쪽만 반영된 부분 갱신이 정상이 된다. 두 소스가 하나의 Kafka로
  합류할 때 컨텍스트 간 순서와 at-least-once 중복을 누가 정규화하는지, 어느 시점 스냅샷을 보여줄지가 침묵된다. 프로젝션 쓰기가 프라이머리 하나에 집중돼 레플리카 확장으로도 소비 병목을 못 가린다.
- **판정 근거**: 순서·원자성·중복정규화의 핵심은 이미 결정됨: ADR-22/RFC-021이 '교차-스트림 적용 순서는 정확성 요구 아님(멱등 upsert가 최종수렴/LWW)'을 명시하고,
  point-in-time 파생 사실은 페이로드 박제로, 진짜 교차 불변식은 사가로 가른다. ADR-09가 '두 소스가 한 Kafka로 합류'의 중복을 event_id 기반 멱등 컨슈머/inbox가 정규화한다고
  확정. 따라서 '부분 갱신이 정상 동작'은 침묵이 아니라 의도된 사양(최종 일관성, RFC-002)이다. 다만 두 잔여 갭이 남아 partially: (1) '어느 시점 스냅샷을 보여주나'는
  point-in-time 파생 사실 외의 일반 교차-스트림 읽기 스냅샷 정책이 명시 read 정책으로 못 박히지 않고 최종 일관성 수용으로 대체됨. (2) '프로젝션 쓰기가 프라이머리에 집중→레플리카로 병목 못
  가림'은 competing consumers로 부분 답이 있으나, DESIGN-004 §188과 DESIGN-008 §268이 '인스턴스 분할 금지'+'파티션 수=계약'으로 탈출구를 닫은 것을 문서 스스로 미해결
  약점(후속 검토 대상)으로 표기. C08 문장 자체가 DESIGN-004 Weakness 절에서 그대로 온 것이라, 설계 문서가 이를 open item으로 명시 보류 중.
- **인용**:
    - **ADR-22** (§정확성 불변식 (line 56-60) + 정정노트 line 10-23) — 프로젝터 정확성 = per-aggregate 순서(파티션 키) + 멱등 upsert +
      per-aggregate 버전 가드. '교차-애그리거트 적용 순서는 정확성 의존이 아니다(멱등이 흡수)'. 즉 두 스트림이 한 read model 행을 갱신할 때 부분 갱신은 최종 수렴하는 정상 상태이며
      순서는 정확성 요구가 아니라고 명시 결정. 진짜 교차 순서/불변식은 사가.
    - **RFC-021** (논점2 (line 105, 111)) — 핵심 예외를 못 박음: '여러 애그리거트의 상대 시점에 의존하는 파생 사실(point-in-time)'은 리플레이 interleaving에
      따라 값이 달라질 수 있으므로 '생산 시점에 이벤트 페이로드로 박아 넣어 프로젝션이 cross-stream 순서를 재구성하지 않게 한다'. '어느 시점 스냅샷'류 파생 사실의 정합성을 이 규칙으로 해결.
      진짜 교차 불변식 강제는 사가.
    - **ADR-09** (결정사항 순서/전달 (line 54-64), 컨슈머그룹·백프레셔 (line 70-72)) — 파티션 키=aggregate_id로 애그리거트별 순서, at-least-once+멱등
      컨슈머/inbox(event_id 키, read model 갱신과 한 트랜잭션)로 effectively-once. 하나의 Kafka로 합류하는 중복 정규화 주체=멱등 컨슈머. '전역 순서는 보장하지
      않는다', 교차 애그리거트 순서 필요 시 saga. 소비 확장=같은 그룹 내 competing consumers, 병렬 상한=파티션 수. consumer lag=읽기 신선도 지표.
    - **RFC-002** (논점2 결론 (line 118-126), 결정 요약 #2) — '방금 쓴 걸 바로 읽으면 아직 없을 수 있다'를 버그 아닌 기본 사양(최종 일관성)으로 못 박음. 부분/지연 갱신을
      정상으로 수용하는 정책 근거. read-your-writes는 증명된 화면만 예외.
    - **DESIGN-004** (§4.6 트랜잭션 경계 (line 110-112) + Weakness §185, §188) — '한 이벤트=한 트랜잭션+오프셋 커밋' 규칙을 명시. 그러나 문서 자신의
      Weakness 절이 C08과 동일 문장으로, 다중 소스 프로젝션의 순서·원자성·'어느 시점 스냅샷'을 §4.6이 다루지 않는다고 인정하고 '후속 검토 대상'으로 남김(§185). 또한 §188은 '
      인스턴스 분할 안 함'+단일 프라이머리 프로젝션 쓰기가 소비 병목의 탈출구를 미리 닫았다고 자기 지적 — 미해결 약점.
    - **DESIGN-008** (§4.4 competing consumers (line 85-89), §138 백프레셔, Weakness §268) — 프로젝터 소비 확장은 레플리카가 아니라 competing
      consumers(상한=파티션 수), 백프레셔는 lag로 흡수. 그러나 파티션 수=순서 계약이라 증설 유일 해법이 무중단 토픽 마이그레이션(고비용)이며 §268 Weakness가 이를 '초기 실수의 벌칙을
      최대치로 키운 설계'로 지적 — 소비 병목 잔여 리스크.
- **처분안(제안)**: accept-new-rfc → *RFC: 프로젝션 쓰기 처리량 확장 — 파티션 계약과 단일 프라이머리 병목의 탈출구*
    - 근거: C08의 핵심(순서·원자성·중복정규화·부분 갱신=정상)은 ADR-22/RFC-021의 '교차-스트림 순서는 정확성 요구 아님(멱등 upsert LWW 수렴)+point-in-time은 페이로드
      박제', ADR-09의 event_id 멱등 컨슈머/inbox, RFC-002의 최종 일관성 기본 사양으로 이미 의도된 사양이라 그 부분은 침묵이 아니다. 그러나 DESIGN-004 §188과
      DESIGN-008 §268이 스스로 미해결 약점으로 표기한 '단일 프라이머리 프로젝션 쓰기 + 인스턴스 분할 금지 + 파티션 수=계약'이 소비 병목의 탈출구를 미리 닫은 문제는 competing
      consumers로 부분 답에 그쳐 실질적·미결이며, 파티션 초기 추정 실패 시 유일 해법이 고비용 토픽 마이그레이션이라 결정 필요. '어느 시점 스냅샷' 갭은 최종 일관성 수용+point-in-time
      박제로 대부분 흡수되므로 별도 RFC보다는 이 RFC 또는 후속 관측 RFC에서 흡수 가능하고, 우선순위는 실트래픽 부재로 MED로 둔다.

### C14 — read-your-writes를 Non-Goal로 미뤘으나 핵심 여정(202 취소·예약 직후 조회)이 이미 예외 후보

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-004 §4.7, D-013 §4.2
- **설명**: 예약 확정 직후 내 예약 목록·202 취소 결과 확인은 부수 엣지가 아니라 핵심 여정인데, 나중에 고를 예외 수단(동기 프로젝션/버전 토큰/command 직접 읽기)이 오히려 프로젝션
  파이프라인·트랜잭션 경계를 거꾸로 제약한다. 202를 확정하면서 결과 조회 채널을 남의 문서에 위임해 계약의 절반을 비웠고, 폴링만 남으면 취소마다 수백ms~수초 왕복이 UX 기본값이 된다. 나중에 못 고를 것을
  나중에 고르겠다고 선언한 셈.
- **판정 근거**: 정책 골격은 결정됐다. RFC-012/DESIGN-013 §4.2가 "command 기본 응답 = 즉시 202"를 확정했고, RFC-002 논점 2/DESIGN-004 §4.7이 "기본은 최종
  일관성, read-your-writes는 증명된 화면만 예외, 수단((b)동기 프로젝션/(c)버전 토큰/(d)command 직접 읽기)은 화면별로 그때 선택"이라는 정책을 세웠다. 그러나 concern이 찌르는
  정확한 지점 — (1) '예약 확정 직후 내 예약 목록·202 취소 결과 확인'이 부수 엣지가 아니라 핵심 여정이라 이미 예외 후보인데, (2) 예외 수단 선택과 202 결과 조회 채널(폴링/SSE/웹소켓)을
  신설 예정 ADR '읽기 신선도 예외 정책'과 구현 사이클로 미뤄, 그 뒤늦은 선택이 프로젝션 파이프라인·트랜잭션 경계를 거꾸로 제약하고 폴링만 남으면 취소마다 왕복이 UX 기본값이 될 위험 — 은 미해결이다.
  결정적으로 이 concern은 DESIGN-004 §Weakness(184행)와 DESIGN-013 §Weakness(192행)에 거의 그대로 적힌 저자 자신의 Devil's-Advocate 노트다. 즉 문서가
  이 긴장을 인지·기록했으나 해소하지 않았다. RFC-002는 "후보가 나오면 여기에 적는다"고 문을 열어뒀지만 실제로 후보를 승격하지 않았고, 예고된 ADR '읽기 신선도 예외 정책'은 docs/v2/adr에
  존재하지 않는다(21개 중 없음). 따라서 정책은 있으나 핵심 여정에 대한 결정 공백이 남은 partially-decided.
- **인용**:
    - **RFC-002** (논점 2 / 결정 요약 #2 (line 113-126, 175)) — read-your-writes 기본 미보장, 예외는 증명된 화면만 (b)동기 프로젝션/(c)버전 토큰/(d)
      command DB 직접 읽기 중 택일; '필요 시 신규 ADR 읽기 신선도 예외 정책'. line 126에 '예약 확정 직후 내 예약 목록처럼 명백히 즉시 반영이 필요한 화면이 이미 있다면 RFC
      단계에서 예외로 인정해도 된다 — 후보가 나오면 여기에 적는다'며 문만 열고 실제 승격은 안 함.
    - **DESIGN-004** (§3 Non-Goal (line 31) / §4.7 (line 116-119)) — §3은 read-your-writes 구현 상세를 '화면별 증명 후 별도 ADR로 승격'으로
      미룸. §4.7은 예외 수단(동기 프로젝션·버전 토큰·command DB 직접 읽기)을 기본값으로 안 깔고 증명된 화면만 승인, '예약 확정 직후 내 예약 목록처럼 명백한 후보가 나오면 신규 ADR 읽기
      신선도 예외 정책으로 승격'이라 명시하나 승격 미수행.
    - **DESIGN-004** (§Weakness (line 184)) — concern과 거의 동일: 'read-your-writes를 Non-Goal로 밀어낸 결과가 §4.7과 충돌 — 방금 예약한 걸
      바로 못 본다는 핵심 플로우의 신선도 정책을 구현 사이클에 떠넘기면 예외 수단 선택이 프로젝션 파이프라인·트랜잭션 경계를 거꾸로 제약한다. 나중에 고를 수 없는 걸 나중에 고르겠다고 선언한 셈.' 저자가
      인지·기록했으나 미해결.
    - **RFC-012** (§Goal/Non-goal (line 71-73) / 결정 요약 #2 (line 98,154)) — command 기본 응답 = 즉시 202 Accepted 확정. 단 '202 결과
      조회 메커니즘의 구체(폴링/SSE/웹소켓)'는 Non-goal로, RFC-002 read-your-writes 결론에 종속시켜 Design으로 미룸.
    - **DESIGN-013** (§4.2 (line 62-64) / §3 Non-Goal (line 40) / §6 (line 136)) — 202 채택은 여기서 확정하나, 202를 받은 클라이언트의 자기
      쓰기 확인 채널(폴링/SSE/웹소켓)과 동기 거절 vs 202 경계는 DESIGN-004 read-your-writes 결론·구현 사이클로 종속·이관. 계약의 절반이 비어 있음.
    - **DESIGN-013** (§Weakness (line 192)) — concern과 거의 동일: '202 기본인데 결과 조회 채널이 통째로 미정 — 동기 응답을 버린다는 되돌리기 어려운 결정은 확정하고
      결과 확인 방법은 열어둔 상태. read-your-writes 결론이 폴링만 실용적으로 나면 취소마다 수백ms~수초 폴링 왕복이 UX 기본값이 된다 — 이 종속의 최악 케이스를 계약이 흡수했다고 볼 근거가
      없다.'
    - **ADR-04** (미결정 사항 (line 55)) — '프로젝션 지연 허용치, 동기 프로젝션이 필요한 예외 화면'을 명시적 미결로 남김 — 예외 화면 목록이 ADR 차원에서도 미확정.
    - **RFC-INDEX** (docs/v2/adr 파일 목록 대조 (line 23)) — 예고된 신규 ADR '읽기 신선도 예외 정책'이 21개 ADR 파일 중 존재하지 않음(
      신선도/freshness/read-your 매치 0). RFC-002는 '🏷 합의' 상태이나 하류 ADR 미생성으로 결정 공백 확인.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C15 — 9개 컨텍스트 수동 도메인↔JPA 매핑의 반복 비용과 데이터 정합성 결함 위험 과소평가

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-002 §4.7, D-005 §4.1
- **설명**: MapStruct류를 경계 흐림으로 기각하고 컨텍스트별 손 매핑을 정당한 대가로 선언하지만, 필드 변경 시 매핑 함수 수동 동기화가 필요하고 누락은 런타임/테스트에서만 잡힌다. 마이그레이션 맥락에선
  8개+ 컨텍스트 × (커맨드+프로젝션+genesis) 보일러플레이트가 되며 그 반복이 버그의 상당 원천이다. Risk 표는 생산성만 적고 매핑 누락 데이터 정합성 결함을 다루지 않는다.
- **판정 근거**: 매핑 방식 결정 자체는 ADR-07·RFC-010에서 명확히 닫혀 있다: 도메인↔JPA는 컨텍스트별 명시적 수동 매핑, MapStruct류 코드젠은 Kotlin 스택 마찰로, 공통 매퍼 추상은
  hexagonal 경계를 흐리는 틈으로 각각 비채택, 보일러플레이트는 "분리의 정당한 대가"로 선언. C15가 요약한 결정 내용은 문서와 정확히 일치한다. 그러나 C15의 핵심 비판(위험을 생산성/유지비로만
  다루고, 필드 변경 시 매핑 누락으로 인한 데이터 정합성 결함·조용한 누락 위험은 다루지 않는다)은 실제로 미해결이다. ADR-07 트레이드오프(49-51행)와 RFC-010 리스크 표(52행)·논점2(
  107-118행) 모두 하방을 "보일러플레이트/유지비"로만 프레이밍하고, 매핑 누락이 정합성 결함으로 이어지는 실패 모드나 그 탐지 장치(왕복 테스트 등)를 명시하지 않는다. ADR-14/RFC-009 테스트
  전략에도 도메인↔JPA 왕복·누락 검증 규율이 없고, RFC-013의 "정합성"은 V1↔V2 병행창 일관성이지 매핑 누락과 무관하다. RFC-013의 8개+ 컨텍스트 × (커맨드+프로젝션+genesis) 반복
  부담이 정합성 결함 원천이라는 각도도 다뤄지지 않는다. 따라서 방향은 결정, C15가 지적한 정합성-위험 갭은 열림.
- **인용**:
    - **ADR-07** (§트레이드오프 49-51행 (command-domain-jpa-separation.md)) — 수동 매핑 채택, MapStruct류·공통 매퍼 비채택 명시. 단점을 '매핑 비용'으로만
      적고 '보일러플레이트는 분리의 정당한 대가이지 제거할 결함이 아니다'로 마무리 — 데이터 정합성/매핑 누락 실패 모드는 언급 없음.
    - **ADR-07** (§미결정 사항 58행) — '컨텍스트가 크게 늘어 동일 패턴 반복이 과해지면' 유지비 관점만 다루고, 답은 국소 컨벤션이라 함. 정합성 결함 위험은 미포함.
    - **RFC-010** (논점2 107-118행 + 결정 표 52·162행) — MapStruct=Kotlin 스택 마찰, 공통 추상=경계 흐림으로 기각. 인정 트레이드오프를 '유지비가 눈에 띄게 커질 수
      있다'로만 기술 — 매핑 누락→정합성 결함·탐지 시점 문제는 다루지 않음.
    - **RFC-010** (리스크/결정 거리 표 49-56행) — '매핑 보일러플레이트' 행이 하방을 '분리의 대가로 변환 코드 발생'으로만 프레이밍. 정합성 결함 항목 부재 — C15가 지적한 바로 그 갭.
    - **ADR-14 / RFC-009** (testing-strategy.md / RFC-009 전문 grep) — 도메인↔JPA 매핑 왕복(round-trip)·누락 검증 규율에 대한 언급 없음 — '누락은
      런타임/테스트에서만 잡힌다'는 우려를 완화할 테스트 장치가 명시되지 않음.
    - **RFC-013** (§병행 정합성 44·54·115행) — '정합성'은 V1↔V2 병행창 단방향 동기 맥락이지 도메인↔JPA 매핑 누락 정합성과 무관. genesis/프로젝션 반복 매핑이 결함
      원천이라는 각도는 미포함.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C17 — event_store 무한 성장·스냅샷 재생성과 콜드 파티션 이관·업캐스팅 누적 비용의 충돌

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-003 §4.1, D-009 §4.2, D-009 §6.3
- **설명**: 스냅샷은 리플레이 이벤트 수만 줄일 뿐 append-only 테이블은 계속 커지는데 파티셔닝·아카이빙 전략이 부재하다. 스키마 변경 시 스냅샷 폐기-리플레이 재생성은 콜드(S3)로 이관된 옛
  이벤트를 핫 경로에서 강제 호출해 콜드 이관 전제를 깬다. 읽기 시 업캐스팅은 재구축마다 업캐스터 체인을 수백만 이벤트에 곱하나 p99 가드레일이 이 비용을 계산하지 않는다.
- **판정 근거**: C17은 세 갈래인데 결이 다르다. (1) "무한 성장인데 파티셔닝·아카이빙 전략 부재"는 이 전제 자체가 틀렸다 — RFC-004 논점4/5/6이 시간(생성월) 파티셔닝 + 핫/콜드 경계(
  도메인 종결상태+유예) + S3 콜드 이관을 명시 결정했고, ADR-05·DESIGN-009 §4/§6이 이를 반영한다. 이 부분은 already-decided. (2) "스냅샷 폐기-리플레이 재생성이 콜드로
  빠진 옛 이벤트를 핫 경로에서 강제 호출해 콜드 이관 전제를 깬다"는 미해결이다. RFC-004 논점8은 스냅샷 폐기 후 리플레이 재생성을 결정했지만, 그 재생성이 콜드 파티션과 충돌하는 상호작용은 결정문
  어디에도 없다 — 정확히 이 충돌이 DESIGN-009 §Weakness(라인 318)에 devil's-advocate 항목으로 올라와 있고 "이 상호작용이 문서에 없다"고 자인하며, 그 절 말미(라인 332)
  는 "결정을 뒤집지 않는다·후속 검토 대상"이라 명시한다. 즉 인지됐으나 미결. (3) "읽기 시 업캐스팅 누적 비용이 p99 가드레일에 미계상"도 미해결이다. DESIGN-009 §6.3 가드레일(재구성
  p99>50ms→k6)은 스냅샷+증분만 보고, DESIGN-009 §Weakness(라인 320)가 "p99>50ms 가드레일은 업캐스팅 누적 비용을 재구축 경로에서 계산하지 않았다"고 명시 자인한다.
  RFC-022는 업캐스터 카탈로그를 다루나 재구축 경로의 누적 비용 가드레일은 정하지 않는다. 결론: 성장·아카이빙 축은 결정됐으나 C17이 지목한 두 상호작용(재생성×콜드 강제 호출, 업캐스팅×p99 미계상)은
  문서가 스스로 gap으로 인정한 open 항목이라 partially-decided.
- **인용**:
    - **RFC-004** (논점4 무엇을 단위로 보존·파티셔닝 (결론)) — 보존·파티셔닝 단위 = 시간(생성월). 핫패스는 aggregate_id 인덱스, 콜드 이관은 옛 파티션 drop/export.
      성장·파티셔닝 전략이 '부재'라는 C17 전제를 반박한다.
    - **RFC-004** (논점5·논점6 (핫/콜드 경계·콜드 스토리지 결론)) — 핫/콜드 경계 = 도메인 종결상태+유예(예 +90일), 콜드 = S3(파티션 export→drop, 복원 시
      로드→리플레이). 아카이빙 전략이 실제로 결정돼 있음.
    - **ADR-05** (line 58 (경계 밖 항목)) — '이벤트 테이블 파티셔닝/아카이빙(데이터 증가 시)'을 후속 결정 항목으로 명시 — 이후 RFC-004가 이를 닫음.
    - **RFC-004** (논점8 스냅샷 포맷 변경 (결론)) — 스냅샷은 업캐스팅하지 않고 폐기 후 이벤트 리플레이로 재생성. 다만 이 재생성이 콜드로 빠진 옛 이벤트를 필요로 하는 경우의 상호작용은
      결정문에 없음.
    - **DESIGN-009** (§Weakness line 318 ('스냅샷 폐기 후 리플레이 재생성'이 콜드 파티션과 충돌)) — '장수 애그리거트의 스냅샷을 스키마 변경 후 폐기하면 재생성에 필요한 옛
      이벤트가 이미 콜드로 빠져 hot DB에 없을 수 있다 … 이 상호작용이 문서에 없다'고 자인. C17 두 번째 갈래가 미해결임을 문서 스스로 인정.
    - **DESIGN-009** (§6.3 리플레이 성능 가드레일 (line 210·216·217)) — 가드레일은 스냅샷+증분으로 리플레이 길이를 N 이하로 묶고 전체 리플레이는 배치·오프피크로 분리 —
      재구성 p99>50ms 시 k6 스윕으로 N 조정. 업캐스팅 누적 비용은 이 계산에 포함되지 않음.
    - **DESIGN-009** (§Weakness line 320 (읽기 시점 업캐스팅 반복 비용)) — '§6.3의 p99>50ms 가드레일은 스냅샷+증분만 보고 업캐스팅 누적 비용을 재구축 경로에서
      계산하지 않았다'고 명시 — C17 세 번째 갈래가 미해결임을 문서 스스로 인정.
    - **DESIGN-009** (§Weakness 말미 line 332) — '본 절은 리뷰용 반박 정리이며 문서의 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상.' → 위 두 tension이 결정으로
      닫히지 않은 open 상태임을 확정.
    - **RFC-022** (맥락 (line 31)·업캐스터 카탈로그 범위) — 업캐스터·스키마 레지스트리를 담당하지만 라이프사이클(리플레이·성장) 관심사는 RFC-004로 분리했다고 명시 — 업캐스팅 누적
      비용을 재구축 경로 p99 가드레일에 계상하는 결정은 없음.
    - **DESIGN-003** (§4.1 Weakness line 185) — '스냅샷은 리플레이만 줄이지 저장 성장은 안 줄인다 … 파티셔닝·아카이빙·콜드 스토리지 이관 전략이 부재'라는 C17의 문장이
      원래 D-003 리뷰 항목에서 유래 — 이후 RFC-004가 파티셔닝/콜드를 닫아 이 갈래는 해소됨.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C18 — 스냅샷 N값·reconciliation·업캐스팅 전략이 예약 도메인 실제 스트림 특성과 자기모순

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-009 §6.3, D-009 §6.3
- **설명**: N=100에서 예약은 스트림이 짧아 스냅샷이 거의 안 찍혀 스냅샷 인프라 투자가 무력화되고, 짧으면 seq 0 리플레이가 싸서 불필요, 길면 파티션 경계 문제가 있는 딜레마가 '의도'로 포장됐다.
  reconciliation은 seq 0 전체 리플레이로 정의되나 §6.3은 그것을 금지하는 가드레일이라 자기모순이고, 콜드 포함 여부로 신뢰도와 비용이 서로를 잠식한다. 표본률이 '운영 측정에 맡김'으로
  얼버무려졌다.
- **판정 근거**: C18의 세 갈래 모두 코퍼스에서 '정책/메커니즘 수준으로는 결정됨, 절대값·표본률은 명시적으로 측정 위임'이라 partially-decided다. (1) N=100 '의도' 프레이밍은 비판이
  아니라 RFC-004 논점1·DESIGN-009 §6.3에서 실제 채택된 결정이다. (2) reconciliation seq0 '자기모순'은 §6.3 가드레일이 핫패스 한정이고 reconciliation은
  배경/오프피크 배치로 분리돼 층위가 달라 진짜 모순이 아니다(콜드 포함 여부도 재구축 목적별 선택으로 명시). (3) 표본률 '얼버무림'은 RFC-004 논점3·§9.2·NG1에서 의도적으로 운영 측정에 위임한
  것으로, 재검토 트리거(p99>50ms→k6 스윕)까지 붙어 있다. 다만 C18이 짚은 핵심 긴장(스냅샷이 값싼 곳엔 불필요/필요한 곳엔 위험, reconciliation 신뢰도-비용 상호잠식)은
  §7·Weakness에서 문서가 스스로 '후속 검토'로 남겨 완전 해소는 아니다. 따라서 already-decided가 아니라 partially-decided.
- **인용**:
    - **RFC-004** (논점 1 (스냅샷 주기 N) — 라인 86·88·90, 요약표 라인 204) — N=100 기본 + aggregate_type별 오버라이드를 결정으로 확정. '예약 도메인은 스트림이
      짧아 100이면 스냅샷이 사실상 거의 안 찍히는데, 이건 단점이 아니라 의도다 — 짧은 스트림에 공격적으로 찍는 건 과최적화'라고 명시하고, 장수 애그리거트 섞임은 '타입별 N을 더 잘게, 비중은 측정으로
      확인'으로 위임. 즉 C18이 '의도로 포장됐다'고 비판하는 그 프레이밍이 실제 채택된 결정.
    - **RFC-004** (논점 3 (스냅샷-이벤트 정합성) — 라인 108·110·112·114, 요약표 라인 206) — 인라인 1회 + 배경 표본 2겹 검증, 불일치 시 폐기 후 리플레이 재생성 +
      알람으로 정합성 정책을 확정. '표본률과 주기의 구체 값은 운영 측정에 맡긴다'로 명시 위임 — C18이 '얼버무렸다'고 지적한 부분이 곧 의도적 측정-위임.
    - **DESIGN-009** (§6.3 리플레이 성능 가드레일 — 라인 216·217) — '핫 경로에서 seq 0 전체 리플레이가 일어나면 안 된다'는 가드레일이 핫 경로 한정임을 명시. 전체 리플레이(
      스냅샷 폐기/검증/프로젝션 재구축)는 '배치·오프피크 작업으로 분리', 콜드 포함 여부는 '재구축 목적에 따라 선택'. reconciliation은 배경 배치라 가드레일과 층위가 다름 — C18의 '
      자기모순'은 가드레일 범위(핫패스)를 놓친 독해이고, 문서는 이를 배치/오프피크로 분리해 해소.
    - **DESIGN-009** (§9.1 Glossary(reconciliation 정의) + §9.2 라인 289 + NG1 라인 35) — reconciliation을 'seq 0 전체 리플레이 결과와
      스냅샷+증분 결과 비교'로 정의하되 '배경 배치'로 규정. 표본률·주기는 '운영 측정에 맡김'(§9.2), N 절대값도 NG1으로 운영 측정·k6 스윕에 위임 — 결정된 것은 정책/메커니즘, 미룬 것은
      절대 수치.
    - **DESIGN-009** (§7 Risks 라인 252·253 + Weakness 세 번째·여섯 번째 불릿 라인 322·328) — 장수 애그리거트 파티션 경계·N 무력화
      딜레마·reconciliation 신뢰도vs비용 트레이드오프를 위험/반박 항목으로 문서가 스스로 열거하고 '후속 검토 대상'으로 남김 — 즉 C18이 제기한 긴장을 문서가 인지하되 완전 해소가 아닌
      측정-후-재검토로 처리.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C19 — 크립토 셰딩의 물리 잔존·백업 전파·at-rest 평문·DR 복구 완결성이 보장되지 않는다

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-016 §4.1, D-016 §4.1, D-016 §4.4, D-016 §4.3, D-016 §4.2, D-016 §4.4, D-009 §6.2, D-009 §6.2
- **설명**: 봉투 암호화를 미뤄 key_material이 평문 at-rest로 앉고 접근통제가 뚫리면 전 PII가 복호되며, InnoDB DELETE는 논리 삭제라 undo·페이지·SSD에 평문 키가 잔존한다.
  binlog·스냅샷·슬레이브 사본에 파기 키가 소급 전파된다는 메커니즘이 없고, 셰딩된 PII 의존 프로젝션은 재구축해도 빈 값이 되어 완전복구 서사와 상충한다. 오주입 PII를 물리 제거할 경로도, blind
  index 존재 사실 누출 대책도 없다.
- **판정 근거**: C19는 사실상 DESIGN-016 자신의 'Weakness(Devil's Advocate)' 절을 거의 축자 재기술한 것이고, 그 절은 문서가 '결정을 뒤집지 않는 후속 검토 대상'으로
  스스로 명시한 미해결 항목이다. 하위 6개 주장을 코퍼스에 대조하면 혼재 상태다. (a) DR 복구 vs 셰딩 완결성(빈 값)은 ADR-18이 '이벤트 스토어만 1급·파생물 재구축·키 시계 불역'으로 의도된
  결과로 조정 — 해결에 가깝다. (b) 키 백업이 셰딩 의미론을 깨지 않는다는 불변식은 ADR-18/RFC-005/RFC-017이 원칙 수준에서 결정. 그러나 (c) binlog·PITR·standby·어제
  덤프로의 파기 키 소급 전파 보장 메커니즘은 RFC-005·ADR-18이 명시적으로 'Design/운영 검증'으로 유보, (d) InnoDB DELETE의 undo/페이지/SSD 물리 잔존은 어느 문서도 다루지
  않고(완전 open), (e) 봉투암호화 유보로 인한 key_material 평문 at-rest는 ADR-11이 '졸업 경로'로 의식적 유보하며 위협을 접근통제에 의존, (f) 오주입 PII 물리제거 경로는
  ADR-18 §50이 '사례별 판단'으로 미해결, (g) blind index 사전공격·존재누출은 DESIGN-016 Weakness가 미해결로 표시. 따라서 일부는 결정, 다수 항목은 인지된 채 유보/공백 —
  partially-decided.
- **인용**:
    - **DESIGN-016-pii-security** (Weakness (Devil's Advocate 반박 포인트) 절 전체) — C19의 6개 하위 주장(백업 전파 보장 부재, InnoDB DELETE
      논리삭제·undo/페이지/SSD 잔존, 봉투암호화 유보로 key_material 평문 at-rest, 셰딩=이벤트 정정 완결성 미보장, blind index HMAC 사전공격+존재누출, 콜드의 핫 키
      종속)이 이 문서 자신의 Weakness 절에 거의 축자 그대로 이미 열거돼 있음. 문서는 이를 '리뷰용 반박 정리이며 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상'으로 명시 — 즉 인지된 미해결
      항목.
    - **ADR-11.es-pii-crypto-shredding** (§64 메커니즘·인용구, §99 미결정(TBD)) — key_material은 전용 키 테이블에 두고 봉투 암호화(마스터 키 래핑)는 '
      격리·규제 요구가 실재할 때 도입하는 졸업 경로'로 명시 유보. '테이블이 읽히면 전 PII가 복호 가능'을 스스로 인정하고 접근통제를 잠정 경계로 삼음. → C19의 '봉투암호화 유보=평문
      at-rest' 지적은 해결이 아니라 의식적 유보.
    - **RFC-005-pii-security** (논점1 결론 '이의 여지', 논점4 결론) — '별도 스키마는 논리적 분리일 뿐 물리적 분리가 아니므로 DB 유출 시 키와 암호문이 함께 노출될 수 있다'를
      명시. 키 백업이 셰딩 의미론을 깨지 않아야 한다는 원칙은 결정했으나 ''백업에서 키를 함께 무효화'가 실제 백업 도구로 구현 가능한지는 Design/운영에서 검증'이라며 전파 메커니즘을 유보 —
      C19의 '파기 키 소급 전파 메커니즘 부재'와 정확히 겹치며 미결로 남음.
    - **ADR-18.event-store-recovery-semantics** (§34 셰딩 복원 견딤, §31 파생물 best-effort, §50-51 트레이드오프) — 셰딩된 PII 의존 프로젝션이 복구
      후 빈 값이 되는 것은 결함이 아니라 의도된 결과로 조정됨 — '1급 보호는 이벤트 스토어만, 파생물은 재구축', '복원은 이벤트 시계는 되돌리되 키 시계는 안 되돌린다'. 이 하위 주장(DR 완결성 vs
      셰딩)은 사실상 해결. 그러나 §50 '절대 일어나선 안 됐던 PII 노출 이벤트는 사례별 판단 필요'(오주입 PII 물리제거 경로 부재)와 §51 '키 저장소 자체 유실 시 복원 경로 필요'는 열린
      항목으로 인정.
    - **RFC-017-disaster-recovery-event-store** (논의 §56-57, Goal §68) — 복원이 크립토 셰딩을 부활시키지 않는다는 불변식을 백업 설계의 1급 제약으로 잠금.
      다만 binlog→PITR/연속복제 구체 메커니즘은 ADR-13으로 위임 — 원칙은 결정, 매체 레벨 전파 보장은 미해결.
    - **ADR-13.db-hosting-and-read-write-topology** (§26-37 복제(binlog) 용도) — binlog는 HA(같은 모델 DB 이중화) 전용으로 결정. 그러나
      key_store 스키마가 이 binlog·standby 복제에 함께 실리는지, DELETE가 standby/PITR 재료에 소급 전파되는지는 다루지 않음 — C19의 'binlog·슬레이브 사본 소급
      전파' 메커니즘은 여전히 부재.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C20 — 콜드 이벤트의 핫 키 저장소 종속이 재해복구·키 수명주기·감사 요구와 충돌

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-016 §4.4, D-009 §4.2
- **설명**: 콜드(S3) 본문 복호에 항상 핫 키 저장소를 참조하게 해 콜드 데이터가 self-contained가 아니며, 키 저장소 유실 시 미파기 주체의 콜드 PII까지 영구 복호 불능이 된다. 수년 뒤
  재생 시 로테이션·마이그레이션을 거친 옛 key_id 조회 보장이 필요한데 무한보존 가정만 있다. as-of 조회 ops 한정이 감사·규제(비개발자 조회) 요구와 모순 소지가 있다.
- **판정 근거**: 셰딩 정합성 자체(콜드엔 암호문만·키는 핫 잔류·키 1회 삭제로 핫·콜드 동시 셰딩)는 RFC-005 논점7·ADR-11·D-016 §4.4·D-009 §2.2에서 확정됐다. 그러나 C20이
  제기하는 세 갈래 긴장은 모두 결정이 아니라 명시적 유보/미결로 남아 있다. (1) 재해복구/가용성 — 키 저장소 유실 시 미파기 주체 콜드 PII 영구 복호 불능은 RFC-017 논점3에서 "진짜 미결 논점"
  으로 못박고 이의여지에서 Design 검증으로 위임, ADR-18도 후속 항목으로 RFC-005에 위임. 완화책은 "키 별도 백업 정책"이라는 방향뿐이고 self-contained/복원 경로 설계는 없다. (2)
  키 수명주기 — ADR-11은 로테이션 정책과 콜드 키 셰딩 경로를 미해결로, RFC-005 논점4는 정책 방향만 잡고 수치는 별도 ADR로 위임. 수년 뒤 재생 시 로테이션·마이그레이션을 거친 옛 key_id
  조회 보장은 무결정. D-009 §6.5는 콜드 복호 결합도 정합을 TBD, NG5로 Non-Goal 처리. (3) as-of ops 한정 — temporal=ops/debug 한정(YAGNI, API 비노출)은
  D-009 §4.2에서 결정됐으나, 감사·규제(비개발자 조회) 요구와의 모순은 D-009 자체 Weakness(§326)와 D-016 Weakness(§195)에서 미해결 반박 포인트로 자기 지적됨. 즉 C20의
  세 갈래 모두 문서가 스스로 열어둔 gap이다.
- **인용**:
    - **DESIGN-016** (§4.4 콜드 스토리지의 셰딩 경로 + Weakness(§195)) — 콜드 본문 복호는 항상 핫 키 저장소 참조로 확정. 그러나 마지막 Weakness 항목이 C20을 그대로
      자기 지적: '콜드 데이터는 self-contained 아니며 키 저장소 유실 시 미파기 콜드 PII 영구 복호 불능(가용성 리스크)', '무한 보존 가정만 있을 뿐 로테이션·보존 수명과 정합이 열려
      있다'. 결정을 뒤집지 않는 후속 검토 대상으로 명시.
    - **DESIGN-009** (§6.5 콜드 이관 설계 정합 + Non-Goal NG5) — '콜드 본문 복호가 핫 키 저장소를 참조하는 결합도와 S3 이관 설계 정합은 구현 사이클에서 검증 — TBD.'
      NG5로 '콜드 이관 후 복호 결합도 세부 구현'을 Non-Goal 처리. 결합도 문제 자체가 미결로 위임됨.
    - **RFC-017** (논점 3 (복원이 셰딩된 PII 부활 안 하는가 — 키 유실 시 활성 유저 PII?)) — C20 가용성 프롱을 정면으로 인정: '셰딩하지 않은 활성 유저인데 재해로 키 저장소까지
      유실되면 암호문만 남아 영구 복호 불가 — 이것이 진짜 미결 논점이다.' 이의여지에서 '키 저장소 자체 유실 시 복원 경로는 RFC-005 키 백업 스코프와 함께 Design에서 검증'으로 명시 위임.
    - **ADR-18** (결과/후속·미해결 (line 51, 63)) — '키 저장소 자체가 유실되면 키 백업 무결성과 셰딩 정합성을 동시에 만족시키는 복원 경로가 필요(RFC-005 키 백업 스코프와
      함께)' — 미해결/후속 위임 항목으로 명시. 1급 보호 대상은 이벤트 스토어 한 곳이고 키 저장소 복원 경로는 결정되지 않음.
    - **RFC-005** (논점 4 (키 로테이션·백업·접근통제) + 논점 7) — 논점4: 정책 방향만(백업=셰딩 의미론 보존, 로테이션=셰딩 무충돌)이고 로테이션 주기·백업 보존 수치는 별도 ADR로
      위임. 논점7 이의여지: 콜드 복호 재생 시 핫 키 저장소 상시 참조 가능성·RFC-004 S3 이관과 충돌 여부는 Design에서 검증. 옛 key_id 조회 보장·무한 보존 정합은 무결정.
    - **ADR-11** (미해결·후속 (키 로테이션 정책; 콜드 이관 이벤트 키 셰딩 경로)) — '키 로테이션 정책, 키 자체의 백업·접근 통제'와 '콜드 스토리지로 이관된 이벤트의 키 셰딩 적용 경로'를
      미해결/후속으로 명시. 봉투 암호화는 졸업 경로로 유보 — 키 수명주기·정합 결정은 없음.
    - **DESIGN-009** (§4.2 Key Design Decisions + Weakness(§326)) — 'temporal 조회는 운영·디버깅 한정(YAGNI)' API 비노출로 결정. 그러나
      Weakness에서 자기 지적: '§1은 감사를 ES 가치로, §9.2는 종결+90일 감사·분쟁 윈도우를 두는데 분쟁·감사는 대개 비개발자(CS·법무·규제)가 그 시점 상태를 물어야 함 — ops 한정이면
      매번 개발자 수동 조회 의존. 감사·규제는 사전 준비 영역이라 요구 증명 후 대응이 늦을 수 있다.' as-of ops 한정과 감사 모순이 미해결로 남음.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C21 — YAGNI 비대칭·학습가치 정당화·트래픽 발생 시 재검토 경로 폐쇄

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-001 §4.3, D-006 §4.1·§5, D-003 §4.1
- **설명**: 프로토타입에 인프라는 YAGNI로 엄격히 자르면서 ES+리플레이+스냅샷+분산락이라는 최고난도 스택은 선제 도입하는 비대칭이 방어되지 않는다. '실트래픽 없어 낙관의 공짜 이득이 실재 안 함'은 대칭
  논변이라 비관 락을 정당화 못 하고 남는 근거는 학습가치뿐인데, §7이 '낙관 회귀 안 함'으로 못박아 트래픽 발생 시 재검토 경로를 스스로 닫았다. 전용 제품 미도입으로 리플레이·스냅샷·업캐스팅·동시성을 자체
  재발명하는 리스크가 규모로만 논증되고 구현 정확성 리스크로는 논증되지 않았다.
- **판정 근거**: 세 갈래로 나눠 판정. (1) YAGNI 비대칭 — RFC-001 §맥락(line 76)이 비대칭 긴장을 명시적으로 이름 붙이고 RFC 전체를 비용 트레이드오프로 프레이밍하며, ADR-02가
  ES에도 컷을 적용(선택적 ES). 그러나 'ES 스택 자체는 왜 도입하나'의 방어는 RFC-001 §6의 '학습가치'라는 단일 축에만 의존 — concern의 진단과 정확히 일치. (2) 학습가치·재검토
  폐쇄 — 학습가치 정당화는 ADR-16 line20에 명시돼 concern이 맞다. 그러나 '재검토 경로 폐쇄' 주장은 틀림: ADR-16 line63 '실트래픽 시 재검토 트리거', ADR-05 line59 '
  트래픽 증가 시 전환 기준'이 재검토 경로를 명시적으로 열어둠. '낙관 회귀 안 함'은 Redis 다운 폴백 의미론 고정일 뿐 전면 봉쇄가 아니라 concern이 과대 해석. (3) 구현 정확성 리스크 —
  ADR-05는 concern 지적대로 규모/코드책임으로만 논증하고 정확성 리스크를 build-vs-buy 요인으로 저울질하지 않음. ADR-14·RFC-009가 정확성을 테스트로 완화하나 이는 도입 결정 근거가
  아닌 사후 안전망. 종합: 비대칭·서브결정은 내려졌으나 concern이 겨냥한 학습가치 축 위에서 내려졌고, 재검토 경로는 오히려 열려 있어 concern의 핵심 전제 하나가 반증되며, 구현-정확성 리스크는
  트레이드오프에 미편입 — 따라서 부분 결정.
- **인용**:
    - **RFC-001** (§맥락 line 76 / 결정 요약 #6) — YAGNI 긴장을 명시적으로 이름 붙임: '세 목표를 전부 최대 강도로 밀면 트래픽도 없는 프로토타입에 과한 복잡도를 지불' — RFC
      전체를 '어디에 비용을 쓰고 어디서 아끼나'의 변주로 프레이밍. 즉 비대칭 자체는 인지·논의됨.
    - **RFC-001** (§논점 6 (line 171)) — ES 저장소를 직접 만드는 근거가 '학습 목적상 직접 만들어보는 쪽이 남는 게 많다'로 명시 — 이것이 concern이 지적하는 '남는 근거는
      학습가치뿐'과 정확히 일치. 즉 비대칭이 방어되는 유일한 축이 학습가치임을 문서가 스스로 드러냄.
    - **ADR-02** (결정사항/장점) — 선택적 ES — reservation·timetable·restaurant만 진짜 ES, lookup은 제외. ES 자체에도 YAGNI 컷이 적용됨(전면 ES
      기각). 다만 '왜 ES라는 스택 자체는 도입하는가'의 상위 비대칭은 여기서 다루지 않음.
    - **ADR-16** (§C-1 (line 20) · §트레이드오프 (line 63)) — 비관 락 정당화가 '트래픽 없는 학습 환경에서 비용은 명목상이며 분산 락 운영 학습가치가 상회'로 학습가치에 의존.
      동시에 line 63 '무경합 쓰기에도 락 비용 — 학습 환경선 수용, 실트래픽 시 재검토 트리거'로 트래픽 기반 재검토 경로를 명시적으로 열어둠 → concern의 '재검토 경로를 스스로 닫았다'는
      주장과 정면 배치.
    - **ADR-16** (§결정사항 L1′ (line 40) · RFC-014 논점1 결과) — '낙관으로 회귀하지 않는다'의 실제 범위는 Redis 다운 시 폴백이 낙관이 아니라 DB 비관 락이라는 뜻 —
      폴백 의미론 고정이지 트래픽 발생 시 낙관/비관 재검토 전면 봉쇄가 아님. concern이 이 문장을 '재검토 경로 폐쇄'로 확대 해석함.
    - **ADR-05** (§트레이드오프 (line 50-54) · 미결정 (line 59)) — 전용 제품 대신 직접 구현 리스크를 '스트림·스냅샷·동시성 직접 구현(코드 책임 증가)'·'초대규모 성능 한계'
      로만 논증 — concern 지적대로 규모 리스크에 편중. 구현 정확성(리플레이/업캐스팅/동시성을 틀리게 만들 위험)을 build-vs-buy 판단 요인으로 저울질하지 않음. line 59에서 '트래픽
      증가 시 전용 제품/CDC 전환 기준'은 미결로 남김(재검토 경로는 여기서도 열려 있음).
    - **ADR-14** (§Option B (line 33) · 한 줄 (line 38)) — 자체 구현 ES의 구현 정확성 리스크를 property-based(fold(events)==state)·리플레이
      수렴·경계 강제로 1급 테스트 범주화. 정확성 리스크가 '완화'는 되나, ADR-05의 build-vs-buy 결정 근거로 편입되지는 않음.
    - **RFC-009** (§라운드2 (line 53) / line 19) — 멱등·재구축·리플레이·업캐스팅 회귀 등 동적 분산 행위를 정적 테스트가 못 잡는다며 별도 게이트로 커버 — 자체 재발명한 ES의
      정확성 리스크를 테스트 전략으로 흡수하는 근거. 단 이는 사후 안전망이지 도입 결정의 정당화가 아님.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C22 — query 측 수평 권한상승·역할 게이트 단일지점 방어가 개발자 규율·엣지 단일점에만 의존

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-014 §4.3, D-014 §4.1
- **설명**: WHERE owner_id 스코프 술어가 '빠뜨릴 자리 없다'는 주장은 모든 핸들러가 명시적으로 붙였을 때만 참이라 새 핸들러 한 곳의 누락이 전량 유출을 만드는데 RLS·기본 스코프 인터셉터
  강제가 없다. 역할 게이트를 엣지에만 두고 defense-in-depth를 후속으로 미룬 채 Accepted라, 게이트웨이 우회 경로 하나면 역할 미검증 요청이 앱에 도달한다. 프레임워크 강제 없는 규율 의존이
  반복된다.
- **판정 근거**: 두 갈래 모두 문서가 인지·논의는 했으나 프레임워크 강제 장치는 명시적으로 구현 사이클로 미뤄 미결로 남았다. (1) query 스코프: ADR-17 Q-2가 "WHERE owner_id는
  쿼리의 구조적 일부라 빠뜨릴 자리가 없다"고 주장하지만 이는 핸들러별 명시 규율에 의존하는 진술이고, RLS나 기본 스코프 인터셉터 결정은 어디에도 없다. 강제라 부른 것은 '프로젝터가 스코프 키 컬럼을 채우는
  것'뿐이며, 그마저 ADR-17 미결정에 '프로젝터 강제 규칙을 ArchUnit/Konsist에 얹을지'로 구현 사이클에 위임됐다. 새 핸들러 누락→전량 유출을 막는 프레임워크 강제는 미결. (2) 역할
  게이트/defense-in-depth: ADR-17 미결정 첫 항목이 '역할 게이트(엣지)·소유권(앱) 이중화(defense-in-depth) 여부'를 명시적으로 구현 사이클로 미뤘다. 게이트웨이 우회 경로는
  RFC-020이 강하게 다뤄 모델 A의 의무로 '신원 헤더 strip + NetworkPolicy "게이트웨이만 앱 도달"'을 못박았으나(안 지키면 뚫린다고 명시), 그 강제 장치의 '구체 구현(strip
  규칙·NetworkPolicy/mTLS)'은 09-deployment-runtime로 위임돼 아직 확정된 강제물이 아니다. 즉 우려의 대상(엣지 단일점·프레임워크 강제 부재)이 문서상 인지·의무화까지는 갔으나
  defense-in-depth와 query 강제 인터셉터는 열려 있다.
- **인용**:
    - **ADR-17** (결정사항 §Q-2 query 측 = 쿼리 스코프 조건 (line 34) 및 장점 2 (line 42)) — 검증된 주체를 클레임에서 받아 WHERE로 내리며 '스코프가 쿼리의 구조적
      일부라 빠뜨릴 자리가 없다'고 주장 — 그러나 이는 각 핸들러가 명시적으로 WHERE를 붙였을 때만 참인 규율 의존 진술이고, RLS·기본 스코프 인터셉터 같은 프레임워크 강제는 결정하지 않음.
    - **ADR-17** (미결정 사항 및 추가 논의 (→ 구현 사이클), line 60) — '역할 게이트(엣지)·소유권(앱) 이중화(defense-in-depth) 여부 — RFC-007 토폴로지와' —
      역할 게이트 단일점 방어의 이중화를 명시적으로 미결·구현 사이클로 위임. 우려의 핵심(defense-in-depth 후속 미룸)이 문서상 그대로 열려 있음.
    - **ADR-17** (미결정 사항, line 63) — '컨텍스트별 프로젝션 스코프 키 컬럼 목록과 프로젝터 강제 규칙(14.testing-strategy ArchUnit/Konsist에 얹을지)' —
      query 스코프 성립을 위한 유일한 '강제' 후보인 프로젝터 스코프 키 채움조차 강제 규칙 확정을 구현 사이클로 미룸. 새 핸들러 WHERE 누락을 막는 강제는 다루지 않음.
    - **RFC-015** (결정 요약 #3 및 결과 (line 36, 44)) — query 소유권 = 'WHERE owner_id = :header_user_id' 쿼리 스코프로 확정하되, '웹 앱 인가의
      기본 패턴, 자명하므로 설계 메모로 충분'하다며 강제 장치 없이 규율 패턴으로 종결 — 프레임워크 강제 부재가 우려의 지점과 일치.
    - **RFC-020** (논점 1 결론 (line 90, 94) 및 결과 모델 A 의무 (line 156)) — 모델 A 의무로 '신원 헤더 strip + NetworkPolicy "게이트웨이만 앱
      도달" — 안 지키면 헤더 위조/우회로 뚫린다'를 명시. 게이트웨이 우회 경로 위험을 정면으로 인지하고 강제를 '의무'로 못박았으나, 그 강제 장치를 결정이 아닌 의무로만 남김.
    - **RFC-020** (Non-goal, line 74) — '모델 A 강제 장치의 구체 — 인입 신원 헤더 strip 규칙, "게이트웨이만 앱 도달" NetworkPolicy(또는 mTLS) 구현 →
      09-deployment-runtime' — 우회 차단 강제 장치의 구체 구현을 명시적으로 위임. 강제 존재는 확정됐으나 실 강제물은 아직 미확정.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C23 — 소유권 판단의 결과적 일관성 창이 조회 유출과 command 인가 우회를 동시에 열고, 도메인에 역할 어휘가 샌다

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-014 §4.4, D-014 §4.5, D-014 §4.2
- **설명**: 식당 양도의 일관성 창을 조회 유출로만 다뤘으나 소유권을 자기 컨텍스트 이벤트/상태로 판단하므로 command 측 소유권도 stale해 옛 주인이 취소·변경을 성사시킬 수 있다. 클레임 스냅샷
  stale 창(강등 후 토큰 수명만큼 예전 권한)의 완화가 미착수 백로그로 위임됐다. '주인 OR 충분권한 역할' 불변식은 애그리거트가 보안 역할을 알아야 해 '역할=엣지' 분리를 도메인 내부에서 위반한다.
- **판정 근거**: 세 하위 우려(C23) 모두 DESIGN-014에 이미 인지·기록돼 있으나 결정이 아니라 미결/백로그 위임 상태다. (1) 양도 일관성 창의 command 측 소유권 stale — 본문
  §4.4는 조회 유출만 명시했고, command 측 우회는 문서의 §Weakness(line 191)가 정확히 같은 표현으로 스스로 지적했을 뿐 해소책이 없다(민감도 분류·command 측 재확인은
  §6·ADR-17 미결). (2) 클레임 스냅샷 stale 창 — §4.5/§7에서 인지했으나 완화를 인프라 백로그 T-13으로 위임, 미착수임을 §Weakness(189)가 확인. (3) '주인 OR 충분권한
  역할' 불변식의 도메인 역할 어휘 누출 — §4.2에서 형태를 확정하면서 도메인이 보안 어휘를 알지 여부는 §6·ADR-17에서 명시적으로 미결. 즉 문제는 전부 '건드려졌으나(touched) 결정으로 닫히지
  않은' 상태 → partially-decided. ADR-17이 방향(역할=엣지/소유권=앱, 이벤트로 양도 모델링)은 정하되 이 세 완화의 구체안은 구현 사이클/백로그로 명시 위임했다.
- **인용**:
    - **DESIGN-014** (§4.4 프로젝션이 스코프 키를 1급 컬럼으로 (line 90)) — 식당 양도 일관성 창을 '조회 유출(옛 주인이 잠깐 더 본다)'로만 명시하고, 허용 범위를 자원 민감도
      분류(§6, 미결)로 넘김. command 측 stale 소유권은 본문에서 다루지 않음 — 오직 §Weakness에서만 지적됨.
    - **DESIGN-014** (§Weakness bullet 4 (line 191)) — 문서 스스로 반박: '소유권 이전의 일관성 창이 인가 우회 창과 동일 — 프로젝션이 결과적 일관성이면 이 창은 조회
      유출만이 아니라 command 측에서도 열린다. 애그리거트 소유권 불변식이 옛 주인을 여전히 주인으로 보는 동안 옛 주인이 취소·변경 command를 성사시킬 수 있다. 문서는 조회 유출만 언급하고
      command 측 stale 소유권은 다루지 않는다.' 문제를 식별했으나 해소는 없음.
    - **DESIGN-014** (§4.5 신원·역할 전파 (line 101) + §7 Risks (line 142)) — 클레임 역할을 토큰 발급 시점 스냅샷으로 두면 '강등 후 한 토큰 수명만큼
      stale'. 완화를 '인증 토큰 정책(인프라 백로그 T-13)'으로 위임. §Weakness bullet 3(line 189)은 이 완화책이 미착수 백로그에 있고 ADMIN 전역 강제 취소 같은 고위험
      행위조차 stale 창에 노출됨을 지적.
    - **DESIGN-014** (§4.2 (line 71) + §Weakness bullet 5 (line 193)) — 소유권 불변식을 '주인 본인 OR 충분 권한 역할' 형태로 확정. §Weakness는
      이것이 애그리거트가 ROLE_ADMIN 같은 보안 역할 어휘를 알게 해 '역할=엣지, 도메인은 자원 상태만' 분리를 도메인 내부에서 위반한다고 지적. 해소는 §6으로 미룸.
    - **DESIGN-014** (§6 후속으로 넘기는 설계 사항 (line 126, 129, 130)) — '도메인이 보안 역할 어휘를 직접 알지 vs 핸들러 플래그의 경계', '소유권 변경 이벤트 + 일관성
      창 옛 주인 노출 자원별 허용 범위(RFC-002 종속)', '민감도 분류(command 측 권위 상태 재확인 요구 자원)' 모두 명시적으로 후속(미결)로 위임.
    - **ADR-17** (트레이드오프·미결정 사항 (line 50-52, 61, 64)) — '양도 시 스코프 키 반영 지연 동안 인가가 옛 키로 이뤄질 수 있다(좁은 창은 남는다)', 클레임 강등
      stale은 'T-13·RFC-019', '주인 본인 OR 충분권한 역할'에서 도메인이 보안 역할을 알지 여부는 미결정, '옛 주인이 잠깐 더 보는 위험의 자원별 허용 범위·민감 자원 command 측
      재확인 분류'를 구현 사이클로 넘김.
    - **RFC-015** (상태 헤더 (line 3)) — ✅ 종결(2026-06-29), 하류 산출물 없음 — '표준 관행을 과잉 분석'으로 닫음. C23의 세부 완화(command 측 stale, 도메인
      역할 어휘)에 대한 결정을 담지 않음. 실질 설계는 DESIGN-014로 이관됨.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C25 — 무상태 refresh·재사용 탐지·단일 refresh 컬럼·denylist 포기가 세션 보안 기본기를 훼손

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-017 §4.3, D-017 §4.3, D-017 §4.3, D-017 §4.3
- **설명**: 서버 사본 없는 refresh는 탈취 하나로 만료까지 무제한 rotation이 가능한데 access TTL 값이 후속으로 미뤄져 노출 창 크기가 이 문서에서 결정되지 않고 디바이스 바인딩 보완책이
  전무하다. current_refresh_jti 재사용 탐지는 병렬 탭·다기기 동시 로그인 정상 경합에서 로그아웃 폭탄이 되고, 단일 jti 컬럼은 다중 디바이스 세션을 구조적으로 지원 못 한다. denylist
  포기를 'V1도 못 했으니'로 정당화하나 즉시 전역 무효화는 계정 탈취·강제 차단 대응의 기본기다.
- **판정 근거**: C25는 네 갈래(무제한 rotation·access TTL 미결·디바이스 바인딩 부재 / 재사용 탐지 오탐 폭탄 / 단일 jti의 다중 디바이스 미지원 / denylist 포기 정당화)를
  묶는데, 코퍼스는 이 중 일부만 명시적으로 결정했고 핵심 운영 공백은 미결로 남긴다.

결정된 것: (a) denylist 즉시 폐기 포기는 ADR-20이 D-2로 의도적 채택했고(V1 미구현+짧은 TTL 근거, 요구 입증 시 must-not-evict 부활 예외경로), 무상태 refresh가 만료까지
통제 불가라는 '탈취 하나로 무제한 rotation' 트레이드오프도 ADR-20 트레이드오프절에서 명시적으로 수용했다. (b) 재사용 탐지·강제 로그아웃을 current_refresh_jti 단일 컬럼으로 푸는
메커니즘은 RFC-019 논점3·결정요약#4·RFC-020에서 확정됐다.

미결로 남은 것(C25의 실질 공백과 정확히 겹침): (1) 노출 창의 실제 크기를 정하는 access TTL 구체 값은 RFC-019 Non-goal·ADR-20 미결정·D-017 §6에서 모두
design_doc(권한 스냅샷 stale 창)으로 후속 이관 — 이 문서에서 결정 안 됨. (2) 디바이스 바인딩·IP/UA 핀·토큰 지문 보완책은 세 문서 어디에도 언급 없음(전무). (3) 병렬 탭·다기기 정상
경합의 재사용 탐지 오탐 처리(유예·grace window·refresh 체이닝)와 병렬 /refresh 동시성은 D-017 §6·Risk표에서 '후속 사이클로 넘김'으로 미해결. (4) 단일 jti가 다중 디바이스
동시 세션을 구조적으로 못 받는 문제는 D-017 Weakness절이 '리스크로도 다루지 않았다'고 스스로 인정—어떤 ADR/RFC도 해소 안 함.

즉 C25가 지적한 공백들은 대부분 문서가 인지하되 후속으로 미룬 상태이고 일부(디바이스 바인딩·다중 디바이스)는 아예 미해결이라, resolved가 아니라 partial. (부수 관찰: ADR-20 결정절은 '
재사용 탐지 유지=기각'이라 적었으나 RFC-019/020/D-017은 이를 채택—문서 간 모순이 존재하나 이는 concern 해소가 아님.)

- **인용**:
    - **ADR-20** (결정사항 · 즉시 폐기 포기(D-2)) — denylist 즉시 폐기를 V2 기본에서 의도적으로 포기. 근거 ①must-not-evict 재도입 회피 ②V1도 즉시 폐기 못 함(잃을
      게 없음) ③짧은 access TTL로 대부분 로그아웃 덮임. 요구 입증 시 denylist 부활 예외경로 명시. → 'V1도 못 했으니'는 의도적 결정으로 확정된 부분.
    - **ADR-20** (트레이드오프 · 선택한 방식의 한계) — '무상태 refresh는 한 번 발급되면 만료까지 서버가 통제하지 못한다 — 도난된 refresh도 만료 전까지 유효(재사용 탐지 포기와 한
      몸)'. 무제한 rotation 노출을 명시 수용. 단 access TTL 구체 값은 미결정으로 이관.
    - **ADR-20** (미결정 사항 및 추가 논의) — 'refresh JWT 클레임 구성·TTL·서명 키와 access TTL 구체 값'을 authorization 스냅샷 stale 창과 한 몸으로 후속
      이관. 'rotation 정책 — 재사용 탐지 최종 포기 확인'도 미결. → 노출 창 크기가 이 문서에서 결정 안 됨(C25 핵심 지적 확인).
    - **RFC-019** (Non-goal) — 'refresh JWT 클레임 구성·TTL·access TTL의 구체 값'과 'rotation 정책의 만료 연장 방식·재사용 탐지 최종 확인'을
      design_doc으로 명시 이관. access TTL·재사용 탐지 마감은 미결.
    - **RFC-019** (논점3 결론 · 결정요약 #4·#5) — 즉시 폐기 포기 + rotation 유지 + current_refresh_jti 단일 컬럼으로 재사용 탐지·강제 로그아웃 확정. jti
      불일치=탈취→NULL로 전 세션 무효화. 메커니즘 자체는 결정됨(그러나 오탐·다중디바이스 함의는 다루지 않음).
    - **RFC-020** (JTI 정합 (line 123)) — '즉시 로그아웃 불가(access TTL 지연)는 RFC-019가 이미 수용한 트레이드오프(짧은 access TTL로 완화)'. 재사용
      탐지=refresh 제출 시점에만, token family 무효화. 즉시 무효화 불가를 재확인하되 access TTL 값은 여전히 미제시.
    - **D-017** (§6 후속으로 넘기는 것 + §7 Risks) — 'current_refresh_jti 갱신·대조의 동시성(같은 주체의 병렬 /refresh 경합) 처리'와 access TTL 구체
      값을 후속으로 이관; Risk표에서 병렬 /refresh 경합 완화='후속 사이클로 넘김'. → 재사용 탐지 오탐(로그아웃 폭탄) 처리 미해결.
    - **D-017** (Weakness §3 (다중 디바이스)) — 'current_refresh_jti=주체당 refresh 1개 → 다중 디바이스 동시 세션 불가... 문서는 이 기능 상실을 리스크로도
      다루지 않는다'—문서 스스로 미해결 인정. 디바이스 바인딩(§Weakness1)도 '언급조차 없다'. → C25의 다중디바이스·디바이스 바인딩 지적은 어떤 문서로도 해소 안 됨.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C26 — CSRF 방어를 SameSite Lax+body 비대칭에만 걸고 in-memory access 강제 UX·부하 비용 미계량

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-017 §4.2, D-017 §4.2
- **설명**: access를 body로 내려 CSRF 면역이라 하나 /refresh CSRF 성공 시 서버 rotation이 발생해 victim의 유효 refresh를 교체하는 세션 파괴 DoS가 생기고,
  SameSite Lax는 top-level GET을 허용해 method 혼용 시 뚫린다. 진짜 방어(CSRF 토큰·Origin·Strict)는 모두 후속으로 미뤘다. in-memory access 강제는 모든
  리로드·새 탭·복귀마다 /refresh 왕복을 강제해 부하·지연·오탐 확률을 올리나 계량되지 않았다.
- **판정 근거**: C26의 두 축(① CSRF 방어가 SameSite Lax + access=body 비대칭에만 걸려 /refresh CSRF 성공 시 rotation으로 victim refresh가 교체되는
  세션 파괴 DoS·method 혼용 취약, ② in-memory access 강제의 리로드/새탭/복귀 왕복 부하·지연·오탐 미계량)은 ADR-20/RFC-019/DESIGN-017에서 "결정"으로 닫힌 게 아니라
  오히려 DESIGN-017 스스로가 §7 리뷰에서 명시적으로 제기한 반박이다. 정확히 C26 문구가 DESIGN-017 §7 라인153·155에 그대로 있고(리뷰용 반박, 결정 뒤집지 않음·후속 검토 대상이라
  못박음), 진짜 방어(CSRF 토큰·Origin·SameSite Strict)와 in-memory 비용 계량은 §6 "후속으로 넘기는 것"과 §7 위험표(SameSite Strict §6 후속 검토)로 전부
  미뤄졌다. ADR-20은 "SameSite로 /refresh CSRF 표면을 막는다"고 단언하지만 rotation-DoS·Lax의 top-level GET 구멍·왕복 부하를 다루지 않고, 쿠키 속성(Lax vs
  Strict)·TTL을 미결정으로 남긴다. RFC-020은 엣지 검증 위치·헤더 신뢰만 정할 뿐 CSRF 토큰/Origin 검증을 결정하지 않는다. 즉 개념은 인지·언급됐으나 해소 결정은 없고 gap이 명시적으로
  열려 있음 → partially-decided.
- **인용**:
    - **DESIGN-017 (D-017)** (§7 Risks/리뷰 반박 line 153) — 'CSRF 방어를 SameSite Lax + body 비대칭에만 의존 — /refresh CSRF 성공 시 응답
      못 읽어도 서버 rotation 발생해 victim 유효 refresh 교체(세션 파괴=DoS), SameSite Lax는 top-level GET 허용해 method 혼용 시 뚫림. 진짜 방어인 CSRF
      토큰·Origin 검증·Strict 채택은 모두 §6 후속으로 미뤄.' — 문서가 C26을 자기 반박으로 제기하고 후속으로 넘김
    - **DESIGN-017 (D-017)** (§7 line 155) — 'in-memory access 저장 강제가 SPA UX·리프레시 폭주 유발 — 모든 리로드·새탭·복귀마다 /refresh 왕복 강제,
      짧은 TTL과 결합 시 호출 급증·재사용 탐지 오탐 상승. 운영 비용·UX 저하는 계량되지 않았다.' — C26 두 번째 축 그대로, 미계량 인정
    - **DESIGN-017 (D-017)** (§7 말미 line 157) — '본 절은 리뷰용 반박 정리이며 문서의 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상.' — 반박이 해소가 아니라 open으로
      남음을 명시
    - **DESIGN-017 (D-017)** (§6 후속으로 넘기는 것 line 95) — '쿠키 속성 확정 — SameSite Lax vs Strict(외부 링크 진입 영향), path 스코프, 도메인' 을
      후속(design_doc)으로 위임. 진짜 방어 확정이 미결
    - **DESIGN-017 (D-017)** (§4.2 line 48-54) — C26 Members 지목 지점. transport=V1 계승+SameSite Lax 보강, access=body 비대칭이
      CSRF 면역이라 주장, access 클라 저장은 in-memory 강제(localStorage 금지). 방어를 SameSite+비대칭에만 걸고 in-memory 왕복 비용은 언급 없음
    - **ADR-20 (20.auth-token-transport)** (결정사항 transport + 미결정 사항) — 'SameSite(기본 Lax)·path 스코프를 채워 /refresh CSRF 표면을
      막는다'·'access=body 비대칭이 CSRF 노출을 낮춤'으로 단언하나, 미결정 사항에 'SameSite Lax vs Strict, path 스코프'를 남기고 rotation-DoS·Lax
      top-level GET·왕복 부하는 다루지 않음
    - **RFC-019** (논점2 결론 + 결정 요약 #2) — transport=V1 계승+SameSite Lax·path 보완, access=body가 CSRF 면역을 원리적으로 보장한다고 결론. 단 '
      Lax vs Strict, path'는 design_doc으로 명시 위임 — CSRF 토큰·Origin은 논의조차 없음
    - **RFC-020** (논점1·Non-goal line 63-94) — 엣지 1회 검증(모델 A)·헤더 신뢰·NetworkPolicy·헤더 strip만 결정. CSRF 토큰/Origin
      검증·SameSite Strict는 범위 밖 — /refresh CSRF DoS를 해소하지 않음
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C27 — 솔로 운영자의 self-managed 인프라 운영 표면과 진실원천 HA·relay failover 미스매치

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-010 §4.5, D-010 §4.5, D-010 §4.4, D-010 §4.2
- **설명**: KRaft·PDB·리밸런스·브로커 디스크·업그레이드 등 사람이 값을 정해야 하는 운영 표면을 측정 후로 미뤄, 미확정 규격 브로커가 디스크 풀·리밸런스 폭주를 내면 학습이 아니라 사고 대응이 된다.
  event_store가 진실원천인데 HA를 비동기 binlog standby로 둬 primary 유실 시 미복제 tail 이벤트=진실원천 소실이 된다. relay replicas:1의 failover 공백이
  프로젝션 지연 하한을 만들고 stateful 동거 평탄 namespace가 폭발 반경을 키운다.
- **판정 근거**: C27은 D-010의 자체 "Weakness(Devil's Advocate)" 절(라인 248·249·252·253)을 거의 그대로 옮긴 4개 하위 우려의 묶음이며, D-010 스스로 "본
  절은 리뷰용 반박 정리이며 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상"(라인 256)이라고 명시해 미해결임을 인정한다. 방향/정책 형태는 ADR로 잠겼으나, 우려가 지목한 핵심 수치·규격·실패 모드는
  의식적으로 측정/구현 사이클로 미뤄져 있다. (1) Kafka self-managed 운영 표면: ADR-12가 self-managed를 학습 목표로 의식적 채택("의도된 비용")하되 KRaft·브로커
  수·복제팩터·PDB·스토리지클래스·리소스한도를 전부 "구현 사이클"로 미결정 명시(ADR-12 §미결정, RFC-007 논점8·결론 KRaft만 확정). 디스크풀·리밸런스 폭주 위험은 D-010 Risks 표("
  Strimzi KRaft 운영 미숙")에 나열만 되고 정량 완화는 없음 → touched-but-open. (2) event_store 진실원천 HA=비동기 binlog/tail 유실: ADR-13이
  binlog=HA로 고정하되 "standby 개수·복제 지연 허용치·페일오버 자동화"를 미결정으로 남김. ADR-18은 "물리 장애 = 최근 일관 시점 복원, 잃는 건 마지막 복제분 이후 꼬리뿐"을 명시적으로
  수용하고 "복제 지연=RPO 하한"을 T-18 운영 백로그로 미룸 — 즉 우려의 핵심 질문(진실원천 RPO가 0이어야 하는가)은 결정되지 않고 의식적으로 연기됨. (3) relay replicas:1
  failover 공백: D-010 자체 Weakness 외 어떤 ADR/RFC도 relay의 failover-시간 SLI를 정의하지 않음(§4.6 페일오버 SLI는 DB에만 적용, relay 제외).
  RFC-008/011이 SLI 정의를 소유하지만 relay failover SLI는 부재 → 사실상 open. (4) 평탄 namespace + data면 동거 폭발반경: RFC-007 논점1이 반론("시야
  상실이 트리거보다 먼저 올 수 있음")을 인정하지만 Design 이의여지로 남기고, D-010 Risks는 NetworkPolicy만 언급할 뿐 앱↔stateful 간 리소스쿼터·PDB 경계는 없음. 종합:
  광범위하게 다뤄졌고 형태는 잠겼으나 우려가 지목한 실패 모드·수치는 미해결 gap으로 남아 partially-decided.
- **인용**:
    - **D-010 (DESIGN-010-deployment-runtime)** (§Weakness 라인 248·249·252·253 및 라인 256 마무리) — C27의 4개 하위우려가 이 절의 문장과 거의
      verbatim 일치. 문서 스스로 '리뷰용 반박 정리이며 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상'이라 명시 — 미해결 인정.
    - **ADR-12 (kafka-hosting-msk-vs-self-managed)** (결정사항 + 트레이드오프 + 미결정 사항(라인 52-53, 60-65)) — self-managed를 학습목표로 의식적
      채택하고 '브로커 운영·업그레이드·스토리지·장애복구 전부 내 책임 … 의도된 비용'이라 수용. 그러나 Strimzi 규격(브로커수·복제팩터·PDB·스토리지클래스·리소스한도)과 KRaft vs
      ZooKeeper를 '구현 사이클'로 미결정. 디스크풀/리밸런스 폭주 위험은 정량화·완화하지 않음.
    - **ADR-13 (db-hosting-and-read-write-topology)** (결정사항 binlog=HA(라인 37) + 미결정 사항(라인 72)) — binlog는 다리가 아니라 각 DB의
      이중화(HA)로 고정. 그러나 'standby 개수·복제 지연 허용치·페일오버 자동화'를 미결정으로 남김. 비동기 복제 tail 유실이 진실원천 소실인지에 대한 판단 없음.
    - **ADR-18 (event-store-recovery-semantics)** (결정사항 물리장애=일관시점복원(라인 35 부근) + 미결정 사항 'binlog→PITR … 복제 지연=RPO 하한') — '
      물리 장애는 가장 최근 일관된 시점까지 복원 … 잃는 건 마지막 복제분 이후의 꼬리뿐'이라며 tail 유실을 명시적으로 수용. RTO/RPO·복제 지연=RPO 하한은 T-18 운영 백로그로 연기 —
      진실원천 RPO가 0이어야 하는가는 미결.
    - **RFC-017 (disaster-recovery-event-store)** (논점2 결론 및 '이의 여지'(라인 104·108) + Non-goals(라인 72)) — '잃을 수 있는 건 마지막 복제분
      이후의 꼬리뿐 … 아무도 확정적으로 보지 못한 이벤트이길 바란다(RPO 논의로 연결)'. RTO/RPO 수치는 실트래픽 없어 T-18로 미룸, 가드레일만 제공.
    - **RFC-007 (deployment-infra-ops)** (논점1 namespace(라인 80-83) + 논점6 standby(라인 111-115) + 논점8 KRaft(라인 127) + 범위밖(라인
      67)) — 단일 평탄 namespace 기본 채택하되 반론('컨텍스트 증가로 시야 상실이 트리거보다 먼저 올 수 있음')을 Design 이의여지로 남김. standby 1대·KRaft만 확정, 복제 지연
      허용치·자동 페일오버·브로커 규격·PDB는 측정 후로 명시 이관.
    - **D-010 §4.4 + §4.6** (라인 158(relay replicas:1 +standby 괄호) 및 라인 180-182(SLI 목록)) — relay는 replicas:1(+standby 괄호
      언급), HPA 대상 아님. 핵심 SLI 4종에 '페일오버 소요 시간' 포함되나 §4.6은 이를 DB HA에 걸고 relay failover SLI는 정의하지 않음 — relay 가용성 공백은 어느
      ADR/RFC에서도 해소되지 않음.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C28 — 파티션 수를 계약으로 못박고 HPA 상한=파티션 수로 두어 초기 추정 실패의 벌칙을 극대화

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-008 §4.3, D-010 §4.4
- **설명**: in-place 증설을 금지하고 신규 토픽 마이그레이션을 강제해, 추정이 빗나가면 병렬 상한에 막힌 프로젝터가 lag을 쌓고 유일 해법이 고비용 무중단 마이그레이션이 된다. 컨슈머 그룹 스케일을
  파티션 수로 캡해 적체를 스케일아웃으로 푸는 최대치가 토픽 생성 시 고정되며, 보수적 초기값 근거도 처리량 추정 TBD에 걸려 순환이다. 초기 실수의 벌칙을 최대치로 키웠다.
- **판정 근거**: 설계 결정 자체(파티션 고정 지향, HPA 상한=파티션 수, in-place 증설 금지·새 토픽 마이그레이션, 병렬 상한=파티션 수)는 ADR-09(O-A 채택)·RFC-003(논점 6·9)
  ·DESIGN-008/010에서 명시적으로 확정돼 있어 '토픽'은 결정됨. 그러나 concern이 제기한 핵심 리스크 — 초기 추정이 빗나가면 벌칙이 최대화되고(프로젝터가 병렬 상한에 막혀 lag 적체, 유일
  해법이 고비용 무중단 마이그레이션), 보수적 초기값의 근거가 처리량 추정 TBD에 걸려 순환 — 는 해소되지 않았다. ADR-09 Risks와 DESIGN-008 §4.3/§4.7이 이 트레이드오프를 '인지'는
  하지만 완화 설계(여유 과다프로비저닝 기준, 재추정·재파티셔닝 회복 절차, 순환을 끊는 초기값 산정 방법)는 전부 TBD/운영 위임이고, RFC-003 line 174에는 초기값 근거에 '〔근거 확인/보강
  필요〕' 표식이 남아 있다. 즉 결정은 됐으나 concern이 지적한 벌칙 극대화·순환 논리에 대한 명시적 해소는 없어 partially-decided.
- **인용**:
    - **ADR-09** (§Decision line 57-59, §Risks line 86, §미결 line 97 (event-ordering-and-delivery-guarantee, 상태:
      Proposed)) — 파티션 키=aggregate_id, '파티션 수는 순서 계약의 일부 — 넉넉히 잡고 가급적 고정. 증설은 컨슈머 드레인 후 정지 상태에서'를 결정으로 채택(Option O-A).
      병렬 상한=파티션 수도 명시(line 71). Risks에서 '파티션 수가 순서 계약에 묶임 — 증설이 무중단이 아니라 드레인-정지를 요구'로 벌칙을 인지는 하나 완화책 없음. 초기값·증설 절차는 '처리량
      추정 의존, 운영 사이클 TBD'로 미결(line 97).
    - **RFC-003** (논점 6·논점 9 (line 142-146, 168-176, 189/192), 미결 line 69) — '그룹 내 스케일은 파티션 수 상한', '파티션 고정 지향 + 보수적 초기값(
      일반 3, 고처리량 6~12), 증설=새 토픽 마이그레이션'을 결론으로 confirm. 그러나 line 174 네 결정에 '〔근거 확인/보강 필요〕'가 붙어 초기값 근거가 미확정이고, line
      69/176은 '절대 수치(파티션 초기값)'를 Design/운영으로 미룸 — 개념의 순환(보수적 초기값 ← 처리량 추정 TBD)이 문서상 그대로 남음.
    - **DESIGN-008** (§4.3 한계(재해싱)·§4.4 Competing consumers·§4.7 백프레셔) — concern이 인용한 원문. '파티션 수는 순서 보장의 계약 일부', 증설은
      in-place 금지·새 토픽 마이그레이션, '병렬 상한=파티션 수', 백프레셔 최종 대응이 '파티션 증설(§4.3 한계 감수, 정지 후)'. 벌칙 구조 자체를 서술하지만 초기 추정 실패 시 완화는 '정지
      후 마이그레이션'뿐 — concern이 지적한 고비용 유일해법이 문서로 확인됨. 초기값은 'TBD'.
    - **DESIGN-010** (§4.4 projector, §6 미결(TBD)) — '스케일은 파티션 수에 종속 … 따라서 HPA 상한은 파티션 수에 맞춘다'로 HPA캡=파티션수를 확정. §미결은 물리분리
      시점·standby 수치 등은 다루나 파티션 초기 추정 실패에 대한 회복 경로(예: 여유 파티션 사전 과다프로비저닝, 컨슈머 다중그룹 샤딩)는 없음.
    - **ADR-12** (§미결 line 64 (kafka-hosting)) — '파티션 수가 projector HPA 상한을 정함'을 재확인하고 토픽·파티션 설계를 이벤트 스토밍·projector 스케일과
      함께 결정할 것으로 넘김 — 숫자 미확정을 재차 위임.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C29 — command/query 배포 합침이 독립 스케일·단일 이미지 격리 주장과 충돌

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-010 §4.6, D-012 §4
- **설명**: 초기 한 배포·단일 이미지 다중 프로파일이 command/query/projector/relay 독립 스케일과 워크로드 격리 주장에 배치된다. 읽기 폭증이 같은 파드 쓰기 경로까지 스케일시키고, 한
  워크로드의 의존성·CVE 변경이 나머지에 전파되며, 프로파일 오설정으로 relay가 둘 뜨는 단일성 위반이 다뤄지지 않는다. 분리 트리거를 관측할 지표가 합쳐진 배포에서 오염돼 분리 판단 자체가 어렵다.
- **판정 근거**: C29는 서로 다른 하위 주장 네 개를 묶고 있고, 결정 상태가 갈린다.

(1) relay 단일성 위반(프로파일 오설정 등으로 둘 뜨는 것) — 사실상 결정됨. RFC-003 논점3이 "여러 relay 인스턴스가 동시에 뜨는" 바로 그 상황을 전제로
`SELECT … FOR UPDATE SKIP LOCKED` 경쟁 소비를 채택해(leader election 미도입) DB가 직렬화하게 만들고, at-least-once + projector 멱등성(Zero
Payload)이 잔여 중복을 흡수한다(ADR-09). 즉 "relay가 둘 떠도" 중복 발행이 read model을 오염시키지 않도록 데이터 안전성은 구조적으로 닫혀 있다. C29가 이 축을 "다뤄지지 않는다"고
한 것은 부정확하다.

(2) command/query 배포 합침 vs 독립 스케일 충돌 — 방향은 결정(RFC-007 논점2·D-010 §4.6: 초기 단일 배포, 읽기 스케일 한계/장애 격리 시 물리 분리). 그러나 "한 파드에서는
read/write 독립 HPA가 성립하지 않는다"는 충돌 자체의 해소책은 없고, D-010 Weakness 절에 반박으로만 실려 "후속 검토 대상"으로 남는다.

(3) 분리 트리거 지표가 합쳐진 배포에서 오염 — D-010 Weakness가 C29와 거의 동일 문장으로 자기 지적하며 미해결. RFC-007 논점2도 "분리 신호의 지표·임계 수치는 Design"으로 이의
여지만 남김.

(4) 단일 이미지 다중 프로파일의 CVE·의존성 전파 — D-012 §4는 단일 이미지를 택하며 "빌드·취약점 스캔 표면을 하나로 유지"를 오히려 이점으로 서술. 워크로드 간 CVE/의존성 폭발 반경은 어디서도
저울질하지 않음.

따라서 relay 단일성은 결정, 나머지 세 축(배포 합침-스케일 충돌, 분리 트리거 관측 오염, 단일 이미지 전파)은 언급됐으나 미해결 — partially-decided.

- **인용**:
    - **RFC-003** (논점 3. Outbox relay의 단일성 (라인 104-116, 186)) — relay는 가용성 위해 여러 인스턴스로 뜨고 같은 Outbox 행을 둘이 집으면 중복 발행 위험 →
      결정: relay 단일성 = SELECT … FOR UPDATE SKIP LOCKED 경쟁 소비, leader election 미도입. 여러 relay 인스턴스가 서로 다른 잠기지 않은 행만 집어 중복
      없이 경쟁 소비. C29의 '프로파일 오설정으로 relay 둘 뜨는 단일성 위반'의 데이터 안전성 축을 구조적으로 흡수.
    - **DESIGN-010** (§4.4 outbox relay 왜 단일성 필요한가 (라인 153-158) + Risks(라인 212)) — replicas:1(+standby) 또는 분산 락 기반 소수
      replica, HPA 대상 아님. 중복 발행은 projector 멱등성(Zero Payload)으로 흡수. relay는 불필요 중복 최소화 책임. 단일성 축은 닫혀 있음.
    - **RFC-007** (논점 2. command/query를 언제 물리 분리하는가 (라인 89)) — 결론: 초기 단일 배포, 읽기 스케일 한계 또는 장애 격리 필요 시 물리 분리. '분리 신호의
      지표·임계 수치는 Design'이라며 이의 여지로만 남김 — 합침-스케일 충돌 자체의 해소책 없음.
    - **DESIGN-010** (§4.6 확장 축 (라인 180)) — query·projector는 읽기 부하로, command는 쓰기 부하로 독립 스케일 주장. §4.1/§4.4의 초기 단일 배포와
      병치 — 한 파드에서 독립 스케일이 성립하지 않는 충돌의 근거.
    - **DESIGN-010** (Weakness 절 'command/query 배포 합침과 HPA 축 충돌' (라인 250 부근)) — C29와 거의 동일 문장으로 자기 지적: 합친 상태에서 독립 스케일
      미성립, 분리 트리거(읽기 스케일 증명)를 관측할 지표가 합쳐진 배포에서 오염돼 분리 판단 자체가 어렵다. 문서 스스로 '후속 검토 대상'으로 명시 — 미해결.
    - **DESIGN-012** (§4 Docker 이미지 전략 (라인 319-330)) — 단일 이미지, 다중 진입점(
      --spring.profiles.active=command|query|projector|relay). '빌드·취약점 스캔 표면을 하나로 유지'를 이점으로 서술 — 워크로드 간 의존성·CVE 전파 폭발
      반경은 저울질하지 않음.
    - **ADR-01** (cqrs-command-query-module-split) — 모듈은 이미 분리(코드). 배포 물리 분리는 별개이며 모듈 분리가 별도 파드를 강제하지 않는다는 근거 — RFC-007
      논점2가 참조.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C30 — 검증 환경이 게이트로 거는 다중브로커·HA 특유 위반을 재현할 수 없는 구성 모순

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-012 §5.2, D-012 §2.1, D-012 §1
- **설명**: 단일 브로커·복제1·standby 없는 Testcontainers/k3s는 리밸런스 중 중복 소비·리더 전환 재전달·복제 지연 신선도 같은 effectively-once 위반을 재현하지 못하는데
  T-05를 결정적 CI 필수로 건다. k3s~EKS 패리티 표는 하필 운영 사고가 나는 축(failover·standby·ALB·리밸런스)을 전부 단순화해 '운영 버그를 로컬로 당긴다'가 가장 필요한 지점에서
  성립하지 않는다. compose 인프로세스 relay와 k3s 별 파드 relay의 코드 경로 등가성 테스트도 없다.
- **판정 근거**: 부분 결정. concern의 전제 일부는 문서와 어긋난다 — DESIGN-012 §5.7·RFC-009는 '결정적 하위집합(멱등·재생등가·동시성)'만 CI 필수로 걸고, HA/다중브로커 특유의
  effectively-once 위반(리밸런스 중복소비·리더전환 재전달·복제지연 신선도)을 검증하는 T-05 카오스는 명시적으로 '정기/통합' 또는 비-차단으로 연기했다(Chaos Mesh는 리스크 부상 시
  졸업). 즉 '위반을 못 만드는 환경으로 그 위반을 CI 필수로 건다'는 강한 모순 주장은 문서 결정에 정확히 대응하지 않는다. 그러나 concern의 핵심 — (1)단일브로커·복제1·standby 없는 패리티
  환경이 하필 운영사고 축(failover·리밸런스·ALB)을 재현 못 한다, (2)그래서 '운영버그 로컬로 당긴다'가 가장 필요한 지점에서 성립 안 한다, (3)compose 인프로세스 relay vs k3s
  파드 relay 경로 등가성 테스트가 없다 — 이 세 축은 DESIGN-012 §Weakness(line 427·430·432)에 거의 축자적으로 적혀 있으나 line 435에서 '결정을 뒤집지 않는 후속 검토
  대상'으로 못박혀 미해결이다. 관련 ADR(09/12/14)·RFC(003/009/017) 어디에도 이 재현불가 모순을 해소하는 결정이 없다. 따라서 관련 결정(패리티 축 단순화·게이트 분류·카오스 연기)은
  내려졌으나 concern이 지적하는 gap 자체는 인지된 채 미해결 → partially-decided.
- **인용**:
    - **DESIGN-012** (§9.1 용어집 T-05 정의 + §5.7 동적 분산 행위 표 (게이트 열)) — T-05='무손실·effectively-once 보장 요건'. §5.7 표는
      멱등성·재전달/재생·스냅샷 등가/동시성만 '결정적 → CI 필수'로 걸고, 무손실·effectively-once의 인프라 실패(broker 파티션·DB failover·리밸런스) 검증은 카오스로 밀어 '
      무거움→정기/통합' 또는 비-차단으로 둔다. 즉 concern이 말하는 'T-05를 결정적 CI 필수로 건다'는 정확히는 결정적 하위집합만 CI 필수이고 HA/다중브로커 위반 검증 자체는 CI 필수가
      아니다.
    - **DESIGN-012** (§5.5 카오스 행 + Alternatives 'Chaos Mesh 선제 도입' (line 141)) — 인프라 레벨(파드 kill·네트워크 분단·broker 파티션·DB
      failover)은 '지금 도구를 도입하지 않는다' — T-05(무손실·effectively-once) 검증이 실제 운영 리스크로 부상하는 시점을 트리거로 Chaos Mesh를 1순위 후보로 예약. 즉
      HA 특유 위반 재현은 명시적으로 연기됨.
    - **DESIGN-012** (§2.1 미러링 표 (Kafka: 파티션 축소·복제 1 / DB: HA standby 생략)) — k3s는 단일 브로커·복제 1·standby 생략을 명시적으로 채택.
      concern이 지적한 '위반을 만들 수 없는 구성'이 설계로 확정돼 있음. 다만 '이 축이 하필 운영사고 축이라 재현 불가'라는 모순 자체는 §2.1 본문에서 다루지 않음.
    - **DESIGN-012** (§Weakness (Devil's Advocate) line 432) — concern C30과 사실상 동일 문장: '단일 브로커·복제1·standby 없음 환경에서는 리밸런스
      중 중복 소비, 파티션 리더 전환 중 재전달, 복제 지연 하 read model 신선도 같은 다중 브로커/HA에서만 발현되는 effectively-once 위반이 재현되지 않는다. T-05를 결정적 CI
      항목으로 필수화했는데 그 검증 환경이 위반을 만들어낼 수 없는 구성이라는 모순을 짚지 않는다.' — 그러나 line 435에서 '본 절은 리뷰용 반박이며 문서의 결정을 뒤집지 않는다. 각 항목은 후속 검토
      대상'으로 명시 → 인지만 하고 미해결.
    - **DESIGN-012** (§Weakness line 430 (compose 모놀리식 vs k3s 4분리 코드 경로 분기)) — '인프로세스 스케줄러로 도는 relay와 별 파드 leader relay는
      동시성·트랜잭션·중복발행 경로가 다르다... 두 실행 모드의 등가성을 보장하는 테스트가 피라미드에 없다'고 스스로 인정. concern의 세 번째 축(relay 경로 등가성 테스트 부재)이 미해결로
      명기됨.
    - **RFC-009** (논점 6 + 결정 요약 #6) — 카오스는 앱 레벨 Chaos Monkey + 인프라 레벨 Chaos Mesh 둘 다 하되 '딥한 시나리오 설계는 인프라가 갖춰진 후'로 명시.
      인프라 카오스로 검증하는 HA 위반은 게이트 확정 대상이 아니라 후속. effectively-once 재현 환경의 모순은 RFC-009 어디에도 논의되지 않음.
    - **ADR-09** (§전달 — effectively-once + 미결정 사항) — effectively-once='at-least-once+멱등'으로 합성한다는 메커니즘은 확정. 그러나 이 보장을 다중
      브로커·리밸런스·리더 전환 조건에서 어떻게 검증/재현하는지는 다루지 않음(테스트 환경 관심사 아님).
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C31 — 무거운 E2E 게이트·비-차단 관측·절대 SLO 부재로 게이트가 실효를 잃고 완만한 회귀가 누적

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-012 §5.3, D-012 §4.2
- **설명**: 매 PR/머지마다 ephemeral k3s+Strimzi+2 MySQL을 띄우고 Awaitility 비동기 대기를 도는 E2E는 느리고 flaky해 스킵·타임아웃 완화를 부른다. k6·Chaos를
  비-차단으로 두고 절대 임계 없이 '추세를 사람이 읽는다'에 기대면 서서히 나빠지는 회귀가 매번 통과해 누적되며, 솔로 환경에서 추세를 매번 읽는 규율 보장과 자동 게이트화 트리거 시점이 없다.
- **판정 근거**: C31이 지적하는 세 축(무거운 E2E 게이트의 실효성·비-차단 관측+절대 임계 부재로 인한 완만한 회귀 누적·솔로 환경의 추세 읽기 규율 및 자동 게이트화 트리거 부재)은 D-012와
  RFC-009/ADR-14에서 모두 명시적으로 다뤄지나, 정책 방향만 잠그고 결함 자체는 미해결로 남긴다.

해결된 부분: (1) 게이트 차단성 분류가 고정됨 — Chaos/k6는 비-차단 관측, 대신 CI 아티팩트·추세 리포트로 매 실행 가시화 의무화(D-012 §4.2 line107, §6.4). (2) 커버리지는
ratchet(후퇴 금지) 정책으로 고정(D-012 line108). (3) E2E 비용 완화책으로 매 커밋이 아닌 PR/머지 게이트 시점 ephemeral 실행 지정(D-012 §7 리스크표 line198).

남은 갭(정확히 C31이 짚는 것): (a) 절대 SLO/임계는 Non-Goal로 명시 연기 — "절대 SLO를 지금 정의하지 않는다(베이스라인 측정 후 결정)"(D-012 line35, ADR-14 미결정
line60, RFC-009 논점8). (b) 자동 차단 임계로의 '졸업 트리거 시점' 자체가 TBD — "자동 차단 임계로의 졸업은 베이스라인 측정 후 별도 결정"(D-012 line107)이며 Phase 5 졸업
트리거=TBD(line216), 즉 언제 게이트화할지 정의 안 됨. (c) 커버리지 ratchet은 잠갔으나 k6·Chaos 성능/회귀에는 절대 임계 없음 → 완만한 회귀가 "전 대비 소폭"으로 통과 누적. (d)
솔로 환경에서 추세 리포트를 매번 읽는 규율 보장·E2E 게이트 완화(매 머지 무게)가 충분한지 근거 없음 — 이 두 항목은 D-012 §Weakness 반박 3(line429)·5(line431)에 프로젝트 스스로
미해결 약점으로 등재. 즉 C31은 D-012의 자기 devil's-advocate 항목과 사실상 동일하며, 후속 검토 대상으로만 표시되고 결정으로 닫히지 않았다.

- **인용**:
    - **DESIGN-012** (§3 Non-Goal (line 35)) — "절대 SLO를 지금 정의하지 않는다(베이스라인 측정 후 결정)." — 절대 임계 부재를 의도적 연기로 명시.
    - **DESIGN-012** (§4.2 게이트 차단성/비-차단 관측 (line 104-107)) — Chaos Monkey·k6는 비-차단 관측(머지 안 막음), CI 아티팩트·추세 리포트로 가시화하되 "
      자동 차단 임계로의 졸업은 베이스라인 측정 후 별도 결정" — 게이트화 트리거 시점 미정.
    - **DESIGN-012** (§4.2 커버리지 ratchet (line 108)) — 커버리지는 후퇴 금지 정책을 지금 고정. 단 이는 커버리지 한정이며 k6/Chaos 성능 회귀에는 절대 임계 없음.
    - **DESIGN-012** (§7 Risks (line 198, 200)) — E2E 완화책=PR/머지 게이트에만 ephemeral 실행; "절대 SLO 없이 k6 결과가 무시됨" 리스크의 완화는 가시화
      의무화+베이스라인 후 임계 설정으로 재차 연기.
    - **DESIGN-012** (§8 Milestones Phase 5 (line 216)) — "Phase 5: 졸업 트리거 TBD — 절대 SLO 설정, Chaos Mesh 검토" — 게이트화 트리거
      자체가 미정.
    - **DESIGN-012** (§Weakness 반박 3·5 (line 429, 431)) — C31과 사실상 동일한 문장: E2E를 PR/머지 게이트로 두는 비용·flaky로 스킵·타임아웃 완화 우려('
      완화가 충분한지 근거 없음'), 그리고 절대 SLO 부재+비-차단으로 완만한 회귀 누적·솔로 환경 추세 읽기 규율 미보장·자동 게이트화 트리거 미정의. 프로젝트 스스로 미해결 약점으로 등재(결정으로 닫히지
      않음).
    - **RFC-009** (논점 8 / 결정요약 #7·#8 (line 130-136, 154-155)) — 게이트 정책은 지금 잠그되 커버리지·k6 절대 임계는 베이스라인 측정 후 ratchet;
      k6=정기/릴리스 전 게이트, 절대 SLO는 측정 후 확정 — 임계·게이트화 결정 자체는 미확정.
    - **ADR-14** (미결정 사항 (line 60), 상태 Proposed (line 3)) — "각 범주 커버리지 목표·CI 게이트화 임계 — 베이스라인 후." ADR-14는 아직 Proposed
      상태이며 임계/게이트화는 미결정으로 남김.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C32 — 관측성 규약이 Context 전파 메커니즘·traceparent 위치·백엔드 배선을 전부 미결로 두어 종이 위에서만 성립

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-011 §4.1, D-011 §4.3, D-011 §4.7
- **설명**: OTel Context=전파 매체 전제가 스케줄러·Kafka 리스너·@Async executor 등 관측성이 가장 필요한 경계에서 실제로 이어지는지가 구현 사이클로 미뤄져 규약 전체가 미검증이다.
  traceparent를 봉투/페이로드 중 어디 둘지 미결이면 event_store 영구 직렬화 특성상 나중 변경이 과거 이벤트 재직렬화·업캐스팅을 불러 되돌리기 비싼 인질이 된다. Tempo·Loki 배선 전면
  보류로 교차 조회 같은 핵심 규약을 사이클 안에서 확인할 방법이 없다.
- **판정 근거**: C32는 사실상 DESIGN-011 자신의 Weakness(Devil's Advocate) 절 1·2·6번 항목을 거의 그대로 재진술한 것이다. 관측성 "규약" 자체는 RFC-008(🏷합의)
  ·DESIGN-011(Accepted)에서 확정돼 있다 — 전파 매체=OTel Context/MDC=투영 원칙(§4.1), correlationId/causationId/traceparent를
  AbstractEvent 메타데이터로 의무화(§4.2·§4.3), correlationId를 root span 필수 attribute로 격상(§4.4), AOP 스코프 주입·메트릭 카탈로그(§4.5·§4.6),
  OSS 백엔드 스택(Prometheus/Grafana/Tempo/Loki) 선택 고정(§4.7.7). 그러나 C32가 지목한 세 가지 구체 항목은 문서가 의도적으로 미결/보류로 남긴다: (1) Context 전파
  메커니즘(taskDecorator/ContextSnapshot을 스케줄러·Kafka 리스너·@Async executor 경계에 실제로 잇는 구현)은 §4.1·§6에서 명시적으로 "구현 사이클"로 미룸, (2)
  traceparent를 봉투 헤더 vs 페이로드 어디 둘지는 §4.3·§6에서 "DESIGN-003 스키마와 맞춰 확정"으로 아직 미결(event_store 영구 직렬화·ADR-010 업캐스팅과의 상호작용
  미짚음을 §Weakness가 스스로 인정), (3) Tempo·Loki·Prometheus·Grafana 배선은 §3 Non-Goal·§4.7·§6·RFC-008 Non-goal에서 전면 보류(배포 사이클).
  즉 "규약은 확정됐으나 그 규약이 실제로 이어지는지·되돌리기 비싼 위치 결정·도구 위 검증"이라는 C32의 정확한 gap은 여전히 열려 있고, 이 미결을 해소하는 별도 ADR/RFC는 없다(관측성 전용 ADR
  부재, RFC-007은 SLI/배포 hook만 다루고 관측성 신호 정의는 RFC-008로 되넘김). 규약 확정과 의도적 deferral이 공존하므로 partially-decided.
- **인용**:
    - **RFC-008** (Non-goal (line 90) + 결과 (line 200)) — '추적/메트릭/로그 백엔드(Tempo·Prometheus·Grafana·Loki)의 배포와 수집 토폴로지'를
      명시적 Non-goal로 두고 docs/todo 백로그로 미룸 — '배포 사이클에서 검증'. 즉 백엔드 배선은 이 사이클에서 미결정.
    - **DESIGN-011** (§4.1 (line 48)) — '어느 taskDecorator·context-propagation 수단으로 비동기 경계를 잇고 어느 경계마다 거는지는 구현 디테일이라 구현
      사이클에서 검증한다' — Context 전파 메커니즘 실검증을 미룸.
    - **DESIGN-011** (§4.3 (line 103) + §6 미결정 (line 190)) — 'traceparent를 봉투 헤더에 둘지 페이로드에 둘지는 DESIGN-003 스키마와 맞춰 확정' —
      아직 결정되지 않음. §4.3은 event_store에 메타데이터가 함께 영구 직렬화된다고 명시(감사 추적 로그화).
    - **DESIGN-011** (§3 Non-Goal (lines 30-34) + §6 (line 185)) — Grafana/Tempo/Prometheus/Loki 배포·수집기·대시보드·로그 파이프라인
      전부 '보류 — 별도 운영 작업'. 스택은 OSS로 고정하되 배선은 todo.
    - **DESIGN-011** (§4.7 결정 요약 7 (line 172)) — '백엔드 스택 = OSS(선택 고정, 배선은 보류)' — 스택 선택은 확정, 배선은 명시적 보류. 규약과 검증의 분리.
    - **DESIGN-011** (Weakness 1·2·6 (lines 232-237)) — C32의 세 축을 문서가 스스로 반박 포인트로 기록: OTel Context 일급화 구현 리스크를 '구현 디테일로
      밀어냄', traceparent 위치 미결정이 재직렬화/업캐스팅 인질, 백엔드 배선 전면 보류가 핵심 규약(correlationId root span·Tempo→Loki 교차조회)을 사이클 내 검증 불가로
      만듦. C32는 이 절의 재진술.
    - **RFC-008** (논점1·논점4 결론 (lines 109, 139)) — 전파 수단·traceparent 위치를 모두 '구체 수단은 Design' / 'Design에서 스키마와 맞춰 확정'으로 명시적
      이양 — 규약 층에서 확정, 구현/위치는 후속으로 넘김.
    - **RFC-007** (line 69) — 'readiness 신호 정의·핵심 SLI·메트릭 카탈로그·알람 임계 → RFC-008'로 되넘김. RFC-007은 배포 측 hook만 다루므로 관측성 배선을
      해소하는 별도 문서 아님.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C36 — 클라이언트 Idempotency-Key 거부·422/409 경계·cursor 안정성 전제가 재시도·경합에서 취약

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-013 §4.3, D-013 §4.4, D-013 §4.6
- **설명**: 신뢰 클라이언트라는 이유로 멱등 키를 거부하나 네트워크 타임아웃 재시도는 서버가 재시도/새 의도를 구분 못 하게 만드는 분산 근본 문제라 생성 계열은 사실상 필요하다. 락 후 자리 없음의 422(
  확정 거절)와 409(경합) 경계가 경합 상황에서 실제로 안 갈려 클라이언트가 오분기한다. cursor 안정성 완화책(정렬 키 존재 검증)은 키 존재만 볼 뿐 단조·유일성을 못 봐 재처리로 timestamp 역행
  시 항목 건너뜀·반복을 못 잡는다.
- **판정 근거**: 세 하위 논점 모두 방향(direction)은 RFC-012에서 잠겼으나, C36가 지목한 구체적 실패 모드는 각각 미해결로 남아 있어 partially-decided다. (1) 멱등 키
  거부: RFC-012·D-013이 '서버 책임'을 확정했으나 '유니크 불변식 없는 생성 command'의 재시도/새의도 구분 구멍을 '잔여 케이스'로 구현에 위임, D-013 Weakness가 '생성 계열은
  사실상 필요'라고 자인. (2) 422/409 경계: RFC-014·ADR-16이 '재판단 시 결과 뒤집힘=409, 도메인 거절=422' 규칙을 주지만 '락 후 자리 없음'을 422/409로 병기해 경계가 확정
  문구에서조차 흔들리고, D-013은 이를 Risks로 넘김. (3) cursor 단조성: 어느 문서도 정렬 키의 단조·유일성을 다루지 않고, 유일한 완화책(정렬 키 존재 검증)은 concern이 지적한 그대로
  불충분하며 D-013 Weakness가 이를 인정. 즉 corpus가 문제를 '건드렸으나' concern이 명명한 취약점을 해소한 결정은 없다.
- **인용**:
    - **RFC-012** (논점 3 · 결정 요약 #3 (line 100-120, 155)) — 멱등 = 서버(도메인) 책임, 클라이언트 발급 키 미도입을 확정. 상태전이는 자연 멱등+낙관 락, 생성은 도메인
      유니크 불변식. '자연 유니크 불변식이 없는 생성 command의 잔여 케이스는 서버 측 디듀프로 처리' — 즉 방향은 잠갔으나 생성 계열의 재시도/새의도 구분 구멍은 '잔여 케이스'로 열어 구현에 위임.
    - **DESIGN-013** (§4.3 잔여 케이스 각주 + Weakness 3 (line 79, 196)) — '클라이언트 발급 키는 외부·비신뢰 클라이언트가 붙으면 다시 테이블에 오른다'고 명시하고,
      Weakness 절이 concern과 동일하게 '네트워크 타임아웃 재시도는 신뢰 클라이언트라도 분산 근본 문제 … 생성 계열은 사실상 필요하다가 정직한 결론'이라 자인. 문서 스스로 미해결로 표기.
    - **RFC-014** (논점 3 결론 + line 106-112, 129) — 422/409 경계에 규칙 제시: '재판단해도 결과 같은 충돌→흡수', '재판단하면 결과 뒤집히는 충돌(자리 경합)
      →409', '락 잡고 reload하니 자리없음→422/409 확정적 거절'. 즉 slot-contention을 409 쪽으로 두면서도 '자리 없음'을 422/409로 병기해 경계가 확정 문구에서조차
      흔들림.
    - **ADR-16** (line 47 충돌 처리) — 잔여 충돌 3분류(① lock-wait→409/503 ② 도메인 거절 reload 판단뒤집힘→422/409 확정 ③ 잔여 UNIQUE→흡수).
      concern이 지적한 '자리 경합 결과이면서 동시에 422로 라벨' 케이스의 클라이언트 오분기는 규약으로 확정 못 하고 RFC-012/Design에 노출 시점을 위임.
    - **DESIGN-013** (§4.4 + Risks + Weakness 4 (line 93, 151, 198)) — 422 vs 409 경계 모호를 Risks에 '@ControllerAdvice 매핑 표를
      구현 사이클에서 명시 확정'으로 넘김. Weakness 4가 concern과 동일하게 '동시 예약 2건 중 하나 거절 시 422냐 409냐 오분기'를 규약으로 확정 못 했다고 자인.
    - **RFC-012** (논점 6 · 결정 요약 #6 (line 139-145, 158)) — 페이징 표준 = cursor 확정. 그러나 '정렬 키는 read model 투영에 의존'까지만이고 정렬 키의
      단조(monotonic)·유일성 보장은 논하지 않음.
    - **RFC-002** (전체 (cursor 관련 서술 없음)) — read-your-writes 정책만 다루고 cursor 정렬 키의 단조·유일성은 전혀 언급 없음 — concern이 종속 대상으로 지목한
      문서인데 해당 보장을 제공하지 않음.
    - **DESIGN-013** (§4.6 + Risks + Weakness 5 (line 110, 150, 200)) — cursor 안정성 완화책이 '프로젝터 단위 테스트에서 정렬 키 존재 검증'뿐.
      Weakness 5가 concern과 동일하게 '존재 검증은 키가 있는지만 보고 단조·유일한지는 안 봐 재처리로 timestamp 역행 시 건너뜀·반복을 못 잡는다'고 자인 — 진짜 실패 모드 미해결.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C37 — 생성 도메인 유니크와 동시성 UNIQUE가 물리적으로 같은 제약이면 재시도 storm을 분리 못 한다

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-006 §6.1·§6.4
- **설명**: 시퀀스 채번을 한 곳에 모아 UNIQUE 위반을 항상 동시성 충돌로 해석하나, 생성 도메인 유니크(같은 시각 중복 예약) 충돌도 append 시점 UNIQUE로 나타난다. 이는 재시도로 안 풀리는
  도메인 거절인데 규칙대로면 '동시성→바운디드 재시도'로 흡수돼 막으려던 재시도 storm이 발생한다. 두 제약이 물리적으로 같으면 채번 계층에서 두 해석을 분리할 수 없다.
- **판정 근거**: 부분 결정(partially-decided). 결정된 부분: (1) ADR-22(16)가 'append 시점 DuplicateKey → 동시성 충돌 매핑, 시퀀스 채번 단일화'를 명시하고, (
  2) DESIGN-013 §4.3/§6.1이 도메인 유니크(같은 슬롯 중복 예약)를 물리 UNIQUE가 아니라 '락 안 reload 후 도메인 판단'으로 걸러 확정적 422/409로 분리하는 모델을 세워, 현
  설계상 도메인 거절과 동시성 충돌의 물리 제약을 분리하려는 의도는 존재한다. 미해소 부분: C37이 지적하는 '만약 도메인 유니크가 자연키 UNIQUE로 append 경로에 물리적으로 얹히면 채번 계층에서 두
  해석을 못 가른다'는 바로 그 시나리오를 DESIGN-006 §Weakness line 279가 거의 자구 동일하게 스스로 제기하고, line 282에서 '후속 검토 대상, 결정을 뒤집지 않음'으로 명시
  이월했다. 게다가 DESIGN-013 §Risks line 149는 유니크 불변식 없는 생성의 디듀프를 구현 사이클로 미뤘다. 따라서 방향(도메인 거절은 판단 계층에서, 동시성은 UNIQUE 백스톱에서)은
  잡혔으나 '두 제약이 물리적으로 겹칠 때의 분리 규약'은 어떤 ADR/RFC에서도 확정·해소되지 않았다.
- **인용**:
    - **DESIGN-006** (§Weakness (Devil's Advocate 반박 포인트), 5번째 불릿 (line 279)) — C37과 거의 자구 동일한 자기비판이 이 문서 안에 이미 적혀 있다: '
      시퀀스 채번을 한 곳에 모아 UNIQUE 위반을 항상 동시성으로 해석 … 생성(도메인 유니크, 예: 같은 시각 중복 예약) 충돌도 append 시점 UNIQUE로 나타날 수 있다 … 도메인 유니크가
      append UNIQUE와 물리적으로 같은 제약을 공유하면 두 해석을 채번 계층에서 분리할 수 없다.' 즉 C37은 문서가 스스로 인정한 미해소 약점이다.
    - **DESIGN-006** (§Weakness 말미 각주 (line 282)) — '본 절은 리뷰용 반박 정리이며, 문서의 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상.' — C37은 결정으로 닫힌
      게 아니라 후속 검토로 명시 이월된 상태.
    - **ADR-22(파일 16.optimistic-concurrency-control)** (§Consequences 마지막 불릿 (line 77)) — C37이 겨냥하는 실제 결정: '
      DuplicateKeyException → 동시성 충돌 매핑(시퀀스 채번 단일화).' append 시점 중복키를 일괄 동시성으로 매핑한다는 규칙이 명시돼 있고, 이는 채번 단일화 때문에 도메인 유니크
      위반과 구분이 불가하다는 C37의 우려 지점 그 자체다.
    - **DESIGN-013** (§4.3 멱등 흡수 표 (line 74-75) 및 §6.1/§4.4) — 현 설계의 방어 논리: 도메인 유니크(같은 슬롯 두 번 예약)는 물리 UNIQUE 인덱스가 아니라 '락
      잡고 reload 후 도메인 판단'으로 걸러 확정적 422/409로 거절(line 75, 88, 93)하고, (aggregate_id, sequence_no) UNIQUE는 락 유실 edge의 동시성
      백스톱으로만 쓴다(line 74, 89). 즉 두 제약을 물리적으로 분리하는 것을 전제한다 — 그러나 이 전제가 깨지는(자연키 UNIQUE 도입) 경우의 해석 분리는 규약으로 못박지 않았다.
    - **DESIGN-013** (§Risks (line 149)) — '도메인 멱등 흡수가 누락된 생성 command … 별도 디듀프 메커니즘은 구현 사이클에서 적용' — 유니크 불변식 없는 생성의 처리
      방식을 구현 사이클로 미뤄, C37이 지적하는 생성-측 중복/거절 해석 경계가 미확정임을 확인.
    - **RFC-014** (§Background·§Alternatives (line 60, 87)) — 동시성 축(비관 락 + UNIQUE 백스톱, 낙관 기각의 사유가 핫 스트림 retry storm)은
      확정하나, 도메인 유니크 위반과 동시성 위반을 append/채번 계층에서 어떻게 코드로 분리 판정할지는 다루지 않는다 — C37의 물리 제약 공유 시 분리 불가 문제를 해소하는 서술 없음.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C38 — 핫 슬롯 직렬화 구간이 사가 다단계 트랜잭션 길이가 되고 granularity 완화는 순환에 빠진다

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-006 §6.3, D-006 §4.2·§4.4
- **설명**: 인기 슬롯의 모든 시도가 한 락에 직렬화되는데 예약 확정은 timetable·payment·reservation 세 애그리거트를 사가로 잇는다. 핫 슬롯에선 사가 전체가 슬롯 락 뒤에 줄서 직렬화
  구간이 단일 INSERT가 아니라 다단계 분산 트랜잭션 길이가 된다. 슬롯보다 잘게 쪼개면 동시 N석 교차 불변식이 다시 사가로 넘어가 같은 문제로 회귀한다. 불변 apply 리플레이 비용이 핫 스트림에서 경합과
  곱해지나 정량화가 없다.
- **판정 근거**: C38의 세 갈래는 결정 상태가 갈린다. (1) "핫 슬롯에서 사가 전체가 슬롯 락 뒤에 줄서 직렬화 구간이 다단계 분산 트랜잭션 길이가 된다"는 전제는 아키텍처가 명시적으로 반박한다.
  ADR-16과 RFC-014는 락 범위 = 단일 aggregate_id로 못박고, 락이 감싸는 구간은
  `lock(aggregate_id) → load(replay)=N → handle → append(N+1) → release` 하나의 append뿐이다. timetable→payment→reservation
  사가는 최종 일관성(이벤트 구동)으로 락 바깥에서 돈다 — "즉시 일관성(한 애그리거트)=비관 락, 최종 일관성(여러 애그리거트)=사가, 전역 락 금지". 즉 슬롯 락 뒤에 줄서는 것은 timetable 슬롯
  애그리거트의 SeatHeld append 하나이지 사가 3단계가 아니다. 따라서 직렬화 구간=다단계 분산 트랜잭션 길이라는 전제는 결정된 락 경계와 모순되어 이 부분은 answered. (2)
  granularity 완화가 순환에 빠진다는 지적은 문서가 인지하지만 명시적으로 미결/위임 상태다 — ADR-16 미결정 §의 "🌱 핫스팟 슬롯 granularity와 교차-슬롯 불변식의 사가 위임 균형"은
  이벤트 스토밍·05-aggregate-design으로 위임, RFC-014는 granularity 긋기를 아예 scope 밖으로 뺐다. 즉 긴장은 명명됐으나 해소 결정은 없다. (3) 불변 apply 리플레이
  비용 × 핫 스트림 경합의 정량화는 어디에도 없다 — ADR-16은 비용을 "학습 환경에선 명목상"이라 단언할 뿐이고 스냅샷 처리도 구현 사이클 미결정으로 남겼으며, RFC-008조차 절대 임계 숫자를 측정으로
  위임한다. 헤드라인(사가-길이 직렬화)은 반박됐지만 실질 꼬리 두 갈래(granularity 균형, 정량화)가 명시적으로 열려 있어 부분 결정으로 판정한다.
- **인용**:
    - **ADR-16** (결정사항 · 부속 규칙 (락 범위)) — '락 범위 = 단일 aggregate_id만. 전역 락 금지. 여러 애그리거트에 걸친 불변식은 전역 락이 아니라 사가로 흡수한다... 즉시
      일관성(한 애그리거트)=비관 락, 최종 일관성(여러 애그리거트)=사가.' → 사가는 락 바깥·최종 일관성이므로 슬롯 락 뒤에 사가 3단계가 직렬화된다는 전제를 반박.
    - **ADR-16** (결정사항 · L1 (경합 직렬화 1차)) — 락이 감싸는 구간을 'lock(aggregate_id) → load(replay)=N → handle → append(N+1) →
      release'로 명시 — 직렬화 구간은 단일 애그리거트의 append 하나이지 다단계 분산 트랜잭션이 아니다.
    - **ADR-16** (미결정 사항 및 추가 논의 (🌱 항목)) — '🌱 핫스팟 슬롯 granularity와 교차-슬롯 불변식의 사가 위임 균형 — 이벤트 스토밍·05-aggregate-design.' →
      granularity 순환 긴장을 인지하나 미결로 위임(open).
    - **ADR-16** (granularity 위임 부속 규칙) — '락 단위=애그리거트=직렬화 단위. 핫스팟 경계(슬롯·좌석)는 경합 범위를 줄이는 방향으로 잘게 식별 — 경계 결정은
      05-aggregate-design·이벤트 스토밍(🌱).' — 경계 결정 자체를 위임.
    - **RFC-014** (비목표(Non-goals) §75-76) — '애그리거트 경계(granularity)를 긋는 일 — ...05-aggregate-design·이벤트 스토밍에 위임. 이 RFC는 그
      경계가 동시성에 갖는 의미만 명시.' 및 '교차 애그리거트 일관성을 동시성 제어로 푸는 것 — 사가의 몫.' → granularity 해소는 scope 밖.
    - **RFC-014** (결정 R3 · 요약 표) — '락 범위 = 단일 aggregate_id, 직렬화 수용, 교차는 사가, 전역 락 금지' — 직렬화 수용 대상은 단일 애그리거트 경합이며 교차는 사가(락
      외부).
    - **ADR-16** (트레이드오프 · 선택한 방식의 한계) — '무경합 쓰기에도 락 획득 비용 — 학습 환경에선 수용, 실트래픽 시 재검토 트리거.' 리플레이×경합 비용을 정량화하지 않고 '명목상'으로만
      처리 → 정량화 open.
    - **ADR-08** (결정사항 · 적용 표) — 예약 확정(점유→결제→확정) 3스텝을 코레오그래피(각 컨텍스트 이벤트 반응)로 조율 — 사가 단계 간 결합이 이벤트 기반 비동기임을 확인, 락으로 묶이지
      않음.
    - **RFC-008** (결정 6 / 관련 §153-159) — '절대 임계 숫자는 측정해 보기 전엔 의미가 없다... 절대 임계는 RFC-007 측정 트리거로 위임.' → 핫 스트림 비용의 정량 임계는
      미측정·위임(open).
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C39 — 단일 Redis durability 등급 전제가 멱등 디듀프 must-not-evict 성격 및 미확정 상위 결정과 모순

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-018 §4.4, D-018 §6.5
- **설명**: 디듀프 키가 allkeys-lru로 윈도 내 evict되면 막으려던 결제·예약 중복이 다시 열리므로 준-must-not-evict인데, 결정 3은 '손실 허용 상태뿐'이라며 단일 등급으로 충분하다
  단언해 volatile-lru+명시 TTL 분리를 하지 않는다. 이 단순화 전체가 'DESIGN-017이 refresh를 들어내 must-not-evict가 사라졌다'에 얹혔으나 DESIGN-017의 즉시 폐기
  포기는 'denylist 부활' 미결 조건을 달고 있어, 부활 시 durability·인스턴스·eviction을 통째 재설계해야 한다.
- **판정 근거**: 이 우려는 결정 문서들이 이미 명시적으로 인지하고 경계를 그어놓았으나, 정작 그것을 닫는 정책은 후속으로 미뤄져 있어 partially-decided다.

1) 디듀프 키의 준-must-not-evict 성격 vs allkeys-lru 단일 등급의 자기모순: DESIGN-018이 스스로 §4.4(라인 119 ⚠ 경고)·§6.2(라인 147)에서 "디듀프 키가 윈도 내
   evict되면 막으려던 중복이 다시 열린다"를 인정하고, 심지어 §Weakness 5번(라인 261)이 이 우려와 거의 동일한 문장이다. ADR-19 라인 32는 락(lock)에 대해서는 "liveness지
   safety가 아니고 (aggregate_id,sequence_no) UNIQUE가 백스톱이라 allkeys-lru로 evict돼도 정확성 안전"이라고 해소했으나, 멱등 디듀프에는 그런 UNIQUE 백스톱이
   없다(디듀프의 존재 이유가 자연 유니크 불변식 없는 생성 command 보호). 디듀프의 eviction/정확성 문제는 해소가 아니라 후속으로 명시 연기됨: DESIGN-018 §8 Phase 3, ADR-19
   라인 59·61(키 구성·윈도·maxmemory-policy 확정 = 후속). 우려가 요구하는 volatile-lru+명시 TTL 분리는 "allkeys-lru(또는 volatile-lru)"로 미선택 상태.

2) 상위 결정(denylist 부활) 취약성: DESIGN-017 §4.3 예외(라인 78)·§5.2(라인 88)·§6 후속(라인 97), ADR-19 라인 49·53, RFC-018 라인 130 모두
   denylist 부활을 "요구 입증 시에만"의 조건부/미결 트리거로 두고, 부활 시 durability·인스턴스·eviction 통째 재설계가 필요함을 명시. 즉 단순화가 미확정 상위 조건에 얹혔다는 우려의
   사실관계는 문서들이 그대로 확인해 준다.

결론: 긴장은 여러 문서가 이름 붙이고 부활 경로까지 확보했으나(=touched), 디듀프 eviction 정책 확정·volatile-lru+TTL 분리 여부·denylist 부활 대응은 후속 사이클로 열려 있어
gap이 남는다.

- **인용**:
    - **DESIGN-018** (§4.4 Data Model, eviction 정책 (라인 108-119)) — maxmemory-policy = allkeys-lru(또는 volatile-lru) 단일.
      그리고 ⚠ 경고: '멱등 디듀프 키가 allkeys-lru로 윈도 내 eviction되면 막으려던 중복을 도로 연다 — 키 구성/윈도와 eviction 정책의 상호작용을 후속 사이클에서 확정해야 한다(
      §8).' 우려가 지적한 자기모순을 문서가 스스로 인정하고 후속으로 연기.
    - **DESIGN-018** (§Weakness 5번·6번 (라인 261, 263)) — 5번: '멱등 디듀프 키를 allkeys-lru에 태우는 것은 정책 자기모순... 디듀프 키는 윈도 동안
      evict되면 안 되는 준-must-not-evict 성격인데 결정 3은 손실 허용뿐이라 단언... volatile-lru+명시 TTL로 분리하지 않으면 결제/예약 중복이 비기능적 사건에 좌우된다.'
      6번: '인증 부산물 제거가 durability 단순화의 유일 근거라 되돌림 비용이 크다... DESIGN-017의 즉시 폐기 포기는 denylist 부활 미결 조건을 달고 있다.' 이 우려(C39)와
      거의 동일 문장. 리뷰용 반박 정리이며 결정을 뒤집지 않고 후속 검토 대상으로 남김(라인 265).
    - **DESIGN-018** (§8 Milestones Phase 3, Phase 5 (라인 191, 193)) — Phase 3: '멱등 디듀프 Redis 키 구성·윈도 길이 확정. eviction 정책
      상호작용 검증.' Phase 5(조건부): 'denylist 부활 시 등급별 인스턴스 분리 재설계.' 디듀프 eviction 정책과 부활 대응 모두 후속/조건부로 미확정.
    - **DESIGN-018** (§6.5 Migration/Rollback 되살아남 조건 (라인 170-172)) — 'DESIGN-017에서 즉시 폐기 요구가 입증돼 denylist가 부활하면
      must-not-evict 등급이 되살아나 단일 durability·단일 인스턴스 가정이 깨진다. 그때 등급별 분리 정책·인스턴스 분리를 RFC-007과 재검토.' 단순화가 상위 미결 조건에 얹혔음을
      문서가 확인.
    - **ADR-19** (결정 라인 32 (분산 락 정확성 논거)) — 락에 한해 'liveness(경합 완화)지 safety가 아니다 — 정확성은 (aggregate_id, sequence_no)
      UNIQUE가 백스톱, 단일 인스턴스·allkeys-lru(락 키 evict 가능)·페일오버가 정확성을 위협하지 않는 건 그 때문.' 이 안전 논거는 락에만 적용되고 멱등 디듀프에는 해당 UNIQUE
      백스톱이 없어 동일 방어가 성립하지 않음.
    - **ADR-19** (후속 항목 라인 59, 61 + 되살아남 라인 49, 53) — 라인 59: '요청-단 멱등 디듀프의 Redis 키 구성·윈도 길이 — 후속.' 라인 61: '
      maxmemory-policy 확정(allkeys-lru/volatile-lru 계열) — denylist 부활 시 등급별 분리·인스턴스 분리 재검토.' 라인 49/53: denylist 부활 시 단일
      durability·단일 인스턴스 가정 파괴, 기능별 분리는 부활 시에만 재검토. eviction 정책·분리 여부 미확정.
    - **RFC-018** (논점 4 결정·결론 (라인 128, 130)) — '인스턴스 분리는 추후 진행.' 결론: 'denylist가 부활하면 must-not-evict 등급이 되살아나고 인스턴스 분리
      논의도 함께 되살아난다... maxmemory-policy 확정은 단일 등급만 남았으므로 allkeys-lru/volatile-lru 계열로 단순화해 Design에서.' 정책 확정 자체를
      Design/후속으로 넘김.
    - **DESIGN-017** (§4.3 예외(라인 78)·§5.2(라인 88)·§6 후속(라인 97)) — '즉시 강제 로그아웃이 도메인·규제 요구로 실제 입증되면 그때만 must-not-evict 등급을
      되살려 denylist를 둔다. 모델 기본은 폐기 없음, 예외는 요구가 입증될 때만.' 라인 97: '즉시 폐기가 요구로 입증될 경우의 denylist 부활 트리거'가 후속 항목. 상위 결정이
      조건부/미결임을 확인 — 우려의 두 번째 축 사실관계 입증.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C41 — read model 캐시 거부→전용 프로젝션 방침의 영구 운영·조합 폭증 비용이 저울질되지 않았다

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-004 §4.8, D-018 §4.2
- **설명**: 핫 쿼리를 Redis 한 겹 대신 화면 전용 프로젝션 추가로 돌리면 백필·재구축·다중 소스 갱신·구독·스키마가 영구 자산으로 남고, 파라미터 많은 쿼리(식당×날짜)는 조합이 폭발한다. 캐시는 배포로
  롤백되지만 프로젝션은 영구 부채다. 2차 staleness 회피 이득이 프로젝션 N배의 운영·정합성 비용을 정당화하는지 측정이 없다.
- **판정 근거**: 방침 자체(캐시 거부→핫 쿼리는 전용 프로젝션 1차, 캐시는 측정된 예외 패턴에만)는 ADR-19·RFC-018·DESIGN-004/018에서 확정됐다. 그리고 C41이 지적하는 두 비용 축이
  문서에 명시적으로 인지돼 있다: (1) ADR-19 트레이드오프 §47-48이 "프로젝션 재설계 vs 캐시 한 겹" 손익분기는 측정 후에야 갈리고 핫 쿼리마다 전용 프로젝션을 늘리면 프로젝터·저장 비용이
  증가한다고 적시했고, (2) DESIGN-004 §Weakness(line 187)는 C41을 거의 그대로 재진술한다 — 백필/최종일관성/다중소스 갱신이 영구 자산으로 남고 캐시는 배포로 롤백되나 프로젝션은 영구
  부채이며 "N배 운영·정합성 부담을 사는 트레이드오프를 문서는 저울질하지 않는다". 그러나 결정적으로 그 저울질(측정·손익분기 임계·트리거)은 ADR-19 미결정(§57)·RFC-018 Non-goal(§68)
  에서 Design/운영으로 명시 위임됐고, DESIGN-004 §Weakness는 line 191에서 "문서의 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상"이라 스스로 미해결 후속으로 표시한다. 파라미터
  조합 폭증(식당×날짜)의 정량 평가와 영구 부채 대 롤백 비대칭의 실제 비교는 어디에도 없다. 따라서 방침·정성적 트레이드오프는 인지·확정됐으나(→ 단순 open 아님), C41이 요구하는 "정당화 측정"은
  명시적으로 미결·후속 위임 상태다 → partially-decided.
- **인용**:
    - **ADR-19** (§트레이드오프 > 선택한 방식의 한계 (line 47-48)) — '프로젝션으로도 비싼 진짜 핫 패턴이 있으면 프로젝션 재설계 vs 캐시 한 겹의 손익분기가 측정 후에야 갈린다 —
      그때까지 캐시 없음' 및 '핫 쿼리마다 전용 프로젝션을 늘리면 프로젝터·저장 비용이 증가(캐시 한 겹 대비)' — 두 비용을 인지하되 정량 평가는 측정 후로 미룸.
    - **ADR-19** (§미결정 사항 (line 57)) — '캐시를 둘 프로젝션이 싸게 못 푸는 패턴 식별 — 읽기 분포 측정 트리거·손익분기 임계(RFC-002 측정 정책과 연동)'로 손익분기 측정을
      Design/운영에 명시 위임.
    - **DESIGN-004** (§Weakness Devil's Advocate 4번째 bullet (line 187)) — C41을 거의 그대로 재진술: 새 프로젝션은 백필 재구축·최종일관성 지연·다중소스
      갱신을 영구 자산으로 지고, 캐시는 배포로 롤백되나 프로젝션은 영구; '캐시 staleness 두 겹을 피하려고 프로젝션 N개의 운영·정합성 부담 N배를 사는 트레이드오프를 문서는 저울질하지 않는다'.
    - **DESIGN-004** (§Weakness 말미 (line 191)) — '본 절은 리뷰용 반박 정리이며, 문서의 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상' — C41 축의 비용 저울질이
      미해결 후속임을 명시.
    - **RFC-018** (§Non-goal (line 68)) — '캐시를 정말 둘 프로젝션이 싸게 못 푸는 패턴의 식별과 손익분기 임계 — 읽기 분포 측정 트리거로 Design/운영에 위임' —
      측정·저울질을 이번 결정 범위에서 제외.
    - **DESIGN-018** (§4.2 결정1 우선순위3 및 §8 위임 (line ~86)) — 핫 쿼리 1차=전용 프로젝션 추가, 캐시는 '측정으로 드러난 최후'로 순위 고정하되 '식별 트리거·손익분기
      임계는 §8 후속으로 넘기는 것'이라 명시 — 정량 평가 미수행.
    - **RFC-002** (§측정 트리거 정책 (line 132-136)) — 프로젝션 지연/비용 관련 절대 수치는 실제 lag 측정 전엔 근거 없다며 '측정 트리거와 정책 형태만' 확정, 절대값은 운영
      튜닝으로 위임 — C41이 요구하는 비용 측정이 아직 수행되지 않았음을 뒷받침.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C42 — 컨텍스트 경계가 잠정값인데 하위 설계가 이미 결합됐고 수직 분산이 미래 분할을 막는다

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-001 §4.4, D-002 §4.1
- **설명**: schedule·menu 경계를 잠정으로 두면서 DESIGN-003은 이미 세 그룹으로 서로 다른 쓰기 메커니즘을 확정해, 스토밍 후 ES 전환 시 상태 테이블→이벤트 스토어 재작성이라는 값비싼
  되돌림이 발생하나 잠정 라벨이 이 비용을 회계하지 않는다. 한 컨텍스트가 core/application/adapter/infrastructure 4곳에 수직 분산돼 향후 마이크로서비스 분할이 사실상 불가능해져,
  C안 기각으로 얻었다던 분할 용이성을 스스로 반납했다.
- **판정 근거**: C42는 서로 다른 두 주장을 묶고 있어 판정도 갈린다.

(2) "수직 분산이 미래 마이크로서비스 분할을 막고, C안 기각으로 얻었다던 분할 용이성을 반납했다"는 ADR-01이 정면으로 이미 결정·해명한다. ADR-01은 Option C(컨텍스트-top, 도메인별 분할이
가장 쉬움)를 명시적으로 고려·기각했고, 채택한 Option B의 대가로 C42가 지적하는 바로 그 트레이드오프—"top-level 축이 CQRS라 한 도메인이 두 트리에 걸쳐 도메인별 서비스 분할이 비싸진다"—를
이름 붙여 자인하며 완화책(깨끗한 패키지 유지)까지 명문화한다(RFC-001 §대안·§내 의견도 동일). 오히려 C42의 전제가 뒤집혀 있다: 분할 용이성을 준 쪽은 기각된 C이지 채택된 B가 아니며, ADR-01은
B가 분할을 *더 비싸게* 만든다고 인정한다. 즉 "반납했다"는 프레이밍은 문서가 이미 알고 선택한 트레이드오프다. 다만 ADR-01은 "비싸짐"이라 하지 "사실상 불가능"이라 하지 않아 C42의 강한 표현과는 정도
차가 있다.

(1) "schedule·menu 경계가 잠정인데 DESIGN-003이 세 가지 쓰기 메커니즘을 이미 확정해, 스토밍 후 비-ES→ES 전환 시 상태테이블→이벤트스토어 재작성이라는 되돌림 비용이 발생하나 잠정 라벨이
이를 회계하지 않는다"는 부분은 touched-but-gap이다. 잠정 라벨(ADR-02 §미결정, DESIGN-001 §4.4) 및 세 갈래 분류의 인지비용 트레이드오프(ADR-02 §트레이드오프)는 인정돼 있고,
상태→이벤트 전환 메커니즘 자체는 RFC-013(genesis 이벤트)에 설계돼 있어 되돌림 경로는 존재한다. 그러나 어떤 문서도 "잠정 경계가 ES로 뒤집힐 때 이미 구축한 상태 테이블을 이벤트 스토어로 재작성하는
비용"을 명시적으로 계상하거나, 스토밍 전에 쓰기 메커니즘을 지금 확정하는 것이 조숙한지를 그 되돌림 비용과 교차 검토하지 않는다. 오히려 DESIGN-003 Weakness 절 마지막 항목의 검토는 Payment의
ES 승격을 "분류 재검토 항목으로 올릴 것"이라 남겨두어 재분류가 실제 발생 가능함을 시사하나, 재분류 비용의 회계는 여전히 공백이다.

두 주장 중 하나는 완전 해결, 하나는 공백 잔존 → 종합 partially-decided.

- **인용**:
    - **ADR-01** (01.cqrs-command-query-module-split.md · 고려된 옵션(Option B/C) · 트레이드오프 §선택한 방식의 한계) — Option C(컨텍스트-top)
      를 '도메인별 분할이 가장 쉽다'로 두고 기각, Option B 채택. B의 명시 한계: 'top-level 축이 CQRS라 나중에 도메인별 서비스 분할이 비싸진다(한 도메인이 두 트리에 걸침)'. '
      도메인을 깨끗한 패키지로 유지하면 수용 가능… 이 트레이드오프를 알고 선택'. → C42의 분할-용이성 반납 주장을 정면으로 다룸(다만 '비싸짐'이지 '불가능'은 아님).
    - **RFC-001** (RFC-001-v2-cqrs-and-event-sourcing.md:127-129,141) — 풀 버티컬은 'top-level 축이 CQRS라 도메인별 분할이 비싸진다',
      컨텍스트-top은 '도메인별 서비스 분할이 쉽다'고 대비. §내 의견: '풀 버티컬의 유일한 약점은 도메인별 분할 비용인데 각 모듈 안에서 도메인을 깨끗한 패키지로 갈라두면 대부분 상쇄'. C42의 두
      번째 주장에 대한 근거·완화가 이미 기록됨.
    - **ADR-02** (02.selective-event-sourcing-scope.md · §미결정 사항 · §트레이드오프) — '경계가 모호한 schedule·menu 의 분류는 이벤트 스토밍 재실시 후
      재검토'라고 잠정성을 명시. 트레이드오프로 '쓰기 모델이 세 갈래라 컨텍스트마다 패턴이 다르다(인지 비용)'와 'restaurant ES 편입은 과할 수 있다(검토 후 결정)'를 인정. 잠정 라벨과 세
      갈래 분류 비용은 인정하나, 비-ES→ES 전환 시의 재작성 비용은 계상하지 않음.
    - **DESIGN-001** (DESIGN-001-design-overview.md §4.4 컨텍스트 분류) — 세 분류표(진짜 ES / 상태+Outbox / 현행)와 함께 '분류 경계(특히
      schedule·menu)는 이벤트 스토밍 재실시 후 재검토 가능'을 각주. 잠정 경계와 확정된 쓰기 모델이 같은 문서에 공존하나 되돌림 비용은 미기재.
    - **DESIGN-003** (DESIGN-003-write-model.md §4.1~4.3 · §7 Risks · Weakness 절 마지막 항목) —
      reservation·timetable·restaurant=이벤트스토어, schedule·user·authenticate=상태+Outbox, menu·category·company=현행으로 세 가지 쓰기
      메커니즘을 확정. §7 Risk는 카탈로그 미확정만 다루고 재분류/재작성 비용은 없음. Weakness 검토는 Payment의 ES 승격을 'DESIGN-001 §4.4 분류 재검토 항목으로 올릴 것'이라
      남겨 재분류가 실제 가능함을 시사 — 그러나 그 되돌림 비용 회계는 여전히 공백.
    - **RFC-013** (RFC-013-data-migration-genesis-events.md §논점1(genesis)·Report 행0) — 상태 테이블 → 이벤트 스토어 변환 메커니즘(genesis
      단일 이벤트)이 설계돼 있어 비-ES→ES 전환의 되돌림 경로는 존재. 단 이는 V1→V2 마이그레이션 맥락이고 '잠정 경계 flip 시 재작성 비용을 지금 회계'하는 논의는 아님(V2는 클린 슬레이트
      전제).
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C43 — 컴파일 의존성 우위 원칙이 정작 컨텍스트 간 격리에는 적용되지 않는 자기모순

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-002 §4.6
- **설명**: 논리축이 '컴파일 의존성 > 정적분석'이지만 컨텍스트 간 직접 참조 금지는 서브모듈이 아니라 ArchUnit/Konsist로 강제한다. 실무에서 가장 자주 위반될 9개 컨텍스트 상호 참조 경계가
  정확히 문서가 열등하다고 규정한 수단에 의존한다. 원칙과 가장 중요한 경계 강제 수단이 어긋난다.
- **판정 근거**: 이 우려가 지적한 "왜 컨텍스트 간 격리는 서브모듈이 아니라 ArchUnit/Konsist로 강제하는가"라는 결정 자체는 문서에서 의식적으로 내려졌고 근거도 있다. ADR-01은 모듈 경계는
  Gradle로 1차 강제, 도메인 패키지 간(=컨텍스트 간) 경계는 ArchUnit/Konsist로 강제한다고 명시적으로 이원화한다. D-002 §4.5가 컨텍스트 간 직접 참조 금지를
  ArchUnit/Konsist로 두는 것을 규정하고, §5.3(C안: 컨텍스트별 독립 모듈)이 "컨텍스트 간 격리까지 컴파일 타임 보장"하는 대안을 명시적으로 검토한 뒤 "YAGNI, 9개 컨텍스트 규모에서 모듈
  수 폭발, 컨텍스트 간 격리는 ArchUnit으로 충분"이라며 기각한다. 즉 문서는 "컴파일 의존성 우위" 원칙을 도메인 순수성(계층 간 JPA·Spring 금지)에 한정 적용하고, 컨텍스트 간 격리에는 비용
  대비 효용(YAGNI)으로 ArchUnit을 택한 스코프드 트레이드오프로 처리했다. 결정은 명확하다. 다만 우려의 더 날카로운 논점 — 원칙 자체가 "가장 자주 위반될 경계에서 열등하다고 규정한 수단에 의존하니
  자기모순" — 에 대한 반박은 암묵적이다. 문서는 원칙을 보편 원칙이 아니라 순수성 강제용 스코프드 원칙으로 재해석해 우회할 뿐, 정면으로 "이 원칙은 계층 순수성에만 적용되며 컨텍스트 격리에는 적용되지 않는다"
  고 원칙의 적용범위를 선언하지 않는다. 실제로 §5.1(A안)과 리뷰 §5(line 302)에서 문서 스스로 이 모순을 인정 문구로 남겨둔 상태다. 게다가 감사 문서(analysis/04 line 145)는
  ArchUnit/Konsist 규칙이 아직 이름만 있고 미작성(어느 툴·테스트모듈 위치 미정)이라 "모듈 분리 정당화의 강제장치가 TODO"라고 지적한다 — 즉 열등하다는 수단조차 아직 구현되지 않아 격리 강제가
  현재는 공백이다. 결정 방향은 정해졌으나 원칙 적용범위의 명시적 정합화와 강제 수단 구현이 남아 partially-decided.
- **인용**:
    - **ADR-01** (§본문 Option B (line 36) + 미결정 사항 (line 59)) — 모듈 경계는 Gradle로 1차 강제, 도메인 패키지 간(컨텍스트 간) 경계는
      ArchUnit/Konsist로 강제한다고 강제 수단을 명시적으로 이원화. 단 'ArchUnit vs Konsist 도구 선택'은 미결정으로 남김.
    - **D-002 (DESIGN-002-module-structure)** (§4.5 경계 강제 (line 181-188)) — command-core/command-application 내 컨텍스트 간 직접
      참조 금지를 서브모듈이 아니라 ArchUnit/Konsist 패키지 레벨 규칙으로 강제, 위반 시 빌드 실패로 detekt/테스트 단계 편입한다고 규정.
    - **D-002 (DESIGN-002-module-structure)** (§5.3 C안 컨텍스트별 독립 모듈 (line 220-225)) — 컨텍스트 간 격리까지 모두 컴파일 타임 보장하는 대안을 명시
      검토 후 'YAGNI, 컨텍스트 간 격리는 ArchUnit으로 충분, 필요 증명되면 그때 분할'로 기각 — 우려가 문제 삼는 결정을 의식적으로 내림.
    - **D-002 (DESIGN-002-module-structure)** (§4.4 의존성 매트릭스 주석 (line 177)) — command-core의 순수성만 build.gradle에
      JPA·Spring 부재로 컴파일 타임 물리 보장 — '컴파일 의존성 우위' 원칙이 계층 순수성에 한정 적용됨을 보여줌.
    - **D-002 (DESIGN-002-module-structure)** (§5.1 A안 단점 (line 210) 및 리뷰 §5 (line 302)) — 문서 스스로 'core-module 안 컨텍스트 간
      격리는 ArchUnit 의존 — 컴파일 의존성>검증 규칙 논리와 모순'이라고 자기모순을 인정 문구로 남겨둠. 즉 원칙-수단 어긋남을 정면 정합화하지 않고 트레이드오프로만 처리.
    - **analysis/04-design-completeness-audit** (line 145) — ArchUnit/Konsist 규칙이 명명만 되고 미작성(어느 툴·테스트모듈 위치 미정) — 모듈 분리
      정당화의 강제장치가 TODO. 열등하다는 수단조차 아직 구현 공백.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C44 — 신규 기능 게이팅이 사업 우선순위와 교착하며 실행 가능한 답이 없다

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-002 §4.6, D-005 §4.4
- **설명**: '패턴 검증 전 신규 코드 금지'는 옳으나 급하면 '순서 뒤집힐 시 리스크 명시'로 물러서는데 이는 완화가 아니라 문제의 재기술이다. 레퍼런스 전환이 진행되는 몇 주 동안 신규 기능을 V1 구조와
  미확정 신구조 중 어디에 지어야 하는지 실행 가능한 답이 없다. 합성 이력 예외 판단을 사이클로 미뤄 첫 레퍼런스 사이클 규모가 아직 안 본 데이터에 걸린다.
- **판정 근거**: 두 갈래 모두 "기본 정책"은 RFC/ADR에서 결정돼 있으나, 개념이 지적한 실행 가능성 공백은 닫혀 있지 않다. (1) 신규 기능 게이팅: RFC-010 §5는 "신규 기능=별도
  command 컨텍스트, 레퍼런스 확정 이후 투입"으로 기본 순서를 결정하고, 사업 우선순위가 높으면 "패턴 미확정 리스크를 명시한 채 순서가 뒤집힐 수 있다"고 트레이드오프를 인정한다. 하지만 개념의 핵심
  비판대로 이 폴백은 완화가 아니라 문제의 재기술이며, 레퍼런스 전환이 도는 수 주 동안 신규 기능을 V1 구조에 지을지 미확정 신구조에 지을지에 대한 실행 가능한 규칙은 어떤 ADR/RFC에도 없다. ADR-06
  §미결정도 "신규 기능 투입 시점"을 미결로 남긴다. (2) 합성 이력 예외: RFC-013 §1은 genesis (a) 기본 + 컨텍스트별 (b) 예외로 결정하되 "(b) 적용 여부는 전환 사이클에서 데이터를
  보고 판단"으로 미룬다 — 판단 기준·범위 상한이 없어 첫 레퍼런스 사이클 규모가 아직 안 본 데이터에 걸린다는 개념의 지적이 그대로 성립한다. 실제로 이 개념은 DESIGN-005-migration.md
  §4.4 리뷰 지적(라인 177)에서 파생된 것으로, 그 리뷰 자체가 "순서·사이클 분할이 아직 안 본 데이터에 종속"됨을 명시한다. 따라서 결정은 존재하나 실행 가능한 답의 공백은 미해소 =
  partially-decided.
- **인용**:
    - **RFC-010** (§논점 5 결론 (line 153) 및 결정 요약 #5 (line 165)) — 신규 기능=별도 command 컨텍스트(신구조 네이티브), 레퍼런스 확정 이후 투입. 그러나 '사업
      우선순위가 전환보다 높으면 패턴 미확정 리스크를 명시한 채 순서가 뒤집힐 수 있고'로만 폴백 — 뒤집힐 때 어디에 지을지 실행 규칙은 없음(개념이 지적한 '재기술' 그대로).
    - **ADR-06** (미결정 사항 및 추가 논의 (line 62)) — '신규 기능(리뷰·포인트·신고) 투입 시점(전환된 패턴 위에서 추가)'을 명시적 미결로 남김 — 게이팅 시점의 실행 가능한 답 부재를
      ADR이 스스로 인정.
    - **RFC-013** (§논점 1 네 결정/결론 (lines 102, 104) 및 결정 요약 #1 (line 159)) — genesis 단일 이벤트 (a) 기본 + 컨텍스트별 (b) 합성 이력
      예외, '(b) 적용 여부는 전환 사이클에서 데이터를 보고 판단' — 판단 기준·범위 상한 없이 사이클로 미룸.
    - **DESIGN-005** (§4.4 리뷰 지적 (line 177)) — '§4.4 예외(합성 이력 복원) 판단을 전환 사이클로 미뤄 순서 결정이 불안정... reservation이 합성 이력을 요구하면
      레퍼런스 사이클이 예상보다 커지고 템플릿화 산출이 흔들린다' — 개념 C44 두 번째 갈래의 원출처, 미해소 공백임을 리뷰가 명시.
    - **DESIGN-005** (§4.4 본문 (line 91) / DESIGN_DOC 04-migration.md (line 63)) — '해당 여부는 전환 사이클에서 데이터를 보고 판단한다' — 예외 판단
      기준을 설계 단계에서도 확정하지 않고 사이클로 이월.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C46 — inbox 생략 자격·effectively-once가 검증 불가 전역 가정과 부분 보장에 기댄다

`HIGH` · 판정 **partially-decided** (conf high) · 처분안 **accept-new-rfc** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-008 §4.5, D-008 §6
- **설명**: '순서 역전 없음+자연 멱등 upsert' inbox 생략 자격은 파티션 키 가정에만 기대 파티션 증설·리밸런싱·relay 재시도가 국소적으로 깨며, 컴파일러가 강제 못 하는 사회적 규약이라 운영
  변경 시 read model이 조용히 오염된다. effectively-once는 upsert 가능한 상태 수렴에만 성립하는 부분 보장인데 §4.8이 부수효과 이중 발사를 인정하면서도 요약 표는 전 계층 동일
  보장인 듯 오도한다. 부수효과 보장은 inbox/outbox 래핑 부채지 전달 보장이 아니다.
- **판정 근거**: 메커니즘·정책은 잡혔으나 핵심 취약성은 미결로 열려 있다. (1) inbox 생략 정책 자체는 ADR-09 §결정과 RFC-003 논점1에서 "순서 역전 없음 + 자연 멱등 upsert 동시
  충족"으로 확정됐다. 그러나 concern이 지적한 핵심 — "어떤 컨슈머가 실제로 순서 무풍지대인가의 판정 기준"은 ADR-09 §미결정에 명시적으로 미해결(TBD)로 남았고, RFC-003 §85/§87은
  〔근거 확인/보강 필요〕를 달고 컨슈머별 검증을 Design으로 위임한다. (2) 파티션 증설·리밸런싱·relay 재시도가 "순서 역전 없음" 전제를 국소적으로 깨고, 생략 판단이 컴파일러 강제 불가한 사회적
  규약이라 read model이 조용히 오염된다는 지적은 D-008 자신의 Weakness(§260-262)에 devil's-advocate로 자기고발돼 있을 뿐 어느 ADR/RFC도 해소하지 않는다. 부분 완화로
  ADR-09가 (aggregate_id, sequence_no)를 "더 과거를 덮지 마라" 순서·버전 가드로 병용한다고 언급하고 ADR-16이 UNIQUE 백스톱을 두지만, 이는 append(쓰기) 측 가드이며
  프로젝터 read model의 생략 안전성과 연결돼 있지 않아 갭이 남는다. (3) effectively-once가 상태 수렴에만 성립하는 부분 보장이고 §4.8이 부수효과 이중 발사를 인정한다는 점은
  ADR-09 §88·RFC-003이 일관되게 "효과만 1회, 비-멱등 부수효과는 별도"로 다뤄 방향은 잡혔으나, §6 요약 표가 전 계층 동일 보장인 듯 오도한다는 문서 정확성 지적과 "부수효과
  보장=inbox/outbox 래핑 부채지 전달 보장 아님"이라는 재프레이밍은 어느 문서도 결정하지 않았다.
- **인용**:
    - **ADR-09** (09.event-ordering-and-delivery-guarantee.md §미결정 사항 (L98)) — 'inbox 보존 기간·정리(GC) 정책, 자연 멱등으로 inbox 를
      생략할 컨텍스트의 판정 기준'을 미결정으로 명시 — 생략 자격 판정 기준이 열려 있음을 ADR 스스로 인정.
    - **ADR-09** (결정사항 전달 절 (L63)) — 'Zero Payload upsert 는 자연 멱등이라 inbox 생략 가능(같은 애그리거트 순서가 §순서로 보장된다는 전제)' — 생략을 파티션 키
      순서 가정에만 명시적으로 의존시킴. (aggregate_id, sequence_no)는 dedup이 아닌 순서·버전 가드로 병용이라 하나 read model 생략 안전성과 연결 안 됨.
    - **ADR-09** (단점 (L88)) — 'effectively-once 는 효과만 1회 — 부수효과(외부 알림 발송 등)가 멱등이 아니면 별도 대책 필요' — 부분 보장임을 인정하나 §6 요약 표 오도
      지적은 미해소.
    - **RFC-003** (논점1 네 결정/결론 (L85-87)) — 'inbox 생략은 순서 역전 없음+자연 멱등 upsert 동시 충족 컨슈머만' 결정. 단 〔근거 확인/보강 필요〕 표시와 '어떤
      프로젝터가 실제로 순서 역전 무풍지대인지는 토폴로지에 달렸다 — Design에서 컨슈머별 검증'으로 검증을 위임 = 자격 검증 미완.
    - **RFC-003** (논점2 결론 (L102) / 미결(L70)) — '비-멱등 부수효과=inbox/부수효과 outbox 래핑 기본'. 컨슈머별 inbox 귀속·부수효과 유형별 귀속 확정을
      Design으로 위임 — 방향만, 자격/보장 프레이밍은 미확정.
    - **D-008** (§4.5 (L104-105) / §6 요약 표 (L222-223) / Weakness (L260-262)) — §4.5가 생략 자격을 규정하고 §6 표는 '프로젝터/부수효과 =
      effectively-once'로 균일하게 제시하나, 문서 자신의 Weakness가 '생략 자격이 검증 불가 전역 가정·사회적 규약에 의존, 증설·리밸런싱·relay가 국소 붕괴시켜 read model
      조용히 오염'을 devil's-advocate로 자기고발 — 즉 concern이 D-008 미해소 약점을 그대로 옮긴 것.
    - **ADR-16** (결정사항 L38 (L0 불변식)) — '(aggregate_id, sequence_no) UNIQUE = 진짜 단일-라이터 백스톱'. append 측 정확성 보장이며 프로젝터 read
      model의 순서 역전 하 생략 안전성을 직접 다루지 않음 — 부분 완화에 그침.
- **처분안(제안)**: accept-new-rfc
    - 근거: verdict 'partially-decided' + 심각도 HIGH 기준 자동도출 — 검토 필요

### C47 — CDC 전환 트리거가 전부 주관적이라 폴링 부채를 발화 못 시킨다

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-008 §4.9
- **설명**: 세 CDC 전환 트리거(폴링 SLI 위협, 듀얼 라이트 제거 요구, Connect 성숙도)가 모두 주관적이고 임계가 TBD다. SLI 절대값은 RFC로 위임, 성숙도는 자기 평가라 영원히 '아직
  아니다'로 미룰 수 있다. '트리거 없으면 부채로 굳는다'는 경고를 인용하면서 발화 가능한 임계를 하나도 못박지 못했다.
- **판정 근거**: 전환 트리거의 정성적 방향(폴링 시작 + 세 트리거 명문화)은 RFC-003 논점4에서 결정됐고 D-008 §4.9와 정확히 일치한다. 그러나 concern의 핵심 — '발화 가능한 정량
  임계'는 어디에도 없다. 트리거(1) SLI 절대값은 RFC-008이 RFC-007 측정 트리거로 위임하고, RFC-007은 다시 '절대 수치는 운영 실측 후 확정'으로 미룬다(전부 TBD). 트리거(3)
  Connect 성숙도는 ADR-12에서 '도입 시'로만 서술돼 자기평가에 의존, 임계 없음. RFC-003 결정에도 〔근거 확인/보강 필요〕가 달려 확정 미완이다. 즉 방향은 결정됐으나 concern이 지적한 '
  주관성·TBD 임계로 영구 미룰 수 있음'이라는 갭은 실제로 남아 있어 partially-decided.
- **인용**:
    - **RFC-003-messaging-delivery** (논점 4 "폴링이냐 CDC냐 — 그리고 언제 넘어가나" (L118-126, 결정표 항목 4 L187)) — 세 전환 트리거(폴링 지연 SLI
      위협 / 듀얼 라이트 제거 정합성 요구 / Connect 성숙도)를 D-008 §4.9와 동일하게 명문화하되, 정량 임계 없이 정성 방향만 잠금. 결정에 〔근거 확인/보강 필요〕가 붙어 확정 미완 표시.
    - **RFC-008-observability** (논점(L153-159), 배제(L91) "메트릭 절대 임계 숫자 → RFC-007 측정 트리거") — 트리거(1)의 근거가 될 consumer
      lag/프로젝션 지연 등은 이름·라벨·단위 카탈로그만 고정하고 절대 임계 숫자는 명시적으로 RFC-007 측정 트리거로 위임 — SLI 절대값 미확정.
    - **RFC-007-deployment-infra-ops** (원칙(L48) 및 배제/결정표(L67-69, L115, L146)) — "방향(정성)은 지금 잠그고 절대 수치는 운영 실측 후 확정" — 핵심
      SLI·알람 임계는 RFC-008로, 지연 허용치 절대값은 측정 후 확정. CDC 발화 임계로 쓸 수 있는 어떤 절대 수치도 이 문서에서 못 박히지 않음.
    - **ADR-12.kafka-hosting-msk-vs-self-managed** (L65 후속(폴링 relay → CDC 도입 시 self-managed Connect 운영)) — CDC 전환을 '
      Connect/Debezium 도입 시'로만 서술 — 성숙도 자기평가에 의존하며 발화 임계 없음.
    - **ADR-05.event-store-mysql-table** (미해결/후속 L59) — 트래픽 증가 시 CDC 전환 '기준'은 후속 과제로 열어둠 — 기준 자체는 미확정.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C49 — ES/비-ES 추상화 누수와 hexagonal/layered 비대칭이 read side 실제 복잡도를 과소평가

`MED` · 판정 **partially-decided** (conf med) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-001 §4.3, D-001 §4.3
- **설명**: ES/비-ES 차이를 쓰기 어댑터 세부로 격리한다지만 리플레이 지연·스냅샷 신선도·분산 락 특성 때문에 타임아웃·SLA·장애 모드가 컨텍스트 종류로 달라져 추상화가 샌다. query를
  layered로 얇게 둔 근거(DB→DTO)는 projector가 여러 contract 이벤트를 조인·집계·멱등 병합해 비정규 read model을 구성하기 시작하면 오히려 테스트·격리를 어렵게 해, 얇은 조회
  전제가 프로젝션 재구축·백필의 실제 복잡도를 과소평가한다.
- **판정 근거**: C49는 두 주장이 합쳐진 우려다. (1) ES/비-ES 차이가 리플레이 지연·스냅샷 신선도·분산 락 때문에 타임아웃·SLA·장애 모드로 새어 "쓰기 어댑터에만 격리" 불변식(D-001
  §4.3-3)이 깨진다. (2) query를 layered로 얇게 둔 전제가 projector의 조인·집계·멱등 병합·재구축·백필 실제 복잡도를 과소평가한다.

두 번째 주장은 사실상 이미 결정돼 있다. RFC-011이 프로젝션 재구축·catch-up·백필·멱등 병합(키 upsert + sequence_no 버전 가드 + 오프셋 추적 + blue-green 무중단
교체 + "구독 먼저→백필→멱등 봉합")을 정면으로, 상세하게 확정했다 — 정확히 concern이 "과소평가된다"고 지목한 그 복잡도를 명시적 운영 결정으로 소유한다. 또 RFC-002 §6 + ADR-03이
layered를 "projection=갱신(쓰기 경로) / service=조회(읽기 경로)"로 책임 분리해, projector 로직이 얇은 service를 오염시켜 테스트·격리를 어렵게 한다는 걱정을 구조적으로
갈랐다. ADR-04는 read model이 파생 구조라 재구축이 일상임을 이미 인정한다.

첫 번째 주장 중 쓰기 측 타임아웃·장애 모드 갈림(ES의 리플레이·분산 락)은 ADR-16/RFC-014가 lock-wait 타임아웃→409/503, 도메인 거절→422/409, UNIQUE 백스톱으로 확정했다.
그러나 concern이 정확히 겨눈 지점 — read 측 유스케이스 타임아웃·SLA·신선도가 "컨텍스트 종류(ES vs 비-ES)로 갈린다"는 read-path 추상화 누수 — 는 컨텍스트 종류별로 결정되지 않았다.
RFC-002는 전 읽기 경로에 단일 최종 일관성 베이스라인을 깔고, p99 절대값(§3)과 읽기 신선도 예외 정책(§2, 필요 시 신규 ADR)을 측정·후속으로 미뤘을 뿐, ES/비-ES 컨텍스트별 SLA·장애
모드 차등화를 다루지 않는다. D-001 §4.3 직후 Weakness 절도 이 항목을 "후속 검토 대상"으로 자인한다. 따라서 재구축·백필 복잡도(둘째 주장)와 쓰기 측 장애 모드는 해소됐으나, read-path의
컨텍스트별 SLA/신선도 추상화 누수라는 잔여 갭이 남아 partially-decided.

- **인용**:
    - **RFC-011** (프로젝션 재구축·catch-up 운영 — 논점 1·2·4·6 및 결정 요약) — projector 멱등 강제(키 upsert + sequence_no 버전 가드 + 프로젝션별 오프셋
      추적, dedup/순서 가드 분리), blue-green 무중단 재구축, '구독 먼저→백필→멱등 무버퍼 봉합', 스토어 리플레이+토픽 2단 구조를 확정. concern이 '과소평가된다'고 지목한 프로젝션
      재구축·백필·조인/집계/멱등 병합 복잡도를 정면으로 소유하는 결정.
    - **RFC-002** (§논점 6 및 결정 요약 #6) — query layered를 'projection=읽기모델 갱신(쓰기 경로) / service=조회(읽기 경로)'로 책임 분리, TX는
      service에서 닫고 projection 갱신은 메시징 소비 단위로 별도. layered 얇음이 projector 로직 테스트·격리를 어렵게 한다는 우려를 구조적으로 가름 — ADR-03이 미결로 남긴
      layered 세부(트랜잭션 경계·책임 분리)를 닫음.
    - **ADR-03** (결정사항 및 '미결정 사항 및 추가 논의') — command=hexagonal / query=layered 비대칭을 '의도된 경제성'으로 확정. 단 query 측 layered의
      세부 규약(트랜잭션 경계, projection과 service 책임 분리)을 명시적으로 미결정으로 남김 — 이 갭은 RFC-002 §6이 이후 채움.
    - **ADR-16** (결정사항·충돌 처리(L1 Redisson) 및 트레이드오프) — ES 쓰기 측의 분산 락·리플레이 경합에서 오는 장애 모드/타임아웃을 확정: lock-wait 타임아웃→409/503,
      도메인 거절→422/409, UNIQUE 백스톱. concern의 쓰기 측 장애 모드·타임아웃 갈림은 여기서 해소되나, read 측 SLA는 다루지 않음.
    - **RFC-002** (논점 2·3 및 결정 요약 #2·#3) — 읽기 신선도 = 기본 최종 일관성, read-your-writes 예외는 증명된 화면만(필요 시 신규 ADR), p99 지연 절대값은 측정
      후 튜닝. 전 읽기 경로에 단일 베이스라인을 적용할 뿐 ES/비-ES 컨텍스트 종류별 SLA·타임아웃·장애 모드 차등화는 결정하지 않음 — read-path 추상화 누수 갭이 잔존.
    - **D-001** (§4.3 설계 불변식 #3 및 직후 Weakness(Devil's Advocate) 절) — 'ES/비-ES 차이는 command 쓰기 어댑터에만'을 불변식으로 선언. 같은 문서
      Weakness 절이 리플레이 지연·스냅샷 신선도·Redisson 락 특성이 유스케이스 타임아웃·SLA·장애 모드로 새어 이 불변식이 read 측에서 무너진다는 항목과 layered 과소평가 항목을
      스스로 '후속 검토 대상'으로 자인 — 미해소 갭임을 문서가 인정.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C50 — 비-ES가 ES 데이터를 조인하는 문제가 첫 레퍼런스(예약 상세)에서 바로 터진다

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-004 §4.2
- **설명**: 비-ES가 ES 데이터를 조인해야 하면 '통일 압력 시 결정'이라 미루지만, 예약(ES)이 메뉴/카테고리(비-ES lookup)를 함께 보여주는 화면은 예약 상세에서 거의 확실히 발생한다.
  비-ES는 QueryDSL로 자기 테이블만 읽으니 예약 프로젝션이 메뉴까지 비정규화하거나 비-ES를 강제 ES화하는 두 나쁜 선택으로 수렴한다. 첫 레퍼런스 컨텍스트에서 바로 터질 결정을 리스크 표로만 미뤘다.
- **판정 근거**: 교차 컨텍스트 읽기의 '메커니즘'은 이미 결정돼 C50이 전제한 '두 나쁜 선택'을 대부분 해소한다. RFC-002 결정1 + DESIGN-004 §4.5 + DESIGN-003 §4.3이
  확정한 정식 패턴은: (1) 예약(ES) query 측 프로젝션이 외부 데이터를 비정규화하는 것은 안티패턴이 아니라 채택된 방식이고(식당명 예시가 곧 메뉴명에 그대로 적용됨), (2) 메뉴(비-ES lookup)
  는 '구독 필요 시 Outbox 발행'으로 published-subscription 소스가 되므로 조인도 강제 ES화도 아니다. 따라서 '조인해야 하는데 QueryDSL은 자기 테이블만 읽는다 → 나쁜 선택 둘로
  수렴'이라는 프레이밍은 문서 결정으로 반박된다. 다만 남는 진짜 gap: (a) 메뉴/카테고리의 실제 소유권과 projection vs published 항목별 귀속표가 구현 사이클로 명시 위임됨, (b)
  RFC-002 논점5의 '통일 압력'(비-ES를 ES로 통일할지)은 Design 검증 항목으로만 열려 있음, (c) DESIGN-004 §7 리스크 표가 예약 상세 케이스를 '그때 결정'으로 미룸. 메커니즘은
  결정, 구체 귀속·통일 압력 트리거는 미결이므로 partially-decided.
- **인용**:
    - **RFC-002** (논점 1 / 결정 요약 #1 (lines 87-111, 174)) — 교차 컨텍스트 lookup(category·company·menu)을 읽기 모델에 실현하는 수단을
      projection ∨ published-subscription 둘로 확정하고 소유자로 가른다. 조회 시점 동기 cross-context fetch는 금지. 즉 예약 화면이 메뉴 데이터를 함께 보여줄 때의
      메커니즘(비동기 로컬 카피)은 결정돼 있다 — 조인 자체를 하지 않고 예약 query 측이 메뉴 변경을 published-subscription으로 받아 로컬에 적재.
    - **DESIGN-004** (§4.5 교차 컨텍스트 예시 (lines 99-104)) — 예약 목록이 식당 이름을 보여줄 때: restaurant가 이벤트 발행 →
      query.reservation.projection이 구독해 자기 read model의 식당명 칼럼 갱신 → 예약 조회는 '조인 없이' 읽는다. 예약 프로젝션이 외부 데이터를 비정규화하는 것은 문서가
      채택한 정식 패턴이지 '나쁜 선택'이 아니다 — 메뉴명도 동일하게 처리 가능.
    - **DESIGN-003** (§4.3 현행/lookup (line 107)) — menu·category·company는 상태 테이블 유지하되 '다른 컨텍스트가 구독해야 할 때만 Outbox 이벤트를
      추가한다'. 예약 상세가 메뉴를 필요로 하면 메뉴가 Outbox를 발행하고 예약 query 측이 구독한다 — 비-ES를 강제 ES화하지 않고도 해결. 발행 경로는 ES/비-ES 동일(§4.4).
    - **ADR-04** (결정사항 (나) 경량 lookup 프로젝션 (lines 32-36)) — 저빈도 lookup은 query DB 안 경량 프로젝션 또는 lookup 컨텍스트가 published한
      테이블로 둔다. '비-ES도 Outbox로 발행하므로 ES와 같은 길.' command DB 직접 조회·replica 읽기 금지.
    - **RFC-002** (논점 5 / 결정 요약 #5 (lines 148-156, 178)) — 비-ES는 QueryDSL 유지. '비-ES 컨텍스트가 ES 컨텍스트의 데이터를 조인해 읽어야 하는 경우가
      있으면 통일 압력이 생긴다'를 Design 검증 항목으로 명시적으로 남김 — 통일 압력 자체는 미결로 위임.
    - **DESIGN-004** (§4.2(나) line 68 및 §7 Risks 표 (lines 155), Weakness 마지막 불릿 (line 189)) — 항목별 projection/published
      귀속표 확정은 'company·menu의 실제 소유권이 드러나는 구현 사이클에서'로 미룸. 리스크 표는 '통일 압력이 생기면 그때 결정'. 문서 자신의 Weakness 절이 C50과 동일한 반박(예약
      상세에서 바로 터진다)을 적어두었으나 결정을 뒤집지 않고 후속 검토로 둠.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C51 — 깨진 크로스레퍼런스가 CI 필수 게이트 강제력을 미확정 상류에 인질로 잡는다

`MED` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 MED

- **문서/항목**: D-012 §5.7
- **설명**: 다수 RFC·DESIGN 링크에 게이트 근거를 위임하지만 관련 문서 목록과 인용 번호 체계가 일관되지 않는다. '동적 분산 행위 게이트를 CI 필수로 건다'는 핵심 결정이 미확정이거나 번호가 어긋난
  RFC 결론에 기대, 게이트 강제력이 미확정 상류에 인질로 잡힌다.
- **판정 근거**: C51은 두 주장을 묶고 있다. (1) '동적 분산 행위 게이트 CI 필수' 핵심 결정이 미확정 상류에 인질로 잡힌다 — 이 부분은 이미 해결됨: 게이트 정책의 실소유자는 RFC-009(상태
  합의, 2026-06-23)이고 논점4·결정요약#4·결과 절에서 '결정적=CI 필수 / 무거운 통합=정기'로 명확히 확정한다. 그리고 §5.7이 인용하는 상류 RFC(003/004/006/011/012/013)가
  전부 합의 또는 종결 상태라 '미확정 상류' 전제 자체가 틀렸다. (2) 그러나 C51이 지목한 실제 결함 — D-012 상단 관련문서 목록과 §5.7/§5.5 본문 인용 번호 체계의 불일치(깨진
  크로스레퍼런스) — 는 어떤 ADR/RFC도 해소하지 않는 편집상 결함이며, D-012가 Weakness 절에서 스스로 자인한 그대로 남아 있다. 또한 ADR-14가 아직 Proposed라 최종 비준 고리도
  미완. 따라서 게이트 강제력의 실체적 결정은 already-decided이나, 참조 무결성 갭은 미해소로 남아 partially-decided.
- **인용**:
    - **RFC-009** (논점 4 (게이트 표, L102-111) 및 결정 요약 #4 (L151)) — '동적 분산 행위 6범주 — 결정적(멱등성·재생/스냅샷 등가·동시성)=CI 필수, 무거운 통합(
      재구축·사가·종단)=정기 단계'로 게이트 강제력을 명시 확정. 게이트 근거의 실소유자는 D-012가 아니라 RFC-009.
    - **RFC-009** (헤더 상태 (L3)) — 상태 '🏷 합의 (2026-06-23)'. 게이트 정책 결정이 확정 상태로 잠겨 있음 — 미확정 상류가 아님.
    - **RFC-009** (결과/목표 요약 (L166-189)) — 'CI 필수 게이트' subgraph에 아키텍처강제·property-based·계약·업캐스팅회귀·행위명세·결정적 동적행위를 배치; 무거운
      통합·k6·카오스는 정기/비-차단. 게이트 배치가 문서 내에서 완결.
    - **RFC-003·004·006·011·012·013** (각 RFC 헤더 상태) — §5.7이 인용하는 상류 RFC들이 모두 합의(003/004/006/011) 또는 종결(012/013) 상태.
      게이트가 '미확정 상류에 인질'이라는 전제가 실제로는 성립 안 함 — 상류는 확정됨.
    - **ADR-14** (헤더 상태 (L3)) — 테스트 전략 ADR은 여전히 'Proposed (2026-06-14)'. RFC-009가 '닫으면 14.testing-strategy 비준(
      Proposed→Accepted)'이라 명시했으나 아직 비준 전 — 최종 못박음은 미완.
    - **DESIGN-012** (§5.7 (L392) 및 Weakness 마지막 불릿 (L433)) — §5.7이 게이트 근거를 RFC-009에 위임하나, 상단 '관련 RFC' 목록(
      004/005/012/013/015/022/002)과 본문 §5.7·§5.5 인용(002~006/011/012)의 번호 체계가 불일치. 문서 스스로 Weakness에서 '깨진 크로스레퍼런스가 게이트 근거를
      공중에 띄움'이라 자인 — C51은 이 자인의 재진술.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 MED 기준 자동도출 — 검토 필요

### C04 — L1 분산 락이 append와 원자적이지 않아 정확성은 사실상 L0 UNIQUE에만 의존한다

`HIGH` · 판정 **already-decided** (conf high) · 처분안 **mitigate-note** · 우선 MED

- **문서/항목**: D-006 §4.1·§4.3, D-003 §4.1, D-013 §4.3, D-018 §4.3
- **설명**: Redis 락 획득과 MySQL append INSERT는 별 시스템의 별 트랜잭션이라 리스 만료·watchdog 미커버 창에 둘째 라이터가 진입할 수 있고, 이를 L0 UNIQUE가 흡수한다면
  L1은 정확성이 아니라 경합 완화에 불과하다. 여러 문서가 비관 락을 '정확성 의존'으로 승격하면서 동시에 최종 방어를 UNIQUE로 두는 자기모순을 반복한다. 정직하려면 L1을 가속 계층으로 재분류해야 한다.
- **판정 근거**: C04가 요구하는 결론('L1을 정확성이 아니라 가속/경합완화 계층으로 재분류하라')이 ADR-16과 RFC-014에 이미 확정된 정본 결정이다. 두 문서 모두 '분산 락은 liveness지
  safety가 아니다', '정확성은 락과 무관, L0 UNIQUE가 유일한 안전 불변식', 'L1/L1′은 경합 직렬화'라고 명시한다. 리스 만료·watchdog·페일오버·split-brain 창까지 열거하고 그
  창의 둘째 라이터를 L0 UNIQUE가 흡수한다고 못박는다. 따라서 C04가 전제한 '비관 락을 정확성 의존으로 승격하는 자기모순'은 최소한 ADR/RFC 층위에서는 성립하지 않으며 오히려 반대로 정리돼 있다.
  한 가지 유의: C04가 지목한 자기모순이 만약 존재한다면 그것은 멤버인 설계 문서(D-006/D-003/D-013/D-018) 본문 표현 층위이지 ADR/RFC 결정 층위가 아니다 — 결정 문서는 이미 C04와
  같은 입장을 취한다. 그 결정을 각 설계 문서가 정확히 반영했는지의 문구 정합성 검증은 별개 작업이나, '이미 결정됐는가'라는 질문에는 명확히 already-decided.
- **인용**:
    - **ADR-16** (16.optimistic-concurrency-control.md 제목 + §4.1 '락은 정확성을 보장하나' (line 29-30) + 결정 요약 (line 34)) — 제목부터 '
      비관 락(Redisson+DB 폴백) + UNIQUE 백스톱'. line 30: '분산 락은 liveness지 safety가 아니다... 락 키 evict·페일오버·리스 만료로 풀릴 수 있다 →
      UNIQUE를 정확성 불변식으로 유지(L0)'. line 34: '정확성은 (aggregate_id, sequence_no) UNIQUE가, 경합 완화는 분산 락이 맡는다 — 락이 정확성을 대체하지
      않는다.' 즉 C04가 요구하는 '락=가속/경합완화 재분류'가 이미 확정된 결정이다.
    - **ADR-16** (§4.3 3층 방어 (line 38-40)) — L0=안전 불변식(항상) UNIQUE '락 유실 edge에서 이중 append를 최종 거절하는 백스톱'. L1='경합 직렬화(1차)'
      Redisson, L1′='경합 직렬화(폴백)' DB FOR UPDATE. L1은 명시적으로 correctness가 아니라 '경합 직렬화'로 라벨링됨 — C04가 지적한 자기모순이 아니라 정확히 반대.
    - **ADR-16** (line 46, 55, 65) — line 46: '리스 만료·watchdog 미커버 창'에 해당하는 dual-provider split-brain을 'L0가 흡수 → 둘째
      append가 UNIQUE로 거절돼 정확성 유지(일시 비효율일 뿐 부정확 아님)'. line 55: '정확성은 락과 무관 — L0 UNIQUE가 락 유실·split-brain·폴백 전환 모두에서 이중
      점유를 구조적으로 막는다.' line 65가 Redisson 리스 TTL·watchdog을 명시 인지.
    - **RFC-014** (§4.3 (line 100) + 결정 표 (line 96-98) + 한줄요약 (line 154)) — line 100: 'Redisson 락은 liveness(경합 완화) 도구지
      safety(정확성) 보장이 아니다 — 리스 만료 등으로 풀릴 수 있으므로 L0 UNIQUE가 반드시 남아야 한다.' 표에서 L0='정확성 최종 심판', L1='라이터를 큐로 세움'으로 역할 분리.
      한줄요약: '잠그되 믿지 않는다 — ... UNIQUE가 최종 심판.' 상태: 🏷 합의 (2026-06-29).
- **처분안(제안)**: mitigate-note
    - 근거: C04가 요구하는 결론(L1을 정확성이 아니라 가속·경합완화 계층으로 재분류)은 ADR-16(line 30·34·38-40·46·55)과 RFC-014(line 96-100·149-154)에 이미
      정본으로 확정돼 있다 — L1/L1′='경합 직렬화(liveness)', L0 UNIQUE='정확성 최종 심판(safety)', 리스 만료·watchdog·split-brain 창의 둘째 라이터를 L0가
      흡수한다고 명시하므로 결정 층위의 자기모순은 성립하지 않고 오히려 반대로 정리돼 있다. 다만 C04가 지목한 자기모순이 남아 있다면 그것은 멤버 설계 문서(D-006/D-003/D-013/D-018) 본문
      표현이 이 확정 결정을 정확히 반영했는지의 문구 정합성 문제이므로, 각 문서에서 '비관 락=정확성 의존' 뉘앙스를 '경합완화, 정확성은 L0 UNIQUE'로 맞추는 note성 정합 검증이 적절하다. 이미
      confirm된 ADR을 다시 여는 새 RFC는 topical-RFC 규율에 어긋나므로 불필요하다.

### C06 — event_store와 Outbox의 트랜잭션 경계 공유 조건이 명시되지 않아 '원자적' 주장이 취약

`HIGH` · 판정 **already-decided** (conf high) · 처분안 **mitigate-note** · 우선 MED

- **문서/항목**: D-003 §4.2
- **설명**: 상태(또는 이벤트스토어)+Outbox 원자성은 둘이 같은 트랜잭션·같은 datasource일 때만 성립한다. ES 엔진이 독립 트랜잭션으로 append하면 이벤트는 저장되나 Outbox 실패 시
  발행이 유실된다. 동일 datasource 전제를 명문화하지 않았다.
- **판정 근거**: 우려의 핵심(ES와 Outbox가 원자적이려면 같은 트랜잭션·같은 datasource여야 하고, ES 엔진이 독립 트랜잭션으로 append하면 원자성이 깨진다)은 ADR-05에서 정면으로
  결정되어 있다. 이벤트 스토어를 별도 ES 제품(독립 트랜잭션·이중 datasource, Option A)이 아니라 기존 MySQL의 event_store 테이블로 직접 구현하기로 확정했고(합의됨
  2026-06-12), 그 결정 근거가 '기존 MySQL/Outbox 자산 재사용 → 한 트랜잭션 원자성'(L22/L39/L44)이다. 우려가 상정한 실패모드(ES 독립 append)는 바로 여기서 기각한
  대안이다. RFC-001·RFC-003도 '같은 트랜잭션'을 반복 명시하고, RFC-003 다이어그램은 이벤트 스토어+Outbox를 하나의 command DB 노드로 묶어 동일 datasource임을 구조적으로
  표현한다. D-003 §4.2 문장 자체도 ADR-05를 근거로 인용하며 BEFORE_COMMIT 같은 트랜잭션을 명시한다. 다만 '동일 datasource'를 명명된 불변식(invariant)으로 한 줄로
  못박은 문구는 없고, 단일 MySQL 테이블 결정에서 함의로 도출된다 — 결정 내용은 명확하므로 already-decided.
- **인용**:
    - **ADR-05** (05.event-store-mysql-table.md — 결정사항/장점 (L27, L39, L44), 고려된 옵션 (L19, L22)) — 이벤트 스토어를 '전용 제품(Option
      A)'이 아니라 기존 MySQL의 append-only 이벤트 테이블로 직접 구현하기로 결정. L39 '이벤트 저장과 통합 이벤트 발행을 한 트랜잭션으로', L44 '이벤트 저장·상태·Outbox가 한
      트랜잭션 → 원자성'. Option A를 뺀 근거가 바로 '기존 MySQL/Outbox 자산과 이중화'(L19) — 즉 별도 ES 엔진의 독립 트랜잭션 문제를 회피하려고 단일 MySQL(동일
      datasource)을 택한 것이 결정의 핵심. 우려가 든 실패모드(ES가 독립 트랜잭션으로 append)는 여기서 명시적으로 기각한 대안.
    - **RFC-001** (RFC-001-v2-cqrs-and-event-sourcing.md — 파이프라인 §L23-24, L31 다이어그램, L212-214, L223-224) — '같은 트랜잭션 안에서
      Outbox 테이블에도 대외용 이벤트를 적는다(원자성 보장)' (L23). 다이어그램 '③ 같은 TX'로 command→Outbox 표기. ES/비-ES 모두 command DB에 쓰고 동일
      Outbox→Kafka 경로.
    - **RFC-003** (RFC-003-messaging-delivery.md — 골격 §L20, 다이어그램 L27/L203) — 'command DB(이벤트 스토어 + Outbox)에 변경과 발행할
      이벤트를 같은 트랜잭션으로 적는다' (L20). 다이어그램에서 이벤트 스토어와 Outbox를 한 노드(command DB / '이벤트 스토어+Outbox')로 묶어 동일 datasource임을 구조적으로
      표현.
    - **DESIGN-D-003(02-write-model)** (design_doc/02-write-model.md §A/§공통 발행 경로 — L56, L63, L83-84) — 우려가 지목한 바로 그 문장(
      L84 '상태(or 이벤트스토어) + Outbox 원자적'). 같은 문서가 그 원자성의 근거를 명시: L56 '전용 제품 미도입… 근거 [[05.event-store-mysql-table]]',
      L63/L83 Outbox 기록이 상태 저장과 '같은 트랜잭션(BEFORE_COMMIT)'. 즉 이벤트 스토어=상태 테이블과 같은 MySQL이라는 전제가 §A(단일 event_store 테이블 정의)와
      ADR-05 인용으로 확립됨.
- **처분안(제안)**: mitigate-note
    - 근거: 우려가 상정한 실패모드(ES 엔진이 독립 트랜잭션으로 append)는 ADR-05가 별도 ES 제품(Option A)을 기각하고 기존 MySQL의 event_store 테이블로 직접 구현하기로
      확정한 바로 그 대안이라 결정은 이미 닫혀 있고, RFC-001/003·D-003이 '같은 트랜잭션'을 반복 명시하며 RFC-003 다이어그램은 이벤트 스토어+Outbox를 하나의 command DB
      노드로 묶어 동일 datasource를 구조적으로 표현한다. 결정 자체는 명확하므로 새 RFC나 결정 재개는 불필요하다. 다만 '동일 datasource·동일 트랜잭션'이 명명된 불변식으로 한 줄 못박히지
      않고 단일 테이블 결정의 함의로만 도출되므로, D-003 §A(또는 §공통 발행 경로)에 원자성이 성립하는 전제 조건을 명시적 invariant 한 줄로 추가하는 문서 보강이 적절하다.

### C12 — 혼합 패러다임(ES/비-ES) 사가와 보상 인터리빙이 원자성·순서를 훨씬 어렵게 하나 표시되지 않는다

`MED` · 판정 **partially-decided** (conf med) · 처분안 **mitigate-note** · 우선 LOW

- **문서/항목**: D-003 §4.1, D-007 §4.5
- **설명**: ES 예약이 비-ES schedule·ES restaurant와 얽히면 사가는 이벤트 스트림 진실원과 상태 테이블 진실원을 동시 조율해야 하고 보상의 원자성·순서가 두 패러다임 경계에서 더
  어려워진다. 멱등 보상+Zero Payload 재조회는 같은 이벤트 중복엔 안전하나 서로 다른 보상의 순서 역전(인터리빙)엔 안전하지 않다. 문서는 혼합 패러다임 사가가 더 어렵다는 점 자체를 표시하지 않고 하위
  문서로 넘긴다.
- **판정 근거**: 우려의 두 갈래를 나눠 판정한다.

(1) 기술적 실체 — "멱등 보상은 중복엔 안전하나 서로 다른 보상의 순서 역전(인터리빙)엔 안전하지 않다"는 지적: 이미 문서가 정면으로 인정·처리하고 있다. RFC-003 §논점(line 83)과 결정 요약(
line 184)은 자연 멱등 upsert(Zero Payload id 덮어쓰기)의 inbox 생략을 "순서 역전이 없을 때뿐"으로 못박고 "역전이 가능하면 오래된 값으로 덮어쓰는 사고가 난다"고 명시한다.
RFC-011(line 129)은 dedup(event_id inbox)과 순서 가드(sequence_no 버전 가드)를 다른 일로 분리하고, 특히 교차-애그리거트 카운터 같은 증분 갱신은 per-aggregate
가드로 멱등이 안 됨을 명시한다. RFC-021 §논점2(line 105)는 교차-애그리거트 인터리빙에 값이 달라지는 point-in-time 파생 사실을 유일 예외로 카빙하고, 생산 시점 페이로드 박제로
해소하며 "진짜 교차 불변식 강제는 사가 몫"으로 귀속한다. 즉 "멱등만으로 순서 역전을 못 막는다"는 우려의 핵심은 해결선이 그어져 있다.

(2) 프레이밍 — "ES/비-ES 혼합 패러다임 경계에서 사가가 이벤트 스트림 진실원과 상태 테이블 진실원을 동시 조율해야 해 보상의 원자성·순서가 더 어렵다는 점 자체가 표시되지 않는다": 여기엔 gap이
남는다. ADR-02·ADR-07·RFC-001은 ES/비-ES를 "저장 방식 차이일 뿐 대외 발행 경로(Outbox→Kafka)는 동일"로 균질화하고, ADR-08·RFC-006은 사가 보상을 패러다임 구분
없이 "각 컨텍스트가 자기 aggregate 상태로 자기 보상 판단"으로 일원 처리한다 — 진실원이 스트림이냐 테이블이냐가 보상 난이도를 바꾼다는 서술은 없다. RFC-006 §논점4(line 125)는 부분 보상
상태(SeatReleased 됐는데 PaymentRefunded 실패)를 PoisonMessage가 담을 수 있는지 TBD로 하위 넘김 — 우려가 지적한 "하위 문서로 넘긴다"가 실제로 확인된다. 따라서 기술적
위험은 대부분 커버되지만, "혼합 패러다임 자체가 사가 보상을 더 어렵게 한다"는 명시적 표시는 없다 → partially-decided.

- **인용**:
    - **RFC-006** (§논점3 보상 (line 111-119), §논점4 (line 121-125)) — 보상=각 컨텍스트 자기 aggregate 상태 기준·멱등·append-only로 일원 처리.
      ES/비-ES 패러다임 구분 없이 균질 서술. 부분 보상 상태(SeatReleased 성공+PaymentRefunded 실패)를 PoisonMessage가 담을 수 있는지는 구현 사이클 TBD로 하위
      위임 — 우려의 '하위 문서로 넘긴다'가 실제 확인됨.
    - **ADR-08** (결정사항·적용표 (line 42-71)) — 코레오그래피 기본, 각 컨텍스트 자치 보상. ES/비-ES 경계가 보상 원자성·순서를 더 어렵게 한다는 언급 없음. 모든 예약 흐름을 2~
      3스텝 선형으로 동일 취급.
    - **RFC-003** (§논점(line 83), 결정요약(line 184)) — 자연 멱등 upsert의 inbox 생략은 '순서 역전이 없을 때뿐'; '역전이 가능하면 오래된 값으로 덮어쓰는 사고가
      난다'. 멱등이 순서 역전을 막지 못한다는 우려 핵심을 정면 인정.
    - **RFC-011** (§논점(line 129)) — dedup(event_id inbox)과 순서 가드(sequence_no 버전 가드)를 분리. 교차-애그리거트 카운터 등 증분 갱신은
      per-aggregate 가드로 멱등 불가 → event_id inbox 또는 절대값 재계산 필요.
    - **RFC-021** (§논점2 (line 105), 결론(line 111)) — 교차-애그리거트 적용 순서는 정확성 의존 아님(멱등 흡수). 예외: 여러 애그리거트 상대 시점 의존
      point-in-time 파생 사실은 인터리빙에 값 달라짐 → 생산 시점 페이로드 박제. '진짜 교차 불변식 강제는 사가 몫'. 인터리빙 위험의 처리선이 그어져 있음.
    - **ADR-02** (결정사항 (line 33-41)) — reservation·timetable·restaurant=ES, schedule·user=비-ES. ES/비-ES 차이는 '상태를 이벤트로
      쌓느냐 테이블로 들고 있느냐'일 뿐, 대외 발행 경로 동일로 균질화 — 혼합 사가 조율 난이도라는 관점은 없음.
- **처분안(제안)**: mitigate-note
    - 근거: 우려의 기술적 핵심(멱등 보상이 순서 역전을 못 막는다)은 RFC-003·011·021이 이미 정면으로 처리선을 그어놓아 새 결정 사항이 없다 — 따라서 accept-new-rfc는 과잉이다. 남은
      gap은 순수 프레이밍 결함: ES/비-ES 혼합 경계가 사가 보상을 더 어렵게 한다는 점 자체가 RFC-006에 표시되지 않고, 특히 부분 보상 상태(SeatReleased
      성공+PaymentRefunded 실패)의 PoisonMessage 수용 여부는 TBD로 하위 위임됨(§논점4 line 125). RFC-006에 "혼합 패러다임 사가는 두 진실원을 동시 조율하므로
      난이도가 다르다"는 한 줄 표지와, 부분 보상 TBD가 이 난이도의 발현임을 명시하는 note를 추가하면 충분하다.

### C48 — core에서 Spring 배제가 흔한 도메인 헬퍼(Bean Validation·로깅)까지 배제한다

`LOW` · 판정 **partially-decided** (conf high) · 처분안 **mitigate-note** ⚙️검토필요 · 우선 LOW

- **문서/항목**: D-002 §6.1
- **설명**: 순수성 보장은 강력하나 jakarta.validation·SLF4J 파사드·일부 코틀린 확장까지 core에서 못 쓰게 된다. 애그리거트 불변식 검증을 순수 코틀린으로만 짜거나 shared
  재구현·application 상향 우회가 생기는데, 후자는 리치 도메인 목표와 충돌한다.
- **판정 근거**: 뿌리 결정(core = 물리적 별도 모듈, JPA·Spring 컴파일 타임 배제로 순수성 강제)은 ADR-07(합의됨)·RFC-010(합의)에서 확정됐고, 매핑 보일러플레이트 등 분리의
  대가를 "정당한 대가"로 수용한다. 그러나 이 두 문서는 JPA/매핑 비용만 다루지 jakarta.validation·SLF4J·코틀린 확장 배제라는 C48의 구체 쟁점은 언급하지 않는다. 로깅 갈래는
  RFC-008 §5가 실질적으로 해소한다 — 로깅을 도메인 코드에서 하지 않고 도메인 경계 AOP로 스코프 키를 자동 주입하고 OTel Baggage 전파 후 MDC로 투영하기로 결정했으므로, core에
  SLF4J가 필요하다는 우려 자체가 설계상 성립하지 않는다. 반면 검증 갈래는 애그리거트 내부에서 순수 require(...)로 불변식을 검증한다는 패턴이 DESIGN-006 §4.2·DESIGN-003에만
  있고(디자인 문서, ADR/RFC 아님), Bean Validation 배제·shared 재구현/application 상향 우회의 딜레마를 결론지은 ADR/RFC는 없다. 오히려 D-002 §6.1(
  Weakness)에 여전히 열린 반박 포인트로 남아 있다. 따라서 로깅은 RFC로 결정, 검증은 설계 문서 수준에만 존재하고 ADR/RFC 미결 → partially-decided.
- **인용**:
    - **ADR-07** (07.command-domain-jpa-separation.md §결정·근거 (상태: 합의됨 2026-06-13)) — 순수 도메인 + 별도 JPA 엔티티 분리 유지, core
      순수성(도메인 외부 의존성 0) 계승 확정. 단 다루는 대가는 JPA 매핑 비용(Mutator/toEntity)뿐 — Bean Validation/로깅 헬퍼 배제 쟁점은 언급 없음.
    - **RFC-010** (module-structure-migration §논점1·결론 (상태: 합의 2026-06-23)) — 순수 도메인 거처 = 별도 core-module(b) 확정. '도메인은
      인프라를 모른다'를 빌드 차원 컴파일 의존성으로 강제. 배제되는 헬퍼(validation/logging)에 대한 검토는 없고 매핑 보일러플레이트만 정당한 대가로 수용.
    - **RFC-008** (observability §5 결정 (도메인 경계 AOP 스코프 키 자동 주입 + OTel Baggage 전파 후 MDC 투영)) — 로깅을 도메인 메서드에 손으로
      MDC.put/SLF4J 흩뿌리지 않고 도메인 경계 AOP 아스펙트로 자동 주입한다고 결정 — 로깅 헬퍼가 core에 필요하다는 C48 우려의 로깅 갈래를 설계상 해소.
    - **DESIGN-006** (§4.2 (애그리거트 책임) / 표: 불변식 검증 = 애그리거트, command.<ctx>.domain) — 불변식 검증은 애그리거트 안에서 순수 require(...)로
      수행하고 'V1처럼 서비스에서 하지 않는다'고 명시 — 검증 갈래의 의도된 패턴이나 이는 디자인 문서이지 ADR/RFC가 아니며 jakarta.validation 배제·우회 딜레마를 결론짓지 않음.
    - **D-002** (§6.1 / Weakness (Devil's Advocate)) — Spring 배제가 Bean Validation·SLF4J·일부 코틀린 확장까지 배제 → 불변식 검증을 순수
      코틀린으로만, shared 재구현 또는 application 상향 우회(리치 도메인과 충돌) 발생 가능. 여전히 열린 반박 포인트로 문서에 남아 ADR/RFC 미결임을 자증.
- **처분안(제안)**: mitigate-note
    - 근거: verdict 'partially-decided' + 심각도 LOW 기준 자동도출 — 검토 필요

### C05 — 분산 락의 안전성·토폴로지 가정(Redlock·단일 인스턴스 SPOF·상시 왕복 비용)이 검증되지 않았다

`HIGH` · 판정 **already-decided** (conf high) · 처분안 **reject** · 우선 LOW

- **문서/항목**: D-018 §4.3, D-006 §4.1·§5
- **설명**: Redis 락을 상호배제의 1차로 삼으면서 GC 정지·네트워크 지연·watchdog 실패에 대한 fencing token 안전장치를 언급하지 않고, 정확성 의존 컴포넌트를 페일오버 없는 단일
  인스턴스로 두어 split-brain 창을 연다. 동시에 정상 경로 락으로 인해 모든 ES 쓰기에 Redis 왕복 지연이 상시 추가되나 락프리 대안과 정량 비교가 없다. 안전성·가용성·성능 세 축이 모두 미검증
  가정 위에 있다.
- **판정 근거**: 우려의 세 축이 모두 ADR-16(비관 락)과 그 상위 RFC-014, 그리고 ADR-19(Redis 역할)에서 명시적으로 다뤄지고 결정됐다. 핵심 반박은 "잠그되 믿지 않는다":
  Redisson 락을 safety가 아닌 liveness(경합 완화) 수단으로만 쓰고, 정확성은 락과 독립한 `(aggregate_id, sequence_no)` UNIQUE(L0)가 최종 심판한다. (1)
  안전성 — GC 정지·네트워크 지연·watchdog 실패·락 키 evict로 락이 풀려도 UNIQUE 백스톱이 이중 append를 구조적으로 거절하므로 fencing token이 불필요하게 설계됐다(
  fencing을 "언급 안 함"이 아니라 아키텍처적으로 대체함). (2) 가용성 — 단일 인스턴스 SPOF/페일오버는 DB 비관 락 폴백(L1', `SELECT … FOR UPDATE`)으로 쓰기 가용성을
  유지하고, split-brain 창(일부 노드 Redisson·일부 DB 락 공존)의 정확성도 L0 UNIQUE가 흡수한다고 명시. (3) 성능 — 무경합 쓰기에도 락 왕복 비용이 상시 추가됨을 트레이드오프로
  자인하고, 락프리(낙관) 대안을 명시적으로 검토·기각(핫 스트림 retry storm/라이브락)했다. 다만 정량 비교는 없이 "트래픽 없는 학습 환경에서 명목상 비용" 판단이며 "실트래픽 시 재검토 트리거"로
  위임 — 성능 축만 정성적이므로 partially에 가깝지만, 결정 자체(비관 채택·근거·재검토 조건)는 내려져 있어 미검증 가정으로 열려 있지 않다.
- **인용**:
    - **ADR-16** (결정사항 §L0/L1/L1' 3층 + '락은 정확성을 보장하나' 절) — 분산 락은 liveness지 safety가 아니다; Redis 단일 인스턴스·손실 허용·allkeys-lru라
      락 키 evict·페일오버·리스 만료로 풀릴 수 있음 → (aggregate_id, sequence_no) UNIQUE를 정확성 불변식(L0)으로 유지. 락 유실 edge의 이중 append를 최종
      거절하는 백스톱 — fencing token을 아키텍처적으로 대체.
    - **ADR-16** (결정사항 부속규칙 'dual-provider split-brain은 L0가 흡수' + A-2 DB 폴백) — Redis 불가 시 command DB per-aggregate
      SELECT … FOR UPDATE로 폴백(비관 의미론 유지, 새 하드 의존 0); 폴백 전환 창의 split-brain(일부 Redisson·일부 DB 락)도 둘째 append가 UNIQUE로 거절돼
      정확성 유지 — 가용성 SPOF·split-brain 명시 해소.
    - **ADR-16** (트레이드오프 §선택한 방식의 한계 + 옵션 C-1) — 무경합 쓰기에도 락 획득 비용(평소 0이던 낙관 대비) — 학습 환경에선 수용, 실트래픽 시 재검토 트리거;
      watchdog·리스 TTL·lock-wait 타임아웃 운영 표면 자인. 상시 왕복 비용을 인지하되 정량 비교 없이 학습가치로 판단.
    - **RFC-014** (논점 1 '3층 동시성 제어 구조' + 결정 요약 #1) — 낙관(락프리) 대안을 명시 검토 후 기각(핫 스트림 retry storm/라이브락). Redisson=liveness
      도구지 safety 아님, 단일 인스턴스·손실 허용에서 키 evict·리스 만료로 풀릴 수 있어 L0 UNIQUE 필수, split-brain 정확성도 L0가 흡수 — 락프리 vs 락 비교는 정성적.
    - **ADR-19** (결정사항 'Redis = 분산 조정·휘발성 상태 전용' 절) — Redisson 락은 liveness(경합 완화)지 safety가 아니며, 단일 인스턴스·allkeys-lru(락 키
      evict 가능)·페일오버가 정확성을 위협하지 않는 이유는 (aggregate_id, sequence_no) UNIQUE 백스톱 때문. Redis 불가 시 DB 비관 락 폴백 — 토폴로지 SPOF 가정을
      정확성과 분리해 정당화.
- **처분안(제안)**: reject
    - 근거: 우려가 지목한 세 축(안전성·가용성·성능)이 모두 ADR-16·RFC-014·ADR-19에서 이미 명시적으로 다뤄지고 결정됐으므로 "미검증 가정 위에 열려 있다"는 전제가 사실과 어긋난다.
      fencing token은 누락이 아니라 락과 독립한 (aggregate_id, sequence_no) UNIQUE(L0) 백스톱으로 아키텍처적으로 대체됐고(GC 정지·리스 만료·키
      evict·split-brain 모두 UNIQUE가 최종 거절), SPOF는 DB FOR UPDATE 폴백(L1')이, 락프리 대안은 핫 스트림 retry storm/라이브락 근거로 명시 기각됐다.
      유일하게 정성적인 성능 축(락 왕복 비용의 정량 비교 부재)도 무트래픽 학습 환경에서의 의도적 유예이자 "실트래픽 시 재검토 트리거"로 위임된 결정이라 새 RFC로 열 미결 사항이 아니다.

### C07 — UUIDv7 커서가 '순서 정확성 아님'인데 전역 재구축이 이 부정확성에 노출된다

`LOW` · 판정 **already-decided** (conf high) · 처분안 **mitigate-note** · 우선 LOW

- **문서/항목**: D-003 §4.1
- **설명**: UUIDv7은 밀리초 해상도라 동일 밀리초 다중 삽입의 시간순을 보장하지 않는다. aggregate 단위 재구축은 sequence_no 정렬로 안전하지만 keyset=PK 스캔인 전역
  projection 재빌드는 이 부정확성에 걸린다. keyset 커서와 순서 정확성을 한 컬럼에 겸하게 한 경계가 흐리다.
- **판정 근거**: C07의 우려 — 전역 projection 재구축이 UUIDv7 keyset(`WHERE event_id > :last ORDER BY event_id`) PK 스캔에 의존하는데 UUIDv7이
  밀리초 해상도라 동일 ms 다중 삽입의 시간순을 보장하지 못한다는 점 — 은 이미 명시적으로 해소되어 있다. 핵심 결정: keyset은 '진행/열거·재개 커서'일 뿐 '교차-애그리거트 전순서 보장'이 아니며,
  projector 정확성은 스캔 순서가 아니라 per-aggregate 순서(파티션 키=aggregate_id) + 멱등 upsert + per-aggregate 버전 가드(sequence_no)가 진다는
  불변식으로 분리했다. 즉 keyset 커서와 순서 정확성을 한 컬럼이 '겸한다'는 우려의 전제 자체를 설계가 명시적으로 깬다 — event_id는 진행만 나르고 정확성은 sequence_no가 진다. 동일 ms
  타이로 스캔 순서가 뒤바뀌어도 결과 정확성에 영향이 없다. 실제로 이 우려는 D-003 §4.1 자기 리뷰(라인 197-199)에서 이미 제기됐고, 리뷰어가 귀속처를
  DESIGN-009·DESIGN-004·ADR-22로 지정했으며, 그 문서들이 실제로 그 불변식을 못박아 닫았다. 남은 gap 없음.
- **인용**:
    - **ADR-22** (§정확성 불변식 (line 58) 및 정정 노트(line 10, 23)) — 'global_seq는 진행/열거를 나르지 순서 정확성을 나르지 않는다. 프로젝터 정확성 =
      per-aggregate 순서(파티션 키) + 멱등 upsert + per-aggregate 버전 가드(sequence_no). 교차-애그리거트 적용 순서는 정확성 의존이 아니다(멱등이 흡수).' 정정
      노트에서 이 역할을 UUIDv7 event_id가 그대로 승계: 'UUIDv7이라 재구축 keyset = PK 스캔... 이는 진행 커서이지 교차 순서 보장이 아니다.'
    - **DESIGN-009** (§6.3 리플레이 성능 가드레일 line 218 (및 line 217)) — '스캔은 event_id(UUIDv7) keyset(WHERE event_id > :last
      ORDER BY event_id)로 열거·재개한다(ADR-22) — 이는 진행 커서이지 교차-애그리거트 순서 보장이 아니며, 프로젝터 정확성은 per-aggregate 순서+멱등+버전 가드가 진다(
      RFC-011).' 라인 217은 프로젝션 재구축(DESIGN-004)도 전체 스캔이므로 동일 가드레일 적용을 명시 — 전역 재구축 경로를 정확히 커버.
    - **RFC-011** (논점1 결론(line 94) · 자산(line 51) · 논점4 멱등(line 129,131)) — '전역 스캔의 열거·재개 기준은 event_id(UUIDv7) keyset이다 —
      이는 진행/열거 커서이지 교차-애그리거트 전순서 보장이 아니다. 정확성 불변식은 RFC-021이 박은 그대로 per-애그리거트 순서 + 멱등 upsert + per-애그리거트 버전 가드(
      sequence_no).' dedup(event_id inbox)과 순서 가드(sequence_no)를 명시적으로 분리; 2경로 겹침에서 순서 역전이 정상 발생해도 버전 가드가 흡수하도록 설계.
    - **RFC-021** (닫힘 결정 (ADR-22·D-003 §4.1 line 68,132가 참조)) — event_id를 UUIDv7로 확정하고 전용 global_seq를 불채택 — UUIDv7이
      keyset 커서를 겸하되 '순서 정확성 아님'을 명시. 순서 정확성은 sequence_no 버전 가드가 전담하도록 책임 분리.
    - **DESIGN-003** (§4.1 self-review line 197-199) — C07과 동일한 문구의 우려가 이 문서 자기 리뷰에 기록됨. 리뷰 응답: '동의. 귀속처 = DESIGN-009(
      재구축)·DESIGN-004(projection 재빌드)·ADR-22. 전역 재구축 keyset은 순서 아닌 열거이며 대부분 프로젝션은 aggregate 단위 순서만 필요해 실제 영향 범위는 좁음;
      003은 정체성만 다루고 순서 의미론은 링크 위임.' — 위 문서들이 그 위임을 실제로 이행.
- **처분안(제안)**: mitigate-note
    - 근거: 우려의 전제(keyset 커서와 순서 정확성을 한 컬럼이 겸함)는 이미 ADR-22·DESIGN-009·RFC-011이 명시적으로 깨서 닫았다 — event_id는 진행/열거만, 정확성은
      per-aggregate 순서+멱등 upsert+sequence_no 버전 가드가 전담하므로 동일 ms 스캔 순서 역전은 결과에 무해하다. 다만 DESIGN-003 §4.1 자기리뷰의 정정 문구는 실제
      채택되지 않은 해법(`(occurred_at, event_id)` 정렬)을 가리켜 하류 문서와 어긋나므로, 그 한 줄을 "정확성은 스캔 순서가 아닌 sequence_no 가드가 진다"로 정정하는 노트만
      남기면 충분하다. 새 설계 결정이 없으므로 신규 RFC는 불필요하다.
