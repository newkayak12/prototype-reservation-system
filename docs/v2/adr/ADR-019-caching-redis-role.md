# ADR-019: 캐싱·Redis의 V2 역할 — 읽기 캐시 아님, 분산 조정·휘발성 상태 전용(단일 durability)

- **상태**: Accepted (2026-08-03)
- **사이클**: `20260612-v2-cqrs-es-architecture`
- **상위 RFC**: [[RFC-018-caching-redis-role]] · **설계**: [[DESIGN-018-caching]]
- **연관 ADR**: [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-004-read-model-projection-and-replica]] · [[ADR-020-auth-token-transport]] · [[ADR-013-db-hosting-and-read-write-topology]]

---

## 맥락과 문제 (Context and Problem Statement)

V1의 Redis(+Redisson)는 한 가지 역할이 아니었다. 분산 락·세마포어(`timetable` 자리 점유), 레이트리미터(`AcquireRateLimitRedisAdapter`), 리프레시 토큰 저장(`SaveGeneralUserRefreshToken`), 피처 플래그, 재시도 컨텍스트 캐시, `RedisCacheManager`(`@Cacheable` 받침대)까지 한 인스턴스에 얹혀 있었다 — *동기 모놀리식이 못 들고 있던 휘발성·분산 상태를 한 군데 모은 사이드 저장소*였다("Redis 세션"은 실제로는 없었다 — `HttpSession` 미사용, 인증 상태로 남는 건 리프레시 토큰뿐).

V2는 이 그림을 두 군데에서 흔든다.

1. **핫 쿼리 시나리오** — "오늘 X식당 예약 목록" 같은 조회가 분(分) 단위로 같은 화면에 쏟아진다. 이 조회는 이미 읽기 최적화된 프로젝션 read model을 친다([[ADR-004-read-model-projection-and-replica]]) — 즉 read model 자체가 조인을 미리 풀어 디스크에 영속한 *머티리얼라이즈드 뷰=영속 캐시*다. 그 앞에 Redis 캐시를 또 얹는 게 이득인가, 무효화 부담만 늘리나?
2. **토큰 만료 시나리오** — 액세스 토큰이 만료돼 클라이언트가 리프레시한다. 인증이 무상태 JWT로 정리되면 "서버가 들고 있던 세션 상태"라는 명목은 사라지지만, V1이 Redis에 넣던 리프레시 토큰·강제 로그아웃(폐기)이라는 잔여분은 어딘가 있어야 하지 않나?

**읽기는 프로젝션이라는 영속 캐시를 이미 가졌고 인증은 무상태로 정리됐다. 그렇다면 Redis를 "읽기 가속 캐시"로 더 얹지 않고 "분산 조정·휘발성 상태" 전용으로 좁히되, 그 안에 섞인 durability 등급 발산이 인스턴스를 가를 근거가 되는지까지 정해야 한다.**

## 결정 동인 (Decision Drivers)

- read model은 이벤트를 비동기로 받아 갱신되어 이미 프로젝션 지연이라는 staleness 창을 하나 갖고 있다 — 그 위에 TTL 캐시를 얹으면 staleness가 두 겹이 되어 디버깅이 흐려진다.
- 무상태 JWT는 세션 상태(로그인 중인가의 서버 보관분)만 없앨 뿐, 리프레시 저장·토큰 폐기라는 잔여 상태는 별도로 거취를 정해야 한다.
- 정확해야 하는 상태(인증 부산물)와 손실 허용 상태(조정 상태)는 eviction 정책이 정반대(`noeviction` vs `allkeys-lru`)라 한 인스턴스의 `maxmemory-policy` 하나로 양립하지 못한다.
- 무트래픽 학습 규모에 맞는 최소 machinery — 새 인프라 컴포넌트(캐시 층)·기능별 인스턴스 분리를 함부로 늘리지 않는다.

## 검토한 선택지 (Considered Options)

**읽기 캐시**
- **A. read model 앞에 Redis 캐시 층을 기본 도입** — 모든 조회 앞에 TTL 캐시를 얹는다.
- **B. 캐시 층 기본 미도입** — 핫 쿼리는 화면 전용 프로젝션 추가, 읽기 확장은 query HA 레플리카.

**인증 부산물**
- **C. V1처럼 세션류 상태를 Redis에 유지** — 리프레시 토큰·폐기 목록을 서버 사본으로 계속 든다.
- **D. 세션 상태 제거 + 인증 부산물 거취는 [[RFC-019-auth-token-transport]] 결정([[ADR-020-auth-token-transport]])에 위임** — 무상태 서명 JWT로 정리되어 Redis에 남지 않는다.

