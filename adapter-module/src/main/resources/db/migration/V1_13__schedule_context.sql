CREATE TABLE prototype_reservation.schedule
(
    id                       VARCHAR(255) PRIMARY KEY,
    tables_configured        BOOLEAN                     DEFAULT FALSE,
    working_hours_configured BOOLEAN                     DEFAULT FALSE,
    holidays_configured      BOOLEAN                     DEFAULT FALSE,
    status                   ENUM ('ACTIVE', 'INACTIVE') DEFAULT 'INACTIVE',
    total_tables             INT                         DEFAULT 0,
    total_capacity           INT                         DEFAULT 0
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';



CREATE TABLE prototype_reservation.time_span
(
    id            VARCHAR(128) COMMENT '식별키',
    restaurant_id VARCHAR(128)                                                                               NOT NULL COMMENT '음식점 식별키',
    day           ENUM ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') COMMENT '요일' NOT NULL,
    start_time    TIME                                                                                       NOT NULL COMMENT '설명',
    end_time      TIME                                                                                       NOT NULL COMMENT '설명',
    PRIMARY KEY (id),
    INDEX index_restaurant_id (restaurant_id)
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
    id            VARCHAR(128) COMMENT '식별키',
    restaurant_id VARCHAR(128)                                                                  NOT NULL COMMENT '음식점 식별키',
    date          DATE                                                                          NOT NULL COMMENT '날짜',
    start_time    TIME                                                                          NOT NULL COMMENT '시작 시간',
    end_time      TIME                                                                          NOT NULL COMMENT '종료 시간',
    table_number  INT COMMENT '테이블 번호',
    table_size    INT COMMENT '좌석 수',
    day           ENUM ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') NOT NULL COMMENT '요일',
    table_status  ENUM ('EMPTY', 'OCCUPIED')                                                    NOT NULL DEFAULT 'EMPTY' COMMENT '테이블 상태',
    PRIMARY KEY (id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';
