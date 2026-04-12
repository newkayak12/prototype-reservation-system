CREATE TABLE prototype_reservation.menu
(
    id                VARCHAR(128) COMMENT '식별키',
    restaurant_id     VARCHAR(128) COMMENT '음식점 식별키',
    title             VARCHAR(30) COMMENT '이름',
    description       VARCHAR(255) COMMENT '설명',
    price             DECIMAL(10, 0) COMMENT '가격',
    is_representative BOOLEAN DEFAULT FALSE COMMENT '대표 여부',
    is_recommended    BOOLEAN DEFAULT FALSE COMMENT '추천 여부',
    is_visible        BOOLEAN DEFAULT FALSE COMMENT '노출 여부',
    is_deleted        BOOLEAN DEFAULT FALSE COMMENT '삭제 여부',
    PRIMARY KEY (id),
    INDEX index_title_is_deleted  (title, is_deleted),
    INDEX index_restaurant_id  (restaurant_id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';


CREATE TABLE prototype_reservation.menu_photo
(
    id      VARCHAR(128) COMMENT '식별키',
    menu_id VARCHAR(128) COMMENT '메뉴 식별키',
    url     VARCHAR(256) COMMENT 'URL',
    PRIMARY KEY (id),
    INDEX index_menu_id (menu_id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

