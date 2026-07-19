# V2 Design Doc — 17. Caching (캐싱·Redis의 V2 역할)

- **개요**: [[00-design-overview]] · **근거**: [[RFC-018-caching-redis-role]]
- **관련 결정**: [[03-read-model]] · [[16-auth-token]] · [[12-api-contract]] · [[RFC-015-authorization-model]] · [[RFC-019-auth-token-transport]] · [[RFC-002-read-model-consistency]]

> 이 문서는 V2에서 **Redis가 어떤 자리에 사는가**(아키텍처 역할)를 확정한다. "읽기를 어떻게 빠르게 하나"는 프로젝션의 몫([[03-read-model]])이고, refresh·폐기 같은 인증 부산물의 거취는 [[16-auth-token]]에서 이미 닫혔다. 여긴 *그 둘을 뺀 나머지 Redis*가 무엇을 들고, durability 등급이 왜 하나로 줄며, 호스팅·토폴로지를 왜 여기서 정하지 않는가를 다룬다. (Redis의 *호스팅* — 관리형 vs 자가, 단일/센티넬/클러스터·인스턴스 개수 — 는 배포 사이클 [[09-deployment-runtime]]·[[RFC-007-deployment-infra-ops]]의 몫. 여긴 역할만.)

## 0. 출발점: V1 Redis는 "캐시"가 아니라 사이드 상태 저장소였다

V1에서 Redis(+Redisson)는 한 역할이 아니었다 — Redisson 분산 락·세마포어(`timetable` 자리 점유), 레이트리미터(`AcquireRateLimitRedisAdapter`), 리프레시 토큰 저장(`SaveGeneralUserRefreshToken`), 피처 플래그(`FeatureFlagRedisTemplate`), 재시도 컨텍스트(`RedisRetryContextCache`), `RedisCacheManager`. 즉 *동기 모놀리식이 못 들던 휘발성·분산 상태를 한 군데 모은 사이드 저장소*였다. ("Redis 세션"은 V1에 실제로는 없다 — `HttpSession` 미사용, 인증 상태로 남는 건 refresh 토큰뿐.)

V2가 이 그림을 둘에서 흔든다.
- **읽기가 프로젝션 조회로 갈렸다**([[03-read-model]]) — 프로젝션 read model 자체가 이미 질의 모양으로 비정규화된 머티리얼라이즈드 뷰, 곧 *영속 캐시*다.
- **인증이 무상태 JWT로 정리됐다**([[RFC-015-authorization-model]]·[[16-auth-token]]) — refresh 서버 사본이 제거됐다.

이 둘을 빼고 나면 Redis에 남는 자리가 좁아진다. 아래는 그 좁혀진 역할의 *설계 확정*이다.

## 1. 프로젝션이 곧 읽기 캐시다 — 그 위에 캐시 층을 얹지 않는다

**결정: read model 앞에 Redis 캐시 층을 기본으로 두지 않는다.**

프로젝션은 이벤트를 구독해 화면·조회 모양으로 비정규화해 디스크에 영속한 뷰다([[03-read-model]] "(가) 이벤트 프로젝션 read model"). 그 앞에 Redis를 한 겹 더 두면 *캐시 위의 캐시* — 같은 일을 두 번 한다.

핫 쿼리에 대한 1차 대응의 **우선순위**를 이렇게 고정한다:

1. **그 화면 전용 프로젝션 추가** — read model은 용도마다 여럿 둘 수 있다([[03-read-model]] 도메인별 스키마 분리). "오늘 X식당 예약 목록"이 뜨거우면 그 모양 전용 프로젝션을 만든다. 이미 가진 메커니즘(프로젝터)의 재사용이지, 새 인프라가 아니다.
2. **읽기 확장은 query 인스턴스 HA 레플리카로** 분산([[RFC-007-deployment-infra-ops]]) — 캐시 한 겹이 아니다.
3. **Redis 캐시 한 겹은 최후** — 프로젝션 재설계로도 싸게 못 푸는 패턴이 *측정으로* 드러난 경우에 한해, 그 패턴 하나에만. 전역 캐시 정책을 먼저 세우지 않는다.

