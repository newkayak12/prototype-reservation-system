# DESIGN-018: Caching (캐싱·Redis의 V2 역할)

- **상태**: Accepted
- **작성자**: Team
- **작성일**: 2026-06-30
- **최종 수정일**: 2026-06-30
- **관련 RFC**: [[RFC-018-caching-redis-role]] · [[RFC-014-aggregate-concurrency-control]] · [[RFC-002-read-model-consistency]] · [[RFC-015-authorization-model]] · [[RFC-019-auth-token-transport]]
- **관련 ADR**: [[19.caching-redis-role]]
- **관련 Design Doc**: [[DESIGN-001]] · [[DESIGN-003]] · [[DESIGN-004]] · [[DESIGN-006]] · [[DESIGN-007]] · [[DESIGN-010]] · [[DESIGN-013]] · [[DESIGN-017]]

---

## 1. Background

V1에서 Redis(+Redisson)는 한 역할이 아니었다 — Redisson 분산 락·세마포어(`timetable` 자리 점유), 레이트리미터(`AcquireRateLimitRedisAdapter`), 리프레시 토큰 저장(`SaveGeneralUserRefreshToken`), 피처 플래그(`FeatureFlagRedisTemplate`), 재시도 컨텍스트(`RedisRetryContextCache`), `RedisCacheManager`. 즉 *동기 모놀리식이 못 들던 휘발성·분산 상태를 한 군데 모은 사이드 저장소*였다. ("Redis 세션"은 V1에 실제로는 없다 — `HttpSession` 미사용, 인증 상태로 남는 건 refresh 토큰뿐.)

V2가 이 그림을 둘에서 흔든다.

- **읽기가 프로젝션 조회로 갈렸다**([[DESIGN-004]]) — 프로젝션 read model 자체가 이미 질의 모양으로 비정규화된 머티리얼라이즈드 뷰, 곧 *영속 캐시*다.
- **인증이 무상태 JWT로 정리됐다**([[RFC-015-authorization-model]]·[[DESIGN-017]]) — refresh 서버 사본이 제거됐다.

이 둘을 빼고 나면 Redis에 남는 자리가 좁아진다. 본 문서는 그 좁혀진 역할의 *설계 확정*이다. "읽기를 어떻게 빠르게 하나"는 프로젝션의 몫([[DESIGN-004]])이고, refresh·폐기 같은 인증 부산물의 거취는 [[DESIGN-017]]에서 이미 닫혔다. 여긴 *그 둘을 뺀 나머지 Redis*가 무엇을 들고, durability 등급이 왜 하나로 줄며, 호스팅·토폴로지를 왜 여기서 정하지 않는가를 다룬다.

> Redis의 *호스팅* — 관리형 vs 자가, 단일/센티넬/클러스터·인스턴스 개수 — 는 배포 사이클 [[DESIGN-010]]·[[RFC-007-deployment-infra-ops]]의 몫. 여긴 역할만.

## 2. Goal

- V2에서 Redis의 아키텍처 역할을 확정한다: "읽기 캐시"가 아니라 "분산 조정·휘발성 상태" 전용.
- read model 앞에 Redis 캐시 층을 기본으로 두지 않는다는 결정과 그 우선순위를 못 박는다.
- Redis에 정당하게 남는 후보 세 자리(멱등 디듀프·레이트리밋·분산 락)를 명시하고 경계를 확정한다.
- 인증 부산물 제거에 따른 단일 durability 등급·단일 인스턴스 단순화를 확정한다.

## 3. Non-Goal

- **Redis 호스팅·토폴로지**: 관리형 vs 자가, 단일/센티넬/클러스터·인스턴스 개수 — 배포 사이클 [[DESIGN-010]]·[[RFC-007-deployment-infra-ops]]의 몫.
- **read model 설계**: 화면별 프로젝션 설계·스키마 분리 — [[DESIGN-004]]의 몫.
- **인증 토큰 모델**: refresh 저장·폐기 전략 — [[DESIGN-017]]에서 확정됨.
- **캐시를 둘 "프로젝션이 싸게 못 푸는 패턴"의 식별**: 읽기 분포 측정 트리거와 손익분기 임계는 후속 사이클로 넘긴다(§8 참조).
- **V1 Redis 잔재의 항목별 귀속**: 피처 플래그·재시도 컨텍스트·Redisson 락 사용 면적 — 각 항목의 V2 거취가 정해질 때.

