#!/usr/bin/env bash
# k6 요청 종료 시점부터, timetable_occupancy의 OCCUPIED 건수가 더 이상 변하지 않을 때까지의
# wall-clock을 측정한다 ("프로세스 완료 시각" / 정착 시간). 동기 구조(Phase 0)에서는 거의 0에 수렴하고,
# 비동기 파이프라인(Phase 1~4 이후)에서는 유의미한 지연이 관찰될 것으로 예상.
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-verysecret}"
DB_NAME="${DB_NAME:-prototype_reservation}"
POLL_INTERVAL="${POLL_INTERVAL:-0.5}"
STABLE_ROUNDS_REQUIRED="${STABLE_ROUNDS_REQUIRED:-4}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-60}"

count_occupied() {
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -N -B "$DB_NAME" -e "
    SELECT COUNT(*) FROM timetable_occupancy o
    JOIN timetable t ON t.id = o.timetable_id
    JOIN restaurant r ON r.id = t.restaurant_id
    WHERE r.name = 'K6_PERF_RESTAURANT' AND o.occupied_status = 'OCCUPIED';
  " 2>/dev/null
}

start_ts=$(date +%s.%N)
prev_count=-1
stable_rounds=0
elapsed=0

while true; do
  current=$(count_occupied)
  if [ "$current" = "$prev_count" ]; then
    stable_rounds=$((stable_rounds + 1))
  else
    stable_rounds=0
  fi
  prev_count="$current"

  now=$(date +%s.%N)
  elapsed=$(echo "$now - $start_ts" | bc)

  if [ "$stable_rounds" -ge "$STABLE_ROUNDS_REQUIRED" ]; then
    break
  fi
  if (( $(echo "$elapsed > $MAX_WAIT_SECONDS" | bc -l) )); then
    echo "WARNING: settle wait exceeded ${MAX_WAIT_SECONDS}s, still counting at ${current}" >&2
    break
  fi
  sleep "$POLL_INTERVAL"
done

printf '{"settleSeconds": %.2f, "finalOccupiedCount": %s}\n' "$elapsed" "$prev_count"
