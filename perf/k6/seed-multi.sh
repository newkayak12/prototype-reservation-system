#!/usr/bin/env bash
# 다중 경합 지점 시딩: 음식점 R개 × 슬롯 S개, 각 슬롯마다 좌석 SEAT_COUNT석.
#
# 단일 슬롯 시딩(seed.sh)과 나누어 둔 이유:
#   단일 슬롯 측정은 이미 끝났고(docs/perf/single-slot-burst.md) 그 하네스는 재현을 위해
#   손대지 않는다. 여기는 "경합 지점 수"를 축으로 추가한 별도 시나리오다.
#
# 왜 경합 지점 수가 축인가:
#   before는 슬롯당 분산락 + `@Transactional`이라, 경합 지점이 늘면 **동시에 열린 트랜잭션
#   수**가 그만큼 늘고 커넥션 풀을 잠식한다. after는 요청 경로에 DB 트랜잭션이 없다.
#   단일 슬롯에서는 이 차이가 드러날 수 없다 - 동시 트랜잭션이 항상 1개이기 때문이다.
#
# 사용법:
#   RESTAURANT_COUNT=10 SLOT_COUNT=1 ./seed-multi.sh    # 경합 지점 10개
#   RESTAURANT_COUNT=10 SLOT_COUNT=5 ./seed-multi.sh    # 경합 지점 50개
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-verysecret}"
DB_NAME="${DB_NAME:-prototype_reservation}"
BASE_URL="${BASE_URL:-http://localhost:8081}"

SEAT_COUNT="${SEAT_COUNT:-30}"
RESTAURANT_COUNT="${RESTAURANT_COUNT:-1}"
SLOT_COUNT="${SLOT_COUNT:-1}"
POOL_SIZE="${POOL_SIZE:-2000}"
RESTAURANT_NAME="K6_PERF_RESTAURANT"
PASSWORD="K6perf!2026"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$SCRIPT_DIR/lib"
mkdir -p "$LIB_DIR"

MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 -N -B)

BOOKING_DATE="$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d tomorrow +%Y-%m-%d)"
BOOKING_DAY="$(date -v+1d +%A 2>/dev/null || date -d tomorrow +%A)"
BOOKING_DAY="$(echo "$BOOKING_DAY" | tr '[:lower:]' '[:upper:]')"

POINTS=$(( RESTAURANT_COUNT * SLOT_COUNT ))
echo "==> Wiping previous K6 perf-test data"
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

echo "==> Seeding $RESTAURANT_COUNT restaurants x $SLOT_COUNT slots = $POINTS 경합 지점, 슬롯당 ${SEAT_COUNT}석"

# 음식점/슬롯/좌석 전부와 slots.json을 한 번에 생성한다. 경합 지점이 200개면 좌석은 6,000행이라
# 셸 루프로는 느리다.
python3 - "$LIB_DIR" "$RESTAURANT_COUNT" "$SLOT_COUNT" "$SEAT_COUNT" \
         "$BOOKING_DATE" "$BOOKING_DAY" "$RESTAURANT_NAME" \
         "$BASE_URL" "$POOL_SIZE" <<'PY' > "$LIB_DIR/seed-multi.generated.sql"
import json, sys, uuid

(lib_dir, r_count, s_count, seat_count, date, day, name,
 base_url, pool_size) = sys.argv[1:10]
r_count, s_count, seat_count, pool_size = int(r_count), int(s_count), int(seat_count), int(pool_size)

# 슬롯 시작 시각은 18:00부터 1시간씩. 슬롯 키가 (restaurantId, date, startTime)이므로
# 시각이 다르면 서로 다른 락/파티션 키가 된다.
def slot_times(i):
    start = 18 + i
    return f"{start:02d}:00:00", f"{start + 1:02d}:00:00"