## 4. Proposed Solution

### 4.1 High-Level Architecture

V2 Redis의 역할은 세 후보로 압축된다. 세 후보의 공통점: "읽기를 빠르게 하려는 캐시"가 아니라 "분산 환경에서 인스턴스 간에 공유해야 하는, 본질적으로 휘발성이고 TTL이 자연스러운 조정 상태"다.

```
[ Write Path ]
  Command → Redisson 분산 락(L1) → Aggregate 처리 → Event Store
                       ↑
               (Redis 정상 시 라이터 큐잉;
                Redis 불가 시 DB 비관 락 폴백)

[ API Path ]
  Request → 레이트리밋 카운터(Redis) → 멱등 디듀프(Redis) → Handler

[ Read Path ]
  Query → Projection Read Model(DB) ← Projector ← Event Store
  (Redis 캐시 층 없음 — 기본)
```

인증 부산물(refresh 저장·폐기 목록)은 [[DESIGN-017]]에 의해 Redis 밖으로 이동 완료. Redis에 남는 것은 손실 허용 조정 상태(등급 1) 뿐.

### 4.2 Key Design Decisions

**결정 1: read model 앞에 Redis 캐시 층을 기본으로 두지 않는다.**

프로젝션은 이벤트를 구독해 화면·조회 모양으로 비정규화해 디스크에 영속한 뷰다([[DESIGN-004]] "(가) 이벤트 프로젝션 read model"). 그 앞에 Redis를 한 겹 더 두면 *캐시 위의 캐시* — 같은 일을 두 번 한다.

핫 쿼리에 대한 1차 대응의 **우선순위**를 이렇게 고정한다:

1. **그 화면 전용 프로젝션 추가** — read model은 용도마다 여럿 둘 수 있다([[DESIGN-004]] 도메인별 스키마 분리). "오늘 X식당 예약 목록"이 뜨거우면 그 모양 전용 프로젝션을 만든다. 이미 가진 메커니즘(프로젝터)의 재사용이지, 새 인프라가 아니다.
2. **읽기 확장은 query 인스턴스 HA 레플리카로** 분산([[RFC-007-deployment-infra-ops]]) — 캐시 한 겹이 아니다.
3. **Redis 캐시 한 겹은 최후** — 프로젝션 재설계로도 싸게 못 푸는 패턴이 *측정으로* 드러난 경우에 한해, 그 패턴 하나에만. 전역 캐시 정책을 먼저 세우지 않는다.

**근거(2차 staleness 회피)**: read model은 이벤트를 비동기로 받아 갱신되므로 쓰기↔읽기 사이에 이미 프로젝션 지연이라는 staleness 창이 하나 있다([[RFC-002-read-model-consistency]]). 그 위에 TTL 캐시를 얹으면 staleness가 *두 겹*(이벤트→프로젝션 + 프로젝션→캐시 TTL)이 되고, "프로젝션이 늦은 건지 캐시가 안 비워진 건지" 디버깅이 흐려진다. 이 2차 staleness는 기본적으로 사들이지 않는다.

만약 우선순위 3의 예외 캐시를 정말 둔다면, 무효화는 **그 패턴 하나에 한해** 정한다 — 이벤트 기반 비우기(프로젝터 갱신에 캐시 무효화를 묶음)면 결국 "프로젝션 한 벌 더"와 비용이 비슷해지고, TTL 기반이면 "옛 데이터를 TTL만큼 보여도 되는 화면"에서만 정당하다. 어느 쪽이든 화면별 허용 staleness가 전제다. (식별 트리거·손익분기 임계는 §8 "후속으로 넘기는 것".)

