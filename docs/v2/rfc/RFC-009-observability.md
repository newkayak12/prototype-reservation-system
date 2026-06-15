# RFC-009 — 관측성

- **상태**: Open · 논의 중 · 2026-06-15
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[10-observability]] 보강

## 맥락

관측성의 큰 방향은 이미 한 차례 정리됐다. 벤더에 묶이지 않기 위해 계측은 OTel로 통일하고, 백엔드는 OSS 스택(Prometheus/Grafana/Tempo/Loki)을 기본값으로 둔다. 추적을 끊김 없이 잇기 위해 correlation/causation id 전파 규약을 세우고, 이벤트 봉투에 trace 헤더를 실을 슬롯을 둔다 — 여기까지가 [[10-observability]]에서 잡아 둔 *규약의 방향*이다.

방향은 섰지만, 코드에 실제로 박히는 부분은 아직 비어 있다. 코루틴 경계를 넘을 때 추적 컨텍스트를 어떻게 살릴지, 메트릭 카탈로그를 어떤 이름·라벨로 못박을지, 스케줄러가 이벤트를 재처리할 때 추적 id를 어떻게 보존할지가 남았다. 이 RFC는 그 셋을 추론으로 좁히는 자리다.

한 가지는 미리 선을 긋는다. 백엔드 *배포* 자체 — Tempo·Prometheus·Grafana·Loki를 어디에 어떻게 띄울지 — 는 여기서 결정하지 않는다. 벤더 중립 규약을 따르는 한 백엔드는 교체 가능하고, 배포는 운영 시점의 todo다(말미 [[index|docs/todo]]). 여기서 닫는 것은 **코드에 박히는 규약**이다.

## 논의

### 추적 컨텍스트를 코루틴 경계에서 어떻게 살리나

가장 먼저 부딪히는 건 Kotlin 코루틴이다. 흔히 쓰는 MDC는 스레드 로컬에 얹혀 있어서, 코루틴이 디스패처를 바꿔 다른 스레드로 넘어가는 순간 추적 컨텍스트가 조용히 사라진다. 로그에 correlationId가 찍히다 말다 하는 전형적인 증상이 여기서 나온다. 선택지는 대략 셋이다 — MDC를 그대로 쓰기, 코루틴 `CoroutineContext`로 직접 전파하기, OTel Context를 코루틴에 브리지하는 것.

나는 OTel Context를 일급 전파 매체로 두고 코루틴에 브리지하는 쪽을 택한다. 이미 계측을 OTel로 통일하기로 한 이상 추적 컨텍스트의 진짜 출처는 OTel Context이고, MDC는 그것을 로그 포맷에 비추기 위한 *투영*으로만 남기는 게 맞다. 즉 OTel Context를 코루틴 컨텍스트 엘리먼트로 실어 디스패치 경계를 넘기게 하고, 로깅 시점에 그 값을 MDC로 복사해 찍는다. MDC 단독은 코루틴에서 깨지고, 코루틴 컨텍스트 단독은 OTel 계측과 두 개의 진실 출처를 만든다 — 브리지가 둘을 한 출처로 묶는다. 다만 어느 라이브러리(예: `kotlinx-coroutines-slf4j`, OTel의 코루틴 확장)를 쓸지, 브리지를 어느 경계마다 거는지는 구현 디테일이라 Design에서 검증한다.

### correlationId를 span에 박아 교차 조회를 규약으로 올릴까

지금 [[10-observability]]는 correlationId를 span attribute로 다는 걸 "권고" 수준으로만 적어 둔다. 권고로 두면 어떤 흐름엔 붙고 어떤 흐름엔 안 붙어서, 정작 장애 추적할 때 추적↔로그 교차 조회가 반쪽이 된다.

나는 이걸 *규약*으로 격상한다. correlationId를 모든 root span의 attribute로 박는 것을 필수로 두면, Tempo에서 추적을 찾아 correlationId를 뽑고 그 값으로 Loki 로그를 한 번에 끌어오는 경로가 보장된다. "권고"는 관측성에서 사실상 "없음"과 같다 — 빠진 한 흐름이 하필 장애난 흐름이기 때문이다. attribute 키 이름을 어떤 표준(예: OTel semantic convention의 커스텀 네임스페이스)에 맞출지는 (이의 여지)이며 Design에서 못박는다.

### 재처리할 때 추적 id를 어떻게 보존하나

스케줄러가 Outbox를 재발행하거나 v1의 PoisonMessage 경로로 이벤트를 다시 태울 때, 추적을 어떻게 잇느냐가 까다롭다. 새 추적을 발급하면 원 흐름과의 인과가 끊기고, 원 추적을 그대로 쓰면 "이건 재처리된 거다"라는 사실이 사라진다. 둘 다 곤란하다.

