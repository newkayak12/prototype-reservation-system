# ADR-016: 애그리거트 동시성 제어 — 비관적 동시성 제어(분산 락 Redisson + DB 행 락 폴백) + UNIQUE 백스톱

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-014-aggregate-concurrency-control]] · **설계**: [[DESIGN-006-aggregate-design]]
- **연관 ADR**: [[ADR-005-event-store-mysql-table]] · [[ADR-009-event-ordering-and-delivery-guarantee]] · [[ADR-008-saga-orchestration-vs-choreography]] · [[ADR-019-caching-redis-role]]

---

## 맥락과 문제 (Context and Problem Statement)

V1에서 예약 한 건은 사실상 DB 트랜잭션 하나였다. 행 잠금과 유니크 제약이 경합을 막아 줬다. V2의 ES 컨텍스트에는 그 잠글 행이 없다. 현재 상태는 이벤트 리플레이로 재구성하고, 쓰기는 스트림 끝에 *append*할 뿐이다.

두 손님이 같은 7시 테이블을 버전 N에서 로드해 둘 다 "자리 있음"으로 판단하면, 제어가 없는 한 둘 다 `SeatHeld`를 append해 한 자리가 두 번 점유된다.

[[ADR-005-event-store-mysql-table]]가 `(aggregate_id, sequence_no)` UNIQUE를 박아 뒀고, 이는 **스트림당 단일 라이터**를 구조적으로 보장한다 — 같은 버전 위에 두 append가 성공할 수 없다. 이 UNIQUE는 충돌한 *뒤에* 거절할 뿐, 충돌을 미리 막지는 않는다.

**그 경합을 어떻게 다루는가 — 충돌한 뒤 거절(낙관)하느냐, 미리 직렬화(비관)하느냐. 그리고 그 어느 쪽도 UNIQUE라는 최종 불변식을 대체하지 않는다.**

> 원안(2026-06-16)은 낙관을 택했으나, 핫 스트림(인기 슬롯)의 retry storm/라이브락을 트레이드오프로 자인했다. [[ADR-019-caching-redis-role]]가 Redisson을 이미 provision했고, 예약 경합을 *질서 있는 큐*로 바꾸고 둘째 이후를 *확정적으로* 거절하는 편이 낫다고 판단해 [[RFC-014-aggregate-concurrency-control]] §재개에서 비관으로 전환했다.

## 결정 동인 (Decision Drivers)

- 정확성 불변식은 락과 독립적으로 유지되어야 한다 — 락은 liveness 도구일 뿐 safety를 대체할 수 없다.
- 핫 스트림(인기 슬롯)의 retry storm/라이브락을 피해야 한다.
- Redis 가용성과 무관하게 쓰기 가용성과 비관 의미론을 유지해야 한다.
- 인프라 추가 없이 이미 provision된 Redisson([[ADR-019-caching-redis-role]])의 운영 학습 가치를 취한다.
- 애그리거트 경계를 넘는 일관성은 락이 아니라 사가([[ADR-008-saga-orchestration-vs-choreography]])로 흡수해야 한다 — 전역 락 금지.

## 검토한 선택지 (Considered Options)

**경합을 무엇으로 막나**
- **C-1 비관 락 — 분산 락(Redisson) + DB 폴백** — 스트림을 잠가 라이터를 줄 세운다.
- **C-2 낙관 + `(aggregate_id, sequence_no)` UNIQUE = expected-version (원안)** — 잠그지 않고 충돌 시 UNIQUE 위반으로 거절→재시도.
- **C-3 낙관 + 별도 버전 컬럼** — 이미 가진 UNIQUE로 충분하므로 별도 컬럼은 불필요.

**락 제공자가 죽으면 (Redis 가용성)**
- **A-1 낙관으로 폴백** — Redis 불가 시 낙관 재시도.
- **A-2 DB 행 락으로 폴백** — Redis 불가 시 command DB lock-row `FOR UPDATE`(비관 의미론 유지).
- **A-3 락 필수(폴백 없음)** — Redis 다운 = 쓰기 중단.

## 결정 (Decision Outcome)

