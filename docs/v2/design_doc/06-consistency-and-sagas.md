# V2 Design Doc — 06. Consistency & Sagas

- **상위 결정**: [[08.saga-orchestration-vs-choreography]]
- **개요**: [[00-design-overview]] · **쓰기 모델**: [[02-write-model]]
- **계승**: [[07.reservation]] (v1 — Timetable·Reservation EDA, 보상 트랜잭션은 v1에서 미결정으로 남았던 항목)

> 9개 컨텍스트가 하나의 예약 시스템으로 어떻게 맞물리는가. 핵심은 **일관성 경계를 어디에 긋고, 경계를 넘는 비즈니스 트랜잭션을 무엇으로 잇는가**다.

## 일관성 경계: 애그리거트 = 트랜잭션, 그 바깥 = 최종 일관성

V2의 일관성 규칙은 두 줄이다.

1. **애그리거트 하나 = 강한 일관성 = 한 트랜잭션.** 한 커맨드는 단 하나의 애그리거트만 원자적으로 바꾼다. 불변식은 그 애그리거트 안에서 동기 검증된다([[02-write-model]] — `handle(command) → events`).
2. **애그리거트 둘 이상 = 최종 일관성 = 이벤트.** 여러 애그리거트(특히 다른 컨텍스트)에 걸친 변화는 한 트랜잭션으로 묶지 않는다. 이벤트로 비동기 전파한다([[00-design-overview]] 불변식 #2·#3).

이 규칙은 새로 만든 것이 아니라 [[07.reservation]]에서 이미 실천하던 것을 명문화한 것이다 — Timetable 점유와 Reservation 생성은 별개 트랜잭션이고, 그 사이를 Kafka 이벤트가 잇는다.

```mermaid
graph LR
    subgraph T1 [트랜잭션 1 · 강한 일관성]
        A1[애그리거트 A<br/>불변식 동기 검증]
    end
    subgraph T2 [트랜잭션 2 · 강한 일관성]
        A2[애그리거트 B<br/>불변식 동기 검증]
    end
    A1 -->|이벤트 · 최종 일관성| A2
```

> "한 커맨드 = 한 애그리거트"는 규율이다. 두 애그리거트를 한 트랜잭션에서 바꾸고 싶어지면, 그것은 경계를 잘못 그었다는 신호이거나 사가가 필요하다는 신호다.

## 컨텍스트 횡단 비즈니스 트랜잭션

예약 시스템의 의미 있는 동작 상당수는 **여러 컨텍스트에 걸친다**. 대표 예 — "예약을 잡는다":

| 단계 | 컨텍스트 | 애그리거트 변화 |
|------|----------|-----------------|
| 1 | `timetable` (ES) | 해당 슬롯을 임시 점유(hold) |
| 2 | `restaurant` (ES) | 영업/가용성·휴무 충돌 확인 |
| 3 | `reservation` (ES) | 예약 확정(confirmed) |
| (참여) | `user` (비-ES) | 예약자 식별·자격 |

이 4단계는 **한 트랜잭션에 들어갈 수 없다**(컨텍스트마다 다른 애그리거트·다른 저장소·top-level 모듈 분리). 따라서 단계 사이는 이벤트로 잇고, 중간 실패는 **보상 트랜잭션**으로 되돌린다. 이것이 사가(saga)다.

## 사가 = 최종 일관성 비즈니스 트랜잭션

사가는 "여러 로컬 트랜잭션을 이벤트로 잇고, 실패하면 앞선 단계를 보상하는" 패턴이다. 두 가지 조율 방식이 있다.

- **안무(Choreography)**: 중앙 조율자 없음. 각 컨텍스트가 이벤트를 듣고 자기 일을 한 뒤 다음 이벤트를 낸다. 단순 반응에 적합.
- **오케스트레이션(Orchestration)**: 프로세스 매니저(process manager)가 단계 순서·보상·타임아웃을 들고 커맨드를 보낸다. 멀티스텝·라이프사이클에 적합.

**무엇을 어디에 쓰는지는 [[08.saga-orchestration-vs-choreography]]에서 결정한다.** 요약: **예약 라이프사이클 = 오케스트레이션(프로세스 매니저), 단순 통지·동기화 = 안무.**

## 예약 사가 — 오케스트레이션 (권고 적용)

예약 확정은 멀티스텝이고, 중간 실패 시 임시 점유를 풀어야 하며, 사용자가 결제/확인을 안 하면 만료시켜야 한다. 이런 라이프사이클은 흐름을 한 곳에서 보는 **프로세스 매니저**가 맞다.

```mermaid
sequenceDiagram
    participant U as Actor
    participant PM as ReservationProcessManager<br/>(command · reservation)
    participant TT as timetable (ES)
    participant R as restaurant (ES)
    participant RES as reservation (ES)

    U->>PM: 예약 요청
    PM->>TT: HoldSlot (커맨드)
    TT-->>PM: SlotHeld (이벤트)
    PM->>R: CheckAvailability (커맨드)
    R-->>PM: AvailabilityConfirmed / Rejected
    alt 가용 확인
        PM->>RES: ConfirmReservation
        RES-->>PM: ReservationConfirmed
        PM->>TT: CommitHold (점유 확정)
    else 거절 또는 타임아웃
        PM->>TT: ReleaseHold (보상)
        PM-->>U: 예약 실패
    end
```

- 프로세스 매니저는 `command-module`의 `reservation` 컨텍스트에 둔다(예약 라이프사이클의 주인이 예약 컨텍스트이므로). 단계 진행은 이벤트 구독으로 받고, 다음 단계는 커맨드로 보낸다.
- 프로세스 매니저 자신의 상태(어느 단계인가)도 이벤트 소싱으로 기록할 수 있다 — `reservation`이 ES이므로 자연스럽다. 상태 모델 세부는 구현 사이클에서 확정(TBD).

## 보상 트랜잭션

분산 트랜잭션엔 롤백이 없다. 이미 커밋된 로컬 트랜잭션은 **반대 동작(보상)** 으로 되돌린다.

| 정방향 | 보상 |
|--------|------|
| `HoldSlot` (timetable 임시 점유) | `ReleaseHold` (점유 해제) |
| `ConfirmReservation` | `CancelReservation` |
| (상태 차감류) | 역연산 이벤트 |

- 보상은 **삭제가 아니라 새 이벤트**다. ES 컨텍스트에서 점유를 "없던 일"로 지우지 않고 `SlotReleased` 를 append 한다 — append-only 불변식 유지([[05.event-store-mysql-table]]), 이력·감사 보존.
- 보상은 **멱등**이어야 한다. 같은 보상 이벤트를 두 번 받아도 한 번 적용한 것과 같아야 한다(이벤트 재처리·중복 전달 대비). Zero Payload 재조회([[07.reservation]])로 "이미 풀린 점유면 무시"를 판단한다.
- [[07.reservation]]이 미결정으로 남겼던 "Reservation 생성 불가 시 Timetable 상태 보정"이 바로 이 보상이다. V2는 이를 프로세스 매니저의 명시적 보상 단계로 끌어올린다.

## 사가 타임아웃 / 만료

사가는 영원히 열려 있으면 안 된다. 두 종류의 시간 제약을 다룬다.

### (가) 임시 점유 만료 — 도메인 본질

`timetable`의 임시 점유(hold)는 **TTL**을 갖는다. 사용자가 N분 안에 확정하지 않으면 점유는 자동 만료되어 슬롯이 풀린다.

- 만료의 권위는 **`timetable` 애그리거트**에 둔다 — "내 점유가 언제 죽는가"는 timetable의 불변식이다.
- 만료 트리거: 스케줄러가 만료 대상을 깨워 `HoldExpired` 이벤트를 발생. (v1의 Outbox 재처리와 같은 스케줄러 인프라 결을 따른다 — [[07.reservation]].)
- `HoldExpired` 를 프로세스 매니저가 구독해 열린 예약 사가를 실패로 닫는다.

### (나) 사가 스텝 타임아웃 — 프로세스 매니저

프로세스 매니저가 다음 단계 이벤트(예: `AvailabilityConfirmed`)를 일정 시간 안에 못 받으면, 타임아웃으로 간주하고 보상 경로로 진입한다.

- 타임아웃 역시 스케줄러가 "기한 지난 미완 사가"를 깨우는 방식(폴링). 전용 타이머 인프라는 도입하지 않는다(YAGNI — [[00-design-overview]] 불변식 #4).
- 구체 TTL 값·타임아웃 임계치는 화면/UX 요구에 묶이므로 구현 사이클에서 확정(TBD).

```mermaid
graph LR
    HOLD[SlotHeld] -->|TTL 경과| EXP[HoldExpired]
    EXP --> PM[ProcessManager]
    PM -->|사가 실패 처리| REL[ReleaseHold · 보상]
```

## 중복·순서·재처리 (이미 가진 자산 활용)

컨텍스트 횡단 일관성의 실무 난점은 대부분 메시징 특성에서 온다. V2는 새로 발명하지 않고 [[07.reservation]] 자산을 재사용한다.

- **순서**: Kafka 파티션 키 = `aggregate_id` 로 애그리거트별 순서 보장. 토픽 분할·전달 보장의 세부는 [[07-messaging-topology]].
- **중복(at-least-once)**: 컨슈머·프로젝션·보상 모두 **멱등** 설계. Zero Payload 재조회로 현재 상태 기준 판단.
- **재처리 실패**: 스케줄러 기반 Outbox 재시도 + PoisonMessage 별도 관리(v1 계승). 사가 단계 실패도 같은 PoisonMessage 운영 흐름에 태운다.

## 정리

- 애그리거트 안 = 강한 일관성, 애그리거트 밖 = 이벤트 기반 최종 일관성.
- 컨텍스트 횡단 트랜잭션(예약 확정)은 **사가**. 멀티스텝 라이프사이클은 **오케스트레이션(프로세스 매니저)**, 단순 반응은 **안무** — 결정은 [[08.saga-orchestration-vs-choreography]].
- 실패는 롤백이 아니라 **보상 이벤트**(append-only·멱등).
- 시간 제약은 **임시 점유 TTL**(timetable 권위) + **사가 스텝 타임아웃**(프로세스 매니저), 둘 다 스케줄러 폴링으로(YAGNI).
- 구체 이벤트 카탈로그·TTL 값·프로세스 매니저 상태 모델은 이벤트 스토밍 후 TBD.

## 관련 문서
- [[00-design-overview]] · [[02-write-model]] · [[03-read-model]] · [[07-messaging-topology]]
- ADR: [[08.saga-orchestration-vs-choreography]] · [[09.event-ordering-and-delivery-guarantee]] · [[05.event-store-mysql-table]] · [[02.selective-event-sourcing-scope]]
- 계승: [[07.reservation]]
