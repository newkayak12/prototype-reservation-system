#!/usr/bin/env bash
# S4 — Retry Storm 러너.
#
#   for P in $POLICIES:         # 재시도 정책 (독립변수)
#     for i in 1..REPEATS:
#       Redis flush → 재시딩(새 restaurantId) → 사용자 도착률 RATE로 DURATION초
#         → settle 대기 → 정합성 검사
#
#   RATE=750 ./run.sh before
#   RATE=750 POLICIES="NO_RETRY RETRY_FOREVER" REPEATS=1 WARMUP=0 ./run.sh probe
set -euo pipefail
SCENARIO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCENARIO_DIR/../_lib/common.sh"

LABEL="${1:-before}"

# RATE에 기본값을 주지 않는다. 나선은 용량 초과 상태에서만 발생하므로, knee를 모르는 채
# 임의의 도착률로 돌리면 "재시도해도 아무 일 없다"는 결과가 나와도 그게 반증인지
# 부하가 모자랐던 건지 구분할 수 없다. S2 없이는 이 실험이 성립하지 않는다.
RATE="${RATE:?RATE 를 지정할 것 — S2 knee × 1.5 (README 선행 조건). 예: RATE=750 ./run.sh before}"

POLICIES="${POLICIES:-NO_RETRY RETRY_ONCE RETRY_FOREVER}"
DURATION="${DURATION:-120}"
PATIENCE_SEC="${PATIENCE_SEC:-3}"
RETRY_CAP="${RETRY_CAP:-10}"
REPEATS="${REPEATS:-3}"
WARMUP="${WARMUP:-1}"

# 좌석은 S2/S3/S5와 같은 1,000으로 고정한다. 시나리오끼리 숫자를 나란히 읽으려면
# 재고가 같아야 하기 때문이다.
#
# "매진을 피하려고 좌석을 크게 잡는다"는 접근은 쓰지 않는다. before의
# findBookableTimeTable에는 LIMIT이 없어서 임계구간이 잔여 재고 N에 비례하고
# (실측: 8ms + 0.010ms × N), 100,000석이면 임계구간이 1초를 넘어 처리량이 1 req/s로
# 무너진다. 그러면 재시도 정책의 효과가 아니라 그 쿼리 하나를 재게 된다.
#
# 매진은 그대로 받아들이고, scenario.js가 매진 전(할당)과 매진 후(거절) 국면을
# 나눠서 낸다. 정책 비교는 매진 전 구간에서 한다 — 매진 후에는 어떤 정책이든
# 빠른 실패라 차이가 안 난다.
SEATS="${SEATS:-1000}"
POOL_SIZE="${POOL_SIZE:-500}"

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

# 정책 C는 이터레이션 하나가 최대 RETRY_CAP × PATIENCE_SEC 초를 산다. 그만큼 VU와
# 커넥션이 동시에 살아 있으므로 fd 한도에 먼저 걸리면 서버가 아니라 발생기를 재게 된다.
ulimit -n 200000 2>/dev/null || true

hr
log " S4 Retry Storm — label=$LABEL"
log " 재시도 정책   : $POLICIES"
log " 사용자 도착률 : ${RATE} user/s (S2 knee × 1.5)"
log " 지속          : ${DURATION}s"
log " 인내 한계     : ${PATIENCE_SEC}s (클라이언트 타임아웃 = F5 시점)"
log " 무한 재시도 상한: ${RETRY_CAP}회"
log " 좌석(고정)    : $SEATS"
log " 반복          : $REPEATS (+워밍업 $WARMUP)"
log " 출력          : $OUT_ROOT"
hr

"$PERF_LIB/preflight.sh" "$LABEL"
POOL_SIZE="$POOL_SIZE" "$PERF_LIB/users.sh"

for policy in $POLICIES; do
  for r in $(seq 1 $((WARMUP + REPEATS))); do
    if [ "$r" -le "$WARMUP" ]; then
      prefix="$RAW/warmup-p${policy}-i$(printf '%02d' "$r")"
      tag="워밍업 (집계 제외)"
    else
      idx=$((r - WARMUP))
      prefix="$RAW/p${policy}-i$(printf '%02d' "$idx")"
      tag="$idx / $REPEATS"
    fi

    log ""
    log "-------- [$LABEL] ${policy} / $tag --------"

    flush_redis
    POINTS=1 SEATS="$SEATS" BG_SEATS=20 "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
      log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

    # k6는 시스템 환경변수를 __ENV로 넘기지 않는다 — 반드시 -e로 전달.
    set +e
    k6 run -e "POLICY=$policy" -e "RATE=$RATE" -e "DURATION=$DURATION" \
      -e "PATIENCE_SEC=$PATIENCE_SEC" -e "RETRY_CAP=$RETRY_CAP" -e "OUT=$prefix.json" \
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
