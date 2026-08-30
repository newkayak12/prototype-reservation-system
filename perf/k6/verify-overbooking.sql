-- Phase 0/5 정합성 검증: 성공한 점유 건수가 시드한 좌석 수를 넘지 않는지 확인.
-- 사용법: mysql ... prototype_reservation < verify-overbooking.sql
SELECT
    r.name AS restaurant_name,
    COUNT(DISTINCT t.id) AS seeded_seats,
    COUNT(DISTINCT CASE WHEN o.occupied_status = 'OCCUPIED' THEN o.id END) AS occupied_count,
    COUNT(DISTINCT CASE WHEN o.occupied_status = 'OCCUPIED' THEN o.id END) - COUNT(DISTINCT t.id) AS overbooked_by
FROM restaurant r
JOIN timetable t ON t.restaurant_id = r.id
LEFT JOIN timetable_occupancy o ON o.timetable_id = t.id
WHERE r.name = 'K6_PERF_RESTAURANT'
GROUP BY r.id, r.name;

-- 같은 timetable에 2건 이상 OCCUPIED가 붙었는지(=이중예약 발생 지점) 직접 표시.
SELECT
    t.id AS timetable_id,
    t.table_number,
    COUNT(*) AS occupied_rows
FROM timetable t
JOIN timetable_occupancy o ON o.timetable_id = t.id AND o.occupied_status = 'OCCUPIED'
JOIN restaurant r ON r.id = t.restaurant_id
WHERE r.name = 'K6_PERF_RESTAURANT'
GROUP BY t.id, t.table_number
HAVING COUNT(*) > 1;
