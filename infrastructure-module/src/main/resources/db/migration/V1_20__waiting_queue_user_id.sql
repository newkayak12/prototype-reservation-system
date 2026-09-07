-- 대기열 폴백 테이블에 사용자 식별자를 추가한다.
--
-- ticketId가 서버 발급 nonce로 바뀌면서 진입의 멱등 기준이 티켓에서 사용자로 옮겨갔다.
-- Redis 경로는 `TICKET_OF:{slot}:{userId}` 선점(SET NX)으로 "한 사용자 = 한 자리"를 보장하는데,
-- 폴백 경로에도 같은 보장이 없으면 Redis 장애 중에 한 사용자가 대기열에 여러 자리를 잡고
-- 나중에 입장 정원을 여러 번 소모한다. 그 선점에 대응하는 것이 아래 유니크 키다.
ALTER TABLE prototype_reservation.waiting_queue
    ADD COLUMN user_id VARCHAR(128) NOT NULL COMMENT '사용자 ID' AFTER start_time,
    ADD UNIQUE KEY unique_slot_user_id (restaurant_id, date, start_time, user_id);
