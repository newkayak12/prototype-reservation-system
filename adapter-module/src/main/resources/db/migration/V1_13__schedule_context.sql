CREATE TABLE prototype_reservation.time_span
(
    id            VARCHAR(128) COMMENT '식별키',
    restaurant_id VARCHAR(128) NOT NULL COMMENT '음식점 식별키',
    day           ENUM ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') COMMENT '요일'NOT NULL ,
    startTime     TIME NOT NULL COMMENT '설명',
    endTime       TIME NOT NULL COMMENT '설명',
    PRIMARY KEY (id),
    INDEX index_restaurant_id (restaurant_id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';


CREATE TABLE prototype_reservation.holiday
(
    id      VARCHAR(128) COMMENT '식별키',
    restaurant_id VARCHAR(128) NOT NULL COMMENT '음식점 식별키',
    date     date COMMENT '날짜',
    PRIMARY KEY (id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