**근거(2차 staleness 회피)**: read model은 이벤트를 비동기로 받아 갱신되므로 쓰기↔읽기 사이에 이미 프로젝션 지연이라는 staleness 창이 하나 있다([[RFC-002-read-model-consistency]]). 그 위에 TTL 캐시를 얹으면 staleness가 *두 겹*(이벤트→프로젝션 + 프로젝션→캐시 TTL)이 되고, "프로젝션이 늦은 건지 캐시가 안 비워진 건지" 디버깅이 흐려진다. 이 2차 staleness는 기본적으로 사들이지 않는다.

만약 §1-3의 예외 캐시를 정말 둔다면, 무효화는 **그 패턴 하나에 한해** 정한다 — 이벤트 기반 비우기(프로젝터 갱신에 캐시 무효화를 묶음)면 결국 "프로젝션 한 벌 더"와 비용이 비슷해지고, TTL 기반이면 "옛 데이터를 TTL만큼 보여도 되는 화면"에서만 정당하다. 어느 쪽이든 화면별 허용 staleness가 전제다. (식별 트리거·손익분기 임계는 §아래 "넘기는 것".)

## 2. Redis가 정당하게 사는 자리 — 분산 조정·휘발성 상태 전용

**결정: V2에서 Redis는 "읽기 가속 캐시"가 아니라 "여러 인스턴스가 공유해야 하는 휘발성·짧은 TTL 조정 상태" 전용이다.** 그 자리에 드는 후보는 셋:

| 자리 | 출처 | 왜 Redis인가 |
|---|---|---|
| **① 요청-단 멱등 디듀프** | [[12-api-contract]] 잔여 케이스 (자연 유니크 불변식 없는 생성 command) | 짧은 윈도·TTL 자동 청소·다중 인스턴스 공유 |
| **② 레이트리밋 카운터** | V1 `AcquireRateLimitRedisAdapter` 계승 | 다중 인스턴스가 공유하는 분산 카운터 — 인스턴스 로컬 불가 |
| **③ 분산 락 (Redisson)** | V1 Redisson 락 계승 + [[RFC-014-aggregate-concurrency-control]] L1 | 애그리거트 쓰기 경합 직렬화(1차) + 인프라 레벨 상호배제 |

세 후보의 공통점: "읽기를 빠르게 하려는 캐시"가 아니라 "분산 환경에서 인스턴스 간에 공유해야 하는, 본질적으로 휘발성이고 TTL이 자연스러운 조정 상태"다.

**경계 둘을 못 박는다:**
- **애그리거트 쓰기 경합은 Redisson 분산 락이 1차로 직렬화한다([[RFC-014-aggregate-concurrency-control]] L1).** RFC-018 원안은 도메인 동시성을 낙관적 락으로 흡수한다고 봤으나, [[RFC-014-aggregate-concurrency-control]](합의)가 이를 **비관 락 3층**으로 뒤집었다 — L0 `(aggregate_id, sequence_no)` UNIQUE 불변식([[05.event-store-mysql-table]]) + L1 단일 `aggregate_id` **Redisson 분산 락**(Redis 정상 시 라이터 큐잉) + L1′ DB 비관 락(`SELECT … FOR UPDATE`) 폴백(Redis 불가 시; *낙관으로 회귀하지 않는다*). 즉 분산 락 ③은 V1보다 *줄어든* 사용 면적이 아니라 — 인프라 레벨 상호배제에 더해 **애그리거트 경합 직렬화의 1차 메커니즘**으로 쓰인다([[02-write-model]]·[[05-aggregate-design]]·ADR `16.optimistic-concurrency-control`(비관 락으로 재작성)). 따라서 **V2는 Redisson 분산 락 인프라를 *반드시 provision*해야 한다** — 이는 선택적 가속이 아니라 쓰기 경로 정확성이 의존하는 컴포넌트다(다운 시 L1′ DB 폴백으로 강등될 뿐 경합 직렬화 자체는 포기하지 않는다).
- **사가 임시 점유 TTL은 Redis가 아니다.** 점유 만료는 스케줄러 DB 폴링으로 결정됐다([[RFC-006-saga-process-manager]]·[[06-consistency-and-sagas]]). V1 `timetable` Redisson 세마포어를 V2 점유로 끌어오지 말 것.

(V1 잔재 — 피처 플래그·재시도 컨텍스트·Redisson 락 사용 면적 — 의 V2 항목별 귀속은 각 항목의 V2 거취가 정해질 때. 본 문서는 "캐시냐 조정 상태냐"의 분류 원칙만 확정.)

