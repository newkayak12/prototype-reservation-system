DROP TABLE IF EXISTS prototype_reservation.`nationality`;
CREATE TABLE prototype_reservation.`nationality`
(
    id               BIGINT COMMENT '카테고리 식별키',
    title            VARCHAR(64) NOT NULL COMMENT '카테고리명',
    category_type    ENUM ('NATIONALITY') NOT NULL COMMENT '카테고리 타입',
    is_deleted       TINYINT  DEFAULT 0 COMMENT '삭제 여부',
    created_datetime DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성 날짜-시간',
    updated_datetime DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '수정 날짜-시간',
    PRIMARY KEY (id),
    index index_category_type (category_type, id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

