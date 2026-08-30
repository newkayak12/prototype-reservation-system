-- Phase 0/5 정합성 검증: 성공한 점유 건수가 시드한 좌석 수를 넘지 않는지 확인.
-- 사용법: mysql ... prototype_reservation < verify-overbooking.sql
--
-- "살아 있는 점유"의 기준을 상태 나열이 아니라 `released_at IS NULL`로 잡는다.
-- Phase 4에서 점유가 PENDING → CONFIRMED로 갈라졌고 예전 데이터에는 OCCUPIED도 있다. 상태를
-- 나열하면 상태가 하나 늘 때마다 이 쿼리를 찾아 고쳐야 하고, 빠뜨리면 **오버부킹을 0으로 잘못
-- 보고한다** — 검증 쿼리가 조용히 통과하는 것이 가장 위험하다.
-- `released_at`은 Phase 4 이전 행에서도 NULL이므로 이 조건은 양쪽 데이터에 모두 맞는다.
SELECT
    r.name AS restaurant_name,
    COUNT(DISTINCT t.id) AS seeded_seats,
    COUNT(DISTINCT CASE WHEN o.released_at IS NULL THEN o.id END) AS occupied_count,
    COUNT(DISTINCT CASE WHEN o.released_at IS NULL THEN o.id END) - COUNT(DISTINCT t.id) AS overbooked_by
FROM restaurant r
JOIN timetable t ON t.restaurant_id = r.id
LEFT JOIN timetable_occupancy o ON o.timetable_id = t.id
WHERE r.name = 'K6_PERF_RESTAURANT'
GROUP BY r.id, r.name;

-- 같은 timetable에 살아 있는 점유가 2건 이상 붙었는지(=이중예약 발생 지점) 직접 표시.
-- Phase 4의 UNIQUE(timetable_id, active_marker)가 정상 동작하면 이 결과는 항상 비어 있어야 한다.
SELECT
    t.id AS timetable_id,
    t.table_number,
    COUNT(*) AS occupied_rows
FROM timetable t
JOIN timetable_occupancy o ON o.timetable_id = t.id AND o.released_at IS NULL
JOIN restaurant r ON r.id = t.restaurant_id
WHERE r.name = 'K6_PERF_RESTAURANT'
GROUP BY t.id, t.table_number
HAVING COUNT(*) > 1;
