#!/usr/bin/env bash
# S5 — Hotspot Skew 러너.
#
#   for K in $POINT_LEVELS:     # 경합점(분산락 키) 수
#     for i in 1..REPEATS:
#       Redis flush → 경합점 K개 재시딩 → 총 도착률 고정으로 DURATION초
#         → settle 대기 → 정합성 검사
#
#   ./run.sh before
#   POINT_LEVELS="1 20" REPEATS=1 WARMUP=0 ./run.sh probe
set -euo pipefail
SCENARIO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCENARIO_DIR/../_lib/common.sh"

LABEL="${1:-before}"
POINT_LEVELS="${POINT_LEVELS:-1 5 20 100}"
RATE="${RATE:-1500}"              # 총 도착률. K와 무관하게 고정한다.
DURATION="${DURATION:-30}"
REPEATS="${REPEATS:-3}"
WARMUP="${WARMUP:-1}"

# 경합점당 좌석. 전 레벨에서 "경합점당" 재고를 고정해야 임계구간 비용이 같아진다.
# 총 좌석은 K에 비례해 늘지만, 락 키 하나가 보는 재고는 항상 SEATS_PER_POINT다.
# S2와 같은 1,000으로 맞춰 두 시나리오의 숫자를 나란히 읽을 수 있게 한다.
# (재고 민감도 자체는 S8이 따로 잰다 — 임계구간 ≈ 8ms + 0.010ms × N)
SEATS_PER_POINT="${SEATS_PER_POINT:-1000}"
POOL_SIZE="${POOL_SIZE:-500}"

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

ulimit -n 200000 2>/dev/null || true

hr
log " S5 Hotspot Skew — label=$LABEL"
log " 경합점 레벨   : $POINT_LEVELS"
log " 총 도착률     : ${RATE} req/s (고정)"
log " 지속          : ${DURATION}s"
log " 경합점당 좌석 : $SEATS_PER_POINT"
log " 반복          : $REPEATS (+워밍업 $WARMUP)"
log " 출력          : $OUT_ROOT"
hr

"$PERF_LIB/preflight.sh" "$LABEL"
POOL_SIZE="$POOL_SIZE" "$PERF_LIB/users.sh"

for k in $POINT_LEVELS; do
  # 시딩 규모 경고: K=100 × 20,000석 = 2,000,000행. 시딩이 오래 걸린다.
  total=$((k * SEATS_PER_POINT))
  [ "$total" -gt 500000 ] && log "  (주의: 이 레벨은 ${total}행을 시딩한다 — 시간이 걸린다)"

  for r in $(seq 1 $((WARMUP + REPEATS))); do
    if [ "$r" -le "$WARMUP" ]; then
      prefix="$RAW/warmup-k$(printf '%04d' "$k")-i$(printf '%02d' "$r")"
      tag="워밍업 (집계 제외)"
    else
      idx=$((r - WARMUP))
      prefix="$RAW/k$(printf '%04d' "$k")-i$(printf '%02d' "$idx")"
      tag="$idx / $REPEATS"
    fi

    log ""
    log "-------- [$LABEL] 경합점 $k / $tag --------"

    flush_redis
    POINTS="$k" SEATS="$SEATS_PER_POINT" BG_SEATS=20 "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
      log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

    set +e
    k6 run -e "RATE=$RATE" -e "DURATION=$DURATION" -e "OUT=$prefix.json" \
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