**Redis의 아키텍처 역할**
- **E. V1의 잡다한 역할(캐시·세션·피처 플래그·재시도 컨텍스트·락 등)을 그대로 계승**
- **F. 분산 조정·휘발성 상태로 좁힘** — 멱등 디듀프·레이트리밋 카운터·일시적 분산 락만. 도메인 동시성(애그리거트 쓰기 경합)의 구체 메커니즘은 [[ADR-016-aggregate-concurrency-pessimistic-lock]]이 흡수한다.

**durability**
- **G. 기능별 인스턴스 분리** — must-not-evict(인증 부산물)와 evict 가능(조정 상태)을 인스턴스로 갈라 각각의 `maxmemory-policy`를 둔다.
- **H. 발산 워크로드 제거로 단일 등급·단일 인스턴스** — 인증 부산물이 Redis 밖으로 빠지면 남는 건 손실 허용 조정 상태뿐이라 분리할 이유가 사라진다.

## 결정 (Decision Outcome)

**채택: B + D + F + H.** 프로젝션이 이미 영속 캐시이므로 A는 같은 일을 두 번 하며 2차 staleness를 사들이고, C는 무상태 JWT가 없애려는 잔여 상태를 그대로 남겨 인증 경계를 흐린다. E는 V1의 사이드 저장소 잡동사니를 무비판적으로 계승한다. G는 정당한 우려(등급 발산)에서 출발하지만, 그 발산 자체를 없애는 H가 인스턴스를 쪼개는 것보다 싸다.

1. **읽기 캐시 = 기본 미설치.** read model 자체가 이벤트를 구독해 조회 모양으로 비정규화한 영속 캐시다([[ADR-004-read-model-projection-and-replica]]). 핫 쿼리의 1차 대응은 Redis 한 겹이 아니라 **그 화면 전용 프로젝션 추가** — read model은 용도마다 여럿 둘 수 있으므로 이미 가진 메커니즘의 재사용이다. 읽기 확장은 캐시가 아니라 **query 인스턴스의 HA 레플리카**로 분산한다.
2. **세션 상태는 불필요(무상태 JWT).** 인증 부산물(리프레시 토큰 저장·토큰 폐기/denylist)의 거취는 [[RFC-019-auth-token-transport]]가 닫았다 — 무상태 서명 JWT + rotation, 즉시 폐기는 포기(요구 입증 시에만 denylist 부활). 이 결정에 따라 인증 부산물은 **V2 기본에서 Redis에 남지 않는다**. 구체 전송·검증 메커니즘은 [[ADR-020-auth-token-transport]]이 소유한다 — 여기서는 "Redis에 남지 않는다"는 결과만 전사한다.
3. **V2에서 Redis 역할은 분산 조정·휘발성 상태로 좁힌다.** 정당한 자리는 셋 — ① 요청-단 멱등 디듀프(짧은 윈도), ② 레이트리밋 카운터(V1 계승), ③ 일시적 분산 락(V1 Redisson 락 계승). 다만 **도메인 동시성(애그리거트 쓰기 경합)은 [[ADR-016-aggregate-concurrency-pessimistic-lock]](애그리거트 + 비관 락)이 흡수한다** — 그 구체 메커니즘(L1 Redisson 락/L1′ DB 폴백)은 ADR-016이 정의하므로 여기서는 링크만 걸고 내용을 복제하지 않는다. 이 흡수로 본 ADR이 다뤄야 할 분산 락의 서술 범위는 도메인 불변식으로 못 푸는 인프라 레벨 상호배제로 좁혀진다.
4. **durability는 단일 등급.** 인증 부산물이 Redis 밖으로 빠지므로 남는 워크로드는 손실 허용 조정 상태(등급 1)뿐이다. 따라서 `maxmemory-policy`는 단일 정책(`allkeys-lru`/`volatile-lru` 계열) 하나로 충분하고, 인스턴스도 하나면 된다.

**Non-goal.** Redis의 *호스팅*·토폴로지(관리형 vs 자가, 단일/센티넬/클러스터, 인스턴스 개수)는 이 ADR의 범위가 아니다. 여긴 Redis의 아키텍처 *역할*만 정하고, 그 역할을 어떤 물리 배치로 얹을지는 배포 사이클([[ADR-013-db-hosting-and-read-write-topology]] 인접)에 위임한다.

### 결과 (Consequences)

- 좋은 점: read model이 영속 캐시 역할을 하므로 캐시 층 없이도 대부분의 핫 쿼리를 화면 전용 프로젝션·query HA 레플리카로 흡수한다.
- 좋은 점: 인증 부산물이 Redis 밖으로 빠지면서 Redis 침해가 곧 세션 탈취로 직결되는 경로가 제거된다.
- 좋은 점: 남는 워크로드가 단일 durability 등급이라 `maxmemory-policy` 하나·단일 인스턴스로 충분해 운영이 단순해진다.
- 나쁜 점 / 트레이드오프: 프로젝션으로도 싸게 못 푸는 진짜 핫 패턴이 있으면 "프로젝션 재설계 vs Redis 캐시 한 겹"의 손익분기가 측정 후에야 갈린다 — 그 식별 트리거·임계는 이 ADR의 범위 밖이다.
- 나쁜 점 / 트레이드오프: 즉시 폐기 요구가 나중에 입증돼 denylist가 부활하면 must-not-evict 등급이 되살아나고, 단일 durability·단일 인스턴스 가정이 깨져 재검토가 필요하다(재검토 트리거).

