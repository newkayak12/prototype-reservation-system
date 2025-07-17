ALTER TABLE prototype_reservation.app_user
    ADD COLUMN is_need_to_change_password TINYINT(1) DEFAULT false COMMENT '비밀번호 변경 필요 여부'
        AFTER password_changed_datetime
