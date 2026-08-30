# k6 부하테스트 전략 — 무엇을 닫으려 재는가

> 작성: 2026-07-20 · 상태: **전략(실행 전)**. 실측은 구현 이후 부하테스트 단계([[12-implementation-plan]] C-6)에서 실행한다.

이 노트는 k6 사용법 노트가 아니다. [[_triage-summary]]의 **미해소 두 항목을 무엇으로 닫는가**를 정하고, k6는 그 판정을 만들기 위한 도구로만 쓴다. 도구 사용법은 §5(부록)에 몰아둔다.

---

## 1. 닫으려는 것 — 트리아지 두 항목

트리아지(07/08)가 남긴 미해소 항목:

| # | 항목 | 정체 |
|---|---|---|
| **B** | DB 쓰기 상한 미측정 (**LWW 가드 행 경합**) | 프로젝터가 read model에 반영할 때 aggregate별 `applied_sequence_no`를 읽고-비교-쓰는 **행 경합**([[RFC-025]]). 비동기 경로. |
| **C** | **freshness gate 대기 상한 미정** | read-your-writes용 `ReadFreshnessGate`([[RFC-030]] 결정 4)의 대기 시간 상한. B의 lag 분포에서 파생. |

**주의 — 커맨드 쓰기 상한(비관 락)은 여기 없다.** 동기 커맨드 경로의 경합(Redisson L1 + DB `FOR UPDATE`, [[RFC-014]]·[[ADR-016]])은 트리아지에서 **이미 해소된 항목**이다(04-command-application). 측정하면 유용한 참고지만 트리아지를 닫는 대상은 아니다 — §4에 참고 축으로만 둔다.

### 닫힘 = 숫자가 아니라 판정이다

트리아지가 걱정하는 건 "몇 ev/s냐"가 아니라 **"이 경합 설계가 병리적으로 무너지나, 우아하게 버티나"**다. 그래서 닫힘의 형태는 SLO 숫자가 아니라 **실패의 형태에 대한 판정**이다.

| 항목 | 닫힘 판정 |
|---|---|
| B | 부하를 올릴 때 lag이 **수렴(따라잡음)**이면 → 단일 순차 relay + LWW 설계 **OK**. **발산(무한 증가)**이면 → 설계 재검토 트리거(파티션드/CDC 졸업, [[RFC-025]]). 변곡점 rate는 부산물로 기록. |
| C | gate 대기 꼬리가 timeout **밑에서 안정** → 동기 권위 응답 기본([[RFC-030]] 결정 1) **OK**. 대량 **초과** → 초과 시 동작(stale 응답 / 503 / 202) 결정이 필요하다는 신호. |

"이 상한이 충분한가"의 SLO 판정만 실트래픽이 생길 때로 유예한다(지어내지 않음).

---

## 2. 측정 전 확정 — 경합이 떨어지는 도메인 지점 하나

**합성 부하는 hotspot을 발견하지 못한다.** 실트래픽이 없으니 부하 분포를 내가 만든다 — 균등하게 뿌리면 핫 행이 안 생겨 경합이 안 드러나고, 몰아주면 내가 hotspot을 고른 것이다. 즉 hotspot은 테스트가 찾아주는 게 아니라 **도메인 구조에서 단언하는 사실**이고, 테스트는 그 지점의 상한만 잰다.

경합이 수렴하는 지점은 **공유·유한 자원 = 같은 TimeTable 슬롯**이다([[RFC-014]] 시나리오 "같은 7시 테이블을 동시에 노린다"). **어느 슬롯이 hot인지**는 이렇게 도메인으로 단언한다 — 예약이 몰리는 시간슬롯 한 곳에 커맨드를 집중시켜야 락(A)·LWW 행(B)이 의미를 갖는다.

반면 그 경합이 **어느 aggregate 행에 떨어지는가** — ① 개별 `Reservation`, ② 공유 슬롯 점유(`timeTableOccupancyId`) aggregate, ③ 슬롯 용량은 교차 불변식이라 [[RFC-014]] 결정 3대로 **사가** — 는 책상에서 확정할 대상이 아니라 **재보면서 배우는 설계 변수**다. RFC-014의 "락 범위=단일 `aggregate_id`, 교차=사가"에서 ②/③ 중 어디냐가 B의 핫 행 위치를 바꾸지만, 무트래픽 프로토타입에서 이걸 미리 확신하긴 어렵다.