**결정 2: V2에서 Redis는 "분산 조정·휘발성 상태" 전용이다.**

세 후보의 자리와 근거:

| 자리 | 출처 | 왜 Redis인가 |
|---|---|---|
| **① 요청-단 멱등 디듀프** | [[DESIGN-013]] 잔여 케이스 (자연 유니크 불변식 없는 생성 command) | 짧은 윈도·TTL 자동 청소·다중 인스턴스 공유 |
| **② 레이트리밋 카운터** | V1 `AcquireRateLimitRedisAdapter` 계승 | 다중 인스턴스가 공유하는 분산 카운터 — 인스턴스 로컬 불가 |
| **③ 분산 락 (Redisson)** | V1 Redisson 락 계승 + [[RFC-014-aggregate-concurrency-control]] L1 | 애그리거트 쓰기 경합 직렬화(1차) + 인프라 레벨 상호배제 |

**결정 3: 단일 durability 등급.**

[[DESIGN-017]]이 인증 부산물(refresh 저장·폐기)을 Redis 밖으로 들어냈으므로, Redis에 남는 건 손실 허용 조정 상태(등급 1)뿐. 따라서 `maxmemory-policy` = `allkeys-lru`(또는 `volatile-lru`) 단일, 인스턴스 하나면 충분.

### 4.3 Interface / Contract

**경계 둘을 못 박는다:**

- **애그리거트 쓰기 경합은 Redisson 분산 락이 1차로 직렬화한다([[RFC-014-aggregate-concurrency-control]] L1).** RFC-018 원안은 도메인 동시성을 낙관적 락으로 흡수한다고 봤으나, [[RFC-014-aggregate-concurrency-control]](합의)가 이를 **비관 락 3층**으로 뒤집었다 — L0 `(aggregate_id, sequence_no)` UNIQUE 불변식([[05.event-store-mysql-table]]) + L1 단일 `aggregate_id` **Redisson 분산 락**(Redis 정상 시 라이터 큐잉) + L1′ DB 비관 락(`SELECT … FOR UPDATE`) 폴백(Redis 불가 시; *낙관으로 회귀하지 않는다*). 즉 분산 락 ③은 V1보다 *줄어든* 사용 면적이 아니라 — 인프라 레벨 상호배제에 더해 **애그리거트 경합 직렬화의 1차 메커니즘**으로 쓰인다([[DESIGN-003]]·[[DESIGN-006]]·ADR `16.optimistic-concurrency-control`(비관 락으로 재작성)). 따라서 **V2는 Redisson 분산 락 인프라를 *반드시 provision*해야 한다** — 이는 선택적 가속이 아니라 쓰기 경로 정확성이 의존하는 컴포넌트다(다운 시 L1′ DB 폴백으로 강등될 뿐 경합 직렬화 자체는 포기하지 않는다).

- **사가 임시 점유 TTL은 Redis가 아니다.** 점유 만료는 스케줄러 DB 폴링으로 결정됐다([[RFC-006-saga-process-manager]]·[[DESIGN-007]]). V1 `timetable` Redisson 세마포어를 V2 점유로 끌어오지 말 것.

(V1 잔재 — 피처 플래그·재시도 컨텍스트·Redisson 락 사용 면적 — 의 V2 항목별 귀속은 각 항목의 V2 거취가 정해질 때. 본 문서는 "캐시냐 조정 상태냐"의 분류 원칙만 확정.)

### 4.4 Data Model

**eviction 정책:**

- `maxmemory-policy` = `allkeys-lru`(또는 `volatile-lru`) 단일.
- 논리 DB(`SELECT 0~15`)·키 프리픽스는 네임스페이스만 가를 뿐 메모리·단일 스레드 코어를 공유해 eviction·blast-radius를 격리하지 못한다 — 진짜 격리가 필요하면 별도 인스턴스다(지금은 불필요).

**키 구성 (초안 — 후속 확정):**

