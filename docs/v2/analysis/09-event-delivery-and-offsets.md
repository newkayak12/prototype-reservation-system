# 이벤트 전달과 오프셋 — 학습·정리 노트

> 성격: 결정이 아니라 **개념 정리**. 현재 [[07-query-projection-server]]·[[08-query-read-model-server]]·[[RFC-025]]·[[RFC-030]]은 **Kafka 전제**로 쓰여 있고, "Kafka 미채택"은 여기서 정하는 게 아니라 RFC/ADR로 확정할 사안. 이 노트는 그 판단에 필요한 용어와 선택지를 정리한다.
> 최종 갱신: 2026-07-20

## 1. 왜 헷갈리는가 (문제 한 장)

커맨드 서버가 예약을 저장한다. 조회 서버는 그 변화를 어떻게 알아서 read model에 반영할까?

답은 "event_store에 쌓인 이벤트를 read model로 옮기는 **배달**"이다. 그런데 이 배달을 설명하는 용어들이 **서로 다른 층**인데 한 덩어리로 들려서 헷갈린다. 특히 이 둘이 자꾸 섞인다:

- **offset** — "로그를 **어디까지 읽었나**" (구독자의 진행 위치)
- **sequenceNo** — "이 **한 행이 최신인가**" (행 하나의 버전)

이름이 둘 다 "번호"라 같아 보이지만, 하는 일도 사는 곳도 다르다. 이 노트의 절반은 이 둘을 갈라놓는 것이다.

## 2. 기초 개념 (용어 풀이)

| 용어 | 한 줄 뜻 | 사는 곳 |
|---|---|---|
| **event_store** | 커맨드 결과를 append-only로 쌓는 원천 로그. 지우지 않음 | 커맨드 쪽 DB |
| **read model** | 조회 전용으로 비정규화해둔 테이블(예: `ReservationView`) | 조회 쪽 DB |
| **projector** | event_store의 이벤트를 읽어 read model을 갱신하는 소비자 루프 | projection 서버 |
| **projection** | projector가 하는 일(이벤트 → read model 변환) 자체 | — |
| **offset / checkpoint** | 이 소비자가 로그를 **어디까지 읽었는지**. 크래시 후 이어읽기용 | 브로커 or projection DB (아래 §3에서 갈림) |
| **sequenceNo** | 한 aggregate(예약 1건)의 이벤트 **버전 번호**. 클수록 최신 | read model **각 행**(`appliedSequenceNo`) + inbox |
| **LWW 가드** | Last-Writer-Wins. 들어온 이벤트 seq가 이미 반영한 seq보다 **작거나 같으면 무시**. 역순·중복 도착 방지 | projector 로직 |
| **inbox** | "이 이벤트 이미 처리함"을 기록해 중복을 흡수하는 표 | projection DB |
| **멱등(idempotent)** | 같은 이벤트를 두 번 처리해도 결과가 같음 | — |
| **at-least-once** | "최소 한 번" 배달. 재시도 때문에 **중복은 날 수 있음** → 멱등 필수 | — |
| **effectively-once** | at-least-once + 멱등으로 만든 "사실상 한 번" 효과 | — |
| **catch-up subscription** | 소비자가 로그를 seq 순서로 따라가며 읽는 구독 방식. 끊기면 checkpoint부터 재개 | — |
| **CDC** | Change Data Capture. DB의 변경 로그(MySQL binlog)를 따라 읽어 이벤트를 뽑는 방식 | Debezium 등 |
| **outbox** | 배달 **신뢰성** 보강 장치. 상태 저장과 "발행할 이벤트"를 같은 트랜잭션에 함께 써서 유실을 막음 | 커맨드 쪽 DB |
| **consumer group** | (Kafka 용어) 같은 토픽을 나눠 읽는 소비자 묶음. **offset을 브로커가 대신 관리** | Kafka |

### 꼭 구분할 두 쌍

**offset ≠ sequenceNo** — 제일 많이 섞이는 지점.

| | offset (checkpoint) | sequenceNo (LWW 버전) |
|---|---|---|
| 질문 | "로그 어디까지 읽었나" | "이 행에 반영된 게 최신인가" |
| 단위 | 구독(projector) 당 1개 | aggregate(행) 당 1개 |
| 목적 | 크래시 후 **재개** | 재전송·역순 도착 시 **덮어쓰기 방지** |
| 없으면 | 어디부터 다시 읽을지 모름 | 오래된 이벤트가 최신 행을 덮어씀 |

→ **둘 다 필요하고, 하나로 합칠 수 없다.**

**transport ≠ 신뢰성 장치** — "Kafka(브로커)"는 이벤트를 **나르는 통로**의 선택이고, "outbox"는 그 통로로 넣기 전에 **유실을 막는 장치**다. 층이 다르다. outbox를 쓰든 안 쓰든 브로커는 쓸 수도 안 쓸 수도 있다.

## 3. 이벤트를 배달하는 방식 (선택지)

핵심 갈림은 **"브로커를 두느냐"**, 그리고 그에 따라 **"offset을 누가 관리하느냐"**다.

### A. 브로커 경유 (Kafka 등) — 현재 07 전제

`event_store → (relay) → Kafka → projector`

- offset을 **Kafka consumer group이 대신 관리**한다. projection 서버가 직접 안 짬.
- 병렬·순서 보장을 Confluent Parallel Consumer 같은 라이브러리가 제공.
- 대가: Kafka라는 **운영 인프라**가 붙는다.