그래서 블로커로 두지 않는다. **현재 write-model 모델링을 작업가설로 잡고** 그 aggregateId에 부하를 몰아, 경합이 실제로 어디에 떨어지고 어떤 모양으로 무너지는지 관측한다 — 그 실패 모양이 "경계가 맞았나"의 피드백이고, 측정이 이 결정을 대신 가르쳐준다. (단 skew가 조준할 구체 슬롯 aggregateId는 가설로라도 하나 정해야 실행된다. [[02-write-model]]·[[05-aggregate-design]] 참조.)

### 상한은 목표치(SLO) 없이 잰다

| 구분 | 무엇을 답하나 | 목표치 필요? | 이 노트 대상 |
|---|---|---|---|
| **capacity 상한** | 최대 몇까지 버티나 | ❌ | ✅ |
| **목표/SLO** | 몇을 버텨야 하나 | ✅ 실트래픽 필요 | ❌ 유예 |

capacity 상한은 시스템이 스스로 무너지는 지점이라 목표 없이도 잰다. 결과는 SLO가 아니라 특성화(characterization)로 기록한다.

---

## 3. 항목별 측정 설계

각 항목을 `질문 → 관측 → 도메인 흐름 → 도구 → 닫힘 판정`으로 편다. 도구 상세는 §5.

### 항목 B — 프로젝터 처리량 상한 (LWW 가드 행 경합)

- **질문**: hot 슬롯에 이벤트를 몰아넣을 때 projection lag이 따라잡나, 발산하나?
- **관측 (k6 밖 지표)**: read model `applied_sequence_no` vs event_store 최신 `sequence_no`의 차이, 또는 Kafka consumer lag.
- **도메인 흐름**: §2에서 확정한 hot 슬롯 aggregate에 커맨드를 주입해 이벤트를 발생시킴. projector는 Kafka 소비자지 HTTP 엔드포인트가 아니므로 **상한은 HTTP 응답이 아니라 lag으로 관측**한다.
- **도구**: `ramping-arrival-rate`로 입력 rate를 계단식 상승(§5). 부하는 hot 슬롯에 skew(§5). relay는 ShedLock 단일 순차라 우회로가 없으므로([[RFC-025]]) 이 경로가 곧 병목.
- **닫힘 판정**: lag 수렴 → 설계 OK. **발산 시작 rate = 프로젝터 상한**이자 재검토 트리거.
- **부수 학습**: 경합이 §2에서 가설한 aggregate 행에 실제로 떨어졌는지 관찰 → aggregate 경계 모델링(①②③)의 적절성을 여기서 배운다. 측정의 산출물이 숫자만이 아니라 이 학습이기도 하다.

### 항목 C — freshness gate 대기 분포

- **질문**: read-after-write에서 gate가 얼마나 기다리며, timeout을 얼마나 넘기나?
- **관측**: GET의 gate 대기 시간(커스텀 `Trend`), timeout 발생률.
- **도메인 흐름**: `POST 커맨드` → 응답에서 `sequenceNo` 토큰 추출([[RFC-030]] 결정 4·5) → 즉시 그 토큰을 실어 `GET`.
- **도구**: B와 같은 부하 위에서 VU가 write→read 왕복. C는 B의 lag 실측에서 파생되는 대기 분포다.
- **닫힘 판정**: 꼬리가 timeout 밑 안정 → 동기 응답 기본 유지 OK. 대량 초과 → 초과 시 동작(stale/503/202) 결정 근거로 W 분포를 남김.

---

## 4. 참고 축 (트리아지 아님) — 커맨드 쓰기 상한

동기 커맨드 경로의 비관 락 경합. 트리아지는 해소됐지만, hot 슬롯에 부하가 몰릴 때의 체감을 보려면 같이 재둘 만하다.

