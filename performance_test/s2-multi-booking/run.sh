#!/usr/bin/env bash
# S2 — 다건 오픈런 러너.
#
# S1과 같은 인원 사다리를 쓴다. 두 시나리오를 나란히 읽으려면 인원 축이 같아야 한다.
# 다른 건 시딩뿐이다: SLOTS=3 (18/19/20시) × 30석 = 90석.
#
#   ./run.sh before
#   CROWDS="1000" REPEATS=1 WARMUP=0 ./run.sh probe
set -uo pipefail
SCENARIO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCENARIO_DIR/../_lib/common.sh"

LABEL="${1:-before}"
CROWDS="${CROWDS:-200 1000 3000 8000 10000}"
REPEATS="${REPEATS:-5}"
WARMUP="${WARMUP:-1}"

SLOTS="${SLOTS:-3}"
SEATS="${SEATS:-30}"          # 슬롯당. 총 90석
BG_SEATS="${BG_SEATS:-100}"

TIMEOUT_SEC="${TIMEOUT_SEC:-60}"
SETTLE_SEC="${SETTLE_SEC:-20}"

POOL_SIZE="${POOL_SIZE:-$(echo "$CROWDS" | tr ' ' '\n' | sort -n | tail -1)}"

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

ulimit -n 200000 2>/dev/null || true

hr
log " S2 다건 오픈런 — label=$LABEL"
log " 동시 인원 : $CROWDS  (각자 ${SLOTS}슬롯 동시 요청)"
log " 좌석      : ${SLOTS}슬롯 × ${SEATS}석 = $((SLOTS * SEATS))석"
log " 반복      : $REPEATS (+워밍업 $WARMUP)"
log " 출력      : $OUT_ROOT"
hr

"$PERF_LIB/preflight.sh" "$LABEL" || exit 1
POOL_SIZE="$POOL_SIZE" "$PERF_LIB/users.sh" || exit 1

for crowd in $CROWDS; do
  for r in $(seq 1 $((WARMUP + REPEATS))); do
    if [ "$r" -le "$WARMUP" ]; then
      prefix="$RAW/warmup-n$(printf '%05d' "$crowd")-i$(printf '%02d' "$r")"
      tag="워밍업 (집계 제외)"
    else
      idx=$((r - WARMUP))
      prefix="$RAW/n$(printf '%05d' "$crowd")-i$(printf '%02d' "$idx")"
      tag="$idx / $REPEATS"
    fi

    log ""
    log "-------- [$LABEL] 동시 ${crowd}명 × ${SLOTS}슬롯 / $tag --------"

    "$PERF_LIB/reset.sh"
    POINTS=1 SLOTS="$SLOTS" SEATS="$SEATS" BG_SEATS="$BG_SEATS" \
      "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
        log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

    "$PERF_LIB/infra-sample.sh" start "$prefix-infra.jsonl"

    k6 run \
      -e "CROWD=$crowd" \
      -e "TIMEOUT_SEC=$TIMEOUT_SEC" \
      -e "SETTLE_SEC=$SETTLE_SEC" \
      -e "OUT=$prefix.json" \
      "$SCENARIO_DIR/scenario.js" 2>&1 | tee "$prefix.log"
    k6_status="${PIPESTATUS[0]}"

    if [ "$k6_status" -eq 99 ]; then
      log "    ★ 유효성 threshold 위반 — 이 회차는 집계에서 제외된다"
    elif [ "$k6_status" -ne 0 ]; then
      log "    (k6 exit=$k6_status — 결과는 남기고 계속)"
    fi

    "$PERF_LIB/infra-sample.sh" stop "$prefix-infra.jsonl" > "$prefix-infra.json"
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
