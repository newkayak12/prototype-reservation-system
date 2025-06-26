ALTER TABLE prototype_reservation.user_change_history ADD COLUMN user_uuid VARCHAR(128) comment '사용자 식별키' AFTER id;
ALTER TABLE prototype_reservation.user_change_history DROP COLUMN fail_count;
ALTER TABLE prototype_reservation.user_change_history DROP COLUMN locked_datetime;