| 자리 | 키 패턴 | TTL |
|---|---|---|
| 멱등 디듀프 | `idem:{command-type}:{idempotency-key}` | 요청 윈도 길이(미확정, §8) |
| 레이트리밋 카운터 | `rl:{resource}:{subject}:{window}` | 윈도 길이 |
| Redisson 분산 락 | Redisson 관리 (내부 키) | 락 TTL (Redisson 기본 + watchdog) |

> ⚠ 멱등 디듀프 키가 `allkeys-lru`로 *윈도 내 eviction*되면 막으려던 중복을 도로 연다 — 키 구성/윈도와 eviction 정책의 상호작용을 후속 사이클에서 확정해야 한다(§8).

## 5. Alternatives Considered

### 5.1 read model 앞에 Redis 캐시 층 기본 도입 (비채택)

프로젝션 앞에 Redis TTL 캐시를 전역으로 두는 방안. 2차 staleness(이벤트→프로젝션 + 프로젝션→캐시 TTL) 두 겹이 생겨 디버깅이 흐려진다. 프로젝션 자체가 이미 비정규화된 영속 캐시라 "캐시 위의 캐시"가 된다. 비채택.

### 5.2 기능별 Redis 인스턴스 분리 (비채택 — 조건부 유보)

인증 부산물(must-not-evict)과 조정 상태(evict 가능)가 한 인스턴스에 섞이면 `maxmemory-policy` 하나로 양립이 안 돼 인스턴스 분리가 정당해진다. 그러나 V2는 발산을 *쪼개서 관리*하지 않고 *발산하는 워크로드를 제거*해 닫는다. [[DESIGN-017]]이 인증 부산물을 Redis 밖으로 들어냈으므로 등급 1만 남아 분리 불필요. 되살아남 조건: denylist가 부활하면 그때 등급별 분리 정책·인스턴스 분리를 [[RFC-007-deployment-infra-ops]]와 재검토한다(§6.5 참조).

### 5.3 사가 임시 점유를 Redisson 세마포어로 (비채택)

V1 `timetable` Redisson 세마포어를 V2 점유로 이어오는 방안. 점유 만료가 Redis 가용성에 결합되고, [[RFC-006-saga-process-manager]]·[[DESIGN-007]]에서 스케줄러 DB 폴링으로 이미 확정됐다. 비채택.

## 6. Details

### 6.1 Error Handling

- **Redis 장애 시 분산 락 폴백**: Redisson 분산 락(L1) 불가 시 DB 비관 락(`SELECT … FOR UPDATE`)으로 자동 강등(L1′). 쓰기 경합 직렬화 자체는 포기하지 않는다. 낙관적 락으로 회귀하지 않는다([[RFC-014-aggregate-concurrency-control]]).
- **Redis 장애 시 레이트리밋 = fail-open (통과 허용)**([[RFC-028-redis-fault-fallback-semantics]]). 레이트리밋은 정확성 불변식이 아니라 부하 보호 heuristic이므로, Redis 불가 시 요청을 거부하면 내부 fault를 사용자 장애로 전이시킨다. 통과시켜 가용성을 지키고, 심층방어로 애플리케이션 레벨 써킷 브레이커를 별도 검토.
- **Redis 장애 시 멱등 디듀프 = cache-through, fail-to-DB**([[RFC-028-redis-fault-fallback-semantics]]). 디듀프는 정확성 불변식이라 fail-open을 그대로 얹을 수 없다. DB를 SoR로 두고 Redis를 그 앞의 coherent 캐시로 쓴다(write-through로 DB UNIQUE에 착지, read-through로 조회). Redis 불가 시 **통과가 아니라 DB로 강등** — 정확성에 창(window)이 없다. eviction도 무해해진다(read-through miss가 DB에서 재적재 → §4.4 line 119 구멍이 닫힌다). **동시 중복 직렬화의 가드는 캐시가 아니라 DB UNIQUE**다(event_store `(aggregate_id, sequence_no)` UNIQUE + 도메인 자연 유니크 [[DESIGN-013]]).
  - *구현 방향 메모(구현 사이클)*: 레이트리밋은 GCRA 권장 — 직접 구현 시 TAT 갱신은 반드시 서버측 원자(Lua), 또는 Redisson GCRA 사용(③ 락이 이미 Redisson을 provision하므로 스택에 남는다). 디듀프 idem 레코드는 DB에도 남으므로 DB쪽 보존/GC 정책 필요(§8).

