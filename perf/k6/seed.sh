#!/usr/bin/env bash
# perf-test seed: restaurant + limited-seat timetable slot (raw SQL) + real sign-up users (real HTTP, no auth bypass).
#
# 좌석(=timetable)도 유저 풀(=최대 VU 수)도 매 버스트마다 DELETE→INSERT로 초기화한다.
# 둘 다 raw SQL 벌크 인서트라 60번 반복해도 회당 1초 미만이다.
#
# 레스토랑 UUID는 매번 새로 뽑는다 - Redis 세마포어/분산락 키가 restaurantId 기반이라
# 같은 ID를 재사용하면 이전 버스트의 세마포어 permit(TTL 10분)이 남아 다음 측정을 오염시킨다.
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-verysecret}"
DB_NAME="${DB_NAME:-prototype_reservation}"
BASE_URL="${BASE_URL:-http://localhost:8081}"
SEAT_COUNT="${SEAT_COUNT:-30}"
POOL_SIZE="${POOL_SIZE:-2000}"
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

# ---------------------------------------------------------------------------
# 유저 풀: 최대 VU 수만큼 DB에 직접 벌크 INSERT 한다.
#
# 회원가입 API를 POOL_SIZE번 호출하는 방식은 2000명 기준 수 분이 걸리고, 측정 대상도 아니다
# (측정 대상은 예약 API 하나). 로그인/토큰 발급은 여전히 실제 API를 쓰므로 인증 자체를
# 우회하지는 않는다 - 우회하는 건 "계정 생성" 뿐이다.
#
# 매 시딩마다 지우고 다시 넣는다: 60번 반복하는 동안 로그인 실패가 누적돼 fail_count가
# SignInPolicy 한계를 넘으면 계정이 잠기는데(DEACTIVATED), 그러면 다음 버스트의 setup()이
# 조용히 토큰을 못 받는다. 매번 fail_count=0 / locked_datetime=NULL / ACTIVATED로 되돌린다.
#
# 비밀번호는 BCrypt(strength 10) 해시. 앱의 PasswordEncoderUtility(BCryptPasswordEncoder)가
# 그대로 검증한다 - BCrypt는 해시에 salt가 박혀 있어 전원이 같은 해시 문자열을 써도 된다.
# 아래 값은 'K6perf!2026'을 Spring의 BCryptPasswordEncoder로 인코딩한 결과다.
# ---------------------------------------------------------------------------
PASSWORD_HASH='$2a$10$XRUpNNPbgz/DSUtLZX4BxOxDSq/DU47QZ2N7X3wqea6TZANie.tkS'
USER_PREFIX="k6perf"

echo "==> Provisioning $POOL_SIZE users (bulk INSERT, 인증 상태 초기화)"
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
print(f"-- {len(users)} users", file=sys.stderr)
PY

"${MYSQL[@]}" "$DB_NAME" < "$LIB_DIR/users.generated.sql"

ACTUAL="$("${MYSQL[@]}" "$DB_NAME" -e \
  "SELECT COUNT(*) FROM \`user\` WHERE login_id LIKE '${USER_PREFIX}%' AND user_status='ACTIVATED' AND fail_count=0;")"
if [ "$ACTUAL" -ne "$POOL_SIZE" ]; then
  echo "ERROR: 유저 $ACTUAL/$POOL_SIZE 만 생성됨" >&2
  exit 1
fi

echo "==> Done. $ACTUAL users + $SEAT_COUNT seats ready. env.json + users.json in $LIB_DIR"
