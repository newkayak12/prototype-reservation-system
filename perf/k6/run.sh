#!/usr/bin/env bash
# 동일 시나리오(booking.js)를 10회 반복 실행. 매 회 요약 JSON + 정착 시간(wait-settle.sh)을 기록하고,
# 다음 회를 위해 좌석/유저 데이터를 재시드한다 (매 회 오버부킹 검증이 독립적으로 성립하도록).
set -euo pipefail

SCENARIO="${1:-baseline}"
RUNS="${RUNS:-10}"
START="${START:-1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
mkdir -p "$RESULTS_DIR"

for i in $(seq "$START" "$RUNS"); do
  echo "=================================================="
  echo "==> [$SCENARIO] run $i/$RUNS - reseeding"
  echo "=================================================="
  "$SCRIPT_DIR/seed.sh"

  echo "==> [$SCENARIO] run $i/$RUNS - k6"
  k6 run \
    --summary-export="$RESULTS_DIR/${SCENARIO}-${i}.json" \
    --out "json=$RESULTS_DIR/${SCENARIO}-${i}-raw.json" \
    "$SCRIPT_DIR/scenarios/booking.js" \
    | tee "$RESULTS_DIR/${SCENARIO}-${i}.log"

  echo "==> [$SCENARIO] run $i/$RUNS - VU-stage breakdown"
  python3 "$SCRIPT_DIR/lib/analyze-vu-stages.py" "$RESULTS_DIR/${SCENARIO}-${i}-raw.json" \
    | tee "$RESULTS_DIR/${SCENARIO}-${i}-vu-stages.txt"
  rm -f "$RESULTS_DIR/${SCENARIO}-${i}-raw.json"

  echo "==> [$SCENARIO] run $i/$RUNS - waiting for settle"
  "$SCRIPT_DIR/lib/wait-settle.sh" | tee "$RESULTS_DIR/${SCENARIO}-${i}-settle.json"

  echo "==> [$SCENARIO] run $i/$RUNS - overbooking check"
  mysql -h127.0.0.1 -P3306 -uroot -pverysecret prototype_reservation \
    < "$SCRIPT_DIR/verify-overbooking.sql" | tee "$RESULTS_DIR/${SCENARIO}-${i}-overbooking.txt"
done

echo "==> All $RUNS runs of [$SCENARIO] complete. Results in $RESULTS_DIR"
