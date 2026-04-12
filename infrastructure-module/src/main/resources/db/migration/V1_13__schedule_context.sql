CREATE TABLE prototype_reservation.schedule
(
    id                       VARCHAR(255) PRIMARY KEY,
    tables_configured        BOOLEAN DEFAULT FALSE,
    working_hours_configured BOOLEAN DEFAULT FALSE,
    holidays_configured      BOOLEAN DEFAULT FALSE,
    status                   ENUM ('ACTIVE', 'INACTIVE') DEFAULT 'INACTIVE',
    total_tables             INT     DEFAULT 0,
    total_capacity           INT     DEFAULT 0
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';



CREATE TABLE prototype_reservation.time_span
(
    id            VARCHAR(128) COMMENT '식별키',
    restaurant_id VARCHAR(128) NOT NULL COMMENT '음식점 식별키',
    day           ENUM ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') COMMENT '요일' NOT NULL,
    start_time    TIME         NOT NULL COMMENT '설명',
    end_time      TIME         NOT NULL COMMENT '설명',
    PRIMARY KEY (id),
    INDEX         index_restaurant_id (restaurant_id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';


CREATE TABLE prototype_reservation.holiday
(
    id            VARCHAR(128) COMMENT '식별키',
    restaurant_id VARCHAR(128) NOT NULL COMMENT '음식점 식별키',
    date          date COMMENT '날짜',
    PRIMARY KEY (id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';


CREATE TABLE prototype_reservation.table
(
    id            VARCHAR(128) COMMENT '식별키',
    restaurant_id VARCHAR(128) NOT NULL COMMENT '음식점 식별키',
    table_number  INT COMMENT '테이블 번호',
    table_size    INT COMMENT '좌석 수',
    PRIMARY KEY (id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

CREATE TABLE prototype_reservation.timetable
(
    id                        VARCHAR(128) PRIMARY KEY COMMENT '시간표 ID',
    restaurant_id             VARCHAR(128) COMMENT '매장 ID',
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