### 6.2 Security Considerations

- Redis에 인증 부산물(refresh 토큰·폐기 목록)을 두지 않으므로, Redis 침해가 곧 세션 탈취로 직결되는 경로가 제거된다.
- 분산 락·레이트리밋·멱등 디듀프 데이터는 TTL로 자동 소멸하는 단기 상태이므로 eviction 또는 재시작으로 인한 데이터 소실이 보안 구멍을 만들지 않는다.
- 멱등 디듀프 키 윈도 내 eviction은 중복 허용으로 이어질 수 있다(§4.4 경고 참조). 윈도 길이·메모리 한도 설정 시 고려 필요.

### 6.3 Performance & Scalability

- read model 앞 캐시 층 없이도 화면 전용 프로젝션 추가·query HA 레플리카 확장으로 대부분의 핫 쿼리를 흡수한다.
- Redis 단일 스레드 특성상 고처리량 레이트리밋 카운터는 파이프라이닝·Lua 스크립트 원자 연산으로 처리한다.
- 인스턴스 하나면 충분하나, 레이트리밋·디듀프의 처리량이 증가하면 읽기 전용 레플리카 없이 수직 확장이 먼저다. 수평 분산(클러스터)은 [[RFC-007-deployment-infra-ops]] 배포 사이클에서 결정.

### 6.4 Observability

- **분산 락**: 락 획득 대기 시간·획득 실패율·폴백(L1→L1′) 전환 횟수를 메트릭으로 노출.
- **레이트리밋**: 차단 횟수·윈도별 카운터 현황.
- **멱등 디듀프**: 디듀프 히트(중복 차단) 횟수·miss(신규 요청) 비율.
- **Redis 가용성**: 연결 상태·메모리 사용률·eviction 횟수 — [[DESIGN-011-observability]] 연동.

### 6.5 Migration / Rollback

**V1 → V2 전환:**

- V1 `RedisCacheManager`(read 캐시), refresh 토큰 저장 제거. [[DESIGN-004]] 프로젝션이 준비되면 read 캐시 층은 자연 폐기.
- V1 Redisson 분산 락은 V2에서도 유지되나, 역할이 "인프라 가속"에서 "쓰기 경로 정확성 의존 컴포넌트"로 승격.
- V1 피처 플래그·재시도 컨텍스트는 V2 항목별 귀속 확정 시까지 임시 유지.

**되살아남 조건(단일 durability 가정 파괴):**

[[DESIGN-017]]에서 즉시 폐기 요구가 입증돼 denylist가 부활하면, must-not-evict 등급이 되살아나 단일 durability·단일 인스턴스 가정이 깨진다. 그때 등급별 분리 정책·인스턴스 분리를 [[RFC-007-deployment-infra-ops]]와 재검토한다.

## 7. Risks & Mitigations

| 위험 | 완화 |
|------|------|
| Redis 장애 시 분산 락 미작동 → 쓰기 경합 직렬화 저하 | DB 비관 락(L1′) 자동 폴백; 낙관 락 회귀 금지([[RFC-014-aggregate-concurrency-control]]) |
| 멱등 디듀프 키 윈도 내 eviction → 중복 처리 허용 | **cache-through로 해소**([[RFC-028-redis-fault-fallback-semantics]]) — read-through miss가 DB에서 재적재, DB UNIQUE가 가드. eviction 무해 |
| Redis 장애 시 레이트리밋 미작동 → 과부하 허용 | **fail-open 확정**([[RFC-028-redis-fault-fallback-semantics]]) — 통과 허용(내부 fault 외부 전이 방지); 써킷 브레이커 별도 검토 |
| HA(Sentinel/Cluster) 과설계로 운영 복잡도 증가 | role 설계에서 불채택([[RFC-028-redis-fault-fallback-semantics]]) — 폴백 시맨틱으로 Redis가 손실 허용이 되어 단일 인스턴스 정당; HA는 배포 사이클([[RFC-007-deployment-infra-ops]]) 옵션 |
| denylist 부활 시 단일 durability 가정 파괴 | 부활 조건 명시(§6.5); [[RFC-007-deployment-infra-ops]]와 재검토 경로 확보 |
| 2차 staleness(프로젝션 + 캐시 TTL) 두 겹 도입 | read model 앞 캐시 층 기본 미도입; 예외 시 화면별 허용 staleness 전제 |
| V1 Redisson 세마포어(점유)를 V2에 끌어오는 실수 | 사가 점유 만료는 DB 폴링으로 명시 확정([[DESIGN-007]]); 이 문서로 경계 못 박음 |