**채택: C-1 비관 락 + A-2 DB 폴백.** 정확성은 `(aggregate_id, sequence_no)` UNIQUE가, 경합 완화는 분산 락이 맡는다 — 락이 정확성을 대체하지 않는다.

3층으로 분리한다.

| 층 | 역할 | 메커니즘 |
|---|------|---------|
| **L0 — 안전 불변식(항상)** | 정확성 최종 심판 | `(aggregate_id, sequence_no)` UNIQUE. append-only 스토어의 진짜 단일-라이터 보장. 어떤 락을 얹어도 제거하지 않는다. 락 유실 edge에서 이중 append를 최종 거절하는 백스톱 |
| **L1 — 경합 직렬화(1차)** | 라이터를 큐로 세움 | 단일 `aggregate_id`에 대한 Redisson 분산 락. `lock(aggregate_id) → load(replay)=N → handle → append(N+1) → release` |
| **L1′ — 경합 직렬화(폴백)** | Redis 다운 시 비관 의미론 유지 | Redis 불가 시 DB 행 락(per-aggregate lock-row `SELECT … FOR UPDATE`, 트랜잭션 스코프) — 비관 의미론 그대로. 낙관으로 회귀하지 않는다 — 락 제공자만 Redis→DB로 강등 |

부속 규칙:

- **락 범위 = 단일 `aggregate_id`만. 전역 락 금지.** 여러 애그리거트에 걸친 불변식은 사가([[ADR-008-saga-orchestration-vs-choreography]])로 흡수한다. 즉시 일관성(한 애그리거트) = 비관 락, 최종 일관성(여러 애그리거트) = 사가.
- **ES 쓰기 경로 한정.** 비-ES(상태+Outbox)는 DB 행 락 그대로(V1 유지).
- **dual-provider split-brain은 L0가 흡수**: 폴백 전환 창에 일부 노드 Redisson·일부 DB 락이 공존해도, 둘째 append가 UNIQUE로 거절돼 정확성은 유지된다(일시 비효율일 뿐 부정확 아님).
- **충돌 처리**: 락 보유로 동시 충돌은 대부분 소거. 잔여 표면 셋 — ① lock-wait 타임아웃 → 재시도 신호(409/503), ② 도메인 거절(reload 후 도메인 상태가 실제로 커밋되어 바뀐 경우, 예: 자리 선점) → **422**, ③ 잔여 UNIQUE 위반(락 유실) → 충돌 흡수(바운디드 재시도/409). 판별축은 "재판단 결과가 뒤집혔는가"가 아니라 **도메인 상태가 실제로 커밋되어 바뀌었는가**다. `409`는 순수 락 경합(①·③, 재시도로 풀림)에 한정.
- **granularity 위임**: 락 단위 = 애그리거트 = 직렬화 단위. 핫스팟 경계(슬롯·좌석)는 경합 범위를 줄이는 방향으로 잘게 식별한다 — 경계 결정은 [[DESIGN-006-aggregate-design]]·이벤트 스토밍에 위임.
- **세 층 분리(불변)**: 동시성 충돌(쓰기 경합·command DB) ≠ 요청 멱등(중복 제출·HTTP) ≠ 전달 멱등(중복 전달·메시징).

> 결정의 한 줄: "잠그되 믿지 않는다 — Redisson(또는 Redis 다운 시 DB)으로 한 줄로 세우고, `(aggregate_id, sequence_no)` UNIQUE가 최종 심판. 전역 락은 없고, 교차는 사가."

### 결과 (Consequences)

**좋은 점**

- 핫 스트림이 큐가 된다 — 동시 리플레이→충돌→재시도 낭비가 직렬 진행으로, 둘째 이후는 재시도 없이 확정 거절.
- 정확성은 락과 무관 — L0 UNIQUE가 락 유실·split-brain·폴백 전환 모두에서 이중 점유를 구조적으로 막는다.
- 가용성 유지 — Redis 다운에도 DB 행 락으로 비관 의미론 보존, 새 하드 의존 0.
- 인프라 추가 0 — Redisson은 [[ADR-019-caching-redis-role]]가 이미 provision.
- 경계 일관 — 순서(파티션 키)·동시성(락 범위)·교차 일관성(사가)이 모두 애그리거트 경계에서 풀린다.

