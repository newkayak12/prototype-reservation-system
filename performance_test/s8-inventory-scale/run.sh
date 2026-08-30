#!/usr/bin/env bash
# S8 — Inventory Scale 러너.
#
#   for N in $SEAT_LEVELS:
#     for r in 1..REPEATS:
#       Redis flush → 좌석 N석 재시딩(새 restaurantId) → VU명이 동시에 1번씩
#         → settle 대기 → 정합성 검사
#
#   ./run.sh before
#   SEAT_LEVELS="10 1000" REPEATS=2 ./run.sh probe
set -euo pipefail
SCENARIO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCENARIO_DIR/../_lib/common.sh"

LABEL="${1:-before}"
# 좌석 N은 반드시 VUS 이상이어야 한다 (아래 VUS 주석 참고).
SEAT_LEVELS="${SEAT_LEVELS:-100 300 1000 3000 10000 30000}"
REPEATS="${REPEATS:-5}"
# VU를 100으로 낮게 잡는 이유: 이 시나리오는 "무너지는가"가 아니라 "임계구간이 얼마인가"를
# 잰다. VU가 좌석 N보다 많으면 매진이 나서 추정량이 무효가 되고(전원 성공이 아니게 됨),
# VU가 많을수록 런 중 재고 소진량도 커져 "N이 고정"이라는 전제가 흔들린다.
# VU 100이면 소진량이 최대 100석이라 N>=1000 구간에서 재고 변화가 10% 이내다.
VUS="${VUS:-100}"
WARMUP="${WARMUP:-1}"     # 레벨마다 집계에서 제외하는 워밍업 런 수

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

# 고 VU 구간에서 k6가 VU당 커넥션을 연다. fd 한도에 걸리면 서버 포화가 아니라
# 발생기 한계를 측정하게 되므로 넉넉히 올린다.
ulimit -n 200000 2>/dev/null || true

hr
log " S8 Inventory Scale — label=$LABEL"
log " 좌석 레벨 : $SEAT_LEVELS"
log " VU        : $VUS"
log " 반복      : $REPEATS (+워밍업 $WARMUP)"
log " 출력      : $OUT_ROOT"
hr

"$PERF_LIB/preflight.sh" "$LABEL"

POOL_SIZE="$VUS" "$PERF_LIB/users.sh"

for seats in $SEAT_LEVELS; do
  for r in $(seq 1 $((WARMUP + REPEATS))); do
    if [ "$r" -le "$WARMUP" ]; then
      prefix="$RAW/warmup-n$(printf '%06d' "$seats")-r$(printf '%02d' "$r")"
      tag="워밍업 (집계 제외)"
    else
      idx=$((r - WARMUP))
      prefix="$RAW/n$(printf '%06d' "$seats")-r$(printf '%02d' "$idx")"
      tag="$idx / $REPEATS"
    fi

    log ""
    log "-------- [$LABEL] 좌석 $seats / $tag --------"

    flush_redis
    POINTS=1 SEATS="$seats" BG_SEATS=20 "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
      log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

    # k6는 시스템 환경변수를 __ENV로 넘기지 않는다 — 반드시 -e로 전달해야 한다.
    # threshold 위반으로 non-zero 종료해도 그건 기록해야 할 결과지 스윕 중단 사유가 아니다.
    set +e
    k6 run -e "VUS=$VUS" -e "SEATS=$seats" -e "OUT=$prefix.json" \
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