## 8. Milestones & Phases

| 단계 | 내용 | 의존 |
|------|------|------|
| Phase 1 (현재) | Redis 역할 원칙 확정(본 문서). read 캐시 미도입 결정. 단일 durability 확정. | [[DESIGN-017]] 완료 |
| Phase 2 | V1 RedisCacheManager·refresh 저장 제거. 프로젝션 read model로 전환. | [[DESIGN-004]] 프로젝션 구현 |
| Phase 3 | 멱등 디듀프 cache-through 구현(DB SoR·UNIQUE + Redis 캐시). Redis 키 구성·윈도 길이 + **DB idem 레코드 보존/GC 정책** 확정. 레이트리밋 GCRA(Redisson or Lua) 구현. | [[DESIGN-013]] 잔여 케이스 확정 · [[RFC-028-redis-fault-fallback-semantics]] |
| Phase 4 | V1 피처 플래그·재시도 컨텍스트 V2 귀속 확정. | 각 항목 V2 거취 결정 시 |
| Phase 5 (조건부) | denylist 부활 시 등급별 인스턴스 분리 재설계. | 즉시 폐기 요구 입증 시 |

## 9. Appendix

### 9.1 Glossary

| 용어 | 설명 |
|------|------|
| 프로젝션 (Projection) | 이벤트를 구독해 화면·조회 모양으로 비정규화해 디스크에 영속한 뷰. read model의 구현 메커니즘 |
| 조정 상태 (Coordination State) | 여러 인스턴스가 공유해야 하는 휘발성·단기 TTL 상태. 손실 허용(evict 가능) |
| must-not-evict | Redis eviction 정책 중 메모리 압박 시 절대 퇴거하지 않아야 하는 등급 (예: 폐기 토큰 목록) |
| allkeys-lru | Redis maxmemory-policy 옵션. 모든 키를 LRU(Least Recently Used) 순으로 evict |
| 멱등 디듀프 (Idempotent Dedup) | 요청 단위 중복 처리 방지. 같은 idempotency key의 요청이 짧은 윈도 내 재전송될 때 차단 |
| Redisson | Java/Kotlin용 Redis 클라이언트. 분산 락·세마포어·레이트리미터 등 고수준 분산 자료구조 제공 |
| L1 / L1′ | 애그리거트 쓰기 경합 직렬화 계층. L1 = Redisson 분산 락(Redis 정상 시), L1′ = DB 비관 락(Redis 불가 시 폴백) |
| 2차 staleness | 프로젝션 지연(1차) 위에 캐시 TTL 지연(2차)이 쌓이는 현상. 디버깅 복잡도 증가 |
| denylist | 폐기된 토큰 식별자를 잔여 수명만큼 들고 있는 목록. must-not-evict 워크로드 |

### 9.2 Calculations / Benchmarks

**단일 인스턴스 충분성 근거:**

- V2 Redis 워크로드: 레이트리밋 카운터(초당 수백~수천 ops), 분산 락 획득/해제(쓰기 TPS와 동일 오더), 멱등 디듀프(생성 command TPS). 세 워크로드 모두 단기 TTL·경량 연산.
- Redis 단일 인스턴스는 초당 10만+ ops 처리 가능(공식 벤치마크). 현 규모에서 수직 확장 여유 충분.
- must-not-evict 워크로드 제거로 메모리 압박 시 LRU eviction이 안전해짐 → 단일 `allkeys-lru` 정책 일관 적용 가능.

