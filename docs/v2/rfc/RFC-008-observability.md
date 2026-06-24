# RFC-008 — 관측성

- **상태**: 🏷 합의 (2026-06-21) · design [[10-observability]] 반영 · 코루틴 기각(블로킹 MVC 유지)
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-INDEX]]
- **닫으면**: [[10-observability]] 보강

## 맥락

먼저 기본부터 분명히 하자. 분산 추적의 *기본 계층* — 각 인스턴스/JVM을 resource attribute로 식별하고, HTTP·JDBC 같은 동기 경계에서 span을 자동 생성하며, trace_id/span_id를 전파해 로그에 찍고, OTel로 내보내는 것 — 은 Spring Boot 3의 기본이다(Micrometer Tracing + OTel 브리지). 이건 새로 설계할 게 아니라 스타터를 켜고 그대로 채택한다. 벤더에 묶이지 않으려 계측을 OTel로 통일하고 백엔드를 OSS 스택(Prometheus/Grafana/Tempo/Loki) 기본값으로 둔다는 방향도 [[10-observability]]에서 이미 섰다. 이 RFC는 그 기본을 다시 정하지 않는다.

이 RFC가 다루는 건 *그 기본이 닿지 않는 곳*이다. 우리 스택이 **블로킹 Spring MVC + CQRS/ES + Kafka**라서, Spring 기본만으로는 추적이 끊기거나 도메인이 요구하는 식별이 기본 trace_id로는 표현되지 않는 지점들이 있다. 한 요청이 한 스레드에 머무는 동안엔 MDC가 그대로 살아 있으므로, 추적이 끊기는 곳은 *비동기·메시지 경계*뿐이다 — Kafka 소비(프로젝터), Outbox relay·스케줄러, `@Async`. 넷이다 — **(1)** "같은 비즈니스 흐름"을 묶고 ES 인과를 가리키는 correlationId/causationId — 요청 하나짜리인 OTel trace_id와는 다른 층의 식별자다, **(2)** Kafka를 건너고 이벤트를 재생할 때 추적을 잇도록 봉투에 traceparent를 싣는 일, **(3)** 프로젝션 지연·Outbox 적체처럼 무엇을 어떤 이름·라벨로 잴지의 도메인 메트릭 카탈로그, **(4)** 로그를 질의 가능하게 만드는 구조화 로깅 — MDC에 trace_id·correlationId·도메인 스코프 키를 실어 인덱싱하되, 그 스코프 주입을 AOP 아스펙트로 자동화하고 비동기·메시지 경계를 넘게 하는 것. 요컨대 "OTel로 span 던지기"는 전제(= Spring 기본)이고, 여기서 닫는 건 그 위에서 우리 아키텍처가 강제하는 규약이다.

> **코루틴은 기각.** 영속화가 블로킹 JPA/QueryDSL이라 코루틴을 얹어도 결국 스레드풀로 디스패치돼 throughput 이득은 없고, 디스패처 전환으로 MDC가 깨지는 전파 비용만 진다 — 성능상 마이너스다. 그래서 V2도 블로킹 MVC를 유지하고, IO 확장이 필요해지면 코루틴/WebFlux가 아니라 **virtual thread**(JDK21·Boot 3.4)를 레버로 둔다([[RFC-007-deployment-infra-ops]]) — 명령형 코드 그대로, MDC도 안 깨진다. 그 결과 아래에서 "코루틴 컨텍스트 전파" 결정은 다루지 않는다(끊김은 비동기·메시지 경계에만 남는다).

한 가지는 미리 선을 긋는다. 백엔드 *배포* 자체 — Tempo·Prometheus·Grafana·Loki를 어디에 어떻게 띄울지 — 는 여기서 결정하지 않는다. 벤더 중립 규약을 따르는 한 백엔드는 교체 가능하고, 배포는 운영 시점의 todo다(말미 [[index|docs/todo]]). 여기서 닫는 것은 **코드에 박히는 규약**이다.

## 논의

### 추적 컨텍스트를 비동기·메시지 경계에서 어떻게 잇나

블로킹 MVC라 *요청 안*에서는 MDC(ThreadLocal)가 그대로 살아 추가 장치가 필요 없다. 깨지는 건 스레드가 바뀌는 경계뿐 — `@Async`·스케줄러, 그리고 Kafka 소비다. 원칙은 하나로 둔다: **전파의 진짜 출처는 OTel Context이고 MDC는 그것을 로그 포맷에 비추는 *투영*이다.** 그래야 추적 출처가 둘로 갈리지 않는다.

- **인-프로세스 비동기(`@Async`·스케줄러)**: Micrometer의 context-propagation(`ContextSnapshot`/`taskDecorator`)으로 OTel Context를 넘긴 스레드에 복원하고, 로깅 시점에 MDC로 복사해 찍는다 — 손으로 `MDC.put`을 나르지 않는다.
- **Kafka 경계**: 인-프로세스 복원으론 안 되고 봉투에 추적을 직렬화해야 한다 — 아래 traceparent 절로 잇는다.

