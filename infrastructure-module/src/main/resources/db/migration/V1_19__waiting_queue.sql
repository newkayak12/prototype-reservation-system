CREATE TABLE prototype_reservation.waiting_queue
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '대기열 순번 (Redis INCR SEQUENCE 대체)',
    restaurant_id VARCHAR(128) NOT NULL COMMENT '매장 ID',
    date          DATE         NOT NULL COMMENT '시간표 날짜',
    start_time    TIME         NOT NULL COMMENT '시작 시간',
    ticket_id     VARCHAR(128) NOT NULL COMMENT '대기열 티켓 ID',
    status        ENUM ('WAITING','ADMITTED','PENDING','CONFIRMED','CANCELLED','EXPIRED') NOT NULL COMMENT '대기열 상태',
    admitted_at   DATETIME NULL COMMENT '입장 허용 시각 (Redis ADMITTED SET TTL 대체)',
    created_at    DATETIME     NOT NULL COMMENT '대기열 진입 시각',
    UNIQUE KEY unique_slot_ticket_id (restaurant_id, date, start_time, ticket_id),
    INDEX index_slot_status_id (restaurant_id, date, start_time, status, id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';