그래서 나는 둘을 함께 쓴다 — 원 correlationId는 그대로 유지해 원 흐름과 한 묶음으로 조회되게 하고, 재처리라는 사실은 causationId 체인(또는 재처리 표식 attribute)으로 따로 드러낸다. correlationId는 "같은 비즈니스 흐름"을 묶는 끈이므로 재처리에도 끊지 않는 게 맞고, causationId는 "무엇이 이 이벤트를 낳았나"를 가리키므로 재처리를 일으킨 직전 원인을 가리키게 하면 자연스럽게 "재처리됨"이 식별된다. 이 정책은 v1 PoisonMessage 스케줄러 재처리 경로([[07.reservation]])와 정합해야 하고, 재처리 표식을 attribute로 둘지 별도 메타 필드로 둘지는 Design에서 검증한다.

### AbstractEvent의 추적 메타 필드를 확정한다

위 세 결정은 결국 이벤트 봉투에 무엇이 실리느냐로 수렴한다. 지금 [[10-observability]]의 추적 메타는 개념 예시에 머물러 있는데, 공통 발행 경로가 의존하려면 실제 필드명·타입·필수 여부가 박혀 있어야 한다.

나는 AbstractEvent에 correlationId·causationId·traceparent를 추적 메타로 둔다. correlationId는 흐름을 묶고(필수), causationId는 직전 원인을 가리키며(루트 이벤트에선 비거나 자기 자신), traceparent는 W3C Trace Context 포맷으로 OTel 추적을 봉투에 직렬화해 메시지 경계를 넘게 한다. 셋 다 [[02-write-model]]의 공통 발행 경로에서 채워지도록 두면, 발행자가 일일이 신경 쓰지 않아도 추적이 자동으로 흐른다. 구체 타입(문자열 vs 값 객체)과 traceparent를 봉투 헤더에 둘지 페이로드에 둘지는 (이의 여지)이며 Design에서 [[02-write-model]] 스키마와 맞춰 확정한다.

### 메트릭 카탈로그를 지금 정의한다

마지막은 무엇을 재느냐다. 프로젝션 지연, Outbox 적체, PoisonMessage 건수, consumer lag — v2 CQRS/ES 구조에서 막히면 가장 먼저 신호가 떠야 할 지점들이다. 절대 임계 숫자는 측정해 보기 전엔 의미가 없으니 [[RFC-008-deployment-infra-ops]]의 측정 트리거로 미루지만, *무엇을 어떤 이름·라벨·단위로 재느냐*는 지금 정의해 둬야 한다. 카탈로그가 없으면 각자 멋대로 메트릭을 찍어 대시보드가 파편화된다.

그래서 핵심 메트릭의 이름·라벨·단위 카탈로그를 이 RFC에서 정의한다. 이때 [[RFC-008-deployment-infra-ops]]의 SLI와 겹치지 않게 연계하는 게 중요하다 — SLI는 "사용자가 느끼는 수준"을, 여기 카탈로그는 "내부 파이프라인 건강"을 재므로 층이 다르지만, 같은 현상을 두 이름으로 재면 혼선이 난다. 카탈로그의 구체 메트릭 목록과 라벨 카디널리티는 Design에서 다듬는다.

## Design으로 넘기는 것

- OTel Context↔코루틴 브리지의 구체 라이브러리 선택과 적용 경계
- correlationId span attribute의 키 이름·네임스페이스 표준
- 재처리 표식을 attribute로 둘지 메타 필드로 둘지, PoisonMessage 경로와의 정합
- AbstractEvent 추적 메타의 구체 타입과 traceparent 직렬화 위치([[02-write-model]] 스키마 연계)
- 메트릭 카탈로그의 구체 목록·라벨 카디널리티, [[RFC-008-deployment-infra-ops]] SLI와의 경계

## 위임

- 추적/메트릭/로그 백엔드(Tempo·Prometheus·Grafana·Loki) **배포**는 [[index|docs/todo]] 백로그로 둔다. 벤더 중립 규약을 따르므로 백엔드는 교체 가능하다(예: 매니지드 서비스).
- 절대 임계 숫자는 [[RFC-008-deployment-infra-ops]]의 측정 트리거로 넘긴다.

## 관련 문서

- [[RFC-002-decision-queue]] · [[10-observability]] · [[07.reservation]] · [[02-write-model]] · [[RFC-008-deployment-infra-ops]]
