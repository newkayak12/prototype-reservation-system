#!/usr/bin/env bash
# S3 — Blast Radius 러너.
#
#   for R in $HOT_RATES:        # 핫스팟 폭격 강도
#     for i in 1..REPEATS:
#       Redis flush → 재시딩 → 배경 GET + 핫스팟 POST 동시 실행
#         → settle 대기 → 정합성 검사
#
#   ./run.sh before
#   HOT_RATES="1500" REPEATS=1 WARMUP=0 ./run.sh probe
set -euo pipefail
SCENARIO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCENARIO_DIR/../_lib/common.sh"

LABEL="${1:-before}"
HOT_RATES="${HOT_RATES:-0 400 1500 3000}"   # 0 = 핫스팟 없는 대조군
BG_RATE="${BG_RATE:-50}"
TOTAL_SEC="${TOTAL_SEC:-120}"
SPIKE_AT="${SPIKE_AT:-30}"
SPIKE_SEC="${SPIKE_SEC:-60}"
REPEATS="${REPEATS:-3}"
WARMUP="${WARMUP:-1}"

# 핫슬롯 좌석. S2/S5와 같은 1,000으로 맞춘다.
#
# 스파이크 도중 매진되면 그 뒤 요청은 빠른 실패 경로로 빠져 워커를 오래 붙잡지 않는다.
# 그래서 폭발 반경은 매진과 함께 줄어들 것으로 예상되는데, 그걸 피하지 않고 그대로 관측한다.
# "재고가 남아 있는 동안만 폭발 반경이 크다"가 참이라면 시계열에서 봉우리가 매진 시점에
# 맞춰 꺼져야 하고, 그건 회피할 게 아니라 이 시나리오가 내놓을 결론이다.
SEATS="${SEATS:-1000}"
BG_SEATS="${BG_SEATS:-100}"   # 배경 GET이 훑는 행 수. 조회 자체 비용을 고정한다.
POOL_SIZE="${POOL_SIZE:-500}"

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

ulimit -n 200000 2>/dev/null || true

hr
log " S3 Blast Radius — label=$LABEL"
log " 배경 GET      : ${BG_RATE} req/s, ${TOTAL_SEC}s 내내"
log " 핫스팟 사다리 : $HOT_RATES (req/s), ${SPIKE_AT}s 부터 ${SPIKE_SEC}s 간"
log " 핫슬롯 좌석   : $SEATS"
log " 반복          : $REPEATS (+워밍업 $WARMUP)"
log " 출력          : $OUT_ROOT"
hr

"$PERF_LIB/preflight.sh" "$LABEL"
POOL_SIZE="$POOL_SIZE" "$PERF_LIB/users.sh"

for rate in $HOT_RATES; do
  for r in $(seq 1 $((WARMUP + REPEATS))); do
    if [ "$r" -le "$WARMUP" ]; then
      prefix="$RAW/warmup-h$(printf '%05d' "$rate")-i$(printf '%02d' "$r")"
      tag="워밍업 (집계 제외)"
    else
      idx=$((r - WARMUP))
      prefix="$RAW/h$(printf '%05d' "$rate")-i$(printf '%02d' "$idx")"
      tag="$idx / $REPEATS"
    fi

    log ""
    log "-------- [$LABEL] 핫스팟 ${rate} req/s / $tag --------"

    flush_redis
    POINTS=1 SEATS="$SEATS" BG_SEATS="$BG_SEATS" "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
      log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

    # HOT_RATE=0 이면 scenario.js가 핫스팟 시나리오 자체를 빼고 배경만 돌린다(대조군).
    set +e
    k6 run -e "HOT_RATE=$rate" -e "SPIKE_SEC=$SPIKE_SEC" \
      -e "BG_RATE=$BG_RATE" -e "TOTAL_SEC=$TOTAL_SEC" -e "SPIKE_AT=$SPIKE_AT" \
      -e "OUT=$prefix.json" \
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
