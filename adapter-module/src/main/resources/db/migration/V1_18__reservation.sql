CREATE TABLE prototype_reservation.reservation
(
    id                             VARCHAR(128) COMMENT '예약 PK' PRIMARY KEY,
    user_id                        VARCHAR(128) COMMENT '사용자 ID',
    restaurant_id                  VARCHAR(128) COMMENT '매장 ID',
    timetable_id                   VARCHAR(128) COMMENT '시간표 ID',
    reservation_date               DATE COMMENT '시간표 날짜',
    reservation_day                ENUM ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') COMMENT '시간표 날짜 요일',
    reservation_time               TIME COMMENT '시작 시간',
    reservation_seat_size          INT COMMENT '테이블 사이즈',
    timetable_occupancy_id         VARCHAR(128) COMMENT '시간표 점유 ID',
    reservation_occupied_datetime  DATETIME COMMENT '예약 시간',
    reservation_status             ENUM ('RESERVED', 'CANCELLED') COMMENT '예약 상태',
    reservation_cancelled_datetime DATETIME COMMENT '취소 시간',
    INDEX index_user_id_reservation_date_reservation_status (user_id, reservation_date, reservation_status)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci'