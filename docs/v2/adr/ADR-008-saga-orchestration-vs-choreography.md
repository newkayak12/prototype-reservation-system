# ADR-008: 사가 조율 — 코레오그래피 기본, PM 인프라 불필요

- **상태**: Proposed
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-006-saga-process-manager]] · [[RFC-016-payment-integration-boundary]] · **설계**: [[DESIGN-007-consistency-and-sagas]]
- **연관 ADR**: [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-009-event-ordering-and-delivery-guarantee]] · [[ADR-005-event-store-mysql-table]]

---

## 맥락과 문제 (Context and Problem Statement)

V1에서 예약 하나를 만드는 건 사실상 DB 트랜잭션 하나였다. 자리를 확인하고 잡는 게 전부 한 트랜잭션이라, 중간에 실패하면 통째로 롤백돼 따로 조율할 게 없었다.

V2는 컨텍스트를 쪼갠다. "예약하기"가 이제 여러 컨텍스트에 걸친다 — `timetable`이 자리를 임시 점유하고(`SeatHeld`), `payment`가 결제를 기다려 확정하고(`PaymentConfirmed`), `reservation`이 예약을 확정한다(`ReservationConfirmed`). 각자 다른 애그리거트, 다른 저장소, top-level 모듈이 분리돼 있어 이 세 단계를 한 트랜잭션으로 묶을 수 없다.

중간이 어긋날 수 있다 — 결제가 제때 안 오면 점유를 풀어야 하고(타임아웃 + 보상 `SeatReleased`), 결제는 됐는데 확정이 깨지면 환불해야 한다(`PaymentRefunded`). V1(`07.reservation`)은 "Reservation 생성 불가 시 Timetable 상태 보정"을 보상 트랜잭션 미결정 항목으로 남겼다.

**단일 트랜잭션이 없는 이 컨텍스트 횡단 흐름을 무엇으로 조율하는가 — 중앙 지휘자를 세우는 오케스트레이션(프로세스 매니저)인가, 각 컨텍스트가 이벤트를 듣고 자율로 반응하는 코레오그래피인가.**

## 결정 동인 (Decision Drivers)

- 예약 흐름(확정·취소·노쇼)의 실제 스텝 수와 분기 복잡도 — 인프라 investment를 정당화하는가.
- 관리 포인트 수와 컨텍스트 간 결합도 — 조율자가 모든 참여자의 커맨드를 알아야 하면 결합이 는다.
- 타임아웃 감시("시계는 누가 보나")와 되감기("어디까지 왔는지 누가 아나")를 중앙 없이 해소할 수 있는가.
- 무트래픽 프로토타입 단계에서 새 상태 머신 인프라를 신설할 학습·운영 가치가 있는가.
- V1 자산(스케줄러 패턴, PoisonMessage 운영 흐름)을 재사용할 수 있는가.

## 검토한 선택지 (Considered Options)

- **코레오그래피 기본** — 중앙 조율자 없음. 각 컨텍스트가 이벤트를 듣고 자기 aggregate 상태를 보고 자기 일과 자기 보상을 판단한다.
- **오케스트레이션(프로세스 매니저) 기본** — PM이 단계 순서·보상·타임아웃을 들고 각 컨텍스트에 커맨드를 보낸다.
- **혼합** — 라이프사이클(확정·취소·노쇼)은 PM, 단발 통지는 코레오그래피.

## 결정 (Decision Outcome)

**채택: 코레오그래피 기본.** PM을 정당화하던 두 논거 — 타임아웃 감시, 되감기 주인 — 가 모두 코레오그래피로 해소되고, PM은 관리 포인트를 N에서 1+N으로 늘릴 뿐 복잡도를 줄이지 않고 위치만 옮긴다(RFC-006 논점1). 예약 흐름(확정·취소·노쇼)이 전부 2~3스텝 선형이라 PM 상태 머신 인프라 비용을 정당화하지 못한다.

구조·규칙:

