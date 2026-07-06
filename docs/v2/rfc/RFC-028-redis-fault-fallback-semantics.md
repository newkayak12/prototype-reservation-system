# RFC-028 — Redis 장애 폴백 시맨틱과 단일 인스턴스 결정의 순서 정합 (트리아지 C40)

- **상태**: 🏷 합의 (2026-07-05) — 레이트리밋=fail-open · 디듀프=cache-through(DB=보장, fail-to-DB) · HA는 role 불채택(배포 사이클 이월). ADR 비준 대기
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **선행**: [[RFC-018-caching-redis-role]](Redis 역할·단일 인스턴스) · [[RFC-014-aggregate-concurrency-control]](L1→L1′ 락 폴백) · [[DESIGN-013-api-contract]](요청-단 멱등) · 인덱스 [[RFC-INDEX]]
- **닫으면**: [[DESIGN-018-caching]] §6.1 폴백 시맨틱 확정 + §8 후속 항목 갱신 + 신규 ADR
- **분석 출처**: [[06-design-weakness-triage]] C40 (D-018 Devil's Advocate line 259 · §6.1 line 140-141 · §4.4 line 119)

---

## 배경 (Background)

### 시나리오: Redis가 흔들리는 순간

DESIGN-018은 Redis에 세 자리를 뒀다 — ①요청-단 멱등 디듀프 ②레이트리밋 카운터 ③Redisson 분산 락. ③은 이미 폴백이 있다(Redis 불가 → DB 비관 락 L1′, [[RFC-014-aggregate-concurrency-control]]). 그러나 ①②는 "Redis가 죽으면 무엇을 할지"가 미정인 채 **"단일 인스턴스면 충분"만 먼저 확정**됐다(RFC-018/ADR-19). 버티는 법을 안 정하고 대수만 정한 순서 역전 — D-018 스스로 line 259 Devil's Advocate에 같은 문장으로 적어뒀다.

### 먼저 용어

| 말 | 뜻 |
|---|---|
| **레이트리밋** | "N초에 M번"으로 요청 수를 제한 — 부하 보호 heuristic |
| **멱등 디듀프** | 같은 요청이 두 번 오면 두 번째를 걸러 중복 생성 방지 |
| **fail-open / fail-closed** | 의존 컴포넌트 장애 시 통과시킴 / 막음 |
| **cache-through** | 캐시가 backing store(DB) 앞에 서서 read-through(miss 시 DB 로드)·write-through(DB에 동기 기록)로 정합 유지 |
| **SoR (system of record)** | 진실의 원천. 여기선 DB |
| **GCRA** | leaky bucket을 TAT(이론 도착시각) 타임스탬프 하나로 계산하는 레이트리밋 알고리즘 |
| **HA** | 고가용성 — Sentinel(자동 failover)·Cluster(샤딩) 등 |

---

## 맥락 (Context)

- **자산 — 락은 이미 폴백을 확정했다.** ③ L1→L1′([[RFC-014-aggregate-concurrency-control]])이 "Redis 죽어도 정확성 포기 안 함"의 선례다. → ①②도 같은 규율로 닫는다.
- **자산 — 디듀프의 진짜 보장은 DB에 있다.** event_store `(aggregate_id, sequence_no)` UNIQUE + 도메인 자연 유니크([[DESIGN-013-api-contract]]: "도메인이 직접 멱등 흡수, 클라 키 불필요"). → Redis 디듀프는 최적화지 보장이 아니다.
- **한계 — Redis 디듀프는 살아있어도 보장이 아니다.** `allkeys-lru`가 윈도 안에서 디듀프 키를 evict하면 막으려던 중복을 도로 연다(D-018 §4.4 line 119). → 정확성을 Redis에 얹으면 안 된다는 증거.

핵심 긴장 — **Redis 장애 시 ①②의 행동을 확정하되, 각 자리의 성격(정확성 불변식이냐 부하 보호냐)에 맞게 가르고, 무트래픽 프로토타입에 과한 HA를 얹지 않는다.**

---

## Goal / Non-goal

**Goal**
- ① 멱등 디듀프의 Redis 장애 폴백을 확정한다.
- ② 레이트리밋의 Redis 장애 폴백을 확정한다.
- 그 폴백 시맨틱과 정합되게 단일 인스턴스 결정(RFC-018)을 재확인한다.

**Non-goal**
- Redis 호스팅·토폴로지(관리형/자가, 인스턴스 개수). → 배포 사이클 [[DESIGN-010-deployment-runtime]]·[[RFC-007-deployment-infra-ops]].
- 레이트리밋 알고리즘·디듀프 키 구성의 구현 상세. → 구현 사이클(본 RFC는 방향 메모만).
- ③ 분산 락 폴백. → 이미 [[RFC-014-aggregate-concurrency-control]] 확정.

---

## 논의 (Discussion)

### 논점 1. 레이트리밋(②) — 통과 허용인가 요청 거부인가

레이트리밋은 **정확성 불변식이 아니라 부하 보호 heuristic**이다. Redis 장애 시 요청을 거부하면 *내 인프라 장애를 사용자 장애로 전이*시킨다 — 안티패턴. 통과시키면 보호를 잃되 가용성을 지킨다.

**결정: fail-open(통과 허용).** 내부 fault를 외부로 전이시키지 않는다. 심층방어로 애플리케이션 레벨 써킷 브레이커를 별도 검토.

### 논점 2. 멱등 디듀프(①) — fail-open을 그대로 적용하면 안 된다

디듀프는 **정확성 불변식**이다 — 통과시키면 예약·결제 중복 생성. 레이트리밋 논리를 그대로 얹을 수 없다. 그러나 §4.4 line 119가 증명하듯 **Redis 디듀프는 살아있어도 보장이 아니다**(eviction이 중복을 연다). 따라서 정확성은 처음부터 DB에 있어야 한다.

**결정: cache-through.** DB를 SoR로 두고 Redis를 그 앞의 coherent 캐시로 쓴다.
- write-through로 디듀프 레코드가 **DB(UNIQUE)에 반드시 착지**, read-through로 조회.
- **eviction이 무해해진다** — read-through miss가 DB에서 다시 채운다. line 119 구멍이 닫힌다.
- **폴백은 "fail-open"이 아니라 "fail-to-DB"** — Redis 죽으면 통과시키고 뒤에서 줍는 게 아니라 그냥 DB로 강등돼 정확성에 *창(window)이 없다*.
- **동시 중복 직렬화는 DB UNIQUE가 한다** — read-through는 둘 다 miss날 수 있으므로 캐시가 가드가 아니라 **DB UNIQUE가 가드**다(과대평가 방지).

### 논점 3. HA(Sentinel/Cluster)는 답이 아니다

1. **HA는 폴백 시맨틱을 대체 못 한다.** Sentinel failover엔 수 초 unavailable 창, Cluster엔 resharding 창이 있어 그 순간 여전히 폴백 경로를 탄다. HA는 폴백 *발동 확률*만 낮춘다.
2. **HA는 오히려 단일 인스턴스 결정·등급-1 프레임과 모순이다.** ② fail-open + ① fail-to-DB로 만들면 Redis에 남는 건 전부 손실 허용(등급 1, D-018 line 92) — 버려도 되는 상태를 지키려 Sentinel/Cluster 운영 복잡도를 얹는 건 무트래픽 프로토타입에 과설계([[v2-optimize-for-learning-not-cost]] 반대 방향).

**결정: HA는 role 설계에서 불채택.** 실 트래픽이 생기면 배포 사이클([[DESIGN-010-deployment-runtime]]·[[RFC-007-deployment-infra-ops]])에서 꺼내는 옵션으로 이월.

### 순서 역전의 해소

폴백 시맨틱을 먼저 정하니(② fail-open, ① fail-to-DB) Redis가 손실 허용이 되고 → **단일 인스턴스가 비로소 정당화**된다(거꾸로가 아니라). 등급-1 프레임도 엄밀해진다 — Redis 디듀프 사본은 DB에 원본이 있어 진짜로 버려도 된다.

---

## 결정 요약

1. **레이트리밋 = fail-open** — 내부 fault를 외부로 전이 안 함.
2. **멱등 디듀프 = cache-through** — DB=SoR·UNIQUE=보장, Redis=coherent 캐시, 장애 시 fail-to-DB(정확성 창 없음), eviction 무해.
3. **HA(Sentinel/Cluster) = role 불채택** — 과설계. 배포 사이클 이월.
4. **단일 인스턴스(RFC-018) 재확인** — 위 폴백으로 Redis가 손실 허용이 되어 정당.

## 구현 방향 메모 (RFC 결정 아님 · 구현 사이클 소관)

- **레이트리밋 알고리즘**: GCRA 권장(키당 TAT 값 1개, 메모리·구현 경제적). 직접 구현 시 **TAT 갱신은 반드시 서버측 원자(Lua)** — 앱 코드 GET-후-SET은 인스턴스 간 경합으로 샌다. Redisson GCRA를 쓰면 검증된 원자 구현을 그대로 얻는다. 학습 목적이면 Lua 직접 작성도 정당.
- **"Redisson 안 씀" caveat**: ③ 분산 락(L1)이 Redisson을 "반드시 provision" 요구(D-018 line 98)하므로, 레이트리밋에서 빼도 **Redisson은 스택에 남는다** — 의존성을 던다기보다 알고리즘을 고르는 것.
- **디듀프 cache-through 부수 비용**: idem 레코드가 DB에도 남으므로 **DB쪽 보존/GC 정책**이 필요(짧은 윈도 디듀프라 영구 아님) → D-018 §8에 추가.

---

## 관련 문서

- 원 역할: [[RFC-018-caching-redis-role]] · 설계: [[DESIGN-018-caching]] (§6.1 폴백 · §4.4 line 119 · §8)
- 분석: [[06-design-weakness-triage]] (C40)
- 재사용 자산: [[RFC-014-aggregate-concurrency-control]] · [[DESIGN-013-api-contract]]