구체 수단(어느 `taskDecorator`·어느 경계에 거는지)은 구현 디테일이라 Design에서 확정한다.

### correlationId를 span에 박아 교차 조회를 규약으로 올릴까

먼저 왜 correlationId가 따로 필요한지부터. OTel이 자동으로 붙이는 trace_id는 *추적 하나*(대개 요청 하나)에 매이고, 재처리하면 새 trace_id가 발급된다. 하지만 "같은 예약 건"이라는 비즈니스 흐름은 여러 추적·여러 재처리에 걸쳐 한 묶음으로 조회돼야 한다 — 그래서 correlationId는 trace_id 위에 얹는 별도의 비즈니스 흐름 식별자다(trace_id가 자동이라고 따라오지 않는다). 그런데 지금 [[10-observability]]는 그 correlationId를 span attribute로 다는 걸 "권고" 수준으로만 적어 둔다. 권고로 두면 어떤 흐름엔 붙고 어떤 흐름엔 안 붙어서, 정작 장애 추적할 때 추적↔로그 교차 조회가 반쪽이 된다.

나는 이걸 *규약*으로 격상한다. correlationId를 모든 root span의 attribute로 박는 것을 필수로 두면, Tempo에서 추적을 찾아 correlationId를 뽑고 그 값으로 Loki 로그를 한 번에 끌어오는 경로가 보장된다. "권고"는 관측성에서 사실상 "없음"과 같다 — 빠진 한 흐름이 하필 장애난 흐름이기 때문이다. attribute 키 이름을 어떤 표준(예: OTel semantic convention의 커스텀 네임스페이스)에 맞출지는 (이의 여지)이며 Design에서 못박는다.

### 재처리할 때 추적 id를 어떻게 보존하나

스케줄러가 Outbox를 재발행하거나 v1의 PoisonMessage 경로로 이벤트를 다시 태울 때, 추적을 어떻게 잇느냐가 까다롭다. 새 추적을 발급하면 원 흐름과의 인과가 끊기고, 원 추적을 그대로 쓰면 "이건 재처리된 거다"라는 사실이 사라진다. 둘 다 곤란하다.

그래서 나는 둘을 함께 쓴다 — 원 correlationId는 그대로 유지해 원 흐름과 한 묶음으로 조회되게 하고, 재처리라는 사실은 causationId 체인(또는 재처리 표식 attribute)으로 따로 드러낸다. correlationId는 "같은 비즈니스 흐름"을 묶는 끈이므로 재처리에도 끊지 않는 게 맞고, causationId는 "무엇이 이 이벤트를 낳았나"를 가리키므로 재처리를 일으킨 직전 원인을 가리키게 하면 자연스럽게 "재처리됨"이 식별된다. 이 정책은 v1 PoisonMessage 스케줄러 재처리 경로([[07.reservation]])와 정합해야 하고, 재처리 표식을 attribute로 둘지 별도 메타 필드로 둘지는 Design에서 검증한다.

### AbstractEvent의 추적 메타 필드를 확정한다

위 세 결정은 결국 이벤트 봉투에 무엇이 실리느냐로 수렴한다. 지금 [[10-observability]]의 추적 메타는 개념 예시에 머물러 있는데, 공통 발행 경로가 의존하려면 실제 필드명·타입·필수 여부가 박혀 있어야 한다.

나는 AbstractEvent에 correlationId·causationId·traceparent를 추적 메타로 둔다. correlationId는 흐름을 묶고(필수), causationId는 직전 원인을 가리키며(루트 이벤트에선 비거나 자기 자신), traceparent는 W3C Trace Context 포맷으로 OTel 추적을 봉투에 직렬화해 메시지 경계를 넘게 한다. 셋 다 [[02-write-model]]의 공통 발행 경로에서 채워지도록 두면, 발행자가 일일이 신경 쓰지 않아도 추적이 자동으로 흐른다. 구체 타입(문자열 vs 값 객체)과 traceparent를 봉투 헤더에 둘지 페이로드에 둘지는 (이의 여지)이며 Design에서 [[02-write-model]] 스키마와 맞춰 확정한다.

### 로그를 어떻게 인덱싱 가능하게 만드나 — MDC 구조화 키와 도메인 스코프 아스펙트

추적·메트릭과 별개로, 로그 자체가 *질의 가능*해야 한다. 자유 텍스트 로그는 Loki에서 grep은 돼도 "이 예약 건의 모든 로그"처럼 구조화된 조회가 안 된다. 그러려면 로그 한 줄마다 구조화된 키 — trace_id, correlationId, 그리고 도메인 스코프(어느 애그리거트·어느 운영인지) — 가 실려 인덱싱돼야 한다. 표준 수단은 MDC다: MDC에 넣은 키가 로그 포맷에 찍히고 Loki 라벨/필드로 인덱싱된다.