### B. 브로커 없이 event_store 직접 구독 (catch-up subscription) — "Kafka 안 씀"의 자연스러운 형태

`event_store → (projection 서버가 직접 polling) → read model`

- projection 서버가 `WHERE global_seq > checkpoint`로 **직접 따라 읽는다**.
- offset(checkpoint)을 **projection 서버가 자기 DB에 소유**한다. 이게 브로커리스의 핵심 결과.
- read model 갱신 + checkpoint 전진을 **같은 DB 트랜잭션**으로 묶을 수 있어 오히려 원자성이 깔끔해짐.
- 대가: 병렬·순서·백프레셔를 **직접 설계**해야 함(브로커가 안 해주니까). 그리고 조회 서버가 커맨드 쪽 event_store를 **어떻게 읽을지**(리드 레플리카? 피드 API?) 결정 필요 — 이게 새로 생기는 숙제.

### C. CDC (binlog 테일링, Debezium 등) — 중간

`event_store 테이블 → binlog → Debezium → projector`

- 폴링 SELECT가 사라져 핫 경로 I/O 경합이 준다([[06-command-infrastructure]] 반론2가 걱정한 부분).
- 대가: Kafka Connect류 인프라가 사실상 따라옴 → "Kafka 안 씀" 취지와 어긋날 수 있음.

### 한눈 비교

| | A. 브로커(Kafka) | B. 직접 구독 | C. CDC |
|---|---|---|---|
| offset 관리 주체 | 브로커(consumer group) | **projection 서버(자기 DB)** | 커넥터 |
| read model+offset 원자성 | 분리(멱등이 메움) | **한 트랜잭션 가능** | 분리 |
| 병렬·순서 | 라이브러리 제공 | **직접 설계** | 커넥터+브로커 |
| 추가 인프라 | Kafka | 없음(DB만) | Debezium/Connect |
| 07 현재 문서 | ✅ 이걸 전제 | ✏️ 재작성 필요 | ✏️ 재작성 필요 |

## 4. "Kafka 안 쓴다"면 무엇이 바뀌나

사용자 방향은 **Kafka 미채택**(§B에 가까움). 그러면 구체적으로 이렇게 바뀐다:

1. **offset은 projection 서버가 직접 소유** — checkpoint 테이블을 자기 DB에 두고, read model 갱신과 **한 트랜잭션**으로 전진시킨다.
2. `07:37`의 "offset 스키마"(Flyway) — 지금은 Kafka offset과 중복돼 모호하지만, **브로커리스면 오히려 자연스럽게 맞는다.** 이 방향이면 모호함이 해소됨.
3. **병렬·순서·백프레셔를 직접 설계** — Parallel Consumer가 하던 "Key별 순서 + 병렬"을 대체할 방법(예: aggregate 단위 순차 + 워커 분할)을 정해야 함.
4. **조회 서버 ↔ 커맨드 event_store 접근 경로**를 새로 정해야 함 — 리드 레플리카 직접 읽기 vs 커맨드가 이벤트 피드 API 노출 vs 경량 스토어(Redis Stream 등). CQRS 경계와 얽히는 결정.
5. sequenceNo/LWW/inbox 계층은 **그대로 유효** — transport가 바뀌어도 멱등·순서 가드는 브로커리스에서 오히려 더 중요해진다.

> 이건 **문서 전파 대상**이다. 07/08/RFC-025·030이 Kafka·Parallel Consumer·`PERIODIC_TRANSACTIONAL`을 전제하므로, 방향을 확정하면 이 문서들을 함께 고쳐야 drift가 안 남는다.

## 5. 현재 문서와의 차이 (미결로 남는 것)

| 지점 | 현재 문서 | 브로커리스 방향이면 |
|---|---|---|
| offset 위치 | Kafka consumer-group (`PERIODIC_TRANSACTIONAL`, `07:90`) | projection DB checkpoint 테이블 |
| `07:37` "offset 스키마" | Kafka offset과 중복돼 모호 | 자연 해소(직접 관리와 일치) |
| 병렬·순서 | Confluent Parallel Consumer(`07:3`) | 직접 설계 필요 |
| 다중 소스 순서·원자성 | `07:142`에서 이미 "구현 시 확정"으로 미결 | 동일하게 미결(transport 무관) |
| read model↔offset 원자성 | 분리, 멱등이 메움(`07:126`) | 한 트랜잭션으로 묶을 여지 |

## 6. 잠정 정리 / 공부하며 확인할 것

- offset과 sequenceNo가 **다른 물건**이라는 것만 잡으면 대부분의 혼란은 풀린다(§2 두 쌍).
- "Kafka 안 씀"의 가장 단순한 형태는 **§B 직접 구독 + projection 소유 checkpoint**다. 원자성이 오히려 깔끔.
- 확인 필요: (a) 조회 서버가 커맨드 event_store를 읽는 경로, (b) 병렬·순서 보장 대체 설계, (c) 이 방향을 RFC/ADR로 올릴지.

## 7. 관련 문서

- [[07-query-projection-server]] — 현재 Kafka 전제 projection 설계
- [[08-query-read-model-server]] — `ReadFreshnessGate`, read-after-write
- [[06-command-infrastructure]] — outbox·relay·반론2(동일 datasource)
- [[RFC-025]] — ordering·relay·DLQ·LWW seq 가드 정의
- [[RFC-030]] — read-after-write 토큰 = `sequence_no`
