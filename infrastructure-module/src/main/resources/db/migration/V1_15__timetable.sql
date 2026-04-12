CREATE TABLE prototype_reservation.timetable_occupancy
(
    id                  VARCHAR(128) PRIMARY KEY COMMENT '시간표 점유 ID',
    timetable_id        VARCHAR(128) COMMENT '시간표 ID',
    user_id             VARCHAR(128) COMMENT '사용자 ID',
    occupied_status     ENUM('OCCUPIED','UNOCCUPIED') COMMENT '점유 상태',
    occupied_datetime   DATETIME COMMENT '점유 시간',
    unoccupied_datetime DATETIME COMMENT '점유 해제 시간',
    INDEX               index_timetable_id_occupied_status (timetable_id, occupied_status)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

