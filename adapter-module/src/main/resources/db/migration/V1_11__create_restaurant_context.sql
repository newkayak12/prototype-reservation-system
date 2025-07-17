CREATE TABLE prototype_reservation.restaurant
(
    id               VARCHAR(128) COMMENT '식별키',
    company_id       VARCHAR(128) COMMENT 'company 식별키',
    user_id          VARCHAR(128) COMMENT 'user 식별키',
    name             VARCHAR(64) COMMENT '음식점 이름',
    introduce        TEXT COMMENT '음식점 소개',
    phone            VARCHAR(13) COMMENT '음식점 전화번호',
    zip_code         VARCHAR(5) COMMENT '우편번호',
    address          VARCHAR(256) COMMENT '음식점 주소',
    detail           VARCHAR(256) COMMENT '음식점 주소 상세',
    latitude         DECIMAL(8, 5) COMMENT '위도',
    longitude        DECIMAL(8, 5) COMMENT '경도',
    is_deleted       TINYINT  DEFAULT 0 COMMENT '삭제 여부',
    created_datetime DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성 날짜-시간',
    updated_datetime DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '수정 날짜-시간',
    PRIMARY KEY (id),
    INDEX index_name_is_deleted (name, is_deleted)
)
    ENGINE = innodb
    DEFAULT CHARACTER SET 'utf8mb4'
    COLLATE 'utf8mb4_general_ci';

CREATE TABLE prototype_reservation.restaurant_photo
(
    id            VARCHAR(128) COMMENT '식별키',
    restaurant_id VARCHAR(128) COMMENT '음식점 식별키',
    url           VARCHAR(256) COMMENT 'URL',
    PRIMARY KEY (id)
)
    ENGINE = innodb
    DEFAULT CHARACTER SET 'utf8mb4'
    COLLATE 'utf8mb4_general_ci';

CREATE TABLE prototype_reservation.restaurant_working_day
(
    restaurant_id  VARCHAR(128) COMMENT '음식점 식별키',
    day_of_month ENUM ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY') COMMENT '요일',
    start_time TIME COMMENT '시작 시간',
    end_time TIME COMMENT '종료 시간',
    PRIMARY KEY (restaurant_id, day_of_month)
)
    ENGINE = innodb
    DEFAULT CHARACTER SET 'utf8mb4'
    COLLATE 'utf8mb4_general_ci';


CREATE TABLE prototype_reservation.restaurant_category
(
    id VARCHAR(128) COMMENT '식별키',
    restaurant_id  VARCHAR(128) COMMENT '음식점 식별키',
    category_id BIGINT COMMENT '카테고리 식별자',
    created_datetime DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성 날짜-시간',
    updated_datetime DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '수정 날짜-시간',
    PRIMARY KEY (id),
    INDEX index_restaurant_category (restaurant_id, category_id)
)
    ENGINE = innodb
    DEFAULT CHARACTER SET 'utf8mb4'
    COLLATE 'utf8mb4_general_ci';
