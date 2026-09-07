#!/usr/bin/env bash
# S2 — Arrival Storm 러너.
#
#   for R in $RATES:            # 도착률 사다리
#     for r in 1..REPEATS:
#       Redis flush → 재시딩(새 restaurantId) → 고정 도착률 R로 DURATION초
#         → settle 대기 → 정합성 검사
#
#   ./run.sh before
#   RATES="100 400" REPEATS=1 WARMUP=0 ./run.sh probe
set -euo pipefail
SCENARIO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCENARIO_DIR/../_lib/common.sh"

LABEL="${1:-before}"
RATES="${RATES:-100 200 400 800 1600 3200}"
DURATION="${DURATION:-30}"
REPEATS="${REPEATS:-3}"
WARMUP="${WARMUP:-1}"

# 좌석 수는 사다리 전 칸에서 고정한다. before의 findBookableTimeTable에는 LIMIT이 없어서
# 임계구간이 잔여 재고 N에 비례하기 때문이다(S8). N이 칸마다 다르면 도착률의 효과와
# 재고의 효과가 뒤섞여 사다리가 아무것도 말해주지 못한다.
#
# 값을 1,000으로 잡은 근거 (좌석만 바꾼 실측, 목표 200 req/s / 10초):
#   좌석    100 → 달성 200 req/s, 할당  10 req/s, p95   723ms  (즉시 매진)
#   좌석  1,000 → 달성  91 req/s, 할당  61 req/s, p95  9,393ms
#   좌석  3,000 → 달성  31 req/s, 할당  26 req/s, p95 28,797ms (미매진)
#   → 임계구간 ≈ 8ms + 0.010ms × N
#
# 100석은 즉시 매진돼 사다리 전체가 "빠른 실패 경로 처리량"만 재게 되고,
# 20,000석은 임계구간이 200ms를 넘어 아키텍처가 아니라 그 쿼리 하나를 재게 된다.
# 1,000석은 고정비용과 재고비용이 비슷한 지점이라, 락 전략의 차이가 드러난다.
#
# 매진 자체는 회피하지 않는다. 굿즈 드롭은 원래 매진되고, scenario.js가 매진 전(할당)과
# 매진 후(거절) 처리량을 나눠서 낸다.
SEATS="${SEATS:-1000}"
POOL_SIZE="${POOL_SIZE:-500}"

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

# 고 도착률 구간에서 k6가 VU당 커넥션을 연다. fd 한도에 걸리면 서버 포화가 아니라
# 발생기 한계를 측정하게 된다.
ulimit -n 200000 2>/dev/null || true

hr
log " S2 Arrival Storm — label=$LABEL"
log " 도착률 사다리 : $RATES (req/s)"
log " 칸당 지속     : ${DURATION}s"
log " 좌석(고정)    : $SEATS"
log " 반복          : $REPEATS (+워밍업 $WARMUP)"
log " 출력          : $OUT_ROOT"
hr

"$PERF_LIB/preflight.sh" "$LABEL"
POOL_SIZE="$POOL_SIZE" "$PERF_LIB/users.sh"

for rate in $RATES; do
  for r in $(seq 1 $((WARMUP + REPEATS))); do
    if [ "$r" -le "$WARMUP" ]; then
      prefix="$RAW/warmup-r$(printf '%05d' "$rate")-i$(printf '%02d' "$r")"
      tag="워밍업 (집계 제외)"
    else
      idx=$((r - WARMUP))
      prefix="$RAW/r$(printf '%05d' "$rate")-i$(printf '%02d' "$idx")"
      tag="$idx / $REPEATS"
    fi

    log ""
    log "-------- [$LABEL] ${rate} req/s / $tag --------"

    flush_redis
    POINTS=1 SEATS="$SEATS" BG_SEATS=20 "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
      log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

    # k6는 시스템 환경변수를 __ENV로 넘기지 않는다 — 반드시 -e로 전달.
    set +e
    k6 run -e "RATE=$rate" -e "DURATION=$DURATION" -e "OUT=$prefix.json" \
      "$SCENARIO_DIR/scenario.js" 2>&1 | tee "$prefix.log"
    k6_status="${PIPESTATUS[0]}"
    set -e
    [ "$k6_status" -ne 0 ] && log "    (k6 exit=$k6_status — 결과는 남기고 계속)"

    "$PERF_LIB/wait-settle.sh" > "$prefix-settle.json"
    "$PERF_LIB/integrity.sh"   > "$prefix-integrity.json"
    cat "$prefix-integrity.json"
  done
done

log ""
hr
log "==> 집계"
python3 "$SCENARIO_DIR/aggregate.py" "$OUT_ROOT"
log "==> 완료: $OUT_ROOT"
