#!/usr/bin/env bash
# 정합성 검사. 슬롯 단위로 센다.
#
# 왜 슬롯 단위인가: 총합만 보면 한 슬롯의 오버부킹이 다른 슬롯의 미판매에 가려진다.
#   슬롯A 2건(초과) + 슬롯B 0건(미판매) = 총 2건 = 좌석 2석 → "정상"으로 보인다.
#
# 왜 released_at IS NULL 인가: after는 점유를 PENDING으로 먼저 만든다.
#   occupied_status='OCCUPIED'로 세면 after의 판매 좌석이 전부 0으로 집계돼
#   "오버부킹 없음"이라는 잘못된 결론이 나온다.
#
# 출력: {"seeded":N,"sold":N,"overbookedSlots":N,"unsoldSlots":N}
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

# released_at 컬럼이 없는 스키마(before 초기본)면 occupied_status로 폴백한다.
if mysql_q -e "SHOW COLUMNS FROM timetable_occupancy LIKE 'released_at';" | grep -q released_at; then
  ALIVE="o.released_at IS NULL"
else
  ALIVE="o.occupied_status = 'OCCUPIED'"
fi

read -r SEEDED SOLD OVER UNSOLD <<<"$(mysql_q <<SQL
SELECT
  (SELECT COUNT(*) FROM timetable t JOIN restaurant r ON r.id = t.restaurant_id
     WHERE r.name LIKE '${HOT_PREFIX}%'),
  (SELECT COUNT(*) FROM timetable_occupancy o
     JOIN timetable t ON t.id = o.timetable_id
     JOIN restaurant r ON r.id = t.restaurant_id
     WHERE r.name LIKE '${HOT_PREFIX}%' AND ${ALIVE}),
  (SELECT COUNT(*) FROM (
     SELECT o.timetable_id FROM timetable_occupancy o
       JOIN timetable t ON t.id = o.timetable_id
       JOIN restaurant r ON r.id = t.restaurant_id
     WHERE r.name LIKE '${HOT_PREFIX}%' AND ${ALIVE}
     GROUP BY o.timetable_id HAVING COUNT(*) > 1) dup),
  (SELECT COUNT(*) FROM timetable t
     JOIN restaurant r ON r.id = t.restaurant_id
     WHERE r.name LIKE '${HOT_PREFIX}%'
       AND NOT EXISTS (SELECT 1 FROM timetable_occupancy o
                       WHERE o.timetable_id = t.id AND ${ALIVE}));
SQL
)"

printf '{"seeded":%s,"sold":%s,"overbookedSlots":%s,"unsoldSlots":%s,"alivePredicate":"%s"}\n' \
  "${SEEDED:-0}" "${SOLD:-0}" "${OVER:-0}" "${UNSOLD:-0}" "$ALIVE"
