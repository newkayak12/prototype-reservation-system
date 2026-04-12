CREATE TABLE prototype_reservation.outbox
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'outbox id',
    event_version DOUBLE COMMENT 'version',
    event_type    ENUM ('TIME_TABLE_OCCUPIED') COMMENT '이벤트 타입',
    status        ENUM ('PUBLISHED','PROCESSED', 'ERRORED') COMMENT '상태',
    payload       JSON COMMENT 'payload',
    created_at    DATETIME COMMENT '등록 시간',
    updated_at    DATETIME COMMENT '수정 시간',
    INDEX index_status (status)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