**멱등 디듀프 윈도 eviction 위험 추산 (미확정, 후속 확정):**

- 키 하나의 크기: ~수십 바이트. 윈도 100초, TPS 1,000 기준 최대 키 수 ~10만 개. 메모리 ~수 MB.
- Redis 메모리 한도를 충분히 잡으면 윈도 내 eviction 확률은 무시 가능하나, 한도 미설정 시 위험. 후속 확정 필요.

### 9.3 Reference

- [[RFC-018-caching-redis-role]]
- [[RFC-014-aggregate-concurrency-control]]
- [[RFC-002-read-model-consistency]]
- [[RFC-015-authorization-model]]
- [[RFC-019-auth-token-transport]]
- [[RFC-007-deployment-infra-ops]]
- [[RFC-006-saga-process-manager]]
- [[DESIGN-004]] (read model)
- [[DESIGN-017]] (auth token)
- [[DESIGN-013]] (api contract)
- [[DESIGN-003]] (write model)
- [[DESIGN-007]] (consistency and sagas)
- [[DESIGN-006]] (aggregate design)
- [[DESIGN-010]] (deployment runtime)
- ADR `19.caching-redis-role`
- ADR `16.optimistic-concurrency-control` (비관 락으로 재작성)

## Changelog

| 날짜 | 변경 내용 |
|------|-----------|
| 2026-07-05 | §6.1·§7·§8 갱신 — Redis 장애 폴백 시맨틱 확정([[RFC-028-redis-fault-fallback-semantics]], 트리아지 C40): 레이트리밋=fail-open, 디듀프=cache-through(DB SoR·UNIQUE 보장·fail-to-DB·eviction 무해), HA=role 불채택(배포 사이클 이월). §4.4 line 119 eviction 구멍이 cache-through로 닫힘. 구현 메모(GCRA·DB idem GC) 추가. |
| 2026-06-30 | DESIGN-018로 재포맷. 템플릿 구조(Background/Goal/Non-Goal/Proposed Solution/Alternatives/Details/Risks/Milestones/Appendix) 적용. 상호 참조 번호 갱신 (03-read-model → DESIGN-004, 16-auth-token → DESIGN-017, 12-api-contract → DESIGN-013, 02-write-model → DESIGN-003, 06-consistency-and-sagas → DESIGN-007, 05-aggregate-design → DESIGN-006, 09-deployment-runtime → DESIGN-010). 원본 17-caching.md 전체 내용 보존. |

---

## Weakness (Devil's Advocate 반박 포인트)

- **단일 인스턴스 = SPOF인데 락은 "쓰기 경로 정확성 의존"으로 승격됨** — §4.3은 Redisson 분산 락을 "선택적 가속이 아니라 쓰기 경로 정확성이 의존하는 컴포넌트"로 못 박고, §4.4·§9.2는 동시에 "인스턴스 하나면 충분"이라 한다. 두 결정이 충돌한다: 정확성이 의존하는 컴포넌트를 SPOF로 두면, 그 인스턴스가 죽는 순간 L1′ DB 폴백으로 넘어가는 *전환 자체*가 경합이다. 페일오버 없는 단일 Redis에서 락 상태가 유실되면 L1과 L1′가 동시에 다른 판단을 내리는 split-brain 창이 열리고, 이 전환 중 정확성을 무엇이 보장하는지가 공백이다. 호스팅을 §3 Non-Goal로 밀어냈지만 "정확성 의존"으로 승격한 이상 토폴로지는 더 이상 배포 사이클의 몫으로만 미룰 수 없다.

