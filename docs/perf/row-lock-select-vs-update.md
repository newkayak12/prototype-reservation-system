# 좌석 행 잠금: `SELECT ... FOR UPDATE` → 조건부 `UPDATE`

Phase 4의 마지막 방어선(DB 행 잠금)을 구현하는 두 가지 방식을 같은 시나리오로 각각 10회 측정하고
바꾼 기록. 원본 산출물은 `perf/k6/result-after/` (`redesigned-*` = 이전 방식, `claim-update-*` = 현재).

전체 개선 맥락은 `docs/perf/baseline-vs-redesign.md`를 본다. 이 문서는 그중 **좌석 행을 가져가는
한 지점**만 다룬다.

## 무엇을 바꿨나

처음 구현은 `@Lock(PESSIMISTIC_WRITE)` — 즉 `SELECT ... FOR UPDATE`로 갔다. 원래 정한 설계는
조건부 `UPDATE`였고, 그쪽으로 되돌린 것이 이 변경이다.

| | `SELECT ... FOR UPDATE` (이전) | 조건부 `UPDATE` (현재) |
|---|---|---|
| 문장 수 | 잠금 SELECT → (판단) → UPDATE | UPDATE 한 문장 |
| 승패 판정 | 잠긴 행을 받았는가 | **갱신 건수가 1인가** |
| 조건 검사와 갱신 사이 | 애플리케이션이 끼어든다 | 같은 문장 안이라 창이 없다 |
| 대상 행 선택 | `Pageable`로 잠금 SELECT에서 한 건 | 후보를 먼저 읽고 그중 하나를 노린다 |

둘 다 같은 창을 닫는다. 차이는 **판정이 어디서 일어나는가**다. 이전 방식은 "행을 잠갔다"와
"그 행이 아직 비어 있다"가 서로 다른 문장에 있었고, 현재 방식은 `AND table_status = 'EMPTY'`가
갱신과 한 문장 안에 있다.

## 구현

`infrastructure-module/.../TimeTableJpaRepository.kt`

```kotlin
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
UPDATE TimeTableEntity timetable
SET timetable.tableStatus = 'OCCUPIED',
    timetable.version = timetable.version + 1
WHERE timetable.identifier = :identifier
AND timetable.tableStatus = 'EMPTY'
""")
fun claimTimeTable(identifier: String): Int
```

`AND table_status = 'EMPTY'`가 이 문장의 전부다. 같은 행을 두고 두 트랜잭션이 겹치면 뒤에 온 쪽은
앞 트랜잭션이 커밋할 때까지 이 행의 X-lock을 기다리고, 풀린 뒤 최신 값을 다시 읽어 `EMPTY`가
아님을 보고 **0행을 고친다**. 즉 갱신 건수가 곧 승패다 — 1이면 내가 가져간 것이고, 0이면 남이
먼저 가져간 것이다.

### 왜 두 단계인가

`infrastructure-module/.../ClaimTimeTableAdapter.kt`

```kotlin
override fun claim(inquiry: ClaimBookableTimeTableInquiry): TimeTable? =
    timeTableJpaRepository.findBookableTimeTable(...)
        .shuffled()
        .firstOrNull { timeTableJpaRepository.claimTimeTable(it.identifier) == 1 }
        ?.toDomainEntity()
```

어느 행을 노릴지 알아야 조건부 갱신을 걸 수 있는데, JPQL의 UPDATE에는 `LIMIT`도 서브쿼리로
자기 테이블을 고르는 방법도 없다. 그래서 후보를 먼저 읽고(**잠그지 않는다**) 그중 하나를 갱신으로
가져간다.

후보 목록이 낡아도 상관없다. 판정은 전적으로 `claimTimeTable`의 `WHERE`가 하고, 이미 팔린
낡은 후보는 0행으로 떨어질 뿐이다. 후보 조회는 정확성에 기여하지 않고 **대상 후보를 좁히는
역할만** 한다.

### 왜 후보를 섞는가

모두가 같은 순서로 읽으면 전원이 첫 번째 행에 달라붙는다. 한 명이 이기고 나머지는 그 행의 잠금이
풀릴 때까지 기다렸다가 0행을 받고 두 번째 행으로 몰려간다 — 좌석 수만큼 줄서기가 반복된다.
시작 지점을 흩어 두면 서로 다른 행을 노리므로 대부분 첫 시도에 끝난다.