- **관측**: `http_req_duration` p99, 에러율, **409(`AggregateConflict`) 비율**, lock-wait 타임아웃.
- **특징**: 병목이 HTTP 응답에 그대로 나타난다 → k6만으로 잡힌다.
- 이 축은 B와 **한 판에 섞지 않는다**(서로 오염). 별도 실행.

---

## 5. 부록 — k6 도구 사용법 (처음이라면)

### open vs closed — 상한 측정에는 open만

| 모델 | executor | 상한 측정 |
|---|---|---|
| closed (VU 기반) | `constant-vus`, `ramping-vus` | ❌ 느려지면 부하도 줄어 상한을 못 찾음 |
| **open (도착률 기반)** | **`ramping-arrival-rate`** | ✅ 시스템 상태와 무관하게 rate를 주입 → 무너지는 지점이 드러남 |

open model이 답하는 건 **"몇 req/s에서 무너지나(rate knee)"**뿐이다. **"어느 aggregate가 hot이냐"는 알려주지 않는다** — 그건 §2에서 도메인으로 단언한다.

### hot-key skew — 경합 행에 조준

부하를 여러 aggregate에 균등하게 뿌리면 경합이 안 드러나고 상한이 뻥튀기된다. §2에서 확정한 hot 슬롯에 부하를 편향시킨다. 균등 baseline과 skew를 둘 다 재면 그 격차가 경합 비용이다.

```js
// skew: 요청의 90%를 §2에서 확정한 hot aggregate 세트에 집중
const HOT = ['<hot-slot-agg-1>', '<hot-slot-agg-2>', '<hot-slot-agg-3>'];
function pickAggregateId() {
  return Math.random() < 0.9
    ? HOT[Math.floor(Math.random() * HOT.length)]
    : `agg-${Math.floor(Math.random() * 10000)}`;
}
```

### 부하 프로파일 — 순서대로

1. **smoke** (1 VU, 1분): 파이프라인이 도는지 검증(상한 측정 아님).
2. **breakpoint** (`ramping-arrival-rate`, 계단식): 상한 탐색 — 본편.
3. **soak** (상한의 70~80%, 30분+): lag 누적·메모리 누수 확인.

```js
import http from 'k6/http';

export const options = {
  scenarios: {
    breakpoint: {
      executor: 'ramping-arrival-rate',
      startRate: 10, timeUnit: '1s',
      preAllocatedVUs: 50, maxVUs: 500,
      stages: [
        { target: 50,  duration: '2m' },
        { target: 100, duration: '2m' },
        { target: 200, duration: '2m' },
        { target: 400, duration: '2m' }, // 무너질 때까지 올린다
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],   // 넘으면 상한 초과로 간주
    http_req_duration: ['p(99)<500'], // 잠정 관찰선(SLO 아님)
  },
};
```

- 구성요소: `k6/http`, `options.scenarios`, `k6/metrics`의 `Trend`/`Rate`/`Counter`(gate 대기 등), `check()`·`thresholds`.

### 측정 환경 — 숫자가 의미 있으려면

- Testcontainers 임시 컨테이너에서 재지 않는다 — 배포 유사 환경(리소스 고정)이어야 재현·비교 가능.
- k6 자체가 병목이 되지 않게 — 부하 생성기는 대상과 다른 머신/충분한 리소스.
- 단발은 노이즈 → 3회 이상 반복, warm-up 구간 제외.
- B·C·참고축(§4)을 한 판에 섞지 않는다 — 축별로 따로 측정.

---

## 6. 관련 문서

- [[12-implementation-plan]] — C-6(쓰기 병목 측정)이 실행 시점.
- [[RFC-025]] — 항목 B의 LWW seq 가드·단일 순차 relay 근거.
- [[RFC-030]] — 항목 C의 `ReadFreshnessGate`·`sequenceNo` 계약.
- [[RFC-014]]·[[ADR-016]] — §2 경합 지점·§4 비관 락 근거.
- [[02-write-model]]·[[05-aggregate-design]] — §2 "확인 필요"(경합 aggregate 경계)의 확정 위치.
- [[_triage-summary]] — 닫으려는 두 미해소 항목의 출처.