- **타임아웃 = `timetable` TTL 자치.** 임시 점유는 TTL을 가지며, "내 점유가 언제 죽는가"는 `timetable` 애그리거트의 불변식이다. 스케줄러가 주기적으로 깨어 TTL 지난 `SeatHeld`를 찾아 `SeatReleased` 보상을 발행한다 — V1 스케줄러 패턴 재사용. `reservation`은 `SeatReleased`를 구독해 상태를 EXPIRED로 전이한다. 폴링 주기 ≤ TTL 관계는 구현 사이클에서 수치로 검증한다.
- **보상 = 각 컨텍스트 자기 책임.** 중앙에서 보상 순서를 제어하지 않는다. `payment`는 자기가 `Confirmed` 상태인지 보고 환불을 결정하고, `timetable`은 자기가 `SeatHeld` 상태인지 보고 해제를 결정한다. 보상은 삭제가 아니라 새 이벤트다 — append-only 불변식 유지([[ADR-005-event-store-mysql-table]]). 보상은 멱등이어야 한다 — 같은 보상 이벤트를 두 번 받아도 한 번 적용한 것과 같아야 하며, 판단은 자기 aggregate 상태 + `sequence_no` 가드로 한다.
- **실패 처리 = V1 PoisonMessage 운영 흐름 계승.** 사가 스텝 실패만을 위한 별도 파이프라인을 세우지 않는다 — 메시지가 반복 실패하면 기존 저장·추적·수동 재처리·알림 경로에 그대로 태운다. 부분 보상 잔류(예: 좌석은 풀렸는데 환불이 실패한 상태)의 구체 격리 방식은 [[ADR-009-event-ordering-and-delivery-guarantee]]의 꼬리 격리 메커니즘으로 흡수한다 — 여기서는 "별도 운영 표면을 새로 세우지 않는다"는 원칙만 확정한다.
- **결제 사가 표면 = 3 이벤트 동결.** 코레오그래피에서 `payment`가 다른 컨텍스트에 노출하는 이벤트는 `PaymentConfirmed`/`PaymentFailed`/`PaymentRefunded` 3개로 동결한다([[RFC-016-payment-integration-boundary]]). PG 벤더가 바뀌어도 이 표면은 불변이다 — `payment` 내부의 ACL·웹훅 검증·대사 상세는 이 ADR의 범위 밖이며 해당 결정(15.payment-acl-boundary)으로 위임한다.
- **교차 애그리거트 불변식은 락이 아니라 사가가 흡수한다.** [[ADR-016-aggregate-concurrency-pessimistic-lock]]의 락 범위는 단일 `aggregate_id`로 한정되고 전역 락은 금지된다 — 즉시 일관성(한 애그리거트)은 락, 최종 일관성(여러 애그리거트)은 이 ADR의 코레오그래피 사가가 맡는다. 경합(paid-after-expiry: TTL 만료와 결제 완료가 엇갈리는 레이스)도 별도 락이 아니라 aggregate 상태 가드로 방어한다 — `reservation`이 EXPIRED 상태에서 `PaymentConfirmed`를 받으면 확정을 거부하고 `RefundRequired`를 발행해 `payment`가 환불을 처리한다.

> 결정의 한 줄: "2~3스텝 선형 흐름에 PM 상태 머신은 과투자다. 각 컨텍스트가 자기 aggregate 상태를 보고 자기 보상을 책임지면 충분하고, 애그리거트를 넘는 일관성은 락이 아니라 사가가 흡수한다."

### 결과 (Consequences)

- 좋은 점: 관리 포인트가 N(이벤트 핸들러)뿐 — PM(1+N)보다 경량이고 결합이 최소다.
- 좋은 점: 각 컨텍스트가 자기 불변식과 자기 보상을 자기가 지킨다 — DDD 경계를 존중한다.
- 좋은 점: 타임아웃·PoisonMessage 모두 V1 자산을 그대로 재사용 — 새 인프라 신설 0.
- 좋은 점: 경합(paid-after-expiry)이 aggregate 상태 가드만으로 방어돼 별도 조율자가 필요 없다.
- 나쁜 점 / 트레이드오프: "이 사가가 지금 어느 단계인가"를 한 곳에서 조회할 단일 지점이 없다 — 여러 컨텍스트의 이벤트 스트림을 correlationId로 조인해야 한다. 이 관측 공백은 트레이드오프로 수용하며, DLQ 알림 + correlationId 상관분석으로 보완한다.
- 나쁜 점 / 트레이드오프: 흐름이 5스텝 이상이거나 조건부 분기가 복잡해지는 미래 요구가 생기면 이 결정을 재검토해야 한다. **재검토 트리거(모두 해당 시)**: ① 단계 5개 이상 + 복잡한 조건부 분기, ② 여러 컨텍스트 상태를 중앙에서 조합해야만 다음 단계가 결정됨, ③ 각 컨텍스트의 자치적 보상으로 정합성을 보장할 수 없음. 무트래픽 단계에서는 이 조건을 정성적 판정으로만 두고 운영화하지 않는다(YAGNI).

