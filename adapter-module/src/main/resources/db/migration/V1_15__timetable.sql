CREATE TABLE prototype_reservation.timetable
(
    identifier                BIGINT PRIMARY KEY COMMENT '시간표 ID',
    restaurant_id             BIGINT COMMENT '매장 ID',
    `date`                    DATE COMMENT '시간표 날짜',
    `day`                     ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') COMMENT '시간표 날짜 요일',
    start_time                TIME COMMENT '시작 시간',
    end_time                  TIME COMMENT '종료 시간',
    table_number              INT COMMENT '테이블 번호',
    table_size                INT COMMENT '테이블 사이즈',
    table_status              ENUM('EMPTY','OCCUPIED') COMMENT '테이블 점유 상태',
    time_table_confirm_status ENUM('NOT_CONFIRMED','CONFIRMED') COMMENT '테이블 승인 여부',
    INDEX                     index_restaurant_date_start_time_table_status (restaurant_id,`date`,start_time,table_status)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

CREATE TABLE prototype_reservation.timetable_occupancy
(
    identifier          BIGINT PRIMARY KEY COMMENT '시간표 점유 ID',
    timetable_id        BIGINT COMMENT '시간표 ID',
    user_id             BIGINT COMMENT '사용자 ID',
    occupied_status     ENUM('OCCUPIED','UNOCCUPIED') COMMENT '점유 상태',
    occupied_datetime   DATETIME COMMENT '점유 시간',
    unoccupied_datetime DATETIME COMMENT '점유 해제 시간',
    INDEX               index_timetable_id_occupied_status (timetable_id, occupied_status)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