restaurants, slots, seat_rows = [], [], []
for _ in range(r_count):
    rid = str(uuid.uuid4())
    restaurants.append(
        f"('{rid}', '{name}', '0212345678', '06236', 'Seoul', 'K6 load test fixture', 0)"
    )
    for s in range(s_count):
        start, end = slot_times(s)
        slots.append({"restaurantId": rid, "date": date, "day": day,
                      "startTime": start, "endTime": end})
        for n in range(1, seat_count + 1):
            seat_rows.append(
                f"('{uuid.uuid4()}', '{rid}', '{date}', '{day}', '{start}', '{end}', "
                f"{n}, 4, 'EMPTY', 'NOT_CONFIRMED', 0)"
            )

print("INSERT INTO restaurant (id, name, phone, zip_code, address, detail, is_deleted) VALUES")
print(",\n".join(restaurants) + ";")

cols = ("INSERT INTO timetable (id, restaurant_id, date, day, start_time, end_time, "
        "table_number, table_size, table_status, time_table_confirm_status, version) VALUES")
CHUNK = 1000
for start_i in range(0, len(seat_rows), CHUNK):
    print(cols)
    print(",\n".join(seat_rows[start_i:start_i + CHUNK]) + ";")

with open(f"{lib_dir}/env.json", "w") as f:
    json.dump({
        "baseUrl": base_url,
        "slots": slots,
        "seatCount": seat_count,
        "contentionPoints": len(slots),
        "totalSeats": len(slots) * seat_count,
        "poolSize": pool_size,
    }, f, indent=2)
print(f"-- {len(slots)} slots, {len(seat_rows)} seats", file=sys.stderr)
PY

"${MYSQL[@]}" "$DB_NAME" < "$LIB_DIR/seed-multi.generated.sql"

# 유저 풀은 단일 슬롯 시딩과 동일한 방식이다 (seed.sh 주석 참고).
PASSWORD_HASH='$2a$10$XRUpNNPbgz/DSUtLZX4BxOxDSq/DU47QZ2N7X3wqea6TZANie.tkS'
USER_PREFIX="k6perf"

echo "==> Provisioning $POOL_SIZE users"
python3 - "$LIB_DIR" "$POOL_SIZE" "$PASSWORD" "$PASSWORD_HASH" "$USER_PREFIX" <<'PY' > "$LIB_DIR/users.generated.sql"
import json, sys, uuid

lib_dir, pool_size, password, pw_hash, prefix = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5]
width = max(4, len(str(pool_size)))
CHUNK = 500

print(f"DELETE FROM `user` WHERE login_id LIKE '{prefix}%';")

users, rows = [], []
for i in range(1, pool_size + 1):
    n = str(i).zfill(width)
    login_id = f"{prefix}{n}"
    users.append({"loginId": login_id, "password": password})
    rows.append(
        "('{id}','{lid}','{pw}','{lid}@t.local','{lid}','010{mob}','USER',0,NULL,'ACTIVATED',0)".format(
            id=str(uuid.uuid4()), lid=login_id, pw=pw_hash, mob=str(i).zfill(8)
        )
    )

cols = ("INSERT INTO `user` (id, login_id, password, email, nickname, mobile, role, "
        "fail_count, locked_datetime, user_status, is_need_to_change_password) VALUES")
for start in range(0, len(rows), CHUNK):
    print(cols)
    print(",\n".join(rows[start:start + CHUNK]) + ";")

with open(f"{lib_dir}/users.json", "w") as f:
    json.dump(users, f, indent=2)
PY

"${MYSQL[@]}" "$DB_NAME" < "$LIB_DIR/users.generated.sql"

ACTUAL="$("${MYSQL[@]}" "$DB_NAME" -e \
  "SELECT COUNT(*) FROM \`user\` WHERE login_id LIKE '${USER_PREFIX}%' AND user_status='ACTIVATED' AND fail_count=0;")"
if [ "$ACTUAL" -ne "$POOL_SIZE" ]; then
  echo "ERROR: 유저 $ACTUAL/$POOL_SIZE 만 생성됨" >&2
  exit 1
fi

echo "==> Done. $POINTS 경합 지점 / $(( POINTS * SEAT_COUNT ))석 / $ACTUAL users"