### 왜 `version`을 직접 올리는가

벌크 UPDATE는 낙관적 잠금 버전을 자동으로 올리지 않는다(JPA 명세). 그냥 두면 이 행을 미리
읽어 둔 다른 트랜잭션이 옛 버전 그대로 덮어써도 아무도 모른다. `@Version`을 달아 둔 의미가
이 경로에서만 사라지는 셈이라 명시적으로 올린다.

### 왜 flush + clear 둘 다인가

- `flushAutomatically = true` — 벌크 UPDATE 전에 보류 중인 변경을 먼저 DB로 보낸다. 안 그러면
  아직 flush되지 않은 변경이 벌크 갱신 뒤에 덮어쓴다.
- `clearAutomatically = true` — 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 1차 캐시에 남은
  엔티티는 낡은 `EMPTY`를 그대로 들고 있다. 비우지 않으면 갱신 직후 재조회가 **DB가 아니라
  캐시의 낡은 값**을 돌려준다.

`clear`의 대가로 방금 가져간 행이 준영속이 된다. 그래서 `CreateTimeTableOccupancyAdapter`가
`findTimeTableEntityByIdentifierEquals`로 다시 읽어서 점유를 붙인다 — 원래도 그렇게 하고 있었다.

## 이름

`Lock*` → `Claim*`으로 바꿨다. 더는 잠그고 읽는 것이 아니라 조건부로 가져가는 것이라,
`SELECT ... FOR UPDATE`를 가리키던 이름을 남겨 두면 코드가 거짓말을 한다.

| 이전 | 현재 |
|---|---|
| `LockBookableTimeTable` (포트) | `ClaimBookableTimeTable` |
| `LockBookableTimeTable.query()` | `ClaimBookableTimeTable.claim()` |
| `LockTimeTableAdapter` | `ClaimTimeTableAdapter` |
| `TimeTableJpaRepository.lockBookableTimeTable()` | `.claimTimeTable()` |

## 반환하는 좌석의 상태

`claim`은 가져가기에 성공한 행을 **가져가기 직전 모습(`EMPTY`)** 그대로 돌려준다. 도메인이
`CreateTimeTableOccupancyDomainService.create()`에서 `EMPTY → OCCUPIED` 전이를 자기 층에서
다시 표현하기 때문이다(`TimeTableStatusIsNotVacantPolicy`가 `EMPTY`를 요구한다).

DB의 조건부 갱신과 도메인의 `attachOccupied()`는 **같은 전이를 서로 다른 층에서 표현한 것**이지
중복이 아니다. 앞의 것은 그 전이가 전역에서 한 번만 일어나게 만드는 동시성 장치이고, 뒤의 것은
그 전이가 도메인 규칙을 만족하는지 검사한다.

## 검증

- `spotlessCheck` + `detekt` — BUILD SUCCESSFUL
- 전체 테스트 — 191 suites / **454 tests / 0 failures / 0 errors** (전환 전과 동일)
- `TimeTableOccupancyConcurrencyTest` 4건 통과 (실제 MySQL Testcontainer, 진짜 동시 트랜잭션)
  - 좌석보다 훨씬 많은 사용자가 동시에 달려들어도 좌석 수만큼만 점유된다 (80명 → 10좌석 → 10건)
  - 조건부 갱신을 우회해도 같은 좌석에 살아 있는 점유를 두 건 만들 수 없다
  - 점유를 풀면 같은 좌석에 새 점유가 들어오고, 풀린 이력은 남는다
  - 같은 좌석을 두고 동시에 달려들어도 정확히 한 명만 가져간다

한 가지 관찰: 이전 방식에서는 `Duplicate entry ... unique_active_occupancy`가 테스트 결과에
**2번** 찍혔다 — 의도적으로 우회한 테스트 1건과, **행 잠금을 빠져나가 UNIQUE 제약까지 도달한
경합 1건**. 현재 방식에서는 1번, 의도적 우회 것만 찍힌다. 조건부 갱신이 경합을 앞에서 막았다는
신호로 보이지만 단일 실행이라 타이밍일 수 있어, 증명이 아니라 관찰로 남긴다. 어느 쪽이든
UNIQUE 제약이 마지막 방어선으로 실제 작동한다는 사실은 두 방식 모두에서 확인됐다.

