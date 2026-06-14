# RFC-009 — 관측성

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[10-observability]] 보강

## 배경

OTel(벤더 중립) + OSS 스택(Prometheus/Grafana/Tempo/Loki), correlation/causation id 전파 규약, 이벤트 봉투에 trace 헤더 슬롯 — *규약 방향*은 라운드1에서 잠갔다. 남은 건 코루틴 경계의 전파 구현, 메트릭 카탈로그 구체화, 재처리 시 추적 id 보존이다. 백엔드 *배포* 자체는 todo로 위임하고, 여기선 **코드에 박히는 규약**을 닫는다.

## 논의 항목

### Q1. 계측 라이브러리·MDC vs 코루틴 컨텍스트 전파
- **출처**: [[10-observability]]
- **옵션**: (a) MDC 기반 / (b) 코루틴 `CoroutineContext` 전파 / (c) OTel Context + 코루틴 브리지
- **쟁점**: Kotlin 코루틴 경계에서 추적 컨텍스트가 끊기지 않게 하는 표준 패턴 확정. 스레드 로컬(MDC)은 코루틴 디스패치에서 유실 위험.

### Q2. correlationId span attribute 교차 조회 규약 확정
- **출처**: [[10-observability]]
- **쟁점**: 현재 "권고" 수준. correlationId를 span attribute로 박아 추적↔로그 교차 조회를 *규약*으로 확정할지.

### Q3. 스케줄러 재처리·Outbox 재발행 시 추적 id 보존 정책
- **출처**: [[10-observability]] · [[07.reservation]]
- **옵션**: (a) 원 correlationId 유지 / (b) 재처리 표식(causationId 체인) 추가 / (c) 둘 다
- **쟁점**: v1 PoisonMessage 스케줄러 재처리 경로와 정합. 재처리 이벤트의 추적이 원 흐름과 이어지면서도 "재처리됨"이 식별돼야 함.

### Q4. AbstractEvent 추적 메타데이터 필드/이름 확정
- **출처**: [[10-observability]]
- **쟁점**: 현재 개념 예시. correlationId·causationId·traceparent 등 실제 필드명·타입·필수 여부를 [[02-write-model]] 공통 발행 경로와 맞춰 확정.

### Q5. 메트릭 카탈로그 구체 정의
- **출처**: [[10-observability]]
- **쟁점**: 프로젝션 지연·Outbox 적체·PoisonMessage 건수·consumer lag 등 핵심 메트릭의 이름·라벨·단위 카탈로그. 절대 임계는 측정으로, 카탈로그 *정의*는 지금. [[RFC-008-deployment-infra-ops]] SLI와 연계해 중복 방지.

## 닫는 방식

- Q1·Q2·Q3·Q4·Q5 = **논의로 지금 결정**(규약·필드·카탈로그 정의).
- 절대 임계 숫자는 [[RFC-008-deployment-infra-ops]] Q9·Q10에서 측정 트리거로.
- 🌱 없음.

## 위임

- 추적/메트릭/로그 백엔드(Tempo·Prometheus·Grafana·Loki) **배포** = [[index|docs/todo]] 백로그. 벤더 중립 규약이라 백엔드는 교체 가능(DD 등).

## 산출물

- [[10-observability]] §이벤트 메타데이터 스키마·전파 규약·메트릭 카탈로그 확정, 미결정 섹션 해소.
- 필요 시 신규 ADR(예: "추적 컨텍스트 전파·재처리 보존 규약").

## 관련 문서
- [[RFC-002-decision-queue]] · [[10-observability]] · [[07.reservation]] · [[RFC-008-deployment-infra-ops]]