## 3. 단일 durability 등급 — 인스턴스를 쪼갤 이유가 사라진다

RFC-019는 좁혀진 Redis 안에도 **메모리 압박 시 동작이 정반대인 두 등급**이 섞일 위험을 짚었다:

- **등급 1 — 조정 상태(손실 허용, evict 가능):** 레이트리밋 카운터·분산 락·멱등 디듀프. 날아가도 최악이 "몇 건이 한 번 더 새거나 윈도 리셋". `allkeys-lru` 정당.
- **등급 2 — 인증 부산물(must-not-evict):** refresh·폐기 목록. evict되면 *폐기된 토큰이 되살아나는* 보안 구멍. `noeviction` 필요.

한 인스턴스의 `maxmemory-policy`는 하나뿐이라 두 등급은 한 정책으로 양립 못 한다 — 이게 "기능별로 Redis를 쪼개자"가 정당해지는 지점이었다.

**그러나 V2는 발산을 *쪼개서 관리*하지 않고 *발산하는 워크로드를 제거*해 닫는다.** [[16-auth-token]]이 인증 부산물(refresh 저장·폐기)을 Redis 밖으로 들어냈으므로(무상태 서명 JWT + 폐기 포기), **Redis에 남는 건 등급 1뿐 — 단일 durability 등급**이다. 따라서:

- **`maxmemory-policy` = `allkeys-lru`(또는 `volatile-lru`) 단일.**
- **인스턴스 = 하나면 충분.** 기능별 분리 불필요.
- 논리 DB(`SELECT 0~15`)·키 프리픽스는 네임스페이스만 가를 뿐 메모리·단일 스레드 코어를 공유해 eviction·blast-radius를 격리하지 못한다 — 진짜 격리가 필요하면 별도 인스턴스다(지금은 불필요).

**되살아남 조건:** [[16-auth-token]]에서 즉시 폐기 요구가 입증돼 denylist가 부활하면, must-not-evict 등급이 되살아나 단일 durability·단일 인스턴스 가정이 깨진다. 그때 등급별 분리 정책·인스턴스 분리를 [[RFC-007-deployment-infra-ops]]와 재검토한다.

## 4. 설계 한 줄 요약

> 프로젝션이 이미 읽기 캐시다 — 그 위에 또 얹지 않는다. V2 Redis는 읽기 캐시가 아니라 *분산 조정·휘발성 상태*(멱등 디듀프·레이트리밋·인프라 락) 전용이고, 인증 부산물이 [[16-auth-token]]으로 빠져 **단일 durability·단일 인스턴스**로 단순해진다.

## Design / 후속으로 넘기는 것

- 캐시를 정말 둘 "프로젝션이 싸게 못 푸는 패턴"의 식별 — 읽기 분포 측정 트리거와 "프로젝션 재설계 vs Redis 캐시 한 겹"의 손익분기 임계([[RFC-002-read-model-consistency]] 측정 정책과 연동).
- 캐시를 둘 경우 그 한 패턴에 한한 무효화 전략(이벤트 기반 vs TTL)·허용 staleness — 화면별.
- 요청-단 멱등 디듀프의 Redis 키 구성·윈도 길이 — [[12-api-contract]] 잔여 케이스(자연 유니크 없는 생성 command) 확정과 함께. ⚠ 디듀프 키가 `allkeys-lru`로 *윈도 내 eviction*되면 막으려던 중복을 도로 연다 — 키 구성/윈도와 eviction 정책의 상호작용을 여기서 확정.
- V1 Redis 잔재(피처 플래그·재시도 컨텍스트·Redisson 락 사용 면적)의 V2 항목별 귀속표.
- Redis 호스팅(관리형 vs 자가)·토폴로지(단일/센티넬/클러스터) — 배포 사이클 [[09-deployment-runtime]]·[[RFC-007-deployment-infra-ops]].

## 관련 문서

- 근거: [[RFC-018-caching-redis-role]]
- [[03-read-model]] · [[16-auth-token]] · [[12-api-contract]] · [[02-write-model]] · [[06-consistency-and-sagas]] · [[RFC-015-authorization-model]] · [[RFC-019-auth-token-transport]] · [[RFC-002-read-model-consistency]] · [[RFC-007-deployment-infra-ops]] · [[09-deployment-runtime]]
