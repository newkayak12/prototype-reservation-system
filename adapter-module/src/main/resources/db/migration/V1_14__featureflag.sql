CREATE TABLE prototype_reservation.feature_flag
(
    identifier        BIGINT PRIMARY KEY,
    feature_flag_type ENUM('BACKEND', 'FRONTEND') DEFAULT 'BACKEND',
    feature_flag_key  VARCHAR(255) DEFAULT FALSE,
    is_enabled        BOOLEAN      DEFAULT FALSE,
    INDEX             index_feature_flag_type_is_enabled (feature_flag_type, is_enabled)
) ENGINE = innodb
  DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_general_ci';

