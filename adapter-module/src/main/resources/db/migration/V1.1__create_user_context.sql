CREATE TABLE prototype_reservation.`user`
(
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
    index index_login_id_and_role (login_id, role)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';


CREATE TABLE prototype_reservation.user_change_history
(
    id               VARCHAR(128) COMMENT '식별키',
    user_id          VARCHAR(128) COMMENT '식별키',
    log_id           VARCHAR(32) COMMENT '사용자 아이디',
    email            VARCHAR(32) COMMENT '이메일',
    nickname         VARCHAR(16) COMMENT '닉네임',
    mobile           VARCHAR(13) COMMENT '휴대폰 번호',
    role             ENUM ('ROOT', 'RESTAURANT_OWNER', 'USER') COMMENT '역할 (ROOT, SELLER, USER)',
    fail_count       TINYINT COMMENT '로그인 실패 카운트',
    locked_datetime  DATETIME COMMENT '접근 잠긴 날짜-시간',
    created_datetime DATETIME COMMENT '생성 날짜-시간',
    updated_datetime DATETIME COMMENT '수정 날짜-시간',
    PRIMARY KEY (id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';


CREATE TABLE prototype_reservation.user_access_history
(
    id              VARCHAR(128) COMMENT '식별키',
    user_id         VARCHAR(128) COMMENT '식별키',
    access_status   ENUM ('SUCCESS', 'FAILURE') COMMENT '상태(SUCCESS, FAILURE)',
    access_datetime DATETIME COMMENT '요청 날짜-시간',
    PRIMARY KEY (id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';


CREATE TABLE prototype_reservation.withdrawal_user
(
    id                 VARCHAR(128) COMMENT '식별키',
    login_id           VARCHAR(32) COMMENT '사용자 아이디',
    encrypted_email    VARCHAR(256) COMMENT '이메일',
    encrypted_nickname VARCHAR(256) COMMENT '닉네임',
    encrypted_mobile   VARCHAR(256) COMMENT '휴대폰 번호',
    encrypted_role     VARCHAR(256) COMMENT '역할 ',
    created_datetime   DATETIME COMMENT '생성 날짜-시간',
    PRIMARY KEY (id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';
