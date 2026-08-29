#!/usr/bin/env bash
# HTTP 응답이 모두 끝난 시점부터, DB 점유 건수가 더 이상 변하지 않을 때까지의 wall-clock.
#
# before는 동기 구조라 거의 0에 수렴한다. after는 비동기 파이프라인이라 유의미한 지연이 난다.
# 정합성 검사를 이 대기 전에 하면 after가 부당하게 유리해 보인다 (아직 DB에 안 들어간 걸
# "오버부킹 없음"으로 세게 된다).
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

POLL_INTERVAL="${SETTLE_POLL_INTERVAL:-0.5}"
STABLE_ROUNDS_REQUIRED="${STABLE_ROUNDS_REQUIRED:-4}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-60}"

if mysql_q -e "SHOW COLUMNS FROM timetable_occupancy LIKE 'released_at';" | grep -q released_at; then
  ALIVE="o.released_at IS NULL"
else
  ALIVE="o.occupied_status = 'OCCUPIED'"
fi

count_occupied() {
  mysql_q -e "
    SELECT COUNT(*) FROM timetable_occupancy o
    JOIN timetable t ON t.id = o.timetable_id
    JOIN restaurant r ON r.id = t.restaurant_id
    WHERE r.name LIKE '${HOT_PREFIX}%' AND ${ALIVE};"
}

start_ts=$(python3 -c 'import time;print(time.time())')
prev=-1
stable=0

while true; do
  current="$(count_occupied)"
  if [ "$current" = "$prev" ]; then stable=$((stable + 1)); else stable=0; fi
  prev="$current"

  elapsed=$(python3 -c "import time;print(round(time.time()-$start_ts,2))")
  [ "$stable" -ge "$STABLE_ROUNDS_REQUIRED" ] && break
  if python3 -c "import sys;sys.exit(0 if $elapsed > $MAX_WAIT_SECONDS else 1)"; then
    echo "WARNING: settle ${MAX_WAIT_SECONDS}s 초과, 현재 $current" >&2
    break
  fi
  sleep "$POLL_INTERVAL"
done

printf '{"settleSeconds": %s, "finalOccupiedCount": %s}\n' "$elapsed" "$prev"
