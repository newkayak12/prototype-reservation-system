-- 좌석을 곧바로 확정하지 않고 임시로 잡아 두는(PENDING) 흐름을 위한 스키마.
--
-- PENDING  : 좌석을 잡았지만 사용자가 아직 확정하지 않은 상태. 5분 내 확정하지 않으면 스케줄러가 취소한다.
-- CONFIRMED: 사용자가 확정한 상태. 이 시점에 하류(예약 생성)로 도메인 이벤트가 나간다.
-- OCCUPIED / UNOCCUPIED: 기존 값. 이전 데이터를 그대로 두기 위해 남긴다.
ALTER TABLE prototype_reservation.timetable_occupancy
    MODIFY COLUMN occupied_status ENUM('PENDING','CONFIRMED','OCCUPIED','UNOCCUPIED')
        COMMENT '점유 상태',
    ADD COLUMN released_at DATETIME NULL
        COMMENT '점유가 풀린 시각. NULL이면 아직 살아 있는 점유' AFTER unoccupied_datetime;

-- 같은 좌석에 살아 있는 점유가 둘 이상 생기지 않게 하는 마지막 방어선.
--
-- MySQL은 UNIQUE 인덱스에서 NULL을 서로 다른 값으로 취급한다. 그래서 `UNIQUE(timetable_id,
-- released_at)`에 **활성 행이 released_at IS NULL**이라면 (timetable_id, NULL)이 여러 개
-- 들어가도 아무도 막지 않는다 — 의도와 정반대다.
--
-- 그래서 조건을 뒤집는다. 살아 있는 점유에만 상수 1이 붙고 풀린 점유는 NULL이 되는 생성 컬럼을
-- 두면, 활성 행은 (timetable_id, 1)로 충돌해 한 건만 남고 취소·만료된 이력은 NULL이라 얼마든지
-- 쌓일 수 있다. 이력을 지우지 않고도 "지금 이 좌석을 쥔 사람은 한 명"을 DB가 보장한다.
ALTER TABLE prototype_reservation.timetable_occupancy
    ADD COLUMN active_marker TINYINT
        GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN 1 ELSE NULL END) STORED
        COMMENT '살아 있는 점유에만 1. 유니크 제약 전용 파생 컬럼',
    ADD UNIQUE KEY unique_active_occupancy (timetable_id, active_marker);

-- 만료 스캔이 훑는 조건. 이 인덱스가 없으면 스케줄러가 주기마다 풀스캔한다.
CREATE INDEX index_occupied_status_occupied_datetime
    ON prototype_reservation.timetable_occupancy (occupied_status, occupied_datetime);