**나쁜 점 / 트레이드오프**

- 무경합 쓰기에도 락 획득 비용(평소 0이던 낙관 대비) — 학습 환경에선 수용. **재검토 트리거**: 실 트래픽이 유입되어 락 비용이 관측 가능해지면 재검토한다.
- 락 제공자 이원화(Redisson/DB)로 전환 창·split-brain 처리 복잡도 — L0가 정확성은 보장하나 운영 코드·테스트 부담이 는다.
- Redisson 리스 TTL·watchdog 튜닝, lock-wait 타임아웃 정책 등 분산 락 운영 표면이 늘어난다.

### 확인 (Confirmation)

- `(aggregate_id, sequence_no)` UNIQUE 제약이 이벤트 스토어 테이블에 존재하는지 마이그레이션으로 검증한다([[ADR-005-event-store-mysql-table]]).
- append 경로가 락 획득 없이 직접 INSERT를 시도할 수 없도록 애플리케이션 서비스 계층에서 강제한다 — 코드 리뷰 체크 또는 Konsist 규칙.
- 동시 요청 통합 테스트로 "둘째 이후 확정 거절"과 "split-brain 시 L0 흡수"를 재현한다.

## 선택지 상세 (Pros and Cons of the Options)

### C-2. 낙관 + UNIQUE = expected-version (원안, 기각)
- 장점: 평소(무경합) 비용 0.
- 단점: 핫 스트림에서 충돌 시 리플레이→재시도 비용이 높아 retry storm/라이브락으로 번진다.
- 기각 사유: 인기 슬롯 경합이 빈번한 예약 도메인 특성상 트레이드오프가 과하다. UNIQUE 자체는 본 결정에서도 L0 백스톱으로 잔존한다 — 낙관을 버리는 것이지 UNIQUE를 버리는 게 아니다.

### C-3. 낙관 + 별도 버전 컬럼
- 단점: 이미 가진 `(aggregate_id, sequence_no)` UNIQUE로 동일한 목적을 달성한다.
- 기각 사유: 중복 메커니즘.

### A-1. Redis 다운 시 낙관으로 폴백
- 장점: 구현이 단순하다.
- 단점: 정상 시(비관)와 장애 시(낙관)의 동시성 의미론이 갈려 운영·테스트 부담이 는다.
- 기각 사유: 모드 전환으로 의미론이 분기하는 것을 피한다.

### A-3. 락 필수(폴백 없음)
- 단점: Redis 다운 = 쓰기 전면 중단.
- 기각 사유: 가용성 회귀. command DB는 어차피 필수 의존이라 DB 폴백이 새 하드 의존을 추가하지 않는다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: Redisson 락 키 포맷·리스 TTL·watchdog 자동 연장, lock-wait 타임아웃·백오프. DB 폴백 lock-row 스키마·트랜잭션 스코프, Redis up/down 판정(서킷 브레이커)·전환 창. 스냅샷이 낀 로드의 expected `sequence_no` 계산([[ADR-018-event-store-recovery-semantics]]). `DuplicateKeyException` → 동시성 충돌 매핑. 핫스팟 슬롯 granularity와 교차-슬롯 불변식의 사가 위임 균형은 이벤트 스토밍·[[DESIGN-006-aggregate-design]]에서 확정한다.
- 관련: [[RFC-014-aggregate-concurrency-control]] · [[DESIGN-006-aggregate-design]] · [[DESIGN-003-write-model]] · [[DESIGN-007-consistency-and-sagas]] · [[ADR-005-event-store-mysql-table]] · [[ADR-009-event-ordering-and-delivery-guarantee]] · [[ADR-008-saga-orchestration-vs-choreography]] · [[ADR-019-caching-redis-role]] · [[ADR-022-event-identity]]
- 계승: 원안(2026-06-16, 낙관 락)을 [[RFC-014-aggregate-concurrency-control]] §재개 결과로 supersede.
