#!/usr/bin/env bash
# Phase 0 perf-test seed: restaurant + limited-seat timetable slot (raw SQL) + real sign-up users (real HTTP, no auth bypass).
# Idempotent: re-running wipes prior K6_PERF_RESTAURANT data and reseeds fresh (seat count reset).
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-verysecret}"
DB_NAME="${DB_NAME:-prototype_reservation}"
BASE_URL="${BASE_URL:-http://localhost:8081}"
SEAT_COUNT="${SEAT_COUNT:-30}"
POOL_SIZE="${POOL_SIZE:-300}"
RESTAURANT_NAME="K6_PERF_RESTAURANT"
PASSWORD="K6perf!2026"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$SCRIPT_DIR/lib"
mkdir -p "$LIB_DIR"

MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 -N -B)

uuid() { uuidgen | tr '[:upper:]' '[:lower:]'; }

RESTAURANT_ID="$(uuid)"
BOOKING_DATE="$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d tomorrow +%Y-%m-%d)"
BOOKING_DAY="$(date -v+1d +%A 2>/dev/null || date -d tomorrow +%A)"
BOOKING_DAY="$(echo "$BOOKING_DAY" | tr '[:lower:]' '[:upper:]')"
START_TIME="18:00:00"
END_TIME="19:00:00"

echo "==> Wiping previous K6 perf-test data (idempotent reseed)"
"${MYSQL[@]}" "$DB_NAME" <<SQL
DELETE FROM timetable_occupancy WHERE timetable_id IN (
  SELECT id FROM timetable WHERE restaurant_id IN (
    SELECT id FROM restaurant WHERE name = '$RESTAURANT_NAME'
  )
);
DELETE FROM timetable WHERE restaurant_id IN (
  SELECT id FROM restaurant WHERE name = '$RESTAURANT_NAME'
);
DELETE FROM restaurant WHERE name = '$RESTAURANT_NAME';
SQL

echo "==> Seeding restaurant $RESTAURANT_ID + $SEAT_COUNT timetable seats on $BOOKING_DATE $BOOKING_DAY $START_TIME"
{
  echo "INSERT INTO restaurant (id, name, phone, zip_code, address, detail, is_deleted) VALUES"
  echo "  ('$RESTAURANT_ID', '$RESTAURANT_NAME', '0212345678', '06236', 'Seoul', 'K6 load test fixture', 0);"
  echo "INSERT INTO timetable (id, restaurant_id, date, day, start_time, end_time, table_number, table_size, table_status, time_table_confirm_status, version) VALUES"
  for i in $(seq 1 "$SEAT_COUNT"); do
    tid="$(uuid)"
    sep=","
    if [ "$i" -eq "$SEAT_COUNT" ]; then sep=";"; fi
    echo "  ('$tid', '$RESTAURANT_ID', '$BOOKING_DATE', '$BOOKING_DAY', '$START_TIME', '$END_TIME', $i, 4, 'EMPTY', 'NOT_CONFIRMED', 0)$sep"
  done
} > "$LIB_DIR/seed.generated.sql"

"${MYSQL[@]}" "$DB_NAME" < "$LIB_DIR/seed.generated.sql"

cat > "$LIB_DIR/env.json" <<JSON
{
  "baseUrl": "$BASE_URL",
  "restaurantId": "$RESTAURANT_ID",
  "date": "$BOOKING_DATE",
  "day": "$BOOKING_DAY",
  "startTime": "$START_TIME",
  "endTime": "$END_TIME",
  "seatCount": $SEAT_COUNT,
  "poolSize": $POOL_SIZE
}
JSON

echo "==> Sign-up $POOL_SIZE real users via $BASE_URL/api/v1/user/sign-up (no auth bypass)"
signup_one() {
  local i="$1"
  local n
  n="$(printf '%03d' "$i")"
  local login_id="k6perf$n"
  local email="k6perf$n@test.local"
  local mobile="010$(printf '%08d' "$i")"
  local nickname="k6perf$n"
  curl -s -o /dev/null -w '' -X POST "$BASE_URL/api/v1/user/sign-up" \
    -H 'Content-Type: application/json' \
    -d "{\"loginId\":\"$login_id\",\"password\":\"$PASSWORD\",\"email\":\"$email\",\"mobile\":\"$mobile\",\"nickname\":\"$nickname\"}" \
    || true
  echo "{\"loginId\":\"$login_id\",\"password\":\"$PASSWORD\"}"
}
export -f signup_one
export BASE_URL PASSWORD

seq 1 "$POOL_SIZE" | xargs -P 20 -I{} bash -c 'signup_one "$@"' _ {} > "$LIB_DIR/users.ndjson"

python3 -c "
import json
users = []
with open('$LIB_DIR/users.ndjson') as f:
    for line in f:
        line = line.strip()
        if line:
            users.append(json.loads(line))
users.sort(key=lambda u: u['loginId'])
with open('$LIB_DIR/users.json', 'w') as f:
    json.dump(users, f, indent=2)
print(f'{len(users)} users written to users.json')
"
rm -f "$LIB_DIR/users.ndjson"

echo "==> Done. env.json + users.json ready in $LIB_DIR"
