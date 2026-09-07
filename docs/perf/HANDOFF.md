# 부하테스트 작업 인계 요약

> `before` 워크트리(`/Users/sanghyeonkim/Downloads/port/prototype-reservation-system`,
> 브랜치 `chore/performance-test`)에서 이어서 작업하기 위한 문맥 정리.
> 작성 시점 기준으로 이 문서가 있는 워크트리는 `chore-performance-test-after`다.

## 0. 지켜야 할 제약

- **커밋은 사용자가 직접 한다.** 에이전트가 `git commit` 하지 않고, `Co-Authored-By` 트레일러도 넣지 않는다.
- 서브에이전트/워크플로는 사용자가 요청할 때만 쓴다.

## 1. 워크트리 지형

| 워크트리 | 경로 | 브랜치 | 역할 |
|---|---|---|---|
| before | `/Users/sanghyeonkim/Downloads/port/prototype-reservation-system` | `chore/performance-test` (`67a97c15`) | 개선 전 아키텍처. 워킹트리 거의 깨끗 |
| after | `/Users/sanghyeonkim/orca/workspaces/prototype-reservation-system/chore-performance-test-after` | `newkayak12/perf-timetable-occupancy-redesign` (`4421802a`) | 재설계. **Phase 1 작업 60여 개 파일이 스테이징된 채 미커밋** |

**중요**: 두 워크트리는 같은 Docker 스택(`chore-performance-test-after-*`)과 포트 8081을 공유한다.
한쪽을 측정할 때 다른 쪽 앱이 8081을 잡고 있으면 안 된다. 앱 기동 시 `--server.port=8081`을
명시해야 한다(기본값은 8080).

`chore/performance-test`의 최신 내용은 이미 after 워크트리로 병합해 뒀다(파일 내용 기준.
git 머지 커밋은 만들지 않았다 — 인덱스가 더러워 `git merge`가 거부됨).

## 2. 지금까지의 결론

### 폐기된 측정

- `docs/perf/baseline-report.md` — `ramping-vus` 설계 결함. 좌석을 한 번만 시딩해 첫 구간에서
  매진시킨 뒤 나머지를 전부 '매진 거절 경로'로 측정했다. 문서 상단에 폐기 배너가 붙어 있다.
- `docs/perf/baseline-vs-redesign.md`의 VU 구간별 수치 — 같은 이유로 무효.
- `docs/perf/row-lock-select-vs-update.md`의 부하 측정 절 — 같은 하네스 기반이라 무효.
  (단, `SELECT FOR UPDATE` → 조건부 `UPDATE` 리팩터링의 설계 서술 자체는 유효)

### 유효한 측정 — 단일 슬롯 버스트 (`docs/perf/single-slot-burst.md`)

VU 레벨마다 좌석 30석 리셋 후 VU 수만큼이 동시에 1번씩 시도. 100·300·600·1000·1500·2000·3000
각 10회. 원본은 `performance_test/single-slot/{before,after}/`.

| VU | 처리율 B→A | p95(ms) B→A | 해소(s) B→A |
|---:|:--|:--|:--|
| 100 | 150 → 169 | 652 → 50 | 0.67 → 0.59 |
| 600 | **486** → 171 | 1104 → 56 | 1.23 → 3.53 |
| 1500 | **568** → 163 | 2052 → 54 | 2.64 → 9.19 |
| 3000 | **529** → 170 | 3943 → 38 | 5.68 → 17.69 |

- **정합성은 양쪽 완벽**. 140회 전부 30/30, 오버부킹 0, 중복 0, 5xx 0, timeout 0.
  재설계 명분이던 "오버부킹 방지"는 이 측정으로 증명되지 않았다 — before가 애초에 안 틀렸다.
- **단일 슬롯에서는 before 우세.** 처리량·매진 시간에서 이기고 지연에서만 진다.
- before가 빠른 이유: 임계구간이 1.9ms로 짧다. `해소 5.68s / 3000건 = 1.9ms`이고, 이 가정으로
  예측한 p50(2.85s)이 실측 p50(2.71s)과 일치한다. **직렬화는 맞고, 구간이 짧을 뿐이다.**
