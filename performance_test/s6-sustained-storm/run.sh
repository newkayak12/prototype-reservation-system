#!/usr/bin/env bash
# S6 — Sustained Storm 러너.
#
#   PART=sustained   고정 도착률 RATE로 SUSTAIN_SEC초 (Part A — 지속)
#   PART=waves       WAVE_RATE × WAVE_SEC 스파이크 + IDLE_SEC 휴지를 WAVES회 (Part B — 웨이브)
#
#   for i in 1..REPEATS:
#     Redis flush → 재시딩 → k6 → settle 대기 → 정합성 검사
#
#   PART=sustained RATE=<S2 무릎 × 0.8> ./run.sh before
#   PART=waves ./run.sh before
#   PART=waves REPEATS=1 WARMUP=0 ./run.sh probe
#
# Part A는 런 하나가 10분이다. 두 부분을 기본값으로 다 돌리면 라벨당
# (2+1)×약13분 + (3+1)×약9분 ≈ 75분 (시딩 제외). 이 비용은 줄일 수 없다 —
# "10분간 유지되는가"를 5분으로 물을 수는 없기 때문이다.
set -euo pipefail
SCENARIO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCENARIO_DIR/../_lib/common.sh"

LABEL="${1:-before}"
PART="${PART:-sustained}"

RATE="${RATE:-400}"                 # Part A. S2 무릎의 80%를 넣는다.
SUSTAIN_SEC="${SUSTAIN_SEC:-600}"

WAVE_RATE="${WAVE_RATE:-1500}"      # Part B
WAVE_SEC="${WAVE_SEC:-30}"
IDLE_SEC="${IDLE_SEC:-60}"
WAVES="${WAVES:-5}"

TAIL_SEC="${TAIL_SEC:-60}"
POOL_SIZE="${POOL_SIZE:-500}"

case "$PART" in
  sustained|waves) ;;
  *) log "PART은 sustained 또는 waves 여야 한다 (받은 값: $PART)"; exit 1 ;;
esac

# 워밍업 1회는 두 부분 모두 유지한다. Part A의 판정은 "첫 버킷 대비 마지막 버킷"이고
# Part B의 판정은 "웨이브 1 대비 웨이브 5"라, 둘 다 기준점이 런의 맨 앞에 있다.
# 콜드 JIT가 그 기준점을 부풀리면 열화가 실제보다 작아 보인다 — 즉 워밍업을 빼면
# 가설에 유리하게가 아니라 불리하게 편향된다. 워밍업은 라벨당 1회면 되고(앱을
# 재기동하지 않으므로 이후 런은 계속 웜 상태), 그 비용을 반복 횟수로 나눠 갚는다.
#
# 반복은 README대로 지속 2회 / 웨이브 3회. 웨이브가 한 회 더 많은 건 웨이브별 p95가
# 30초 표본이라 지속 부하의 60초 버킷보다 회차 간 분산이 크기 때문이다.
if [ "$PART" = "sustained" ]; then
  REPEATS="${REPEATS:-2}"
else
  REPEATS="${REPEATS:-3}"
fi
WARMUP="${WARMUP:-1}"

# --- 좌석 사전 계산 ----------------------------------------------------------
# 이 시나리오에서 좌석 수는 편의 설정이 아니라 결론을 좌우하는 변수다.
#
# (1) 런 도중 매진되면 그 시점부터 요청이 "빈 좌석 없음" 빠른 실패 경로로 빠진다.
#     임계구간이 갑자기 싸져서 처리량이 튀어 오르는데, 시계열에서 이건 "시스템이
#     회복했다"로 보인다. 10분 런의 후반부가 전반부와 다른 코드 경로를 재게 되므로
#     Part A의 결론이 통째로 무효가 된다. → 최대 소비량보다 크게 잡아야 한다.
#
#     Part A 최대 소비 = RATE × SUSTAIN_SEC        (400 × 600 = 240,000)
#     Part B 최대 소비 = WAVE_RATE × WAVE_SEC × WAVES (1500 × 30 × 5 = 225,000)
#     둘 중 큰 값에 1.5배 여유 → 기본 360,000석.
#     실제로는 직렬 상한(단일 락 키 약 500 req/s)이 성공 건수를 더 낮게 묶으므로
#     이 값은 상한의 상한이다.
#
#     두 부분에 같은 좌석 수를 쓴다. 다르게 잡으면 (2)의 이유로 요청 하나의 비용이
#     달라져서 Part A와 Part B의 지연을 나란히 놓을 수 없게 된다.
#
# (2) 반대 방향의 함정: before의 findBookableTimeTable에는 LIMIT이 없어서 임계구간
#     비용이 잔여 재고에 비례한다(S8). 재고를 크게 잡을수록 요청 하나가 비싸지고,
#     런이 진행될수록 재고가 줄어 요청이 싸진다. "매진 회피"와 "요청 비용 고정"은
#     동시에 만족할 수 없다 — 10분간 용량 근처로 밀면 소비량이 어떤 현실적 재고와도
#     같은 자릿수이기 때문이다.
#
#     기본값에서 Part A는 360,000 → 약 120,000석으로 약 67% 소진된다. 남은 재고가
#     줄면 요청이 싸지므로 이 드리프트는 "시간이 갈수록 나빠진다"는 가설에 불리하게
#     작용한다. 그래서 p99가 그럼에도 우상향하면 결론은 강해지고, 평탄하게 나오면
#     "열화 없음"과 "재고 감소가 열화를 상쇄"를 구분할 수 없다.
#     aggregate.py가 실제 소진율을 정합성 결과에서 뽑아 summary.md에 적는다.
#     소비량을 도착률 × 시간으로 잡으면 안 된다. 그건 서버가 도착률만큼 처리한다는
#     전제인데, 실측 처리량은 도착률과 무관하게 ~62 req/s에서 평평하다(S2). 그 방식으로
#     계산하면 360,000석이 나오고, 임계구간이 3.6초가 되어 처리량이 0.3 req/s로 무너진다.
#     아키텍처가 아니라 그 쿼리 하나를 재게 된다.
#
#     대신 평형점을 푼다. 처리량 = 1000/(8 + 0.01N) req/s 이고 소비량 = 처리량 × T 이므로,
#     "T초 동안 정확히 N석을 소진"하는 N은
#         0.01N² + 8N - 1000T = 0
#     T=600초에서 N ≈ 7,400. 이 값이면 Part A가 10분 내내 할당 경로를 유지한다.
#
#     남은 드리프트는 숨기지 않는다. 재고가 7,400 → 0으로 줄면 요청이 싸져 처리량이
#     12 → 125 req/s로 오른다. 이 방향은 "시간이 갈수록 나빠진다"는 가설에 **불리하게**
#     작용하므로, 그럼에도 p99가 우상향하면 결론이 강해진다. 반대로 평탄하게 나오면
#     "열화 없음"과 "재고 감소가 열화를 상쇄"를 구분할 수 없다 — aggregate.py가 이 한계를
#     summary.md에 명시한다.
SEATS="${SEATS:-7400}"
BG_SEATS="${BG_SEATS:-20}"

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