### 확인 (Confirmation)

- 각 컨텍스트의 상태 가드가 서로 맞물리는지(예: EXPIRED 상태에서 확정 거부가 실제로 동작하는지) 계약 테스트로 검증한다([[RFC-009-testing-quality-gates]]).
- `payment`가 노출하는 이벤트가 `PaymentConfirmed`/`PaymentFailed`/`PaymentRefunded` 3종으로 고정되는지 코드 리뷰 또는 계약 테스트로 확인한다.
- 사가 스텝 실패가 별도 파이프라인이 아니라 기존 PoisonMessage 저장·추적·알림 경로를 타는지 통합 테스트로 재현한다.
- paid-after-expiry 레이스 시나리오(TTL 만료 후 결제 완료 도착)에서 확정 거부 + `RefundRequired` 발행이 재현되는지 통합 테스트로 검증한다.

## 선택지 상세 (Pros and Cons of the Options)

### 오케스트레이션(프로세스 매니저) 기본

- 장점: 전체 흐름의 "지금 어느 단계인가"를 한 곳(PM 상태)에서 조회할 수 있다.
- 단점: 관리 포인트가 1+N(PM + 각 컨텍스트 핸들러)으로 증가한다. PM이 모든 참여 컨텍스트의 커맨드를 알아야 해 결합이 는다. PM 전용 상태 모델·영속화·재구성(rehydrate) 인프라가 별도로 필요하다.
- 기각 사유: PM을 정당화하던 두 논거(타임아웃 감시, 되감기 주인)가 코레오그래피로 이미 해소된다. 남는 가치는 "가시성" 하나인데, 2~3스텝 선형 흐름에서 상태 머신 인프라 비용을 정당화하지 못한다.

### 혼합 (라이프사이클=PM, 단발 통지=코레오그래피)

- 단점: "이게 라이프사이클인지 단발 통지인지" 판단 비용이 추가로 든다.
- 기각 사유: 무게중심이 실질적으로 PM과 같다 — 예약 흐름 자체가 라이프사이클로 분류되므로 오케스트레이션 기본과 실질적 차이가 없다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 구체 TTL 값·폴링 주기(화면/UX 요구에 묶여 확정), 구체 이벤트 카탈로그(이벤트 스토밍 재실시 후 확정), 부분 보상 잔류 상태의 구체 격리 스키마([[ADR-009-event-ordering-and-delivery-guarantee]] 범위). PM 전환 3조건은 정성적 판정으로 남기고 무트래픽 단계에서는 운영화하지 않는다.
- 관련: [[RFC-006-saga-process-manager]] · [[RFC-016-payment-integration-boundary]] · [[DESIGN-007-consistency-and-sagas]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-009-event-ordering-and-delivery-guarantee]] · [[ADR-005-event-store-mysql-table]] · [[RFC-009-testing-quality-gates]]
- 계승: `08.saga-orchestration-vs-choreography.md`(v2 초기 스케치) — 코레오그래피 기본 결론과 PM 전환 3조건은 그대로 승계한다. 스케치 이후 확정된 결제 이벤트 표면 3종 동결([[RFC-016-payment-integration-boundary]])과 부분 보상 잔류의 꼬리 격리 배선([[DESIGN-007-consistency-and-sagas]] §4.9)을 반영해 이 ADR이 스케치를 대체한다. `07.reservation`이 남긴 보상 트랜잭션 미결정 항목을 이 ADR이 이어받아 코레오그래피 보상 이벤트로 정식화한다.