- **단일 슬롯은 before에게 가장 유리한 조건**이다. 락 키가 하나라 동시에 열린 트랜잭션이 항상
  1개다. after의 원자 연산/파티션 병렬성은 발휘될 여지가 없다.
- after의 처리율은 VU 100→3000에서 169·139·171·166·163·171·170으로 **평평하다**. 상한 =
  `admission-capacity(100) / admission-interval-millis(500)` = 200 req/s. 대기열이 의도대로
  뒷단 부하를 상수로 만든 결과이고, 동시에 처리량에서 지는 이유다.

핵심 문장: **재설계가 바꾼 것은 성능이 아니라 성능의 모양이다.** 예측 가능성을 얻고 최대
처리량을 내줬다.

## 3. after 쪽에서 발견하고 고친 결함 (이 워크트리에만 있음, 미커밋)

`CreateTimeTableOccupancyService`에서 `releaseAdmission.release(...)` 호출이 성공 경로 한
곳뿐이었다. 품절로 거절된 사용자가 입장 permit을 영영 반납하지 않아 **VU가 300이든 3,000이든
예약 API 도달 요청이 누적 130건에서 멈췄다**(정원 100 + 성공 30명 반납분). 대기열이 아니라
사실상 데드락.

수정: 품절·중복 같은 **종착 거절**(재시도해도 결과가 같음)에서도 반납. 발행 실패처럼 재시도
여지가 있는 실패에서는 반납하지 않고 lease 만료에 맡긴다.

수정 후 전 VU 레벨 대기포기 0. 결함 상태 원본은 `performance_test/single-slot/after-permit-bug/`
에 증거로 보존.

변경 파일: `CreateTimeTableOccupancyService.kt`, `ReleaseAdmission.kt`(KDoc 기준 정정),
`CreateTimeTableOccupancyServiceTest.kt`(반납 검증 3건 추가). `spotlessCheck` + `detekt` +
`:application-module:test` 통과.

## 4. 장비 한계 (실측)

| 항목 | 값 |
|---|---|
| 발생기 처리량 상한 | **약 27,000 req/s** (VU 500부터 포화) |
| 동시 VU 상한 | 약 16,000 (임시 포트 49152–65535) |
| 장비 | 12코어 / 48GB (Mac16,8), k6 v2.2.0 |

**결론: 지금까지의 before 528~608 req/s는 발생기 한계가 아니라 앱 한계다.** 발생기에 45배
여유가 있으므로 VU 3,000은 이 장비가 낼 수 있는 부하의 20% 수준이었다. **before를 무너뜨릴
만큼 밀어본 적이 없다.**

## 5. 하네스 현황 (`perf/k6/`)

| 파일 | 상태 |
|---|---|
| `run.sh`, `seed.sh`, `scenarios/booking.js`, `lib/aggregate.py` | 단일 슬롯. **검증됨** (140회 실행) |
| `seed-multi.sh`, `run-multi.sh`, `scenarios/booking-multi.js`, `lib/aggregate-multi.py` | 다중 경합 지점. **작성만 됨, 미실행** |

- 결과 저장 경로는 시나리오별로 분리했다: `performance_test/single-slot/<label>/`,
  `performance_test/multi-slot/<label>/`
- 다중 하네스는 `SKIP_QUEUE=1`로 before도 돌릴 수 있게 만들었다(before엔 대기열이 없음).
  라벨이 `before*`면 자동 설정된다.
- 다중 하네스 정합성 SQL은 **슬롯 단위**로 초과를 센다. 총합만 보면 한 슬롯의 오버부킹이
  다른 슬롯의 미판매에 가려진다.
- **다중 하네스 파일들은 이 워크트리에만 있다.** before에서 쓰려면 옮겨야 한다.

