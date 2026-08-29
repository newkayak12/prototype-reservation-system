#!/usr/bin/env bash
# S1 — 단건 오픈런 러너.
#
#   for N in $CROWDS:            # 동시 도착 인원
#     for i in 1..REPEATS:
#       redis flush → 재시딩(30석) → k6 일제 발사 → settle 대기 → 정합성 검사
#
# 인원을 임의로 고르지 않았다. 이 시스템에서 사용자 경험이 질적으로 바뀌는 경계에
# 맞췄다 (ENVIRONMENT.md 참조):
#     200    tomcat.threads.max      — 여기까지는 전원 즉시 처리
#   8,192    tomcat.max-connections  — 이 위로는 접속 자체가 안 됨
#  16,383    macOS 임시 포트          — 이 위는 서버가 아니라 발생기를 재게 됨
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

# 좌석은 30석 고정. 오픈런은 재고가 희소한 게 정상이고, 군중만 변수로 둔다.
SEATS="${SEATS:-30}"
BG_SEATS="${BG_SEATS:-100}"     # 예열 GET이 훑는 행 수. 조회 비용을 고정한다.

TIMEOUT_SEC="${TIMEOUT_SEC:-60}"
SETTLE_SEC="${SETTLE_SEC:-20}"

# 유저 풀은 최대 군중 이상이어야 한다. 모자라면 토큰이 돌려쓰기 되어
# 같은 사람이 동시에 여러 번 예약하는 게 되고, "몇 명이 좌석을 받았나"가 흐려진다.
POOL_SIZE="${POOL_SIZE:-$(echo "$CROWDS" | tr ' ' '\n' | sort -n | tail -1)}"

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

ulimit -n 200000 2>/dev/null || true

hr
log " S1 단건 오픈런 — label=$LABEL"
log " 동시 인원 : $CROWDS"
log " 좌석      : ${SEATS}석 (고정)"
log " 반복      : $REPEATS (+워밍업 $WARMUP)"
log " 유저 풀   : $POOL_SIZE"
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
    log "-------- [$LABEL] 동시 ${crowd}명 / $tag --------"

    "$PERF_LIB/reset.sh"
    POINTS=1 SLOTS=1 SEATS="$SEATS" BG_SEATS="$BG_SEATS" \
      "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
        log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

    # k6가 도는 동안 인프라 포화도를 기록한다. 이게 없으면 관측된 한계가
    # 앱 한계인지 데이터 면(Docker VM) 한계인지 사후에 증명할 수 없다.
    "$PERF_LIB/infra-sample.sh" start "$prefix-infra.jsonl"

    k6 run \
      -e "CROWD=$crowd" \
      -e "SEATS=$SEATS" \
      -e "TIMEOUT_SEC=$TIMEOUT_SEC" \
      -e "SETTLE_SEC=$SETTLE_SEC" \
      -e "OUT=$prefix.json" \
      "$SCENARIO_DIR/scenario.js" 2>&1 | tee "$prefix.log"
    k6_status="${PIPESTATUS[0]}"

    # exit 99 = threshold 위반 = 일제 발사가 재현되지 않음(스큐 초과 또는 늦은 VU).
    # 결과는 남기되 표시해 둔다. aggregate.py가 usable=false 회차를 중앙값에서 뺀다.
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
