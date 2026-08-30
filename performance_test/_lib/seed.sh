#!/usr/bin/env bash
# 경합점(핫슬롯) + 배경 조회용 식당을 시딩한다.
#
#   POINTS         경합점 수 (식당 1개 = 경합점 1개, 각각 독립 분산락 키)
#   SLOTS          경합점당 시간 슬롯 수 (18:00부터 1시간씩). 다건 예약 시나리오용
#   SEATS          슬롯당 좌석(timetable) 수
#   BG_SEATS       배경 조회용 식당의 좌석 수 (락 경합 없음, 커넥션 예열 GET 대상)
#
# 경합점마다 식당 UUID를 새로 뽑는다 - 분산락/세마포어 키가 restaurantId 기반이고
# 세마포어 TTL이 10분이라, 같은 ID를 재사용하면 이전 런의 permit이 남아 결과를 오염시킨다.
#
# 슬롯이 여러 개인 이유: 다건 예약(한 사람이 18/19/20시를 한꺼번에 잡는다)은 락 키가
# 슬롯마다 다르다. 슬롯이 하나면 그 시나리오 자체가 성립하지 않는다.
#
# 출력: _lib/env.json  { baseUrl, date, day, slots:[...], points:[...], background:{...} }
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

POINTS="${POINTS:-1}"
SLOTS="${SLOTS:-1}"
SEATS="${SEATS:-30}"
BG_SEATS="${BG_SEATS:-20}"

BOOKING_DATE="$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d tomorrow +%Y-%m-%d)"
BOOKING_DAY="$(date -v+1d +%A 2>/dev/null || date -d tomorrow +%A)"
BOOKING_DAY="$(echo "$BOOKING_DAY" | tr '[:lower:]' '[:upper:]')"

log "==> 이전 픽스처 제거"
mysql_q <<SQL
DELETE FROM timetable_occupancy WHERE timetable_id IN (
  SELECT id FROM timetable WHERE restaurant_id IN (
    SELECT id FROM restaurant WHERE name LIKE '${HOT_PREFIX}%' OR name = '${BG_NAME}'
  )
);
DELETE FROM timetable WHERE restaurant_id IN (
  SELECT id FROM restaurant WHERE name LIKE '${HOT_PREFIX}%' OR name = '${BG_NAME}'
);
DELETE FROM restaurant WHERE name LIKE '${HOT_PREFIX}%' OR name = '${BG_NAME}';
SQL

log "==> 시딩: 경합점 ${POINTS}개 × 슬롯 ${SLOTS}개 × 좌석 ${SEATS}석  (+ 배경 식당 ${BG_SEATS}석)"
python3 - "$PERF_LIB" "$POINTS" "$SLOTS" "$SEATS" "$BG_SEATS" "$BOOKING_DATE" "$BOOKING_DAY" \
         "$HOT_PREFIX" "$BG_NAME" "$BASE_URL" \
         <<'PY' > "$PERF_LIB/seed.generated.sql"
import json, sys, uuid

(lib, points, slot_count, seats, bg_seats, date, day,
 hot_prefix, bg_name, base_url) = sys.argv[1:11]
points, slot_count, seats, bg_seats = int(points), int(slot_count), int(seats), int(bg_seats)
CHUNK = 1000

def uid(): return str(uuid.uuid4())

# 18시부터 1시간씩. 슬롯이 하나면 기존과 동일한 18:00~19:00 이다.
slots = [{"startTime": f"{18 + s:02d}:00:00", "endTime": f"{19 + s:02d}:00:00"}
         for s in range(slot_count)]

restaurants, timetables, point_ids = [], [], []

for i in range(1, points + 1):
    rid = uid()
    point_ids.append(rid)
    restaurants.append(
        f"('{rid}','{hot_prefix}_{i:04d}','0212345678','06236','Seoul','perf hot',0)")
    for sl in slots:
        for n in range(1, seats + 1):
            timetables.append(
                f"('{uid()}','{rid}','{date}','{day}','{sl['startTime']}','{sl['endTime']}',"
                f"{n},4,'EMPTY','NOT_CONFIRMED',0)")

# 배경 식당은 커넥션 예열용 GET 대상이다. 락도 쓰기도 타지 않으므로 첫 슬롯만 있으면 된다.
bg_id = uid()
restaurants.append(f"('{bg_id}','{bg_name}','0212345679','06236','Seoul','perf background',0)")
for n in range(1, bg_seats + 1):
    timetables.append(
        f"('{uid()}','{bg_id}','{date}','{day}','{slots[0]['startTime']}',"
        f"'{slots[0]['endTime']}',{n},4,'EMPTY','NOT_CONFIRMED',0)")

R_COLS = ("INSERT INTO restaurant (id, name, phone, zip_code, address, detail, is_deleted) "
          "VALUES")
T_COLS = ("INSERT INTO timetable (id, restaurant_id, date, day, start_time, end_time, "
          "table_number, table_size, table_status, time_table_confirm_status, version) VALUES")

for cols, rows in ((R_COLS, restaurants), (T_COLS, timetables)):
    for s in range(0, len(rows), CHUNK):
        print(cols)
        print(",\n".join(rows[s:s + CHUNK]) + ";")

with open(f"{lib}/env.json", "w") as f:
    json.dump({
        "baseUrl": base_url,
        "date": date, "day": day,
        "slots": slots,
        "seatsPerSlot": seats,
        "points": point_ids,
        "background": {"restaurantId": bg_id, "seats": bg_seats},
    }, f, indent=2)

print(f"-- {len(restaurants)} restaurants, {len(timetables)} timetables", file=sys.stderr)
PY

mysql_q < "$PERF_LIB/seed.generated.sql"

ACTUAL="$(mysql_q -e "SELECT COUNT(*) FROM timetable t JOIN restaurant r ON r.id = t.restaurant_id
                      WHERE r.name LIKE '${HOT_PREFIX}%';")"
EXPECTED=$(( POINTS * SLOTS * SEATS ))
if [ "$ACTUAL" -ne "$EXPECTED" ]; then
  echo "ERROR: 좌석 $ACTUAL/$EXPECTED 만 시딩됨" >&2
  exit 1
fi

log "==> 완료. ${POINTS}점 × ${SLOTS}슬롯 × ${SEATS}석 = ${ACTUAL}석. env.json 갱신."