문제는 둘이다. 첫째, 그 키를 *누가 넣느냐*. 도메인 메서드마다 손으로 `MDC.put`을 흩뿌리면 빠뜨리고 일관성이 깨진다. 그래서 나는 **도메인 경계에 AOP 아스펙트를 걸어 스코프 키를 자동 주입**하는 쪽을 택한다 — 도메인 서비스/커맨드 핸들러 진입 시 아스펙트가 운영 이름·애그리거트 id 같은 스코프를 컨텍스트에 넣고, 그 스코프 안의 모든 로그가 자동으로 그 키를 달고 나간다. 가시성이 코드 산발이 아니라 경계 한 곳에서 일관되게 보장된다.

둘째, 그 키가 *비동기·메시지 경계를 넘어 살아남느냐*. 블로킹 MVC라 요청 안에선 MDC가 유지되지만, `@Async`·스케줄러·Kafka 경계를 넘으면 raw `MDC.put`(ThreadLocal)으로 박은 스코프 키는 증발한다. 그래서 스코프 키도 MDC에 *직접* 박는 게 아니라 OTel 컨텍스트(특히 키-값을 함께 전파하는 Baggage)에 실어 전파하고 로깅 시점에 MDC로 투영해 찍는다 — 위 §전파 절의 "출처=OTel 컨텍스트, MDC=투영" 원칙을 trace_id뿐 아니라 도메인 스코프 키에도 똑같이 적용한다. (이의 여지: 어떤 스코프 키를 표준으로 넣을지·아스펙트를 어느 경계마다 걸지·Baggage 전파 범위는 Design에서 구체화.)

### 메트릭 카탈로그를 지금 정의한다

마지막은 무엇을 재느냐다. 프로젝션 지연, Outbox 적체, PoisonMessage 건수, consumer lag — v2 CQRS/ES 구조에서 막히면 가장 먼저 신호가 떠야 할 지점들이다. 절대 임계 숫자는 측정해 보기 전엔 의미가 없으니 [[RFC-007-deployment-infra-ops]]의 측정 트리거로 미루지만, *무엇을 어떤 이름·라벨·단위로 재느냐*는 지금 정의해 둬야 한다. 카탈로그가 없으면 각자 멋대로 메트릭을 찍어 대시보드가 파편화된다.

그래서 핵심 메트릭의 이름·라벨·단위 카탈로그를 이 RFC에서 정의한다. 이때 [[RFC-007-deployment-infra-ops]]의 SLI와 겹치지 않게 연계하는 게 중요하다 — SLI는 "사용자가 느끼는 수준"을, 여기 카탈로그는 "내부 파이프라인 건강"을 재므로 층이 다르지만, 같은 현상을 두 이름으로 재면 혼선이 난다. 카탈로그의 구체 메트릭 목록과 라벨 카디널리티는 Design에서 다듬는다.

## Design으로 넘기는 것

- `@Async`·스케줄러 경계의 OTel Context 전파 수단(Micrometer context-propagation·`taskDecorator`)과 적용 경계
- correlationId span attribute의 키 이름·네임스페이스 표준
- 재처리 표식을 attribute로 둘지 메타 필드로 둘지, PoisonMessage 경로와의 정합
- AbstractEvent 추적 메타의 구체 타입과 traceparent 직렬화 위치([[02-write-model]] 스키마 연계)
- 구조화 로깅: MDC에 넣을 표준 스코프 키 집합, 도메인 스코프 주입 아스펙트의 적용 경계, OTel Baggage 전파 범위(비동기·메시지 경계별)
- 메트릭 카탈로그의 구체 목록·라벨 카디널리티, [[RFC-007-deployment-infra-ops]] SLI와의 경계

## 위임

- 추적/메트릭/로그 백엔드(Tempo·Prometheus·Grafana·Loki) **배포**는 [[index|docs/todo]] 백로그로 둔다. 벤더 중립 규약을 따르므로 백엔드는 교체 가능하다(예: 매니지드 서비스). 이 배포 결정엔 **메트릭 수집 토폴로지**도 포함된다 — 중앙 Prometheus가 서비스 디스커버리로 파드를 scrape할지, OTel Collector(= Datadog Agent에 해당하는 수집 에이전트)/Prometheus agent를 사이드카·DaemonSet으로 두어 로컬에서 모아 중앙으로 remote-write/forward할지. (직관: 우리가 떠올리는 "수집 에이전트 사이드카"는 Datadog Agent 같은 것이고, 그 OSS/OTel 대응이 OTel Collector다. 반면 Prometheus *서버* 자체를 파드별 사이드카로 두는 건 TSDB가 파편화돼 안티패턴 — 사이드카로 띄우는 건 저장소가 아니라 수집기/agent다. OTel로 통일한 이상 `앱 → OTLP push → Collector(에이전트) → 중앙 백엔드` 경로가 중앙 pull scrape보다 정합적일 수 있다 — 배포 사이클에서 검증.)
- 절대 임계 숫자는 [[RFC-007-deployment-infra-ops]]의 측정 트리거로 넘긴다.

## 관련 문서

- [[RFC-INDEX]] · [[10-observability]] · [[07.reservation]] · [[02-write-model]] · [[RFC-007-deployment-infra-ops]]
