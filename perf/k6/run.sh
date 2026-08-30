#!/usr/bin/env bash
# VU 레벨별 버스트 측정 러너.
#
#   for VU in 100 300 600 1000 1500 2000:
#     for r in 1..REPEATS:
#       좌석 30석 리셋(seed.sh)  →  VU명이 동시에 1번씩 예약 시도(k6)
#                              →  정착 시간(wait-settle.sh)  →  정합성 검증(오버부킹)
#
# 핵심은 "VU 레벨마다 좌석을 리셋한다"는 것. 좌석을 한 번만 시딩하고 램프를 올리면
# 첫 구간에서 30석이 소진돼 이후 전 구간이 '매진 거절 경로' 측정이 된다.
#
# 사용법:
#   ./run.sh before     # 아키텍처 변경 전
#   ./run.sh after      # 변경 후
#   REPEATS=3 VU_LEVELS="100 600" ./run.sh before     # 빠른 확인
set -euo pipefail

LABEL="${1:-before}"
VU_LEVELS="${VU_LEVELS:-100 300 600 1000 1500 2000 3000}"
REPEATS="${REPEATS:-10}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-verysecret}"
DB_NAME="${DB_NAME:-prototype_reservation}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_ROOT="$REPO_ROOT/performance_test/$LABEL"
RAW_DIR="$OUT_ROOT/raw"
mkdir -p "$RAW_DIR"

# 유저 풀은 최대 VU 레벨만큼 필요하다 (VU 1개 = 유저 1명).
MAX_VU=0
for vu in $VU_LEVELS; do
  [ "$vu" -gt "$MAX_VU" ] && MAX_VU="$vu"
done
export POOL_SIZE="${POOL_SIZE:-$MAX_VU}"

# 고 VU 구간에서 k6가 VU당 커넥션을 연다. fd 한도에 걸리면 서버 포화가 아니라 발생기 한계를
# 측정하게 되므로 넉넉히 올려 둔다 (macOS 기본 soft limit이 낮은 셸에서 실행될 수 있음).
ulimit -n 200000 2>/dev/null || ulimit -n unlimited 2>/dev/null || true
echo " ulimit -n : $(ulimit -n)"

echo "=================================================="
echo " label     : $LABEL"
echo " VU levels : $VU_LEVELS"
echo " repeats   : $REPEATS  (총 $(( $(echo "$VU_LEVELS" | wc -w) * REPEATS ))회 버스트)"
echo " user pool : $POOL_SIZE"
echo " output    : $OUT_ROOT"
echo "=================================================="

for vu in $VU_LEVELS; do
  for r in $(seq 1 "$REPEATS"); do
    prefix="$RAW_DIR/vu$(printf '%04d' "$vu")-r$(printf '%02d' "$r")"

    echo ""
    echo "-------- [$LABEL] VU $vu / repeat $r of $REPEATS --------"
    echo "==> reseeding 좌석 (좌석 리셋 + 새 restaurantId)"
    "$SCRIPT_DIR/seed.sh" > "$prefix-seed.log" 2>&1 || {
      echo "seed 실패 - $prefix-seed.log 확인"; exit 1;
    }

    echo "==> burst"
    # k6는 시스템 환경변수를 __ENV로 넘기지 않는다 - 반드시 -e로 전달해야 한다.
    # (그냥 VUS=... k6 run 하면 스크립트가 기본값 100으로 조용히 돌아간다.)
    # threshold 위반(5xx 발생)으로 k6가 non-zero로 끝나도 그건 '기록해야 할 측정 결과'지
    # 스윕을 중단할 사유가 아니다 - pipefail을 잠시 끄고 exit code만 남긴다.
    set +e
    k6 run -e "VUS=$vu" -e "OUT=$prefix.json" \
      ${REQ_TIMEOUT:+-e "REQ_TIMEOUT=$REQ_TIMEOUT"} \
      "$SCRIPT_DIR/scenarios/booking.js" 2>&1 | tee "$prefix.log"
    k6_status="${PIPESTATUS[0]}"
    set -e
    if [ "$k6_status" -ne 0 ]; then
      echo "    (k6 exit=$k6_status - threshold 위반/실패. 결과는 남기고 계속 진행)"
    fi

    echo "==> settle"
    "$SCRIPT_DIR/lib/wait-settle.sh" > "$prefix-settle.json"
    cat "$prefix-settle.json"

    echo "==> 정합성 (오버부킹)"
    mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
      < "$SCRIPT_DIR/verify-overbooking.sql" > "$prefix-overbooking.txt" 2>/dev/null
    mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -N -B "$DB_NAME" 2>/dev/null <<SQL > "$prefix-integrity.txt"
SELECT
  (SELECT COUNT(*) FROM timetable t JOIN restaurant r ON r.id = t.restaurant_id
    WHERE r.name = 'K6_PERF_RESTAURANT'),
  (SELECT COUNT(*) FROM timetable_occupancy o
    JOIN timetable t ON t.id = o.timetable_id
    JOIN restaurant r ON r.id = t.restaurant_id
    WHERE r.name = 'K6_PERF_RESTAURANT' AND o.occupied_status = 'OCCUPIED'),
  (SELECT COUNT(*) FROM (
      SELECT o.timetable_id FROM timetable_occupancy o
      JOIN timetable t ON t.id = o.timetable_id
      JOIN restaurant r ON r.id = t.restaurant_id
      WHERE r.name = 'K6_PERF_RESTAURANT' AND o.occupied_status = 'OCCUPIED'
      GROUP BY o.timetable_id HAVING COUNT(*) > 1
    ) dup);
SQL
    cat "$prefix-integrity.txt"
  done
done

echo ""
echo "=================================================="
echo "==> 집계"
python3 "$SCRIPT_DIR/lib/aggregate.py" "$OUT_ROOT"
echo "==> 완료. 결과: $OUT_ROOT"