## 부하 측정

측정 조건은 `baseline-vs-redesign.md`와 완전히 동일하다 — 좌석 30개, 유저 300명, `ramping-vus`
100→300→600→1000→1500→2000, 매회 재시딩, 10회 반복. 두 방식 모두 같은 Docker 스택 위에서
같은 날 측정했다.

### 조건부 `UPDATE` (현재, `claim-update-*`)

| # | 총 요청수 | 성공 | 거절 | 대기열 timeout | 중앙값(ms) | p95(ms) | 정착(s) | 좌석/점유 | 오버부킹 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | 28,367 | 30 | 4,537 | 2,392 | 174 | 59,593 | 2.33 | 30/30 | 0 |
| 2 | 28,032 | 30 | 4,540 | 2,404 | 141 | 59,731 | 2.31 | 30/30 | 0 |
| 3 | 28,360 | 30 | 4,490 | 2,454 | 96 | 59,824 | 2.29 | 30/30 | 0 |
| 4 | 28,345 | 30 | 4,568 | 2,403 | 136 | 59,717 | 2.32 | 30/30 | 0 |
| 5 | 28,043 | 30 | 4,554 | 2,433 | 139 | 59,784 | 2.32 | 30/30 | 0 |
| 6 | 28,183 | 30 | 4,559 | 2,417 | 128 | 59,708 | 2.32 | 30/30 | 0 |
| 7 | 27,964 | 30 | 4,569 | 2,452 | 89 | 59,712 | 2.30 | 30/30 | 0 |
| 8 | 27,881 | 30 | 4,556 | 2,404 | 81 | 59,812 | 2.35 | 30/30 | 0 |
| 9 | 28,197 | 30 | 4,547 | 2,459 | 100 | 59,792 | 2.29 | 30/30 | 0 |
| 10 | 27,912 | 30 | 4,519 | 2,402 | 89 | 59,746 | 2.27 | 30/30 | 0 |

### 두 방식 비교

| 지표 | `SELECT ... FOR UPDATE` | 조건부 `UPDATE` | |
|---|---|---|---|
| 오버부킹 | 0/10 | 0/10 | 동일 |
| 좌석 판매 | 10회 전부 30건 | 10회 전부 30건 | 동일 |
| 예약 중앙값 | 96~181ms (중앙 148) | 81~174ms (중앙 114) | 차이 없음 |
| p95 | 59,607~59,888ms | 59,593~59,824ms | 동일 |
| 정착 시간 | 2.28~2.37s | 2.27~2.35s | 동일 |
| 총 요청수 편차 | 27,765~28,594 (1.03배) | 27,881~28,367 (**1.02배**) | 미세 개선 |
| 대기열 timeout | 2,401~2,492 | 2,392~2,459 | 동일 |

**성능 차이는 없다고 보는 것이 정직하다.** 중앙값이 148ms에서 114ms로 내려갔지만 회차 내 변동폭
(81~174ms)이 그보다 커서 노이즈와 구분되지 않는다. p95·정착시간·대기열 통과량은 측정 오차 범위
안에서 같다.

예상된 결과이기도 하다. 이 구간은 **컨슈머가 슬롯 키 순서대로 하나씩 처리하는 경로**라 정상
경로에서는 애초에 경합이 거의 없고, 잠금 방식이 응답 시간을 좌우하는 지점이 아니다. 병목은
`baseline-vs-redesign.md` 관찰 5가 짚은 대기열 폴링이고, p95의 60초 벽도 거기서 온다 — 좌석 행을
어떻게 가져가든 그 벽은 그대로다.

### 그래서 왜 바꿨나

성능이 아니라 **정확성의 표현 방식** 때문이다.

1. 판정이 한 문장 안으로 들어왔다. 이전에는 "행을 잠갔다"와 "그 행이 아직 비어 있다"가 서로 다른
   문장에 있었고, 그 사이에 애플리케이션 코드가 있었다. 지금은 없다.
2. 동시성 테스트에서 UNIQUE 제약까지 새어 나가던 경합 1건이 사라졌다(위 "검증" 참고).
3. 원래 정한 설계가 이것이었다.

**성능이 같다는 사실 자체가 결과다** — 더 단순한 쪽을 비용 없이 고를 수 있다는 뜻이다.