## 6. 결정된 것 / 열린 것

**결정됨**: 다중 슬롯 측정 시 Hikari 풀과 Kafka 파티션을 **양쪽 동일 값으로 명시**한다
(예: 풀 50, 파티션 10). 현재는 둘 다 설정 파일에 없어 기본값(Hikari 최대 10)이고, 그대로
돌리면 아키텍처 차이가 아니라 풀 크기 차이를 재게 된다.

**열림**: 측정 범위(경합 지점 수, VU)와 다음 시나리오 우선순위.

## 7. 다음 방향 — 시나리오 매트릭스

사용자 관점: 물류처럼 정상 상태 처리량이 지배하면 before, 예약 플랫폼처럼 순간 스파이크와
그 여파가 지배하면 after. 결론을 "A가 B보다 낫다"가 아니라 **"워크로드 모양이 설계를
결정한다"**로 가져가는 것이 목표.

| # | 시나리오 | 무엇을 가르나 | 예측 | 상태 |
|---|---|---|---|---|
| 1 | 단일 슬롯 스파이크 | 순간 집중 시 처리량 vs 지연 | before 우세 | ✅ 완료, 예측 적중 |
| 2 | **스파이크 중 무관한 트래픽** | 핫슬롯 폭주가 다른 음식점 요청을 죽이는가 (blast radius) | **after 우세** | 미착수 |
| 3 | 다중 슬롯 동시 오픈 | 경합 지점 ↑ → 동시 트랜잭션 ↑ | after 우세 | 하네스만 완성 |
| 4 | 지속 부하 (물류형) | 정상 상태 최대 처리량 | before 우세 | 미착수 |
| 5 | 극한 스파이크 VU 10k~15k | before의 실제 붕괴점 | before 붕괴 | 미착수 |
| 6 | 반복 스파이크 | 웨이브 사이 회복, 큐 배수 | after 우세 | 미착수 |

**2번이 서사적으로 가장 중요하다.** 예약 플랫폼의 진짜 재앙은 "인기 식당 예약이 느린 것"이
아니라 "인기 식당 때문에 사이트 전체가 죽는 것"이다. before는 `@Transactional`이 분산락 전체를
감싸 락 대기 스레드가 DB 커넥션을 물고 있고, after는 요청 경로에 트랜잭션이 없다. 구조 차이가
가장 극명한 지점이고 아직 아무도 측정하지 않았다.

**5번이 가장 싸다** — 기존 하네스에 VU만 올리면 되고, 1번 결론의 빈 곳("밀어보지 못했다")을
바로 메운다.

권장 순서: **5 → 2 → 3**.

## 8. 재현 방법

```bash
# 도커 스택 (after 워크트리의 compose.yaml 사용 중)
docker compose up -d          # 완전 초기화가 필요하면 down -v 먼저

# 앱 (포트 명시 필수)
./gradlew :adapter-module:bootJar -x test
java -jar adapter-module/build/libs/adapter-module-0.0.1-SNAPSHOT.jar --server.port=8081

# 단일 슬롯 스윕
./perf/k6/run.sh before        # 또는 after
REPEATS=1 VU_LEVELS=1000 ./perf/k6/run.sh probe   # 빠른 확인

# 다중 경합 지점 (미검증)
POINT_LEVELS="1 10" REPEATS=2 ./perf/k6/run-multi.sh after
```

측정 사이 주의사항:
- 회차 간 Redis 상태가 남으면 결과가 오염된다. 이상하면 `redis-cli FLUSHALL`.
- `seed.sh`는 매번 새 restaurantId를 뽑아 이전 버스트의 Redis 키를 피한다.
- 정합성 판정 기준은 `released_at IS NULL`이다. after는 점유가 `PENDING`으로 생성되므로
  `occupied_status = 'OCCUPIED'`로 세면 판 좌석이 전부 0으로 집계된다.
- 조건 통일을 위해 **양쪽 모두 `@RateLimiter`를 제거**한 상태다.
