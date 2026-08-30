#!/usr/bin/env bash
# 다중 경합 지점 스윕: VU를 고정하고 **경합 지점 수**를 축으로 올린다.
#
#   for POINTS in 1 10 50 200:
#     for r in 1..REPEATS:
#       음식점/슬롯/좌석 리셋(seed-multi.sh)  →  VU명이 지점에 균등 분산돼 1번씩 예약 시도
#                                            →  정착  →  정합성 검증(지점별 오버부킹)
#
# 단일 슬롯 스윕(run.sh)이 VU를 축으로 했다면 여기는 지점 수가 축이다. 단일 슬롯에서는
# before가 이겼는데, 그 조건이 before에게 가장 유리했기 때문이다 - 슬롯이 하나면 동시에
# 열린 트랜잭션이 항상 1개다. 지점이 늘 때 무엇이 먼저 무너지는지가 이 스윕의 질문이다.
#
# 사용법:
#   ./run-multi.sh before      # SKIP_QUEUE=1로 자동 설정된다
#   ./run-multi.sh after
#   POINT_LEVELS="1 10" REPEATS=3 ./run-multi.sh after
set -euo pipefail

LABEL="${1:-after}"
# 경합 지점 수. 음식점 수 × 슬롯 수로 만든다 (아래 split_points 참고).
POINT_LEVELS="${POINT_LEVELS:-1 10 50 200}"
REPEATS="${REPEATS:-5}"
VUS="${VUS:-3000}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-verysecret}"
DB_NAME="${DB_NAME:-prototype_reservation}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_ROOT="$REPO_ROOT/performance_test/multi-slot/$LABEL"
RAW_DIR="$OUT_ROOT/raw"
mkdir -p "$RAW_DIR"

export POOL_SIZE="${POOL_SIZE:-$VUS}"

# before 아키텍처에는 대기열이 없다. 라벨로 자동 판단하되 명시 지정이 우선한다.
if [ -z "${SKIP_QUEUE:-}" ]; then
  case "$LABEL" in
    before*) SKIP_QUEUE=1 ;;
    *) SKIP_QUEUE=0 ;;
  esac
fi
export SKIP_QUEUE

ulimit -n 200000 2>/dev/null || ulimit -n unlimited 2>/dev/null || true

# 지점 수를 음식점 × 슬롯로 쪼갠다. 한 음식점에 슬롯을 몰아넣으면 restaurantId가 하나뿐이라
# 음식점 단위 경합(예: 음식점별 캐시/인덱스)이 시야에서 사라진다. 슬롯은 최대 5개까지만 쓰고
# 나머지는 음식점 수로 늘린다 - 슬롯 시각이 18시부터 1시간씩이라 무한정 늘릴 수 없다.
split_points() {
  local total="$1"
  if [ "$total" -le 5 ]; then echo "$total 1"; else echo "$(( (total + 4) / 5 )) 5"; fi
}

echo "=================================================="
echo " label        : $LABEL   (SKIP_QUEUE=$SKIP_QUEUE)"
echo " VUs (고정)    : $VUS"
echo " 경합 지점    : $POINT_LEVELS"
echo " repeats      : $REPEATS"
echo " user pool    : $POOL_SIZE"
echo " output       : $OUT_ROOT"
echo "=================================================="

for points in $POINT_LEVELS; do
  read -r r_count s_count <<< "$(split_points "$points")"
  actual=$(( r_count * s_count ))

  for r in $(seq 1 "$REPEATS"); do
    prefix="$RAW_DIR/pt$(printf '%04d' "$actual")-r$(printf '%02d' "$r")"

    echo ""
    echo "-------- [$LABEL] 경합지점 $actual (음식점 $r_count x 슬롯 $s_count) / repeat $r --------"
    echo "==> reseeding"
    RESTAURANT_COUNT="$r_count" SLOT_COUNT="$s_count" POOL_SIZE="$POOL_SIZE" \
      "$SCRIPT_DIR/seed-multi.sh" > "$prefix-seed.log" 2>&1 || {
      echo "seed 실패 - $prefix-seed.log 확인"; exit 1;
    }

    echo "==> burst"
    set +e
    k6 run -e "VUS=$VUS" -e "OUT=$prefix.json" -e "SKIP_QUEUE=$SKIP_QUEUE" \
      ${REQ_TIMEOUT:+-e "REQ_TIMEOUT=$REQ_TIMEOUT"} \
      ${QUEUE_WAIT_BUDGET_MS:+-e "QUEUE_WAIT_BUDGET_MS=$QUEUE_WAIT_BUDGET_MS"} \
      "$SCRIPT_DIR/scenarios/booking-multi.js" 2>&1 | tee "$prefix.log"
    k6_status="${PIPESTATUS[0]}"
    set -e
    [ "$k6_status" -ne 0 ] && echo "    (k6 exit=$k6_status - 결과는 남기고 계속)"

    echo "==> settle"
    "$SCRIPT_DIR/lib/wait-settle.sh" > "$prefix-settle.json"
    cat "$prefix-settle.json"

    # 정합성: 지점이 여러 개이므로 "총 좌석 / 총 점유"만 보면 한 슬롯의 오버부킹이 다른
    # 슬롯의 미판매에 가려진다. 슬롯 단위로 초과분을 세야 한다.
    echo "==> 정합성 (지점별 오버부킹)"
    mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -N -B "$DB_NAME" 2>/dev/null <<SQL > "$prefix-integrity.txt"
SELECT
  (SELECT COUNT(*) FROM timetable t JOIN restaurant r ON r.id = t.restaurant_id
    WHERE r.name = 'K6_PERF_RESTAURANT'),
  (SELECT COUNT(*) FROM timetable_occupancy o
    JOIN timetable t ON t.id = o.timetable_id
    JOIN restaurant r ON r.id = t.restaurant_id
    WHERE r.name = 'K6_PERF_RESTAURANT' AND o.released_at IS NULL),
  (SELECT COUNT(*) FROM (
      SELECT o.timetable_id FROM timetable_occupancy o
      JOIN timetable t ON t.id = o.timetable_id
      JOIN restaurant r ON r.id = t.restaurant_id
      WHERE r.name = 'K6_PERF_RESTAURANT' AND o.released_at IS NULL
      GROUP BY o.timetable_id HAVING COUNT(*) > 1
    ) dup),
  (SELECT COUNT(*) FROM (
      SELECT t.restaurant_id, t.start_time, COUNT(*) AS occupied, MAX(seats.total) AS total
      FROM timetable_occupancy o
      JOIN timetable t ON t.id = o.timetable_id
      JOIN restaurant r ON r.id = t.restaurant_id
      JOIN (SELECT restaurant_id, start_time, COUNT(*) AS total FROM timetable
            GROUP BY restaurant_id, start_time) seats
        ON seats.restaurant_id = t.restaurant_id AND seats.start_time = t.start_time
      WHERE r.name = 'K6_PERF_RESTAURANT' AND o.released_at IS NULL
      GROUP BY t.restaurant_id, t.start_time
      HAVING occupied > total
    ) over_slots);
SQL
    cat "$prefix-integrity.txt"
  done
done

echo ""
echo "=================================================="
echo "==> 집계"
python3 "$SCRIPT_DIR/lib/aggregate-multi.py" "$OUT_ROOT"
echo "==> 완료. 결과: $OUT_ROOT"
