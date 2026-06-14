# RFC-004 — 메시징·전달 보장

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[07-messaging-topology]] 보강 + [[09.event-ordering-and-delivery-guarantee]] 비준 (Proposed→Accepted)

## 배경

전달은 at-least-once + 멱등 컨슈머로 effectively-once를 만든다. `aggregate_id`를 파티션 키로 써서 aggregate 내 순서를 보장하고, command DB→Kafka는 Outbox relay가 잇는다. 라운드1이 *메커니즘*은 잠갔지만 파티션 수·lag 임계·inbox 보존·relay 단일성 같은 **구체 운영값과 전환 기준**은 미결로 남았다 — 여기서 닫는다.

## 논의 항목

### Q1. 파티션 수 초기값·증설 절차
- **출처**: [[09.event-ordering-and-delivery-guarantee]] · [[07-messaging-topology]] · [[12.kafka-hosting-msk-vs-self-managed]]
- **측정 트리거**: 초기값+증설 절차 *정책*은 지금, 절대 수치는 처리량 추정으로 잡는다.
- **쟁점**: 파티션 수는 순서 계약(파티션 키=`aggregate_id`)의 일부라 사후 변경이 재분배를 유발한다. 고정 지향으로 보수적 초기값을 둔다.

### Q2. consumer lag 임계·알람
- **출처**: [[09.event-ordering-and-delivery-guarantee]] · [[09-deployment-runtime]]
- **측정 트리거**: lag 임계·알람 정책은 지금, 절대 숫자는 운영 관측치로 튜닝([[RFC-003-read-model-consistency]] Q3 프로젝션 지연과 동반).

### Q3. inbox 보존/GC 정책 + inbox 생략(자연 멱등) 판정 기준
- **출처**: [[09.event-ordering-and-delivery-guarantee]] · [[07-messaging-topology]]
- **옵션**: 컨슈머별 (a) inbox(처리된 event id) 유지 / (b) 자연 멱등이면 inbox 생략
- **쟁점**: Zero Payload upsert는 *순서 역전이 없을 때만* inbox 생략이 안전하다는 전제를 먼저 점검. 보존 기간·GC 주기와 함께 컨슈머별 (a)/(b) 귀속을 정한다.

### Q4. 비-멱등 부수효과(알림·외부 결제 연동) effectively-once 보강
- **출처**: [[09.event-ordering-and-delivery-guarantee]]
- **옵션**: (a) 부수효과 멱등키/디듀프 테이블 / (b) outbox-of-side-effects / (c) 수동 보정
- **쟁점**: 멱등 upsert로 흡수되지 않는 외부 부수효과는 at-least-once 재처리에서 중복 발사된다. 부수효과 유형별로 (a)/(b)/(c) 귀속을 정한다.

### Q5. outbox relay 단일성 구현
- **출처**: [[09-deployment-runtime]]
- **옵션**: (a) leader election / (b) `SELECT ... FOR UPDATE SKIP LOCKED`
- **쟁점**: relay 다중 인스턴스에서 동일 outbox 행 중복 발행을 막는 단일성 보장 방식.

### Q6. CDC(Debezium) 전환 기준
- **출처**: [[05.event-store-mysql-table]] · [[09-deployment-runtime]] · [[12.kafka-hosting-msk-vs-self-managed]]
- **쟁점**: 초기는 폴링 relay로 간다. CDC는 트래픽·운영 성숙도가 정당화할 때 전환한다 — 그 **전환 기준(트리거)**을 정의한다.

### Q7. 🌱 토픽 목록 확정
- **출처**: [[07-messaging-topology]] · [[09.event-ordering-and-delivery-guarantee]]
- **쟁점**: 분할 축(컨텍스트/aggregate-type)은 확정됨. 실제 토픽 목록만 도메인 이벤트 카탈로그를 대기 → 🌱 스토밍 선행.

## 닫는 방식

- Q3·Q4·Q5·Q6 = **논의로 지금 결정**.
- Q1·Q2 = **측정 트리거**(정책 지금, 숫자 운영시).
- Q7 = **🌱 스토밍 선행**(도메인 이벤트 카탈로그 의존).

## 산출물

- [[07-messaging-topology]] §파티션·토픽·inbox 보강.
- [[09.event-ordering-and-delivery-guarantee]] 미결 해소 → `Proposed`→`Accepted` 승급.
- 필요 시 신규 ADR(예: "relay 단일성", "CDC 전환 기준").

## 관련 문서
- [[RFC-002-decision-queue]] · [[07-messaging-topology]] · [[09.event-ordering-and-delivery-guarantee]] · [[09-deployment-runtime]] · [[12.kafka-hosting-msk-vs-self-managed]]