- **Redisson 분산 락 자체가 안전한 상호배제라는 가정(Redlock 논쟁)** — §4.3은 L1 분산 락을 애그리거트 경합 직렬화의 *1차*로 삼는다. 그러나 Redis 기반 락은 GC 일시정지·네트워크 지연·watchdog 갱신 실패로 인해 "락을 쥐었다고 믿는데 실제로는 TTL 만료로 뺏긴" 상황(the fencing token 문제)이 잘 알려져 있다. 문서는 락 획득을 정확성의 근거로 삼으면서 fencing token·단조 증가 경계 같은 상호배제 안전장치를 언급하지 않는다 — L0의 `(aggregate_id, sequence_no)` UNIQUE가 사실상 최종 방어라면, L1 락은 정확성 의존이 아니라 경합 완화(가속)로 재분류하는 게 정직하다.

- **read model 앞 캐시 거부가 P99 지연·프로젝션 폭증 비용을 감춤** — 결정 1은 핫 쿼리 대응을 "화면 전용 프로젝션 추가"로 돌린다. 그러나 프로젝션 하나 추가는 새 이벤트 구독·프로젝터·스키마·백필·재구축 파이프라인을 수반하는 무거운 자산이고, "오늘 X식당 예약 목록"처럼 파라미터가 많은 핫 쿼리는 프로젝션으로 미리 만들 조합이 폭발한다(모든 식당×모든 날짜). 마이크로초 단위 in-memory 캐시로 풀 문제를 "프로젝션 한 벌 더"로 돌리는 것은 저지연·고팬아웃 읽기에서 오히려 더 비싸고 느리다 — 2차 staleness 회피라는 이득이 이 비용을 정당화하는지 측정이 없다.

- **레이트리밋·디듀프 장애 폴백이 전부 미결인데 단일 인스턴스로 확정** — §6.1은 Redis 장애 시 레이트리밋(통과 vs 차단)과 멱등 디듀프(중복 허용) 폴백을 모두 "후속 확정"으로 미뤘다. 그런데 §4.4·§9.2는 이미 "인스턴스 하나로 충분"을 확정했다. 폴백 정책이 미정인 상태에서 단일 인스턴스를 확정하면, Redis가 잠깐 흔들리는 순간 레이트리밋 붕괴(과부하 통과)나 디듀프 붕괴(결제·예약 중복 생성) 중 무엇이 일어날지 아무도 모른 채 운영에 들어간다. 가용성 결정을 미루면서 인스턴스 수 결정을 확정한 것은 순서가 뒤집혔다.

- **멱등 디듀프 키를 `allkeys-lru`에 태우는 것은 정책 자기모순** — §4.4 경고·§6.2가 스스로 인정하듯, 디듀프 키가 윈도 내 evict되면 막으려던 중복이 다시 열린다. 즉 디듀프 키는 *윈도 동안 evict되면 안 되는* 준-must-not-evict 성격인데, 결정 3은 "손실 허용 조정 상태뿐이라 `allkeys-lru` 단일로 충분"이라고 단언한다. 디듀프의 존재 자체가 "단일 durability 등급" 전제에 대한 반례다 — 최소한 `volatile-lru` + 명시 TTL로 분리하지 않으면, 결제/예약 중복이 메모리 압박이라는 *비기능적 사건*에 좌우된다.

- **"인증 부산물 제거"가 durability 단순화의 유일 근거라 되돌림 비용이 크다** — 결정 3·§5.2·§6.5는 단일 durability를 전적으로 "[[DESIGN-017]]이 refresh를 들어냈으니 must-not-evict가 사라졌다"에 건다. 그런데 DESIGN-017의 즉시 폐기 포기는 스스로 "요구 입증 시 denylist 부활"이라는 미결 조건을 달고 있다. 즉 이 문서의 핵심 단순화(단일 등급·단일 인스턴스)는 아직 확정되지 않은 상위 결정에 얹혀 있고, denylist가 부활하는 순간 §6.5대로 durability 가정·인스턴스 분리·eviction 정책을 통째로 재설계해야 한다. 단순화의 근거가 취약한 상위 가정 하나에 전부 걸려 있다.

> 본 절은 리뷰용 반박 정리이며, 문서의 결정을 뒤집지 않는다. 각 항목은 후속 검토 대상.