### 확인 (Confirmation)

- read model 조회 경로 앞에 Redis 캐시 계층이 코드상 존재하지 않는지 코드 리뷰로 확인한다.
- 리프레시 토큰·폐기 목록의 서버 사본이 Redis 키로 존재하지 않는지 확인한다([[ADR-020-auth-token-transport]] 확인 항목과 공유).
- Redis에 실존하는 키가 멱등 디듀프·레이트리밋·분산 락 셋 중 하나로만 분류되는지 코드 리뷰로 확인한다.
- Redis 인스턴스의 `maxmemory-policy`가 단일 정책(`allkeys-lru`/`volatile-lru`)으로 설정돼 있는지 배포 설정으로 확인한다.

## 선택지 상세 (Pros and Cons of the Options)

### A. read model 앞에 Redis 캐시 층 기본 도입 (기각)

- 장점: 프로젝션 재설계 없이 조회 지연을 즉시 낮출 수 있다.
- 단점: 프로젝션이 이미 영속 캐시라 캐시 위의 캐시가 되고, 이벤트→프로젝션 지연 위에 프로젝션→캐시 TTL 지연이 더해져 staleness가 두 겹으로 쌓인다.
- 기각 사유: 같은 문제(읽기 가속)를 이미 가진 메커니즘(프로젝션)으로 두 번 풀며, 무효화 정책·직렬화 포맷이라는 새 표면을 들여온다.

### C. V1처럼 세션류 상태를 Redis에 유지 (기각)

- 장점: 즉시 강제 로그아웃(폐기)을 무상태 토큰에서도 그대로 지원한다.
- 단점: 무상태 JWT로 정리하려는 목적(서버 세션 저장소 제거)과 어긋난다.
- 기각 사유: [[RFC-019-auth-token-transport]]가 즉시 폐기를 요구 미입증 상태로 포기하고 무상태 서명 JWT로 정리하기로 닫았다.

### E. V1의 잡다한 Redis 역할을 그대로 계승 (기각)

- 장점: 마이그레이션 비용이 없다.
- 단점: 캐시·세션·조정 상태가 한 인스턴스에 섞여 durability 등급이 발산하고, "Redis가 왜 이 일까지 하나"가 항목별로 불분명해진다.
- 기각 사유: V2의 두 구조 변화(프로젝션=영속 캐시, 무상태 JWT)가 V1 역할 상당수를 실제로 대체한다.

### G. 기능별 인스턴스 분리 (기각)

- 장점: must-not-evict(인증 부산물)와 evict 가능(조정 상태)의 정책 충돌을 인스턴스 단위로 정직하게 해소한다.
- 단점: 인스턴스 두 개를 프로비저닝·운영해야 한다.
- 기각 사유: 인증 부산물 자체가 Redis 밖으로 빠지므로(D) 애초에 갈릴 등급이 하나만 남는다 — 분리는 발산이 실재할 때만 정당한데 그 발산을 제거했다.

## 추가 정보 (More Information)

- **미결정 (→ 구현 사이클)**: 캐시를 정말 둘 "프로젝션이 싸게 못 푸는 패턴"의 식별 트리거·손익분기 임계, 멱등 디듀프의 Redis 키 구성·윈도 길이, V1 Redis 잔재(피처 플래그·재시도 컨텍스트)의 V2 항목별 귀속표, denylist 부활 시 durability 등급·인스턴스 분리 재설계.
- 관련: [[RFC-018-caching-redis-role]] · [[DESIGN-018-caching]] · [[RFC-019-auth-token-transport]] · [[RFC-002-read-model-consistency]] · [[RFC-015-authorization-model]] · [[RFC-007-deployment-infra-ops]] · [[ADR-004-read-model-projection-and-replica]] · [[ADR-016-aggregate-concurrency-pessimistic-lock]] · [[ADR-020-auth-token-transport]] · [[ADR-013-db-hosting-and-read-write-topology]]
- 계승: `19.caching-redis-role.md`(v2 초기 스케치) — 읽기 캐시 미도입·인증 부산물 제거·분산 조정 전용·단일 durability 골격은 유지하되, 도메인 동시성 메커니즘의 상세는 [[ADR-016-aggregate-concurrency-pessimistic-lock]]으로 이관해 중복을 없앤 것이 이 ADR의 차이다.
