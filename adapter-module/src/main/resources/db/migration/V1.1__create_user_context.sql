CREATE TABLE `user`(
    id                        VARCHAR(128) comment '식별키',
    login_id                  VARCHAR(32) comment '사용자 아이디',
    password                  VARCHAR(256) comment '비밀번호',
    old_password              VARCHAR(256) comment '변경 전 비밀번호',
    password_changed_datetime DATETIME COMMENT '비밀번호 변경 시간',
    email                     VARCHAR(32) COMMENT '이메일',
    nickname                  VARCHAR(16) COMMENT '닉네임',
    mobile                    VARCHAR(13) COMMENT '휴대폰 번호',
    role                      ENUM ('ROOT', 'RESTAURANT_OWNER', 'USER') COMMENT '역할 (ROOT, SELLER, USER)',
    fail_count                TINYINT COMMENT '로그인 실패 카운트',
    locked_datetime           DATETIME COMMENT '접근 잠긴 날짜-시간',
    user_status               ENUM ('ACTIVATED', 'DEACTIVATED') COMMENT '사용자 상태 (ACTIVATE, DEACTIVATE)',
    created_datetime          DATETIME COMMENT '생성 날짜-시간',
    updated_datetime          DATETIME COMMENT '수정 날짜-시간',
    PRIMARY KEY (id),
    index 'index_login_id_and_role' (login_id, role)
) ENGINE = innodb
  DEFAULT CHARSET 'utf8mb4'


    user_change_history[user_change_history] {
        VARCHAR(128) id "식별키"
        VARCHAR(128) user_id "식별키"
        VARCHAR(32) log_id "사용자 아이디"
        VARCHAR(32) email "이메일"
        VARCHAR(16) nickname "닉네임"
        VARCHAR(13) mobile "휴대폰 번호"
        ENUM role "역할 (ROOT, SELLER, USER)"
        TINYINT fail_count "로그인 실패 카운트"
        DATETIME locked_datetime "접근 잠긴 날짜-시간"
        DATETIME created_datetime "생성 날짜-시간"
        DATETIME updated_datetime "수정 날짜-시간"
    }