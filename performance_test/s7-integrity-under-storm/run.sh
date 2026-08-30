#!/usr/bin/env bash
# S7 — Integrity Under Storm 러너.
#
#   for part in $PARTS:
#     for r in 1..REPEATS:
#       Redis flush → 재시딩(새 restaurantId) → 부하 → settle 대기 → 정합성 검사
#
#   PART=extreme ./run.sh before    # 좌석 1석 × VU 3000 × 30회
#   PART=storm   ./run.sh before    # 1500 req/s × 60s × 10회
#   PART=both    ./run.sh before
#   PART=storm STORM_REPEATS=1 WARMUP=0 ./run.sh probe
set -euo pipefail
SCENARIO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCENARIO_DIR/../_lib/common.sh"

LABEL="${1:-before}"
PART="${PART:-extreme}"
WARMUP="${WARMUP:-1}"

# 토큰 풀은 VU 수보다 작아도 된다 — 서버에 사용자당 중복 예약 제한이 없어서
# 토큰을 돌려써도 결과가 달라지지 않는다. 3000명을 로그인시키는 데 드는 수 분을 아낀다.
POOL_SIZE="${POOL_SIZE:-500}"

case "$PART" in
  extreme) PARTS="extreme" ;;
  storm)   PARTS="storm" ;;
  both)    PARTS="extreme storm" ;;
  *) log "PART은 extreme | storm | both 중 하나여야 한다 (받은 값: $PART)"; exit 1 ;;
esac

# 반복 횟수를 S2(3회)보다 훨씬 크게 잡는 이유.
#
# 여기서 찾는 건 평균값이 아니라 "드물게 터지는 레이스"다. 회차당 결함 발생 확률이 p일 때
# n회에서 한 번이라도 관측할 확률은 1-(1-p)^n 이다. p=0.1이면 3회는 27%, 30회는 96%.
# 3회로 "정합성 무결"을 주장하면 p=0.5짜리 결함도 12% 확률로 놓친다 — 주장이 성립하지 않는다.
#
# extreme은 회차당 요청이 3000건뿐이라 회차 수로 표본을 벌어야 한다 → 30회.
# storm은 회차 하나가 이미 9만 요청(1500×60)이라 회차 내 표본이 충분하고, 회차당
# 60초+settle이 붙어 비용이 크다 → 10회. README의 부하 모형과 같은 값이다.
EXTREME_REPEATS="${EXTREME_REPEATS:-30}"
STORM_REPEATS="${STORM_REPEATS:-10}"

OUT_ROOT="$SCENARIO_DIR/$LABEL"
RAW="$OUT_ROOT/raw"
mkdir -p "$RAW"

# VU 3000이면 k6가 커넥션을 그만큼 연다. fd 한도에 걸리면 서버 정합성이 아니라
# 발생기 한계 때문에 요청이 실패하고, 그게 "판정 불가" 요청으로 잡혀 결론이 흐려진다.
ulimit -n 200000 2>/dev/null || true

hr
log " S7 Integrity Under Storm — label=$LABEL"
log " 파트       : $PARTS"
log " extreme    : 좌석 1석 × VU ${VUS:-3000} × ${EXTREME_REPEATS}회"
log " storm      : 좌석 100석 × ${RATE:-1500} req/s × ${DURATION:-60}s × ${STORM_REPEATS}회"
log " 워밍업     : 파트당 $WARMUP (집계 제외)"
log " 출력       : $OUT_ROOT"
hr

"$PERF_LIB/preflight.sh" "$LABEL"
POOL_SIZE="$POOL_SIZE" "$PERF_LIB/users.sh"

for part in $PARTS; do
  if [ "$part" = "extreme" ]; then
    seats="${EXTREME_SEATS:-1}"
    vus="${VUS:-3000}"
    rate=0
    duration=0
    repeats="$EXTREME_REPEATS"
  else
    seats="${STORM_SEATS:-100}"
    vus=0
    rate="${RATE:-1500}"
    duration="${DURATION:-60}"
    repeats="$STORM_REPEATS"
  fi

  for r in $(seq 1 $((WARMUP + repeats))); do
    if [ "$r" -le "$WARMUP" ]; then
      prefix="$RAW/warmup-$part-i$(printf '%03d' "$r")"
      tag="워밍업 (집계 제외)"
    else
      idx=$((r - WARMUP))
      prefix="$RAW/$part-i$(printf '%03d' "$idx")"
      tag="$idx / $repeats"
    fi

    log ""
    log "-------- [$LABEL] $part / $tag --------"

    flush_redis
    POINTS=1 SEATS="$seats" BG_SEATS=20 "$PERF_LIB/seed.sh" > "$prefix-seed.log" 2>&1 || {
      log "seed 실패 — $prefix-seed.log 확인"; exit 1; }

    # k6는 시스템 환경변수를 __ENV로 넘기지 않는다 — 반드시 -e로 전달.
    set +e
    k6 run -e "PART=$part" -e "VUS=$vus" -e "RATE=$rate" -e "DURATION=$duration" \
      -e "SEATS=$seats" -e "OUT=$prefix.json" \
      "$SCENARIO_DIR/scenario.js" 2>&1 | tee "$prefix.log"
    k6_status="${PIPESTATUS[0]}"
    set -e
    [ "$k6_status" -ne 0 ] && log "    (k6 exit=$k6_status — 결과는 남기고 계속)"

    # settle 대기가 이 시나리오에서는 다른 어디보다 결정적이다.
    # after는 비동기라 HTTP 응답 시점의 DB가 최종 상태가 아니다. 응답 직후에 세면
    #   - 아직 안 들어간 행 → "200은 있는데 DB에 없음" = 없는 유령 성공을 만들어내고
    #   - 아직 안 풀린 임시 점유 → "DB가 200보다 많음" = 없는 오버부킹을 만들어낸다
    # 둘 다 결함이 아니라 그냥 지연이다. 반드시 정착 후에 검사한다.
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