# 고 도착률 구간에서 k6가 VU당 커넥션을 연다. fd 한도에 걸리면 서버 포화가 아니라
# 발생기 한계를 측정하게 된다.
ulimit -n 200000 2>/dev/null || true

if [ "$PART" = "sustained" ]; then
  KEY="sustained-r$(printf '%05d' "$RATE")"
  RUN_SEC=$SUSTAIN_SEC
  SHAPE="고정 ${RATE} req/s × ${SUSTAIN_SEC}s"
else
  KEY="waves-w$(printf '%05d' "$WAVE_RATE")"
  RUN_SEC=$(( WAVES * (WAVE_SEC + IDLE_SEC) ))
  SHAPE="${WAVE_RATE} req/s × ${WAVE_SEC}s 스파이크 + ${IDLE_SEC}s 휴지, ${WAVES}회"
fi

hr
log " S6 Sustained Storm — label=$LABEL / part=$PART"
log " 부하 모형     : $SHAPE"
log " 런당 관측     : ${RUN_SEC}s (+ 잔여 관측 ${TAIL_SEC}s)"
log " 좌석(고정)    : $SEATS  (10분 지속 시 소진되도록 평형점에서 결정)"
log " 반복          : $REPEATS (+워밍업 $WARMUP)"
log " 출력          : $OUT_ROOT"
hr

"$PERF_LIB/preflight.sh" "$LABEL"
POOL_SIZE="$POOL_SIZE" "$PERF_LIB/users.sh"

for r in $(seq 1 $((WARMUP + REPEATS))); do
  if [ "$r" -le "$WARMUP" ]; then
    prefix="$RAW/warmup-${KEY}-i$(printf '%02d' "$r")"
    tag="워밍업 (집계 제외)"
  else
    idx=$((r - WARMUP))
    prefix="$RAW/${KEY}-i$(printf '%02d' "$idx")"
    tag="$idx / $REPEATS"
  fi

  log ""
  log "-------- [$LABEL] $PART / $tag --------"

  # 회차마다 재시딩한다. 웨이브 사이에는 리셋하지 않지만(잔여 누적이 관측 대상이므로)
  # 회차 사이에는 리셋해야 한다 — 안 그러면 2회차가 1회차의 소진된 재고 위에서
  # 시작해 회차 간 중앙값이 의미를 잃는다.
  flush_redis
  POINTS=1 SEATS="$SEATS" BG_SEATS="$BG_SEATS" "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
    log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

  # k6는 시스템 환경변수를 __ENV로 넘기지 않는다 — 반드시 -e로 전달.
  set +e
  k6 run -e "PART=$PART" -e "RATE=$RATE" -e "SUSTAIN_SEC=$SUSTAIN_SEC" \
    -e "WAVE_RATE=$WAVE_RATE" -e "WAVE_SEC=$WAVE_SEC" -e "IDLE_SEC=$IDLE_SEC" \
    -e "WAVES=$WAVES" -e "TAIL_SEC=$TAIL_SEC" -e "OUT=$prefix.json" \
    "$SCENARIO_DIR/scenario.js" 2>&1 | tee "$prefix.log"
  k6_status="${PIPESTATUS[0]}"
  set -e
  [ "$k6_status" -ne 0 ] && log "    (k6 exit=$k6_status — 결과는 남기고 계속)"

  "$PERF_LIB/wait-settle.sh" > "$prefix-settle.json"
  "$PERF_LIB/integrity.sh"   > "$prefix-integrity.json"
  cat "$prefix-integrity.json"
done

log ""
hr
log "==> 집계"
python3 "$SCENARIO_DIR/aggregate.py" "$OUT_ROOT"
log "==> 완료: $OUT_ROOT"
