CREATE TABLE prototype_reservation.company
(
    id                            VARCHAR(128) COMMENT '식별키',
    brand_name                    VARCHAR(64) COMMENT '상호명',
    brand_url                     VARCHAR(64) COMMENT '회사 홈페이지 URL',
    business_number               VARCHAR(16) COMMENT '사업자 번호',
    corporate_registration_number VARCHAR(16) COMMENT '법인 등록번호',
    phone                         VARCHAR(16) COMMENT '전화번호',
    email                         VARCHAR(32) COMMENT '이메일',
    url                           VARCHAR(64) COMMENT 'URL',
    zip_code                      VARCHAR(8) COMMENT '우편번호',
    address                       VARCHAR(64) COMMENT '주소',
    detail                        VARCHAR(64) COMMENT '주소 상세',
    representative_name           VARCHAR(16) COMMENT '대표자',
    representative_mobile         VARCHAR(16) COMMENT '대표자 전화번호',
    PRIMARY KEY (id)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

